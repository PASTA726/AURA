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
import androidx.compose.ui.unit.dp
import app.aura.core.AuraEngine
import app.aura.ui.EngineState
import app.aura.ui.theme.*
import app.aura.ui.theme.LocalAuraPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private data class ActivityRecord(
    val timestamp: String,
    val event: String,
    val detail: String
)

private val displayFormat = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss")

private fun displayTimestamp(rfc3339: String): String = try {
    ZonedDateTime.parse(rfc3339).format(displayFormat)
} catch (_: Throwable) {
    ""
}

private fun eventLabel(event: String): String = when (event) {
    "tool_executed" -> "TOOL EXECUTED"
    "tool_failed" -> "TOOL FAILED"
    "permission_granted" -> "PERMISSION GRANTED"
    "permission_revoked" -> "PERMISSION REVOKED"
    "model_loaded" -> "MODEL LOADED"
    "engine_started" -> "ENGINE STARTED"
    else -> event.uppercase().replace('_', ' ')
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen() {
    val palette = LocalAuraPalette.current
    var records by remember { mutableStateOf<List<ActivityRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    fun reload() {
        coroutineScope.launch {
            EngineState.awaitEngine()
            loading = true
            val json = withContext(Dispatchers.IO) { AuraEngine.getAuditJson(100) }
            val arr = runCatching { JSONArray(json) }.getOrDefault(JSONArray())
            records = (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                ActivityRecord(
                    timestamp = obj.optString("timestamp"),
                    event = obj.optString("event"),
                    detail = obj.optString("detail")
                )
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Activity",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.onBackground
                )
            },
            actions = {
                IconButton(onClick = { reload() }, enabled = !loading) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = palette.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
        )

        Text(
            text = "AUDIT LOG — every gated action the agent took, straight from the engine",
            style = MaterialTheme.typography.labelMedium,
            color = palette.onSurfaceMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!loading && records.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.HourglassEmpty,
                                contentDescription = null,
                                tint = palette.onSurfaceMuted
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "No recorded activity yet. Tool executions, permission " +
                                    "changes and model loads appear here after you use the agent.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.onSurfaceMuted
                            )
                        }
                    }
                }
            }
            items(records) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (record.event.contains("failed", ignoreCase = true))
                            AuraError.copy(alpha = 0.10f) else palette.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = eventLabel(record.event),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (record.event.contains("failed", ignoreCase = true))
                                    AuraError else AuraCyan
                            )
                            Text(
                                text = displayTimestamp(record.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onSurfaceMuted
                            )
                        }
                        Text(
                            text = record.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onSurface,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
