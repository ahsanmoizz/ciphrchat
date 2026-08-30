package org.ciphrchat.app.messaging

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.net.Uri
import java.io.File
import org.ciphrchat.app.files.LargeFileTransferManager
import org.ciphrchat.app.identity.ContactRepository
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MessageRepository,
    private val contacts: ContactRepository,
    private val attachmentStore: AttachmentStore,
    private val largeFileManager: LargeFileTransferManager
) : ViewModel() {

    val conversationId: String = savedStateHandle["conversationId"] ?: ""

    val messages = repository.messages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fileTransferProgress = largeFileManager.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _contactName = MutableStateFlow<String?>(null)
    val contactName = _contactName.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            _contactName.value = contacts.find(conversationId)?.displayName
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

    fun cancelLargeFile(fileId: String) {
        largeFileManager.cancel(fileId)
    }

    fun clearNotice() { _notice.value = null }

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
