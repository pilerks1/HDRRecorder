package com.pilerks1.hdrrecorder.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun ManualControlsGrid(uiState: CameraUiState, onEvent: (CameraUiEvent) -> Unit, isLandscape: Boolean, modifier: Modifier = Modifier) {
    // ==========================================
    // SECONDARY BUTTON CONTROLS (Tweak these as needed)
    // ==========================================
    // Padding specifically for pushing the secondary action buttons away from the screen edge
    // (Right edge in Landscape, Bottom edge in Portrait) independently of the main action buttons.
    val secondaryEdgePadding = 8.dp
    
    // The fixed dimension (width in Landscape, height in Portrait) for each secondary button box
    val secondaryButtonDimension = 52.dp
    // ==========================================

    if (isLandscape) {
        // Landscape: 2 columns, each 50.dp wide, no space between them
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp), 
            modifier = modifier.padding(end = secondaryEdgePadding)
        ) {
            // Left Column (1, 3, 5, 7) - Closer to the preview area
            Column(modifier = Modifier.width(secondaryButtonDimension), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                GridButton(uiState.selectedResolution.qualityName, false, Modifier.weight(1f).fillMaxWidth()) { onEvent(CameraUiEvent.CycleResolution) }
                GridButton("SS", uiState.activeSlider == ActiveControl.SHUTTER, Modifier.weight(1f).fillMaxWidth()) { toggleSlider(ActiveControl.SHUTTER, uiState, onEvent) }
                GridButton("FOC", uiState.activeSlider == ActiveControl.FOCUS, Modifier.weight(1f).fillMaxWidth()) { toggleSlider(ActiveControl.FOCUS, uiState, onEvent) }
                GridButton("WB", uiState.activeSlider == ActiveControl.WB, Modifier.weight(1f).fillMaxWidth()) { toggleSlider(ActiveControl.WB, uiState, onEvent) }
            }
            // Right Column (2, 4, 6, 8) - Closer to the screen edge
            Column(modifier = Modifier.width(secondaryButtonDimension), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                GridButton("FPS", uiState.activeSlider == ActiveControl.FPS, Modifier.weight(1f).fillMaxWidth()) { toggleSlider(ActiveControl.FPS, uiState, onEvent) }
                GridButton("ISO", uiState.activeSlider == ActiveControl.ISO, Modifier.weight(1f).fillMaxWidth()) { toggleSlider(ActiveControl.ISO, uiState, onEvent) }
                GridButton("EV", uiState.activeSlider == ActiveControl.EV, Modifier.weight(1f).fillMaxWidth()) { toggleSlider(ActiveControl.EV, uiState, onEvent) }
                GridButton("TINT", uiState.activeSlider == ActiveControl.TINT, Modifier.weight(1f).fillMaxWidth()) { toggleSlider(ActiveControl.TINT, uiState, onEvent) }
            }
        }
    } else {
        // Portrait mode: 2 rows, each 50.dp high, no space between them
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp), 
            modifier = modifier.padding(bottom = secondaryEdgePadding)
        ) {
            // Top Row (1, 3, 5, 7) - Closer to the preview area
            Row(modifier = Modifier.height(secondaryButtonDimension), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                GridButton(uiState.selectedResolution.qualityName, false, Modifier.weight(1f).fillMaxHeight()) { onEvent(CameraUiEvent.CycleResolution) }
                GridButton("SS", uiState.activeSlider == ActiveControl.SHUTTER, Modifier.weight(1f).fillMaxHeight()) { toggleSlider(ActiveControl.SHUTTER, uiState, onEvent) }
                GridButton("FOC", uiState.activeSlider == ActiveControl.FOCUS, Modifier.weight(1f).fillMaxHeight()) { toggleSlider(ActiveControl.FOCUS, uiState, onEvent) }
                GridButton("WB", uiState.activeSlider == ActiveControl.WB, Modifier.weight(1f).fillMaxHeight()) { toggleSlider(ActiveControl.WB, uiState, onEvent) }
            }
            // Bottom Row (2, 4, 6, 8) - Closer to the screen edge
            Row(modifier = Modifier.height(secondaryButtonDimension), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                GridButton("FPS", uiState.activeSlider == ActiveControl.FPS, Modifier.weight(1f).fillMaxHeight()) { toggleSlider(ActiveControl.FPS, uiState, onEvent) }
                GridButton("ISO", uiState.activeSlider == ActiveControl.ISO, Modifier.weight(1f).fillMaxHeight()) { toggleSlider(ActiveControl.ISO, uiState, onEvent) }
                GridButton("EV", uiState.activeSlider == ActiveControl.EV, Modifier.weight(1f).fillMaxHeight()) { toggleSlider(ActiveControl.EV, uiState, onEvent) }
                GridButton("TINT", uiState.activeSlider == ActiveControl.TINT, Modifier.weight(1f).fillMaxHeight()) { toggleSlider(ActiveControl.TINT, uiState, onEvent) }
            }
        }
    }
}

