package com.pilerks1.hdrrecorder.data

import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode

import com.pilerks1.hdrrecorder.data.storage.PresetRepository
import com.pilerks1.hdrrecorder.model.CameraPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Owns preset documents. The ViewModel serializes complete user operations. */
class PresetManager(private val repository: PresetRepository, private val reporter: ErrorReporter) {
    private val mutex = Mutex()

    data class Listing(val presets: List<CameraPreset>)

    suspend fun list(): Listing = mutex.withLock {
        val presets = repository.listIds().mapNotNull { id ->
            try {
                readDocument(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reporter.report(IssueCode.PRESET_UNREADABLE, "readPresetDocument", error)
                null
            }
        }.sortedWith(compareBy<CameraPreset, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id })
        Listing(presets)
    }

    suspend fun read(id: String): CameraPreset = mutex.withLock { readDocument(id) }

    suspend fun save(preset: CameraPreset, create: Boolean = false) = mutex.withLock {
        require(preset.name.isNotBlank() && preset.name == preset.name.trim()) { "Enter a preset name" }
        if (create) require(repository.read(preset.id) == null) { "Preset already exists" }
        else require(repository.read(preset.id) != null) { "Preset no longer exists" }
        // Validate our own output too, before replacing any file.
        val json = withContext(Dispatchers.Default) {
            PresetCodec.encode(preset).also { PresetCodec.decode(it) }
        }
        repository.write(preset.id, json)
    }

    suspend fun delete(id: String) = mutex.withLock { repository.delete(id) }

    private suspend fun readDocument(id: String): CameraPreset {
        val json = repository.read(id) ?: error("Preset no longer exists")
        return withContext(Dispatchers.Default) {
            PresetCodec.decode(json).also { require(it.id == id) { "Preset ID does not match filename" } }
        }
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
