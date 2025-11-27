package com.pilerks1.hdrrecorder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.data.StatsSnapshot
import kotlin.math.roundToInt

@Composable
fun StatsUI(
    stats: StatsSnapshot,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        val shutterSpeedText = if (stats.shutterSpeed > 0) "1/${stats.shutterSpeed.roundToInt()} s" else "N/A"
        Text(text = shutterSpeedText, color = Color.White, fontSize = 14.sp)
        Text(text = "ISO ${stats.iso}", color = Color.White, fontSize = 14.sp)

        if (isRecording) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "FPS ${stats.effectiveFps}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = "Dropped: ${stats.droppedFrames}", color = Color.White, fontSize = 14.sp)
            Text(text = "Added: ${stats.addedFrames}", color = Color.White, fontSize = 14.sp)
        }
    }
}