private fun toggleSlider(control: ActiveControl, uiState: CameraUiState, onEvent: (CameraUiEvent) -> Unit) {
    if (uiState.activeSlider == control) {
        onEvent(CameraUiEvent.SetActiveSlider(ActiveControl.NONE))
    } else {
        onEvent(CameraUiEvent.SetActiveSlider(control))
    }
}

@Composable
fun GridButton(label: String, isActive: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Text(
            text = label,
            color = if (isActive) Color.Yellow else Color.White,
            fontSize = 14.sp, // Grid button font size
            fontWeight = FontWeight.Bold // Grid button font weight
        )
    }
}

@Composable
fun ActiveSliderPanel(uiState: CameraUiState, onEvent: (CameraUiEvent) -> Unit, isLandscape: Boolean, modifier: Modifier = Modifier) {
    if (uiState.activeSlider == ActiveControl.NONE) return

    // ==========================================
    // SLIDER PANEL LAYOUT CONTROLS
    // ==========================================
    // 1. Screen Edge Padding: Distance from the physical top/bottom (landscape) or left/right (portrait) edges of the screen
    val screenEdgePadding = 8.dp
    // 2. Width/Thickness Padding: Internal buffer pushing the slider thumb/axis away from the preview border
    val innerThicknessPadding = if (isLandscape) 8.dp else 4.dp
    // 3. Slider Panel Thickness: The total width (landscape) or height (portrait) of the translucent box
    val sliderPanelThickness = if (isLandscape) 128.dp else 64.dp
    // ==========================================

    val panelModifier = if (isLandscape) {
        modifier
            .fillMaxHeight()
            .width(sliderPanelThickness) 
            // Background is applied FIRST so the translucency fills the screen end-to-end
            .background(Color.Black.copy(alpha = 0.5f), shape = RectangleShape)
            // Padding is applied AFTER background so the content shrinks but the background stays full
            .padding(vertical = screenEdgePadding) 
            .padding(innerThicknessPadding) 
    } else {
        modifier
            .fillMaxWidth()
            .height(sliderPanelThickness)
            // Background is applied FIRST so the translucency fills the screen end-to-end
            .background(Color.Black.copy(alpha = 0.5f), shape = RectangleShape)
            // Padding is applied AFTER background so the content shrinks but the background stays full
            .padding(horizontal = screenEdgePadding) 
            .padding(innerThicknessPadding) 
    }

    Box(modifier = panelModifier, contentAlignment = Alignment.Center) {
        when (uiState.activeSlider) {
            ActiveControl.FPS -> FpsSlider(uiState, onEvent, isLandscape)
            ActiveControl.ISO -> GenericSlider(
                label = "ISO",
                isManual = uiState.isManualIso,
                value = uiState.isoValue,
                onToggle = { onEvent(CameraUiEvent.SetManualIso(!uiState.isManualIso)) },
                onValueChange = { onEvent(CameraUiEvent.SetManualIso(true, it)) },
                isLandscape = isLandscape
            )
            ActiveControl.SHUTTER -> GenericSlider(
                label = "SS",
                isManual = uiState.isManualShutter,
                value = uiState.shutterValue,
                onToggle = { onEvent(CameraUiEvent.SetManualShutter(!uiState.isManualShutter)) },
                onValueChange = { onEvent(CameraUiEvent.SetManualShutter(true, it)) },
                isLandscape = isLandscape
            )
            ActiveControl.FOCUS -> GenericSlider(
                label = "Focus",
                isManual = uiState.isManualFocus,
                value = uiState.focusValue,
                onToggle = { onEvent(CameraUiEvent.SetManualFocus(!uiState.isManualFocus)) },
                onValueChange = { onEvent(CameraUiEvent.SetManualFocus(true, it)) },
                isLandscape = isLandscape
            )
            ActiveControl.EV -> EvSlider(uiState, onEvent, isLandscape)
            ActiveControl.WB -> GenericSlider(
                label = "WB",
                isManual = uiState.isManualWb,
                value = uiState.wbValue,
                onToggle = { onEvent(CameraUiEvent.SetManualWb(!uiState.isManualWb)) },
                onValueChange = { onEvent(CameraUiEvent.SetManualWb(true, it)) },
                isLandscape = isLandscape
            )
            ActiveControl.TINT -> GenericSlider(
                label = "Tint",
                isManual = uiState.isManualTint,
                value = uiState.tintValue,
                onToggle = { onEvent(CameraUiEvent.SetManualTint(!uiState.isManualTint)) },
                onValueChange = { onEvent(CameraUiEvent.SetManualTint(true, it)) },
                isLandscape = isLandscape
            )
            else -> {}
        }
    }
}

