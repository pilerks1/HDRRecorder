package com.pilerks1.hdrrecorder.ui.manualcontrols

import android.util.Range
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
import com.pilerks1.hdrrecorder.ui.ManualControlsState

/** Describes one grid button: what it shows, whether it's active, and what it does. */
private data class ButtonSpec(
    val label: String,
    val isActive: Boolean,
    val onClick: () -> Unit
)

@Composable
fun ManualControlsGrid(
    state: ManualControlsState,
    caps: CameraCapabilities?,
    resLabel: String,
    isRecording: Boolean,
    onCycleResolution: () -> Unit,
    onToggleSlider: (String) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    val secondaryEdgePadding = 8.dp
    val secondaryButtonDimension = 52.dp

    // Build the data list of available buttons based on capabilities.
    val buttons = buildList {
        if (!isRecording) add(ButtonSpec(resLabel, isActive = false, onClick = onCycleResolution))
        if (caps?.ssRangeNanos != null) add(ButtonSpec("SS", state.activeSlider == "SS") { onToggleSlider("SS") })
        if ((caps?.focusMinDistanceDiopters ?: 0f) > 0f) add(ButtonSpec("FOC", state.activeSlider == "FOC") { onToggleSlider("FOC") })
        if (caps?.supportsCCT == true) add(ButtonSpec("WB", state.activeSlider == "WB") { onToggleSlider("WB") })
        if (caps?.fpsRanges?.isNotEmpty() == true) add(ButtonSpec("FPS", state.activeSlider == "FPS") { onToggleSlider("FPS") })
        if (caps?.isoRange != null) add(ButtonSpec("ISO", state.activeSlider == "ISO") { onToggleSlider("ISO") })
        if (caps?.evRange != null) add(ButtonSpec("EV", state.activeSlider == "EV") { onToggleSlider("EV") })
        if (caps?.supportsCCT == true) add(ButtonSpec("TINT", state.activeSlider == "TINT") { onToggleSlider("TINT") })
    }

    if (isLandscape) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.padding(end = secondaryEdgePadding).fillMaxHeight()
        ) {
            // Split into two columns dynamically, centered.
            val col1Count = (buttons.size + 1) / 2
            val buttonModifier = Modifier.fillMaxWidth().height(secondaryButtonDimension)

            Column(modifier = Modifier.width(secondaryButtonDimension).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                for (i in 0 until col1Count) {
                    buttons[i].let { GridButton(it.label, it.isActive, buttonModifier, it.onClick) }
                }
            }
            Column(modifier = Modifier.width(secondaryButtonDimension).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                for (i in col1Count until buttons.size) {
                    buttons[i].let { GridButton(it.label, it.isActive, buttonModifier, it.onClick) }
                }
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier.padding(bottom = secondaryEdgePadding).fillMaxWidth()
        ) {
            val row1Count = (buttons.size + 1) / 2
            val buttonModifier = Modifier.fillMaxHeight().width(secondaryButtonDimension)

            Row(modifier = Modifier.height(secondaryButtonDimension).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 0 until row1Count) {
                    buttons[i].let { GridButton(it.label, it.isActive, buttonModifier, it.onClick) }
                }
            }
            Row(modifier = Modifier.height(secondaryButtonDimension).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in row1Count until buttons.size) {
                    buttons[i].let { GridButton(it.label, it.isActive, buttonModifier, it.onClick) }
                }
            }
        }
    }
}

