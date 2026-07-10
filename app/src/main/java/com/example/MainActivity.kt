package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.ReaderViewModel
import com.example.ui.Screen
import com.example.ui.HomeScreen
import com.example.ui.ReaderScreen
import com.example.ui.HistoryScreen
import com.example.ui.SettingsScreen

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    // Instantiate ReaderViewModel with application context
    val viewModel = ReaderViewModel(applicationContext)

    setContent {
      MyApplicationTheme {
        val currentScreen by viewModel.currentScreen.collectAsState()

        Surface(modifier = Modifier.fillMaxSize()) {
          when (currentScreen) {
            is Screen.Home -> HomeScreen(viewModel = viewModel)
            is Screen.Reader -> ReaderScreen(viewModel = viewModel)
            is Screen.History -> HistoryScreen(viewModel = viewModel)
            is Screen.Settings -> SettingsScreen(viewModel = viewModel)
          }
        }
      }
    }
  }
}
