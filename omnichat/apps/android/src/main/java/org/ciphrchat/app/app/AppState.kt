package org.ciphrchat.app.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppState @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences("ciphrchat_app_state", Context.MODE_PRIVATE)

    var isOnboarded by mutableStateOf(preferences.getBoolean(KEY_ONBOARDED, false))
        private set

    fun completeOnboarding() {
        isOnboarded = true
        preferences.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    fun resetOnboarding() {
        isOnboarded = false
        preferences.edit().putBoolean(KEY_ONBOARDED, false).apply()
    }

    private companion object {
        const val KEY_ONBOARDED = "onboarded"
    }
}
