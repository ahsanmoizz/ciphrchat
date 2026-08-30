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
import androidx.compose.material.icons.filled.Call
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
    onStartCall: (String, String) -> Unit = { _, _ -> },
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val resolvedContactName by viewModel.contactName.collectAsState()
    val transferProgressMap by viewModel.fileTransferProgress.collectAsState()
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
                        IconButton(onClick = {
                            onStartCall(viewModel.conversationId, resolvedContactName ?: contactName)
                        }) {
                            Icon(Icons.Default.Call, "Audio Call", tint = CiphrText)
                        }
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CiphrSurface)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(8.dp)
            ) {
                notice?.let { message ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = viewModel::clearNotice, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, "Dismiss notice", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        attachmentPicker.launch(arrayOf("*/*"))
                    }) {
                        Icon(Icons.Default.AttachFile, "Attach file", tint = CiphrTextSecondary)
                    }
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message…", color = CiphrTextSecondary) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CiphrBackground,
                            unfocusedContainerColor = CiphrBackground,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        shape = MaterialTheme.shapes.extraLarge
                    )
                    IconButton(
                        onClick = {
                            viewModel.send(inputText)
                            inputText = ""
                        },
                        enabled = inputText.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            "Send",
                            tint = if (inputText.isNotBlank()) CiphrPrimary else CiphrTextSecondary
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visibleMessages, key = { it.id }) { message ->
                val timeFormat = remember { SimpleDateFormat("HH:mm", currentLocale) }
                val timeStr = remember(message.createdAtEpochMs) { timeFormat.format(Date(message.createdAtEpochMs)) }
                val statusStr = if (message.direction == MessageDirection.OUTGOING) {
                    when (message.status) {
                        org.ciphrchat.app.messaging.MessageStatus.QUEUED -> "Queued"
                        org.ciphrchat.app.messaging.MessageStatus.ROUTING -> "Routing"
                        org.ciphrchat.app.messaging.MessageStatus.SENT -> "Sent"
                        org.ciphrchat.app.messaging.MessageStatus.DELIVERED -> "Delivered"
                        org.ciphrchat.app.messaging.MessageStatus.FAILED -> "Failed"
                    }
                } else null

                val fileId = message.attachmentStoragePath?.removePrefix("large_file:")
                val progress = if (fileId != null) transferProgressMap[fileId] else null

                CiphrMessageBubble(
                    text = message.body,
                    time = timeStr,
                    isOutgoing = message.direction == MessageDirection.OUTGOING,
                    statusLabel = statusStr,
                    selectedTransport = message.selectedTransport,
                    attachmentFileName = message.attachmentFileName,
                    attachmentMimeType = message.attachmentMimeType,
                    attachmentSizeBytes = message.attachmentSizeBytes,
                    attachmentStoragePath = message.attachmentStoragePath,
                    transferProgress = progress,
                    onOpenAttachment = if (message.attachmentFileName != null) {
                        {
                            viewModel.materializeAttachment(message) { result ->
                                result.onSuccess { file ->
                                    val authority = "${context.packageName}.fileprovider"
                                    val uri = FileProvider.getUriForFile(context, authority, file)
                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, message.attachmentMimeType ?: "*/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching { context.startActivity(viewIntent) }
                                }
                            }
                        }
                    } else null,
                    onCancelTransfer = if (fileId != null) { { viewModel.cancelLargeFile(fileId) } } else null,
                    onResumeTransfer = if (fileId != null) { { viewModel.materializeAttachment(message) {} } } else null,
                    onRetryTransfer = if (fileId != null) { { viewModel.materializeAttachment(message) {} } } else null
                )
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear Conversation?") },
            text = { Text("All messages and downloaded files in this conversation will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmation = false
                    viewModel.clearChat()
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
