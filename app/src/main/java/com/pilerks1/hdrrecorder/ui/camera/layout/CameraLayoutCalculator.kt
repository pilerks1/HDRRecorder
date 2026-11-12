package com.pilerks1.hdrrecorder.ui.camera.layout

import com.pilerks1.hdrrecorder.camera.model.RecordingAspectRatio

/** Pure responsive camera geometry. */
object CameraLayoutCalculator {
    fun axisFor(widthPx: Int, heightPx: Int): AxisSpec {
        return AxisSpec(if (widthPx > heightPx) CameraOrientation.LANDSCAPE else CameraOrientation.PORTRAIT)
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

            else -> calculateShrunkLayout(
                fullRoot, axis, density, secondaryButtonCount, statsWidthPx, statsHeightPx,
                resolvedActivePanelThickness, resolvedMaximumPanelThickness, orientedRatio
            )
        }
    }

    private fun calculateShrunkLayout(
        fullRoot: LayoutRect,
        axis: AxisSpec,
        density: Float,
        secondaryButtonCount: Int,
        statsWidthPx: Int,
        statsHeightPx: Int,
        resolvedActivePanelThickness: Float,
        resolvedMaximumPanelThickness: Float,
        orientedRatio: Float
    ): CameraLayoutSpec {
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
        return buildSpec(
            mode = CameraLayoutMode.SHRUNK,
            axis = axis,
            geometry = fullscreenGeometry,
            density = density,
            preview = fitCentered(previewBounds, orientedRatio)
        )

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
        primaryActions = primaryActions(preview, geometry.expandedPanel, axis, density),
        manualReset = manualReset(preview, geometry.expandedPanel, axis, density),
        lowLightBoostIndicator = lowLightBoostIndicator(preview, axis, density),
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

}
