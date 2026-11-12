package com.pilerks1.hdrrecorder.ui.style

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Explicit application typography; Material defaults remain unchanged. */
internal object UiTextStyles {
    object Slider {
        val liveReadout = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
        val tick = TextStyle(fontSize = 10.sp)
        val rangeTick = TextStyle(fontSize = 9.sp)
        val rangeThumb = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
        val actionLabelSize = 10.sp
    }
    object Camera {
        val controlTabSize = 14.sp
        val zoomReadoutSize = 14.sp
        val recordingTimerSize = 16.sp
    }
    object Settings {
        val headerSize = 22.sp
        val sectionTitleSize = 14.sp
        val rowTitleSize = 16.sp
        val descriptionSize = 12.sp
        val descriptionLineHeight = 12.sp
        val actionLabelSize = 12.sp
        val choiceLabelSize = 14.sp
        val choiceLineHeight = 16.sp
        val menuLineHeight = 18.sp
    }
    object Stats {
        val normalFontSize = 12.sp
        val normalLineHeight = 18.sp
        val compactFontSize = 10.sp
        val compactLineHeight = 15.sp
        val normal = StatsTypography(normalFontSize, normalLineHeight)
        val compact = StatsTypography(compactFontSize, compactLineHeight)
    }
}

/** One source of text styles for both stats measurement and rendering. */
internal data class StatsTypography(
    val fontSize: TextUnit,
    val lineHeight: TextUnit
) {
    private fun style(weight: FontWeight) =
        TextStyle(fontSize = fontSize, lineHeight = lineHeight, letterSpacing = 0.sp, fontWeight = weight)

    val headerStyle = style(FontWeight.Bold)
    val labelStyle = style(FontWeight.Medium)
    val valueStyle = style(FontWeight.Normal)
}
