package com.pilerks1.hdrrecorder.compatibility

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel

// --- UI Colors for Stabilization Status ---
private val StableColor = Color(0xFF4CAF50) // A pleasant, accessible green
private val UnstableColor = Color(0xFFE57373) // A softer, less jarring red

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompatibilityScreen(
    onNavigateBack: () -> Unit,
    viewModel: CcViewModel = viewModel()
) {
    val result by viewModel.compatibilityResult.collectAsState()

    SystemUiManagement()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Device Compatibility",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Spacer(modifier = Modifier.width(48.dp)) // Balance the navigation icon
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (result == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                Text(
                    "Camera2 Hardware Level: ${result!!.hardwareLevel}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                Text(
                    "Max Supported Bitrate: ${result!!.maxBitrate}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(24.dp))

                // --- 4:3 Aspect Ratio Table ---
                Text("4:3 Aspect Ratio", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                CompatibilityTable(tableData = result!!.tableRows4by3)

                Spacer(modifier = Modifier.height(24.dp))

                // --- 16:9 Aspect Ratio Table ---
                Text("16:9 Aspect Ratio", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                CompatibilityTable(tableData = result!!.tableRows16by9)

                Spacer(modifier = Modifier.height(24.dp))

                // --- Color Key / Legend ---
                Column(horizontalAlignment = Alignment.Start) {
                    Text("Stabilization Support Key", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    KeyEntry(color = StableColor, text = "Supported with stabilization")
                    Spacer(modifier = Modifier.height(4.dp))
                    KeyEntry(color = UnstableColor, text = "Supported, no stabilization")
                }
            }
        }
    }
}

@Composable
private fun CompatibilityTable(tableData: List<CompatibilityResult.TableRow>) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Column(Modifier.border(1.dp, Color.Gray)) {
            TableHeader()
            tableData.forEach { rowData ->
                TableRow(rowData = rowData)
            }
        }
    }
}

@Composable
private fun TableHeader() {
    Row(
        Modifier
            .background(Color.Gray.copy(alpha = 0.3f))
            .height(IntrinsicSize.Min)
    ) {
        HeaderCell("Quality", width = 85.dp)
        HeaderCell("Resolution", width = 110.dp)
        HeaderCell("24 FPS", width = 100.dp)
        HeaderCell("30 FPS", width = 100.dp)
        HeaderCell("60 FPS", width = 100.dp)
    }
}

@Composable
private fun TableRow(rowData: CompatibilityResult.TableRow) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        InfoCell(text = rowData.quality, width = 85.dp)
        InfoCell(text = rowData.resolution, width = 110.dp)
        DynamicRangeListCell(supportedRanges = rowData.fps24, width = 100.dp)
        DynamicRangeListCell(supportedRanges = rowData.fps30, width = 100.dp)
        DynamicRangeListCell(supportedRanges = rowData.fps60, width = 100.dp)
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier
            .border(1.dp, Color.Gray)
            .width(width)
            .padding(4.dp)
            .fillMaxHeight(),
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = Color.White,
        fontSize = 14.sp
    )
}

@Composable
private fun InfoCell(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier
            .border(1.dp, Color.Gray)
            .width(width)
            .padding(4.dp)
            .fillMaxHeight(),
        textAlign = TextAlign.Center,
        color = Color.White,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )
}

@Composable
private fun DynamicRangeListCell(supportedRanges: List<CompatibilityResult.DynamicRangeSupport>, width: Dp) {
    Column(
        modifier = Modifier
            .border(1.dp, Color.Gray)
            .width(width)
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (supportedRanges.isEmpty()) {
            Text("None", color = Color.White, fontSize = 14.sp)
        } else {
            supportedRanges.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly // Use SpaceEvenly for consistent spacing
                ) {
                    rowItems.forEach { dynamicRange ->
                        Text(
                            text = dynamicRange.name,
                            color = if (dynamicRange.stabilizationSupported) StableColor else UnstableColor,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyEntry(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier
            .size(12.dp)
            .background(color, shape = CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}

