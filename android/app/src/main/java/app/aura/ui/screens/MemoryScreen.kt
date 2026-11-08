package app.aura.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

private enum class MemoryCategory(val label: String, val native: String) {
    All("All", ""),
    LongTerm("Long-term", "long_term")
}

private data class MemoryItem(
    val id: String,
    val content: String,
    val entryType: String,
    val timestamp: String
)

private val relativeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")

private fun displayTimestamp(rfc3339: String): String = try {
    ZonedDateTime.parse(rfc3339).format(relativeFormatter)
} catch (_: Throwable) {
    ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen() {
    val palette = LocalAuraPalette.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(MemoryCategory.All) }
    var allMemories by remember { mutableStateOf<List<MemoryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var addDialogOpen by remember { mutableStateOf(false) }
    var newMemoryText by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var wipeConfirmOpen by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun reload() {
        coroutineScope.launch {
            EngineState.awaitEngine()
            loading = true
            val json = withContext(Dispatchers.IO) { AuraEngine.getMemoriesJson(200) }
            val arr = runCatching { JSONArray(json) }.getOrDefault(JSONArray())
            val parsed = (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                MemoryItem(
                    id = obj.optString("id"),
                    content = obj.optString("content"),
                    entryType = obj.optString("entryType"),
                    timestamp = displayTimestamp(obj.optString("timestamp"))
                )
            }
            allMemories = parsed
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    val filtered = allMemories.filter { item ->
        val matchesCategory =
            selectedCategory == MemoryCategory.All || item.entryType == selectedCategory.native
        val matchesSearch =
            searchQuery.isBlank() || item.content.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Memory",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.onBackground
                )
            },
            actions = {
                IconButton(
                    onClick = { wipeConfirmOpen = true },
                    enabled = allMemories.isNotEmpty()
                ) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = "Wipe all memories",
                        tint = AuraError
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
        )

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = {
                Text("Search stored memories", color = palette.onSurfaceMuted)
            },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = palette.onSurfaceMuted)
            },
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuraCyan,
                cursorColor = AuraCyan,
                focusedTextColor = palette.onBackground,
                unfocusedTextColor = palette.onBackground,
                focusedContainerColor = palette.surface,
                unfocusedContainerColor = palette.surface
            )
        )

        // Category chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(MemoryCategory.entries) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = {
                        Text(
                            category.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedCategory == category) AuraCyan
                            else palette.onSurfaceMuted
                        )
                    }
                )
            }
        }

        // Add memory
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (loading) "Loading…" else "${filtered.size} stored",
                style = MaterialTheme.typography.labelMedium,
                color = palette.onSurfaceMuted
            )
            TextButton(onClick = { addDialogOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Remember something")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!loading && filtered.isEmpty()) {
                item {
                    // Honest empty state — no invented content.
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Psychology,
                                contentDescription = null,
                                tint = palette.onSurfaceMuted
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "No memories stored yet. AURA only stores what the " +
                                    "agent engine is told to remember — add one above, or ask " +
                                    "AURA to remember something in chat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.onSurfaceMuted
                            )
                        }
                    }
                }
            }
            items(filtered) { memory ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = memory.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onSurface
                        )
                        Text(
                            text = "${memory.entryType.replace('_', ' ')} • ${memory.timestamp}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onSurfaceMuted,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (addDialogOpen) {
        AlertDialog(
            onDismissRequest = { addDialogOpen = false },
            title = { Text("Remember something") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newMemoryText,
                        onValueChange = { newMemoryText = it },
                        placeholder = { Text("e.g. I prefer short answers") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        singleLine = false
                    )
                    addError?.let { Text(
                        it,
                        color = AuraError,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    ) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val content = newMemoryText.trim()
                        if (content.isEmpty()) {
                            addError = "Nothing to store"
                        } else {
                            coroutineScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    JSONObject(AuraEngine.saveMemory(content))
                                }
                                if (result.optBoolean("ok", false)) {
                                    newMemoryText = ""
                                    addError = null
                                    addDialogOpen = false
                                    reload()
                                } else {
                                    addError = result.optString("error", "Failed to store")
                                }
                            }
                        }
                    },
                    enabled = newMemoryText.isNotBlank()
                ) { Text("Store") }
            },
            dismissButton = {
                TextButton(onClick = { addDialogOpen = false }) { Text("Cancel") }
            }
        )
    }

    if (wipeConfirmOpen) {
        AlertDialog(
            onDismissRequest = { wipeConfirmOpen = false },
            title = { Text("Delete all memories?") },
            text = {
                Text(
                    "This permanently deletes every stored long-term memory. " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        wipeConfirmOpen = false
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) { AuraEngine.wipeMemories() }
                            reload()
                        }
                    }
                ) { Text("Delete all", color = AuraError) }
            },
            dismissButton = {
                TextButton(onClick = { wipeConfirmOpen = false }) { Text("Cancel") }
            }
        )
    }
}
