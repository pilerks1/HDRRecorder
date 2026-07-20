package com.pilerks1.hdrrecorder.ui.viewmodels

/** Keeps capture rotation frozen for the duration of a recording. */
internal object CameraRotationPolicy {
    fun targetRotation(displayRotation: Int, isRecording: Boolean): Int? =
        displayRotation.takeUnless { isRecording }
}
