package app.aura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.aura.ui.EngineState
import app.aura.ui.navigation.AuraNavHost
import app.aura.ui.theme.AuraTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Rust Core engine asynchronously with persistent storage,
        // so conversations survive process restarts (SQLite under filesDir).
        EngineState.initializeAsync(this)

        setContent {
            val isDark by EngineState.isDarkTheme.collectAsState()
            AuraTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AuraNavHost()
                }
            }
        }
    }
}
