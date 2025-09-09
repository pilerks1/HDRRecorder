package com.pilerks1.hdrrecorder.compatibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompatibilityScreen(
    onNavigateBack: () -> Unit,
    viewModel: CcViewModel = viewModel()
) {
    val result by viewModel.compatibilityResult.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Compatibility") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Show a loading indicator while the (currently instant) check runs
            if (result == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Display the placeholder header info
                Text(
                    "Camera2 Hardware Level: ${result!!.hardwareLevel}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Max Supported Bitrate: ${result!!.maxBitrate}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable container for the table
                Box(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState())
                ) {
                    // The Table structure
                    Column(Modifier.border(1.dp, Color.Gray)) {
                        // Header Row
                        TableHeader()

                        // Static rows for the template
                        TableTemplateRow("Highest", "4:3")
                        TableTemplateRow("", "16:9")
                        TableTemplateRow("UHD", "4:3")
                        TableTemplateRow("", "16:9")
                        TableTemplateRow("FHD", "4:3")
                        TableTemplateRow("", "16:9")
                        TableTemplateRow("HD", "4:3")
                        TableTemplateRow("", "16:9")
                    }
                }
            }
        }
    }
}

/**
 * Composable for the header row of the table.
 */
@Composable
private fun TableHeader() {
    Row(Modifier.background(Color.LightGray)) {
        TableCell("Quality", weight = 1.5f, fontWeight = FontWeight.Bold)
        TableCell("Aspect Ratio", weight = 1.5f, fontWeight = FontWeight.Bold)
        TableCell("Resolution (Pixels)", weight = 2f, fontWeight = FontWeight.Bold)
        TableCell("HLG 10-Bit FPS", weight = 2f, fontWeight = FontWeight.Bold)
        TableCell("HDR Capabilities", weight = 2.5f, fontWeight = FontWeight.Bold)
    }
}

/**
 * Composable for a single row in the empty table template.
 */
@Composable
private fun TableTemplateRow(quality: String, aspectRatio: String) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        TableCell(text = quality, weight = 1.5f)
        TableCell(text = aspectRatio, weight = 1.5f)
        TableCell(text = "-", weight = 2f)
        TableCell(text = "-", weight = 2f)
        TableCell(text = "-", weight = 2.5f)
    }
}

/**
 * A helper composable for a single cell in the table.
 */
@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float,
    fontWeight: FontWeight? = null
) {
    Text(
        text = text,
        modifier = Modifier
            .border(1.dp, Color.Gray)
            .weight(weight)
            .padding(8.dp),
        fontWeight = fontWeight,
        textAlign = TextAlign.Center
    )
}

