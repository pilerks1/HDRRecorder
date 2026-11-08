package com.pilerks1.hdrrecorder.data.storage

import android.content.Context
import java.io.File

/** Keeps the latest opaque JSON report without depending on a compatibility checker. */
class CompatibilityReportRepository(context: Context) {
    private val store = PrivateDocumentStore(File(context.filesDir, "compatibility"))

    suspend fun write(json: String) = store.write("latest", json)
    suspend fun read(): String? = store.read("latest")
    suspend fun delete() = store.delete("latest")
}
