package app.aura.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
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

data class ChatMessage(
    val id: Long,
    val content: String,
    val isUser: Boolean,
    val timestamp: String = "",
    val isError: Boolean = false,
    /** Internal tool/system traffic — rendered as a compact detail chip. */
    val isSystemChip: Boolean = false,
)

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun displayTimestamp(rfc3339: String): String = try {
    ZonedDateTime.parse(rfc3339).format(timeFormatter)
} catch (_: Throwable) {
    ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val palette = LocalAuraPalette.current
    var inputText by remember { mutableStateOf("") }
    var isAgentTyping by remember { mutableStateOf(false) }
    var loadingHistory by remember { mutableStateOf(true) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var nextId by remember { mutableLongStateOf(1L) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Load the REAL persisted conversation from the engine (after engine ready).
    LaunchedEffect(Unit) {
        EngineState.awaitEngine()
        val history = withContext(Dispatchers.IO) { JSONObject(AuraEngine.getHistoryJson()) }
        val stored = history.optJSONArray("messages") ?: JSONArray()
        for (i in 0 until stored.length()) {
            val m = stored.optJSONObject(i) ?: continue
            val role = m.optString("role")
            messages.add(
                ChatMessage(
                    id = nextId++,
                    content = m.optString("content"),
                    isUser = role == "user",
                    timestamp = displayTimestamp(m.optString("timestamp")),
                    isError = role == "error",
                    isSystemChip = role == "tool" || role == "system"
                )
            )
        }
        loadingHistory = false
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Auto-scroll whenever the conversation grows.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Chat",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.onBackground
                    )
                    Text(
                        text = "AURA • Local • On-device",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSurfaceMuted
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            isAgentTyping = true
                            val result = withContext(Dispatchers.IO) {
                                JSONObject(AuraEngine.newConversation())
                            }
                            if (result.optBoolean("ok", false)) {
                                messages.clear()
                            } else {
                                // Engine refused the wipe — surface it honestly.
                                messages.add(
                                    ChatMessage(
                                        id = nextId++,
                                        content = "New conversation failed: " +
                                            result.optString("error", "unknown"),
                                        isUser = false,
                                        isError = true
                                    )
                                )
                            }
                            isAgentTyping = false
                        }
                    },
                    enabled = !loadingHistory && !isAgentTyping
                ) {
                    Icon(
                        Icons.Filled.AddComment,
                        contentDescription = "New Conversation",
                        tint = palette.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = palette.surface,
                titleContentColor = palette.onBackground
            )
        )

        // Messages area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (loadingHistory) {
                item {
                    Text(
                        "Loading stored conversation…",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceMuted
                    )
                }
            } else if (messages.isEmpty()) {
                item {
                    Text(
                        "No stored conversation. Say something to AURA.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceMuted
                    )
                }
            }
            items(messages) { message ->
                ChatBubble(message = message)
            }
            if (isAgentTyping) {
                item {
                    TypingIndicator()
                }
            }
        }

        // Stop generation button
        if (isAgentTyping) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) { AuraEngine.emergencyStop() }
                        }
                        isAgentTyping = false
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraError),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(AuraError)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Emergency stop", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Composer
        Surface(
            color = palette.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Message AURA…",
                            color = palette.onSurfaceMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraCyan,
                        unfocusedBorderColor = palette.surfaceVariant,
                        focusedTextColor = palette.onBackground,
                        unfocusedTextColor = palette.onBackground,
                        cursorColor = AuraCyan,
                        focusedContainerColor = palette.surfaceVariant,
                        unfocusedContainerColor = palette.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                IconButton(
                    onClick = {
                        val text = inputText.trim()
                        if (text.isNotEmpty() && !isAgentTyping) {
                            messages.add(
                                ChatMessage(
                                    id = nextId++,
                                    content = text,
                                    isUser = true,
                                    timestamp = ZonedDateTime.now().format(timeFormatter)
                                )
                            )
                            inputText = ""
                            isAgentTyping = true
                            coroutineScope.launch {
                                val response = withContext(Dispatchers.IO) {
                                    EngineState.awaitEngine()
                                    AuraEngine.processMessage(text)
                                }
                                val isEngineError = response.startsWith("Error:")
                                messages.add(
                                    ChatMessage(
                                        id = nextId++,
                                        content = response,
                                        isUser = false,
                                        timestamp = ZonedDateTime.now().format(timeFormatter),
                                        isError = isEngineError
                                    )
                                )
                                isAgentTyping = false
                            }
                        }
                    },
                    enabled = inputText.isNotBlank() && !isAgentTyping,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (inputText.isNotBlank()) AuraCyan else palette.surfaceVariant)
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) Color(0xFF001F2A) else palette.onSurfaceMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val palette = LocalAuraPalette.current
    // Internal tool/system rows: compact neutral chip, never an assistant bubble.
    if (message.isSystemChip) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.surfaceVariant.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    palette.onSurfaceMuted.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceMuted,
                    maxLines = 4,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        return
    }
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AuraCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("A", style = MaterialTheme.typography.labelMedium, color = AuraCyan)
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isUser) 16.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(
                        when {
                            message.isError -> AuraError.copy(alpha = 0.16f)
                            isUser -> AuraViolet
                            else -> palette.surfaceVariant
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        // Engine errors are surfaced verbatim, in error color.
                        message.isError -> AuraError
                        isUser -> Color.White
                        else -> palette.onSurface
                    }
                )
            }
            if (message.timestamp.isNotEmpty()) {
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val palette = LocalAuraPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AuraCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text("A", style = MaterialTheme.typography.labelMedium, color = AuraCyan)
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Thinking",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceMuted
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = AuraCyan,
                    strokeWidth = 1.5.dp
                )
            }
        }
    }
}
