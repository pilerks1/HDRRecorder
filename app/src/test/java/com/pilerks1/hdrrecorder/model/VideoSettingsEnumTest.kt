package com.pilerks1.hdrrecorder.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoSettingsEnumTest {
    @Test
    fun colorFormatReadsStableAndLegacyPresetValues() {
        assertEquals(ColorFormat.HDR10_PLUS, ColorFormat.fromStorageId("hdr10_plus"))
        assertEquals(ColorFormat.HDR10_PLUS, ColorFormat.fromStorageId("HDR10+"))
        assertNull(ColorFormat.fromStorageId("unsupported"))
    }

    @Test
    fun gammaCurveReadsStableAndLegacyPresetValues() {
        assertEquals(GammaCurve.AUTO, GammaCurve.fromStorageId("auto"))
        assertEquals(GammaCurve.AUTO, GammaCurve.fromStorageId("Auto"))
    }

    @Test
    fun resolutionReadsThePreviousHighestPresetName() {
        assertEquals(Resolution.HIGHEST, Resolution.fromStorageId("highest"))
        assertEquals(Resolution.HIGHEST, Resolution.fromStorageId("MAX"))
        assertEquals(Resolution.HIGHEST, Resolution.fromStorageId("Highest"))
    }
}
