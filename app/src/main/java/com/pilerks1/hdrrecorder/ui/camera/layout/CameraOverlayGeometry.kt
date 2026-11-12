package com.pilerks1.hdrrecorder.ui.camera.layout

import com.pilerks1.hdrrecorder.ui.style.CameraDimensions

internal fun primaryActions(
    preview: LayoutRect,
    expandedPanel: LayoutRect,
    axis: AxisSpec,
    density: Float
): LayoutRect {
    val inset = CameraDimensions.PRIMARY_ACTION_INSET_DP * density
    val recordSize = CameraDimensions.RECORD_BUTTON_DP * density
    val secondarySize = CameraDimensions.SECONDARY_ACTION_DP * density
    val crossSize = maxOf(recordSize, secondarySize)
    val groupLength = recordSize + secondarySize
    val trailingBoundary = trailingBoundary(preview, expandedPanel, axis)
    return if (axis.usesVerticalTrack) {
        val top = preview.top + (preview.height - groupLength) / 2f
        LayoutRect(
            trailingBoundary - inset - crossSize,
            top,
            trailingBoundary - inset,
            top + groupLength
        )
    } else {
        val left = preview.left + (preview.width - groupLength) / 2f
        LayoutRect(
            left,
            trailingBoundary - inset - crossSize,
            left + groupLength,
            trailingBoundary - inset
        )
    }
}

/** The preview edge nearest the controls, moved in to the expanded panel when it overlaps. */
private fun trailingBoundary(preview: LayoutRect, expandedPanel: LayoutRect, axis: AxisSpec): Float {
    val panelOverlapsPreview = primaryLength(expandedPanel, axis) > 0f &&
        primaryStart(expandedPanel, axis) < primaryEnd(preview, axis) &&
        primaryEnd(expandedPanel, axis) > primaryStart(preview, axis)
    return if (panelOverlapsPreview) {
        minOf(primaryEnd(preview, axis), primaryStart(expandedPanel, axis))
    } else {
        primaryEnd(preview, axis)
    }
}

internal fun lowLightBoostIndicator(
    preview: LayoutRect,
    axis: AxisSpec,
    density: Float
): LayoutRect {
    val size = CameraDimensions.LOW_LIGHT_BOOST_INDICATOR_DP * density
    val inset = CameraDimensions.LOW_LIGHT_BOOST_INDICATOR_INSET_DP * density
    val left = if (axis.usesVerticalTrack) preview.right - inset - size else preview.left + inset
    return LayoutRect(
        left = left,
        top = preview.bottom - inset - size,
        right = left + size,
        bottom = preview.bottom - inset
    )
}

internal fun manualReset(
    preview: LayoutRect,
    expandedPanel: LayoutRect,
    axis: AxisSpec,
    density: Float
): LayoutRect {
    val width = CameraDimensions.MANUAL_RESET_WIDTH_DP * density
    val height = CameraDimensions.MANUAL_RESET_HEIGHT_DP * density
    val inset = CameraDimensions.MANUAL_RESET_INSET_DP * density
    val trailingBoundary = trailingBoundary(preview, expandedPanel, axis)
    return if (axis.usesVerticalTrack) {
        LayoutRect(
            left = trailingBoundary - inset - width,
            top = preview.top + inset,
            right = trailingBoundary - inset,
            bottom = preview.top + inset + height
        )
    } else {
        LayoutRect(
            left = preview.right - inset - width,
            top = trailingBoundary - inset - height,
            right = preview.right - inset,
            bottom = trailingBoundary - inset
        )
    }
}
