package com.pilerks1.hdrrecorder.ui.manualcontrols

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.ui.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.layout.AxisOverlay
import com.pilerks1.hdrrecorder.ui.layout.AxisStack

data class SliderAxisReservation(
    val start: Dp = 0.dp,
    val end: Dp = 0.dp
)

internal fun sliderAxisReservation(axis: AxisSpec, actionTouchExtent: Dp): SliderAxisReservation =
    if (axis.usesVerticalTrack) {
        SliderAxisReservation(end = actionTouchExtent)
    } else {
        SliderAxisReservation(start = actionTouchExtent)
    }

internal fun sliderActionGroupLength(actionCount: Int): Dp =
    SliderPaneStyle.actionButtonSize * actionCount.coerceAtLeast(0) +
        SliderPaneStyle.actionButtonGap * (actionCount - 1).coerceAtLeast(0)

@Composable
fun BaseSlider(
    value: Float,
    axis: AxisSpec,
    labelString: String,
    ticks: List<SliderTick>,
    layoutLabels: List<String>,
    onValueChange: (Float) -> Unit,
    primaryButtonText: String,
    modifier: Modifier = Modifier,
    sliderEnabled: Boolean = true,
    primaryButtonEnabled: Boolean = true,
    primaryButtonColor: Color,
    primaryButtonTextColor: Color,
    onPrimaryButtonClick: () -> Unit,
    secondaryButtonText: String? = null,
    secondaryButtonEnabled: Boolean = false,
    secondaryButtonColor: Color = Color.DarkGray,
    secondaryButtonTextColor: Color = Color.LightGray,
    onSecondaryButtonClick: (() -> Unit)? = null
) {
    val actionCount = if (secondaryButtonText != null && onSecondaryButtonClick != null) 2 else 1
    SliderPanelLayout(
        axis = axis,
        modifier = modifier.fillMaxSize(),
        actionCount = actionCount,
        actions = {
            SliderActionButton(
                text = primaryButtonText,
                enabled = primaryButtonEnabled,
                background = primaryButtonColor,
                textColor = primaryButtonTextColor,
                onClick = onPrimaryButtonClick
            )
            if (secondaryButtonText != null && onSecondaryButtonClick != null) {
                SliderActionButton(
                    text = secondaryButtonText,
                    enabled = secondaryButtonEnabled,
                    background = secondaryButtonColor,
                    textColor = secondaryButtonTextColor,
                    onClick = onSecondaryButtonClick
                )
            }
        },
        content = { sliderModifier, axisReservation ->
            RibbonSlider(
                value = value,
                onValueChange = onValueChange,
                axis = axis,
                labelString = labelString,
                ticks = ticks,
                layoutLabels = layoutLabels,
                enabled = sliderEnabled,
                axisReservation = axisReservation,
                modifier = sliderModifier.fillMaxSize()
            )
        }
    )
}

@Composable
fun SliderPanelLayout(
    axis: AxisSpec,
    actionCount: Int,
    actions: @Composable () -> Unit,
    content: @Composable (Modifier, SliderAxisReservation) -> Unit,
    modifier: Modifier = Modifier,
    edgePadding: Dp = SliderPaneStyle.actionEdgePadding,
    contentGap: Dp = 0.dp
) {
    val density = LocalDensity.current
    var measuredActionGroup by remember(axis.orientation) { mutableStateOf(Size.Zero) }
    val estimatedActionLength = sliderActionGroupLength(actionCount)
    val measuredActionLength = with(density) {
        (if (axis.usesVerticalTrack) measuredActionGroup.height else measuredActionGroup.width).toDp()
    }
    val resolvedActionLength = measuredActionLength.takeIf { it > 0.dp } ?: estimatedActionLength
    val axisReservation = sliderAxisReservation(
        axis = axis,
        actionTouchExtent = edgePadding + resolvedActionLength + contentGap
    )
    AxisOverlay(
        axis = axis,
        modifier = modifier,
        edgePadding = edgePadding,
        overlay = {
            AxisStack(
                axis = axis,
                spacing = SliderPaneStyle.actionButtonGap,
                modifier = Modifier.onSizeChanged {
                    measuredActionGroup = Size(it.width.toFloat(), it.height.toFloat())
                },
                content = actions
            )
        },
        content = { content(it, axisReservation) }
    )
}

@Composable
fun SliderActionButton(
    text: String,
    enabled: Boolean = true,
    background: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            disabledContainerColor = Color.DarkGray
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(SliderPaneStyle.actionButtonSize),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            color = if (enabled) textColor else Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
