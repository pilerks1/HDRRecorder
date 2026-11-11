package com.pilerks1.hdrrecorder.data.camera.core

/** An estimated native-FOV ratio, not a command to activate a physical sensor. */
data class ZoomShortcut(
    val cameraId: String,
    val zoomRatio: Float
)

data class DefaultCameraInventory(
    val defaultCameraId: String,
    val shortcuts: List<ZoomShortcut> = emptyList()
)

data class CameraZoomState(
    val inventory: DefaultCameraInventory? = null,
    val ratio: Float = 1f,
    val minRatio: Float = 1f,
    val maxRatio: Float = 1f,
    val isReady: Boolean = false
) {
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
