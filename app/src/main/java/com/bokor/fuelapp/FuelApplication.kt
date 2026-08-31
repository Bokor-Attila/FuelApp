package com.bokor.fuelapp

import android.app.Application
import androidx.room.Room
import com.bokor.fuelapp.data.FuelDatabase
import com.bokor.fuelapp.data.SettingsRepository

class FuelApplication : Application() {
    val database: FuelDatabase by lazy {
        Room.databaseBuilder(
            this,
            FuelDatabase::class.java,
            "fuel_database"
        )
        .addMigrations(FuelDatabase.MIGRATION_1_2, FuelDatabase.MIGRATION_2_3)
        .build()
    }

    val settings: SettingsRepository by lazy { SettingsRepository(this) }
}