@Composable
fun GridButton(label: String, isActive: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Text(
            text = label,
            color = if (isActive) Color.Yellow else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ActiveSliderPanel(
    state: ManualControlsState,
    caps: CameraCapabilities?,
    isRecording: Boolean,
    isLandscape: Boolean,
    onClose: () -> Unit,
    // Callbacks for setting state directly (will map to ViewModel)
    onSetIso: (Boolean, Int?) -> Unit,
    onSetSs: (Boolean, Long?) -> Unit,
    onSetEv: (Boolean, Int?) -> Unit,
    onSetNightMode: (Boolean) -> Unit,
    onSetFocus: (Boolean, Float?) -> Unit,
    onSetWb: (Boolean, Int?, Int?) -> Unit,
    onSetFps: (Boolean, Range<Int>?) -> Unit,
    autoFps: Int,
    onCycleFps: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.activeSlider == null) return

    val screenEdgePadding = 8.dp
    val innerThicknessPadding = if (isLandscape) 8.dp else 4.dp
    val sliderPanelThickness = if (isLandscape) 128.dp else 64.dp

    val panelModifier = if (isLandscape) {
        modifier
            .fillMaxHeight()
            .width(sliderPanelThickness)
            .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.ui.graphics.RectangleShape)
            .padding(vertical = screenEdgePadding)
            .padding(start = innerThicknessPadding)
    } else {
        modifier
            .fillMaxWidth()
            .height(sliderPanelThickness)
            .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.ui.graphics.RectangleShape)
            .padding(horizontal = screenEdgePadding)
            .padding(top = innerThicknessPadding)
    }

    Box(modifier = panelModifier, contentAlignment = Alignment.Center) {
        when (state.activeSlider) {
            "ISO" -> {
                StandardSlider(
                    label = "ISO",
                    progress = if (state.isoValue != null) SliderMath.mapIsoToProgress(state.isoValue, caps) else 0.5f,
                    isManual = state.isManualIso,
                    isLandscape = isLandscape,
                    caps = caps,
                    onToggleAuto = { onSetIso(!state.isManualIso, null) },
                    onValueChange = { onSetIso(true, SliderMath.mapProgressToIso(it, caps)) }
                )
            }
            "SS" -> {
                StandardSlider(
                    label = "SS",
                    progress = if (state.ssValueNanos != null) SliderMath.mapShutterToProgress(state.ssValueNanos, caps) else 0.5f,
                    isManual = state.isManualSs,
                    isLandscape = isLandscape,
                    caps = caps,
                    onToggleAuto = { onSetSs(!state.isManualSs, null) },
                    onValueChange = { onSetSs(true, SliderMath.mapProgressToShutter(it, caps)) }
                )
            }
            "EV" -> {
                val progress = SliderMath.mapEvIndexToProgress(state.evValueIndex, caps)
                val aeIsOff = when {
                    state.isManualSs && state.isManualIso -> true
                    state.isManualSs -> caps?.supportsShutterPriorityAe != true
                    state.isManualIso -> caps?.supportsIsoPriorityAe != true
                    else -> false
                }
                val isManualEv = state.evValueIndex != 0
                BaseSlider(
                    label = "EV",
                    value = progress,
                    isLandscape = isLandscape,
                    caps = caps,
                    onValueChange = { onSetEv(true, SliderMath.mapProgressToEvIndex(it, caps)) },
                    primaryButtonText = "RES",
                    primaryButtonEnabled = !aeIsOff && isManualEv,
                    primaryButtonColor = if (isManualEv) Color.White else Color.DarkGray,
                    primaryButtonTextColor = if (isManualEv && !aeIsOff) Color.Black else Color.Gray,
                    onPrimaryButtonClick = { onSetEv(true, 0) },
                    secondaryButtonText = if (caps?.supportsNightMode == true) "NHT" else null,
                    secondaryButtonEnabled = !aeIsOff,
                    secondaryButtonColor = if (state.isNightModeAeEnabled && !aeIsOff) Color.Yellow else Color.DarkGray,
                    secondaryButtonTextColor = if (state.isNightModeAeEnabled && !aeIsOff) Color.Black else Color.LightGray,
                    onSecondaryButtonClick = if (caps?.supportsNightMode == true) { { onSetNightMode(!state.isNightModeAeEnabled) } } else null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            "FOC" -> {
                StandardSlider(
                    label = "Focus",
                    progress = if (state.focusDistanceDiopters != null) (state.focusDistanceDiopters / (caps?.focusMinDistanceDiopters ?: 10f)).coerceIn(0f, 1f) else 0f,
                    isManual = state.isManualFocus,
                    isLandscape = isLandscape,
                    caps = caps,
                    onToggleAuto = { onSetFocus(!state.isManualFocus, null) },
                    onValueChange = { onSetFocus(true, SliderMath.mapProgressToFocus(it, caps)) }
                )
            }
            "WB" -> {
                StandardSlider(
                    label = "WB",
                    progress = if (state.wbTemp != null) SliderMath.mapWbTempToProgress(state.wbTemp, caps) else 0.5f,
                    isManual = state.isManualWb,
                    isLandscape = isLandscape,
                    caps = caps,
                    onToggleAuto = { onSetWb(!state.isManualWb, null, state.wbTint) },
                    onValueChange = { onSetWb(true, SliderMath.mapProgressToWbTemp(it, caps), state.wbTint) }
                )
            }
            "TINT" -> {
                StandardSlider(
                    label = "Tint",
                    progress = if (state.wbTint != null) SliderMath.mapWbTintToProgress(state.wbTint) else 0.5f,
                    isManual = state.isManualWb,
                    isLandscape = isLandscape,
                    caps = caps,
                    onToggleAuto = { onSetWb(!state.isManualWb, state.wbTemp, null) },
                    onValueChange = { onSetWb(true, state.wbTemp, SliderMath.mapProgressToWbTint(it)) }
                )
            }
            "FPS" -> {
                // If not manual, default to the first available range or a standard 30fps fallback
                val defaultRange = Range(30, 30)
                val currentRange = state.fpsRange ?: defaultRange
                
                FpsSlider(
                    isManualFps = state.isManualFps,
                    currentRange = currentRange.lower.toFloat()..currentRange.upper.toFloat(),
                    autoFps = autoFps,
                    isRecording = isRecording,
                    caps = caps,
                    onToggleAuto = onCycleFps,
                    onValueChange = { newRange ->
                        val intRange = Range(newRange.start.roundToInt(), newRange.endInclusive.roundToInt())
                        onSetFps(true, intRange)
                    },
                    isLandscape = isLandscape
                )
            }
        }
    }
}

@Composable
fun StandardSlider(
    label: String,
    progress: Float,
    isManual: Boolean,
    isLandscape: Boolean,
    caps: CameraCapabilities?,
    onToggleAuto: () -> Unit,
    onValueChange: (Float) -> Unit
) {
    BaseSlider(
        label = label,
        value = progress,
        isLandscape = isLandscape,
        caps = caps,
        onValueChange = onValueChange,
        primaryButtonText = if (isManual) "MAN" else "AUTO",
        primaryButtonColor = if (isManual) Color.DarkGray else Color.White,
        primaryButtonTextColor = if (isManual) Color.White else Color.Black,
        onPrimaryButtonClick = onToggleAuto,
        modifier = Modifier.fillMaxSize()
    )
}
