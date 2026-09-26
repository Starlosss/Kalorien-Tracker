package com.kalorientracker.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GtinTest {
    @Test
    fun upcAIsAlsoTriedAsEan13() {
        assertEquals(listOf("012345678905", "0012345678905"), Gtin.candidates("012345678905"))
    }

    @Test
    fun ean13WithALeadingZeroIsAlsoTriedAsUpcA() {
        assertEquals(listOf("0012345678905", "012345678905"), Gtin.candidates("0012345678905"))
    }

    @Test
    fun ean8StaysAsItIs() {
        assertEquals(listOf("40123455"), Gtin.candidates("40123455"))
    }

    @Test
    fun spacesAndDashesAreIgnored() {
        assertEquals(listOf("4001686301203"), Gtin.candidates(" 4001686-301203 "))
    }

    @Test
    fun checkDigitIsVerified() {
        assertTrue(Gtin.isValid("4001686301203"))
        assertFalse(Gtin.isValid("4001686301204"))
        assertFalse(Gtin.isValid("123"))
    }
}
