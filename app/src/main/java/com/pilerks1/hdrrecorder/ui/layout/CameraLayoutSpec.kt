package com.pilerks1.hdrrecorder.ui.layout

import com.pilerks1.hdrrecorder.model.RecordingAspectRatio

enum class CameraOrientation {
    PORTRAIT,
    LANDSCAPE
}

enum class CameraLayoutMode {
    CENTERED_RESERVED,
    SLIDER_OVERLAY,
    SHRUNK
}

/** All orientation-dependent presentation values, derived once at the screen boundary. */
data class AxisSpec(
    val orientation: CameraOrientation,
    val contentCounterRotationDegrees: Float
) {
    val usesVerticalTrack: Boolean
        get() = orientation == CameraOrientation.LANDSCAPE
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

data class ActionRailSpec(
    val buttonSize: Float,
    val requiredThickness: Float
)

data class CameraLayoutSpec(
    val mode: CameraLayoutMode,
    val axis: AxisSpec,
    val preview: LayoutRect,
    val stats: LayoutRect,
    val secondaryControls: LayoutRect,
    val expandedPanel: LayoutRect,
    val maximumPanelReservation: LayoutRect,
    val primaryActions: LayoutRect,
    val actionRail: ActionRailSpec
)

/** Pure responsive geometry used by the Compose camera layout and JVM tests. */
object CameraLayoutCalculator {
    private const val PREFERRED_BUTTON_DP = 54f
    private const val MIN_BUTTON_DP = 36f
    private const val PRIMARY_ACTION_INSET_DP = 8f
    private const val STATS_GAP_DP = 8f
    private const val RECORD_BUTTON_DP = 64f
    private const val SECONDARY_ACTION_DP = 48f

    fun axisFor(widthPx: Int, heightPx: Int): AxisSpec {
        val orientation = if (widthPx > heightPx) CameraOrientation.LANDSCAPE else CameraOrientation.PORTRAIT
        return AxisSpec(
            orientation = orientation,
            contentCounterRotationDegrees = if (orientation == CameraOrientation.LANDSCAPE) 90f else 0f
        )
    }

    fun calculate(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        aspectRatio: RecordingAspectRatio,
        secondaryButtonCount: Int,
        statsWidthPx: Int,
        statsHeightPx: Int,
        activePanelThicknessPx: Float,
        maximumPanelThicknessPx: Float,
        cutoutInsets: EdgeInsets = EdgeInsets()
    ): CameraLayoutSpec {
        require(widthPx > 0 && heightPx > 0)
        require(density > 0f)

        val resolvedActivePanelThickness = activePanelThicknessPx.coerceAtLeast(0f)
        val resolvedMaximumPanelThickness = maximumPanelThicknessPx
            .coerceAtLeast(resolvedActivePanelThickness)
        val fullRoot = LayoutRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        val safeRoot = fullRoot.inset(cutoutInsets)
        val axis = axisFor(widthPx, heightPx)
        val orientedRatio = if (axis.usesVerticalTrack) {
            aspectRatio.landscapeRatio
        } else {
            1f / aspectRatio.landscapeRatio
        }
        val safeGeometry = rails(
            root = safeRoot,
            screenRoot = fullRoot,
            axis = axis,
            density = density,
            buttonCount = secondaryButtonCount,
            statsWidth = statsWidthPx.toFloat(),
            statsHeight = statsHeightPx.toFloat(),
            activePanelThickness = resolvedActivePanelThickness,
            maximumPanelThickness = resolvedMaximumPanelThickness
        )
        val fullSizePreview = fillCrossAxis(safeRoot, axis, orientedRatio)
        val fullSizeLength = primaryLength(fullSizePreview, axis)
        val preferredLength = primaryStart(safeGeometry.maximumPanelReservation, axis) - safeGeometry.leadingBoundary
        val maximumLength = safeGeometry.actionBoundary - safeGeometry.leadingBoundary

        return when {
            fullSizeLength <= preferredLength -> buildSpec(
                mode = CameraLayoutMode.CENTERED_RESERVED,
                axis = axis,
                geometry = safeGeometry,
                density = density,
                preview = centerOnPrimaryAxis(
                    preview = fullSizePreview,
                    axis = axis,
                    start = safeGeometry.leadingBoundary,
                    end = primaryStart(safeGeometry.maximumPanelReservation, axis)
                )
            )

            fullSizeLength <= maximumLength -> buildSpec(
                mode = CameraLayoutMode.SLIDER_OVERLAY,
                axis = axis,
                geometry = safeGeometry,
                density = density,
                preview = placeAtPrimaryStart(fullSizePreview, axis, safeGeometry.leadingBoundary)
            )

            else -> {
                val preferredFullscreenGeometry = rails(
                    root = fullRoot,
                    screenRoot = fullRoot,
                    axis = axis,
                    density = density,
                    buttonCount = secondaryButtonCount,
                    statsWidth = statsWidthPx.toFloat(),
                    statsHeight = statsHeightPx.toFloat(),
                    activePanelThickness = resolvedActivePanelThickness,
                    maximumPanelThickness = resolvedMaximumPanelThickness
                )
                val fullscreenPreview = fillCrossAxis(fullRoot, axis, orientedRatio)
                val shrunkButtonSizeLimit = shrinkStageButtonSizeLimit(
                    root = fullRoot,
                    axis = axis,
                    density = density,
                    buttonCount = secondaryButtonCount,
                    previewLength = primaryLength(fullscreenPreview, axis),
                    leadingBoundary = preferredFullscreenGeometry.leadingBoundary
                )
                val fullscreenGeometry = rails(
                    root = fullRoot,
                    screenRoot = fullRoot,
                    axis = axis,
                    density = density,
                    buttonCount = secondaryButtonCount,
                    statsWidth = statsWidthPx.toFloat(),
                    statsHeight = statsHeightPx.toFloat(),
                    activePanelThickness = resolvedActivePanelThickness,
                    maximumPanelThickness = resolvedMaximumPanelThickness,
                    maximumButtonSize = shrunkButtonSizeLimit
                )
                val previewBounds = betweenRails(fullRoot, axis, fullscreenGeometry)
                buildSpec(
                    mode = CameraLayoutMode.SHRUNK,
                    axis = axis,
                    geometry = fullscreenGeometry,
                    density = density,
                    preview = fitCentered(previewBounds, orientedRatio)
                )
            }
        }
    }

    private data class RailGeometry(
        val stats: LayoutRect,
        val controls: LayoutRect,
        val expandedPanel: LayoutRect,
        val maximumPanelReservation: LayoutRect,
        val actionRail: ActionRailSpec,
        val leadingBoundary: Float,
        val actionBoundary: Float
    )

    private fun rails(
        root: LayoutRect,
        screenRoot: LayoutRect,
        axis: AxisSpec,
        density: Float,
        buttonCount: Int,
        statsWidth: Float,
        statsHeight: Float,
        activePanelThickness: Float,
        maximumPanelThickness: Float,
        maximumButtonSize: Float = PREFERRED_BUTTON_DP * density
    ): RailGeometry {
        val crossLength = if (axis.usesVerticalTrack) root.height else root.width
        val actionRail = actionRailSpec(buttonCount, crossLength, density, maximumButtonSize)
        val controls = if (axis.usesVerticalTrack) {
            LayoutRect(root.right - actionRail.requiredThickness, root.top, root.right, root.bottom)
        } else {
            LayoutRect(root.left, root.bottom - actionRail.requiredThickness, root.right, root.bottom)
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
        val statsGap = STATS_GAP_DP * density
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
        if (count <= 0) return ActionRailSpec(0f, 0f)

        val usableLength = availableLength.coerceAtLeast(0f)
        val preferredSize = minOf(PREFERRED_BUTTON_DP * density, maximumButtonSize.coerceAtLeast(0f))
        val fittingSize = usableLength / count
        val buttonSize = minOf(preferredSize, fittingSize)
        return ActionRailSpec(
            buttonSize = buttonSize,
            requiredThickness = buttonSize
        )
    }

    private fun shrinkStageButtonSizeLimit(
        root: LayoutRect,
        axis: AxisSpec,
        density: Float,
        buttonCount: Int,
        previewLength: Float,
        leadingBoundary: Float
    ): Float {
        if (buttonCount <= 0) return 0f

        val crossLength = if (axis.usesVerticalTrack) root.height else root.width
        val countLimitedMaximum = minOf(PREFERRED_BUTTON_DP * density, crossLength / buttonCount)
        val countLimitedMinimum = minOf(MIN_BUTTON_DP * density, countLimitedMaximum)
        val availableThickness = primaryEnd(root, axis) - leadingBoundary - previewLength
        return availableThickness.coerceIn(countLimitedMinimum, countLimitedMaximum)
    }

    private fun primaryActions(
        preview: LayoutRect,
        axis: AxisSpec,
        density: Float
    ): LayoutRect {
        val inset = PRIMARY_ACTION_INSET_DP * density
        val recordSize = RECORD_BUTTON_DP * density
        val secondarySize = SECONDARY_ACTION_DP * density
        return if (axis.usesVerticalTrack) {
            val groupHeight = recordSize + secondarySize
            val top = preview.top + (preview.height - groupHeight) / 2f
            LayoutRect(preview.right - inset - recordSize, top, preview.right - inset, top + groupHeight)
        } else {
            val groupWidth = recordSize + secondarySize
            val left = preview.left + (preview.width - groupWidth) / 2f
            LayoutRect(left, preview.bottom - inset - recordSize, left + groupWidth, preview.bottom - inset)
        }
    }

    private fun buildSpec(
        mode: CameraLayoutMode,
        axis: AxisSpec,
        geometry: RailGeometry,
        density: Float,
        preview: LayoutRect
    ): CameraLayoutSpec = CameraLayoutSpec(
        mode = mode,
        axis = axis,
        preview = preview,
        stats = geometry.stats,
        secondaryControls = geometry.controls,
        expandedPanel = geometry.expandedPanel,
        maximumPanelReservation = geometry.maximumPanelReservation,
        primaryActions = primaryActions(preview, axis, density),
        actionRail = geometry.actionRail
    )

    private fun fillCrossAxis(root: LayoutRect, axis: AxisSpec, ratio: Float): LayoutRect =
        if (axis.usesVerticalTrack) {
            val width = root.height * ratio
            LayoutRect(root.left, root.top, root.left + width, root.bottom)
        } else {
            val height = root.width / ratio
            LayoutRect(root.left, root.top, root.right, root.top + height)
        }

    private fun centerOnPrimaryAxis(
        preview: LayoutRect,
        axis: AxisSpec,
        start: Float,
        end: Float
    ): LayoutRect {
        val offset = start + (end - start - primaryLength(preview, axis)) / 2f
        return placeAtPrimaryStart(preview, axis, offset)
    }

    private fun placeAtPrimaryStart(preview: LayoutRect, axis: AxisSpec, start: Float): LayoutRect =
        if (axis.usesVerticalTrack) {
            LayoutRect(start, preview.top, start + preview.width, preview.bottom)
        } else {
            LayoutRect(preview.left, start, preview.right, start + preview.height)
        }

    private fun betweenRails(root: LayoutRect, axis: AxisSpec, geometry: RailGeometry): LayoutRect =
        if (axis.usesVerticalTrack) {
            LayoutRect(geometry.leadingBoundary, root.top, geometry.actionBoundary, root.bottom)
        } else {
            LayoutRect(root.left, geometry.leadingBoundary, root.right, geometry.actionBoundary)
        }

    private fun fitCentered(bounds: LayoutRect, ratio: Float): LayoutRect {
        val widthFromHeight = bounds.height * ratio
        val previewWidth: Float
        val previewHeight: Float
        if (widthFromHeight <= bounds.width) {
            previewWidth = widthFromHeight
            previewHeight = bounds.height
        } else {
            previewWidth = bounds.width
            previewHeight = if (ratio > 0f) bounds.width / ratio else 0f
        }
        val left = bounds.left + (bounds.width - previewWidth) / 2f
        val top = bounds.top + (bounds.height - previewHeight) / 2f
        return LayoutRect(left, top, left + previewWidth, top + previewHeight)
    }

    private fun primaryLength(rect: LayoutRect, axis: AxisSpec): Float =
        if (axis.usesVerticalTrack) rect.width else rect.height

    private fun primaryStart(rect: LayoutRect, axis: AxisSpec): Float =
        if (axis.usesVerticalTrack) rect.left else rect.top

    private fun primaryEnd(rect: LayoutRect, axis: AxisSpec): Float =
        if (axis.usesVerticalTrack) rect.right else rect.bottom

}
