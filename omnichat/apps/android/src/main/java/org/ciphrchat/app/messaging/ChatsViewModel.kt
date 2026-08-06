package org.ciphrchat.app.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ChatsViewModel @Inject constructor(
    repository: MessageRepository
) : ViewModel() {

    val conversations = repository.conversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
