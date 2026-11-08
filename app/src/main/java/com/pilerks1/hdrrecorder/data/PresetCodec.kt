package com.pilerks1.hdrrecorder.data

import android.util.Range
import com.pilerks1.hdrrecorder.model.*
import com.pilerks1.hdrrecorder.data.camera.core.*
import org.json.JSONObject
import java.util.UUID

/** Version 1 only; no historical preset formats are accepted. */
internal object PresetCodec {
    fun encode(preset: CameraPreset): String = JSONObject().apply {
        put("schemaVersion", preset.schemaVersion)
        put("id", preset.id)
        put("name", preset.name)
        put("saveRecordingOptions", preset.saveRecordingOptions)
        put("saveStorageLocation", preset.saveStorageLocation)
        put("settings", encodeSettings(preset.settings))
        preset.recording?.let { put("recording", encodeRecording(it)) }
        if (preset.saveStorageLocation) put("storageUri", preset.storageUri ?: JSONObject.NULL)
    }.toString(2)

    fun decode(text: String): CameraPreset {
        val json = JSONObject(text)
        require(json.get("schemaVersion") == 1) { "Unsupported preset version" }
        val id = json.getString("id")
        require(UUID.fromString(id).toString() == id) { "Invalid preset ID" }
        val name = json.getString("name")
        require(name.isNotBlank() && name == name.trim()) { "Invalid preset name" }
        val recordingEnabled = json.getBoolean("saveRecordingOptions")
        val storageEnabled = json.getBoolean("saveStorageLocation")
        require(recordingEnabled == json.has("recording"))
        require(storageEnabled == json.has("storageUri"))
        return CameraPreset(
            id, name, decodeSettings(json.getJSONObject("settings")), recordingEnabled, storageEnabled,
            if (recordingEnabled) decodeRecording(json.getJSONObject("recording")) else null,
            if (storageEnabled && json.get("storageUri") != JSONObject.NULL) json.getString("storageUri") else null
        )
    }

    private fun JSONObject.nullableObject(key: String): JSONObject? =
        if (get(key) == JSONObject.NULL) null else getJSONObject(key)

    private fun encodeSettings(value: PresetSettings) = JSONObject().apply {
        put("videoCodec", value.videoCodec ?: JSONObject.NULL)
        put("audioCodec", value.audioCodec ?: JSONObject.NULL)
        put("audioBitrate", value.audioBitrate)
        put("videoBitDepth", value.videoBitDepth)
        put("encoding", value.encoding.storageId)
        put("bitrate", value.bitrate)
        put("isStabilizationEnabled", value.isStabilizationEnabled)
        put("isForceDisplaySdrEnabled", value.isForceDisplaySdrEnabled)
        put("isRespectDisplayCutoutsEnabled", value.isRespectDisplayCutoutsEnabled)
        put("isExtendManualRangesEnabled", value.isExtendManualRangesEnabled)
        put("isWaitToLockAeEnabled", value.isWaitToLockAeEnabled)
        put("gamma", JSONObject().apply {
            put("mode", value.gammaSettings.mode.name)
            put("value", value.gammaSettings.value.toDouble())
            put("curve", value.gammaSettings.curve.storageId)
            put("preset", value.gammaSettings.preset.name)
        })
        put("preview", JSONObject().apply {
            put("frameRate", value.previewSettings.frameRate?.let {
                JSONObject().put("lower", it.lower).put("upper", it.upper)
            } ?: JSONObject.NULL)
            put("dynamicRange", value.previewSettings.dynamicRange?.storageId ?: JSONObject.NULL)
        })
        put("camera2", JSONObject().apply {
            Camera2Control.entries.forEach { control ->
                val override = value.camera2Settings[control]
                put(control.name, JSONObject().put("enabled", override.enabled)
                    .put("mode", override.mode ?: JSONObject.NULL))
            }
        })
    }

    private fun decodeSettings(json: JSONObject): PresetSettings {
        val gamma = json.getJSONObject("gamma")
        val preview = json.getJSONObject("preview")
        val camera2 = json.getJSONObject("camera2")
        return PresetSettings(
            videoCodec = if (json.get("videoCodec") == JSONObject.NULL) null else json.getString("videoCodec"),
            audioCodec = if (json.get("audioCodec") == JSONObject.NULL) null else json.getString("audioCodec"),
            audioBitrate = json.getString("audioBitrate"),
            videoBitDepth = json.getInt("videoBitDepth"),
            encoding = requireNotNull(VideoEncoding.fromStorageId(json.getString("encoding"))),
            bitrate = json.getString("bitrate"),
            isStabilizationEnabled = json.getBoolean("isStabilizationEnabled"),
            isForceDisplaySdrEnabled = json.getBoolean("isForceDisplaySdrEnabled"),
            isRespectDisplayCutoutsEnabled = json.getBoolean("isRespectDisplayCutoutsEnabled"),
            isExtendManualRangesEnabled = json.getBoolean("isExtendManualRangesEnabled"),
            isWaitToLockAeEnabled = json.getBoolean("isWaitToLockAeEnabled"),
            gammaSettings = GammaSettings(
                GammaMode.valueOf(gamma.getString("mode")), gamma.getDouble("value").toFloat(),
                requireNotNull(GammaCurve.fromStorageId(gamma.getString("curve"))),
                GammaPreset.valueOf(gamma.getString("preset"))
            ),
            previewSettings = PreviewSettings(
                preview.nullableObject("frameRate")?.let { Range(it.getInt("lower"), it.getInt("upper")) },
                if (preview.get("dynamicRange") == JSONObject.NULL) null else
                    requireNotNull(PreviewDynamicRange.fromStorageId(preview.getString("dynamicRange")))
            ),
            camera2Settings = Camera2Settings(Camera2Control.entries.associateWith {
                val entry = camera2.getJSONObject(it.name)
                Camera2ModeOverride(entry.getBoolean("enabled"), if (entry.get("mode") == JSONObject.NULL) null else entry.getInt("mode"))
            })
        ).also {
            require(it.videoBitDepth in listOf(8, 10))
            require(it.bitrate.isEmpty() || Bitrate.parse(it.bitrate) != null)
            require(it.audioBitrate.isEmpty() || Bitrate.parseAudio(it.audioBitrate) != null)
            require(it.previewSettings.frameRate?.lower?.let { lower -> lower > 0 } != false)
            require(it.camera2Settings.overrides.values.none { v -> v.enabled && v.mode == null })
        }
    }

