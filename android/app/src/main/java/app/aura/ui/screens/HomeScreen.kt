package app.aura.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aura.ui.EngineState
import app.aura.ui.theme.*
import app.aura.ui.theme.LocalAuraPalette

@Composable
fun HomeScreen(onNavigate: (route: String) -> Unit = {}) {
    val palette = LocalAuraPalette.current

    val engineReady by EngineState.engineReady.collectAsState()
    val agentStatus by EngineState.agentStatus.collectAsState()
    val modelLoaded by EngineState.modelLoaded.collectAsState()
    val modelInfo by EngineState.modelInfo.collectAsState()
    val conversationCount by EngineState.conversationCount.collectAsState()

    // Fresh snapshot every time the Home screen is (re)shown.
    LaunchedEffect(Unit) { EngineState.refreshStatus() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AURA",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = palette.onBackground
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (engineReady) "Engine ready" else "Engine offline",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (engineReady) AuraSuccess else AuraError
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (engineReady) AuraCyan else AuraError)
                    )
                }
            }
        }

        // Agent Status Card — real engine snapshot
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Agent Status",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.onBackground
                        )
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (agentStatus == "paused") AuraError.copy(alpha = 0.15f)
                            else AuraCyan.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "● ${agentStatus.replaceFirstChar { it.uppercase() }}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (agentStatus == "paused") AuraError else AuraCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = palette.surfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Model",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onSurfaceMuted
                            )
                            Text(
                                text = modelInfo?.optString("name")?.takeIf { it.isNotBlank() && modelLoaded }
                                    ?: "No model loaded",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.onSurface
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (modelLoaded) AuraSuccess.copy(alpha = 0.15f)
                            else palette.surfaceVariant
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (modelLoaded) "GGUF • on-device" else "placeholder",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (modelLoaded) AuraSuccess else palette.onSurfaceMuted
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = palette.surfaceVariant)
                    Text(
                        text = "$conversationCount stored conversation(s). Chat history " +
                            "survives app restarts (SQLite).",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceMuted
                    )
                }
            }
        }

        // Quick actions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.onBackground
                    )
                    if (!modelLoaded) {
                        // Honest onboarding prompt when no model present
                        Text(
                            text = "Load a GGUF model to enable real on-device inference. " +
                                "Until then the agent answers on its internal placeholder brain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onSurfaceMuted
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickAction(
                            icon = Icons.Filled.Chat,
                            label = "Chat",
                            onClick = { onNavigate("chat") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickAction(
                            icon = Icons.Filled.Memory,
                            label = if (modelLoaded) "Model OK" else "Load model",
                            onClick = { onNavigate("settings") },
                            modifier = Modifier.weight(1f),
                            highlighted = !modelLoaded
                        )
                        QuickAction(
                            icon = Icons.Filled.History,
                            label = "Activity",
                            onClick = { onNavigate("activity") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(color = palette.surfaceVariant)
                    QuickAction(
                        icon = Icons.Filled.Delete,
                        label = "Start a new conversation",
                        onClick = {
                            onNavigate("chat")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Capabilities overview — what really ships today
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "What AURA can do today",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.onBackground
                    )
                    CapabilityRow(Icons.Filled.Bolt, "Reasoning loop with a tool protocol (JSON actions)")
                    CapabilityRow(Icons.Filled.Search, "Search files inside granted roots only")
                    CapabilityRow(Icons.Filled.CalendarMonth, "Time & device info tools")
                    CapabilityRow(Icons.Filled.Memory, "Remember facts into SQLite long-term memory")
                    CapabilityRow(Icons.Filled.Shield, "Everything offline; audit log of every action")
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    val palette = LocalAuraPalette.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (highlighted) AuraCyan else palette.onSurface
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun CapabilityRow(icon: ImageVector, label: String) {
    val palette = LocalAuraPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = AuraCyan,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.onSurface
        )
    }
}
