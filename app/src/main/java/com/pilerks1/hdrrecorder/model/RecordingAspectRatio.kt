package com.pilerks1.hdrrecorder.model

import androidx.camera.core.AspectRatio

/** Recording and preview aspect ratios exposed by the camera UI. */
enum class RecordingAspectRatio(
    val displayLabel: String,
    val storageId: String,
    val width: Int,
    val height: Int,
    val cameraXValue: Int
) {
    FOUR_THREE("4:3", "4_3", 4, 3, AspectRatio.RATIO_4_3),
    SIXTEEN_NINE("16:9", "16_9", 16, 9, AspectRatio.RATIO_16_9);

    val landscapeRatio: Float
        get() = width.toFloat() / height.toFloat()

    fun next(): RecordingAspectRatio = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromStorageId(value: String?): RecordingAspectRatio? = entries.firstOrNull {
            it.storageId == value || it.displayLabel == value
        }
    }
}
