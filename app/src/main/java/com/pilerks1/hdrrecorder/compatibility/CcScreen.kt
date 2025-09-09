package com.pilerks1.hdrrecorder.compatibility

import android.app.Activity
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
private fun SystemUiManagement() {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        DisposableEffect(Unit) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) // Opt-in for TopAppBar
@Composable
fun CompatibilityScreen(
    onNavigateBack: () -> Unit,
    viewModel: CcViewModel = viewModel()
) {
    val result by viewModel.compatibilityResult.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Device Compatibility",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black // Dark theme for the screen
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (result == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                Text(
                    "Camera2 Hardware Level: ${result!!.hardwareLevel}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "Max Supported Bitrate: ${result!!.maxBitrate}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable container for the table
                Box(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(Modifier.border(1.dp, Color.DarkGray)) {
                        TableHeader()
                        // Dynamically create a row for each item in the results
                        if (result!!.tableRows.isEmpty()) {
                            TableTemplateRow("No data", "-") // Show a placeholder if list is empty
                        } else {
                            result!!.tableRows.forEach { rowData ->
                                TableRow(rowData = rowData)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeader() {
    Row(Modifier.background(Color.DarkGray)) {
        TableCell("Quality", weight = 1.5f, fontWeight = FontWeight.Bold)
        TableCell("Aspect Ratio", weight = 1.5f, fontWeight = FontWeight.Bold)
        TableCell("Resolution", weight = 2f, fontWeight = FontWeight.Bold)
        TableCell("HLG FPS", weight = 2f, fontWeight = FontWeight.Bold)
        TableCell("HDR Capabilities", weight = 2.5f, fontWeight = FontWeight.Bold)
    }
}

// New Composable to display a row from dynamic data
@Composable
private fun TableRow(rowData: CompatibilityResult.TableRow) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        TableCell(text = rowData.quality, weight = 1.5f)
        TableCell(text = rowData.aspectRatio, weight = 1.5f)
        TableCell(text = rowData.resolution, weight = 2f)
        TableCell(text = rowData.hlgFrameRates, weight = 2f)
        TableCell(text = rowData.hdrCapabilities, weight = 2.5f)
    }
}

// Kept for showing a placeholder row when data is empty
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

@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float,
    fontWeight: FontWeight? = null
) {
    Text(
        text = text,
        modifier = Modifier
            .border(1.dp, Color.DarkGray)
            .weight(weight)
            .padding(8.dp),
        fontWeight = fontWeight,
        textAlign = TextAlign.Center,
        color = Color.White // White text for dark theme
    )
}

