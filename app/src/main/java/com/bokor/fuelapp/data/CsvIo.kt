package com.bokor.fuelapp.data

import android.content.Intent
import androidx.compose.foundation.layout.size
import androidx.core.content.FileProvider
import com.bokor.fuelapp.data.FuelEntry
import com.bokor.fuelapp.data.Vehicle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val CSV_HEADER = "Date,Odometer,Liters,PricePerLiter,TotalCost,isFull,Vehicle"

/** Serialises entries with their vehicle name. Commas in names are flattened to spaces. */
fun buildCsv(rows: List<Pair<FuelEntry, String>>): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val body = rows.joinToString("\n") { (entry, vehicleName) ->
        "${dateFormat.format(Date(entry.date))},${entry.odometer},${entry.liters},${entry.pricePerLiter}," +
            "${entry.totalCost},${if (entry.isFull) 1 else 0},${vehicleName.replace(',', ' ')}"
    }
    return CSV_HEADER + "\n" + body
}

/** @return true when the share sheet was launched, false when the file could not be written. */
fun exportEntriesToCsv(context: android.content.Context, rows: List<Pair<FuelEntry, String>>): Boolean {
    val fullCsv = buildCsv(rows)

    return try {
        val fileName = "fuel_log_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        file.writeText(fullCsv)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Fuel Data"))
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

/** A parsed CSV row. [vehicleName] is null for exports made before vehicles existed. */
data class ImportedEntry(val entry: FuelEntry, val vehicleName: String?)

/**
 * Parses exported CSV. The first line is assumed to be a header and skipped. The trailing
 * Vehicle column is optional so that files written before vehicles existed still import.
 */
fun parseCsv(lines: Sequence<String>): List<ImportedEntry> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val entries = mutableListOf<ImportedEntry>()

    lines.drop(1).forEach { line ->
        val parts = line.split(",")
        if (parts.size < 5) return@forEach

        val date = runCatching { dateFormat.parse(parts[0])?.time }.getOrNull() ?: System.currentTimeMillis()
        val odo = parts[1].toDoubleOrNull() ?: 0.0
        val liters = parts[2].toDoubleOrNull() ?: 0.0
        val price = parts[3].toDoubleOrNull() ?: 0.0
        val total = parts[4].toDoubleOrNull() ?: (liters * price)
        val isFull = if (parts.size > 5) parts[5].trim() == "1" else true
        val vehicleName = if (parts.size > 6) parts[6].trim().ifBlank { null } else null

        entries.add(
            ImportedEntry(
                entry = FuelEntry(
                    date = date,
                    odometer = odo,
                    liters = liters,
                    pricePerLiter = price,
                    totalCost = total,
                    isFull = isFull
                ),
                vehicleName = vehicleName
            )
        )
    }
    return entries
}

/** @return the parsed rows, or null when the file could not be read at all. */
fun importEntriesFromCsv(context: android.content.Context, uri: android.net.Uri): List<ImportedEntry>? = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        parseCsv(input.bufferedReader().lineSequence())
    }
} catch (e: Exception) {
    e.printStackTrace()
    null
}
