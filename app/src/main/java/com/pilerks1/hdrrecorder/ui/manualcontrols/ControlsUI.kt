package com.pilerks1.hdrrecorder.ui.manualcontrols

import android.util.Range
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.pilerks1.hdrrecorder.ui.ManualControl
import com.pilerks1.hdrrecorder.ui.ManualControlsState
import com.pilerks1.hdrrecorder.model.CameraTelemetry
import kotlinx.coroutines.flow.StateFlow

/** Shared edge and action-group spacing for the manual control strip. */
val SecondaryControlsSpacing = 8.dp

/** Describes one grid button: what it shows, whether it's active, and what it does. */
private data class ButtonSpec(
    val label: String,
    val isActive: Boolean,
    val onClick: () -> Unit
)

private data class EffectiveWhiteBalance(
    val temperatureKelvin: Int,
    val tint: Int
)

/**
 * CCT controls are only shown when capability detection reports support. This resolves the
 * current device-reported value once, with a capability-based default for the first frame.
 */
private fun resolveEffectiveWhiteBalance(
    state: ManualControlsState,
    telemetry: CameraTelemetry,
    caps: CameraCapabilities?
): EffectiveWhiteBalance = EffectiveWhiteBalance(
    temperatureKelvin = telemetry.cctTemperatureKelvin
        ?: state.wbTemp
        ?: caps?.cctTemperatureRange?.let { (it.lower + it.upper) / 2 }
        ?: 5000,
    tint = telemetry.cctTint ?: state.wbTint ?: 0
)

