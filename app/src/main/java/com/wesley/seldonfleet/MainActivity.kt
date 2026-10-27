package com.wesley.seldonfleet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.wesley.seldonfleet.ui.AppNavHost
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
                    // Navigation creates each screen's ViewModel with these dependencies.
                    AppNavHost(
                        repository = container.deviceRepository,
                        classify = container.classifyDevice,
                        favorites = container.favoritesRepository,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}