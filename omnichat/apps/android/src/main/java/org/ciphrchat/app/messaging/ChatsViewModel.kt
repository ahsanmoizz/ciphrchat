package org.ciphrchat.app.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ciphrchat.app.identity.ContactRepository
import javax.inject.Inject

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val repository: MessageRepository,
    private val contactRepository: ContactRepository
) : ViewModel() {

    val conversations = repository.conversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contacts = contactRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createGroup(name: String, memberIds: List<String>, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.createGroup(name, memberIds)
            result.onSuccess { groupId ->
                onCreated(groupId)
            }
        }
    }
}