@Composable
fun ManualControlsGrid(
    state: ManualControlsState,
    caps: CameraCapabilities?,
    resLabel: String,
    isRecording: Boolean,
    onCycleResolution: () -> Unit,
    onToggleSlider: (ManualControl) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    val secondaryButtonDimension = 52.dp

    // Build the data list of available buttons based on capabilities.
    val sliderButton = { control: ManualControl ->
        ButtonSpec(control.gridLabel, state.activeSlider == control) { onToggleSlider(control) }
    }
    val buttons = buildList {
        if (!isRecording) add(ButtonSpec(resLabel, isActive = false, onClick = onCycleResolution))
        if (caps?.ssRangeNanos != null) add(sliderButton(ManualControl.SHUTTER))
        if ((caps?.focusMinDistanceDiopters ?: 0f) > 0f) add(sliderButton(ManualControl.FOCUS))
        if (caps?.supportsCCT == true) add(sliderButton(ManualControl.WHITE_BALANCE))
        if (caps?.fpsRanges?.isNotEmpty() == true) add(sliderButton(ManualControl.FPS))
        if (caps?.isoRange != null) add(sliderButton(ManualControl.ISO))
        if (caps?.evRange != null) add(sliderButton(ManualControl.EXPOSURE_COMPENSATION))
        if (caps?.supportsCCT == true) add(sliderButton(ManualControl.TINT))
    }

    if (isLandscape) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.padding(end = SecondaryControlsSpacing).fillMaxHeight()
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
            modifier = modifier.padding(bottom = SecondaryControlsSpacing).fillMaxWidth()
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
    cameraTelemetry: StateFlow<CameraTelemetry>,
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
    val activeControl = state.activeSlider ?: return
    val telemetry = cameraTelemetry.collectAsState().value

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
        when (activeControl) {
            ManualControl.ISO -> {
                val effectiveIso = telemetry.iso ?: state.isoValue ?: caps?.isoRange?.let { (it.lower + it.upper) / 2 }
                StandardSlider(
                    control = ManualControl.ISO,
                    progress = SliderMath.mapIsoToProgress(if (state.isManualIso) state.isoValue ?: effectiveIso ?: 100 else effectiveIso ?: 100, caps),
                    isManual = state.isManualIso,
                    isLandscape = isLandscape,
                    caps = caps,
                    onToggleAuto = {
                        if (state.isManualIso) onSetIso(false, null) else onSetIso(true, effectiveIso)
                    },
                    onValueChange = { onSetIso(true, SliderMath.mapProgressToIso(it, caps)) }
                )
            }
            ManualControl.SHUTTER -> {
                val effectiveShutter = telemetry.shutterNanos ?: state.ssValueNanos ?: caps?.ssRangeNanos?.let { (it.lower + it.upper) / 2 }
                StandardSlider(
                    control = ManualControl.SHUTTER,
                    progress = SliderMath.mapShutterToProgress(if (state.isManualSs) state.ssValueNanos ?: effectiveShutter ?: 1_000_000L else effectiveShutter ?: 1_000_000L, caps),
                    isManual = state.isManualSs,
                    isLandscape = isLandscape,
                    caps = caps,
                    onToggleAuto = {
                        if (state.isManualSs) onSetSs(false, null) else onSetSs(true, effectiveShutter)
                    },
                    onValueChange = { onSetSs(true, SliderMath.mapProgressToShutter(it, caps)) }
                )
            }
            ManualControl.EXPOSURE_COMPENSATION -> {
                val progress = SliderMath.mapEvIndexToProgress(state.evValueIndex, caps)
                val aeIsOff = when {
                    state.isManualSs && state.isManualIso -> true
                    state.isManualSs -> caps?.supportsShutterPriorityAe != true
                    state.isManualIso -> caps?.supportsIsoPriorityAe != true
                    else -> false
                }
                val isManualEv = state.evValueIndex != 0
                BaseSlider(
                    control = ManualControl.EXPOSURE_COMPENSATION,
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
            ManualControl.FOCUS -> {
                val effectiveFocus = telemetry.focusDistanceDiopters
                    ?: state.focusDistanceDiopters
                    ?: 0f
                StandardSlider(
                    control = ManualControl.FOCUS,
                    progress = ((if (state.isManualFocus) state.focusDistanceDiopters ?: effectiveFocus else effectiveFocus) /
                        (caps?.focusMinDistanceDiopters ?: 10f)).coerceIn(0f, 1f),
                    isManual = state.isManualFocus,
                    isLandscape = isLandscape,
                    caps = caps,
                    onToggleAuto = {
                        if (state.isManualFocus) onSetFocus(false, null) else onSetFocus(true, effectiveFocus)
                    },
                    onValueChange = { onSetFocus(true, SliderMath.mapProgressToFocus(it, caps)) }
                )
            }
            ManualControl.WHITE_BALANCE -> {
                val effectiveWhiteBalance = resolveEffectiveWhiteBalance(state, telemetry, caps)
                StandardSlider(
                    control = ManualControl.WHITE_BALANCE,
                    progress = SliderMath.mapWbTempToProgress(
                        if (state.isManualWb) state.wbTemp ?: effectiveWhiteBalance.temperatureKelvin
                        else effectiveWhiteBalance.temperatureKelvin,
                        caps
                    ),
                    isManual = state.isManualWb,
                    isLandscape = isLandscape,
                    caps = caps,
                    onToggleAuto = {
                        if (state.isManualWb) onSetWb(false, null, null)
                        else onSetWb(true, effectiveWhiteBalance.temperatureKelvin, effectiveWhiteBalance.tint)
                    },
                    onValueChange = {
                        onSetWb(true, SliderMath.mapProgressToWbTemp(it, caps), effectiveWhiteBalance.tint)
                    }
                )
            }
            ManualControl.TINT -> {
                val effectiveWhiteBalance = resolveEffectiveWhiteBalance(state, telemetry, caps)
                StandardSlider(
                    control = ManualControl.TINT,
                    progress = SliderMath.mapWbTintToProgress(
                        if (state.isManualWb) state.wbTint ?: effectiveWhiteBalance.tint
                        else effectiveWhiteBalance.tint
                    ),
                    isManual = state.isManualWb,
                    isLandscape = isLandscape,
                    caps = caps,
                    onToggleAuto = {
                        if (state.isManualWb) onSetWb(false, null, null)
                        else onSetWb(true, effectiveWhiteBalance.temperatureKelvin, effectiveWhiteBalance.tint)
                    },
                    onValueChange = {
                        onSetWb(
                            true,
                            state.wbTemp ?: effectiveWhiteBalance.temperatureKelvin,
                            SliderMath.mapProgressToWbTint(it)
                        )
                    }
                )
            }
            ManualControl.FPS -> {
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
    control: ManualControl,
    progress: Float,
    isManual: Boolean,
    isLandscape: Boolean,
    caps: CameraCapabilities?,
    onToggleAuto: () -> Unit,
    onValueChange: (Float) -> Unit
) {
    BaseSlider(
        control = control,
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
