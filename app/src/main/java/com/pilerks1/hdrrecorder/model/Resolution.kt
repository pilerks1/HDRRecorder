package com.pilerks1.hdrrecorder.model

import androidx.camera.video.Quality

/**
 * Defines the available video recording resolutions.
 * This sealed class provides a type-safe way to represent resolution options,
 * preventing the use of error-prone strings.
 */
sealed class Resolution(val quality: Quality, val qualityName: String) {
    object FHD : Resolution(Quality.FHD, "FHD")
    object UHD : Resolution(Quality.UHD, "UHD")
    object HIGHEST : Resolution(Quality.HIGHEST, "Highest")
}
