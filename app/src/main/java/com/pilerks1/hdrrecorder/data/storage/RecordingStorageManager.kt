package com.pilerks1.hdrrecorder.data.storage

import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.OutputOptions
import android.os.storage.StorageManager as AndroidStorageManager

class RecordingStorageManager(private val context: Context, private val reporter: ErrorReporter) {
    private val resolver = context.contentResolver

    fun takePersistablePermission(uri: Uri) {
        resolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    /** A saved destination must still have a writable directory and persisted grant. */
    suspend fun checkPresetDestination(value: String): Result<Boolean> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val uri = Uri.parse(value)
                if (!DocumentsContract.isTreeUri(uri) || resolver.persistedUriPermissions.none {
                        it.uri == uri && it.isReadPermission && it.isWritePermission
                    }) return@withContext Result.success(false)
                val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                val usable = resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
                    cursor.moveToFirst() && cursor.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR &&
                        cursor.getInt(1) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
                } ?: false
                Result.success(usable)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    /** Call from an IO dispatcher. Custom-folder setup falls back to primary MediaStore storage. */
    fun createOutput(destination: RecordingDestination, fileName: String): RecordingOutput {
        if (destination is RecordingDestination.DocumentTree) {
            var document: Uri? = null
            var descriptor: ParcelFileDescriptor? = null
            try {
                val directory = DocumentsContract.buildDocumentUriUsingTree(
                    destination.uri, DocumentsContract.getTreeDocumentId(destination.uri)
                )
                document = DocumentsContract.createDocument(resolver, directory, "application/octet-stream", fileName)
                    ?: error("Document provider did not create the requested file")
                descriptor = resolver.openFileDescriptor(document, "rw")
                    ?: error("Document provider did not open the requested file")
                return RecordingOutput(
                    FileDescriptorOutputOptions.Builder(descriptor).build(),
                    destination, fileName, descriptor, document
                )
            } catch (error: Exception) {
                closeDescriptor(descriptor)
                deleteDocument(document)
                reporter.report(IssueCode.STORAGE_FALLBACK, "createOutput", error, mapOf("recovery" to "MediaStore", "destination" to "default"))
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            // The video collection requires a recognized video MIME type at insertion.
            // This provisional type is replaced using Android's detection after finalization.
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/HDR-Recorder")
        }
        val options = MediaStoreOutputOptions.Builder(
            resolver, MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        ).setContentValues(values).build()
        return RecordingOutput(options, RecordingDestination.Default, fileName, null, null)
    }

    /** Call from an IO dispatcher. An unavailable measurement is never another drive's space. */
    fun availableBytes(destination: RecordingDestination): Long? = try {
        when (destination) {
            RecordingDestination.Default -> {
                val directory = context.getSystemService(AndroidStorageManager::class.java)
                    .primaryStorageVolume.directory
                directory?.let { StatFs(it.absolutePath).availableBytes }
            }
            is RecordingDestination.DocumentTree -> documentTreeAvailableBytes(destination.uri)
        }
    } catch (error: Exception) {
        reporter.report(IssueCode.TELEMETRY, "availableBytes", error, notify = false)
        null
    }

    /**
     * Document directories cannot be opened as files, and a tree grant does not cover the
     * provider's root metadata. Free space is therefore known only for the platform's external
     * storage provider, whose tree IDs start with the storage volume ("primary" or its UUID).
     * Any other provider reports unavailable (null).
     */
    private fun documentTreeAvailableBytes(tree: Uri): Long? {
        if (tree.authority != EXTERNAL_STORAGE_AUTHORITY) return null
        val volumeId = DocumentsContract.getTreeDocumentId(tree).substringBefore(':')
        val volume = context.getSystemService(AndroidStorageManager::class.java).storageVolumes
            .firstOrNull { if (volumeId == "primary") it.isPrimary else it.uuid == volumeId }
        return volume?.directory?.let { StatFs(it.absolutePath).availableBytes }
    }

    /** Retained by RecordingManager until CameraX finalizes; all cleanup is idempotent. */
    inner class RecordingOutput internal constructor(
        val options: OutputOptions,
        val destination: RecordingDestination,
        private val fileName: String,
        private val descriptor: ParcelFileDescriptor?,
        private val document: Uri?
    ) {
        private var finished = false

        fun abort() {
            if (finished) return
            finished = true
            closeDescriptor(descriptor)
            deleteDocument(document)
        }

        /** Call from an IO dispatcher. Failed recordings retain their files, matching the existing behavior. */
        fun complete(outputUri: Uri, successful: Boolean) {
            if (finished) return
            finished = true
            closeDescriptor(descriptor)
            if (successful) correctOutputMetadata(document ?: outputUri, fileName, document != null)
        }
    }

    private fun correctOutputMetadata(uri: Uri, originalName: String, isDocument: Boolean) {
        val mimeType = MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        } ?: error("Android could not identify the recording MIME type")
        check(mimeType.startsWith("video/")) { "Unexpected recording MIME type: $mimeType" }
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.takeIf { it.isNotBlank() }
            ?: error("Android has no filename extension for $mimeType")
        val fileName = "$originalName.$extension"
        if (isDocument) {
            // Document MIME types are provider-owned; providers may report stale or generic types.
            DocumentsContract.renameDocument(resolver, uri, fileName)
                ?: error("Document provider could not rename the recording")
        } else {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            }
            check(resolver.update(uri, values, null, null) > 0) { "Unable to update recording metadata" }
        }
    }

    private fun closeDescriptor(descriptor: ParcelFileDescriptor?) {
        try {
            descriptor?.close()
        } catch (error: Exception) {
            reporter.report(IssueCode.STORAGE_CLEANUP, "closeDescriptor", error, notify = false)
        }
    }

    private fun deleteDocument(uri: Uri?) {
        if (uri == null) return
        try {
            check(DocumentsContract.deleteDocument(resolver, uri)) { "Provider refused document deletion" }
        } catch (error: Exception) {
            reporter.report(IssueCode.STORAGE_CLEANUP, "deleteUnusedDocument", error, notify = false)
        }
    }

    private companion object {
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    }
}
