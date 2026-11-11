package com.pilerks1.hdrrecorder.data

import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode

import com.pilerks1.hdrrecorder.data.storage.PrivateDocumentStore
import com.pilerks1.hdrrecorder.model.CameraPreset
import com.pilerks1.hdrrecorder.model.presetNameError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Owns preset documents and is the only serializer of preset file operations. */
class PresetManager(private val store: PrivateDocumentStore, private val reporter: ErrorReporter) {
    private val mutex = Mutex()

    suspend fun list(): List<CameraPreset> = mutex.withLock {
        store.listIds().mapNotNull { id ->
            try {
                readDocument(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reporter.report(IssueCode.PRESET_UNREADABLE, "readPresetDocument", error)
                null
            }
        }.sortedWith(compareBy<CameraPreset, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id })
    }

    suspend fun read(id: String): CameraPreset = mutex.withLock { readDocument(id) }

    /** New presets always receive a fresh UUID from [newId]; updates must target an existing file. */
    suspend fun save(preset: CameraPreset, create: Boolean = false) = mutex.withLock {
        require(presetNameError(preset.name) == null && preset.name == preset.name.trim()) { "Enter a preset name" }
        if (!create) require(store.read(preset.id) != null) { "Preset no longer exists" }
        store.write(preset.id, withContext(Dispatchers.Default) { PresetCodec.encode(preset) })
    }

    suspend fun delete(id: String) = mutex.withLock { store.delete(id) }

    private suspend fun readDocument(id: String): CameraPreset {
        val json = store.read(id) ?: error("Preset no longer exists")
        return withContext(Dispatchers.Default) {
            PresetCodec.decode(json).also { require(it.id == id) { "Preset ID does not match filename" } }
        }
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
