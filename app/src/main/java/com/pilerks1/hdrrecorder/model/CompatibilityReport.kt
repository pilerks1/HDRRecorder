package com.pilerks1.hdrrecorder.model

enum class CompatibilityStatus(val label: String, val symbol: String) {
    SUPPORTED("Supported", "✓"),
    UNSUPPORTED("Unsupported", "×"),
    CONDITIONAL("Conditional", "◐"),
    UNKNOWN("Unknown", "?"),
    NOT_REPORTED("Not reported", "—")
}

data class CompatibilityTable(
    val title: String,
    val columns: List<String>,
    val rows: List<List<String>>
)

data class CompatibilityColor(val encoding: String, val depth: Int, val label: String)

data class CompatibilityConfiguration(
    val aspectRatio: String,
    val codec: String?,
    val stabilization: Boolean,
    val resolution: String,
    val encoding: String,
    val depth: Int,
    val fps: Int
) {
    val key: String get() = listOf(aspectRatio, codec ?: "auto", stabilization.toString(),
        resolution, encoding, depth.toString(), fps.toString()).joinToString("|")

    companion object {
        /** Recording frame rates evaluated by every compatibility scan. */
        val SCAN_FPS = listOf(24, 30, 60)
    }
}

/** AE boost is a reported runtime capability, not a SessionConfig query parameter. */
data class CompatibilityCombination(
    val configuration: CompatibilityConfiguration,
    val status: CompatibilityStatus,
    val boostStatus: CompatibilityStatus
)

data class CompatibilityReport(
    val environment: String,
    val cameraId: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val total: Int,
    val videoCodecs: List<String>,
    val audioCodecs: List<String>,
    val colors: List<CompatibilityColor>,
    val cameraX: List<CompatibilityTable>,
    val camera2: List<CompatibilityTable>,
    val combinations: List<CompatibilityCombination> = emptyList()
)
