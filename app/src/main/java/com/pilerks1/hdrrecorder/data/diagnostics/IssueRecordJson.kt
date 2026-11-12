package com.pilerks1.hdrrecorder.data.diagnostics

import org.json.JSONObject

internal fun encodeIssueRecord(issue: IssueRecord) = JSONObject().apply {
    put("id", issue.id); put("code", issue.code.name); put("time", issue.timestamp)
    put("operation", issue.operation); put("details", issue.details); put("count", issue.occurrences)
    put("settings", JSONObject(issue.settings))
}

internal fun decodeIssueRecord(json: JSONObject) = IssueRecord(
    json.getString("id").take(100), IssueCode.valueOf(json.getString("code")), json.getLong("time"),
    json.getString("operation").take(100), json.getString("details").take(10_000), json.optInt("count", 1).coerceAtLeast(1),
    json.optJSONObject("settings")?.let { settings ->
        SETTINGS_KEYS.filter { settings.has(it) }.associateWith { settings.getString(it).take(160) }
    }.orEmpty()
)

internal val SETTINGS_KEYS = setOf("resolution", "fps", "bitDepth", "encoding", "videoCodec", "stabilization")
