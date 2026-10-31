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
import app.aura.ui.theme.*

enum class MemoryCategory(val label: String) {
    All("All"),
    Preferences("Preferences"),
    LongTerm("Long-term"),
    Conversations("Conversations"),
    Tasks("Tasks")
}

data class MemoryItem(
    val id: Int,
    val content: String,
    val type: MemoryCategory,
    val timestamp: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen() {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(MemoryCategory.All) }

    val allMemories = remember {
        listOf(
            MemoryItem(1, "User prefers concise, bullet-pointed responses when summarizing documents.", MemoryCategory.Preferences, "3 days ago"),
            MemoryItem(2, "Working on a Rust + Android project called AURA, a local-first AI agent.", MemoryCategory.LongTerm, "1 week ago"),
            MemoryItem(3, "Had a conversation about quarterly report summarization on October 28.", MemoryCategory.Conversations, "3 days ago")
        )
    }

    val filteredMemories = remember(searchQuery, selectedCategory) {
        allMemories.filter { item ->
            val matchesCategory = selectedCategory == MemoryCategory.All || item.type == selectedCategory
            val matchesSearch = searchQuery.isBlank() || item.content.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraBackground)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Memory",
                    style = MaterialTheme.typography.titleLarge,
                    color = AuraOnBackground
                )
            },
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = AuraOnSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraSurface)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = {
                        Text("Search memories…", color = AuraOnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = AuraOnSurfaceMuted)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = AuraOnSurfaceMuted)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraCyan,
                        unfocusedBorderColor = AuraSurfaceVariant,
                        focusedTextColor = AuraOnBackground,
                        unfocusedTextColor = AuraOnBackground,
                        cursorColor = AuraCyan,
                        focusedContainerColor = AuraSurfaceVariant,
                        unfocusedContainerColor = AuraSurfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }

            // Category filter chips
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(MemoryCategory.entries) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category.label, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AuraCyan.copy(alpha = 0.15f),
                                selectedLabelColor = AuraCyan,
                                containerColor = AuraSurfaceVariant,
                                labelColor = AuraOnSurfaceMuted
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedCategory == category,
                                selectedBorderColor = AuraCyan.copy(alpha = 0.4f),
                                borderColor = AuraSurfaceVariant
                            )
                        )
                    }
                }
            }

            // Memory items
            if (filteredMemories.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Psychology,
                                contentDescription = null,
                                tint = AuraOnSurfaceMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No memories found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AuraOnSurfaceMuted
                            )
                        }
                    }
                }
            } else {
                items(filteredMemories) { memory ->
                    MemoryItemCard(memory = memory)
                }
            }

            // Reset Memory button
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraError),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(AuraError.copy(alpha = 0.5f))
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Filled.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Reset Memory", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun MemoryItemCard(memory: MemoryItem) {
    val typeColor = when (memory.type) {
        MemoryCategory.Preferences -> AuraCyan
        MemoryCategory.LongTerm -> AuraVioletLight
        MemoryCategory.Conversations -> AuraWarning
        MemoryCategory.Tasks -> AuraSuccess
        MemoryCategory.All -> AuraOnSurfaceMuted
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AuraSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = typeColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = memory.type.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = typeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = memory.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = AuraOnSurfaceMuted
                )
            }
            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium,
                color = AuraOnSurface,
                maxLines = 3
            )
        }
    }
}
