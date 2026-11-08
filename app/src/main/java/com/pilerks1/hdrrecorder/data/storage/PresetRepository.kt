package com.pilerks1.hdrrecorder.data.storage

import android.content.Context
import java.io.File

/** App-private, opaque JSON documents owned by PresetManager. */
class PresetRepository(context: Context) {
    private val store = PrivateDocumentStore(File(context.filesDir, "presets"))

    suspend fun write(id: String, json: String) = store.write(id, json)
    suspend fun read(id: String): String? = store.read(id)
    suspend fun listIds(): List<String> = store.listIds()
    suspend fun delete(id: String) = store.delete(id)
}
