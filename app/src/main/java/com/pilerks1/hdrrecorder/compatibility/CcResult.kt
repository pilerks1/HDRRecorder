package com.pilerks1.hdrrecorder.compatibility

data class CompatibilityResult(
    val hardwareLevel: String,
    val maxBitrate: String,
    val tableRows: List<TableRow>
) {
    data class TableRow(
        val quality: String,
        val aspectRatio: String,
        val resolution: String,
        val hlgFrameRates: String,
        val hdrCapabilities: String
    )
}
