package com.pilerks1.hdrrecorder.ui

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * A composable dedicated to displaying the camera preview.
 * It handles the CameraXViewfinder, the tap-to-meter gesture, the metering circle
 * visualization, and the recording timer overlay.
 */
@Composable
fun PreviewUI(
    surfaceRequest: SurfaceRequest?,
    recordingTime: Long,
    isRecording: Boolean,
    onEvent: (CameraUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var meterCirclePosition by remember { mutableStateOf<DpOffset?>(null) }

    Box(
        // REMOVED .aspectRatio(4f/3f).
        // We now rely on the parent (CameraUI) to define the size via 'modifier'.
        // This allows the preview to be tall in Portrait and wide in Landscape.
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // With CameraXViewfinder, we use the SurfaceOrientedMeteringPointFactory
                    // with the size of the composable itself.
                    val factory = SurfaceOrientedMeteringPointFactory(
                        size.width.toFloat(),
                        size.height.toFloat()
                    )
                    val point = factory.createPoint(offset.x, offset.y)
                    onEvent(CameraUiEvent.TapToMeter(point))
                    meterCirclePosition = DpOffset(offset.x.toDp(), offset.y.toDp())
                }
            },
        contentAlignment = Alignment.TopStart
    ) {
        if (surfaceRequest != null) {
            CameraXViewfinder(
                surfaceRequest = surfaceRequest,
                modifier = Modifier.fillMaxSize() // Fills the Box provided by CameraUI
            )
        } else {
            // Placeholder while camera is initializing
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }

        // Metering circle visual feedback
        meterCirclePosition?.let {
            Surface(
                modifier = Modifier
                    .offset(it.x - 32.dp, it.y - 32.dp)
                    .size(64.dp),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(2.dp, Color.White)
            ) {}
            LaunchedEffect(it) {
                delay(1500)
                meterCirclePosition = null
            }
        }

        // Recording timer display
        if (isRecording) {
            val minutes = recordingTime / 60
            val seconds = recordingTime % 60
            val timeString = if (minutes > 0) String.format("%d:%02d", minutes, seconds) else String.format("0:%02d", seconds)
            Text(
                text = timeString,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}