// --- Ribbon Slider (Measuring Tape) ---

// Pre-calculates exact tick positions on the slider mathematically so the visual ticks
// align perfectly with the "rounded to nearest 5" values without string-parsing errors
fun getTickPositions(label: String): List<Pair<Float, String>> {
    val ticks = mutableListOf<Pair<Float, String>>()
    val totalTicks = 18
    for (i in 0..totalTicks) {
        val rawFraction = i / totalTicks.toFloat()
        
        when (label) {
            "ISO" -> {
                val minIso = 100.0
                val maxIso = 3200.0
                val stops = Math.log(maxIso / minIso) / Math.log(2.0)
                val currentIso = minIso * Math.pow(2.0, (rawFraction * stops).toDouble())
                
                val roundedIso = (Math.round(currentIso / 5.0) * 5).coerceIn(minIso.toLong(), maxIso.toLong())
                val trueFraction = (Math.log(roundedIso / minIso) / Math.log(2.0)) / stops
                ticks.add(trueFraction.toFloat() to "$roundedIso")
            }
            "SS" -> {
                val minExposureMs = 1.0 / 8000.0
                val maxExposureMs = 1.0 / 15.0
                val stops = Math.log(maxExposureMs / minExposureMs) / Math.log(2.0)
                val currentExposure = minExposureMs * Math.pow(2.0, (rawFraction * stops).toDouble())
                
                if (currentExposure < 1.0) {
                    val frac = Math.round(1.0 / currentExposure).toInt()
                    val roundedFrac = (Math.round(frac / 5.0) * 5).coerceIn(15, 8000)
                    val roundedExposure = 1.0 / roundedFrac
                    val trueFraction = (Math.log(roundedExposure / minExposureMs) / Math.log(2.0)) / stops
                    ticks.add(trueFraction.toFloat() to "1/$roundedFrac")
                } else {
                    ticks.add(rawFraction to "${Math.round(currentExposure * 10) / 10.0}s")
                }
            }
            "WB" -> {
                val minKelvin = 2000f
                val maxKelvin = 8000f
                val currentKelvin = minKelvin + rawFraction * (maxKelvin - minKelvin)
                val roundedKelvin = (Math.round(currentKelvin / 5.0) * 5).coerceIn(minKelvin.toLong(), maxKelvin.toLong())
                val trueFraction = (roundedKelvin - minKelvin) / (maxKelvin - minKelvin)
                ticks.add(trueFraction to "$roundedKelvin")
            }
            "Tint" -> {
                val currentTint = -100f + rawFraction * 200f
                val roundedTint = (Math.round(currentTint / 5.0) * 5).coerceIn(-100, 100)
                val trueFraction = (roundedTint + 100f) / 200f
                val sign = if (roundedTint > 0) "+" else ""
                ticks.add(trueFraction to "$sign$roundedTint")
            }
            "EV" -> {
                val ev = -3f + rawFraction * 6f
                val thirds = Math.round(ev * 3)
                
                val str = if (thirds == 0) {
                    "0"
                } else if (thirds % 3 == 0) {
                    val whole = thirds / 3
                    if (whole > 0) "+$whole" else "$whole"
                } else {
                    val absThirds = kotlin.math.abs(thirds)
                    val sign = if (thirds > 0) "+" else "-"
                    "$sign$absThirds/3"
                }
                
                // EV ticks are exact multiples of 1/3, no rounding displacement needed
                ticks.add(rawFraction to str)
            }
            "Focus" -> {
                val minFocus = 0.0f
                val maxFocus = 1.0f
                val currentFocus = minFocus + rawFraction * (maxFocus - minFocus)
                val str = if (currentFocus <= 0.01f) "INF" else String.format("%.2f", currentFocus)
                ticks.add(rawFraction to str)
            }
            else -> {
                ticks.add(rawFraction to "")
            }
        }
    }
    return ticks
}

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
    labelString: String = "",
    ticks: List<Pair<Float, String>>, // fraction -> label
    modifier: Modifier = Modifier
) {
    // ==========================================
    // RIBBON SLIDER INTERNAL LAYOUT CONTROLS
    // ==========================================
    // Distance from the live readout number to the center white bar
    val liveReadoutYOffset = if (isLandscape) (-40).dp else (-22).dp
    // Distance from the axis text labels to the tickmarks
    val tickToTextPadding = if (isLandscape) 20.dp else 2.dp
    // Distance from the center of the slider to the top of the tickmarks
    val tickStartYOffset = if (isLandscape) 20.dp else 16.dp
    // ==========================================

    val textRotation = if (isLandscape) 90f else 0f
    
    // The width of the panning ribbon logic
    val density = LocalDensity.current
    val totalPanDistancePx = with(density) { 600.dp.toPx() } // How many pixels to drag from 0 to 1
    
    // Use rememberUpdatedState to capture the latest value cleanly during drags
    val currentVal by rememberUpdatedState(value)
    
    val scope = rememberCoroutineScope()
    val animatable = remember { Animatable(value) }
    val velocityTracker = remember { VelocityTracker() }
    
    // Internal tracker for dragging
    var dragAccumulator by remember { mutableStateOf(value) }
    
    val sliderContent = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(ClipXAxisShape) // Clip X (scrolling direction) but not Y
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { 
                            velocityTracker.resetTracking()
                            dragAccumulator = currentVal
                            scope.launch { animatable.snapTo(dragAccumulator) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            // Dragging left (negative) pulls the ribbon left, moving the needle to a higher value on the right
                            dragAccumulator -= (dragAmount / totalPanDistancePx)
                            val newValue = dragAccumulator.coerceIn(0f, 1f)
                            scope.launch { animatable.snapTo(newValue) }
                            onValueChange(newValue)
                        },
                        onDragEnd = {
                            val velocityPx = velocityTracker.calculateVelocity().x
                            val velocityInValue = -(velocityPx / totalPanDistancePx)
                            
                            scope.launch {
                                // 1. Decay Physics
                                val decay = exponentialDecay<Float>(frictionMultiplier = 2f)
                                animatable.animateDecay(
                                    initialVelocity = velocityInValue,
                                    animationSpec = decay
                                ) {
                                    val clamped = this.value.coerceIn(0f, 1f)
                                    onValueChange(clamped)
                                    dragAccumulator = clamped
                                }
                                                         // 2. Snapping Logic (if resting very near a tick)
                                val finalValue = animatable.value.coerceIn(0f, 1f)
                                
                                // Find closest tick in ticks list
                                val closestTick = ticks.minByOrNull { abs(it.first - finalValue) }
                                if (closestTick != null) {
                                    val distanceToTick = abs(finalValue - closestTick.first)
                                    // Snap radius: if within ~0.005 fraction of a tick, lock onto it (less aggressive)
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
            // Draw Pre-Calculated Ticks
            ticks.forEachIndexed { i, tickData ->
                val fraction = tickData.first
                val displayStr = tickData.second
                
                // Position of tick relative to the current value center
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
                                fontSize = 10.sp, // Increased axis label text size
                                maxLines = 1, // Restored horizontal layout
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.graphicsLayer { rotationZ = textRotation }
                            )
                        }
                    }
                }
            }

            // Draw central needle
            Box(modifier = Modifier.width(2.dp).height(24.dp).background(Color.White))
            
            // Draw floating live value label ABOVE needle
            if (labelString.isNotEmpty()) {
                Text(
                    text = labelString,
                    color = Color.White,
                    fontSize = 14.sp, // Increased live readout text size
                    fontWeight = FontWeight.Bold,
                    maxLines = 1, // Restored horizontal layout
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .offset(y = liveReadoutYOffset)
                        .graphicsLayer { rotationZ = textRotation }
                )
            }
        }
    }

    // Apply the same rotation wrapper as RotatingSlider
    if (isLandscape) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = 270f
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            androidx.compose.ui.unit.Constraints(
                                minWidth = constraints.minHeight,
                                maxWidth = constraints.maxHeight,
                                minHeight = constraints.minWidth,
                                maxHeight = constraints.maxWidth,
                            )
                        )
                        layout(placeable.height, placeable.width) {
                            placeable.place(
                                -placeable.width / 2 + placeable.height / 2,
                                -placeable.height / 2 + placeable.width / 2
                            )
                        }
                    }
            ) {
                sliderContent()
            }
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            sliderContent()
        }
    }
}

