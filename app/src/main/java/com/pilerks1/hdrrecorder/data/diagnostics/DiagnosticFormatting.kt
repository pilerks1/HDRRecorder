package com.pilerks1.hdrrecorder.data.diagnostics

import android.content.Context
import android.os.Build
import com.pilerks1.hdrrecorder.BuildConfig
import com.pilerks1.hdrrecorder.R
import java.util.UUID

internal fun exportDiagnostic(appContext: Context, issue: IssueRecord): String = buildString {
    appendLine(appContext.getString(issue.code.message))
    diagnosticSettingsSummary(appContext, issue)?.let { appendLine(it) }
    appendLine("Code: ${issue.code.name} | Severity: ${issue.code.severity}")
    appendLine("ID: ${issue.id} | Time: ${java.time.Instant.ofEpochMilli(issue.timestamp)}")
    appendLine("Occurrences: ${issue.occurrences}")
    append(issue.details)
}

internal fun diagnosticSettingsSummary(appContext: Context, issue: IssueRecord): String? {
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

internal fun newIssueRecord(contextProvider: (() -> Map<String, String>)?, code: IssueCode, operation: String, error: Throwable?, context: Map<String, String>): IssueRecord {
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
        // Messages are kept, but URIs and filesystem paths (which can name the user's files) are redacted.
        var cause = error
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        var depth = 0
        while (cause != null && depth++ < 5 && seen.add(cause)) {
            appendLine(cause.javaClass.name + cause.message?.let { ": " + redact(it).take(300) }.orEmpty())
            cause.stackTrace.take(40).forEach { appendLine("  at $it") }
            cause.suppressed.take(3).forEach { suppressed ->
                appendLine("  Suppressed: ${suppressed.javaClass.name}" +
                    suppressed.message?.let { ": " + redact(it).take(300) }.orEmpty())
                suppressed.stackTrace.take(8).forEach { appendLine("    at $it") }
            }
            cause = cause.cause
        }
    }.take(4_000)
    return IssueRecord(UUID.randomUUID().toString(), code, System.currentTimeMillis(), operation.take(100), details,
        settings = if (code.includeSettings) snapshot.filterKeys { it in SETTINGS_KEYS } else emptyMap())
}

private fun redact(message: String): String = SENSITIVE_TEXT.replace(message, "<redacted>")

private val SENSITIVE_TEXT = Regex("""(content|file)://\S+|/(storage|sdcard|data|mnt)/\S*""")
private val CONTEXT_KEYS = setOf("cameraId", "fps", "resolution", "encoding", "bitDepth", "videoCodec",
    "recording", "platformCode", "recovery", "destination", "previewRange", "query", "stabilization")
