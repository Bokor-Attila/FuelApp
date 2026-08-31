package com.bokor.fuelapp

import com.bokor.fuelapp.data.FuelEntry
import com.bokor.fuelapp.domain.calculateConsumption
import com.bokor.fuelapp.domain.calculateStats
import org.junit.Assert.assertEquals
import org.junit.Test

class FuelStatsTest {

    @Test
    fun testCalculateStatsWithPartialRefuels() {
        val entries = listOf(
            FuelEntry(id = 1, date = 1000, odometer = 1000.0, liters = 50.0, pricePerLiter = 6.0, totalCost = 300.0, isFull = true),
            FuelEntry(id = 2, date = 2000, odometer = 1500.0, liters = 20.0, pricePerLiter = 6.0, totalCost = 120.0, isFull = false),
            FuelEntry(id = 3, date = 3000, odometer = 2000.0, liters = 30.0, pricePerLiter = 6.0, totalCost = 180.0, isFull = true)
        )

        val stats = calculateStats(entries)

        // Distance: 2000 - 1000 = 1000 km
        // Liters after first full: 20 (partial) + 30 (full) = 50 L
        // Consumption: (50 / 1000) * 100 = 5.0 L/100km
        
        assertEquals(1000.0, stats.totalDistance, 0.1)
        assertEquals(5.0, stats.avgConsumption, 0.1)
    }

    @Test
    fun testCalculateConsumptionWithPartialRefuels() {
        val entries = listOf(
            FuelEntry(id = 1, date = 1000, odometer = 1000.0, liters = 50.0, pricePerLiter = 6.0, totalCost = 300.0, isFull = true),
            FuelEntry(id = 2, date = 2000, odometer = 1500.0, liters = 20.0, pricePerLiter = 6.0, totalCost = 120.0, isFull = false),
            FuelEntry(id = 3, date = 3000, odometer = 2000.0, liters = 30.0, pricePerLiter = 6.0, totalCost = 180.0, isFull = true)
        )

        val consumption = calculateConsumption(entries)
        assertEquals(5.0, consumption, 0.1)
    }

    /**
     * A partial refuel logged after the last full tank is not covered by the
     * first-full -> last-full distance, so its liters must not be counted.
     */
    @Test
    fun statsIgnoreLitersAfterLastFullTank() {
        val entries = listOf(
            FuelEntry(id = 1, date = 1000, odometer = 1000.0, liters = 50.0, pricePerLiter = 6.0, totalCost = 300.0, isFull = true),
            FuelEntry(id = 2, date = 2000, odometer = 2000.0, liters = 50.0, pricePerLiter = 6.0, totalCost = 300.0, isFull = true),
            FuelEntry(id = 3, date = 3000, odometer = 2400.0, liters = 20.0, pricePerLiter = 6.0, totalCost = 120.0, isFull = false)
        )

        val stats = calculateStats(entries)

        // Distance: 2000 - 1000 = 1000 km. Liters in that window: 50 (entry 2) only.
        // The trailing 20 L partial belongs to distance not yet measured.
        assertEquals(1000.0, stats.totalDistance, 0.1)
        assertEquals(5.0, stats.avgConsumption, 0.1)
    }

    /** calculateStats and calculateConsumption must agree on the same data set. */
    @Test
    fun statsAndConsumptionAgree() {
        val entries = listOf(
            FuelEntry(id = 1, date = 1000, odometer = 1000.0, liters = 50.0, pricePerLiter = 6.0, totalCost = 300.0, isFull = true),
            FuelEntry(id = 2, date = 2000, odometer = 1500.0, liters = 20.0, pricePerLiter = 6.0, totalCost = 120.0, isFull = false),
            FuelEntry(id = 3, date = 3000, odometer = 2000.0, liters = 30.0, pricePerLiter = 6.0, totalCost = 180.0, isFull = true),
            FuelEntry(id = 4, date = 4000, odometer = 2400.0, liters = 20.0, pricePerLiter = 6.0, totalCost = 120.0, isFull = false)
        )

        assertEquals(calculateConsumption(entries), calculateStats(entries).avgConsumption, 0.0001)
    }

    /**
     * The widget renders whatever calculateConsumption returns; it must respect
     * isFull rather than spanning the raw first/last entry.
     */
    @Test
    fun consumptionIgnoresPartialTailAndUsesFullTankWindow() {
        val entries = listOf(
            FuelEntry(id = 1, date = 1000, odometer = 1000.0, liters = 50.0, pricePerLiter = 6.0, totalCost = 300.0, isFull = true),
            FuelEntry(id = 2, date = 2000, odometer = 2000.0, liters = 60.0, pricePerLiter = 6.0, totalCost = 360.0, isFull = true),
            FuelEntry(id = 3, date = 3000, odometer = 2400.0, liters = 20.0, pricePerLiter = 6.0, totalCost = 120.0, isFull = false)
        )

        // Full-tank window: 60 L over 1000 km = 6.0. The widget's old private algorithm
        // spanned the raw first/last entry (80 L over 1400 km = 5.71) and ignored isFull.
        assertEquals(6.0, calculateConsumption(entries), 0.01)
    }

    /** No second full tank means there is no measurable window yet. */
    @Test
    fun consumptionIsZeroWithoutTwoFullTanks() {
        val entries = listOf(
            FuelEntry(id = 1, date = 1000, odometer = 1000.0, liters = 50.0, pricePerLiter = 6.0, totalCost = 300.0, isFull = true),
            FuelEntry(id = 2, date = 2000, odometer = 1500.0, liters = 20.0, pricePerLiter = 6.0, totalCost = 120.0, isFull = false)
        )

        assertEquals(0.0, calculateConsumption(entries), 0.0001)
        assertEquals(0.0, calculateStats(entries).avgConsumption, 0.0001)
    }

    /** Range prediction must follow the vehicle's tank, not a baked-in 50 L guess. */
    @Test
    fun rangeUsesTheVehicleTankCapacity() {
        val entries = listOf(
            FuelEntry(id = 1, date = 1000, odometer = 1000.0, liters = 50.0, pricePerLiter = 6.0, totalCost = 300.0, isFull = true),
            FuelEntry(id = 2, date = 2000, odometer = 2000.0, liters = 50.0, pricePerLiter = 6.0, totalCost = 300.0, isFull = true)
        )

        // 5 L/100km, 60 L tank -> 1200 km
        assertEquals(1200.0, calculateStats(entries, tankCapacity = 60.0).predictedRange, 0.1)
        // 5 L/100km, 40 L tank -> 800 km
        assertEquals(800.0, calculateStats(entries, tankCapacity = 40.0).predictedRange, 0.1)
    }

    /** Without a capacity there is nothing to predict, and the card hides on zero. */
    @Test
    fun rangeIsZeroWhenTankCapacityIsUnknown() {
        val entries = listOf(
            FuelEntry(id = 1, date = 1000, odometer = 1000.0, liters = 50.0, pricePerLiter = 6.0, totalCost = 300.0, isFull = true),
            FuelEntry(id = 2, date = 2000, odometer = 2000.0, liters = 50.0, pricePerLiter = 6.0, totalCost = 300.0, isFull = true)
        )

        assertEquals(0.0, calculateStats(entries, tankCapacity = null).predictedRange, 0.0001)
        assertEquals(0.0, calculateStats(entries, tankCapacity = 0.0).predictedRange, 0.0001)
    }
}
