package com.pilerks1.hdrrecorder.compatibility

import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode

import com.pilerks1.hdrrecorder.errorReporter
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pilerks1.hdrrecorder.data.compatibility.CompatibilityScanner
import com.pilerks1.hdrrecorder.data.storage.StorageManager
import com.pilerks1.hdrrecorder.model.CompatibilityReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CompatibilityUiState(
    val loading: Boolean = true,
    val scanning: Boolean = false,
    val report: CompatibilityReport? = null,
    val failed: Boolean = false,
    val stale: Boolean = false
) {
    val ready: Boolean get() = !loading && !scanning && !failed && report?.completedAt != null
}

class CcViewModel(application: Application) : AndroidViewModel(application) {
    private val reporter = application.errorReporter
    private val scanner = CompatibilityScanner(application, StorageManager(application, reporter).compatibilityReports, reporter)
    private val _state = MutableStateFlow(CompatibilityUiState())
    val state = _state.asStateFlow()
    private var initialized = false
    private var job: Job? = null

    fun initialize() {
        if (initialized) return
        initialized = true
        start(refresh = false)
    }

    fun retry() = start(refresh = false)
    fun refresh() = start(refresh = true)

    private fun start(refresh: Boolean) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, failed = false)
            try {
                val saved = withContext(Dispatchers.Default) { scanner.read() }
                if (!refresh && saved?.completedAt != null) {
                    _state.value = CompatibilityUiState(loading = false, report = saved,
                        stale = saved.environment != scanner.environment)
                    return@launch
                }
                _state.value = CompatibilityUiState(loading = false, scanning = true,
                    report = if (refresh) null else saved)
                val report = scanner.scan(if (refresh) null else saved) { progress ->
                    _state.value = CompatibilityUiState(loading = false, scanning = true, report = progress)
                }
                _state.value = CompatibilityUiState(loading = false, report = report)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                reporter.report(IssueCode.COMPATIBILITY_SCAN, "compatibilityScan", error)
                _state.value = _state.value.copy(loading = false, scanning = false, failed = true)
            }
        }
    }
}
