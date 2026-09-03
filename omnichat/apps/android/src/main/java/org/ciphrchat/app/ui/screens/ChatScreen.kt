package org.ciphrchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.ciphrchat.app.messaging.ChatMessage
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
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val resolvedContactName by viewModel.contactName.collectAsStateWithLifecycle()
    val contactsList by viewModel.contactsList.collectAsStateWithLifecycle()
    val conversationsList by viewModel.conversationsList.collectAsStateWithLifecycle()
    val groupState by viewModel.groupState.collectAsStateWithLifecycle()
    val groupMembers by viewModel.groupMembers.collectAsStateWithLifecycle()
    val transferProgressMap by viewModel.fileTransferProgress.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var showLeaveGroupConfirmation by remember { mutableStateOf(false) }
    var selectedMessageForActions by remember { mutableStateOf<ChatMessage?>(null) }
    var showForwardDialog by remember { mutableStateOf(false) }
    var showDeleteSingleConfirmation by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val currentLocale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var hasInitiallyScrolled by remember { mutableStateOf(false) }
    var previousMessageCount by remember { mutableIntStateOf(0) }
    var showNewMessagesBadge by remember { mutableStateOf(false) }

    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::sendAttachment)
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            onStartCall(viewModel.conversationId, resolvedContactName ?: contactName)
        } else {
            viewModel.showNotice("Microphone permission is required to make audio calls")
        }
    }

    val visibleMessages = remember(messages, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) messages else messages.filter { message ->
            message.body.contains(query, ignoreCase = true) ||
                message.attachmentFileName?.contains(query, ignoreCase = true) == true
        }
    }

    val isNearBottom by remember {
        derivedStateOf {
            val total = visibleMessages.size
            if (total == 0) return@derivedStateOf true
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= total - 2
        }
    }

    LaunchedEffect(visibleMessages.size, searchQuery) {
        if (visibleMessages.isEmpty() || searchQuery.isNotBlank()) return@LaunchedEffect
        if (!hasInitiallyScrolled) {
            listState.scrollToItem(visibleMessages.size - 1)
            hasInitiallyScrolled = true
            previousMessageCount = visibleMessages.size
            return@LaunchedEffect
        }

        val countDiff = visibleMessages.size - previousMessageCount
        val lastMessage = visibleMessages.lastOrNull()
        val isUserOutgoing = lastMessage?.direction == MessageDirection.OUTGOING

        if (countDiff > 0) {
            if (isUserOutgoing || isNearBottom) {
                listState.animateScrollToItem(visibleMessages.size - 1)
                showNewMessagesBadge = false
            } else {
                showNewMessagesBadge = true
            }
        }
        previousMessageCount = visibleMessages.size
    }

    LaunchedEffect(isNearBottom) {
        if (isNearBottom) {
            showNewMessagesBadge = false
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
                            Text(
                                if (viewModel.isGroupChat) "${groupMembers.size} members · E2EE Group" else "Secure conversation",
                                style = MaterialTheme.typography.labelMedium,
                                color = CiphrTextSecondary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CiphrText)
                        }
                    },
                    actions = {
                        if (!viewModel.isGroupChat) {
                            IconButton(onClick = {
                                val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                if (hasMicPermission) {
                                    onStartCall(viewModel.conversationId, resolvedContactName ?: contactName)
                                } else {
                                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            }) {
                                Icon(Icons.Default.Call, "Audio Call", tint = CiphrText)
                            }
                        }
                        IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) searchQuery = "" }) {
                            Icon(if (searchVisible) Icons.Default.Close else Icons.Default.Search, if (searchVisible) "Close search" else "Search messages", tint = CiphrText)
                        }
                        if (viewModel.isGroupChat && groupState?.isActive == true) {
                            TextButton(onClick = { showLeaveGroupConfirmation = true }) {
                                Text("Leave", color = MaterialTheme.colorScheme.error)
                            }
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
                if (viewModel.isGroupChat && groupState?.isActive == false) {
                    Surface(
                        color = CiphrSurfaceMuted,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "You left this group. Past messages are preserved.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CiphrTextSecondary,
                            modifier = Modifier.padding(14.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    ChatInputBar(
                        onSend = viewModel::send,
                        onAttach = { attachmentPicker.launch(arrayOf("*/*")) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val timeFormat = remember(currentLocale) { SimpleDateFormat("HH:mm", currentLocale) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleMessages, key = { it.id }) { message ->
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
                    val senderName = if (viewModel.isGroupChat && message.direction == MessageDirection.INCOMING) {
                        contactsList.find { it.contactId == message.senderId }?.displayName ?: "Member ${message.senderId.takeLast(6)}"
                    } else null

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
                        isForwarded = message.isForwarded,
                        senderDisplayName = senderName,
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
                        onRetryTransfer = if (fileId != null) { { viewModel.materializeAttachment(message) {} } } else null,
                        onLongClick = {
                            selectedMessageForActions = message
                        }
                    )
                }
            }

            if (showNewMessagesBadge && !isNearBottom) {
                Surface(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(visibleMessages.size - 1)
                            showNewMessagesBadge = false
                        }
                    },
                    color = CiphrPrimary,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Scroll to latest message",
                            tint = CiphrText,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "New messages",
                            color = CiphrText,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }

    selectedMessageForActions?.let { targetMessage ->
        if (!showForwardDialog && !showDeleteSingleConfirmation) {
            AlertDialog(
                onDismissRequest = { selectedMessageForActions = null },
                title = { Text("Message Actions", color = CiphrText) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (targetMessage.attachmentFileName == null && targetMessage.body.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    viewModel.copyToClipboard(context, targetMessage.body)
                                    selectedMessageForActions = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, "Copy", tint = CiphrText)
                                    Text("Copy text", color = CiphrText)
                                }
                            }
                        }

                        TextButton(
                            onClick = {
                                showForwardDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", tint = CiphrText)
                                Text("Forward", color = CiphrText)
                            }
                        }

                        if (targetMessage.attachmentFileName != null) {
                            TextButton(
                                onClick = {
                                    viewModel.shareAttachment(context, targetMessage)
                                    selectedMessageForActions = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Share, "Share", tint = CiphrText)
                                    Text("Share file", color = CiphrText)
                                }
                            }
                        }

                        if (targetMessage.direction == MessageDirection.OUTGOING &&
                            targetMessage.status != org.ciphrchat.app.messaging.MessageStatus.DELIVERED
                        ) {
                            TextButton(
                                onClick = {
                                    viewModel.retryMessage(targetMessage.id)
                                    selectedMessageForActions = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, "Retry", tint = CiphrPrimary)
                                    Text(
                                        if (targetMessage.status == org.ciphrchat.app.messaging.MessageStatus.FAILED) "Retry sending"
                                        else "Resend message",
                                        color = CiphrPrimary
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = {
                                showDeleteSingleConfirmation = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                Text("Delete for me", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { selectedMessageForActions = null }) {
                        Text("Cancel", color = CiphrTextSecondary)
                    }
                },
                containerColor = CiphrSurface
            )
        }
    }

    if (showForwardDialog && selectedMessageForActions != null) {
        val messageToForward = selectedMessageForActions!!
        val otherConversations = conversationsList.filter { it.id != viewModel.conversationId }
        val forwardTargets = (otherConversations.map { Pair(it.id, if (it.isGroup) "[Group] ${it.contactName}" else it.contactName) } +
            contactsList.filter { c -> otherConversations.none { it.id == c.contactId } && c.contactId != viewModel.conversationId }
                .map { Pair(it.contactId, it.displayName.ifBlank { "Contact ${it.contactId.takeLast(6)}" }) }).distinctBy { it.first }

        AlertDialog(
            onDismissRequest = {
                showForwardDialog = false
                selectedMessageForActions = null
            },
            title = { Text("Forward to…", color = CiphrText) },
            text = {
                if (forwardTargets.isEmpty()) {
                    Text("No other contacts or groups available to forward to.", color = CiphrTextSecondary)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(forwardTargets, key = { it.first }) { target ->
                            TextButton(
                                onClick = {
                                    viewModel.forwardMessage(target.first, messageToForward) {
                                        showForwardDialog = false
                                        selectedMessageForActions = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = target.second,
                                        color = CiphrText,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showForwardDialog = false
                    selectedMessageForActions = null
                }) {
                    Text("Cancel", color = CiphrTextSecondary)
                }
            },
            containerColor = CiphrSurface
        )
    }

    if (showLeaveGroupConfirmation) {
        AlertDialog(
            onDismissRequest = { showLeaveGroupConfirmation = false },
            title = { Text("Leave Group", color = CiphrText) },
            text = {
                Text(
                    "Are you sure you want to leave this group? You will no longer receive new messages, but your existing message history will remain readable.",
                    color = CiphrTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveGroupConfirmation = false
                    viewModel.leaveGroup()
                }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveGroupConfirmation = false }) {
                    Text("Cancel", color = CiphrTextSecondary)
                }
            },
            containerColor = CiphrSurface
        )
    }

    if (showDeleteSingleConfirmation && selectedMessageForActions != null) {
        val messageToDelete = selectedMessageForActions!!
        AlertDialog(
            onDismissRequest = {
                showDeleteSingleConfirmation = false
                selectedMessageForActions = null
            },
            title = { Text("Delete Message?", color = CiphrText) },
            text = {
                Text(
                    "This message will be removed from your device. Other recipients will keep their copy.",
                    color = CiphrTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(messageToDelete.id)
                    showDeleteSingleConfirmation = false
                    selectedMessageForActions = null
                }) {
                    Text("Delete for me", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteSingleConfirmation = false
                    selectedMessageForActions = null
                }) {
                    Text("Cancel", color = CiphrTextSecondary)
                }
            },
            containerColor = CiphrSurface
        )
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

@Composable
private fun ChatInputBar(
    onSend: (String) -> Unit,
    onAttach: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAttach) {
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
                val toSend = inputText
                inputText = ""
                onSend(toSend)
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

