package org.ciphrchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.ciphrchat.app.messaging.ChatViewModel
import org.ciphrchat.app.messaging.MessageDirection
import org.ciphrchat.app.ui.components.CiphrMessageBubble
import org.ciphrchat.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contactName: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val resolvedContactName by viewModel.contactName.collectAsState()
    val notice by viewModel.notice.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val currentLocale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::sendAttachment)
    }

    val visibleMessages = remember(messages, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) messages else messages.filter { message ->
            message.body.contains(query, ignoreCase = true) ||
                message.attachmentFileName?.contains(query, ignoreCase = true) == true
        }
    }

    LaunchedEffect(visibleMessages.size, searchQuery) {
        if (searchQuery.isBlank() && visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.size - 1)
        }
    }

    Scaffold(
        containerColor = CiphrBackground,
        topBar = {
            Column(Modifier.background(CiphrSurface)) {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(resolvedContactName ?: contactName, color = CiphrText)
                            Text("Secure conversation", style = MaterialTheme.typography.labelMedium, color = CiphrTextSecondary)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CiphrText)
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) searchQuery = "" }) {
                            Icon(if (searchVisible) Icons.Default.Close else Icons.Default.Search, if (searchVisible) "Close search" else "Search messages", tint = CiphrText)
                        }
                        IconButton(onClick = { showClearConfirmation = true }, enabled = messages.isNotEmpty()) {
                            Icon(Icons.Default.Delete, "Clear chat", tint = CiphrText)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CiphrSurface)
                )
                if (searchVisible) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search this chat") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(CiphrSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.AttachFile, "Attach file", tint = CiphrTextSecondary)
                }
                OutlinedTextField(
                    value = inputText, onValueChange = { inputText = it },
                    placeholder = { Text("Message") }, shape = CiphrPillShape,
                    modifier = Modifier.weight(1f), singleLine = false, maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { viewModel.send(inputText); inputText = "" }, enabled = inputText.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send",
                        tint = if (inputText.isNotBlank()) CiphrPrimaryHover else CiphrTextSecondary)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            notice?.let { message ->
                item {
                    Text(message, color = CiphrDanger, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (searchQuery.isNotBlank() && visibleMessages.isEmpty()) {
                item {
                    Text("No messages found", color = CiphrTextSecondary, modifier = Modifier.padding(vertical = 24.dp))
                }
            }
            items(visibleMessages, key = { it.id }) { message ->
                CiphrMessageBubble(
                    text = message.body,
                    time = SimpleDateFormat("HH:mm", currentLocale).format(Date(message.createdAtEpochMs)),
                    isOutgoing = message.direction == MessageDirection.OUTGOING,
                    statusLabel = if (message.direction == MessageDirection.OUTGOING) message.status.name.lowercase() else null,
                    selectedTransport = message.selectedTransport,
                    attachmentFileName = message.attachmentFileName,
                    attachmentMimeType = message.attachmentMimeType,
                    attachmentSizeBytes = message.attachmentSizeBytes,
                    onOpenAttachment = if (message.attachmentStoragePath != null) {
                        {
                            viewModel.materializeAttachment(message) { result ->
                                result.onSuccess { file ->
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, message.attachmentMimeType ?: "application/octet-stream")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    })
                                }.onFailure { }
                            }
                        }
                    } else null
                )
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear this chat?") },
            text = { Text("All messages and locally saved attachments in this conversation will be permanently removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmation = false
                    searchQuery = ""
                    viewModel.clearChat()
                }) { Text("Clear chat", color = CiphrDanger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}
