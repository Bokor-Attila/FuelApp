package com.bokor.fuelapp

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.bokor.fuelapp.ui.theme.FuelAppTheme
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bokor.fuelapp.data.FuelEntry
import com.bokor.fuelapp.data.Vehicle
import com.bokor.fuelapp.ui.theme.FuelAppTheme
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var showAddDialogFromIntent = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showAddDialogFromIntent.value = intent?.getBooleanExtra("EXTRA_OPEN_ADD_DIALOG", false) ?: false
        enableEdgeToEdge()
        setContent {
            val app = application as FuelApplication
            val viewModel: FuelViewModel = viewModel(
                factory = FuelViewModelFactory(
                    application,
                    app.database.fuelDao(),
                    app.database.vehicleDao(),
                    app.settings
                )
            )
            FuelAppTheme {
                FuelDashboard(viewModel, showAddDialogFromIntent.value) {
                    showAddDialogFromIntent.value = false
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("EXTRA_OPEN_ADD_DIALOG", false)) {
            showAddDialogFromIntent.value = true
        }
    }
}

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
    val stats = calculateStats(entries, selectedVehicle?.tankCapacity)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val importedEntries = importEntriesFromCsv(context, it)
            if (importedEntries.isNotEmpty()) {
                viewModel.importEntries(importedEntries)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                                text = { Text(stringResource(R.string.export_csv)) },
                                onClick = {
                                    showMenu = false
                                    scope.launch { exportEntriesToCsv(context, viewModel.entriesForExport()) }
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
            items(entries.size) { index ->
                val entry = entries[index]
                FuelEntryItem(
                    entry = entry,
                    allEntries = entries,
                    currency = currency,
                    onEdit = { entryToEdit = it },
                    onDelete = { viewModel.deleteEntry(it) }
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

@Composable
fun PriceTrendChart(entries: List<FuelEntry>, currency: String) {
    val sortedEntries = entries.sortedBy { it.date }
    val prices = sortedEntries.map { it.pricePerLiter }

    if (prices.size < 2) return

    val maxVal = (prices.maxOrNull() ?: 1.0)
    val minVal = (prices.minOrNull() ?: 0.0)
    
    // Add some padding to top and bottom of chart
    val displayMax = maxVal + (maxVal - minVal) * 0.3
    val displayMin = (minVal - (maxVal - minVal) * 0.2).coerceAtLeast(0.0)
    val range = (displayMax - displayMin).coerceAtLeast(0.1)

    val accentColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        fontSize = 10.sp
    )
    val valueStyle = TextStyle(
        color = accentColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.price_trend),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.price_per_liter_unit, currency),
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    
                    // Background Grid Lines & Y-Axis Labels
                    for (i in 0..2) {
                        val y = height * (i / 2f)
                        val labelValue = displayMax - (i / 2f) * range
                        
                        drawLine(
                            color = surfaceColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                        
                        drawText(
                            textMeasurer = textMeasurer,
                            text = String.format(Locale.getDefault(), "%.2f", labelValue),
                            style = labelStyle,
                            topLeft = Offset(0f, y - 15.dp.toPx())
                        )
                    }

                    val spacing = width / (prices.size.coerceAtLeast(2) - 1).toFloat()
                    val points = prices.mapIndexed { index, value ->
                        Offset(
                            x = index * spacing,
                            y = height - ((value - displayMin) / range).toFloat() * height
                        )
                    }

                    // Fill Area Path
                    val fillPath = Path().apply {
                        moveTo(0f, height)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(width, height)
                        close()
                    }
                    
                    drawPath(
                        path = fillPath,
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )

                    // Line Path
                    val strokePath = Path().apply {
                        points.forEachIndexed { index, offset ->
                            if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
                        }
                    }

                    drawPath(
                        path = strokePath,
                        color = accentColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )

                    // Draw points & Values
                    points.forEachIndexed { index, point ->
                        drawCircle(
                            color = Color.White,
                            radius = 5.dp.toPx(),
                            center = point
                        )
                        drawCircle(
                            color = accentColor,
                            radius = 3.dp.toPx(),
                            center = point,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        
                        // Draw value above point
                        val valueText = String.format(Locale.getDefault(), "%.2f", prices[index])
                        val textLayoutResult = textMeasurer.measure(valueText, valueStyle)
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(
                                x = point.x - textLayoutResult.size.width / 2,
                                y = point.y - 20.dp.toPx()
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConsumptionChart(entries: List<FuelEntry>) {
    val sortedEntries = entries.sortedBy { it.odometer }
    val consumptions = mutableListOf<Double>()
    
    var accumulatedLiters = 0.0
    var lastFullEntry: FuelEntry? = null
    
    for (entry in sortedEntries) {
        if (lastFullEntry == null) {
            if (entry.isFull) lastFullEntry = entry
            continue
        }
        
        accumulatedLiters += entry.liters
        
        if (entry.isFull) {
            val dist = entry.odometer - lastFullEntry.odometer
            if (dist > 0) {
                consumptions.add((accumulatedLiters / dist) * 100)
            }
            lastFullEntry = entry
            accumulatedLiters = 0.0
        }
    }

    if (consumptions.isEmpty()) return

    val maxVal = (consumptions.maxOrNull() ?: 1.0)
    val minVal = (consumptions.minOrNull() ?: 0.0)
    
    // Add some padding to top and bottom of chart
    val displayMax = maxVal + (maxVal - minVal) * 0.3
    val displayMin = (minVal - (maxVal - minVal) * 0.2).coerceAtLeast(0.0)
    val range = (displayMax - displayMin).coerceAtLeast(0.1)

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        fontSize = 10.sp
    )
    val valueStyle = TextStyle(
        color = primaryColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.efficiency_trend),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.consumption_unit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    
                    // Background Grid Lines & Y-Axis Labels
                    for (i in 0..2) {
                        val y = height * (i / 2f)
                        val labelValue = displayMax - (i / 2f) * range
                        
                        drawLine(
                            color = surfaceColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                        
                        drawText(
                            textMeasurer = textMeasurer,
                            text = String.format(Locale.getDefault(), "%.1f", labelValue),
                            style = labelStyle,
                            topLeft = Offset(0f, y - 15.dp.toPx())
                        )
                    }

                    val spacing = width / (consumptions.size.coerceAtLeast(2) - 1).toFloat()
                    val points = consumptions.mapIndexed { index, value ->
                        Offset(
                            x = index * spacing,
                            y = height - ((value - displayMin) / range).toFloat() * height
                        )
                    }

                    // Fill Area Path
                    val fillPath = Path().apply {
                        moveTo(0f, height)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(width, height)
                        close()
                    }
                    
                    drawPath(
                        path = fillPath,
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )

                    // Line Path
                    val strokePath = Path().apply {
                        points.forEachIndexed { index, offset ->
                            if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
                        }
                    }

                    drawPath(
                        path = strokePath,
                        color = primaryColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )

                    // Draw points & Values
                    points.forEachIndexed { index, point ->
                        drawCircle(
                            color = Color.White,
                            radius = 5.dp.toPx(),
                            center = point
                        )
                        drawCircle(
                            color = primaryColor,
                            radius = 3.dp.toPx(),
                            center = point,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        
                        // Draw value above point
                        val valueText = String.format(Locale.getDefault(), "%.1f", consumptions[index])
                        val textLayoutResult = textMeasurer.measure(valueText, valueStyle)
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(
                                x = point.x - textLayoutResult.size.width / 2,
                                y = point.y - 20.dp.toPx()
                            )
                        )
                    }
                }
            }
        }
    }
}

const val CSV_HEADER = "Date,Odometer,Liters,PricePerLiter,TotalCost,isFull,Vehicle"

/** Serialises entries with their vehicle name. Commas in names are flattened to spaces. */
fun buildCsv(rows: List<Pair<FuelEntry, String>>): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val body = rows.joinToString("\n") { (entry, vehicleName) ->
        "${dateFormat.format(Date(entry.date))},${entry.odometer},${entry.liters},${entry.pricePerLiter}," +
            "${entry.totalCost},${if (entry.isFull) 1 else 0},${vehicleName.replace(',', ' ')}"
    }
    return CSV_HEADER + "\n" + body
}

fun exportEntriesToCsv(context: android.content.Context, rows: List<Pair<FuelEntry, String>>) {
    val fullCsv = buildCsv(rows)

    try {
        val fileName = "fuel_log_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        file.writeText(fullCsv)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Fuel Data"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/** A parsed CSV row. [vehicleName] is null for exports made before vehicles existed. */
data class ImportedEntry(val entry: FuelEntry, val vehicleName: String?)

/**
 * Parses exported CSV. The first line is assumed to be a header and skipped. The trailing
 * Vehicle column is optional so that files written before vehicles existed still import.
 */
fun parseCsv(lines: Sequence<String>): List<ImportedEntry> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val entries = mutableListOf<ImportedEntry>()

    lines.drop(1).forEach { line ->
        val parts = line.split(",")
        if (parts.size < 5) return@forEach

        val date = runCatching { dateFormat.parse(parts[0])?.time }.getOrNull() ?: System.currentTimeMillis()
        val odo = parts[1].toDoubleOrNull() ?: 0.0
        val liters = parts[2].toDoubleOrNull() ?: 0.0
        val price = parts[3].toDoubleOrNull() ?: 0.0
        val total = parts[4].toDoubleOrNull() ?: (liters * price)
        val isFull = if (parts.size > 5) parts[5].trim() == "1" else true
        val vehicleName = if (parts.size > 6) parts[6].trim().ifBlank { null } else null

        entries.add(
            ImportedEntry(
                entry = FuelEntry(
                    date = date,
                    odometer = odo,
                    liters = liters,
                    pricePerLiter = price,
                    totalCost = total,
                    isFull = isFull
                ),
                vehicleName = vehicleName
            )
        )
    }
    return entries
}

fun importEntriesFromCsv(context: android.content.Context, uri: android.net.Uri): List<ImportedEntry> = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        parseCsv(input.bufferedReader().lineSequence())
    } ?: emptyList()
} catch (e: Exception) {
    e.printStackTrace()
    emptyList()
}

@Composable
fun MainStatsCard(stats: FuelStats, currency: String) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.consumption_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${String.format(Locale.getDefault(), "%.2f", stats.avgConsumption)} ${stringResource(R.string.consumption_unit)}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(stringResource(R.string.stats_total_distance), "${stats.totalDistance.toInt()} km")
                StatItem(stringResource(R.string.stats_total_fuel), "${String.format(Locale.getDefault(), "%.1f", stats.totalLiters)} L")
                StatItem(stringResource(R.string.stats_total_cost), "${stats.totalCost.toInt()} $currency")
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatItem(stringResource(R.string.cost_per_km), "${String.format(Locale.getDefault(), "%.2f", stats.costPerKm)} $currency")
                    StatItem("Best", "${String.format(Locale.getDefault(), "%.1f", stats.bestConsumption)}")
                    StatItem("Worst", "${String.format(Locale.getDefault(), "%.1f", stats.worstConsumption)}")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.avg_distance, stats.avgDistance.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${stringResource(R.string.price_per_liter, currency)}: " +
                        "${String.format(Locale.getDefault(), "%.2f", stats.avgPrice)} ${stringResource(R.string.price_per_liter_unit, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(8.dp))
                
                if (stats.predictedRange > 0) {
                    StatItem(stringResource(R.string.est_range), String.format(Locale.getDefault(), "%.0f %s", stats.predictedRange, stringResource(R.string.km_per_tank)))
                }
            }

            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(if (expanded) "Show Less" else "Show More")
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

