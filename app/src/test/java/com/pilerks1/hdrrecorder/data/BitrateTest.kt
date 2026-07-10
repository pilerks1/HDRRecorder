package com.pilerks1.hdrrecorder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BitrateTest {
    @Test
    fun parseAcceptsTheSupportedRange() {
        assertEquals(1, Bitrate.parse("1"))
        assertEquals(999, Bitrate.parse("999"))
    }

    @Test
    fun parseRejectsBlankZeroNegativeAndOversizedValues() {
        assertNull(Bitrate.parse(""))
        assertNull(Bitrate.parse("0"))
        assertNull(Bitrate.parse("-1"))
        assertNull(Bitrate.parse("1000"))
    }

    @Test
    fun parseOrDefaultHandlesInvalidPersistedValues() {
        assertEquals(Bitrate.DEFAULT_MBPS, Bitrate.parseOrDefault("1000"))
    }
}
