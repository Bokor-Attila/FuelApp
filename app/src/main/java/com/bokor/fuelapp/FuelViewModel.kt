package com.bokor.fuelapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bokor.fuelapp.data.FuelDao
import com.bokor.fuelapp.data.FuelEntry
import com.bokor.fuelapp.data.SettingsRepository
import com.bokor.fuelapp.data.Vehicle
import com.bokor.fuelapp.data.VehicleDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class FuelViewModel(
    private val application: Application,
    private val fuelDao: FuelDao,
    private val vehicleDao: VehicleDao,
    private val settings: SettingsRepository
) : AndroidViewModel(application) {

    val vehicles: StateFlow<List<Vehicle>> = vehicleDao.getAllVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The vehicle actually on screen. The stored id is ignored when it points at a
     * vehicle that no longer exists, so deleting the selected car falls back cleanly.
     */
    val selectedVehicle: StateFlow<Vehicle?> =
        combine(vehicleDao.getAllVehicles(), settings.selectedVehicleId) { all, storedId ->
            all.firstOrNull { it.id == storedId } ?: all.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allEntries: StateFlow<List<FuelEntry>> = selectedVehicle
        .flatMapLatest { vehicle ->
            if (vehicle == null) flowOf(emptyList()) else fuelDao.getEntriesForVehicle(vehicle.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currency: StateFlow<String> = settings.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.defaultCurrency())

    init {
        viewModelScope.launch {
            // A fresh install has no vehicle; upgrades get one from MIGRATION_2_3.
            if (vehicleDao.getAllVehiclesOnce().isEmpty()) {
                val id = vehicleDao.insert(Vehicle(name = application.getString(R.string.default_vehicle_name)))
                settings.setSelectedVehicleId(id.toInt())
            }
        }
    }

    fun addEntry(date: Long, odometer: Double, liters: Double, pricePerLiter: Double, isFull: Boolean = true) {
        viewModelScope.launch {
            val vehicleId = selectedVehicle.value?.id ?: return@launch
            val totalCost = liters * pricePerLiter
            fuelDao.insert(
                FuelEntry(
                    date = date,
                    odometer = odometer,
                    liters = liters,
                    pricePerLiter = pricePerLiter,
                    totalCost = totalCost,
                    isFull = isFull,
                    vehicleId = vehicleId
                )
            )
            FuelWidgetProvider.triggerUpdate(application)
        }
    }

    fun updateEntry(entry: FuelEntry) {
        viewModelScope.launch {
            fuelDao.update(entry)
            FuelWidgetProvider.triggerUpdate(application)
        }
    }

    fun deleteEntry(entry: FuelEntry) {
        viewModelScope.launch {
            fuelDao.delete(entry)
            FuelWidgetProvider.triggerUpdate(application)
        }
    }

    /**
     * Rows naming a vehicle are matched to it by name, creating it when unknown, so a
     * multi-vehicle export restores fully. Rows without a name land on the current vehicle.
     */
    fun importEntries(rows: List<ImportedEntry>) {
        viewModelScope.launch {
            val fallbackId = selectedVehicle.value?.id ?: return@launch
            val byName = vehicleDao.getAllVehiclesOnce().associate { it.name to it.id }.toMutableMap()

            val resolved = rows.map { row ->
                val name = row.vehicleName?.takeIf { it.isNotBlank() }
                val vehicleId = when {
                    name == null -> fallbackId
                    byName.containsKey(name) -> byName.getValue(name)
                    else -> vehicleDao.insert(Vehicle(name = name)).toInt().also { byName[name] = it }
                }
                row.entry.copy(vehicleId = vehicleId)
            }

            fuelDao.insertAll(resolved)
            FuelWidgetProvider.triggerUpdate(application)
        }
    }

    fun addVehicle(name: String, tankCapacity: Double?) {
        viewModelScope.launch {
            val id = vehicleDao.insert(Vehicle(name = name, tankCapacity = tankCapacity))
            settings.setSelectedVehicleId(id.toInt())
            FuelWidgetProvider.triggerUpdate(application)
        }
    }

    fun updateVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleDao.update(vehicle)
            FuelWidgetProvider.triggerUpdate(application)
        }
    }

    /** Cascades to the vehicle's fuel entries; the UI confirms the count first. */
    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleDao.delete(vehicle)
            vehicleDao.getAllVehiclesOnce().firstOrNull()?.let { settings.setSelectedVehicleId(it.id) }
            FuelWidgetProvider.triggerUpdate(application)
        }
    }

    fun selectVehicle(vehicle: Vehicle) {
        viewModelScope.launch { settings.setSelectedVehicleId(vehicle.id) }
    }

    suspend fun entryCountFor(vehicle: Vehicle): Int = fuelDao.countForVehicle(vehicle.id)

    fun setCurrency(value: String) {
        viewModelScope.launch {
            settings.setCurrency(value)
            FuelWidgetProvider.triggerUpdate(application)
        }
    }

    /** Entries for every vehicle, paired with the owning vehicle name, for CSV export. */
    suspend fun entriesForExport(): List<Pair<FuelEntry, String>> {
        val names = vehicleDao.getAllVehiclesOnce().associate { it.id to it.name }
        return fuelDao.getAllEntries().first().map { it to (names[it.vehicleId] ?: "") }
    }
}

class FuelViewModelFactory(
    private val application: Application,
    private val fuelDao: FuelDao,
    private val vehicleDao: VehicleDao,
    private val settings: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FuelViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FuelViewModel(application, fuelDao, vehicleDao, settings) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
