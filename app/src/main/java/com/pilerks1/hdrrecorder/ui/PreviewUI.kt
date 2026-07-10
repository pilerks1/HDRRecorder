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
import com.pilerks1.hdrrecorder.model.StatsSnapshot
import kotlinx.coroutines.delay

/**
 * Camera preview composable. Accepts isLandscape from the parent (derived from the
 * actual Activity window dimensions. CameraX target rotation follows the display through
 * a DisplayListener, so preview layout and capture metadata stay in sync without custom
 * sensor mapping.
 */
@Composable
fun PreviewUI(
    surfaceRequest: SurfaceRequest?,
    stats: StatsSnapshot,
    isRecording: Boolean,
    isLandscape: Boolean,
    onEvent: (CameraUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var meterCirclePosition by remember { mutableStateOf<DpOffset?>(null) }

    // ROOT CONTAINER: Always fills the space allocated by parent
    // and centers the content (the viewfinder).
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // VIEWFINDER CONTAINER: Constrained to 4:3 Aspect Ratio
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }

            // Metering circle (Visual only)
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

            // Recording timer display (hardware duration is the single time source)
            if (isRecording || stats.hardwareDurationNanos > 0L) {
                val seconds = stats.hardwareDurationNanos / 1_000_000_000L
                val h = seconds / 3600
                val m = (seconds % 3600) / 60
                val s = seconds % 60
                val timeString = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
                Text(
                    text = timeString,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
