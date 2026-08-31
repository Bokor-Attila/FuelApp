package com.bokor.fuelapp.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    /** Usable tank size in liters. Null means the range estimate is unavailable. */
    val tankCapacity: Double? = null
)

@Entity(
    tableName = "fuel_entries",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vehicleId")]
)
data class FuelEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val odometer: Double,
    val liters: Double,
    val pricePerLiter: Double,
    val totalCost: Double,
    @ColumnInfo(defaultValue = "1") val isFull: Boolean = true,
    @ColumnInfo(defaultValue = "1") val vehicleId: Int = DEFAULT_VEHICLE_ID
)

const val DEFAULT_VEHICLE_ID = 1

@Dao
interface FuelDao {
    @Query("SELECT * FROM fuel_entries WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun getEntriesForVehicle(vehicleId: Int): Flow<List<FuelEntry>>

    @Query("SELECT * FROM fuel_entries ORDER BY date DESC")
    fun getAllEntries(): Flow<List<FuelEntry>>

    @Insert
    suspend fun insert(entry: FuelEntry)

    @Insert
    suspend fun insertAll(entries: List<FuelEntry>)

    @Update
    suspend fun update(entry: FuelEntry)

    @Delete
    suspend fun delete(entry: FuelEntry)

    @Query("DELETE FROM fuel_entries")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM fuel_entries WHERE vehicleId = :vehicleId")
    suspend fun countForVehicle(vehicleId: Int): Int

    @Query("SELECT * FROM fuel_entries WHERE vehicleId = :vehicleId ORDER BY odometer DESC LIMIT 1")
    suspend fun getLastEntry(vehicleId: Int): FuelEntry?
}

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY id ASC")
    fun getAllVehicles(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles ORDER BY id ASC")
    suspend fun getAllVehiclesOnce(): List<Vehicle>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getVehicle(id: Int): Vehicle?

    @Insert
    suspend fun insert(vehicle: Vehicle): Long

    @Update
    suspend fun update(vehicle: Vehicle)

    @Delete
    suspend fun delete(vehicle: Vehicle)
}

@Database(entities = [FuelEntry::class, Vehicle::class], version = 3, exportSchema = true)
abstract class FuelDatabase : RoomDatabase() {
    abstract fun fuelDao(): FuelDao
    abstract fun vehicleDao(): VehicleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fuel_entries ADD COLUMN isFull INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * Introduces vehicles. SQLite cannot add a foreign key with ALTER TABLE, so
         * fuel_entries is rebuilt. Every existing entry is attached to a single default
         * vehicle, which keeps the current log intact.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vehicles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `tankCapacity` REAL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `vehicles` (`id`, `name`, `tankCapacity`) VALUES ($DEFAULT_VEHICLE_ID, 'My Car', NULL)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `fuel_entries_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `odometer` REAL NOT NULL,
                        `liters` REAL NOT NULL,
                        `pricePerLiter` REAL NOT NULL,
                        `totalCost` REAL NOT NULL,
                        `isFull` INTEGER NOT NULL DEFAULT 1,
                        `vehicleId` INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `fuel_entries_new` (`id`, `date`, `odometer`, `liters`, `pricePerLiter`, `totalCost`, `isFull`, `vehicleId`)
                    SELECT `id`, `date`, `odometer`, `liters`, `pricePerLiter`, `totalCost`, `isFull`, $DEFAULT_VEHICLE_ID FROM `fuel_entries`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `fuel_entries`")
                db.execSQL("ALTER TABLE `fuel_entries_new` RENAME TO `fuel_entries`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fuel_entries_vehicleId` ON `fuel_entries` (`vehicleId`)")
            }
        }
    }
}
