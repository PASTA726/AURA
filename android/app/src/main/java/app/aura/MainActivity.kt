package app.aura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.aura.core.AuraEngine
import app.aura.ui.theme.AuraTheme
import app.aura.ui.navigation.AuraNavHost
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Rust Core engine asynchronously with persistent storage,
        // so conversations survive process restarts (SQLite under filesDir).
        thread {
            AuraEngine.initialize(filesDir.absolutePath)
        }

        setContent {
            AuraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AuraNavHost()
                }
            }
        }
    }
}
