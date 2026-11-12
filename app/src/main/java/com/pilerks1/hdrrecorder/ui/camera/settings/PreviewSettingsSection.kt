package com.pilerks1.hdrrecorder.ui.camera.settings

import android.os.Build
import android.util.Range
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pilerks1.hdrrecorder.camera.model.PreviewDynamicRange
import com.pilerks1.hdrrecorder.camera.model.PreviewOptions
import com.pilerks1.hdrrecorder.camera.model.PreviewSettings
import com.pilerks1.hdrrecorder.ui.style.SettingsDimensions

@Composable
fun PreviewSettingsSection(
    settings: PreviewSettings,
    options: PreviewOptions,
    onFrameRateChange: (Range<Int>?) -> Unit,
    onDynamicRangeChange: (PreviewDynamicRange?) -> Unit,
    isRespectDisplayCutoutsEnabled: Boolean,
    onRespectDisplayCutoutsChange: (Boolean) -> Unit,
    isForceDisplaySdrEnabled: Boolean,
    onForceDisplaySdrChange: (Boolean) -> Unit
) {
    SectionTitle("Preview")

    fun frameRateLabel(range: Range<Int>): String =
        if (range.lower == range.upper) "${range.lower} FPS" else "${range.lower}–${range.upper} FPS"

    RecordingChoiceRow(
        label = "Frame rate",
        value = settings.frameRate?.let(::frameRateLabel) ?: "Auto",
        choices = listOf(null to "Auto") + options.frameRates.map { it to frameRateLabel(it) },
        onSelect = onFrameRateChange
    )
    Spacer(Modifier.height(SettingsDimensions.controlSpacing))
    RecordingChoiceRow(
        label = "Dynamic range",
        value = settings.dynamicRange?.displayName ?: "Auto",
        choices = listOf(null to "Auto") + options.dynamicRanges.map { it to it.displayName },
        onSelect = onDynamicRangeChange
    )

    Spacer(Modifier.height(SettingsDimensions.sectionSpacing))
    HdrSettingsSwitchRow(
        label = "Respect Display Cutouts",
        description = "Keeps the preview and camera controls clear of notches and camera holes",
        checked = isRespectDisplayCutoutsEnabled,
        onCheckedChange = onRespectDisplayCutoutsChange
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        Spacer(Modifier.height(SettingsDimensions.sectionSpacing))
        HdrSettingsSwitchRow(
            label = "Force Display SDR",
            description = "Limits HDR headroom (Android 15+)",
            checked = isForceDisplaySdrEnabled,
            onCheckedChange = onForceDisplaySdrChange
        )
        Spacer(modifier = Modifier.height(SettingsDimensions.sectionSpacing))
    }
}
