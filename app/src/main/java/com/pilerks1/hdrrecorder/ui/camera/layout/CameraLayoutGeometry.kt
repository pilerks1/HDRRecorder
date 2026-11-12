package com.pilerks1.hdrrecorder.ui.camera.layout



enum class CameraOrientation {
    PORTRAIT,
    LANDSCAPE
}

/** All orientation-dependent presentation values, derived once at the screen boundary. */
data class AxisSpec(val orientation: CameraOrientation) {
    val usesVerticalTrack: Boolean
        get() = orientation == CameraOrientation.LANDSCAPE

    /** Content drawn along the vertical track is rotated back to stay upright. */
    val contentCounterRotationDegrees: Float
        get() = if (usesVerticalTrack) 90f else 0f
}

data class EdgeInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
)

data class LayoutRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom

    fun inset(insets: EdgeInsets): LayoutRect {
        val insetLeft = (left + insets.left).coerceAtMost(right)
        val insetTop = (top + insets.top).coerceAtMost(bottom)
        return LayoutRect(
            left = insetLeft,
            top = insetTop,
            right = (right - insets.right).coerceAtLeast(insetLeft),
            bottom = (bottom - insets.bottom).coerceAtLeast(insetTop)
        )
    }
}

internal fun primaryLength(rect: LayoutRect, axis: AxisSpec): Float =
    if (axis.usesVerticalTrack) rect.width else rect.height

internal fun primaryStart(rect: LayoutRect, axis: AxisSpec): Float =
    if (axis.usesVerticalTrack) rect.left else rect.top

internal fun primaryEnd(rect: LayoutRect, axis: AxisSpec): Float =
    if (axis.usesVerticalTrack) rect.right else rect.bottom

enum class CameraLayoutMode {
    CENTERED_RESERVED,
    SLIDER_OVERLAY,
    SHRUNK
}

/** The rail is exactly one button thick. */
data class ActionRailSpec(val buttonSize: Float)

data class CameraLayoutSpec(
    val mode: CameraLayoutMode,
    val axis: AxisSpec,
    val preview: LayoutRect,
    val stats: LayoutRect,
    val secondaryControls: LayoutRect,
    val expandedPanel: LayoutRect,
    val primaryActions: LayoutRect,
    val manualReset: LayoutRect,
    val lowLightBoostIndicator: LayoutRect,
    val actionRail: ActionRailSpec
)
