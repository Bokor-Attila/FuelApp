package com.bokor.fuelapp.domain

import androidx.compose.foundation.layout.size
import com.bokor.fuelapp.data.FuelEntry

data class FuelStats(
    val avgConsumption: Double = 0.0,
    val bestConsumption: Double = 0.0,
    val worstConsumption: Double = 0.0,
    /** The tanks behind the best and worst figures, so the UI can date them. */
    val bestTank: TankConsumption? = null,
    val worstTank: TankConsumption? = null,
    val costPerKm: Double = 0.0,
    val avgDistance: Double = 0.0,
    val totalDistance: Double = 0.0,
    val totalLiters: Double = 0.0,
    val totalCost: Double = 0.0,
    val avgPrice: Double = 0.0,
    val predictedRange: Double = 0.0
)

/**
 * @param tankCapacity usable tank size in liters; null disables the range estimate.
 */
fun calculateStats(entries: List<FuelEntry>, tankCapacity: Double? = null): FuelStats {
    if (entries.isEmpty()) return FuelStats()
    
    val sorted = entries.sortedBy { it.odometer }
    
    // Find the first and last full entries for total distance calculation
    val fullEntries = sorted.filter { it.isFull }
    
    val totalDistance = if (fullEntries.size < 2) 0.0 else fullEntries.last().odometer - fullEntries.first().odometer
    
    // Only count liters burned between the first and the last full tank. Refuels logged after
    // the last full tank are not covered by totalDistance, so including them inflates the average.
    var totalLitersCalculated = 0.0
    if (fullEntries.size >= 2) {
        val firstFullOdo = fullEntries.first().odometer
        val lastFullOdo = fullEntries.last().odometer
        totalLitersCalculated = sorted
            .filter { it.odometer > firstFullOdo && it.odometer <= lastFullOdo }
            .sumOf { it.liters }
    }

    val totalCost = entries.sumOf { it.totalCost }
    
    val avgConsumption = if (totalDistance > 0) (totalLitersCalculated / totalDistance) * 100 else 0.0
    val avgPrice = if (entries.isEmpty()) 0.0 else entries.map { it.pricePerLiter }.average()
    val costPerKm = if (totalDistance > 0) totalCost / totalDistance else 0.0
    
    // Shared with the statistics screen so both read the same tank-to-tank windows.
    val tanks = tankConsumptions(entries)
    val best = tanks.minByOrNull { it.consumption }
    val worst = tanks.maxByOrNull { it.consumption }

    val predictedRange = if (avgConsumption > 0 && tankCapacity != null && tankCapacity > 0) {
        (tankCapacity / avgConsumption) * 100
    } else {
        0.0
    }
    
    return FuelStats(
        avgConsumption = avgConsumption,
        bestConsumption = best?.consumption ?: 0.0,
        worstConsumption = worst?.consumption ?: 0.0,
        bestTank = best,
        worstTank = worst,
        costPerKm = costPerKm,
        avgDistance = tanks.map { it.distance }.average().takeIf { !it.isNaN() } ?: 0.0,
        totalDistance = totalDistance,
        totalLiters = entries.sumOf { it.liters },
        totalCost = totalCost,
        avgPrice = avgPrice,
        predictedRange = predictedRange
    )
}

fun calculateConsumption(entries: List<FuelEntry>): Double {
    if (entries.size < 2) return 0.0
    val sorted = entries.sortedBy { it.odometer }
    val fullEntries = sorted.filter { it.isFull }
    if (fullEntries.size < 2) return 0.0
    
    val firstFullOdo = fullEntries.first().odometer
    val lastFullOdo = fullEntries.last().odometer
    val totalDistance = lastFullOdo - firstFullOdo
    
    if (totalDistance <= 0) return 0.0
    
    val totalLiters = sorted.filter { it.odometer > firstFullOdo && it.odometer <= lastFullOdo }.sumOf { it.liters }
    return (totalLiters / totalDistance) * 100
}
