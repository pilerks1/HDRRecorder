package com.pilerks1.hdrrecorder.ui.layout

import com.pilerks1.hdrrecorder.model.RecordingAspectRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraLayoutCalculatorTest {
    @Test
    fun fullSizePreviewCentersBetweenStatsAndReservedSliderStrip() {
        val spec = landscape(width = 1200)

        assertEquals(CameraLayoutMode.CENTERED_RESERVED, spec.mode)
        assertEquals(1_018f, spec.expandedPanel.left, 0.01f)
        assertEquals(563f, (spec.preview.left + spec.preview.right) / 2f, 0.01f)
        assertFalse(intersects(spec.expandedPanel, spec.preview))
        assertRatio(spec, 4f / 3f)
    }

    @Test
    fun fullSizePreviewExtendsTowardButtonsUnderSliderScrim() {
        val spec = landscape(width = 1000)

        assertEquals(CameraLayoutMode.SLIDER_OVERLAY, spec.mode)
        assertEquals(spec.stats.right + 8f, spec.preview.left, 0.01f)
        assertEquals(600f, spec.preview.height, 0.01f)
        assertTrue(intersects(spec.expandedPanel, spec.preview))
        assertTrue(spec.preview.right <= spec.secondaryControls.left)
        assertRatio(spec, 4f / 3f)
    }

    @Test
    fun previewShrinksAndCentersBetweenStatsAndButtonsLast() {
        val spec = landscape(width = 900)

        assertEquals(CameraLayoutMode.SHRUNK, spec.mode)
        assertEquals(spec.stats.right + 8f, spec.preview.left, 0.01f)
        assertEquals(spec.secondaryControls.left, spec.preview.right, 0.01f)
        assertEquals((spec.preview.top + spec.preview.bottom) / 2f, 300f, 0.01f)
        assertRatio(spec, 4f / 3f)
    }

    @Test
    fun portraitStatsMeasurementAlwaysPrecedesPreview() {
        val spec = portrait(height = 610, statsHeight = 80)

        assertEquals(CameraLayoutMode.SHRUNK, spec.mode)
        assertEquals(80f, spec.stats.bottom, 0.01f)
        assertTrue(spec.preview.top >= spec.stats.bottom + 8f)
        assertRatio(spec, 3f / 4f)
    }

    @Test
    fun actionRailUsesPreferredSquareButtonSize() {
        val spec = landscape(width = 1_200)
        val rail = spec.actionRail

        assertEquals(54f, rail.buttonSize, 0.01f)
        assertEquals(rail.buttonSize, rail.requiredThickness, 0.01f)
    }

    @Test
    fun buttonSizeDropsBelowMinimumOnlyWhenCountMustFitScreen() {
        val spec = CameraLayoutCalculator.calculate(
            widthPx = 140,
            heightPx = 400,
            density = 1f,
            aspectRatio = RecordingAspectRatio.FOUR_THREE,
            secondaryButtonCount = 7,
            statsWidthPx = 100,
            statsHeightPx = 40,
            activePanelThicknessPx = 40f,
            maximumPanelThicknessPx = 40f
        )

        assertEquals(20f, spec.actionRail.buttonSize, 0.01f)
        assertEquals(20f, spec.secondaryControls.height, 0.01f)
    }

    @Test
    fun sixteenNineShrinksButtonsOnlyAfterLayoutReachesShrinkStage() {
        val fourThree = landscape(width = 1_200)
        val sixteenNine = landscape(
            width = 1_200,
            aspectRatio = RecordingAspectRatio.SIXTEEN_NINE
        )

        assertEquals(54f, fourThree.actionRail.buttonSize, 0.01f)
        assertEquals(CameraLayoutMode.SHRUNK, sixteenNine.mode)
        assertEquals(36f, sixteenNine.actionRail.buttonSize, 0.01f)
        assertEquals(
            sixteenNine.secondaryControls.left,
            sixteenNine.expandedPanel.right,
            0.01f
        )
        assertRatio(sixteenNine, 16f / 9f)
    }

    @Test
    fun sliderPanelAlwaysAnchorsToActionRail() {
        listOf(
            landscape(width = 1200),
            landscape(width = 1000),
            portrait(height = 610, statsHeight = 80)
        ).forEach { spec ->
            if (spec.axis.usesVerticalTrack) {
                assertEquals(spec.secondaryControls.left, spec.expandedPanel.right, 0.01f)
            } else {
                assertEquals(spec.secondaryControls.top, spec.expandedPanel.bottom, 0.01f)
            }
            assertFalse(intersects(spec.expandedPanel, spec.secondaryControls))
        }
    }

    @Test
    fun activePaneVariesWhileMaximumReservationKeepsPreviewStable() {
        val closed = landscape(width = 1_200, activePanelThickness = 0f, maximumPanelThickness = 160f)
        val narrow = landscape(width = 1_200, activePanelThickness = 80f, maximumPanelThickness = 160f)
        val wide = landscape(width = 1_200, activePanelThickness = 140f, maximumPanelThickness = 160f)

        assertEquals(closed.preview, narrow.preview)
        assertEquals(narrow.preview, wide.preview)
        assertEquals(0f, closed.expandedPanel.width, 0.01f)
        assertEquals(80f, narrow.expandedPanel.width, 0.01f)
        assertEquals(140f, wide.expandedPanel.width, 0.01f)
        assertEquals(160f, narrow.maximumPanelReservation.width, 0.01f)
        assertEquals(narrow.secondaryControls.left, narrow.expandedPanel.right, 0.01f)
        assertEquals(narrow.secondaryControls.left, narrow.maximumPanelReservation.right, 0.01f)
    }

    @Test
    fun paneScrimCrossAxisRemainsFullBleedWithCutouts() {
        val landscape = landscape(
            width = 1_200,
            cutoutInsets = EdgeInsets(top = 20f, bottom = 10f)
        )
        val portrait = portrait(
            height = 900,
            statsHeight = 80,
            cutoutInsets = EdgeInsets(left = 24f, right = 16f)
        )

        assertEquals(0f, landscape.expandedPanel.top, 0.01f)
        assertEquals(600f, landscape.expandedPanel.bottom, 0.01f)
        assertEquals(0f, portrait.expandedPanel.left, 0.01f)
        assertEquals(360f, portrait.expandedPanel.right, 0.01f)
    }

    @Test
    fun cutoutsProtectAllUiUntilShrinkStage() {
        val spec = CameraLayoutCalculator.calculate(
            widthPx = 1200,
            heightPx = 600,
            density = 1f,
            aspectRatio = RecordingAspectRatio.FOUR_THREE,
            secondaryButtonCount = 7,
            statsWidthPx = 100,
            statsHeightPx = 200,
            activePanelThicknessPx = 128f,
            maximumPanelThicknessPx = 128f,
            cutoutInsets = EdgeInsets(left = 50f, top = 20f, bottom = 10f)
        )

        assertEquals(CameraLayoutMode.CENTERED_RESERVED, spec.mode)
        assertEquals(54f, spec.actionRail.buttonSize, 0.01f)
        assertTrue(spec.stats.left >= 50f)
        assertTrue(spec.stats.top >= 20f)
        assertTrue(spec.secondaryControls.top >= 20f)
        assertTrue(spec.secondaryControls.bottom <= 590f)
        assertTrue(spec.preview.left >= 50f)
        assertTrue(spec.preview.top >= 20f)
    }

    @Test
    fun shrinkStageIgnoresCutoutInsets() {
        val withCutout = landscape(width = 900, cutoutInsets = EdgeInsets(left = 80f, top = 30f))
        val withoutCutout = landscape(width = 900)

        assertEquals(CameraLayoutMode.SHRUNK, withCutout.mode)
        assertEquals(withoutCutout.preview, withCutout.preview)
        assertEquals(0f, withCutout.stats.left, 0.01f)
        assertEquals(0f, withCutout.stats.top, 0.01f)
    }

    private fun landscape(
        width: Int,
        cutoutInsets: EdgeInsets = EdgeInsets(),
        activePanelThickness: Float = 128f,
        maximumPanelThickness: Float = 128f,
        aspectRatio: RecordingAspectRatio = RecordingAspectRatio.FOUR_THREE
    ): CameraLayoutSpec = CameraLayoutCalculator.calculate(
        widthPx = width,
        heightPx = 600,
        density = 1f,
        aspectRatio = aspectRatio,
        secondaryButtonCount = 7,
        statsWidthPx = 100,
        statsHeightPx = 200,
        activePanelThicknessPx = activePanelThickness,
        maximumPanelThicknessPx = maximumPanelThickness,
        cutoutInsets = cutoutInsets
    )

    private fun portrait(
        height: Int,
        statsHeight: Int,
        cutoutInsets: EdgeInsets = EdgeInsets(),
        activePanelThickness: Float = 64f,
        maximumPanelThickness: Float = 64f
    ): CameraLayoutSpec = CameraLayoutCalculator.calculate(
        widthPx = 360,
        heightPx = height,
        density = 1f,
        aspectRatio = RecordingAspectRatio.FOUR_THREE,
        secondaryButtonCount = 7,
        statsWidthPx = 340,
        statsHeightPx = statsHeight,
        activePanelThicknessPx = activePanelThickness,
        maximumPanelThicknessPx = maximumPanelThickness,
        cutoutInsets = cutoutInsets
    )

    private fun assertRatio(spec: CameraLayoutSpec, expected: Float) {
        assertEquals(expected, spec.preview.width / spec.preview.height, 0.01f)
    }

    private fun intersects(first: LayoutRect, second: LayoutRect): Boolean =
        first.left < second.right && first.right > second.left &&
            first.top < second.bottom && first.bottom > second.top
}
