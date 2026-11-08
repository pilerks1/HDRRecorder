package com.pilerks1.hdrrecorder.data.camera.core

/** An estimated native-FOV ratio, not a command to activate a physical sensor. */
data class ZoomShortcut(
    val cameraId: String,
    val zoomRatio: Float
)

data class DefaultCameraInventory(
    val defaultCameraId: String,
    val physicalCameraIds: Set<String> = emptySet(),
    val shortcuts: List<ZoomShortcut> = emptyList()
)

data class CameraZoomState(
    val inventory: DefaultCameraInventory? = null,
    val ratio: Float = 1f,
    val minRatio: Float = 1f,
    val maxRatio: Float = 1f,
    val isReady: Boolean = false
) {
    val linearZoom: Float get() = ZoomMapping.progress(ratio, minRatio, maxRatio)

    val shortcuts: List<ZoomShortcut>
        get() {
            val discovered = inventory ?: return emptyList()
            val reachable = discovered.shortcuts.filter {
                it.zoomRatio.isFinite() && it.zoomRatio > 0f && it.zoomRatio in minRatio..maxRatio
            }.sortedBy { it.zoomRatio }
            // Nonlogical cameras and logical cameras without usable child ratios retain 1×.
            return reachable.ifEmpty { listOf(ZoomShortcut(discovered.defaultCameraId, 1f)) }
        }
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