data class FuelStats(
    val avgConsumption: Double = 0.0,
    val bestConsumption: Double = 0.0,
    val worstConsumption: Double = 0.0,
    val costPerKm: Double = 0.0,
    val avgDistance: Double = 0.0,
    val totalDistance: Double = 0.0,
    val totalLiters: Double = 0.0,
    val totalCost: Double = 0.0,
    val avgPrice: Double = 0.0,
    val predictedRange: Double = 0.0
)

/**
 * @param tankCapacity usable tank size in liters; null disables the range estimate.
 */
fun calculateStats(entries: List<FuelEntry>, tankCapacity: Double? = null): FuelStats {
    if (entries.isEmpty()) return FuelStats()
    
    val sorted = entries.sortedBy { it.odometer }
    
    // Find the first and last full entries for total distance calculation
    val fullEntries = sorted.filter { it.isFull }
    
    val totalDistance = if (fullEntries.size < 2) 0.0 else fullEntries.last().odometer - fullEntries.first().odometer
    
    // Only count liters burned between the first and the last full tank. Refuels logged after
    // the last full tank are not covered by totalDistance, so including them inflates the average.
    var totalLitersCalculated = 0.0
    if (fullEntries.size >= 2) {
        val firstFullOdo = fullEntries.first().odometer
        val lastFullOdo = fullEntries.last().odometer
        totalLitersCalculated = sorted
            .filter { it.odometer > firstFullOdo && it.odometer <= lastFullOdo }
            .sumOf { it.liters }
    }

    val totalCost = entries.sumOf { it.totalCost }
    
    val avgConsumption = if (totalDistance > 0) (totalLitersCalculated / totalDistance) * 100 else 0.0
    val avgPrice = if (entries.isEmpty()) 0.0 else entries.map { it.pricePerLiter }.average()
    val costPerKm = if (totalDistance > 0) totalCost / totalDistance else 0.0
    
    val individualConsumptions = mutableListOf<Double>()
    val distances = mutableListOf<Double>()
    
    var accumulatedLiters = 0.0
    var lastFullEntry: FuelEntry? = null
    
    for (entry in sorted) {
        if (lastFullEntry == null) {
            if (entry.isFull) lastFullEntry = entry
            continue
        }
        
        accumulatedLiters += entry.liters
        
        if (entry.isFull) {
            val dist = entry.odometer - lastFullEntry.odometer
            if (dist > 0) {
                distances.add(dist)
                individualConsumptions.add((accumulatedLiters / dist) * 100)
            }
            lastFullEntry = entry
            accumulatedLiters = 0.0
        }
    }

    val predictedRange = if (avgConsumption > 0 && tankCapacity != null && tankCapacity > 0) {
        (tankCapacity / avgConsumption) * 100
    } else {
        0.0
    }
    
    return FuelStats(
        avgConsumption = avgConsumption,
        bestConsumption = individualConsumptions.minOrNull() ?: 0.0,
        worstConsumption = individualConsumptions.maxOrNull() ?: 0.0,
        costPerKm = costPerKm,
        avgDistance = distances.average().takeIf { !it.isNaN() } ?: 0.0,
        totalDistance = totalDistance,
        totalLiters = entries.sumOf { it.liters },
        totalCost = totalCost,
        avgPrice = avgPrice,
        predictedRange = predictedRange
    )
}

