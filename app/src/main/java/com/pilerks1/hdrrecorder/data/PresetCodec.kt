package com.pilerks1.hdrrecorder.data

import android.util.Range
import com.pilerks1.hdrrecorder.model.*
import com.pilerks1.hdrrecorder.data.camera.core.*
import org.json.JSONObject
import java.util.UUID

/** Version 1 only; no historical preset formats are accepted. */
internal object PresetCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(preset: CameraPreset): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("id", preset.id)
        put("name", preset.name)
        put("saveRecordingOptions", preset.saveRecordingOptions)
        put("saveStorageLocation", preset.saveStorageLocation)
        put("settings", encodeSettings(preset.settings))
        preset.recording?.let { put("recording", encodeRecording(it)) }
        if (preset.saveStorageLocation) putNullable("storageUri", preset.storageUri)
    }.toString(2)

    fun decode(text: String): CameraPreset {
        val json = JSONObject(text)
        require(json.get("schemaVersion") == SCHEMA_VERSION) { "Unsupported preset version" }
        val id = json.getString("id")
        require(UUID.fromString(id).toString() == id) { "Invalid preset ID" }
        val name = json.getString("name")
        require(presetNameError(name) == null && name == name.trim()) { "Invalid preset name" }
        val recordingEnabled = json.getBoolean("saveRecordingOptions")
        val storageEnabled = json.getBoolean("saveStorageLocation")
        require(recordingEnabled == json.has("recording"))
        require(storageEnabled == json.has("storageUri"))
        return CameraPreset(
            id, name, decodeSettings(json.getJSONObject("settings")), recordingEnabled, storageEnabled,
            if (recordingEnabled) decodeRecording(json.getJSONObject("recording")) else null,
            if (storageEnabled) json.nullable("storageUri", JSONObject::getString) else null
        )
    }

    // --- Shared nullable and range codecs ---

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun <T> JSONObject.nullable(key: String, read: JSONObject.(String) -> T): T? =
        if (get(key) == JSONObject.NULL) null else read(key)

    private fun JSONObject.putRange(key: String, range: Range<Int>?) =
        putNullable(key, range?.let { JSONObject().put("lower", it.lower).put("upper", it.upper) })

    private fun JSONObject.range(key: String): Range<Int>? =
        nullable(key, JSONObject::getJSONObject)?.let { Range(it.getInt("lower"), it.getInt("upper")) }

    private fun encodeSettings(value: PresetSettings) = JSONObject().apply {
        putNullable("videoCodec", value.videoCodec)
        putNullable("audioCodec", value.audioCodec)
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
            putRange("frameRate", value.previewSettings.frameRate)
            putNullable("dynamicRange", value.previewSettings.dynamicRange?.storageId)
        })
        put("camera2", JSONObject().apply {
            Camera2Control.entries.forEach { control ->
                val override = value.camera2Settings[control]
                put(control.name, JSONObject().put("enabled", override.enabled).apply {
                    putNullable("mode", override.mode)
                })
            }
        })
    }

    private fun decodeSettings(json: JSONObject): PresetSettings {
        val gamma = json.getJSONObject("gamma")
        val preview = json.getJSONObject("preview")
        val camera2 = json.getJSONObject("camera2")
        return PresetSettings(
            videoCodec = json.nullable("videoCodec", JSONObject::getString),
            audioCodec = json.nullable("audioCodec", JSONObject::getString),
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
                preview.range("frameRate"),
                preview.nullable("dynamicRange", JSONObject::getString)
                    ?.let { requireNotNull(PreviewDynamicRange.fromStorageId(it)) }
            ),
            camera2Settings = Camera2Settings(Camera2Control.entries.associateWith {
                val entry = camera2.getJSONObject(it.name)
                Camera2ModeOverride(entry.getBoolean("enabled"), entry.nullable("mode", JSONObject::getInt))
            })
        ).also {
            require(it.videoBitDepth in listOf(8, 10))
            require(Bitrate.isValid(it.bitrate))
            require(Bitrate.isValidAudio(it.audioBitrate))
            require(it.previewSettings.frameRate?.lower?.let { lower -> lower > 0 } != false)
            require(it.camera2Settings.overrides.values.none { v -> v.enabled && v.mode == null })
        }
    }

    private fun encodeRecording(value: PresetRecordingControls) = JSONObject().apply {
        val controls = value.controls
        put("selectedFps", value.selectedFps)
        put("selectedResolution", value.selectedResolution.storageId)
        put("selectedAspectRatio", value.selectedAspectRatio.storageId)
        put("zoomRatio", value.zoomRatio.toDouble())
        put("isManualIso", controls.isManualIso)
        putNullable("isoValue", controls.isoValue)
        put("isManualPostRawSensitivity", controls.isManualPostRawSensitivity)
        putNullable("postRawSensitivityBoost", controls.postRawSensitivityBoost)
        put("isManualSs", controls.isManualSs)
        putNullable("ssValueNanos", controls.ssValueNanos)
        put("isManualEv", controls.isManualEv)
        put("evValueIndex", controls.evValueIndex)
        put("isLowLightBoostEnabled", controls.isLowLightBoostEnabled)
        put("isManualFocus", controls.isManualFocus)
        putNullable("focusDistanceDiopters", controls.focusDistanceDiopters)
        put("isManualWb", controls.isManualWb)
        putNullable("wbTemp", controls.wbTemp)
        putNullable("wbTint", controls.wbTint)
        put("isManualFps", controls.isManualFps)
        putRange("fpsRange", controls.fpsRange)
    }

    private fun decodeRecording(json: JSONObject): PresetRecordingControls {
        val controls = CameraControlValues(
            isManualIso = json.getBoolean("isManualIso"),
            isoValue = json.nullable("isoValue", JSONObject::getInt),
            isManualPostRawSensitivity = json.getBoolean("isManualPostRawSensitivity"),
            postRawSensitivityBoost = json.nullable("postRawSensitivityBoost", JSONObject::getInt),
            isManualSs = json.getBoolean("isManualSs"),
            ssValueNanos = json.nullable("ssValueNanos", JSONObject::getLong),
            isManualEv = json.getBoolean("isManualEv"),
            evValueIndex = json.getInt("evValueIndex"),
            isLowLightBoostEnabled = json.getBoolean("isLowLightBoostEnabled"),
            isManualFocus = json.getBoolean("isManualFocus"),
            focusDistanceDiopters = json.nullable("focusDistanceDiopters", JSONObject::getDouble)?.toFloat(),
            isManualWb = json.getBoolean("isManualWb"),
            wbTemp = json.nullable("wbTemp", JSONObject::getInt),
            wbTint = json.nullable("wbTint", JSONObject::getInt),
            isManualFps = json.getBoolean("isManualFps"),
            fpsRange = json.range("fpsRange")
        )
        return PresetRecordingControls(
            selectedFps = json.getInt("selectedFps"),
            selectedResolution = requireNotNull(Resolution.fromStorageId(json.getString("selectedResolution"))),
            selectedAspectRatio = requireNotNull(RecordingAspectRatio.fromStorageId(json.getString("selectedAspectRatio"))),
            zoomRatio = json.getDouble("zoomRatio").toFloat(),
            controls = controls
        ).also {
            require(it.selectedFps > 0 && it.zoomRatio.isFinite() && it.zoomRatio > 0f)
            require(controls.isoValue == null || controls.isoValue > 0)
            require(controls.postRawSensitivityBoost == null || controls.postRawSensitivityBoost > 0)
            require(controls.ssValueNanos == null || controls.ssValueNanos > 0)
            require(controls.focusDistanceDiopters == null ||
                (controls.focusDistanceDiopters.isFinite() && controls.focusDistanceDiopters >= 0f))
            require(controls.wbTemp == null || controls.wbTemp > 0)
            require(controls.fpsRange == null || controls.fpsRange.lower > 0)
            require(!controls.isManualIso || controls.isoValue != null)
            require(!controls.isManualSs || controls.ssValueNanos != null)
            require(!controls.isManualPostRawSensitivity || controls.postRawSensitivityBoost != null)
            require(!controls.isManualFocus || controls.focusDistanceDiopters != null)
            require(!controls.isManualWb || (controls.wbTemp != null && controls.wbTint != null))
            require(!controls.isManualFps || controls.fpsRange != null)
        }
    }
}
