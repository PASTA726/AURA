package app.aura.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.aura.ui.theme.*

data class TaskRecord(
    val id: Int,
    val description: String,
    val status: TaskStatus,
    val timestamp: String
)

enum class TaskStatus { Completed, Failed, Running }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen() {
    var taskHistory by remember {
        mutableStateOf(
            listOf(
                TaskRecord(1, "Summarize quarterly report PDF", TaskStatus.Completed, "Today, 2:15 PM"),
                TaskRecord(2, "Draft team announcement email", TaskStatus.Completed, "Today, 10:30 AM"),
                TaskRecord(3, "Analyze codebase for refactoring opportunities", TaskStatus.Failed, "Yesterday, 4:45 PM")
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraBackground)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Activity",
                    style = MaterialTheme.typography.titleLarge,
                    color = AuraOnBackground
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = AuraSurface
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current task section
            item {
                Text(
                    text = "CURRENT TASK",
                    style = MaterialTheme.typography.labelMedium,
                    color = AuraOnSurfaceMuted
                )
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AuraSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.HourglassEmpty,
                            contentDescription = null,
                            tint = AuraOnSurfaceMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "No active task",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AuraOnSurfaceMuted
                        )
                    }
                }
            }

            // Task history header with clear button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HISTORY",
                        style = MaterialTheme.typography.labelMedium,
                        color = AuraOnSurfaceMuted
                    )
                    if (taskHistory.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { taskHistory = emptyList() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AuraOnSurfaceMuted
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(AuraSurfaceVariant)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            if (taskHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.HistoryToggleOff,
                                contentDescription = null,
                                tint = AuraOnSurfaceMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No task history",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AuraOnSurfaceMuted
                            )
                        }
                    }
                }
            } else {
                items(taskHistory) { task ->
                    TaskHistoryItem(task = task)
                }
            }
        }
    }
}

@Composable
private fun TaskHistoryItem(task: TaskRecord) {
    val (statusColor, statusText) = when (task.status) {
        TaskStatus.Completed -> AuraSuccess to "Completed"
        TaskStatus.Failed -> AuraError to "Failed"
        TaskStatus.Running -> AuraCyan to "Running"
    }
    val statusIcon = when (task.status) {
        TaskStatus.Completed -> Icons.Filled.CheckCircle
        TaskStatus.Failed -> Icons.Filled.Error
        TaskStatus.Running -> Icons.Filled.PlayCircle
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AuraSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                statusIcon,
                contentDescription = statusText,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuraOnBackground
                )
                Text(
                    text = task.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = AuraOnSurfaceMuted
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
