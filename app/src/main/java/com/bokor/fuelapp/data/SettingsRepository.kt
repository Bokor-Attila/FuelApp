package com.bokor.fuelapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Currency
import java.util.Locale

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** App-wide preferences: the display currency and which vehicle is on screen. */
class SettingsRepository(private val context: Context) {

    val currency: Flow<String> = context.settingsDataStore.data
        .map { it[KEY_CURRENCY] ?: defaultCurrency() }

    val selectedVehicleId: Flow<Int?> = context.settingsDataStore.data
        .map { it[KEY_SELECTED_VEHICLE] }

    suspend fun setCurrency(value: String) {
        context.settingsDataStore.edit { it[KEY_CURRENCY] = value.trim() }
    }

    suspend fun setSelectedVehicleId(id: Int) {
        context.settingsDataStore.edit { it[KEY_SELECTED_VEHICLE] = id }
    }

    companion object {
        private val KEY_CURRENCY = stringPreferencesKey("currency")
        private val KEY_SELECTED_VEHICLE = intPreferencesKey("selected_vehicle_id")

        /** Falls back to the ISO code of the device locale's currency, or an empty label. */
        fun defaultCurrency(locale: Locale = Locale.getDefault()): String = try {
            Currency.getInstance(locale).currencyCode
        } catch (_: IllegalArgumentException) {
            ""
        }
    }
}
