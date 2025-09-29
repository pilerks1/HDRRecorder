package com.pilerks1.hdrrecorder.compatibility

/**
 * Data class to hold the results of the compatibility check.
 * This structure is designed to populate the detailed, color-coded UI table.
 */
data class CompatibilityResult(
    val hardwareLevel: String,
    val maxBitrate: String,
    val tableRows4by3: List<TableRow>,
    val tableRows16by9: List<TableRow>
) {
    /**
     * Represents a single row in the compatibility table (e.g., "UHD").
     * Each FPS column contains a list of supported dynamic ranges for that specific configuration.
     */
    data class TableRow(
        val quality: String,
        val resolution: String,
        val fps24: List<DynamicRangeSupport>,
        val fps30: List<DynamicRangeSupport>,
        val fps60: List<DynamicRangeSupport>
    )

    /**
     * Represents a single dynamic range format and whether it supports stabilization
     * in a given configuration.
     * @param name The name of the dynamic range (e.g., "HLG", "HDR10+").
     * @param stabilizationSupported True if stabilization is available, false otherwise.
     */
    data class DynamicRangeSupport(
        val name: String,
        val stabilizationSupported: Boolean
    )
}
