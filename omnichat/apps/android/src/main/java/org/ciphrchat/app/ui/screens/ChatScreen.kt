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
    onStartCall: (String) -> Unit = {},
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
                        IconButton(onClick = {
                            val targetName = resolvedContactName ?: contactName
                            onStartCall(targetName)
                        }) {
                            Icon(Icons.Default.Call, "Audio call", tint = CiphrText)
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
            Row(
                modifier = Modifier.fillMaxWidth().background(CiphrSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.AttachFile, "Attach file", tint = CiphrTextSecondary)
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Type an encrypted message...") },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CiphrPrimary,
                        unfocusedBorderColor = CiphrBorder,
                        focusedTextColor = CiphrText,
                        unfocusedTextColor = CiphrText
                    )
                )
                IconButton(
                    onClick = {
                        val text = inputText.trim()
                        if (text.isNotEmpty()) {
                            viewModel.send(text)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) CiphrPrimary else CiphrTextSecondary
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            notice?.let { text ->
                Surface(
                    color = CiphrSurface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = CiphrTextSecondary
                    )
                }
            }

            if (visibleMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchQuery.isBlank()) "No messages yet.\nMessages are end-to-end encrypted with Signal Protocol."
                        else "No messages match \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CiphrTextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(visibleMessages, key = { it.id }) { message ->
                        val timeFormat = remember(currentLocale) {
                            SimpleDateFormat("HH:mm", currentLocale)
                        }
                        val formattedTime = remember(message.createdAtEpochMs) {
                            timeFormat.format(Date(message.createdAtEpochMs))
                        }

                        CiphrMessageBubble(
                            message = message,
                            formattedTime = formattedTime,
                            onOpenAttachment = {
                                val storagePath = message.attachmentStoragePath ?: return@CiphrMessageBubble
                                val file = java.io.File(storagePath)
                                if (file.exists()) {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.files",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, message.attachmentMimeType ?: "*/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open attachment"))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear conversation") },
            text = { Text("Are you sure you want to delete all messages and private attachments in this conversation? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearConversation()
                        showClearConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear")
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
