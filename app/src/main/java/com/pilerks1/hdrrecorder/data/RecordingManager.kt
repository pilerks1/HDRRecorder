package com.pilerks1.hdrrecorder.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.net.toUri

/**
 * Manages the video recording process.
 * This class handles starting, stopping, pausing, and resuming recordings.
 * Recording duration is owned by StatsManager (fed via updateHardwareStats using the
 * hardware recording stream), so this class does not keep its own timer.
 */
class RecordingManager(private val context: Context, private val statsManager: StatsManager) {

    private data class ActiveRecording(
        val id: Long,
        val outputPfd: ParcelFileDescriptor?,
        var recording: Recording? = null,
        var stopRequested: Boolean = false,
        var lastBytes: Long = 0L,
        var lastDurationNanos: Long = 0L
    )

    private var activeRecording: ActiveRecording? = null
    private var nextRecordingId = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(videoCapture: VideoCapture<Recorder>, storageUri: String?): Boolean {
        if (activeRecording != null) {
            Log.w("RecordingManager", "Ignoring start request while another recording is finalizing")
            return false
        }

        Log.d("RecordingManager", "startRecording called")

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
            .format(System.currentTimeMillis()) + ".mp4"

        val output = try {
            prepareOutput(videoCapture, storageUri, name)
        } catch (e: Exception) {
            Log.e("RecordingManager", "Failed to prepare recording output", e)
            return false
        }
        val session = ActiveRecording(id = nextRecordingId++, outputPfd = output.outputPfd)
        activeRecording = session

        return try {
            session.recording = output.pendingRecording
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    handleRecordingEvent(session.id, event)
                }
            _isRecording.value = true
            true
        } catch (e: Exception) {
            activeRecording = null
            session.outputPfd?.closeQuietly()
            output.safDocumentUri?.let { context.contentResolver.delete(it, null, null) }
            Log.e("RecordingManager", "Failed to start recording", e)
            false
        }
    }

    private data class PreparedOutput(
        val pendingRecording: PendingRecording,
        val outputPfd: ParcelFileDescriptor?,
        val safDocumentUri: android.net.Uri? = null
    )

    private fun prepareOutput(
        videoCapture: VideoCapture<Recorder>,
        storageUri: String?,
        fileName: String
    ): PreparedOutput {
        if (storageUri != null) {
            var createdFileUri: android.net.Uri? = null
            var pfd: ParcelFileDescriptor? = null
            try {
                val treeUri = storageUri.toUri()
                val directoryUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
                createdFileUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    directoryUri,
                    "video/mp4",
                    fileName
                ) ?: error("Document provider did not create the requested file")
                pfd = context.contentResolver.openFileDescriptor(createdFileUri, "rw")
                    ?: error("Document provider did not open the requested file")

                val options = FileDescriptorOutputOptions.Builder(pfd).build()
                return PreparedOutput(
                    pendingRecording = videoCapture.output.prepareRecording(context, options),
                    outputPfd = pfd,
                    safDocumentUri = createdFileUri
                )
            } catch (e: Exception) {
                pfd?.closeQuietly()
                createdFileUri?.let { context.contentResolver.delete(it, null, null) }
                Log.e("RecordingManager", "Failed to set up SAF custom storage; using MediaStore", e)
            }
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/HDR-Recorder")
        }
        val options = MediaStoreOutputOptions
            .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        return PreparedOutput(videoCapture.output.prepareRecording(context, options), null)
    }

    fun stopRecording() {
        Log.d("RecordingManager", "stopRecording called")
        val session = activeRecording ?: return
        if (!session.stopRequested) {
            session.stopRequested = true
            session.recording?.stop()
        }
    }

    fun pauseRecording() {
        Log.d("RecordingManager", "pauseRecording called")
        activeRecording?.recording?.pause()
    }

    fun resumeRecording() {
        Log.d("RecordingManager", "resumeRecording called")
        activeRecording?.recording?.resume()
    }

    private fun handleRecordingEvent(recordingId: Long, event: VideoRecordEvent) {
        val session = activeRecording?.takeIf { it.id == recordingId } ?: return
        when (event) {
            is VideoRecordEvent.Status -> {
                val stats = event.recordingStats
                val bytes = stats.numBytesRecorded
                val durationNanos = stats.recordedDurationNanos
                
                // 1. Calculate precise bitrate using hardware deltas
                var bitrate = 0.0
                if (session.lastDurationNanos > 0L) {
                    val deltaBytes = bytes - session.lastBytes
                    val deltaNanos = durationNanos - session.lastDurationNanos
                    val deltaSeconds = deltaNanos / 1_000_000_000.0
                    if (deltaSeconds > 0) {
                        bitrate = (deltaBytes * 8.0) / (deltaSeconds * 1_000_000.0)
                    }
                }
                
                session.lastBytes = bytes
                session.lastDurationNanos = durationNanos

                // 2. Update StatsManager with real hardware numbers and calculated bitrate.
                // This hardware duration is the single source of truth for recording time.
                statsManager.updateHardwareStats(bytes, durationNanos, bitrate)
            }
            is VideoRecordEvent.Start -> {
                Log.d("RecordingManager", "Recording started")
            }
            is VideoRecordEvent.Finalize -> {
                session.outputPfd?.closeQuietly()
                activeRecording = null
                _isRecording.value = false

                if (!event.hasError()) {
                    val msg = "Video saved successfully"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    Log.d("RecordingManager", "Video saved to: ${event.outputResults.outputUri}")
                } else {
                    Log.e("RecordingManager", "Video capture error: ${event.error}")
                }
            }
        }
    }

    private fun ParcelFileDescriptor.closeQuietly() {
        try {
            close()
        } catch (e: Exception) {
            Log.w("RecordingManager", "Failed to close recording descriptor", e)
        }
    }
}
