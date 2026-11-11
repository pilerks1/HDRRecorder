package com.pilerks1.hdrrecorder.ui.manualcontrols

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** Action buttons have a fixed size, so this is the exact measured length of the action group. */
internal fun sliderActionGroupLength(actionCount: Int): Dp =
    SliderPaneStyle.actionButtonSize * actionCount.coerceAtLeast(0) +
        SliderPaneStyle.actionButtonGap * (actionCount - 1).coerceAtLeast(0)

/**
 * @param modeKey identifies the control's Auto/manual mode; changing it cancels any fling in
 * progress so stale fling updates cannot override the new mode.
 */
@Composable
fun BaseSlider(
    value: Float,
    axis: AxisSpec,
    labelString: String,
    contentDescription: String,
    ruler: SliderRuler,
    layoutLabels: List<String>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    primaryButtonText: String,
    modeKey: Any?,
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
        content = { axisReservation ->
            RibbonSlider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                axis = axis,
                labelString = labelString,
                contentDescription = contentDescription,
                ruler = ruler,
                layoutLabels = layoutLabels,
                enabled = sliderEnabled,
                modeKey = modeKey,
                axisReservation = axisReservation,
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}

@Composable
fun SliderPanelLayout(
    axis: AxisSpec,
    actionCount: Int,
    actions: @Composable () -> Unit,
    content: @Composable (SliderAxisReservation) -> Unit,
    modifier: Modifier = Modifier,
    edgePadding: Dp = SliderPaneStyle.actionEdgePadding,
    contentGap: Dp = 0.dp
) {
    val axisReservation = sliderAxisReservation(
        axis = axis,
        actionTouchExtent = edgePadding + sliderActionGroupLength(actionCount) + contentGap
    )
    AxisOverlay(
        axis = axis,
        modifier = modifier,
        edgePadding = edgePadding,
        overlay = {
            AxisStack(
                axis = axis,
                spacing = SliderPaneStyle.actionButtonGap,
                content = actions
            )
        },
        content = { content(axisReservation) }
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
