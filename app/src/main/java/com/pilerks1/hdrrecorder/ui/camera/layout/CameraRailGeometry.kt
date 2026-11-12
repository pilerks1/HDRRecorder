package com.pilerks1.hdrrecorder.ui.camera.layout

import com.pilerks1.hdrrecorder.ui.style.CameraDimensions

internal data class RailGeometry(
    val stats: LayoutRect,
    val controls: LayoutRect,
    val expandedPanel: LayoutRect,
    val maximumPanelReservation: LayoutRect,
    val actionRail: ActionRailSpec,
    val leadingBoundary: Float,
    val actionBoundary: Float
)

internal fun rails(
    root: LayoutRect,
    screenRoot: LayoutRect,
    axis: AxisSpec,
    density: Float,
    buttonCount: Int,
    statsWidth: Float,
    statsHeight: Float,
    activePanelThickness: Float,
    maximumPanelThickness: Float,
    maximumButtonSize: Float = CameraDimensions.PREFERRED_BUTTON_DP * density
): RailGeometry {
    val crossLength = if (axis.usesVerticalTrack) root.height else root.width
    val actionRail = actionRailSpec(buttonCount, crossLength, density, maximumButtonSize)
    val controls = if (axis.usesVerticalTrack) {
        LayoutRect(root.right - actionRail.buttonSize, root.top, root.right, root.bottom)
    } else {
        LayoutRect(root.left, root.bottom - actionRail.buttonSize, root.right, root.bottom)
    }
    val expandedPanel = panelRect(
        controls = controls,
        screenRoot = screenRoot,
        axis = axis,
        thickness = activePanelThickness
    )
    val maximumPanelReservation = panelRect(
        controls = controls,
        screenRoot = screenRoot,
        axis = axis,
        thickness = maximumPanelThickness
    )
    val stats = LayoutRect(
        left = root.left,
        top = root.top,
        right = (root.left + statsWidth).coerceAtMost(root.right),
        bottom = (root.top + statsHeight).coerceAtMost(root.bottom)
    )
    val statsGap = CameraDimensions.STATS_GAP_DP * density
    val leadingBoundary = if (axis.usesVerticalTrack) {
        (stats.right + statsGap).coerceAtMost(controls.left)
    } else {
        (stats.bottom + statsGap).coerceAtMost(controls.top)
    }
    val actionBoundary = primaryStart(controls, axis)
    return RailGeometry(
        stats = stats,
        controls = controls,
        expandedPanel = expandedPanel,
        maximumPanelReservation = maximumPanelReservation,
        actionRail = actionRail,
        leadingBoundary = leadingBoundary,
        actionBoundary = actionBoundary
    )
}

private fun panelRect(
    controls: LayoutRect,
    screenRoot: LayoutRect,
    axis: AxisSpec,
    thickness: Float
): LayoutRect {
    val resolvedThickness = thickness.coerceAtLeast(0f)
    return if (axis.usesVerticalTrack) {
        LayoutRect(
            left = (controls.left - resolvedThickness).coerceAtLeast(screenRoot.left),
            top = screenRoot.top,
            right = controls.left,
            bottom = screenRoot.bottom
        )
    } else {
        LayoutRect(
            left = screenRoot.left,
            top = (controls.top - resolvedThickness).coerceAtLeast(screenRoot.top),
            right = screenRoot.right,
            bottom = controls.top
        )
    }
}

private fun actionRailSpec(
    count: Int,
    availableLength: Float,
    density: Float,
    maximumButtonSize: Float
): ActionRailSpec {
    if (count <= 0) return ActionRailSpec(0f)

    val usableLength = availableLength.coerceAtLeast(0f)
    val preferredSize = minOf(CameraDimensions.PREFERRED_BUTTON_DP * density, maximumButtonSize.coerceAtLeast(0f))
    val fittingSize = usableLength / count
    return ActionRailSpec(buttonSize = minOf(preferredSize, fittingSize))
}

internal fun shrinkStageButtonSizeLimit(
    root: LayoutRect,
    axis: AxisSpec,
    density: Float,
    buttonCount: Int,
    previewLength: Float,
    leadingBoundary: Float
): Float {
    if (buttonCount <= 0) return 0f

    val crossLength = if (axis.usesVerticalTrack) root.height else root.width
    val countLimitedMaximum = minOf(CameraDimensions.PREFERRED_BUTTON_DP * density, crossLength / buttonCount)
    val countLimitedMinimum = minOf(CameraDimensions.MIN_BUTTON_DP * density, countLimitedMaximum)
    val availableThickness = primaryEnd(root, axis) - leadingBoundary - previewLength
    return availableThickness.coerceIn(countLimitedMinimum, countLimitedMaximum)
}
