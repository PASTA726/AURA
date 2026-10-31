package app.aura.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aura.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraBackground),
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
                        color = AuraOnBackground
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Ready",
                        style = MaterialTheme.typography.labelMedium,
                        color = AuraSuccess
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AuraCyan)
                    )
                }
            }
        }

        // Agent Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AuraSurface)
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
                            color = AuraOnBackground
                        )
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = AuraCyan.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "● Ready",
                                style = MaterialTheme.typography.labelMedium,
                                color = AuraCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = AuraSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Model",
                                style = MaterialTheme.typography.labelSmall,
                                color = AuraOnSurfaceMuted
                            )
                            Text(
                                text = "No model loaded",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AuraOnSurface
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = AuraSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.WifiOff,
                                    contentDescription = "Offline",
                                    tint = AuraOnSurfaceMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AuraOnSurfaceMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Actions
        item {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                color = AuraOnBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // New Chat - primary button
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuraCyan,
                        contentColor = Color(0xFF001F2A)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Chat", style = MaterialTheme.typography.labelMedium)
                }
                // New Task
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraOnSurface),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(AuraSurfaceVariant)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Task", style = MaterialTheme.typography.labelMedium)
                }
                // Activity
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraOnSurface),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(AuraSurfaceVariant)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Activity", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Recent Tasks
        item {
            Text(
                text = "Recent Tasks",
                style = MaterialTheme.typography.titleMedium,
                color = AuraOnBackground
            )
        }

        item {
            RecentTaskItem(
                title = "Summarize quarterly report",
                status = "Completed",
                statusColor = AuraSuccess,
                timeAgo = "2 hours ago",
                icon = Icons.Filled.Description
            )
        }

        item {
            RecentTaskItem(
                title = "Draft email to team about sprint",
                status = "Completed",
                statusColor = AuraSuccess,
                timeAgo = "Yesterday",
                icon = Icons.Filled.Email
            )
        }

        item {
            RecentTaskItem(
                title = "Analyze codebase for refactoring",
                status = "Failed",
                statusColor = AuraError,
                timeAgo = "2 days ago",
                icon = Icons.Filled.Code
            )
        }

        // Resources Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AuraSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Resources",
                        style = MaterialTheme.typography.titleMedium,
                        color = AuraOnBackground
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ResourceInfoItem(label = "RAM", value = "— GB free")
                        ResourceInfoItem(label = "Storage", value = "— GB free")
                        ResourceInfoItem(label = "CPU", value = "—%")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentTaskItem(
    title: String,
    status: String,
    statusColor: Color,
    timeAgo: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AuraSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AuraSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = AuraCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuraOnBackground,
                    maxLines = 1
                )
                Text(
                    text = timeAgo,
                    style = MaterialTheme.typography.bodySmall,
                    color = AuraOnSurfaceMuted
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun ResourceInfoItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AuraOnSurfaceMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = AuraOnSurface
        )
    }
}
