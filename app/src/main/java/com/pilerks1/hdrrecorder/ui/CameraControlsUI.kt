package com.pilerks1.hdrrecorder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import com.pilerks1.hdrrecorder.ui.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.layout.AxisStack
import com.pilerks1.hdrrecorder.ui.layout.PrimaryActionSizes
import com.pilerks1.hdrrecorder.ui.manualcontrols.SliderPaneStyle

/** Camera buttons use their visible bounds for both layout and touch input. */
@Composable
internal fun CameraControlTouchTargets(content: @Composable () -> Unit) {
    val configuration = LocalViewConfiguration.current
    val buttonConfiguration = remember(configuration) {
        object : ViewConfiguration by configuration {
            override val minimumTouchTargetSize = DpSize.Zero
        }
    }
    CompositionLocalProvider(
        LocalViewConfiguration provides buttonConfiguration,
        LocalMinimumInteractiveComponentSize provides 0.dp,
        content = content
    )
}

/** Record plus pause/settings, ordered along the control axis. */
@Composable
fun PrimaryActionsUI(
    isRecording: Boolean,
    isPaused: Boolean,
    recordEnabled: Boolean,
    onEvent: (CameraUiEvent) -> Unit,
    axis: AxisSpec,
    modifier: Modifier = Modifier
) {
    val record = @Composable {
        IconAction(
            icon = if (isRecording) Icons.Filled.Stop else Icons.Filled.RadioButtonChecked,
            contentDescription = if (isRecording) "Stop" else "Record",
            tint = if (isRecording) Color.White else Color.Red,
            sizeDp = PrimaryActionSizes.RECORD_BUTTON_DP,
            enabled = recordEnabled,
            onClick = { onEvent(CameraUiEvent.ToggleRecording) }
        )
    }
    val secondary = @Composable {
        if (isRecording) {
            IconAction(
                icon = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = if (isPaused) "Play" else "Pause",
                onClick = { onEvent(CameraUiEvent.TogglePause) }
            )
        } else {
            IconAction(
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                onClick = { onEvent(CameraUiEvent.OpenSettings) }
            )
        }
    }
    AxisStack(axis = axis, modifier = modifier) {
        if (axis.usesVerticalTrack) {
            record()
            secondary()
        } else {
            secondary()
            record()
        }
    }
}

@Composable
private fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    sizeDp: Float = PrimaryActionSizes.SECONDARY_ACTION_DP,
    enabled: Boolean = true
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(sizeDp.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun ManualResetButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                color = Color.Black.copy(alpha = if (enabled) 0.5f else 0.25f),
                shape = RoundedCornerShape(2.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "RES",
            style = SliderPaneStyle.tickTextStyle.copy(
                color = if (enabled) Color.White else Color.Gray,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}
