package com.bokor.fuelapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bokor.fuelapp.ui.FuelDashboard
import com.bokor.fuelapp.ui.theme.FuelAppTheme

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
