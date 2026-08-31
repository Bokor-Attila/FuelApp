package com.bokor.fuelapp.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bokor.fuelapp.OdometerScanner
import com.bokor.fuelapp.R
import com.bokor.fuelapp.data.FuelEntry
import com.bokor.fuelapp.domain.toAmountOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun AddFuelEntryDialog(
    initialEntry: FuelEntry? = null,
    currency: String,
    lastOdometer: Double = 0.0,
    onDismiss: () -> Unit,
    onConfirm: (Long, Double, Double, Double, Boolean) -> Unit
) {
    var date by remember { mutableStateOf(initialEntry?.date ?: System.currentTimeMillis()) }
    var odometer by remember { mutableStateOf(initialEntry?.odometer?.toInt()?.toString() ?: "") }
    var liters by remember { mutableStateOf(initialEntry?.liters?.toString() ?: "") }
    var pricePerLiter by remember { mutableStateOf(initialEntry?.pricePerLiter?.toString() ?: "") }
    var totalAmount by remember { mutableStateOf(initialEntry?.let { (it.liters * it.pricePerLiter).toString() } ?: "") }
    var isFull by remember { mutableStateOf(initialEntry?.isFull ?: true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    // Fields stay unflagged until the first submit, so an untouched form does not open in red.
    var submitAttempted by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<Int?>(null) }
    
    val context = LocalContext.current
    val odometerTooLowMessage = stringResource(R.string.error_odometer_low, lastOdometer)
    val odometerRequiredMessage = stringResource(R.string.error_odometer_required)
    val amountRequiredMessage = stringResource(R.string.error_amount_required)
    val calendar = Calendar.getInstance().apply { timeInMillis = date }
    val dateFormat = SimpleDateFormat("yyyy. MMM dd.", Locale.getDefault())

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val newCalendar = Calendar.getInstance()
            newCalendar.set(year, month, dayOfMonth)
            date = newCalendar.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showScanner = true
        }
    }

    if (showScanner) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                OdometerScanner(
                    onResult = { result ->
                        odometer = result
                        showScanner = false
                    },
                    onError = { messageRes ->
                        scanError = messageRes
                        showScanner = false
                    },
                    onCancel = { showScanner = false }
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialEntry == null) stringResource(R.string.add_entry) else stringResource(R.string.edit_entry)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // Without this the confirm button is unreachable behind the keyboard on
                // short screens and in landscape.
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { 
                        odometer = it
                        errorMessage = null
                        scanError = null
                    },
                    label = { Text(stringResource(R.string.odometer)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = (submitAttempted && odometer.isBlank()) || errorMessage != null,
                    trailingIcon = {
                        IconButton(onClick = {
                            when (PackageManager.PERMISSION_GRANTED) {
                                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                                    showScanner = true
                                }
                                else -> {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        }) {
                            Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.scan_odometer))
                        }
                    },
                    supportingText = {
                        val scanMessage = scanError
                        if (errorMessage != null) {
                            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
                        } else if (scanMessage != null) {
                            Text(text = stringResource(scanMessage), color = MaterialTheme.colorScheme.error)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.auto_calculate_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = liters,
                        onValueChange = { 
                            liters = it
                            amountError = null
                            val l = it.toAmountOrNull()
                            val p = pricePerLiter.toAmountOrNull()
                            if (l != null && p != null) {
                                totalAmount = String.format(Locale.getDefault(), "%.2f", l * p)
                            }
                        },
                        label = { Text(stringResource(R.string.liters)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pricePerLiter,
                        onValueChange = { 
                            pricePerLiter = it
                            amountError = null
                            val p = it.toAmountOrNull()
                            val l = liters.toAmountOrNull()
                            if (p != null && l != null) {
                                totalAmount = String.format(Locale.getDefault(), "%.2f", l * p)
                            }
                        },
                        label = { Text(stringResource(R.string.price_per_liter, currency)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1.2f)
                    )
                }

                OutlinedTextField(
                    value = totalAmount,
                    onValueChange = { 
                        totalAmount = it
                        amountError = null
                        val t = it.toAmountOrNull()
                        val p = pricePerLiter.toAmountOrNull()
                        val l = liters.toAmountOrNull()
                        if (t != null) {
                            if (p != null && p > 0) {
                                liters = String.format(Locale.getDefault(), "%.2f", t / p)
                            } else if (l != null && l > 0) {
                                pricePerLiter = String.format(Locale.getDefault(), "%.2f", t / l)
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.total_amount, currency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                amountError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.full_tank),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.full_tank_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = isFull,
                        onCheckedChange = { isFull = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                    )
                }

                OutlinedTextField(
                    value = dateFormat.format(Date(date)),
                    onValueChange = { },
                    label = { Text(stringResource(R.string.date_label)) },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { datePickerDialog.show() }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Select Date")
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitAttempted = true
                    errorMessage = null
                    amountError = null

                    val odo = odometer.toAmountOrNull() ?: 0.0
                    var l = liters.toAmountOrNull() ?: 0.0
                    var p = pricePerLiter.toAmountOrNull() ?: 0.0
                    val t = totalAmount.toAmountOrNull() ?: 0.0
                    
                    // Final calculation if one is missing
                    if (l <= 0 && p > 0 && t > 0) l = t / p
                    if (p <= 0 && l > 0 && t > 0) p = t / l
                    
                    when {
                        odo <= 0 -> errorMessage = odometerRequiredMessage
                        odo <= lastOdometer && initialEntry == null -> errorMessage = odometerTooLowMessage
                        l <= 0 || p <= 0 -> amountError = amountRequiredMessage
                        else -> onConfirm(date, odo, l, p, isFull)
                    }
                }
            ) {
                Text(if (initialEntry == null) stringResource(R.string.calculate) else stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