fun calculateConsumption(entries: List<FuelEntry>): Double {
    if (entries.size < 2) return 0.0
    val sorted = entries.sortedBy { it.odometer }
    val fullEntries = sorted.filter { it.isFull }
    if (fullEntries.size < 2) return 0.0
    
    val firstFullOdo = fullEntries.first().odometer
    val lastFullOdo = fullEntries.last().odometer
    val totalDistance = lastFullOdo - firstFullOdo
    
    if (totalDistance <= 0) return 0.0
    
    val totalLiters = sorted.filter { it.odometer > firstFullOdo && it.odometer <= lastFullOdo }.sumOf { it.liters }
    return (totalLiters / totalDistance) * 100
}

@Composable
fun FuelEntryItem(
    entry: FuelEntry,
    allEntries: List<FuelEntry>,
    currency: String,
    onEdit: (FuelEntry) -> Unit,
    onDelete: (FuelEntry) -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy. MMM dd.", Locale.getDefault())
    val dateString = dateFormat.format(Date(entry.date))
    var showMenu by remember { mutableStateOf(false) }
    
    val consumption = if (entry.isFull) {
        val sorted = allEntries.sortedBy { it.odometer }
        val currentIndex = sorted.indexOf(entry)
        if (currentIndex > 0) {
            var prevFullIndex = -1
            for (i in currentIndex - 1 downTo 0) {
                if (sorted[i].isFull) {
                    prevFullIndex = i
                    break
                }
            }
            if (prevFullIndex != -1) {
                val dist = entry.odometer - sorted[prevFullIndex].odometer
                val liters = sorted.slice(prevFullIndex + 1..currentIndex).sumOf { it.liters }
                if (dist > 0) (liters / dist) * 100 else null
            } else null
        } else null
    } else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.isFull) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocalGasStation, 
                contentDescription = null,
                tint = if (entry.isFull) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = dateString, style = MaterialTheme.typography.labelSmall)
                    if (!entry.isFull) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = stringResource(R.string.partial),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(
                        R.string.liters_value,
                        String.format(Locale.getDefault(), "%.2f", entry.liters)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "${entry.odometer.toInt()} km", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (consumption != null) {
                    Text(
                        text = "${String.format(Locale.getDefault(), "%.2f", consumption)} ${stringResource(R.string.consumption_unit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "${String.format(Locale.getDefault(), "%.2f", entry.pricePerLiter)} ${stringResource(R.string.price_per_liter_unit, currency)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${String.format(Locale.getDefault(), "%.2f", entry.totalCost)} $currency",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_entry)) },
                        onClick = { 
                            showMenu = false
                            onEdit(entry)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_entry)) },
                        onClick = {
                            showMenu = false
                            onDelete(entry)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Parses a number typed on any keyboard. Hungarian and Romanian layouts produce a decimal
 * comma, which [String.toDoubleOrNull] rejects outright.
 */
fun String.toAmountOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

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
                    onCancel = { showScanner = false }
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialEntry == null) stringResource(R.string.add_entry) else stringResource(R.string.edit_entry)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { 
                        odometer = it
                        errorMessage = null
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
                        if (errorMessage != null) {
                            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
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

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    val sampleEntries = listOf(
        FuelEntry(1, System.currentTimeMillis() - 150000000, 1000.0, 50.0, 6.5, 325.0, true),
        FuelEntry(2, System.currentTimeMillis() - 100000000, 1400.0, 20.0, 6.8, 136.0, false),
        FuelEntry(3, System.currentTimeMillis() - 50000000, 1800.0, 30.0, 6.8, 204.0, true),
        FuelEntry(4, System.currentTimeMillis(), 2500.0, 52.0, 6.2, 322.4, true)
    )
    val stats = calculateStats(sampleEntries, tankCapacity = 50.0)
    
    FuelAppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    MainStatsCard(stats, currency = "RON")
                }
                item {
                    ConsumptionChart(sampleEntries)
                }
                item {
                    PriceTrendChart(sampleEntries, currency = "RON")
                }
            }
        }
    }
}

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
