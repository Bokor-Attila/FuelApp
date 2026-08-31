package com.bokor.fuelapp.domain

import com.bokor.fuelapp.data.FuelEntry
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** One completed tank-to-tank measurement, ending at the full fill-up on [date]. */
data class TankConsumption(
    val date: Long,
    val odometer: Double,
    val distance: Double,
    val consumption: Double
)

/**
 * Splits the log into completed full-tank-to-full-tank windows. Partial refuels inside a
 * window contribute their litres to it; a trailing partial belongs to no completed window.
 */
fun tankConsumptions(entries: List<FuelEntry>): List<TankConsumption> {
    val sorted = entries.sortedBy { it.odometer }
    val result = mutableListOf<TankConsumption>()

    var accumulatedLiters = 0.0
    var lastFull: FuelEntry? = null

    for (entry in sorted) {
        val previousFull = lastFull
        if (previousFull == null) {
            if (entry.isFull) lastFull = entry
            continue
        }

        accumulatedLiters += entry.liters

        if (entry.isFull) {
            val distance = entry.odometer - previousFull.odometer
            if (distance > 0) {
                result.add(
                    TankConsumption(
                        date = entry.date,
                        odometer = entry.odometer,
                        distance = distance,
                        consumption = (accumulatedLiters / distance) * 100
                    )
                )
            }
            lastFull = entry
            accumulatedLiters = 0.0
        }
    }
    return result
}

data class MonthlySpend(val month: YearMonth, val cost: Double, val liters: Double)

private fun FuelEntry.localDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(date).atZone(zone).toLocalDate()

/** Spend and volume per calendar month, most recent first. */
fun monthlySpending(
    entries: List<FuelEntry>,
    zone: ZoneId = ZoneId.systemDefault()
): List<MonthlySpend> =
    entries.groupBy { YearMonth.from(it.localDate(zone)) }
        .map { (month, monthEntries) ->
            MonthlySpend(
                month = month,
                cost = monthEntries.sumOf { it.totalCost },
                liters = monthEntries.sumOf { it.liters }
            )
        }
        .sortedByDescending { it.month }

data class SpendingSummary(
    val thisMonth: Double = 0.0,
    val lastMonth: Double = 0.0,
    val yearToDate: Double = 0.0,
    val monthlyAverage: Double = 0.0
)

fun spendingSummary(
    entries: List<FuelEntry>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): SpendingSummary {
    if (entries.isEmpty()) return SpendingSummary()

    val months = monthlySpending(entries, zone)
    val current = YearMonth.from(today)

    return SpendingSummary(
        thisMonth = months.firstOrNull { it.month == current }?.cost ?: 0.0,
        lastMonth = months.firstOrNull { it.month == current.minusMonths(1) }?.cost ?: 0.0,
        yearToDate = entries.filter { it.localDate(zone).year == today.year }.sumOf { it.totalCost },
        // Averaged over the months that actually have fill-ups, not the calendar span.
        monthlyAverage = if (months.isEmpty()) 0.0 else months.sumOf { it.cost } / months.size
    )
}

data class UsageSummary(
    val kmPerMonth: Double = 0.0,
    val litersPerMonth: Double = 0.0,
    val averageDaysBetweenFillUps: Double = 0.0,
    val daysUntilNextFillUp: Int? = null
)

fun usageSummary(
    entries: List<FuelEntry>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): UsageSummary {
    if (entries.size < 2) return UsageSummary()

    val byDate = entries.sortedBy { it.date }
    val first = byDate.first().localDate(zone)
    val last = byDate.last().localDate(zone)
    val spanDays = ChronoUnit.DAYS.between(first, last).toDouble()
    if (spanDays <= 0) return UsageSummary()

    val byOdometer = entries.sortedBy { it.odometer }
    val distance = byOdometer.last().odometer - byOdometer.first().odometer
    // The first fill-up filled a tank burned before the log started, so it is not counted.
    val liters = byDate.drop(1).sumOf { it.liters }
    val months = spanDays / 30.44

    val averageGap = spanDays / (entries.size - 1)
    val daysSinceLast = ChronoUnit.DAYS.between(last, today)
    val daysLeft = (averageGap - daysSinceLast).toInt()

    return UsageSummary(
        kmPerMonth = if (months > 0) distance / months else 0.0,
        litersPerMonth = if (months > 0) liters / months else 0.0,
        averageDaysBetweenFillUps = averageGap,
        daysUntilNextFillUp = daysLeft.takeIf { averageGap > 0 }
    )
}

data class PriceSummary(
    val cheapest: FuelEntry,
    val dearest: FuelEntry,
    val latest: FuelEntry,
    val averagePrice: Double
) {
    /** How far the most recent price sits above (+) or below (-) the running average. */
    val latestVsAveragePercent: Double
        get() = if (averagePrice <= 0) 0.0 else (latest.pricePerLiter - averagePrice) / averagePrice * 100
}

fun priceSummary(entries: List<FuelEntry>): PriceSummary? {
    if (entries.isEmpty()) return null
    return PriceSummary(
        cheapest = entries.minBy { it.pricePerLiter },
        dearest = entries.maxBy { it.pricePerLiter },
        latest = entries.maxBy { it.date },
        averagePrice = entries.map { it.pricePerLiter }.average()
    )
}

data class ConsumptionTrend(
    val latestTank: TankConsumption,
    val previousAverage: Double
) {
    val changePercent: Double
        get() = if (previousAverage <= 0) 0.0 else (latestTank.consumption - previousAverage) / previousAverage * 100

    /** Ignores noise: only a meaningful swing is worth showing as a trend. */
    val isSignificant: Boolean get() = abs(changePercent) >= 5.0
}

/** Compares the most recent completed tank against the average of the ones before it. */
fun consumptionTrend(entries: List<FuelEntry>): ConsumptionTrend? {
    val tanks = tankConsumptions(entries).sortedBy { it.odometer }
    if (tanks.size < 2) return null
    val latest = tanks.last()
    val previous = tanks.dropLast(1)
    return ConsumptionTrend(latestTank = latest, previousAverage = previous.map { it.consumption }.average())
}
