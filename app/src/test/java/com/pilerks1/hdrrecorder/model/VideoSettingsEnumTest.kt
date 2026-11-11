package com.pilerks1.hdrrecorder.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoSettingsEnumTest {
    @Test
    fun encodingReadsStablePresetValues() {
        assertEquals(VideoEncoding.HDR10_PLUS, VideoEncoding.fromStorageId("hdr10_plus"))
        assertNull(VideoEncoding.fromStorageId("unsupported"))
    }

    @Test
    fun gammaCurveReadsStablePresetValues() {
        assertEquals(GammaCurve.HLG, GammaCurve.fromStorageId("hlg"))
        assertEquals(GammaCurve.PQ, GammaCurve.fromStorageId("pq"))
    }

    @Test
    fun resolutionReadsTheHighestStorageId() {
        assertEquals(Resolution.HIGHEST, Resolution.fromStorageId("highest"))
    }

    @Test
    fun recordingAspectRatioReadsStableValues() {
        assertEquals(RecordingAspectRatio.FOUR_THREE, RecordingAspectRatio.fromStorageId("4_3"))
        assertEquals(RecordingAspectRatio.SIXTEEN_NINE, RecordingAspectRatio.fromStorageId("16_9"))
        assertNull(RecordingAspectRatio.fromStorageId("unsupported"))
    }
}