    private fun encodeRecording(value: PresetRecordingControls) = JSONObject().apply {
        put("selectedFps", value.selectedFps)
        put("selectedResolution", value.selectedResolution.storageId)
        put("selectedAspectRatio", value.selectedAspectRatio.storageId)
        put("zoomRatio", value.zoomRatio.toDouble())
        put("isManualIso", value.isManualIso)
        put("isoValue", value.isoValue ?: JSONObject.NULL)
        put("isManualPostRawSensitivity", value.isManualPostRawSensitivity)
        put("postRawSensitivityBoost", value.postRawSensitivityBoost ?: JSONObject.NULL)
        put("isManualSs", value.isManualSs)
        put("ssValueNanos", value.ssValueNanos ?: JSONObject.NULL)
        put("isManualEv", value.isManualEv)
        put("evValueIndex", value.evValueIndex)
        put("isLowLightBoostEnabled", value.isLowLightBoostEnabled)
        put("isManualFocus", value.isManualFocus)
        put("focusDistanceDiopters", value.focusDistanceDiopters ?: JSONObject.NULL)
        put("isManualWb", value.isManualWb)
        put("wbTemp", value.wbTemp ?: JSONObject.NULL)
        put("wbTint", value.wbTint ?: JSONObject.NULL)
        put("isManualFps", value.isManualFps)
        put("fpsRange", value.fpsRange?.let { JSONObject().put("lower", it.lower).put("upper", it.upper) } ?: JSONObject.NULL)
    }

    private fun decodeRecording(json: JSONObject) = PresetRecordingControls(
        selectedFps = json.getInt("selectedFps"),
        selectedResolution = requireNotNull(Resolution.fromStorageId(json.getString("selectedResolution"))),
        selectedAspectRatio = requireNotNull(RecordingAspectRatio.fromStorageId(json.getString("selectedAspectRatio"))),
        zoomRatio = json.getDouble("zoomRatio").toFloat(),
        isManualIso = json.getBoolean("isManualIso"),
        isoValue = if (json.get("isoValue") == JSONObject.NULL) null else json.getInt("isoValue"),
        isManualPostRawSensitivity = json.getBoolean("isManualPostRawSensitivity"),
        postRawSensitivityBoost = if (json.get("postRawSensitivityBoost") == JSONObject.NULL) null else json.getInt("postRawSensitivityBoost"),
        isManualSs = json.getBoolean("isManualSs"),
        ssValueNanos = if (json.get("ssValueNanos") == JSONObject.NULL) null else json.getLong("ssValueNanos"),
        isManualEv = json.getBoolean("isManualEv"),
        evValueIndex = json.getInt("evValueIndex"),
        isLowLightBoostEnabled = json.getBoolean("isLowLightBoostEnabled"),
        isManualFocus = json.getBoolean("isManualFocus"),
        focusDistanceDiopters = if (json.get("focusDistanceDiopters") == JSONObject.NULL) null else json.getDouble("focusDistanceDiopters").toFloat(),
        isManualWb = json.getBoolean("isManualWb"),
        wbTemp = if (json.get("wbTemp") == JSONObject.NULL) null else json.getInt("wbTemp"),
        wbTint = if (json.get("wbTint") == JSONObject.NULL) null else json.getInt("wbTint"),
        isManualFps = json.getBoolean("isManualFps"),
        fpsRange = json.nullableObject("fpsRange")?.let { Range(it.getInt("lower"), it.getInt("upper")) }
    ).also {
        require(it.selectedFps > 0 && it.zoomRatio.isFinite() && it.zoomRatio > 0f)
        require(it.isoValue == null || it.isoValue > 0)
        require(it.postRawSensitivityBoost == null || it.postRawSensitivityBoost > 0)
        require(it.ssValueNanos == null || it.ssValueNanos > 0)
        require(it.focusDistanceDiopters == null || (it.focusDistanceDiopters.isFinite() && it.focusDistanceDiopters >= 0f))
        require(it.wbTemp == null || it.wbTemp > 0)
        require(it.fpsRange == null || it.fpsRange.lower > 0)
        require(!it.isManualIso || it.isoValue != null)
        require(!it.isManualSs || it.ssValueNanos != null)
        require(!it.isManualPostRawSensitivity || it.postRawSensitivityBoost != null)
        require(!it.isManualFocus || it.focusDistanceDiopters != null)
        require(!it.isManualWb || (it.wbTemp != null && it.wbTint != null))
        require(!it.isManualFps || it.fpsRange != null)
    }
}
