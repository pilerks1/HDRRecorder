package com.pilerks1.hdrrecorder.ui.manualcontrols

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ClipXAxisShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Rectangle(
            Rect(
                left = 0f,
                top = -1000f, // Don't clip Y (which is the preview/black bar direction)
                right = size.width,
                bottom = size.height + 1000f
            )
        )
    }
}

@Composable
fun RibbonSlider(
    value: Float, // 0f..1f
    onValueChange: (Float) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelString: String = "",
    ticks: List<Pair<Float, String>> // fraction -> label
) {
    // ==========================================
    // RIBBON SLIDER INTERNAL LAYOUT CONTROLS
    // ==========================================
    val liveReadoutYOffset = if (isLandscape) (-40).dp else (-22).dp
    val tickToTextPadding = if (isLandscape) 20.dp else 2.dp
    val tickStartYOffset = if (isLandscape) 20.dp else 16.dp
    // ==========================================

    val textRotation = if (isLandscape) 90f else 0f
    
    val density = LocalDensity.current
    val totalPanDistancePx = with(density) { 600.dp.toPx() } // How many pixels to drag from 0 to 1
    
    val currentVal by rememberUpdatedState(value)
    
    val scope = rememberCoroutineScope()
    val animatable = remember { Animatable(value) }
    val velocityTracker = remember { VelocityTracker() }
    
    var dragAccumulator by remember { mutableFloatStateOf(value) }
    
    val sliderContent = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(ClipXAxisShape)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { 
                            velocityTracker.resetTracking()
                            dragAccumulator = currentVal
                            scope.launch { animatable.snapTo(dragAccumulator) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            dragAccumulator -= (dragAmount / totalPanDistancePx)
                            val newValue = dragAccumulator.coerceIn(0f, 1f)
                            scope.launch { animatable.snapTo(newValue) }
                            onValueChange(newValue)
                        },
                        onDragEnd = {
                            val velocityPx = velocityTracker.calculateVelocity().x
                            val velocityInValue = -(velocityPx / totalPanDistancePx)
                            
                            scope.launch {
                                val decay = exponentialDecay<Float>(frictionMultiplier = 2f)
                                animatable.animateDecay(
                                    initialVelocity = velocityInValue,
                                    animationSpec = decay
                                ) {
                                    val clamped = this.value.coerceIn(0f, 1f)
                                    onValueChange(clamped)
                                    dragAccumulator = clamped
                                }
                                
                                val finalValue = animatable.value.coerceIn(0f, 1f)
                                val closestTick = ticks.minByOrNull { abs(it.first - finalValue) }
                                if (closestTick != null) {
                                    val distanceToTick = abs(finalValue - closestTick.first)
                                    if (distanceToTick < 0.01f) {
                                        animatable.animateTo(
                                            targetValue = closestTick.first,
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                        ) {
                                            val clamped = this.value.coerceIn(0f, 1f)
                                            onValueChange(clamped)
                                            dragAccumulator = clamped
                                        }
                                    }
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            ticks.forEachIndexed { i, tickData ->
                val fraction = tickData.first
                val displayStr = tickData.second
                
                val offsetPx = (fraction - value) * totalPanDistancePx
                val offsetDp = with(density) { offsetPx.toDp() }
                
                Box(
                    modifier = Modifier.offset(x = offsetDp, y = tickStartYOffset),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.width(1.dp).height(if (i % 3 == 0) 12.dp else 6.dp).background(Color.White))
                        
                        if (displayStr.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(tickToTextPadding))
                            Text(
                                text = displayStr,
                                color = Color.Gray,
                                fontSize = 10.sp,
                                maxLines = 1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.graphicsLayer { rotationZ = textRotation }
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.width(2.dp).height(24.dp).background(Color.White))
            
            if (labelString.isNotEmpty()) {
                Text(
                    text = labelString,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .offset(y = liveReadoutYOffset)
                        .graphicsLayer { rotationZ = textRotation }
                )
            }
        }
    }

    if (isLandscape) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.rotateVertical()) {
                sliderContent()
            }
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            sliderContent()
        }
    }
}
