package app.aura.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.aura.ui.EngineState
import app.aura.ui.theme.*
import app.aura.ui.theme.LocalAuraPalette
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val palette = LocalAuraPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isDark by EngineState.isDarkTheme.collectAsState()
    val maxIterations by EngineState.maxIterations.collectAsState()
    val modelLoaded by EngineState.modelLoaded.collectAsState()
    val modelInfo by EngineState.modelInfo.collectAsState()

    var modelStatusMessage by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    // SAF file picker: user picks a .gguf model; copied into filesDir/models
    // then loaded through the engine. Nothing is downloaded automatically.
    val pickModelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                modelStatusMessage = null
                val result = JSONObject(
                    EngineState.importAndLoadModel(context, uri)
                )
                modelStatusMessage =
                    if (result.optBoolean("ok", false)) {
                        "Model loaded: ${result.optString("name")}"
                    } else {
                        "Model load failed: ${result.optString("error", "unknown")}"
                    }
                isImporting = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.onBackground
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // General section
            item {
                SettingsSection(title = "GENERAL") {
                    SettingsToggleItem(
                        title = "Dark theme",
                        subtitle = if (isDark) "Dark mode active" else "Light mode active",
                        icon = Icons.Filled.DarkMode,
                        checked = isDark,
                        onCheckedChange = { EngineState.setDarkTheme(context, it) }
                    )
                }
            }

            // Model section — real GGUF loading
            item {
                SettingsSection(title = "MODEL (ON-DEVICE GGUF)") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.Memory,
                                contentDescription = null,
                                tint = if (modelLoaded) AuraCyan else palette.onSurfaceMuted
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (modelLoaded) {
                                        modelInfo?.optString("name")?.takeIf { it.isNotBlank() }
                                            ?: "Model loaded"
                                    } else "No model loaded",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = palette.onBackground
                                )
                                Text(
                                    text = if (modelLoaded) {
                                        val info = modelInfo
                                        val quant = info?.optString("quantization").orEmpty()
                                        val ctx = info?.optString("contextLength").orEmpty()
                                        buildString {
                                            append("On-device inference ready")
                                            if (quant.isNotBlank() && quant != "unknown")
                                                append(" • Q: ").append(quant)
                                            if (ctx.isNotBlank())
                                                append(" • ctx ").append(ctx)
                                        }
                                    } else {
                                        "The agent runs on a local placeholder until you load a GGUF model"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.onSurfaceMuted
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                pickModelLauncher.launch(arrayOf("*/*"))
                            },
                            enabled = !isImporting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Importing & loading…")
                            } else {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Load GGUF model from device")
                            }
                        }
                        modelStatusMessage?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (it.startsWith("Model loaded")) AuraSuccess else AuraError
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Model files stay in the app's private storage. " +
                                "Nothing is downloaded from the internet — you choose the file.",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onSurfaceMuted
                        )
                    }
                }
            }

            // Agent limits — real engine connection
            item {
                SettingsSection(title = "AGENT LIMITS") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Max planning iterations",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.onBackground
                            )
                            Text(
                                maxIterations.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                color = AuraCyan
                            )
                        }
                        Slider(
                            value = maxIterations.toFloat(),
                            onValueChange = { v ->
                                EngineState.setMaxIterationsPreview(v.toInt().coerceIn(1, 64))
                            },
                            onValueChangeFinished = {
                                EngineState.setMaxIterationsCommit(context, maxIterations)
                            },
                            valueRange = 1f..64f,
                            steps = 62,
                            colors = SliderDefaults.colors(
                                thumbColor = AuraCyan,
                                activeTrackColor = AuraCyan,
                                inactiveTrackColor = palette.surfaceVariant
                            )
                        )
                        Text(
                            "Hard cap on one request's reasoning→tool→answer loop steps (1–64).",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onSurfaceMuted
                        )
                    }
                }
            }

            // About
            item {
                SettingsSection(title = "ABOUT") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "AURA — local-first autonomous agent",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.onBackground
                        )
                        Text(
                            "v${app.aura.BuildConfig.VERSION_NAME} • Rust core + llama.cpp " +
                                "GGUF inference on-device. All data stays in this app's private " +
                                "storage; no network access is used by the agent.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onSurfaceMuted,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalAuraPalette.current
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = palette.onSurfaceMuted
        )
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = palette.surface)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalAuraPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = palette.onSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = palette.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AuraCyan,
                checkedTrackColor = AuraCyan.copy(alpha = 0.5f)
            )
        )
    }
}
