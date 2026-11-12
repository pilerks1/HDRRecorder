package com.pilerks1.hdrrecorder.data.diagnostics

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Local, bounded diagnostics. No polling, network, or camera-frame work. */
class ErrorReporter(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<Unit>(Channel.CONFLATED)
    private val lock = Any()
    private val crashLock = Any()
    private val mutableState = MutableStateFlow(ErrorReportState())
    val state = mutableState.asStateFlow()
    private data class RecentIssue(val id: String, val elapsed: Long)
    private val recent = linkedMapOf<String, RecentIssue>()
    @Volatile internal var contextProvider: (() -> Map<String, String>)? = null
    private var cleared = false
    @Volatile private var removeCrashFile = false
    @Volatile private var crashing = false
    private val directory get() = File(appContext.noBackupFilesDir, "diagnostics")
    private val historyFile get() = AtomicFile(File(directory, "history.json"))
    private val crashFile get() = AtomicFile(File(directory, "crash.json"))

    init {
        scope.launch {
            load()
            for (ignored in writes) persist()
        }
    }

    fun report(
        code: IssueCode,
        operation: String,
        error: Throwable? = null,
        context: Map<String, String> = emptyMap(),
        notify: Boolean = true
    ) {
        if (error is CancellationException && error !is TimeoutCancellationException) return
        // Reporting must never replace the original operation's result with a reporting failure.
        try {
            synchronized(lock) {
                val key = "${code.name}:$operation:${context.toSortedMap()}:${error?.javaClass?.name}"
                val now = SystemClock.elapsedRealtime()
                val current = mutableState.value
                val match = recent[key]
                val previous = current.history.firstOrNull { it.id == match?.id }
                if (match != null && now - match.elapsed < 5_000 && previous != null) {
                    val updated = previous.copy(occurrences = previous.occurrences + 1)
                    mutableState.value = current.copy(history = current.history.map { if (it.id == updated.id) updated else it })
                } else {
                    val issue = newIssueRecord(contextProvider, code, operation, error, context)
                    val history = (listOf(issue) + current.history).take(MAX_REPORTS)
                    mutableState.value = current.copy(history = history,
                        pending = (if (notify) current.pending + issue.id else current.pending)
                            .filter { id -> history.any { it.id == id } })
                    recent[key] = RecentIssue(issue.id, now)
                    recent.entries.removeAll { entry -> history.none { it.id == entry.value.id } }
                }
                writes.trySend(Unit)
            }
        } catch (_: Exception) {
            // Diagnostics are best effort; never recursively report their own failures.
        }
    }

    fun acknowledge(id: String) = synchronized(lock) {
        mutableState.value = mutableState.value.copy(pending = mutableState.value.pending - id)
    }

    fun clearHistory() = synchronized(lock) {
        cleared = true
        removeCrashFile = true
        recent.clear()
        mutableState.value = mutableState.value.copy(history = emptyList(), pending = emptyList())
        writes.trySend(Unit)
    }

    fun export(issue: IssueRecord): String = exportDiagnostic(appContext, issue)

    fun settingsSummary(issue: IssueRecord): String? = diagnosticSettingsSummary(appContext, issue)

    /** Must only be installed once, at Application startup. No attempt to resume a failed process. */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            crashing = true
            try {
                val record = newIssueRecord(contextProvider, IssueCode.CRASH, "uncaughtException", error, emptyMap())
                synchronized(crashLock) {
                    // A pending history write must not delete this new crash as an old import.
                    removeCrashFile = false
                    directory.mkdirs()
                    writeAtomic(crashFile, encodeIssueRecord(record).toString().toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) {
                // Low memory, full storage and abrupt termination can prevent crash capture.
            } finally {
                previous?.uncaughtException(thread, error) ?: kotlin.system.exitProcess(10)
            }
        }
    }

    private fun load() {
        val saved = try {
            if (historyFile.baseFile.exists() || File(directory, "history.json.bak").exists()) {
                val bytes = historyFile.openRead().use { it.readNBytes(MAX_REPORTS * MAX_RECORD_BYTES + 1) }
                require(bytes.size <= MAX_REPORTS * MAX_RECORD_BYTES)
                val array = JSONArray(bytes.toString(Charsets.UTF_8))
                (0 until minOf(array.length(), MAX_REPORTS)).map { decodeIssueRecord(array.getJSONObject(it)) }
            } else emptyList()
        } catch (_: Exception) {
            markPersistenceUnavailable()
            emptyList()
        }
        // A corrupt history must not prevent recovery of a separate crash report.
        val crash = try {
            synchronized(crashLock) {
                if (crashFile.baseFile.exists() || File(directory, "crash.json.bak").exists()) {
                    val bytes = crashFile.openRead().use { it.readNBytes(MAX_RECORD_BYTES + 1) }
                    require(bytes.size <= MAX_RECORD_BYTES)
                    decodeIssueRecord(JSONObject(bytes.toString(Charsets.UTF_8)))
                } else null
            }
        } catch (_: Exception) {
            markPersistenceUnavailable()
            null
        }
        synchronized(lock) {
            if (!cleared) {
                val current = mutableState.value
                val history = (current.history + listOfNotNull(crash) + saved)
                    .distinctBy { it.id }.sortedByDescending { it.timestamp }.take(MAX_REPORTS)
                val unseenCrash = crash?.takeIf { item -> saved.none { it.id == item.id } }
                mutableState.value = current.copy(history = history,
                    pending = (current.pending + listOfNotNull(unseenCrash?.id)).filter { id -> history.any { it.id == id } })
            }
            removeCrashFile = true
        }
        writes.trySend(Unit)
    }

    private fun markPersistenceUnavailable() = synchronized(lock) {
        mutableState.value = mutableState.value.copy(persistenceAvailable = false)
    }

    private fun persist() {
        try {
            val snapshot = synchronized(lock) { mutableState.value.history }
            directory.mkdirs()
            writeAtomic(historyFile, JSONArray().apply { snapshot.forEach { put(encodeIssueRecord(it)) } }
                .toString().toByteArray(Charsets.UTF_8))
            synchronized(crashLock) {
                if (removeCrashFile && !crashing) {
                    crashFile.delete()
                    removeCrashFile = false
                }
            }
            synchronized(lock) { mutableState.value = mutableState.value.copy(persistenceAvailable = true) }
        } catch (_: Exception) {
            markPersistenceUnavailable()
        }
    }

    private fun writeAtomic(file: AtomicFile, bytes: ByteArray) {
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private companion object {
        const val MAX_REPORTS = 50
        // Records are bounded by construction (details are capped at 4,000 characters); this
        // limit only rejects corrupt files on load.
        const val MAX_RECORD_BYTES = 32 * 1024
    }
}
