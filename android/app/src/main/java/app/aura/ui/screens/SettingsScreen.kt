package app.aura.ui.screens

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
import androidx.compose.ui.unit.dp
import app.aura.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var darkTheme by remember { mutableStateOf(true) }
    var networkAccess by remember { mutableStateOf(false) }
    var maxIterations by remember { mutableStateOf(20f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraBackground)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = AuraOnBackground
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraSurface)
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
                        title = "Dark Theme",
                        subtitle = if (darkTheme) "Dark mode active" else "Light mode active",
                        icon = Icons.Filled.DarkMode,
                        checked = darkTheme,
                        onCheckedChange = { darkTheme = it }
                    )
                    HorizontalDivider(color = AuraSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        title = "Language",
                        subtitle = "English",
                        icon = Icons.Filled.Language,
                        onClick = {}
                    )
                }
            }

            // Model section
            item {
                SettingsSection(title = "MODEL") {
                    // No model card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Memory,
                                    contentDescription = null,
                                    tint = AuraOnSurfaceMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "No model loaded",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AuraOnSurface
                                    )
                                    Text(
                                        text = "Import a GGUF model to get started",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AuraOnSurfaceMuted
                                    )
                                }
                            }
                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AuraCyan.copy(alpha = 0.15f),
                                    contentColor = AuraCyan
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Import GGUF Model", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    HorizontalDivider(color = AuraSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        title = "Context Length",
                        subtitle = "4096 tokens",
                        icon = Icons.Filled.Tune,
                        onClick = {}
                    )
                }
            }

            // Agent section
            item {
                SettingsSection(title = "AGENT") {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Loop,
                                    contentDescription = null,
                                    tint = AuraOnSurfaceMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Max Iterations",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AuraOnSurface
                                )
                            }
                            Text(
                                text = maxIterations.toInt().toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = AuraCyan
                            )
                        }
                        Slider(
                            value = maxIterations,
                            onValueChange = { maxIterations = it },
                            valueRange = 1f..50f,
                            steps = 48,
                            colors = SliderDefaults.colors(
                                thumbColor = AuraCyan,
                                activeTrackColor = AuraCyan,
                                inactiveTrackColor = AuraSurfaceVariant
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("1", style = MaterialTheme.typography.labelSmall, color = AuraOnSurfaceMuted)
                            Text("50", style = MaterialTheme.typography.labelSmall, color = AuraOnSurfaceMuted)
                        }
                    }
                    HorizontalDivider(color = AuraSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleItem(
                        title = "Network Access",
                        subtitle = if (networkAccess) "Agent can access the internet" else "Agent runs fully offline",
                        icon = Icons.Filled.Wifi,
                        checked = networkAccess,
                        onCheckedChange = { networkAccess = it }
                    )
                }
            }

            // Privacy section
            item {
                SettingsSection(title = "PRIVACY") {
                    // Info card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = AuraCyan.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Filled.Shield,
                                contentDescription = null,
                                tint = AuraCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Network access is disabled by default. All processing happens locally on your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AuraOnSurface
                            )
                        }
                    }
                    HorizontalDivider(color = AuraSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraError),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(AuraError.copy(alpha = 0.5f))
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clear All Data", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // About section
            item {
                SettingsSection(title = "ABOUT") {
                    SettingsItem(
                        title = "AURA",
                        subtitle = "Version 0.1.0",
                        icon = Icons.Filled.Info,
                        onClick = {}
                    )
                    HorizontalDivider(color = AuraSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Code,
                                    contentDescription = null,
                                    tint = AuraOnSurfaceMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Technology Stack",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AuraOnSurface
                                )
                            }
                            Text(
                                text = "Built with Rust + Jetpack Compose",
                                style = MaterialTheme.typography.bodySmall,
                                color = AuraOnSurfaceMuted,
                                modifier = Modifier.padding(start = 30.dp)
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = AuraOnSurfaceMuted,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AuraSurface)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = AuraSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = AuraOnSurfaceMuted,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, color = AuraOnSurface)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = AuraOnSurfaceMuted)
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = AuraOnSurfaceMuted,
                modifier = Modifier.size(18.dp)
            )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = AuraOnSurfaceMuted,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = AuraOnSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = AuraOnSurfaceMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AuraCyan,
                checkedTrackColor = AuraCyan.copy(alpha = 0.3f),
                uncheckedThumbColor = AuraOnSurfaceMuted,
                uncheckedTrackColor = AuraSurfaceVariant
            )
        )
    }
}
