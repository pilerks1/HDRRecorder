package com.pilerks1.hdrrecorder.data.storage

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App-private, opaque JSON documents with atomic replacement. Each directory has one owner
 * (PresetManager or the compatibility scan), and that owner serializes its operations.
 *
 * android.util.AtomicFile is not used because its finishWrite does not surface rename failures.
 */
class PrivateDocumentStore(private val directory: File) {

    suspend fun write(id: String, document: String): Unit = withContext(Dispatchers.IO) {
        val file = documentFile(id)
        Files.createDirectories(directory.toPath())
        val temporary = File(file.path + ".new")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(document.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (error: Exception) {
            try {
                Files.deleteIfExists(temporary.toPath())
            } catch (cleanupError: Exception) {
                error.addSuppressed(cleanupError)
            }
            throw error
        }
    }

    suspend fun read(id: String): String? = withContext(Dispatchers.IO) {
        try {
            String(Files.readAllBytes(documentFile(id).toPath()), Charsets.UTF_8)
        } catch (_: NoSuchFileException) {
            null
        }
    }

    suspend fun listIds(): List<String> = withContext(Dispatchers.IO) {
        if (Files.notExists(directory.toPath())) return@withContext emptyList()
        Files.newDirectoryStream(directory.toPath(), "*.json").use { entries ->
            entries.map { it.fileName.toString().removeSuffix(".json") }
                .filter { ID_PATTERN.matches(it) }.sorted()
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val file = documentFile(id)
        Files.deleteIfExists(File(file.path + ".new").toPath())
        Files.deleteIfExists(file.toPath())
        Unit
    }

    private fun documentFile(id: String): File {
        require(ID_PATTERN.matches(id)) { "Document ID must contain only letters, digits, hyphens, or underscores" }
        return File(directory, "$id.json")
    }

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
