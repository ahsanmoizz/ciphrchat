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
import org.ciphrchat.app.identity.ContactRepository
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MessageRepository,
    private val contacts: ContactRepository
) : ViewModel() {

    val conversationId: String = savedStateHandle["conversationId"] ?: ""

    val messages = repository.messages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _contactName = MutableStateFlow<String?>(null)
    val contactName = _contactName.asStateFlow()

    init {
        viewModelScope.launch {
            _contactName.value = contacts.find(conversationId)?.displayName
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.send(conversationId, conversationId, text)
        }
    }
}
