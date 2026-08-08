package org.ciphrchat.app.messaging

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.net.Uri
import java.io.File
import org.ciphrchat.app.identity.ContactRepository
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MessageRepository,
    private val contacts: ContactRepository,
    private val attachmentStore: AttachmentStore
) : ViewModel() {

    val conversationId: String = savedStateHandle["conversationId"] ?: ""

    val messages = repository.messages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun clearNotice() { _notice.value = null }

    fun materializeAttachment(message: ChatMessage, onReady: (Result<File>) -> Unit) {
        val path = message.attachmentStoragePath
        val name = message.attachmentFileName
        if (path.isNullOrBlank() || name.isNullOrBlank()) {
            onReady(Result.failure(IllegalStateException("Attachment is unavailable")))
            return
        }
        viewModelScope.launch {
            onReady(runCatching { attachmentStore.materialize(path, name) })
        }
    }
}