// --- Rotating Slider Helpers ---
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RotatingSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    isLandscape: Boolean,
    enabled: Boolean = true,
    steps: Int = 0,
    labelString: String = "",
    axisLabelMapper: (Float) -> String = { "" },
    modifier: Modifier = Modifier
) {
    val textRotation = if (isLandscape) 90f else 0f

    // An axis with 11 tick marks spaced evenly
    val axis = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp) // pad slightly so it aligns with thumb travel
                // Offset towards the black area
                .offset(y = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0..10) {
                val fraction = i / 10f
                val labelStr = axisLabelMapper(fraction)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.width(1.dp).height(if (i % 5 == 0) 6.dp else 4.dp).background(Color.Gray))
                    if (labelStr.isNotEmpty()) {
                        Text(
                            text = labelStr,
                            color = Color.Gray,
                            fontSize = 7.sp,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer { rotationZ = textRotation }
                        )
                    }
                }
            }
        }
    }

    val sliderContent = @Composable {
        Box(contentAlignment = Alignment.Center) {
            axis()
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                enabled = enabled,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.Gray,
                    disabledThumbColor = Color.DarkGray,
                    disabledActiveTrackColor = Color.DarkGray
                ),
                thumb = {
                    // Exact physical size so track touches it directly
                    Box(
                        modifier = Modifier.size(width = 8.dp, height = 24.dp).background(if (enabled) Color.White else Color.DarkGray, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (labelString.isNotEmpty()) {
                            Text(
                                text = labelString,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                // Offset AWAY from black area
                                modifier = Modifier
                                    .wrapContentSize(unbounded = true)
                                    .offset(y = (-24).dp)
                                    .graphicsLayer { rotationZ = textRotation }
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (isLandscape) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = 270f
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            androidx.compose.ui.unit.Constraints(
                                minWidth = constraints.minHeight,
                                maxWidth = constraints.maxHeight,
                                minHeight = constraints.minWidth,
                                maxHeight = constraints.maxWidth,
                            )
                        )
                        layout(placeable.height, placeable.width) {
                            placeable.place(
                                -placeable.width / 2 + placeable.height / 2,
                                -placeable.height / 2 + placeable.width / 2
                            )
                        }
                    }
            ) {
                sliderContent()
            }
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            sliderContent()
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RotatingRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    isLandscape: Boolean,
    enabled: Boolean = true,
    steps: Int = 0,
    axisLabelMapper: (Float) -> String = { "" },
    modifier: Modifier = Modifier
) {
    val textRotation = if (isLandscape) 90f else 0f

    val axis = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .offset(y = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0..10) {
                val fraction = i / 10f
                val labelStr = axisLabelMapper(fraction)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.width(1.dp).height(if (i % 5 == 0) 6.dp else 4.dp).background(Color.Gray))
                    if (labelStr.isNotEmpty()) {
                        Text(
                            text = labelStr,
                            color = Color.Gray,
                            fontSize = 7.sp,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer { rotationZ = textRotation }
                        )
                    }
                }
            }
        }
    }

    val sliderContent = @Composable {
        Box(contentAlignment = Alignment.Center) {
            axis()
            RangeSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                enabled = enabled,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.Gray,
                    disabledThumbColor = Color.DarkGray,
                    disabledActiveTrackColor = Color.DarkGray
                ),
                startThumb = {
                    Box(
                        modifier = Modifier.size(width = 8.dp, height = 24.dp).background(if (enabled) Color.White else Color.DarkGray, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${value.start.toInt()}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .wrapContentSize(unbounded = true)
                                .offset(y = (-24).dp)
                                .graphicsLayer { rotationZ = textRotation }
                        )
                    }
                },
                endThumb = {
                    Box(
                        modifier = Modifier.size(width = 8.dp, height = 24.dp).background(if (enabled) Color.White else Color.DarkGray, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${value.endInclusive.toInt()}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .wrapContentSize(unbounded = true)
                                .offset(y = (-24).dp)
                                .graphicsLayer { rotationZ = textRotation }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (isLandscape) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = 270f
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            androidx.compose.ui.unit.Constraints(
                                minWidth = constraints.minHeight,
                                maxWidth = constraints.maxHeight,
                                minHeight = constraints.minWidth,
                                maxHeight = constraints.maxWidth,
                            )
                        )
                        layout(placeable.height, placeable.width) {
                            placeable.place(
                                -placeable.width / 2 + placeable.height / 2,
                                -placeable.height / 2 + placeable.width / 2
                            )
                        }
                    }
            ) {
                sliderContent()
            }
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            sliderContent()
        }
    }
}

