package com.pilerks1.hdrrecorder.data

import android.content.Context
import android.content.SharedPreferences
import com.pilerks1.hdrrecorder.model.VideoEncoding
import com.pilerks1.hdrrecorder.model.GammaCurve
import com.pilerks1.hdrrecorder.model.Resolution
import com.pilerks1.hdrrecorder.model.RecordingAspectRatio
import com.pilerks1.hdrrecorder.ui.CameraUiState
import org.json.JSONObject
import androidx.core.content.edit

/**
 * Manages persisted data across app sessions.
 * Now acts as the unified storage engine for Camera Presets.
 */
class PreferencesManager(context: Context) {
    // We use MODE_PRIVATE so only our app can access this preferences file
    private val prefs: SharedPreferences = context.getSharedPreferences("hdr_recorder_prefs", Context.MODE_PRIVATE)

    var storageUri: String?
        get() = prefs.getString("storage_uri", null)
        set(value) = prefs.edit { putString("storage_uri", value) }

    var currentPresetName: String
        get() = prefs.getString("current_preset", "Default") ?: "Default"
        set(value) = prefs.edit { putString("current_preset", value) }

    fun getPresetNames(): Set<String> {
        return prefs.getStringSet("preset_names", setOf("Default")) ?: setOf("Default")
    }

    fun hasPreset(name: String): Boolean {
        return prefs.contains("preset_$name")
    }

    fun savePreset(name: String, state: CameraUiState) {
        val json = JSONObject().apply {
            put("fps", state.selectedFps)
            put("res", state.selectedResolution.storageId)
            put("aspect", state.selectedAspectRatio.storageId)
            put("encoding", state.encoding.storageId)
            put("bitDepth", state.videoBitDepth)
            put("videoCodec", state.videoCodec ?: "")
            put("audioCodec", state.audioCodec ?: "")
            put("audioBitrate", state.audioBitrate)
            put("gamma", state.gammaCurve.storageId)
            put("nr", state.isNoiseReductionEnabled)
            put("bitrate", state.bitrate)
            put("stab", state.isStabilizationEnabled)
            put("forceSdr", state.isForceDisplaySdrEnabled)
            put("extendManualRanges", state.isExtendManualRangesEnabled)
            put("waitToLockAe", state.isWaitToLockAeEnabled)
            // Note: Storage URI remains global, so we don't bake it into individual presets.
        }
        prefs.edit { putString("preset_$name", json.toString()) }

        // Keep track of the preset name in the master list
        val names = getPresetNames().toMutableSet()
        names.add(name)
        prefs.edit { putStringSet("preset_names", names) }
    }

    fun loadPreset(name: String, defaultState: CameraUiState): CameraUiState {
        val str = prefs.getString("preset_$name", null) ?: return defaultState
        return try {
            val json = JSONObject(str)
            val resolution = Resolution.fromStorageId(json.optString("res", ""))
                ?: defaultState.selectedResolution
            val aspectRatio = RecordingAspectRatio.fromStorageId(json.optString("aspect", ""))
                ?: defaultState.selectedAspectRatio

            val legacySdr = json.optBoolean("sdr", false)
            val encoding = if (legacySdr) VideoEncoding.SDR else
                VideoEncoding.fromStorageId(json.optString("encoding", json.optString("color", "")))
                    ?: VideoEncoding.HLG
            val gamma = if (encoding.supportsGammaCurveSelection)
                GammaCurve.fromStorageId(json.optString("gamma", "")) ?: GammaCurve.AUTO
                else GammaCurve.AUTO
            defaultState.copy(
                selectedFps = json.optInt("fps", defaultState.selectedFps),
                selectedResolution = resolution,
                selectedAspectRatio = aspectRatio,
                encoding = encoding,
                videoBitDepth = if (legacySdr) 8 else json.optInt("bitDepth", 10),
                gammaCurve = gamma,
                videoCodec = json.optString("videoCodec", "").takeIf { it.isNotEmpty() },
                audioCodec = json.optString("audioCodec", "").takeIf { it.isNotEmpty() },
                audioBitrate = json.optString("audioBitrate", ""),
                isNoiseReductionEnabled = json.optBoolean("nr", defaultState.isNoiseReductionEnabled),
                bitrate = json.optString("bitrate", defaultState.bitrate),
                isStabilizationEnabled = json.optBoolean("stab", defaultState.isStabilizationEnabled),
                isForceDisplaySdrEnabled = json.optBoolean("forceSdr", defaultState.isForceDisplaySdrEnabled),
                isExtendManualRangesEnabled = json.optBoolean("extendManualRanges", false),
                isWaitToLockAeEnabled = json.optBoolean("waitToLockAe", true),
                currentPresetName = name
            )
        } catch (e: Exception) {
            defaultState
        }
    }

    fun deletePreset(name: String) {
        prefs.edit { remove("preset_$name") }
        val names = getPresetNames().toMutableSet()
        names.remove(name)
        prefs.edit { putStringSet("preset_names", names) }
    }

    fun deleteAllPresets() {
        val names = getPresetNames()
        prefs.edit {
            names.forEach { remove("preset_$it") }
            // Ensure "Default" remains
            putStringSet("preset_names", setOf("Default"))
        }
    }
}
