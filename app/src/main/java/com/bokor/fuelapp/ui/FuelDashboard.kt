package com.bokor.fuelapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bokor.fuelapp.FuelViewModel
import com.bokor.fuelapp.R
import com.bokor.fuelapp.data.FuelEntry
import com.bokor.fuelapp.data.exportEntriesToCsv
import com.bokor.fuelapp.data.importEntriesFromCsv
import com.bokor.fuelapp.domain.calculateStats
import com.bokor.fuelapp.ui.charts.ConsumptionChart
import com.bokor.fuelapp.ui.charts.PriceTrendChart
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelDashboard(
    viewModel: FuelViewModel, 
    triggerAddDialog: Boolean = false,
    onAddDialogShown: () -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val entries by viewModel.allEntries.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val currency by viewModel.currency.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(triggerAddDialog) {
        if (triggerAddDialog) {
            showAddDialog = true
            onAddDialogShown()
        }
    }

    var entryToEdit by remember { mutableStateOf<FuelEntry?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showVehicleMenu by remember { mutableStateOf(false) }
    var showVehicleManager by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showStatistics by remember { mutableStateOf(false) }
    val stats = calculateStats(entries, selectedVehicle?.tankCapacity)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // Resources rather than Context: the count is dynamic, so the lookup happens at call time.
    val resources = LocalResources.current

    val importFailed = stringResource(R.string.csv_import_failed)
    val importEmpty = stringResource(R.string.csv_import_empty)
    val exportFailed = stringResource(R.string.csv_export_failed)
    val entryDeleted = stringResource(R.string.entry_deleted)
    val undoLabel = stringResource(R.string.undo)

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val imported = importEntriesFromCsv(context, it)
            scope.launch {
                when {
                    imported == null -> snackbarHostState.showSnackbar(importFailed)
                    imported.isEmpty() -> snackbarHostState.showSnackbar(importEmpty)
                    else -> {
                        viewModel.importEntries(imported)
                        snackbarHostState.showSnackbar(
                            resources.getString(R.string.csv_imported, imported.size)
                        )
                    }
                }
            }
        }
    }

    if (showStatistics) {
        StatisticsScreen(
            entries = entries,
            stats = stats,
            currency = currency,
            onBack = { showStatistics = false }
        )
        return
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(selectedVehicle?.name ?: stringResource(R.string.dashboard_title)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    Box {
                        IconButton(onClick = { showVehicleMenu = true }) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = stringResource(R.string.switch_vehicle)
                            )
                        }
                        DropdownMenu(expanded = showVehicleMenu, onDismissRequest = { showVehicleMenu = false }) {
                            vehicles.forEach { vehicle ->
                                DropdownMenuItem(
                                    text = { Text(vehicle.name) },
                                    onClick = {
                                        showVehicleMenu = false
                                        viewModel.selectVehicle(vehicle)
                                    },
                                    trailingIcon = {
                                        if (vehicle.id == selectedVehicle?.id) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.vehicles)) },
                                onClick = {
                                    showVehicleMenu = false
                                    showVehicleManager = true
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.statistics)) },
                                onClick = {
                                    showMenu = false
                                    showStatistics = true
                                },
                                leadingIcon = { Icon(Icons.Default.QueryStats, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_csv)) },
                                onClick = {
                                    showMenu = false
                                    scope.launch {
                                        if (!exportEntriesToCsv(context, viewModel.entriesForExport())) {
                                            snackbarHostState.showSnackbar(exportFailed)
                                        }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_csv)) },
                                onClick = {
                                    showMenu = false
                                    csvPickerLauncher.launch("text/*")
                                },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings)) },
                                onClick = {
                                    showMenu = false
                                    showSettings = true
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_entry))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                MainStatsCard(stats, currency)
            }
            if (entries.size >= 2) {
                item {
                    ConsumptionChart(entries)
                }
                item {
                    PriceTrendChart(entries, currency)
                }
            }
            item {
                Text(
                    text = stringResource(R.string.recent_fillups),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_entries),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(entries.size) { index ->
                val entry = entries[index]
                FuelEntryItem(
                    entry = entry,
                    allEntries = entries,
                    currency = currency,
                    onEdit = { entryToEdit = it },
                    onDelete = { deleted ->
                        viewModel.deleteEntry(deleted)
                        scope.launch {
                            val action = snackbarHostState.showSnackbar(
                                message = entryDeleted,
                                actionLabel = undoLabel
                            )
                            if (action == SnackbarResult.ActionPerformed) {
                                viewModel.restoreEntry(deleted)
                            }
                        }
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddFuelEntryDialog(
            currency = currency,
            lastOdometer = entries.firstOrNull()?.odometer ?: 0.0,
            onDismiss = { showAddDialog = false },
            onConfirm = { date, odo, l, price, isFull ->
                viewModel.addEntry(date, odo, l, price, isFull)
                showAddDialog = false
            }
        )
    }

    if (entryToEdit != null) {
        AddFuelEntryDialog(
            initialEntry = entryToEdit,
            currency = currency,
            onDismiss = { entryToEdit = null },
            onConfirm = { date, odo, l, price, isFull ->
                entryToEdit?.let {
                    viewModel.updateEntry(it.copy(date = date, odometer = odo, liters = l, pricePerLiter = price, totalCost = l * price, isFull = isFull))
                }
                entryToEdit = null
            }
        )
    }

    if (showVehicleManager) {
        VehicleManagerDialog(viewModel = viewModel, onDismiss = { showVehicleManager = false })
    }

    if (showSettings) {
        SettingsDialog(
            currency = currency,
            onDismiss = { showSettings = false },
            onConfirm = {
                viewModel.setCurrency(it)
                showSettings = false
            }
        )
    }
}