@Composable
fun GenericSlider(
    label: String,
    isManual: Boolean,
    value: Float,
    onToggle: () -> Unit,
    onValueChange: (Float) -> Unit,
    isLandscape: Boolean
) {
    val content: @Composable (Modifier) -> Unit = { weightModifier ->
        Button(
            onClick = onToggle,
            colors = ButtonDefaults.buttonColors(containerColor = if (isManual) Color.DarkGray else Color.White),
            contentPadding = PaddingValues(0.dp), // Zero padding to ensure MAN/AUTO text centers perfectly
            modifier = Modifier.size(36.dp), // 36dp toggle box as requested
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = if (isManual) "MAN" else "AUTO",
                color = if (isManual) Color.White else Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.size(8.dp)) // Reduced padding between man/auto and phone screen edge/slider
        
        // Removed text between button and slider, it is now attached to the slider thumb
        
        val liveValueStr = formatSliderValue(label, value)
        
        RibbonSlider(
            value = value,
            onValueChange = onValueChange,
            isLandscape = isLandscape,
            labelString = liveValueStr,
            ticks = getTickPositions(label),
            modifier = weightModifier
        )
    }

    if (isLandscape) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) { content(Modifier.weight(1f)) }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { content(Modifier.weight(1f)) }
    }
}

