package com.pilerks1.hdrrecorder.data.diagnostics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.AtomicFile
import com.pilerks1.hdrrecorder.BuildConfig
import com.pilerks1.hdrrecorder.R
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
import java.io.File
import java.util.UUID

enum class IssueSeverity { INFO, WARNING, ERROR }

enum class IssueCode(
    @androidx.annotation.StringRes val message: Int,
    val severity: IssueSeverity = IssueSeverity.ERROR,
    val includeSettings: Boolean = false
) {
    CAMERA_START(R.string.issue_camera_start),
    CAMERA_RUNTIME(R.string.issue_camera_runtime),
    CAMERA_CONFIGURATION(R.string.issue_camera_configuration, includeSettings = true),
    CAMERA_SETUP(R.string.issue_camera_setup),
    CONFIG_VIDEO_CODEC(R.string.issue_config_video_codec, includeSettings = true),
    CONFIG_AUDIO_CODEC(R.string.issue_config_audio_codec, includeSettings = true),
    CONFIG_BIT_DEPTH(R.string.issue_config_bit_depth, includeSettings = true),
    CONFIG_COLOR(R.string.issue_config_color, includeSettings = true),
    CONFIG_VIDEO_BITRATE(R.string.issue_config_video_bitrate, includeSettings = true),
    CONFIG_AUDIO_BITRATE(R.string.issue_config_audio_bitrate, includeSettings = true),
    CONFIG_RESOLUTION(R.string.issue_config_resolution, includeSettings = true),
    CONFIG_STABILIZATION(R.string.issue_config_stabilization, includeSettings = true),
    CONFIG_FRAME_RATE(R.string.issue_config_frame_rate, includeSettings = true),
    CONFIG_COMBINATION(R.string.issue_config_combination, includeSettings = true),
    CAMERA_CONTROL(R.string.issue_camera_control, IssueSeverity.WARNING),
    PREVIEW_CONFIGURATION(R.string.issue_preview_configuration, IssueSeverity.WARNING),
    PREVIEW_RESTORED(R.string.issue_preview_restored, IssueSeverity.WARNING),
    RECORDING_START(R.string.issue_recording_start),
    RECORDING_BUSY(R.string.issue_recording_busy, IssueSeverity.INFO),
    RECORDING_FAILED(R.string.issue_recording_failed),
    RECORDING_CONTROL(R.string.issue_recording_control),
    RECORDING_METADATA(R.string.issue_recording_metadata, IssueSeverity.WARNING),
    STORAGE_ACCESS(R.string.issue_storage_access),
    STORAGE_FALLBACK(R.string.issue_storage_fallback, IssueSeverity.WARNING),
    STORAGE_CLEANUP(R.string.issue_storage_cleanup, IssueSeverity.WARNING),
    PRESET_OPERATION(R.string.issue_preset_operation),
    PRESET_UNREADABLE(R.string.issue_preset_unreadable, IssueSeverity.WARNING),
    PRESET_FOLDER(R.string.issue_preset_folder, IssueSeverity.WARNING),
    COMPATIBILITY_SCAN(R.string.issue_compatibility_scan),
    COMPATIBILITY_CACHE(R.string.issue_compatibility_cache, IssueSeverity.WARNING),
    CAPABILITY_QUERY(R.string.issue_capability_query, IssueSeverity.WARNING),
    TELEMETRY(R.string.issue_telemetry, IssueSeverity.WARNING),
    CRASH(R.string.issue_crash, IssueSeverity.INFO),
    COPY_FAILED(R.string.issue_copy_failed)
}

data class IssueRecord(
    val id: String,
    val code: IssueCode,
    val timestamp: Long,
    val operation: String,
    val details: String,
    val occurrences: Int = 1,
    val settings: Map<String, String> = emptyMap()
)

