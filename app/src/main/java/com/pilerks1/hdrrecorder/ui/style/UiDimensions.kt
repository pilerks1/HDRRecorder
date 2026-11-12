package com.pilerks1.hdrrecorder.ui.style

import androidx.compose.ui.unit.dp

/** Shared dp values for numeric layout geometry and rendered camera actions. */
internal object CameraDimensions {
    const val RECORD_BUTTON_DP = 56f
    const val SECONDARY_ACTION_DP = 36f
    const val PREFERRED_BUTTON_DP = 54f
    const val MIN_BUTTON_DP = 36f
    const val PRIMARY_ACTION_INSET_DP = 4f
    const val STATS_GAP_DP = 2f
    const val LOW_LIGHT_BOOST_INDICATOR_DP = 16f
    const val LOW_LIGHT_BOOST_INDICATOR_INSET_DP = 8f
    const val MANUAL_RESET_WIDTH_DP = 32f
    const val MANUAL_RESET_HEIGHT_DP = 24f
    const val MANUAL_RESET_INSET_DP = 8f

    val indicatorCornerRadius = 2.dp
    val manualResetCornerRadius = 2.dp
    val overlayEdgePadding = 8.dp
    val zoomHorizontalPadding = 12.dp
    val zoomVerticalPadding = 8.dp
    val meteringOuterRingSize = 64.dp
    val meteringInnerRingSize = 54.dp
    val meteringStrokeWidth = 2.dp
    val timerTopPadding = 16.dp
    val timerHorizontalPadding = 8.dp
    val timerVerticalPadding = 4.dp
}

/** Existing UI dimensions, grouped by their visual purpose. */
internal object CompatibilityDimensions {
    val loadingPadding = 24.dp
    val loadingGap = 16.dp
    val progressTopPadding = 12.dp
    val screenPadding = 16.dp
    val summaryVerticalPadding = 8.dp
    val sectionGap = 20.dp
    val reportGap = 12.dp
    val cellMinHeight = 42.dp
    val cellBorderWidth = 0.5.dp
    val cellPadding = 8.dp
    val tableTitleVerticalPadding = 8.dp
    val columnTextPadding = 16.dp
    val columnMaxWidth = 230.dp
}

/** Existing UI dimensions, grouped by their visual purpose. */
internal object DiagnosticsDimensions {
    val screenPadding = 16.dp
    val emptyTopPadding = 16.dp
    val historyGap = 8.dp
    val recordPadding = 12.dp
    val detailsMaxHeight = 420.dp
    val settingsTopPadding = 8.dp
}

/** Existing UI dimensions, grouped by their visual purpose. */
internal object SettingsDimensions {
    val screenPadding = 16.dp
    val navigationGap = 24.dp
    val sectionSpacing = 16.dp
    val controlSpacing = 12.dp
    val rowGap = 8.dp
    val smallGap = 8.dp
    val dropdownMaxWidth = 96.dp
    val dropdownExtraWidth = 24.dp
    val dropdownHorizontalPadding = 12.dp
    val dropdownVerticalPadding = 8.dp
    val dropdownMaxHeight = 300.dp
    val scrollbarInset = 8.dp
    val scrollbarMinThumbHeight = 18.dp
    val scrollbarWidth = 2.dp
    val scrollbarEdgeGap = 4.dp
    val inputWidth = 100.dp
    val bitrateVerticalPadding = 4.dp
    val presetButtonHorizontalPadding = 12.dp
    val presetButtonVerticalPadding = 8.dp
    val presetActionHorizontalPadding = 4.dp
    val dividerVerticalPadding = 16.dp
    val sectionTitleBottomPadding = 8.dp
}

/** Slider rendering and measurement share these unchanged dimensions. */
internal object SliderDimensions {
    val actionButtonSize = 36.dp
    val actionButtonGap = 4.dp
    val actionEdgePadding = 8.dp
    val resolutionContentPadding = 8.dp
    val sliderAxisEdgePadding = 8.dp
    val sliderTickHeight = 8.dp
    val rulerTickSpacing = 20.dp
    val rulerLabelGap = 8.dp
    val standardElementGap = 2.dp
    val standardPaneEdgePadding = 2.dp
    val centerIndicatorHalfHeight = 10.dp
    val rangeTrackThickness = 4.dp
    val rangeThumbWidth = 6.dp
    val rangeThumbHeight = 28.dp
    val rangeThumbHalfWidth = rangeThumbWidth / 2
    val rangeThumbHalfHeight = rangeThumbHeight / 2
    val rangeThumbLabelOffset = 24.dp
    val rangeTickOffset = 16.dp
    val rangeTickTextPadding = 2.dp

    val actionCornerRadius = 4.dp
    val rangeTickAxisHeight = 32.dp
    val tickStrokeWidth = 1.dp
    val markerStrokeWidth = 2.dp
    val centerIndicatorStrokeWidth = 2.dp
}
