package com.pilerks1.hdrrecorder.ui.settingsUI

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Range
import com.pilerks1.hdrrecorder.model.PreviewSettings
import com.pilerks1.hdrrecorder.model.PreviewOptions
import com.pilerks1.hdrrecorder.model.PreviewDynamicRange

@Composable
fun PreviewSection(
    settings: PreviewSettings,
    options: PreviewOptions,
    error: String?,
    enabled: Boolean,
    onFrameRateChange: (Range<Int>?) -> Unit,
    onDynamicRangeChange: (PreviewDynamicRange?) -> Unit,
    isRespectDisplayCutoutsEnabled: Boolean,
    onRespectDisplayCutoutsChange: (Boolean) -> Unit,
    isForceDisplaySdrEnabled: Boolean,
    onForceDisplaySdrChange: (Boolean) -> Unit
) {
    Text(
        text = "Preview",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    fun frameRateLabel(range: Range<Int>): String =
        if (range.lower == range.upper) "${range.lower} FPS" else "${range.lower}–${range.upper} FPS"

    RecordingChoiceRow(
        label = "Frame rate",
        value = settings.frameRate?.let(::frameRateLabel) ?: "Auto",
        choices = listOf(null to "Auto") + options.frameRates.map { it to frameRateLabel(it) },
        onSelect = onFrameRateChange,
        enabled = enabled
    )
    Spacer(Modifier.height(12.dp))
    RecordingChoiceRow(
        label = "Dynamic range",
        value = settings.dynamicRange?.displayName ?: "Auto",
        choices = listOf(null to "Auto") + options.dynamicRanges.map { it to it.displayName },
        onSelect = onDynamicRangeChange,
        enabled = enabled
    )
    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp))
    }

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