data class ErrorReportState(
    val history: List<IssueRecord> = emptyList(),
    val pending: List<String> = emptyList(),
    val persistenceAvailable: Boolean = true,
    val showHistory: Boolean = false
)

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
                    val updated = previous.copy(occurrences = if (previous.occurrences == Int.MAX_VALUE) Int.MAX_VALUE else previous.occurrences + 1)
                    mutableState.value = current.copy(history = current.history.map { if (it.id == updated.id) updated else it })
                } else {
                    val issue = newRecord(code, operation, error, context)
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

    fun showHistory(show: Boolean) = synchronized(lock) {
        mutableState.value = mutableState.value.copy(showHistory = show)
    }

    fun clearHistory() = synchronized(lock) {
        cleared = true
        removeCrashFile = true
        recent.clear()
        mutableState.value = mutableState.value.copy(history = emptyList(), pending = emptyList())
        writes.trySend(Unit)
    }

    fun export(issue: IssueRecord): String = buildString {
        appendLine(appContext.getString(issue.code.message))
        settingsSummary(issue)?.let { appendLine(it) }
        appendLine("Code: ${issue.code.name} | Severity: ${issue.code.severity}")
        appendLine("ID: ${issue.id} | Time: ${java.time.Instant.ofEpochMilli(issue.timestamp)}")
        appendLine("Occurrences: ${issue.occurrences}")
        append(issue.details)
    }

    fun settingsSummary(issue: IssueRecord): String? {
        val settings = issue.settings
        if (settings.isEmpty()) return null
        val resolution = when (settings["resolution"]) {
            "FHD" -> "1080p"; "QHD" -> "1440p"; "UHD" -> "4K"
            "HIGHEST" -> appContext.getString(R.string.diagnostics_highest)
            else -> settings["resolution"].orEmpty()
        }
        val codec = when (val mime = settings["videoCodec"]) {
            "video/hevc" -> "HEVC"; "video/avc" -> "H.264"; "video/av01" -> "AV1"
            "video/x-vnd.on2.vp9" -> "VP9"; else -> mime.orEmpty()
        }
        return appContext.getString(R.string.diagnostics_selected_settings, resolution, settings["fps"],
            settings["bitDepth"], settings["encoding"], codec,
            appContext.getString(if (settings["stabilization"] == "true") R.string.diagnostics_on else R.string.diagnostics_off))
    }

    /** Must only be installed once, at Application startup. No attempt to resume a failed process. */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            crashing = true
            try {
                val record = newRecord(IssueCode.CRASH, "uncaughtException", error, emptyMap())
                synchronized(crashLock) {
                    // A pending history write must not delete this new crash as an old import.
                    removeCrashFile = false
                    directory.mkdirs()
                    writeAtomic(crashFile, encode(record).toString().toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) {
                // Low memory, full storage and abrupt termination can prevent crash capture.
            } finally {
                if (previous != null) previous.uncaughtException(thread, error)
                else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            }
        }
    }

    private fun newRecord(code: IssueCode, operation: String, error: Throwable?, context: Map<String, String>): IssueRecord {
        val snapshot = (contextProvider?.invoke().orEmpty() + context).filterKeys { it in CONTEXT_KEYS }
            .mapValues { it.value.take(160) }
        val details = buildString {
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) / ${BuildConfig.BUILD_TYPE}")
            appendLine("CameraX: ${BuildConfig.CAMERAX_VERSION}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})")
            appendLine("Operation: ${operation.take(100)}")
            // Only developer-controlled technical fields may cross this boundary.
            snapshot.toSortedMap().forEach { (key, value) ->
                appendLine("$key: $value")
            }
            // Omit exception messages: platform/provider messages can contain user paths or names.
            var cause = error
            val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
            var depth = 0
            while (cause != null && depth++ < 5 && seen.add(cause)) {
                appendLine(cause.javaClass.name)
                cause.stackTrace.take(40).forEach { appendLine("  at $it") }
                cause.suppressed.take(3).forEach { suppressed ->
                    appendLine("  Suppressed: ${suppressed.javaClass.name}")
                    suppressed.stackTrace.take(8).forEach { appendLine("    at $it") }
                }
                cause = cause.cause
            }
        }.take(4_000)
        return IssueRecord(UUID.randomUUID().toString(), code, System.currentTimeMillis(), operation.take(100), details,
            settings = if (code.includeSettings) snapshot.filterKeys { it in SETTINGS_KEYS } else emptyMap())
    }

    private fun load() {
        val saved = try {
            if (historyFile.baseFile.exists() || File(directory, "history.json.bak").exists()) {
                val bytes = historyFile.openRead().use { it.readNBytes(MAX_REPORTS * MAX_RECORD_BYTES + 1) }
                require(bytes.size <= MAX_REPORTS * MAX_RECORD_BYTES)
                val array = JSONArray(bytes.toString(Charsets.UTF_8))
                (0 until minOf(array.length(), MAX_REPORTS)).map { decode(array.getJSONObject(it)) }
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
                    decode(JSONObject(bytes.toString(Charsets.UTF_8)))
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
            writeAtomic(historyFile, JSONArray().apply { snapshot.forEach { put(encode(it)) } }
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

    private fun encode(issue: IssueRecord) = JSONObject().apply {
        put("id", issue.id); put("code", issue.code.name); put("time", issue.timestamp)
        put("operation", issue.operation); put("details", issue.details); put("count", issue.occurrences)
        put("settings", JSONObject(issue.settings))
    }.also { require(it.toString().toByteArray(Charsets.UTF_8).size <= MAX_RECORD_BYTES) }

    private fun decode(json: JSONObject) = IssueRecord(
        json.getString("id").take(100), IssueCode.valueOf(json.getString("code")), json.getLong("time"),
        json.getString("operation").take(100), json.getString("details").take(10_000), json.optInt("count", 1).coerceAtLeast(1),
        json.optJSONObject("settings")?.let { settings ->
            SETTINGS_KEYS.filter { settings.has(it) }.associateWith { settings.getString(it).take(160) }
        }.orEmpty()
    )

    private companion object {
        const val MAX_REPORTS = 50
        const val MAX_RECORD_BYTES = 32 * 1024
        val CONTEXT_KEYS = setOf("cameraId", "fps", "resolution", "encoding", "bitDepth", "videoCodec",
            "recording", "platformCode", "recovery", "destination", "previewRange", "query", "stabilization")
        val SETTINGS_KEYS = setOf("resolution", "fps", "bitDepth", "encoding", "videoCodec", "stabilization")
    }
}
