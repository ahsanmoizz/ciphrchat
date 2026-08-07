package org.ciphrchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.ciphrchat.app.messaging.ChatViewModel
import org.ciphrchat.app.messaging.MessageDirection
import org.ciphrchat.app.ui.components.CiphrMessageBubble
import org.ciphrchat.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contactName: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val resolvedContactName by viewModel.contactName.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val currentLocale = LocalConfiguration.current.locales[0]

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        containerColor = CiphrBackground,
        topBar = {
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CiphrSurface)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(CiphrSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
            items(messages, key = { it.id }) { message ->
                CiphrMessageBubble(
                    text = message.body,
                    time = SimpleDateFormat("HH:mm", currentLocale).format(Date(message.createdAtEpochMs)),
                    isOutgoing = message.direction == MessageDirection.OUTGOING,
                    statusLabel = if (message.direction == MessageDirection.OUTGOING) message.status.name.lowercase() else null
                )
            }
        }
    }
}
