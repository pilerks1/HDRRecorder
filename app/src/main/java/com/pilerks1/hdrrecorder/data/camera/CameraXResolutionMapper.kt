package com.pilerks1.hdrrecorder.data.camera

import androidx.camera.video.Quality
import com.pilerks1.hdrrecorder.model.Resolution

/** The only translation between the app's resolution enum and CameraX's Quality type. */
fun Resolution.toCameraXQuality(): Quality = when (this) {
    Resolution.FHD -> Quality.FHD
    Resolution.UHD -> Quality.UHD
    Resolution.HIGHEST -> Quality.HIGHEST
}
