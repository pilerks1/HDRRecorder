package com.pilerks1.hdrrecorder.data.storage

import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode

import android.content.Context

/** Storage entry point. Feature managers retain ownership of document contents. */
class StorageManager(context: Context, reporter: ErrorReporter) {
    private val appContext = context.applicationContext

    val presets = PresetRepository(appContext)
    val compatibilityReports = CompatibilityReportRepository(appContext)
    val recordings = RecordingStorageManager(appContext, reporter)
}
