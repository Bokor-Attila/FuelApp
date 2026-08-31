package com.bokor.fuelapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class FuelWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // The data load is asynchronous, so the broadcast is held open until every widget has
        // been written. Without this the receiver returns first and the process can be killed
        // before the update lands, leaving a stale number on the home screen.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as FuelApplication
                val vehicles = app.database.vehicleDao().getAllVehiclesOnce()
                val storedId = app.settings.selectedVehicleId.first()
                val vehicle = vehicles.firstOrNull { it.id == storedId } ?: vehicles.firstOrNull()

                val entries = if (vehicle == null) {
                    emptyList()
                } else {
                    app.database.fuelDao().getEntriesForVehicle(vehicle.id).first()
                }
                val consumption = calculateConsumption(entries)
                val label = vehicle?.name ?: context.getString(R.string.app_name)

                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId, label, consumption)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        label: String,
        consumption: Double
    ) {
        val views = RemoteViews(context.packageName, R.layout.fuel_widget)

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("EXTRA_OPEN_ADD_DIALOG", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_add_button, pendingIntent)
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        views.setTextViewText(R.id.widget_label, label)
        views.setTextViewText(
            R.id.widget_consumption,
            String.format(Locale.getDefault(), "%.2f", consumption)
        )
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        fun triggerUpdate(context: Context) {
            val intent = Intent(context, FuelWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(android.content.ComponentName(context, FuelWidgetProvider::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}