@Composable
fun FpsSlider(uiState: CameraUiState, onEvent: (CameraUiEvent) -> Unit, isLandscape: Boolean) {
    val content: @Composable (Modifier) -> Unit = { weightModifier ->
        Button(
            onClick = {
                if (!uiState.isRecording) {
                    if (uiState.isCamera2Fps) {
                        // Switch back to CameraX mode, keep the last selected fps
                        onEvent(CameraUiEvent.SetCamera2Fps(false))
                    } else {
                        // Cycle CameraX fps
                        onEvent(CameraUiEvent.CycleFps)
                    }
                }
            },
            // Cannot change modes while recording. Button is completely disabled during recording.
            enabled = !uiState.isRecording,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isCamera2Fps) Color.DarkGray else Color.White,
                disabledContainerColor = Color.DarkGray
            ),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(36.dp), // 36dp toggle box
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = "${uiState.selectedFps}",
                color = if (uiState.isCamera2Fps) Color.LightGray else Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        RotatingRangeSlider(
            value = uiState.camera2FpsRange,
            onValueChange = { 
                if (!uiState.isRecording && !uiState.isCamera2Fps) {
                    onEvent(CameraUiEvent.SetCamera2Fps(true, it))
                } else if (uiState.isCamera2Fps) {
                    onEvent(CameraUiEvent.SetCamera2Fps(true, it))
                }
            },
            valueRange = 15f..60f,
            steps = 44,
            isLandscape = isLandscape,
            axisLabelMapper = { fraction -> 
                val fpsVal = 15f + (fraction * (60f - 15f))
                if (fpsVal.toInt() % 15 == 0 || fraction == 0f || fraction == 1f) "${fpsVal.toInt()}" else ""
            },
            enabled = if (uiState.isRecording) uiState.isCamera2Fps else true,
            modifier = weightModifier
        )
    }

    if (isLandscape) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) { content(Modifier.weight(1f)) }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { content(Modifier.weight(1f)) }
    }
}

