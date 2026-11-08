package com.pilerks1.hdrrecorder.data.storage

import android.net.Uri
import androidx.core.net.toUri

sealed interface RecordingDestination {
    data object Default : RecordingDestination
    data class DocumentTree(val uri: Uri) : RecordingDestination

    fun toStorageUri(): String? = when (this) {
        Default -> null
        is DocumentTree -> uri.toString()
    }

    companion object {
        fun fromStorageUri(uri: String?): RecordingDestination =
            if (uri == null) Default else DocumentTree(uri.toUri())
    }
}
