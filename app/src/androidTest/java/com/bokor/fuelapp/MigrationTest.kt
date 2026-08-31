package com.bokor.fuelapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bokor.fuelapp.data.DEFAULT_VEHICLE_ID
import com.bokor.fuelapp.data.FuelDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration_test.db"

/**
 * The 2 -> 3 migration rebuilds fuel_entries to add the vehicle foreign key. This exercises it
 * against a real version 2 database, since losing the fuel log here would be unrecoverable.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() = context.deleteDatabase(TEST_DB).let { }

    @After
    fun cleanUp() = context.deleteDatabase(TEST_DB).let { }

    /** Builds the schema exactly as version 2 left it: v1 tables plus the isFull column. */
    private fun createVersion2Database() {
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DB), null)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `fuel_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `date` INTEGER NOT NULL,
                `odometer` REAL NOT NULL,
                `liters` REAL NOT NULL,
                `pricePerLiter` REAL NOT NULL,
                `totalCost` REAL NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("ALTER TABLE `fuel_entries` ADD COLUMN `isFull` INTEGER NOT NULL DEFAULT 1")
        db.execSQL(
            "INSERT INTO `fuel_entries` (`date`, `odometer`, `liters`, `pricePerLiter`, `totalCost`, `isFull`) " +
                "VALUES (1000, 1000.0, 50.0, 6.0, 300.0, 1), (2000, 1500.0, 20.0, 6.5, 130.0, 0)"
        )
        db.version = 2
        db.close()
    }

    private fun openMigratedDatabase(): FuelDatabase =
        Room.databaseBuilder(context, FuelDatabase::class.java, TEST_DB)
            .addMigrations(FuelDatabase.MIGRATION_1_2, FuelDatabase.MIGRATION_2_3)
            .build()

    @Test
    fun migration2To3KeepsEveryEntryAndAttachesADefaultVehicle() {
        createVersion2Database()
        val db = openMigratedDatabase()

        try {
            runBlocking {
                val vehicle = db.vehicleDao().getVehicle(DEFAULT_VEHICLE_ID)
                assertNotNull("migration must create the default vehicle", vehicle)

                val entries = db.fuelDao().getEntriesForVehicle(DEFAULT_VEHICLE_ID).first()
                assertEquals(2, entries.size)

                val sorted = entries.sortedBy { it.odometer }
                assertEquals(1000.0, sorted[0].odometer, 0.001)
                assertEquals(50.0, sorted[0].liters, 0.001)
                assertEquals(true, sorted[0].isFull)
                assertEquals(1500.0, sorted[1].odometer, 0.001)
                assertEquals(6.5, sorted[1].pricePerLiter, 0.001)
                assertEquals(false, sorted[1].isFull)
                entries.forEach { assertEquals(DEFAULT_VEHICLE_ID, it.vehicleId) }
            }
        } finally {
            db.close()
        }
    }

    /** An empty v2 database must still come out with the default vehicle present. */
    @Test
    fun migration2To3OnAnEmptyDatabaseStillCreatesTheVehicle() {
        val raw = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DB), null)
        raw.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `fuel_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `date` INTEGER NOT NULL,
                `odometer` REAL NOT NULL,
                `liters` REAL NOT NULL,
                `pricePerLiter` REAL NOT NULL,
                `totalCost` REAL NOT NULL,
                `isFull` INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        raw.version = 2
        raw.close()

        val db = openMigratedDatabase()
        try {
            runBlocking {
                assertEquals(1, db.vehicleDao().getAllVehiclesOnce().size)
                assertEquals(0, db.fuelDao().getEntriesForVehicle(DEFAULT_VEHICLE_ID).first().size)
            }
        } finally {
            db.close()
        }
    }
}
