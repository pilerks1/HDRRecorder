package com.pilerks1.hdrrecorder.ui.settingsUI

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.util.Range
import com.pilerks1.hdrrecorder.model.PreviewSettings
import com.pilerks1.hdrrecorder.model.PreviewOptions
import com.pilerks1.hdrrecorder.model.PreviewDynamicRange

@Composable
fun PreviewSection(
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
    Spacer(Modifier.height(12.dp))
    RecordingChoiceRow(
        label = "Dynamic range",
        value = settings.dynamicRange?.displayName ?: "Auto",
        choices = listOf(null to "Auto") + options.dynamicRanges.map { it to it.displayName },
        onSelect = onDynamicRangeChange
    )

    Spacer(Modifier.height(16.dp))
    HdrSettingsSwitchRow(
        label = "Respect Display Cutouts",
        description = "Keeps the preview and camera controls clear of notches and camera holes",
        checked = isRespectDisplayCutoutsEnabled,
        onCheckedChange = onRespectDisplayCutoutsChange
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        Spacer(Modifier.height(16.dp))
        HdrSettingsSwitchRow(
            label = "Force Display SDR",
            description = "Limits HDR headroom (Android 15+)",
            checked = isForceDisplaySdrEnabled,
            onCheckedChange = onForceDisplaySdrChange
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}
