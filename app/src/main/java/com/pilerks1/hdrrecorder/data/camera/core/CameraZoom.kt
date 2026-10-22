package com.pilerks1.hdrrecorder.data.camera.core

import android.util.Range

/** Camera identities and zoom facts shared by discovery, compatibility and capture backends. */
data class RearLens(
    val cameraId: String,
    val zoomRatio: Float?,
    val logicalParentIds: Set<String>,
    val zoomRatioRange: Range<Float>?,
    val maxDigitalZoom: Float?,
    val isLogical: Boolean
)

data class RearLensInventory(
    val defaultCameraId: String,
    val lenses: List<RearLens>
) {
    val buttonLenses: List<RearLens>
        get() = lenses.filter { lens ->
            lens.zoomRatio != null && (!lens.isLogical || lenses.none {
                it.cameraId != lens.cameraId && lens.cameraId in it.logicalParentIds &&
                    it.zoomRatio == lens.zoomRatio
            })
        }.sortedBy { it.zoomRatio }
}

data class CameraZoomState(
    val inventory: RearLensInventory? = null,
    val ratio: Float = 1f,
    val minRatio: Float = 1f,
    val maxRatio: Float = 1f,
    val isReady: Boolean = false
) {
    val linearZoom: Float get() = ZoomMapping.progress(ratio, minRatio, maxRatio)

}

/** The reciprocal crop-width mapping used by CameraX linear zoom; no CameraX types required. */
object ZoomMapping {
    fun progress(ratio: Float, minimum: Float, maximum: Float): Float {
        if (maximum <= minimum) return 0f
        return ((1f / minimum - 1f / ratio.coerceIn(minimum, maximum)) /
            (1f / minimum - 1f / maximum)).coerceIn(0f, 1f)
    }

    fun ratio(progress: Float, minimum: Float, maximum: Float): Float {
        if (maximum <= minimum) return minimum
        return (1f / (1f / minimum - progress.coerceIn(0f, 1f) *
            (1f / minimum - 1f / maximum))).coerceIn(minimum, maximum)
    }
}
