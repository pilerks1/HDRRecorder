package com.pilerks1.hdrrecorder.data.compatibility

import com.pilerks1.hdrrecorder.model.*
import org.json.JSONArray
import org.json.JSONObject

/** The feature owns the document format; storage only owns atomic file operations. */
internal object CompatibilityReportJson {
    private const val SCHEMA_VERSION = 1

    fun encode(report: CompatibilityReport): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("environment", report.environment)
        put("cameraId", report.cameraId)
        put("startedAt", report.startedAt)
        put("completedAt", report.completedAt ?: JSONObject.NULL)
        put("total", report.total)
        put("videoCodecs", JSONArray(report.videoCodecs))
        put("audioCodecs", JSONArray(report.audioCodecs))
        put("colors", JSONArray(report.colors.map { color -> JSONObject().apply {
            put("encoding", color.encoding); put("depth", color.depth); put("label", color.label)
        } }))
        put("cameraX", tables(report.cameraX))
        put("camera2", tables(report.camera2))
        put("combinations", JSONArray(report.combinations.map { result -> JSONObject().apply {
            val config = result.configuration
            put("configuration", JSONObject().apply {
                put("aspectRatio", config.aspectRatio); put("codec", config.codec ?: JSONObject.NULL)
                put("stabilization", config.stabilization); put("resolution", config.resolution)
                put("encoding", config.encoding); put("depth", config.depth); put("fps", config.fps)
            })
            put("status", result.status.name); put("boostStatus", result.boostStatus.name)
        } }))
    }.toString()

    fun decode(json: String): CompatibilityReport {
        val root = JSONObject(json)
        require(root.getInt("schemaVersion") == SCHEMA_VERSION)
        return CompatibilityReport(
            environment = root.getString("environment"), cameraId = root.getString("cameraId"),
            startedAt = root.getLong("startedAt"),
            completedAt = if (root.isNull("completedAt")) null else root.getLong("completedAt"),
            total = root.getInt("total"), videoCodecs = root.getJSONArray("videoCodecs").strings(),
            audioCodecs = root.getJSONArray("audioCodecs").strings(),
            colors = root.getJSONArray("colors").objects().map {
                CompatibilityColor(it.getString("encoding"), it.getInt("depth"), it.getString("label"))
            },
            cameraX = readTables(root.getJSONArray("cameraX")),
            camera2 = readTables(root.getJSONArray("camera2")),
            combinations = root.getJSONArray("combinations").objects().map { item ->
                val config = item.getJSONObject("configuration")
                CompatibilityCombination(
                    CompatibilityConfiguration(
                        config.getString("aspectRatio"),
                        if (config.isNull("codec")) null else config.getString("codec"),
                        config.getBoolean("stabilization"), config.getString("resolution"),
                        config.getString("encoding"), config.getInt("depth"), config.getInt("fps")
                    ),
                    CompatibilityStatus.valueOf(item.getString("status")),
                    CompatibilityStatus.valueOf(item.getString("boostStatus"))
                )
            }
        ).also { report ->
            require(report.total > 0 && report.colors.isNotEmpty())
            require(report.colors.distinct().size == report.colors.size)
            require(report.colors.all { it.depth in listOf(8, 10) && VideoEncoding.fromStorageId(it.encoding) != null })
            require(report.total == RecordingAspectRatio.entries.size * (report.videoCodecs.size + 1) *
                2 * Resolution.entries.size * report.colors.size * CompatibilityConfiguration.SCAN_FPS.size)
            require(report.combinations.all { result ->
                val config = result.configuration
                RecordingAspectRatio.fromStorageId(config.aspectRatio) != null &&
                    Resolution.fromStorageId(config.resolution) != null && config.fps in CompatibilityConfiguration.SCAN_FPS &&
                    (config.codec == null || config.codec in report.videoCodecs) &&
                    report.colors.any { it.encoding == config.encoding && it.depth == config.depth }
            })
            require(report.combinations.size <= report.total)
            require(report.combinations.map { it.configuration.key }.distinct().size == report.combinations.size)
            require(report.completedAt == null || report.combinations.size == report.total)
        }
    }

    private fun tables(tables: List<CompatibilityTable>) = JSONArray(tables.map { table -> JSONObject().apply {
        put("title", table.title); put("columns", JSONArray(table.columns))
        put("rows", JSONArray(table.rows.map { JSONArray(it) }))
    } })

    private fun readTables(array: JSONArray) = array.objects().map { table ->
        val rows = table.getJSONArray("rows")
        CompatibilityTable(table.getString("title"), table.getJSONArray("columns").strings(),
            (0 until rows.length()).map { rows.getJSONArray(it).strings() }).also { result ->
            require(result.columns.isNotEmpty() && result.rows.all { it.size == result.columns.size })
        }
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
