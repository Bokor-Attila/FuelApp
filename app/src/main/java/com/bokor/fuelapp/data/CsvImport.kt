package com.bokor.fuelapp.data

import androidx.room.withTransaction
import com.bokor.fuelapp.domain.dedupe

data class ImportResult(val inserted: Int, val skipped: Int)

/**
 * Writes parsed CSV rows in a single transaction, so a failure part way leaves neither
 * half the rows nor vehicles created for them. Rows naming a vehicle are matched to it by
 * name, creating it when unknown; rows without a name land on [fallbackVehicleId].
 * Rows already in the log are skipped, which makes re-importing a file harmless.
 */
suspend fun FuelDatabase.importRows(rows: List<ImportedEntry>, fallbackVehicleId: Int): ImportResult =
    withTransaction {
        val byName = vehicleDao().getAllVehiclesOnce().associate { it.name to it.id }.toMutableMap()

        val resolved = rows.map { row ->
            val name = row.vehicleName?.takeIf { it.isNotBlank() }
            val vehicleId = when {
                name == null -> fallbackVehicleId
                byName.containsKey(name) -> byName.getValue(name)
                else -> vehicleDao().insert(Vehicle(name = name)).toInt().also { byName[name] = it }
            }
            row.entry.copy(vehicleId = vehicleId)
        }

        val fresh = dedupe(resolved, fuelDao().getAllEntriesOnce())
        fuelDao().insertAll(fresh)
        ImportResult(inserted = fresh.size, skipped = resolved.size - fresh.size)
    }
