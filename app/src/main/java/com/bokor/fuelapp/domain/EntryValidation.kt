package com.bokor.fuelapp.domain

import com.bokor.fuelapp.data.FuelEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Why an odometer reading does not fit the existing log. */
sealed interface OdometerError {
    /** The reading must be above [above] and below [below]; a null side is unbounded. */
    data class OutOfRange(val above: Double?, val below: Double?) : OdometerError

    /** Another fill-up on the same day already has this exact reading. */
    data object Duplicate : OdometerError
}

/**
 * Checks that a reading fits between the fill-ups dated around it, so a forgotten fill-up
 * can be logged after the fact. Entries are compared by calendar day: the date picker keeps
 * the current time of day, so the order of two entries on the same day is arbitrary and
 * those only rule out an identical reading. [excludeId] is the entry being edited.
 *
 * @return null when the reading is acceptable.
 */
fun validateOdometer(
    odometer: Double,
    date: Long,
    entries: List<FuelEntry>,
    excludeId: Int? = null,
    zone: ZoneId = ZoneId.systemDefault()
): OdometerError? {
    val day = localDay(date, zone)
    val others = entries.filter { it.id != excludeId }

    val above = others.filter { localDay(it.date, zone) < day }.maxOfOrNull { it.odometer }
    val below = others.filter { localDay(it.date, zone) > day }.minOfOrNull { it.odometer }

    return when {
        (above != null && odometer <= above) || (below != null && odometer >= below) ->
            OdometerError.OutOfRange(above, below)
        others.any { localDay(it.date, zone) == day && it.odometer == odometer } -> OdometerError.Duplicate
        else -> null
    }
}

internal fun localDay(epochMillis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
