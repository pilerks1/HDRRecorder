package com.pilerks1.hdrrecorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * A stateless composable to display camera statistics.
 * It receives all necessary data as parameters and has no internal logic.
 */
@Composable
fun StatsUI(
    shutterSpeed: Double,
    iso: Int,
    isRecording: Boolean,
    effectiveFps: Int,
    droppedFrames: Int,
    addedFrames: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        // Always visible stats
        val shutterSpeedText = if (shutterSpeed > 0) "1/${shutterSpeed.roundToInt()} s" else "N/A"
        Text(text = shutterSpeedText, color = Color.White, fontSize = 14.sp)
        Text(text = "ISO $iso", color = Color.White, fontSize = 14.sp)

        // Stats visible only during recording
        if (isRecording) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "FPS $effectiveFps", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = "Dropped: $droppedFrames", color = Color.White, fontSize = 14.sp)
            Text(text = "Added: $addedFrames", color = Color.White, fontSize = 14.sp)
        }
    }
}
