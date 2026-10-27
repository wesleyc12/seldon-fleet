package com.wesley.seldonfleet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wesley.seldonfleet.ui.list.FleetListScreen
import com.wesley.seldonfleet.ui.list.FleetListViewModel
import com.wesley.seldonfleet.ui.theme.SeldonFleetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Builds the app objects by hand (manual dependency injection, spec 6.3).
        val container = AppContainer(applicationContext)
        setContent {
            SeldonFleetTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // The factory hands the repository and the rule to the ViewModel.
                    // The same ViewModel instance survives screen rotation.
                    val listViewModel: FleetListViewModel = viewModel(
                        factory = FleetListViewModel.factory(
                            repository = container.deviceRepository,
                            classify = container.classifyDevice,
                        ),
                    )
                    FleetListScreen(
                        viewModel = listViewModel,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}