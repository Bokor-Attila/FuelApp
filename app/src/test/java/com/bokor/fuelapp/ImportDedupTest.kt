package com.bokor.fuelapp

import com.bokor.fuelapp.data.FuelEntry
import com.bokor.fuelapp.data.buildCsv
import com.bokor.fuelapp.data.parseCsv
import com.bokor.fuelapp.domain.dedupe
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ImportDedupTest {

    private val zone = ZoneId.systemDefault()

    private fun at(value: String): Long =
        LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()

    private fun entry(date: String, odometer: Double, liters: Double = 41.37, vehicleId: Int = 1) = FuelEntry(
        date = at(date), odometer = odometer, liters = liters, pricePerLiter = 612.9,
        totalCost = liters * 612.9, vehicleId = vehicleId
    )

    @Test
    fun reimportingAnExportAddsNothing() {
        // Timestamps carry a time of day that the CSV drops, so an exact date match would miss.
        val logged = listOf(
            entry("2026-01-05T17:42:13", 1000.0),
            entry("2026-02-10T08:03:55", 1523.4, liters = 38.06)
        )
        val parsed = parseCsv(buildCsv(logged.map { it to "Civic" }).lineSequence())
            .map { it.entry.copy(vehicleId = 1) }

        assertEquals(emptyList<FuelEntry>(), dedupe(parsed, logged, zone))
    }

    @Test
    fun aDifferentFillUpOnTheSameDayIsKept() {
        val logged = listOf(entry("2026-01-05T08:00", 1000.0))
        val row = entry("2026-01-05T00:00", 1350.0)

        assertEquals(listOf(row), dedupe(listOf(row), logged, zone))
    }

    @Test
    fun theSameFillUpOnAnotherVehicleIsKept() {
        val logged = listOf(entry("2026-01-05T08:00", 1000.0, vehicleId = 1))
        val row = entry("2026-01-05T00:00", 1000.0, vehicleId = 2)

        assertEquals(listOf(row), dedupe(listOf(row), logged, zone))
    }

    @Test
    fun repeatsWithinOneFileCollapseToOne() {
        val row = entry("2026-01-05T00:00", 1000.0)

        assertEquals(listOf(row), dedupe(listOf(row, row.copy()), emptyList(), zone))
    }
}
