package com.bokor.fuelapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The scanner used to concatenate every digit in the frame into one number. A dashboard
 * shows a trip meter, a clock and a temperature too, so the longest run is the odometer.
 */
class OdometerReadingTest {

    @Test
    fun picksTheLongestDigitRun() {
        assertEquals("125430", longestDigitRun("TRIP 342.1  125430 km  14:05"))
    }

    @Test
    fun ignoresSurroundingText() {
        assertEquals("98765", longestDigitRun("ODO 98765"))
    }

    @Test
    fun returnsNullWhenThereAreNoDigits() {
        assertNull(longestDigitRun("no numbers here"))
        assertNull(longestDigitRun(""))
    }

    /** The old behaviour: every digit concatenated, producing nonsense. */
    @Test
    fun doesNotConcatenateSeparateNumbers() {
        val text = "12:45  23.5C  87654 km"
        assertEquals("87654", longestDigitRun(text))
    }
}
