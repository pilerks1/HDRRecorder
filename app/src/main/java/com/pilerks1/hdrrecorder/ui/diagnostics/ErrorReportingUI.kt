package com.pilerks1.hdrrecorder.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pilerks1.hdrrecorder.R
import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueRecord
import com.pilerks1.hdrrecorder.data.diagnostics.IssueSeverity
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode
import java.text.DateFormat
import java.util.Date

/** The only owner of error dialogs and warning/info Toasts across all app screens. */
@Composable
fun ErrorReportingHost(reporter: ErrorReporter) {
    MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, primary = Color.White,
        onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Color.White)) {
        ErrorReportingContent(reporter)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ErrorReportingContent(reporter: ErrorReporter) {
    val state by reporter.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var selectedId by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(reporter, lifecycle, resources) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            reporter.state.collect { current ->
                current.pending.mapNotNull { id -> current.history.firstOrNull { it.id == id } }
                    .filter { it.code.severity != IssueSeverity.ERROR }.forEach { issue ->
                        reporter.acknowledge(issue.id)
                        Toast.makeText(context, resources.getString(issue.code.message), Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    if (state.showHistory) {
        BackHandler { reporter.showHistory(false) }
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.diagnostics_title)) },
                        navigationIcon = {
                            IconButton(onClick = { reporter.showHistory(false) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.diagnostics_back))
                            }
                        },
                        actions = {
                            TextButton(onClick = reporter::clearHistory) { Text(stringResource(R.string.diagnostics_clear)) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black,
                            titleContentColor = Color.White, navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White)
                    )
                }, containerColor = Color.Black, contentColor = Color.White
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                    if (!state.persistenceAvailable) Text(stringResource(R.string.diagnostics_memory_only))
                    if (state.history.isEmpty()) Text(stringResource(R.string.diagnostics_empty), Modifier.padding(top = 16.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        items(state.history, key = { it.id }) { issue ->
                            OutlinedCard(onClick = { selectedId = issue.id; expanded = false }, modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.outlinedCardColors(containerColor = Color.Black, contentColor = Color.White)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(stringResource(issue.code.message))
                                    reporter.settingsSummary(issue)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    Text(DateFormat.getDateTimeInstance().format(Date(issue.timestamp)), style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.diagnostics_occurrences, issue.occurrences), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val selected = state.history.firstOrNull { it.id == selectedId }
    val pending = if (state.showHistory || selected != null) null else state.pending
        .mapNotNull { id -> state.history.firstOrNull { it.id == id } }
        .firstOrNull { it.code.severity == IssueSeverity.ERROR }
    val issue = selected ?: pending
    if (issue != null) {
        val dismiss = {
            reporter.acknowledge(issue.id)
            selectedId = null
            expanded = false
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.diagnostics_issue)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(issue.code.message))
                    reporter.settingsSummary(issue)?.let { Text(it, Modifier.padding(top = 8.dp)) }
                    TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.diagnostics_details)) }
                    if (expanded) SelectionContainer { Text(reporter.export(issue), style = MaterialTheme.typography.bodySmall) }
                    if (!state.showHistory) TextButton(onClick = { dismiss(); reporter.showHistory(true) }) {
                        Text(stringResource(R.string.diagnostics_title))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { copyDiagnostics(reporter, issue, context) }) {
                    Text(stringResource(R.string.diagnostics_copy))
                }
            },
            confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.diagnostics_dismiss)) } }
        )
    }
}

private fun copyDiagnostics(reporter: ErrorReporter, issue: IssueRecord, context: android.content.Context) {
    try {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.diagnostics_title), reporter.export(issue)))
    } catch (error: Exception) {
        reporter.report(IssueCode.COPY_FAILED, "copyDiagnostics", error)
    }
}
