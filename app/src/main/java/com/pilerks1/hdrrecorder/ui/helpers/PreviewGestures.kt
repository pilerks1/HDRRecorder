package com.pilerks1.hdrrecorder.ui.helpers

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import com.pilerks1.hdrrecorder.ui.layout.LayoutRect
import kotlin.math.abs

/** One recognizer owns tap, hold, and pinch so multi-touch cannot also dispatch a tap. */
internal suspend fun PointerInputScope.detectPreviewGestures(
    excludedBounds: LayoutRect?,
    zoomRatio: () -> Float,
    zoomLimits: () -> ClosedFloatingPointRange<Float>,
    onZoom: (Float) -> Unit,
    onTap: (Offset) -> Unit,
    onHold: (Offset) -> Unit
) = awaitEachGesture {
    val down = awaitFirstDown()
    if (excludedBounds?.contains(down.position.x, down.position.y) == true) return@awaitEachGesture
    var ratio = zoomRatio()
    var accumulatedZoom = 1f
    var multiplePointers = false
    var zooming = false
    var moved = false
    var held = false
    while (true) {
        val event = if (!held && !moved && !multiplePointers) {
            val elapsed = currentEvent.changes.first().uptimeMillis - down.uptimeMillis
            withTimeoutOrNull((viewConfiguration.longPressTimeoutMillis - elapsed).coerceAtLeast(1L)) {
                awaitPointerEvent()
            }
        } else awaitPointerEvent()
        if (event == null) {
            held = true
            onHold(down.position)
            currentEvent.changes.forEach { it.consume() }
            continue
        }
        if (event.changes.any { it.isConsumed }) break
        val pressed = event.changes.filter { it.pressed }
        if (pressed.any { excludedBounds?.contains(it.position.x, it.position.y) == true }) break
        if (pressed.size >= 2) {
            multiplePointers = true
            val scale = event.calculateZoom()
            accumulatedZoom *= scale
            val span = event.calculateCentroidSize(useCurrent = false)
            val crossedSlop = abs(1f - accumulatedZoom) * span > viewConfiguration.touchSlop
            if (zooming || crossedSlop) {
                val limits = zoomLimits()
                ratio = (ratio * if (zooming) scale else accumulatedZoom)
                    .coerceIn(limits.start, limits.endInclusive)
                zooming = true
                onZoom(ratio)
            }
        }
        val original = event.changes.firstOrNull { it.id == down.id }
        if (original != null && (original.position - down.position).getDistance() > viewConfiguration.touchSlop) {
            moved = true
        }
        if (held || multiplePointers) event.changes.forEach { it.consume() }
        if (pressed.isEmpty()) {
            if (!held && !moved && !multiplePointers) {
                event.changes.forEach { it.consume() }
                onTap(down.position)
            }
            break
        }
    }
}
