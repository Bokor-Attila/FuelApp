package com.bokor.fuelapp

import com.bokor.fuelapp.domain.toAmountOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Hungarian and Romanian keyboards emit a decimal comma. Before this parser existed the
 * dialog read those values as null, silently treated them as 0 and refused to save.
 */
class AmountParsingTest {

    @Test
    fun commaDecimalsParse() {
        assertEquals(6.5, "6,5".toAmountOrNull()!!, 0.0001)
        assertEquals(45.25, "45,25".toAmountOrNull()!!, 0.0001)
    }

    @Test
    fun dotDecimalsStillParse() {
        assertEquals(6.5, "6.5".toAmountOrNull()!!, 0.0001)
        assertEquals(1234.0, "1234".toAmountOrNull()!!, 0.0001)
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertEquals(6.5, "  6,5  ".toAmountOrNull()!!, 0.0001)
    }

    @Test
    fun nonNumbersReturnNull() {
        assertNull("".toAmountOrNull())
        assertNull("   ".toAmountOrNull())
        assertNull("abc".toAmountOrNull())
    }
}
