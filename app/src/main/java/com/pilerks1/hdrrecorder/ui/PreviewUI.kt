package com.pilerks1.hdrrecorder.ui

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.MeteringPoint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import android.os.SystemClock
import com.pilerks1.hdrrecorder.data.camera.core.CameraZoomState
import com.pilerks1.hdrrecorder.data.camera.core.MeteringAnchor
import com.pilerks1.hdrrecorder.data.camera.core.MeteringMode
import com.pilerks1.hdrrecorder.data.camera.core.RegionalMeteringState
import com.pilerks1.hdrrecorder.ui.helpers.detectPreviewGestures
import com.pilerks1.hdrrecorder.ui.layout.LayoutRect

/**
 * Camera preview composable. Layout and capture rotation are owned outside this component.
 */
@Composable
fun PreviewUI(
    surfaceRequest: SurfaceRequest?,
    recordingDurationNanos: StateFlow<Long>,
    isRecording: Boolean,
    expandedPanelBounds: LayoutRect?,
    zoomState: CameraZoomState,
    meteringState: RegionalMeteringState,
    onEvent: (CameraUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    placeholderLabel: String? = null
) {
    val recordedDurationNanos by recordingDurationNanos.collectAsStateWithLifecycle()
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val coordinateTransformer = remember { MutableCoordinateTransformer() }
    val currentZoom by rememberUpdatedState(zoomState)
    val currentMetering by rememberUpdatedState(meteringState)
    val currentOnEvent by rememberUpdatedState(onEvent)

    // Use a lambda: local function references compare equal across recompositions and can
    // retain the initial null SurfaceRequest inside rememberUpdatedState.
    val currentPointAt by rememberUpdatedState<(MeteringAnchor) -> MeteringPoint?> { anchor ->
        val request = surfaceRequest
        if (request == null || previewSize.width == 0 || previewSize.height == 0) {
            null
        } else {
            val surfaceOffset = with(coordinateTransformer) {
                Offset(anchor.x * previewSize.width, anchor.y * previewSize.height).transform()
            }
            if (!surfaceOffset.x.isFinite() || !surfaceOffset.y.isFinite()) {
                null
            } else {
                SurfaceOrientedMeteringPointFactory(
                    request.resolution.width.toFloat(), request.resolution.height.toFloat()
                ).createPoint(surfaceOffset.x, surfaceOffset.y)
            }
        }
    }

    DisposableEffect(surfaceRequest, previewSize, meteringState.generation) {
        val generation = meteringState.generation
        onDispose { currentOnEvent(CameraUiEvent.CancelMetering(generation)) }
    }

    LaunchedEffect(surfaceRequest, meteringState.generation, meteringState.requestId, meteringState.mode) {
        if (meteringState.mode != MeteringMode.PERSISTENT) return@LaunchedEffect
        var lastRemap = SystemClock.elapsedRealtime()
        snapshotFlow { currentZoom.ratio }.drop(1).collectLatest {
            delay((lastRemap + 500L - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            val active = currentMetering
            val point = active.anchor?.let(currentPointAt) ?: return@collectLatest
            currentOnEvent(CameraUiEvent.RemapMetering(point, active.generation, active.requestId))
            lastRemap = SystemClock.elapsedRealtime()
        }
    }

    // ROOT CONTAINER: Always fills the space allocated by parent
    // and centers the content (the viewfinder).
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // VIEWFINDER CONTAINER: Its selected aspect ratio is assigned by the root slot layout.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { previewSize = it }
                .pointerInput(surfaceRequest, meteringState.generation, expandedPanelBounds) {
                    fun start(offset: Offset, mode: MeteringMode) {
                        if (surfaceRequest == null || size.width == 0 || size.height == 0) return
                        val anchor = MeteringAnchor(
                            (offset.x / size.width).coerceIn(0f, 1f),
                            (offset.y / size.height).coerceIn(0f, 1f)
                        )
                        val point = currentPointAt(anchor) ?: return
                        currentOnEvent(CameraUiEvent.StartMetering(point, anchor, mode, currentMetering.generation))
                    }
                    detectPreviewGestures(
                        excludedBounds = expandedPanelBounds,
                        zoomRatio = { currentZoom.ratio },
                        zoomLimits = { currentZoom.minRatio..currentZoom.maxRatio },
                        onZoom = { if (currentZoom.isReady) currentOnEvent(CameraUiEvent.SetZoomRatio(it)) },
                        onTap = { offset ->
                            if (currentMetering.mode == MeteringMode.PERSISTENT) {
                                currentOnEvent(CameraUiEvent.CancelMetering(currentMetering.generation))
                            } else start(offset, MeteringMode.TEMPORARY)
                        },
                        onHold = { start(it, MeteringMode.PERSISTENT) }
                    )
                }
        ) {
            if (surfaceRequest != null) {
                CameraXViewfinder(
                    surfaceRequest = surfaceRequest,
                    coordinateTransformer = coordinateTransformer,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    placeholderLabel?.let { label ->
                        Text(
                            text = label,
                            color = Color.Yellow,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.75f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            meteringState.anchor?.let { anchor ->
                val density = androidx.compose.ui.platform.LocalDensity.current
                val x = with(density) { (anchor.x * previewSize.width).toDp() }
                val y = with(density) { (anchor.y * previewSize.height).toDp() }
                val rings = if (meteringState.mode == MeteringMode.PERSISTENT) listOf(64.dp, 54.dp) else listOf(64.dp)
                rings.forEach { diameter ->
                    Surface(
                        modifier = Modifier
                            .offset(x - diameter / 2, y - diameter / 2)
                            .align(Alignment.TopStart)
                            .size(diameter),
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(2.dp, Color.White)
                    ) {}
                }
            }

            // Recording timer display (hardware duration is the single time source)
            if (isRecording || recordedDurationNanos > 0L) {
                val seconds = recordedDurationNanos / 1_000_000_000L
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
