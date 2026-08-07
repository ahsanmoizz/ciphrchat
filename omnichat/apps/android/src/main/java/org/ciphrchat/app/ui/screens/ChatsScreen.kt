package org.ciphrchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.ciphrchat.app.messaging.ChatsViewModel
import org.ciphrchat.app.messaging.ConversationSummary
import org.ciphrchat.app.ui.components.CiphrEmptyState
import org.ciphrchat.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatsScreen(
    onConversationClick: (String) -> Unit,
    onAddContact: () -> Unit,
    viewModel: ChatsViewModel = hiltViewModel()
) {
    val conversations by viewModel.conversations.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CiphrBackground)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Messages",
                style = MaterialTheme.typography.headlineLarge,
                color = CiphrText
            )
            FloatingActionButton(
                onClick = onAddContact,
                containerColor = CiphrPrimary,
                contentColor = CiphrText,
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.Add, "Add contact")
            }
        }

        if (conversations.isEmpty()) {
            CiphrEmptyState(
                title = "No conversations yet",
                subtitle = "Add a contact to start messaging"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        onClick = { onConversationClick(conversation.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationSummary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(CiphrPrimarySoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = conversation.contactName.firstOrNull()?.uppercase() ?: "?",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = CiphrText
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.contactName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = CiphrText
            )
            Text(
                text = conversation.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = CiphrTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = formatTime(conversation.lastMessageEpochMs),
            style = MaterialTheme.typography.labelMedium,
            color = CiphrTextSecondary
        )
    }
}

private fun formatTime(epochMs: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMs))
}
