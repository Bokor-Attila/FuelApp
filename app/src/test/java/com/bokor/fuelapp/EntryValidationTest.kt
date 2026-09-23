package com.bokor.fuelapp

import com.bokor.fuelapp.data.FuelEntry
import com.bokor.fuelapp.domain.OdometerError
import com.bokor.fuelapp.domain.validateOdometer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class EntryValidationTest {

    private val zone = ZoneId.of("UTC")

    private fun at(value: String): Long =
        LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()

    private fun entry(id: Int, date: String, odometer: Double) =
        FuelEntry(id = id, date = at(date), odometer = odometer, liters = 40.0, pricePerLiter = 6.0, totalCost = 240.0)

    private val log = listOf(
        entry(1, "2026-01-10T08:00", 1000.0),
        entry(2, "2026-02-10T08:00", 2000.0),
        entry(3, "2026-03-10T08:00", 3000.0)
    )

    @Test
    fun aForgottenFillUpBetweenTwoEntriesIsAccepted() {
        assertNull(validateOdometer(1500.0, at("2026-01-25T12:00"), log, zone = zone))
    }

    @Test
    fun aBackdatedReadingOutsideItsNeighboursIsRejectedWithBothBounds() {
        assertEquals(
            OdometerError.OutOfRange(above = 1000.0, below = 2000.0),
            validateOdometer(2500.0, at("2026-01-25T12:00"), log, zone = zone)
        )
        assertEquals(
            OdometerError.OutOfRange(above = 1000.0, below = 2000.0),
            validateOdometer(900.0, at("2026-01-25T12:00"), log, zone = zone)
        )
    }

    @Test
    fun aNewLatestEntryOnlyHasALowerBound() {
        assertNull(validateOdometer(3100.0, at("2026-04-01T12:00"), log, zone = zone))
        assertEquals(
            OdometerError.OutOfRange(above = 3000.0, below = null),
            validateOdometer(3000.0, at("2026-04-01T12:00"), log, zone = zone)
        )
    }

    @Test
    fun aReadingBeforeTheFirstEntryOnlyHasAnUpperBound() {
        assertEquals(
            OdometerError.OutOfRange(above = null, below = 1000.0),
            validateOdometer(1200.0, at("2026-01-01T12:00"), log, zone = zone)
        )
    }

    @Test
    fun savingAnEditUnchangedIsAccepted() {
        assertNull(validateOdometer(2000.0, at("2026-02-10T08:00"), log, excludeId = 2, zone = zone))
    }

    @Test
    fun editingAnEntryIgnoresItsOwnOldReading() {
        // Moving entry 2 later and lowering its reading: its old 2000 on Feb 10 must not
        // count as an earlier fill-up.
        assertNull(validateOdometer(1900.0, at("2026-02-20T08:00"), log, excludeId = 2, zone = zone))
    }

    @Test
    fun editingAnEntryIsStillBoundByItsNeighbours() {
        assertEquals(
            OdometerError.OutOfRange(above = 1000.0, below = 3000.0),
            validateOdometer(3500.0, at("2026-02-10T08:00"), log, excludeId = 2, zone = zone)
        )
    }

    @Test
    fun entriesOnTheSameDayDoNotConstrainOrder() {
        // Logged at 18:00 on Feb 10 while the existing entry that day is at 08:00 — either
        // reading is valid, since the time of day from the picker is arbitrary.
        assertNull(validateOdometer(1900.0, at("2026-02-10T18:00"), log, zone = zone))
        assertNull(validateOdometer(2100.0, at("2026-02-10T06:00"), log, zone = zone))
    }

    @Test
    fun theSameReadingTwiceOnOneDayIsRejected() {
        assertEquals(
            OdometerError.Duplicate,
            validateOdometer(2000.0, at("2026-02-10T18:00"), log, zone = zone)
        )
    }

    @Test
    fun anEmptyLogAcceptsAnyReading() {
        assertNull(validateOdometer(1.0, at("2026-01-01T00:00"), emptyList(), zone = zone))
    }
}
