package com.pilerks1.hdrrecorder

import android.app.Application
import android.content.Context
import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter

class HdrRecorderApplication : Application() {
    lateinit var errorReporter: ErrorReporter
        private set

    override fun onCreate() {
        super.onCreate()
        errorReporter = ErrorReporter(this)
        errorReporter.installCrashHandler()
    }
}

val Context.errorReporter: ErrorReporter
    get() = (applicationContext as HdrRecorderApplication).errorReporter
