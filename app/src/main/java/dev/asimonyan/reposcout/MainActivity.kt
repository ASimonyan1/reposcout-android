package dev.asimonyan.reposcout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.asimonyan.reposcout.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: ScoutViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ScoutViewModel((application as ScoutApplication).repository) as T
            })
            val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFFCCBFFF))
                else lightColorScheme(primary = Color(0xFF625099), background = Color(0xFFF9F7FF), surface = Color(0xFFF9F7FF))
            MaterialTheme(colorScheme = colors) { ScoutScreen(model) }
        }
    }
}
