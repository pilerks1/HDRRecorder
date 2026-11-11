package com.pilerks1.hdrrecorder.compatibility

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.pilerks1.hdrrecorder.errorReporter
import com.pilerks1.hdrrecorder.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pilerks1.hdrrecorder.model.*
import com.pilerks1.hdrrecorder.ui.helpers.SystemUiManagement
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompatibilityScreen(
    state: CompatibilityUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    hardwareEnabled: Boolean = true,
    mandatory: Boolean = false
) {
    val reporter = LocalContext.current.errorReporter
    SystemUiManagement()
    val blocked = hardwareEnabled && (mandatory || state.loading || state.scanning)
    BackHandler(enabled = blocked) { }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Device Compatibility") }, navigationIcon = {
                if (!blocked) IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }, actions = {
                if (hardwareEnabled && state.ready) TextButton(onClick = onRefresh) { Text("Refresh") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White,
                navigationIconContentColor = Color.White))
        }, containerColor = Color.Black, contentColor = Color.White
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                !hardwareEnabled -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Not reported") }
                state.failed -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    TextButton(onClick = { reporter.showHistory(true) }) { Text(stringResource(R.string.diagnostics_open)) }
                    Button(onClick = onRetry) { Text("Retry") }
                }
                state.loading || state.scanning -> Column(Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val report = state.report
                    Text(if (report == null) "Capabilities" else "Combinations")
                    Spacer(Modifier.height(16.dp))
                    if (report == null) CircularProgressIndicator() else {
                        LinearProgressIndicator(progress = { report.combinations.size.toFloat() / report.total }, modifier = Modifier.fillMaxWidth())
                        Text("${report.combinations.size} / ${report.total}", Modifier.padding(top = 12.dp))
                    }
                }
                state.report != null -> {
                    val report = state.report
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Camera ${report.cameraId} · ${if (state.stale) "Stale" else "Current"}")
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(report.completedAt ?: report.startedAt)), style = MaterialTheme.typography.bodySmall)
                    }
                    PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Black, contentColor = Color.White) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("CameraX") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Camera2") })
                    }
                    if (tab == 0) CameraXReport(report) else LazyColumn(
                        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) { items(report.camera2) { ReportTable(it) } }
                }
            }
        }
    }
}

@Composable
private fun CameraXReport(report: CompatibilityReport) {
    var ratio by rememberSaveable { mutableStateOf(RecordingAspectRatio.FOUR_THREE.storageId) }
    var codec by rememberSaveable { mutableStateOf<String?>(null) }
    var stabilization by rememberSaveable { mutableStateOf(true) }
    var boost by rememberSaveable { mutableStateOf(false) }
    val index = remember(report) { report.combinations.associateBy { it.configuration.key } }
    val headers = listOf("Color", "24 FPS", "30 FPS", "60 FPS")
    val widths = columnWidths(headers, report.colors.map { listOf(it.label, "✓", "✓", "✓") })
    val scroll = rememberScrollState()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Choice("Aspect ratio", ratio, RecordingAspectRatio.entries.map { it.storageId to it.displayLabel }) { ratio = it }
            Choice("Video codec", codec, listOf(null to "Auto") + report.videoCodecs.map { it to it }) { codec = it }
            Choice("Stabilization", stabilization, listOf(false to "Off", true to "On")) { stabilization = it }
            Choice("AE boost", boost, listOf(false to "Off", true to "On")) { boost = it }
            Text("Preview FPS · Auto", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            Text("Preview dynamic range · Auto", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
        }
        item {
            Row(Modifier.horizontalScroll(scroll).background(Color.DarkGray)) {
                headers.forEachIndexed { column, label -> Cell(label, widths[column]) }
            }
        }
        for (resolution in Resolution.entries) {
            item(key = resolution.storageId) { Text(resolution.qualityName, fontWeight = FontWeight.Bold) }
            items(report.colors, key = { "${resolution.storageId}|${it.encoding}|${it.depth}" }) { color ->
                Row(Modifier.horizontalScroll(scroll).height(IntrinsicSize.Min)) {
                    Cell(color.label, widths[0])
                    for ((column, fps) in listOf(24, 30, 60).withIndex()) {
                        val key = CompatibilityConfiguration(ratio, codec, stabilization, resolution.storageId,
                            color.encoding, color.depth, fps).key
                        val combination = index[key]
                        val status = combination?.let { if (boost) it.boostStatus else it.status } ?: CompatibilityStatus.UNKNOWN
                        Box(Modifier.width(widths[column + 1]).fillMaxHeight().heightIn(min = 42.dp).border(0.5.dp, Color.DarkGray)
                            .semantics { contentDescription = "$fps FPS · ${status.label}" }
                            .padding(8.dp), contentAlignment = Alignment.Center) {
                            Text(status.symbol, color = statusColor(status), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        items(report.cameraX) { ReportTable(it) }
    }
}

@Composable
private fun <T> Choice(label: String, value: T, choices: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(choices.firstOrNull { it.first == value }?.second ?: "Auto")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { (option, text) -> DropdownMenuItem(text = { Text(text) }, onClick = {
                    onSelect(option); expanded = false
                }) }
            }
        }
    }
}

@Composable
private fun ReportTable(table: CompatibilityTable) {
    val widths = columnWidths(table.columns, table.rows)
    Column {
        Text(table.title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.background(Color.DarkGray).height(IntrinsicSize.Min)) {
                table.columns.forEachIndexed { index, column -> Cell(column, widths[index]) }
            }
            table.rows.forEach { row -> Row(Modifier.height(IntrinsicSize.Min)) {
                row.forEachIndexed { index, value -> Cell(value, widths[index]) }
            } }
        }
    }
}

@Composable
private fun Cell(value: String, width: Dp) {
    val status = CompatibilityStatus.entries.firstOrNull { it.label == value }
    Box(Modifier.width(width).fillMaxHeight().heightIn(min = 42.dp).border(0.5.dp, Color.DarkGray).padding(8.dp)) {
        Text(cellText(value),
            color = status?.let(::statusColor) ?: Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

private fun cellText(value: String): String = CompatibilityStatus.entries.firstOrNull { it.label == value }
    ?.let { "${it.symbol} ${it.label}" } ?: value

@Composable
private fun columnWidths(columns: List<String>, rows: List<List<String>>): List<Dp> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.bodySmall
    return remember(columns, rows, measurer, density, style) {
        columns.indices.map { column ->
            val values = listOf(columns[column]) + rows.map { it[column] }
            val pixels = values.maxOf { value ->
                measurer.measure(AnnotatedString(cellText(value)), style = style, softWrap = false).size.width
            }
            (with(density) { pixels.toDp() } + 16.dp).coerceAtMost(230.dp)
        }
    }
}

private fun statusColor(status: CompatibilityStatus): Color = when (status) {
    CompatibilityStatus.SUPPORTED -> Color(0xFF81C784)
    CompatibilityStatus.UNSUPPORTED -> Color(0xFFEF9A9A)
    CompatibilityStatus.CONDITIONAL -> Color(0xFFFFD54F)
    CompatibilityStatus.UNKNOWN, CompatibilityStatus.NOT_REPORTED -> Color.LightGray
}