@Composable
fun EvSlider(uiState: CameraUiState, onEvent: (CameraUiEvent) -> Unit, isLandscape: Boolean) {
    val buttons = @Composable {
        Button(
            onClick = { onEvent(CameraUiEvent.SetManualEv(!uiState.isManualEv)) },
            colors = ButtonDefaults.buttonColors(containerColor = if (uiState.isManualEv) Color.DarkGray else Color.White),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = if (uiState.isManualEv) "MAN" else "AUTO",
                color = if (uiState.isManualEv) Color.White else Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.size(4.dp))
        Button(
            onClick = { onEvent(CameraUiEvent.ToggleNightModeAe(!uiState.isNightModeAeEnabled)) },
            colors = ButtonDefaults.buttonColors(containerColor = if (uiState.isNightModeAeEnabled) Color.Yellow else Color.DarkGray),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text("NM", color = if (uiState.isNightModeAeEnabled) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }

    val content: @Composable (Modifier) -> Unit = { weightModifier ->
        if (isLandscape) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { buttons() }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) { buttons() }
        }
        
        Spacer(modifier = Modifier.size(8.dp))
        
        val liveValueStr = formatSliderValue("EV", uiState.evValue)
        
        RibbonSlider(
            value = uiState.evValue,
            onValueChange = { onEvent(CameraUiEvent.SetManualEv(true, it)) },
            isLandscape = isLandscape,
            labelString = liveValueStr,
            ticks = getTickPositions("EV"),
            modifier = weightModifier
        )
    }

    if (isLandscape) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) { content(Modifier.weight(1f)) }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { content(Modifier.weight(1f)) }
    }
}

// --- Value Mapping and Formatting ---
// Mappings convert the 0f..1f linear slider position into a logarithmic/power scale for display
fun formatSliderValue(label: String, progress: Float): String {
    return when (label) {
        "ISO" -> {
            // Placeholder Log Mapping: e.g. 100 to 3200
            // log2(3200/100) = 5 stops. Progress 0..1 -> 0..5 stops
            val minIso = 100.0
            val maxIso = 3200.0
            val stops = Math.log(maxIso / minIso) / Math.log(2.0)
            val currentIso = minIso * Math.pow(2.0, (progress * stops).toDouble())
            // Round to nearest photographic stop/third-stop
            val roundedIso = Math.round(currentIso).toInt()
            "$roundedIso"
        }
        "SS" -> {
            // Placeholder Log Mapping: e.g. 1/8000s to 1/15s
            // In linear, this is a massive range. In log, it's evenly spaced.
            val minExposureMs = 1.0 / 8000.0 // Fastest
            val maxExposureMs = 1.0 / 15.0   // Slowest
            val stops = Math.log(maxExposureMs / minExposureMs) / Math.log(2.0)
            val currentExposure = minExposureMs * Math.pow(2.0, (progress * stops).toDouble())
            
            // Format as fraction
            if (currentExposure < 1.0) {
                val fraction = Math.round(1.0 / currentExposure).toInt()
                "1/$fraction"
            } else {
                "${Math.round(currentExposure * 10) / 10.0}s"
            }
        }
        "EV" -> {
            // EV from -3 to +3 stops (6 stops total)
            val ev = -3f + progress * 6f
            // We have 18 ticks across the slider, which aligns exactly with 1/3 EV increments
            val thirds = Math.round(ev * 3)
            
            if (thirds == 0) {
                "0"
            } else if (thirds % 3 == 0) {
                val whole = thirds / 3
                if (whole > 0) "+$whole" else "$whole"
            } else {
                val absThirds = kotlin.math.abs(thirds)
                val sign = if (thirds > 0) "+" else "-"
                "$sign$absThirds/3"
            }
        }
        "Focus" -> {
            // Focus is typically linear (diopters)
            val minFocus = 0.0f
            val maxFocus = 1.0f // Diopters (0 = infinity)
            val currentFocus = minFocus + progress * (maxFocus - minFocus)
            if (currentFocus <= 0.01f) "INF" else String.format("%.2f", currentFocus)
        }
        "WB" -> {
            // Linear Kelvin range
            val minKelvin = 2000f
            val maxKelvin = 8000f
            val kelvin = (minKelvin + progress * (maxKelvin - minKelvin)).toInt()
            val roundedKelvin = (Math.round(kelvin / 5.0) * 5).toInt()
            "$roundedKelvin"
        }
        "Tint" -> {
            val tint = (-100 + progress * 200).toInt()
            if (tint > 0) "+$tint" else "$tint"
        }
        else -> String.format("%.2f", progress)
    }
}
