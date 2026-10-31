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
import app.aura.ui.theme.*
import app.aura.core.AuraEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val id: Int,
    val content: String,
    val isUser: Boolean,
    val timestamp: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    var inputText by remember { mutableStateOf("") }
    var isAgentTyping by remember { mutableStateOf(false) }
    val messages = remember {
        mutableStateListOf(
            ChatMessage(1, "Hello! I'm AURA, your local AI assistant. How can I help you today?", isUser = false, "10:00 AM"),
            ChatMessage(2, "Can you help me summarize a document?", isUser = true, "10:01 AM"),
            ChatMessage(3, "Of course! Please share the document content and I'll create a concise summary for you. I run entirely on your device, so your data stays private.", isUser = false, "10:01 AM")
        )
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraBackground)
    ) {
        // TopAppBar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Chat",
                        style = MaterialTheme.typography.titleLarge,
                        color = AuraOnBackground
                    )
                    Text(
                        text = "AURA • Local",
                        style = MaterialTheme.typography.labelSmall,
                        color = AuraOnSurfaceMuted
                    )
                }
            },
            actions = {
                IconButton(onClick = { messages.clear() }) {
                    Icon(
                        Icons.Filled.AddComment,
                        contentDescription = "New Conversation",
                        tint = AuraCyan
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = AuraSurface,
                titleContentColor = AuraOnBackground
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
                        AuraEngine.emergencyStop()
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
                    Text("Stop generation", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Composer
        Surface(
            color = AuraSurface,
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
                            color = AuraOnSurfaceMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraCyan,
                        unfocusedBorderColor = AuraSurfaceVariant,
                        focusedTextColor = AuraOnBackground,
                        unfocusedTextColor = AuraOnBackground,
                        cursorColor = AuraCyan,
                        focusedContainerColor = AuraSurfaceVariant,
                        unfocusedContainerColor = AuraSurfaceVariant
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
                                    id = messages.size + 1,
                                    content = text,
                                    isUser = true,
                                    timestamp = "Now"
                                )
                            )
                            inputText = ""
                            isAgentTyping = true
                            coroutineScope.launch {
                                listState.animateScrollToItem(messages.size - 1)
                                val response = withContext(Dispatchers.IO) {
                                    AuraEngine.processMessage(text)
                                }
                                messages.add(
                                    ChatMessage(
                                        id = messages.size + 1,
                                        content = response,
                                        isUser = false,
                                        timestamp = "Now"
                                    )
                                )
                                isAgentTyping = false
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (inputText.isNotBlank()) AuraCyan else AuraSurfaceVariant)
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) Color(0xFF001F2A) else AuraOnSurfaceMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
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
                    .background(if (isUser) AuraViolet else AuraSurfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) Color.White else AuraOnSurface
                )
            }
            if (message.timestamp.isNotEmpty()) {
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = AuraOnSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
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
            color = AuraSurfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Thinking",
                    style = MaterialTheme.typography.bodySmall,
                    color = AuraOnSurfaceMuted
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
