package com.pilerks1.hdrrecorder.data

import com.pilerks1.hdrrecorder.data.storage.PresetRepository
import com.pilerks1.hdrrecorder.model.CameraPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Owns preset documents. The ViewModel serializes complete user operations. */
class PresetManager(private val repository: PresetRepository) {
    private val mutex = Mutex()

    data class Listing(val presets: List<CameraPreset>, val errors: List<String>)

    suspend fun list(): Listing = mutex.withLock {
        val errors = mutableListOf<String>()
        val presets = repository.listIds().mapNotNull { id ->
            try {
                readDocument(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errors.add("Could not read preset $id: "+(error.message ?: "invalid document"))
                null
            }
        }.sortedWith(compareBy<CameraPreset, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id })
        Listing(presets, errors)
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
