package com.bokor.fuelapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bokor.fuelapp.FuelViewModel
import com.bokor.fuelapp.R
import com.bokor.fuelapp.data.Vehicle
import com.bokor.fuelapp.domain.toAmountOrNull
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun VehicleManagerDialog(viewModel: FuelViewModel, onDismiss: () -> Unit) {
    val vehicles by viewModel.vehicles.collectAsState()
    var vehicleToEdit by remember { mutableStateOf<Vehicle?>(null) }
    var showAddVehicle by remember { mutableStateOf(false) }
    var vehicleToDelete by remember { mutableStateOf<Vehicle?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vehicles)) },
        text = {
            Column {
                vehicles.forEach { vehicle ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = vehicle.name, style = MaterialTheme.typography.bodyLarge)
                            vehicle.tankCapacity?.let {
                                Text(
                                    text = String.format(Locale.getDefault(), "%.0f L", it),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        IconButton(onClick = { vehicleToEdit = vehicle }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_vehicle))
                        }
                        IconButton(
                            onClick = { vehicleToDelete = vehicle },
                            enabled = vehicles.size > 1
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_vehicle))
                        }
                    }
                }
                if (vehicles.size == 1) {
                    Text(
                        text = stringResource(R.string.last_vehicle_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showAddVehicle = true }) {
                Text(stringResource(R.string.add_vehicle))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )

    if (showAddVehicle) {
        VehicleEditDialog(
            initial = null,
            onDismiss = { showAddVehicle = false },
            onConfirm = { name, capacity ->
                viewModel.addVehicle(name, capacity)
                showAddVehicle = false
            }
        )
    }

    vehicleToEdit?.let { editing ->
        VehicleEditDialog(
            initial = editing,
            onDismiss = { vehicleToEdit = null },
            onConfirm = { name, capacity ->
                viewModel.updateVehicle(editing.copy(name = name, tankCapacity = capacity))
                vehicleToEdit = null
            }
        )
    }

    vehicleToDelete?.let { deleting ->
        DeleteVehicleDialog(
            vehicle = deleting,
            viewModel = viewModel,
            onDismiss = { vehicleToDelete = null },
            onConfirm = {
                viewModel.deleteVehicle(deleting)
                vehicleToDelete = null
            }
        )
    }
}

@Composable
fun VehicleEditDialog(
    initial: Vehicle?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, tankCapacity: Double?) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var capacity by remember {
        mutableStateOf(initial?.tankCapacity?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.add_vehicle else R.string.edit_vehicle)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.vehicle_name)) },
                    singleLine = true,
                    isError = name.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = capacity,
                    onValueChange = { capacity = it },
                    label = { Text(stringResource(R.string.tank_capacity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), capacity.toAmountOrNull()) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun DeleteVehicleDialog(
    vehicle: Vehicle,
    viewModel: FuelViewModel,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var entryCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(vehicle.id) { entryCount = viewModel.entryCountFor(vehicle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_vehicle)) },
        text = {
            val count = entryCount
            Text(
                when {
                    count == null || count == 0 ->
                        stringResource(R.string.delete_vehicle_confirm_empty, vehicle.name)
                    else -> stringResource(R.string.delete_vehicle_confirm, vehicle.name, count)
                }
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = entryCount != null) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun SettingsDialog(currency: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(currency) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.currency_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.currency_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
