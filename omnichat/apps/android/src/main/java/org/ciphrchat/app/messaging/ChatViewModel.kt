package org.ciphrchat.app.messaging

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ciphrchat.app.files.LargeFileTransferManager
import org.ciphrchat.app.identity.ContactRepository
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MessageRepository,
    private val contacts: ContactRepository,
    private val attachmentStore: AttachmentStore,
    private val largeFileManager: LargeFileTransferManager,
    private val groupManager: org.ciphrchat.app.groups.GroupManager
) : ViewModel() {

    val conversationId: String = savedStateHandle["conversationId"] ?: ""
    val isGroupChat = conversationId.startsWith("group_")

    val messages = repository.messages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contactsList = contacts.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversationsList = repository.conversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupState = if (isGroupChat) {
        groupManager.observeGroup(conversationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    } else {
        MutableStateFlow<org.ciphrchat.app.data.GroupEntity?>(null)
    }

    val groupMembers = if (isGroupChat) {
        groupManager.observeMembers(conversationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } else {
        MutableStateFlow<List<org.ciphrchat.app.data.GroupMemberEntity>>(emptyList())
    }

    val fileTransferProgress = largeFileManager.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _contactName = MutableStateFlow<String?>(null)
    val contactName = _contactName.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            if (isGroupChat) {
                _contactName.value = groupManager.getGroup(conversationId)?.name ?: "Group"
            } else {
                _contactName.value = contacts.find(conversationId)?.displayName
            }
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.send(conversationId, conversationId, text)
                .onFailure { _notice.value = it.message ?: "Message could not be sent" }
        }
    }

    fun sendAttachment(uri: Uri) {
        viewModelScope.launch {
            repository.sendAttachment(conversationId, conversationId, uri)
                .onFailure { _notice.value = it.message ?: "Attachment could not be sent" }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
                .onFailure { _notice.value = it.message ?: "Message could not be deleted" }
        }
    }

    fun retryMessage(messageId: String) {
        viewModelScope.launch {
            repository.retryMessage(messageId)
                .onFailure { _notice.value = it.message ?: "Could not retry message" }
        }
    }

    fun leaveGroup(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            repository.leaveGroup(conversationId)
                .onSuccess {
                    _notice.value = "You left the group"
                    onComplete(true)
                }
                .onFailure {
                    _notice.value = it.message ?: "Could not leave group"
                    onComplete(false)
                }
        }
    }

    fun forwardMessage(targetConversationId: String, message: ChatMessage, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            repository.forwardMessage(
                targetConversationId = targetConversationId,
                targetRecipientId = targetConversationId,
                originalMessage = message
            ).onSuccess {
                _notice.value = "Message forwarded"
                onComplete(true)
            }.onFailure {
                _notice.value = it.message ?: "Could not forward message"
                onComplete(false)
            }
        }
    }

    fun copyToClipboard(context: Context, text: String) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("CiphrChat Message", text)
            clipboard.setPrimaryClip(clip)
            _notice.value = "Copied to clipboard"
        }.onFailure {
            _notice.value = "Could not copy text"
        }
    }

    fun shareAttachment(context: Context, message: ChatMessage) {
        materializeAttachment(message) { result ->
            result.onSuccess { file ->
                runCatching {
                    val authority = "${context.packageName}.fileprovider"
                    val uri = FileProvider.getUriForFile(context, authority, file)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = message.attachmentMimeType ?: "*/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(shareIntent, "Share attachment")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }.onFailure {
                    _notice.value = "Could not share attachment"
                }
            }.onFailure {
                _notice.value = "Could not prepare attachment for sharing"
            }
        }
    }

    fun cancelLargeFile(fileId: String) {
        largeFileManager.cancel(fileId)
    }

    fun clearNotice() { _notice.value = null }
    fun showNotice(text: String) { _notice.value = text }

    fun clearChat(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            repository.clearConversation(conversationId)
                .onSuccess {
                    _notice.value = null
                    onComplete(true)
                }
                .onFailure {
                    _notice.value = it.message ?: "Chat could not be cleared"
                    onComplete(false)
                }
        }
    }

    fun materializeAttachment(message: ChatMessage, onReady: (Result<File>) -> Unit) {
        val name = message.attachmentFileName
        if (name.isNullOrBlank()) {
            onReady(Result.failure(IllegalStateException("Attachment is unavailable")))
            return
        }
        val path = message.attachmentStoragePath
        viewModelScope.launch {
            onReady(withContext(Dispatchers.IO) {
                runCatching {
                    if (path != null && !path.startsWith("large_file:")) {
                        attachmentStore.materialize(path, name)
                    } else {
                        repository.getOrDownloadLargeFile(message)
                            ?: error("Could not download or materialize large file")
                    }
                }
            })
        }
    }
}

