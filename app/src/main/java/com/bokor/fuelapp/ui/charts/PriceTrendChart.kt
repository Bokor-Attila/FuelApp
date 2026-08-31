package com.bokor.fuelapp.ui.charts

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bokor.fuelapp.R
import com.bokor.fuelapp.data.FuelEntry
import java.util.Locale

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
