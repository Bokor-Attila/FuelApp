package com.bokor.fuelapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bokor.fuelapp.R
import com.bokor.fuelapp.data.FuelEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
