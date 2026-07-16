package com.pilerks1.hdrrecorder.model

/**
 * Defines the available video recording resolutions.
 * This enum provides a type-safe way to represent resolution options,
 * preventing the use of error-prone strings.
 */
enum class Resolution(val qualityName: String, val storageId: String) {
    FHD("FHD", "fhd"),
    UHD("UHD", "uhd"),
    HIGHEST("MAX", "highest");

    fun next(): Resolution = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Supports both the stable ID and values stored by older app versions. */
        fun fromStorageId(value: String?): Resolution? = entries.firstOrNull {
            it.storageId == value || it.qualityName == value || (it == HIGHEST && value == "Highest")
        }
    }
}
