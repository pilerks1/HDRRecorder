package com.pilerks1.hdrrecorder.ui.camera.controls.slider

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pilerks1.hdrrecorder.ui.camera.layout.AxisSpec

/** Auto/manual toggle for one control: Auto releases the value; manual holds the effective value. */
internal fun <T : Any> autoManualBinding(
    isManual: Boolean,
    effectiveValue: T?,
    onSet: (Boolean, T?) -> Unit
) = AutoManualSliderBinding(
    isManual = isManual,
    effectiveValue = effectiveValue,
    onToggleAuto = {
        if (isManual) onSet(false, null) else effectiveValue?.let { onSet(true, it) }
    },
    onManualValue = { onSet(true, it) }
)

@Composable
internal fun <T : Any> AutoManualSlider(
    scale: SliderScale<T>,
    binding: AutoManualSliderBinding<T>,
    contentDescription: String,
    axis: AxisSpec,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onInteractionFinished: () -> Unit
) {
    val effectiveValue = binding.effectiveValue
    val hasValue = effectiveValue != null
    BaseSlider(
        value = effectiveValue?.let(scale::progress) ?: 0f,
        axis = axis,
        labelString = effectiveValue?.let(scale.formatValue) ?: CameraSliderScales.UNAVAILABLE_LABEL,
        contentDescription = contentDescription,
        ruler = if (hasValue) scale.ruler else scale.ruler.copy(ticks = emptyList(), markers = emptyList()),
        layoutLabels = if (hasValue) scale.layoutLabels else listOf(CameraSliderScales.UNAVAILABLE_LABEL),
        onValueChange = { binding.onManualValue(scale.value(it)) },
        onValueChangeFinished = onInteractionFinished,
        modifier = modifier,
        sliderEnabled = enabled && hasValue,
        primaryButtonEnabled = enabled && (hasValue || binding.isManual),
        primaryButtonText = if (binding.isManual) "MAN" else "AUTO",
        modeKey = binding.isManual,
        primaryButtonColor = if (binding.isManual) Color.DarkGray else Color.White,
        primaryButtonTextColor = if (binding.isManual) Color.White else Color.Black,
        onPrimaryButtonClick = {
            binding.onToggleAuto()
            onInteractionFinished()
        }
    )
}

data class AutoManualSliderBinding<T : Any>(
    val isManual: Boolean,
    val effectiveValue: T?,
    val onToggleAuto: () -> Unit,
    val onManualValue: (T) -> Unit
)
