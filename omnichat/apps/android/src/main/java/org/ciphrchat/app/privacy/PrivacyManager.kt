package org.ciphrchat.app.privacy

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages user privacy preferences including IP Privacy Mode ("Hide my IP").
 */
@Singleton
class PrivacyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ciphrchat_privacy_prefs", Context.MODE_PRIVATE)

    private val _isIpPrivacyEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_IP_PRIVACY_ENABLED, DEFAULT_IP_PRIVACY_ENABLED)
    )
    val isIpPrivacyEnabled: StateFlow<Boolean> = _isIpPrivacyEnabled.asStateFlow()

    fun setIpPrivacyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IP_PRIVACY_ENABLED, enabled).apply()
        _isIpPrivacyEnabled.value = enabled
    }

    companion object {
        private const val KEY_IP_PRIVACY_ENABLED = "ip_privacy_enabled"
        const val DEFAULT_IP_PRIVACY_ENABLED = true
        const val TRUTHFUL_PRIVACY_EXPLANATION =
            "Your Internet connection is relayed so the other user does not receive your network IP."
    }
}
