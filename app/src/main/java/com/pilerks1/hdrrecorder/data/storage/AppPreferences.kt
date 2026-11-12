package com.pilerks1.hdrrecorder.data.storage

import android.content.Context
import androidx.core.content.edit

/** Small global preferences; preset documents belong to StorageManager. */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("hdr_recorder_prefs", Context.MODE_PRIVATE)

    var storageUri: String?
        get() = prefs.getString("storage_uri", null)
        set(value) = prefs.edit { putString("storage_uri", value) }

    var selectedPresetId: String?
        get() = prefs.getString("selected_preset_id", null)
        set(value) = prefs.edit { putString("selected_preset_id", value) }
}
