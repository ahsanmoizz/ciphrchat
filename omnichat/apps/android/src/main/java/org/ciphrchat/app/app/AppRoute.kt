package org.ciphrchat.app.app

sealed class AppRoute(val route: String) {
    data object Welcome : AppRoute("welcome")
    data object CreateIdentity : AppRoute("create_identity")
    data object EnableConnections : AppRoute("enable_connections")
    data object IdentityReady : AppRoute("identity_ready")
    data object Chats : AppRoute("chats")
    data object Connect : AppRoute("connect")
    data object Settings : AppRoute("settings")
    data object Chat : AppRoute("chat/{conversationId}") {
        fun create(conversationId: String) = "chat/$conversationId"
    }
}
