package com.bokor.fuelapp

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bokor.fuelapp.data.FuelDatabase
import com.bokor.fuelapp.data.FuelEntry
import com.bokor.fuelapp.data.Vehicle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FuelDaoTest {

    private lateinit var db: FuelDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FuelDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = db.close()

    private fun entry(vehicleId: Int, odometer: Double) = FuelEntry(
        date = odometer.toLong(),
        odometer = odometer,
        liters = 40.0,
        pricePerLiter = 6.0,
        totalCost = 240.0,
        isFull = true,
        vehicleId = vehicleId
    )

    @Test
    fun entriesAreScopedToTheirVehicle() = runBlocking {
        val car = db.vehicleDao().insert(Vehicle(name = "Car")).toInt()
        val van = db.vehicleDao().insert(Vehicle(name = "Van")).toInt()

        db.fuelDao().insertAll(listOf(entry(car, 1000.0), entry(car, 2000.0), entry(van, 50000.0)))

        val carEntries = db.fuelDao().getEntriesForVehicle(car).first()
        val vanEntries = db.fuelDao().getEntriesForVehicle(van).first()

        assertEquals(2, carEntries.size)
        assertEquals(1, vanEntries.size)
        assertTrue(carEntries.all { it.vehicleId == car })
        assertEquals(50000.0, vanEntries.single().odometer, 0.001)
        assertEquals(2, db.fuelDao().countForVehicle(car))
    }

    /** Deleting a vehicle must take its fill-ups with it and leave other vehicles alone. */
    @Test
    fun deletingAVehicleCascadesToItsEntriesOnly() = runBlocking {
        val car = db.vehicleDao().insert(Vehicle(name = "Car")).toInt()
        val van = db.vehicleDao().insert(Vehicle(name = "Van")).toInt()
        db.fuelDao().insertAll(listOf(entry(car, 1000.0), entry(van, 50000.0)))

        db.vehicleDao().delete(db.vehicleDao().getVehicle(car)!!)

        assertEquals(0, db.fuelDao().countForVehicle(car))
        assertEquals(1, db.fuelDao().countForVehicle(van))
        assertEquals(1, db.fuelDao().getAllEntries().first().size)
    }
}
