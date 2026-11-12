package com.pilerks1.hdrrecorder.ui.camera.controls.slider

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Gesture-owned fling handle; composition never reads it. */
internal class FlingHandle {
    var job: Job? = null
}

internal suspend fun PointerInputScope.detectRibbonSliderGestures(
    virtualLengthPx: Float,
    ticks: List<SliderTick>,
    currentValue: () -> Float,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit,
    scope: CoroutineScope,
    animatable: Animatable<Float, AnimationVector1D>,
    fling: FlingHandle
) {
    val velocityTracker = VelocityTracker()
    val trackLengthPx = virtualLengthPx.coerceAtLeast(1f)
    var dragAccumulator = 0f
    detectHorizontalDragGestures(
        onDragStart = {
            fling.job?.cancel()
            velocityTracker.resetTracking()
            dragAccumulator = currentValue()
        },
        onHorizontalDrag = { change, dragAmount ->
            change.consume()
            velocityTracker.addPosition(change.uptimeMillis, change.position)
            // Clamped so reversing at an endpoint responds immediately.
            dragAccumulator = (dragAccumulator - dragAmount / trackLengthPx).coerceIn(0f, 1f)
            onValueChange(dragAccumulator)
        },
        onDragEnd = {
            val velocityInValue = -(velocityTracker.calculateVelocity().x / trackLengthPx)
            fling.job = scope.launch {
                animatable.snapTo(dragAccumulator)
                animatable.animateDecay(
                    initialVelocity = velocityInValue,
                    animationSpec = exponentialDecay(frictionMultiplier = 2f)
                ) { onValueChange(this.value) }

                val finalValue = animatable.value
                val closestTick = ticks.minByOrNull { abs(it.position - finalValue) }
                if (closestTick != null && abs(finalValue - closestTick.position) < 0.01f) {
                    animatable.animateTo(
                        targetValue = closestTick.position,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) { onValueChange(this.value) }
                }
                onFinished()
            }
        },
        onDragCancel = { onFinished() }
    )
}
