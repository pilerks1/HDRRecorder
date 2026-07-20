package com.pilerks1.hdrrecorder.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraRotationPolicyTest {
    @Test
    fun idleDisplayRotationUpdatesExistingUseCases() {
        assertEquals(3, CameraRotationPolicy.targetRotation(displayRotation = 3, isRecording = false))
    }

    @Test
    fun recordingFreezesCaptureRotation() {
        assertNull(CameraRotationPolicy.targetRotation(displayRotation = 1, isRecording = true))
    }
}
