package com.pilerks1.hdrrecorder.compatibility

data class CompatibilityResult(
    val hardwareLevel: String,
    val maxBitrate: String,
    val tableRows4by3: List<TableRow>,
    val tableRows16by9: List<TableRow>
) {
    data class TableRow(
        val quality: String,
        val resolution: String,
        val fps24: String,
        val fps30: String,
        val fps60: String
    )
}

