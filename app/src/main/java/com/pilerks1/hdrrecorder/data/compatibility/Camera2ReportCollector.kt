package com.pilerks1.hdrrecorder.data.compatibility

import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode

import android.hardware.camera2.CameraCharacteristics as C
import android.hardware.camera2.CameraMetadata as M
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Range
import com.pilerks1.hdrrecorder.data.camera.CameraCapabilitiesManager
import com.pilerks1.hdrrecorder.data.camera.core.Camera2Control
import com.pilerks1.hdrrecorder.data.camera.core.CameraMetadata
import com.pilerks1.hdrrecorder.model.CompatibilityStatus as S
import com.pilerks1.hdrrecorder.model.CompatibilityTable
import java.util.Locale

internal class Camera2ReportCollector(private val metadata: CameraMetadata, private val reporter: ErrorReporter) {
    val diagnostics = mutableListOf<String>()
    private fun <T> read(key: C.Key<T>): T? = try { metadata[key] } catch (error: Exception) {
        diagnostics += "${key.name}: ${error.javaClass.simpleName}"
        reporter.report(IssueCode.CAPABILITY_QUERY, "camera2Metadata", error, mapOf("query" to key.name), notify = false)
        null
    }

    fun collect(cameraId: String): List<CompatibilityTable> {
        val requestKeys = metadata.requestKeys
        val caps = CameraCapabilitiesManager.extractCapabilities(metadata, false)
        val hardware = listOf(
            listOf("Hardware level", read(C.INFO_SUPPORTED_HARDWARE_LEVEL)?.let(::hardwareLevel) ?: S.NOT_REPORTED.label),
            listOf("Camera ID", cameraId),
            listOf("Facing", read(C.LENS_FACING)?.let { when (it) {
                C.LENS_FACING_BACK -> "Back"; C.LENS_FACING_FRONT -> "Front"; else -> "External"
            } } ?: S.NOT_REPORTED.label),
            listOf("Physical IDs", metadata.physicalCameraIds.sorted().joinToString().ifEmpty { "—" }),
            listOf("Capabilities", modeNames("REQUEST_AVAILABLE_CAPABILITIES_", read(C.REQUEST_AVAILABLE_CAPABILITIES))),
            listOf("Sensor size", read(C.SENSOR_INFO_PHYSICAL_SIZE)?.let { "${it.width} × ${it.height} mm" } ?: S.NOT_REPORTED.label),
            listOf("Pixel dimensions", read(C.SENSOR_INFO_PIXEL_ARRAY_SIZE)?.let { "${it.width} × ${it.height}" } ?: S.NOT_REPORTED.label),
            listOf("Active array", read(C.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.let { "${it.width()} × ${it.height()} · ${it.left}, ${it.top}" } ?: S.NOT_REPORTED.label),
            listOf("Focal lengths", read(C.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString { "$it mm" } ?: S.NOT_REPORTED.label),
            listOf("Apertures", read(C.LENS_INFO_AVAILABLE_APERTURES)?.joinToString { "f/$it" } ?: S.NOT_REPORTED.label),
            listOf("Optical stabilization", modeNames("LENS_OPTICAL_STABILIZATION_MODE_", read(C.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)))
        )
        val aeModes = read(C.CONTROL_AE_AVAILABLE_MODES)
        val manualKeys = CaptureRequest.SENSOR_SENSITIVITY in requestKeys &&
            CaptureRequest.SENSOR_EXPOSURE_TIME in requestKeys && CaptureRequest.CONTROL_AE_MODE in requestKeys
        val fullManual = caps.supportsFullManualExposure && aeModes?.contains(M.CONTROL_AE_MODE_OFF) == true && manualKeys
        val priorityModes = if (Build.VERSION.SDK_INT >= 36) read(C.CONTROL_AE_AVAILABLE_PRIORITY_MODES) else null
        val isoPriority = Build.VERSION.SDK_INT >= 36 &&
            priorityModes?.contains(M.CONTROL_AE_PRIORITY_MODE_SENSOR_SENSITIVITY_PRIORITY) == true &&
            CaptureRequest.CONTROL_AE_PRIORITY_MODE in requestKeys && CaptureRequest.SENSOR_SENSITIVITY in requestKeys
        val shutterPriority = Build.VERSION.SDK_INT >= 36 &&
            priorityModes?.contains(M.CONTROL_AE_PRIORITY_MODE_SENSOR_EXPOSURE_TIME_PRIORITY) == true &&
            CaptureRequest.CONTROL_AE_PRIORITY_MODE in requestKeys && CaptureRequest.SENSOR_EXPOSURE_TIME in requestKeys
        val exposure = buildList {
            if (aeModes?.contains(M.CONTROL_AE_MODE_ON) == true) add(listOf("Auto", "Auto", "Auto"))
            if (fullManual) add(listOf("Manual", "Manual", "Manual"))
            if (isoPriority) add(listOf("ISO priority", "Manual", "Auto"))
            if (shutterPriority) add(listOf("Shutter priority", "Auto", "Manual"))
            if (read(C.CONTROL_AE_LOCK_AVAILABLE) == true) add(listOf("AE lock", "Locked", "Locked"))
        }
        val focus = read(C.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        val evRange = read(C.CONTROL_AE_COMPENSATION_RANGE)
        val evStep = read(C.CONTROL_AE_COMPENSATION_STEP)
        val ev = if (evRange != null && evStep != null) {
            "${decimal(evRange.lower * evStep.toDouble())}…${decimal(evRange.upper * evStep.toDouble())} EV · ${evStep} EV/step · [${evRange.lower}, ${evRange.upper}]"
        } else S.NOT_REPORTED.label
        val ranges = listOf(
            listOf("ISO", range(caps.isoRange)),
            listOf("Shutter", shutter(caps.ssRangeNanos)),
            listOf("Post-RAW boost", range(caps.postRawSensitivityBoostRange, "%")),
            listOf("EV", ev),
            listOf("Focus", focus?.let { "0…$it D" +
                (if (it > 0f) " · ∞…${decimal(1.0 / it)} m" else "") } ?: S.NOT_REPORTED.label),
            listOf("Manual AE FPS", caps.fpsRanges.joinToString { "[${it.lower}, ${it.upper}]" }.ifEmpty { S.NOT_REPORTED.label }),
            listOf("Frame duration", caps.maxFrameDurationNanos?.let(::time) ?: S.NOT_REPORTED.label),
            listOf("CCT", range(caps.cctTemperatureRange, "K")),
            listOf("Tint", S.NOT_REPORTED.label)
        )
        val processing = Camera2Control.entries.map { control ->
            listOf(controlName(control), modeNames(modePrefix(control), read(control.availableModesKey)))
        }.toMutableList()
        processing += listOf("White balance", modeNames("CONTROL_AWB_MODE_", read(C.CONTROL_AWB_AVAILABLE_MODES)))
        processing += listOf("Tonemapping", modeNames("TONEMAP_MODE_", read(C.TONEMAP_AVAILABLE_TONE_MAP_MODES)))
        return listOf(
            CompatibilityTable("Hardware", listOf("Field", "Value"), hardware),
            CompatibilityTable("Exposure", listOf("Mode", "ISO", "Shutter"), exposure),
            CompatibilityTable("Ranges", listOf("Control", "Reported range"), ranges),
            CompatibilityTable("Processing", listOf("Setting", "Reported modes"), processing),
            CompatibilityTable("Metering", listOf("Region", "Max regions"), listOf(
                listOf("AF", read(C.CONTROL_MAX_REGIONS_AF)?.toString() ?: S.NOT_REPORTED.label),
                listOf("AE", read(C.CONTROL_MAX_REGIONS_AE)?.toString() ?: S.NOT_REPORTED.label),
                listOf("AWB", read(C.CONTROL_MAX_REGIONS_AWB)?.toString() ?: S.NOT_REPORTED.label)
            ))
        )
    }

    private fun modePrefix(control: Camera2Control): String = when (control) {
        Camera2Control.COLOR_CORRECTION_ABERRATION_MODE -> "COLOR_CORRECTION_ABERRATION_MODE_"
        Camera2Control.CONTROL_AF_MODE -> "CONTROL_AF_MODE_"
        Camera2Control.DISTORTION_CORRECTION_MODE -> "DISTORTION_CORRECTION_MODE_"
        Camera2Control.EDGE_MODE -> "EDGE_MODE_"
        Camera2Control.HOT_PIXEL_MODE -> "HOT_PIXEL_MODE_"
        Camera2Control.NOISE_REDUCTION_MODE -> "NOISE_REDUCTION_MODE_"
        Camera2Control.SHADING_MODE -> "SHADING_MODE_"
    }

    private fun controlName(control: Camera2Control): String = when (control) {
        Camera2Control.COLOR_CORRECTION_ABERRATION_MODE -> "Aberration"
        Camera2Control.CONTROL_AF_MODE -> "Autofocus"
        Camera2Control.DISTORTION_CORRECTION_MODE -> "Distortion"
        Camera2Control.EDGE_MODE -> "Edge"
        Camera2Control.HOT_PIXEL_MODE -> "Hot pixel"
        Camera2Control.NOISE_REDUCTION_MODE -> "Noise reduction"
        Camera2Control.SHADING_MODE -> "Shading"
    }

    private fun hardwareLevel(level: Int): String = when (level) {
        C.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        C.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        C.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        C.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        C.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> level.toString()
    }

    private fun modeNames(prefix: String, values: IntArray?): String =
        values?.joinToString { modeName(prefix, it) }?.ifEmpty { "—" } ?: S.NOT_REPORTED.label

    private fun modeName(prefix: String, value: Int): String = M::class.java.fields.firstOrNull {
        it.type == Int::class.javaPrimitiveType && it.name.startsWith(prefix) && it.getInt(null) == value
    }?.name?.removePrefix(prefix)?.lowercase(Locale.ROOT)?.split('_')
        ?.joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) } ?: value.toString()

    private fun <T : Comparable<T>> range(value: Range<T>?, unit: String = ""): String =
        value?.let { "${it.lower}…${it.upper}${if (unit.isEmpty()) "" else " $unit"}" } ?: S.NOT_REPORTED.label
    private fun shutter(value: Range<Long>?): String = value?.let { "${time(it.lower)}…${time(it.upper)}" } ?: S.NOT_REPORTED.label
    private fun time(nanos: Long): String = if (nanos < 1_000_000_000L && nanos > 0) "1/${kotlin.math.round(1e9 / nanos).toLong()} s" else "${decimal(nanos / 1e9)} s"
    private fun decimal(value: Double): String = String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
}
