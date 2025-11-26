package com.pilerks1.hdrrecorder.ui

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun PreviewUI(
    surfaceRequest: SurfaceRequest?,
    recordingTime: Long,
    isRecording: Boolean,
    onEvent: (CameraUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var meterCirclePosition by remember { mutableStateOf<DpOffset?>(null) }

    // Determine screen orientation to apply correct 4:3 logic
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ROOT CONTAINER: Always fills the space allocated by parent (usually full screen)
    // and centers the content (the viewfinder).
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // VIEWFINDER CONTAINER: Constrained to 4:3 Aspect Ratio
        Box(
            modifier = Modifier
                // In Portrait, 4:3 means Width is limiting factor (fill width, calc height) -> 3:4 Ratio
                // In Landscape, 4:3 means Height is limiting factor (fill height, calc width) -> 4:3 Ratio
                .aspectRatio(if (isLandscape) 4f / 3f else 3f / 4f)
                .then(if (isLandscape) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())
                // Tap Gestures attached HERE so coordinates match the sensor image exactly
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val factory = SurfaceOrientedMeteringPointFactory(
                            size.width.toFloat(),
                            size.height.toFloat()
                        )
                        val point = factory.createPoint(offset.x, offset.y)
                        onEvent(CameraUiEvent.TapToMeter(point))
                        meterCirclePosition = DpOffset(offset.x.toDp(), offset.y.toDp())
                    }
                }
        ) {
            if (surfaceRequest != null) {
                CameraXViewfinder(
                    surfaceRequest = surfaceRequest,
                    modifier = Modifier.fillMaxSize() // Fills the 4:3 Container
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }

            // Metering circle (Visual only)
            // Rendered inside this box so offsets are relative to the image, not the screen
            meterCirclePosition?.let {
                Surface(
                    modifier = Modifier
                        .offset(it.x - 32.dp, it.y - 32.dp)
                        .align(Alignment.TopStart)
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
        }

        // Recording timer display (Floating UI)
        // Kept in the Root Container so it stays at the top of the SCREEN (in the black bars if necessary)
        // rather than inside the cropped video area.
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
                    .padding(top = 48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}