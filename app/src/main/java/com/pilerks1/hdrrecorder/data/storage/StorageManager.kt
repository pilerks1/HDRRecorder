package com.pilerks1.hdrrecorder.data.storage

import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter

import android.content.Context
import java.io.File

/** Storage entry point. Feature managers retain ownership of document contents. */
class StorageManager(context: Context, reporter: ErrorReporter) {
    private val appContext = context.applicationContext

    val presets = PrivateDocumentStore(File(appContext.filesDir, "presets"))
    /** Holds the latest opaque compatibility report under [COMPATIBILITY_REPORT_ID]. */
    val compatibilityReports = PrivateDocumentStore(File(appContext.filesDir, "compatibility"))
    val recordings = RecordingStorageManager(appContext, reporter)

    companion object {
        const val COMPATIBILITY_REPORT_ID = "latest"
    }
}
