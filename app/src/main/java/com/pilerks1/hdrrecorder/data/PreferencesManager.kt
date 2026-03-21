package com.pilerks1.hdrrecorder.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persisted data across app sessions.
 * This class is designed to easily expand in the future for saving recording "presets".
 */
class PreferencesManager(context: Context) {
    // We use MODE_PRIVATE so only our app can access this preferences file
    private val prefs: SharedPreferences = context.getSharedPreferences("hdr_recorder_prefs", Context.MODE_PRIVATE)

    var storageUri: String?
        get() = prefs.getString("storage_uri", null)
        set(value) = prefs.edit().putString("storage_uri", value).apply()

    // Example of how you can add presets later:
    // var currentPresetName: String?
    //     get() = prefs.getString("current_preset", "Default")
    //     set(value) = prefs.edit().putString("current_preset", value).apply()
}