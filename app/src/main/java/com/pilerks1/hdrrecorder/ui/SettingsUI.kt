package com.pilerks1.hdrrecorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, fontSize = 24.sp, textAlign = TextAlign.Center) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.Start,
            ) {
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
            }

            // --- CC Button ---
            Button(
                onClick = onNavigateToCompatibility,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text(text = "CC", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

