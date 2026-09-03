package org.ciphrchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    var showCreateGroupDialog by remember { mutableStateOf(false) }

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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { showCreateGroupDialog = true },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = CiphrPrimarySoft,
                        contentColor = CiphrText
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("+ Group", style = MaterialTheme.typography.labelMedium)
                }
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
        }

        if (conversations.isEmpty()) {
            CiphrEmptyState(
                title = "No conversations yet",
                subtitle = "Add a contact or create a group to start messaging"
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

    if (showCreateGroupDialog) {
        var groupName by remember { mutableStateOf("") }
        val selectedContacts = remember { mutableStateListOf<String>() }

        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Create Group", color = CiphrText) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { if (it.length <= 100) groupName = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Select Members:", style = MaterialTheme.typography.titleSmall, color = CiphrText)
                    Spacer(Modifier.height(8.dp))
                    if (contacts.isEmpty()) {
                        Text("No paired contacts available to add", style = MaterialTheme.typography.bodySmall, color = CiphrTextSecondary)
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(contacts, key = { it.contactId }) { contact ->
                                val isSelected = selectedContacts.contains(contact.contactId)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isSelected) selectedContacts.remove(contact.contactId)
                                            else selectedContacts.add(contact.contactId)
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedContacts.add(contact.contactId)
                                            else selectedContacts.remove(contact.contactId)
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(contact.displayName, color = CiphrText)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = groupName.trim()
                        if (trimmed.isNotBlank() && selectedContacts.isNotEmpty()) {
                            viewModel.createGroup(trimmed, selectedContacts.toList()) { newGroupId ->
                                showCreateGroupDialog = false
                                onConversationClick(newGroupId)
                            }
                        }
                    },
                    enabled = groupName.isNotBlank() && selectedContacts.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = CiphrPrimary)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel", color = CiphrTextSecondary)
                }
            },
            containerColor = CiphrSurface
        )
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
                .background(if (conversation.isGroup) CiphrPrimarySoft else CiphrSurfaceMuted, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (conversation.isGroup) "G" else (conversation.contactName.firstOrNull()?.uppercase() ?: "?"),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = CiphrText
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.contactName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = CiphrText
                )
                if (conversation.isGroup) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = CiphrPrimary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "GROUP",
                            style = MaterialTheme.typography.labelSmall,
                            color = CiphrPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
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

private val conversationTimeFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.getDefault()) }
private fun formatTime(epochMs: Long): String {
    return conversationTimeFormat.get()?.format(Date(epochMs)) ?: ""
}
