package com.pilerks1.hdrrecorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A stateless composable for the settings overlay screen.
 * It receives all necessary data as parameters and communicates user interactions
 * via lambda functions (events).
 */
@Composable
fun SettingsUI(
    gammaMode: String,
    onGammaChange: () -> Unit,
    noiseReductionEnabled: Boolean,
    onNoiseReductionChange: (Boolean) -> Unit,
    onNavigateToCompatibility: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Use a Box to make the background fully non-transparent and clickable to close
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black) // Changed from semi-transparent to fully opaque
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Settings",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(32.dp))

            // --- Gamma Mode Setting ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Gamma Profile", color = Color.White, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = onGammaChange,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text(text = gammaMode, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Noise Reduction Setting ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Noise Reduction", color = Color.White, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = noiseReductionEnabled,
                    onCheckedChange = onNoiseReductionChange
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Compatibility Check Button ---
            Button(
                onClick = onNavigateToCompatibility,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text(text = "Device Compatibility Check", color = Color.White)
            }


            Spacer(modifier = Modifier.weight(1f)) // Pushes the close button to the bottom

            // --- Close Button ---
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text(text = "Close", color = Color.White)
            }
        }
    }
}
