package org.ciphrchat.app.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppState @Inject constructor() {
    var isOnboarded by mutableStateOf(false)
        private set

    fun completeOnboarding() {
        isOnboarded = true
    }
}
