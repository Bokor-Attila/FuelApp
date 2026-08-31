package com.bokor.fuelapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bokor.fuelapp.R
import com.bokor.fuelapp.data.FuelEntry
import com.bokor.fuelapp.domain.FuelStats
import com.bokor.fuelapp.domain.consumptionTrend
import com.bokor.fuelapp.domain.monthlySpending
import com.bokor.fuelapp.domain.priceSummary
import com.bokor.fuelapp.domain.spendingSummary
import com.bokor.fuelapp.domain.usageSummary
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    entries: List<FuelEntry>,
    stats: FuelStats,
    currency: String,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.not_enough_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(innerPadding).padding(24.dp)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SpendingCard(entries, currency) }
            item { UsageCard(entries) }
            item { PriceCard(entries, currency) }
            item { ConsumptionCard(entries, stats) }
            item { MonthlyCard(entries, currency) }
        }
    }
}

@Composable
private fun StatSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.padding(top = 12.dp)) { content() }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

private fun money(value: Double, currency: String): String =
    String.format(Locale.getDefault(), "%.0f %s", value, currency)

private fun oneDecimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

private fun percent(value: Double): String = String.format(Locale.getDefault(), "%.1f", abs(value))

private fun dayOf(millis: Long): String =
    SimpleDateFormat("yyyy. MMM dd.", Locale.getDefault()).format(Date(millis))

@Composable
private fun SpendingCard(entries: List<FuelEntry>, currency: String) {
    val summary = spendingSummary(entries)
    StatSection(stringResource(R.string.spending)) {
        StatRow(stringResource(R.string.this_month), money(summary.thisMonth, currency))
        StatRow(stringResource(R.string.last_month), money(summary.lastMonth, currency))
        StatRow(stringResource(R.string.year_to_date), money(summary.yearToDate, currency))
        StatRow(stringResource(R.string.monthly_average), money(summary.monthlyAverage, currency))
    }
}

@Composable
private fun UsageCard(entries: List<FuelEntry>) {
    val usage = usageSummary(entries)
    StatSection(stringResource(R.string.usage)) {
        if (usage.kmPerMonth <= 0.0) {
            Text(
                text = stringResource(R.string.not_enough_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            return@StatSection
        }
        StatRow(stringResource(R.string.km_per_month), "${usage.kmPerMonth.toInt()} km")
        StatRow(stringResource(R.string.liters_per_month), "${oneDecimal(usage.litersPerMonth)} L")
        StatRow(
            stringResource(R.string.days_between_fillups),
            oneDecimal(usage.averageDaysBetweenFillUps)
        )
        usage.daysUntilNextFillUp?.let { days ->
            Note(
                if (days > 0) stringResource(R.string.next_fillup_in, days)
                else stringResource(R.string.next_fillup_due)
            )
        }
    }
}

@Composable
private fun PriceCard(entries: List<FuelEntry>, currency: String) {
    val summary = priceSummary(entries) ?: return
    val perLiter = stringResource(R.string.price_per_liter_unit, currency)

    StatSection(stringResource(R.string.price_section)) {
        StatRow(
            stringResource(R.string.cheapest),
            "${oneDecimal(summary.cheapest.pricePerLiter)} $perLiter · ${dayOf(summary.cheapest.date)}"
        )
        StatRow(
            stringResource(R.string.most_expensive),
            "${oneDecimal(summary.dearest.pricePerLiter)} $perLiter · ${dayOf(summary.dearest.date)}"
        )
        StatRow(
            stringResource(R.string.latest_price),
            "${oneDecimal(summary.latest.pricePerLiter)} $perLiter"
        )
        val delta = summary.latestVsAveragePercent
        Note(
            when {
                delta > 1.0 -> stringResource(R.string.above_average, percent(delta))
                delta < -1.0 -> stringResource(R.string.below_average, percent(delta))
                else -> stringResource(R.string.at_average)
            }
        )
    }
}

@Composable
private fun ConsumptionCard(entries: List<FuelEntry>, stats: FuelStats) {
    val unit = stringResource(R.string.consumption_unit)
    StatSection(stringResource(R.string.consumption_section)) {
        val best = stats.bestTank
        val worst = stats.worstTank
        if (best == null || worst == null) {
            Text(
                text = stringResource(R.string.not_enough_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            return@StatSection
        }
        StatRow(
            stringResource(R.string.best_tank),
            "${oneDecimal(best.consumption)} $unit · ${dayOf(best.date)}"
        )
        StatRow(
            stringResource(R.string.worst_tank),
            "${oneDecimal(worst.consumption)} $unit · ${dayOf(worst.date)}"
        )

        consumptionTrend(entries)?.let { trend ->
            Note(
                when {
                    !trend.isSignificant -> stringResource(R.string.trend_steady)
                    trend.changePercent > 0 ->
                        stringResource(R.string.trend_worse, percent(trend.changePercent))
                    else -> stringResource(R.string.trend_better, percent(trend.changePercent))
                }
            )
        }
    }
}

@Composable
private fun MonthlyCard(entries: List<FuelEntry>, currency: String) {
    val months = monthlySpending(entries)
    val formatter = rememberMonthFormatter()

    StatSection(stringResource(R.string.by_month)) {
        months.forEachIndexed { index, month ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StatRow(
                month.month.format(formatter),
                "${money(month.cost, currency)} · ${oneDecimal(month.liters)} L"
            )
        }
    }
}

@Composable
private fun rememberMonthFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy MMM", Locale.getDefault())
