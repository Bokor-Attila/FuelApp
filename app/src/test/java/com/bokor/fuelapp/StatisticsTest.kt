package com.bokor.fuelapp

import com.bokor.fuelapp.data.FuelEntry
import com.bokor.fuelapp.domain.consumptionTrend
import com.bokor.fuelapp.domain.monthlySpending
import com.bokor.fuelapp.domain.priceSummary
import com.bokor.fuelapp.domain.spendingSummary
import com.bokor.fuelapp.domain.tankConsumptions
import com.bokor.fuelapp.domain.usageSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class StatisticsTest {

    private val utc: ZoneId = ZoneId.of("UTC")

    private fun entry(
        date: String,
        odometer: Double,
        liters: Double,
        price: Double,
        isFull: Boolean = true
    ) = FuelEntry(
        date = LocalDate.parse(date).atStartOfDay(utc).toInstant().toEpochMilli(),
        odometer = odometer,
        liters = liters,
        pricePerLiter = price,
        totalCost = liters * price,
        isFull = isFull
    )

    private val log = listOf(
        entry("2026-01-10", 1000.0, 40.0, 7.0),
        entry("2026-01-25", 1500.0, 25.0, 8.0),
        entry("2026-02-14", 2000.0, 30.0, 6.0),
        entry("2026-03-05", 2600.0, 36.0, 7.5)
    )

    @Test
    fun monthlySpendingGroupsByCalendarMonthNewestFirst() {
        val months = monthlySpending(log, utc)

        assertEquals(3, months.size)
        assertEquals(YearMonth.of(2026, 3), months[0].month)
        assertEquals(270.0, months[0].cost, 0.01)
        assertEquals(YearMonth.of(2026, 1), months[2].month)
        // January holds two fill-ups: 40*7 + 25*8
        assertEquals(480.0, months[2].cost, 0.01)
        assertEquals(65.0, months[2].liters, 0.01)
    }

    @Test
    fun spendingSummarySplitsCurrentAndPreviousMonth() {
        val summary = spendingSummary(log, LocalDate.parse("2026-03-20"), utc)

        assertEquals(270.0, summary.thisMonth, 0.01)
        assertEquals(180.0, summary.lastMonth, 0.01)
        assertEquals(930.0, summary.yearToDate, 0.01)
        // Averaged over the three months that have fill-ups, not the calendar span
        assertEquals(310.0, summary.monthlyAverage, 0.01)
    }

    @Test
    fun yearToDateIgnoresOtherYears() {
        val withLastYear = log + entry("2025-11-01", 500.0, 50.0, 5.0)

        val summary = spendingSummary(withLastYear, LocalDate.parse("2026-03-20"), utc)

        assertEquals(930.0, summary.yearToDate, 0.01)
    }

    @Test
    fun usageSummaryMeasuresDistanceAndCadence() {
        val summary = usageSummary(log, LocalDate.parse("2026-03-12"), utc)

        // 1600 km over 54 days -> per 30.44-day month
        assertEquals(1600.0 / (54 / 30.44), summary.kmPerMonth, 0.5)
        assertEquals(18.0, summary.averageDaysBetweenFillUps, 0.01)
        // Last fill-up was 7 days ago, so about 11 days of the usual gap remain
        assertEquals(11, summary.daysUntilNextFillUp)
    }

    @Test
    fun usageSummaryNeedsTwoEntries() {
        val summary = usageSummary(log.take(1), LocalDate.parse("2026-03-12"), utc)

        assertEquals(0.0, summary.kmPerMonth, 0.001)
        assertNull(summary.daysUntilNextFillUp)
    }

    @Test
    fun priceSummaryFindsExtremesAndComparesTheLatest() {
        val summary = priceSummary(log)!!

        assertEquals(6.0, summary.cheapest.pricePerLiter, 0.01)
        assertEquals(8.0, summary.dearest.pricePerLiter, 0.01)
        assertEquals(7.5, summary.latest.pricePerLiter, 0.01)
        assertEquals(7.125, summary.averagePrice, 0.001)
        // 7.5 against an average of 7.125 is about 5.3% above
        assertEquals(5.26, summary.latestVsAveragePercent, 0.05)
    }

    @Test
    fun priceSummaryIsNullWithoutEntries() {
        assertNull(priceSummary(emptyList()))
    }

    @Test
    fun tankConsumptionsRollPartialRefuelsIntoTheirWindow() {
        val entries = listOf(
            entry("2026-01-01", 1000.0, 50.0, 6.0),
            entry("2026-01-20", 1500.0, 20.0, 6.0, isFull = false),
            entry("2026-02-01", 2000.0, 30.0, 6.0)
        )

        val tanks = tankConsumptions(entries)

        assertEquals(1, tanks.size)
        assertEquals(1000.0, tanks[0].distance, 0.01)
        // 20 L partial plus 30 L full over 1000 km
        assertEquals(5.0, tanks[0].consumption, 0.01)
    }

    @Test
    fun consumptionTrendComparesTheLastTankWithTheEarlierAverage() {
        val entries = listOf(
            entry("2026-01-01", 0.0, 50.0, 6.0),
            entry("2026-01-15", 1000.0, 50.0, 6.0),   // 5.0 L/100km
            entry("2026-02-01", 2000.0, 50.0, 6.0),   // 5.0 L/100km
            entry("2026-02-20", 3000.0, 70.0, 6.0)    // 7.0 L/100km
        )

        val trend = consumptionTrend(entries)!!

        assertEquals(7.0, trend.latestTank.consumption, 0.01)
        assertEquals(5.0, trend.previousAverage, 0.01)
        assertEquals(40.0, trend.changePercent, 0.01)
        assertTrue(trend.isSignificant)
    }

    @Test
    fun consumptionTrendNeedsTwoCompletedTanks() {
        assertNull(consumptionTrend(log.take(2)))
    }
}
