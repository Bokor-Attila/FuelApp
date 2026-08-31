package com.bokor.fuelapp

import com.bokor.fuelapp.data.CSV_HEADER
import com.bokor.fuelapp.data.FuelEntry
import com.bokor.fuelapp.data.buildCsv
import com.bokor.fuelapp.data.parseCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class CsvTest {

    private fun dateOf(value: String): Long =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(value)!!.time

    @Test
    fun exportedCsvRoundTripsThroughTheParser() {
        val rows = listOf(
            FuelEntry(id = 1, date = dateOf("2026-01-05"), odometer = 1000.0, liters = 50.0, pricePerLiter = 6.0, totalCost = 300.0, isFull = true) to "Civic",
            FuelEntry(id = 2, date = dateOf("2026-02-10"), odometer = 1500.0, liters = 20.0, pricePerLiter = 6.5, totalCost = 130.0, isFull = false) to "Van"
        )

        val parsed = parseCsv(buildCsv(rows).lineSequence())

        assertEquals(2, parsed.size)
        assertEquals("Civic", parsed[0].vehicleName)
        assertEquals("Van", parsed[1].vehicleName)
        assertEquals(1000.0, parsed[0].entry.odometer, 0.001)
        assertEquals(6.5, parsed[1].entry.pricePerLiter, 0.001)
        assertEquals(true, parsed[0].entry.isFull)
        assertEquals(false, parsed[1].entry.isFull)
        assertEquals(dateOf("2026-01-05"), parsed[0].entry.date)
    }

    /** Files exported before vehicles existed have six columns and must still load. */
    @Test
    fun legacyCsvWithoutVehicleColumnParses() {
        val csv = """
            Date,Odometer,Liters,PricePerLiter,TotalCost,isFull
            2026-01-05,1000.0,50.0,6.0,300.0,1
            2026-02-10,1500.0,20.0,6.5,130.0,0
        """.trimIndent()

        val parsed = parseCsv(csv.lineSequence())

        assertEquals(2, parsed.size)
        assertNull(parsed[0].vehicleName)
        assertNull(parsed[1].vehicleName)
        assertEquals(50.0, parsed[0].entry.liters, 0.001)
        assertEquals(false, parsed[1].entry.isFull)
    }

    @Test
    fun headerNamesTheVehicleColumn() {
        assertTrue(buildCsv(emptyList()).startsWith(CSV_HEADER))
        assertTrue(CSV_HEADER.endsWith("Vehicle"))
    }

    /** A comma in a vehicle name would shift every later column on re-import. */
    @Test
    fun commasInVehicleNamesDoNotBreakColumns() {
        val rows = listOf(
            FuelEntry(id = 1, date = dateOf("2026-01-05"), odometer = 1000.0, liters = 50.0, pricePerLiter = 6.0, totalCost = 300.0, isFull = true) to "Golf, mk7"
        )

        val parsed = parseCsv(buildCsv(rows).lineSequence())

        assertEquals(1, parsed.size)
        assertEquals("Golf  mk7", parsed[0].vehicleName)
        assertEquals(1000.0, parsed[0].entry.odometer, 0.001)
    }
}
