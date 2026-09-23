package com.bokor.fuelapp.domain

import com.bokor.fuelapp.data.FuelEntry
import java.time.ZoneId

/**
 * Drops imported rows that are already logged, and repeats within the import itself.
 * CSV keeps only the day while the database keeps a timestamp, so rows are matched on
 * vehicle, calendar day, odometer and liters. The doubles compare exactly because the
 * export writes them with [Double.toString], which round-trips.
 */
fun dedupe(
    rows: List<FuelEntry>,
    existing: List<FuelEntry>,
    zone: ZoneId = ZoneId.systemDefault()
): List<FuelEntry> {
    fun FuelEntry.key() = listOf(vehicleId, localDay(date, zone), odometer, liters)

    val seen = existing.mapTo(HashSet()) { it.key() }
    return rows.filter { seen.add(it.key()) }
}
