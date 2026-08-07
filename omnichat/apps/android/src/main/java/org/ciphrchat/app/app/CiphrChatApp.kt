package org.ciphrchat.app.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.ciphrchat.app.identity.IdentityRepository
import org.ciphrchat.app.identity.InvitationService
import org.ciphrchat.app.identity.LocalIdentity
import org.ciphrchat.app.ui.components.BottomNavItem
import org.ciphrchat.app.ui.components.CiphrBottomBar
import org.ciphrchat.app.ui.screens.*
import org.ciphrchat.app.ui.theme.CiphrBackground
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val appState: AppState,
    private val invitationService: InvitationService
) : ViewModel() {
    var identity by mutableStateOf<LocalIdentity?>(null)
        private set
    var invitation by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            identityRepository.current()?.let {
                identity = it
                invitationService.createInvitation().onSuccess { value -> invitation = value }
            }
        }
    }

    fun createIdentity(name: String) {
        viewModelScope.launch {
            identityRepository.create(name).onSuccess {
                identity = it
                invitationService.createInvitation().onSuccess { value -> invitation = value }
            }
        }
    }

    fun finishOnboarding() { appState.completeOnboarding() }
    fun isOnboarded() = appState.isOnboarded
}

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val invitationService: InvitationService
) : ViewModel() {
    var status by mutableStateOf<String?>(null)
        private set

    fun importInvitation(raw: String) {
        if (raw.isBlank()) return
        viewModelScope.launch {
            invitationService.importInvitation(raw)
                .onSuccess { status = "Paired with ${it.displayName}" }
                .onFailure { status = it.message ?: "Invitation could not be imported" }
        }
    }
}

@Composable
fun CiphrChatApp(viewModel: OnboardingViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val mainRoutes = setOf(AppRoute.Chats.route, AppRoute.Connect.route, AppRoute.Settings.route)
    val showBottomBar = currentRoute in mainRoutes

    val startDest = if (viewModel.isOnboarded()) AppRoute.Chats.route else AppRoute.Welcome.route

    Scaffold(
        containerColor = CiphrBackground,
        bottomBar = {
            if (showBottomBar) {
                CiphrBottomBar(currentRoute = currentRoute) { item ->
                    navController.navigate(item.route) {
                        popUpTo(AppRoute.Chats.route) { saveState = true }
                        launchSingleTop = true; restoreState = true
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController, startDest, Modifier.padding(padding)) {
            composable(AppRoute.Welcome.route) {
                WelcomeScreen(
                    onCreateIdentity = { navController.navigate(AppRoute.CreateIdentity.route) },
                    onRestore = { /* Phase 2 */ }
                )
            }
            composable(AppRoute.CreateIdentity.route) {
                CreateIdentityScreen { name ->
                    viewModel.createIdentity(name)
                    navController.navigate(AppRoute.EnableConnections.route)
                }
            }
            composable(AppRoute.EnableConnections.route) {
                EnableConnectionsScreen { navController.navigate(AppRoute.IdentityReady.route) }
            }
            composable(AppRoute.IdentityReady.route) {
                val id = viewModel.identity
                IdentityReadyScreen(
                    displayName = id?.displayName ?: "User",
                    fingerprint = id?.fingerprint ?: "0000-0000-0000-0000",
                    qrContent = viewModel.invitation,
                    onShowQr = { /* QR is always visible */ },
                    onStartMessaging = {
                        viewModel.finishOnboarding()
                        navController.navigate(AppRoute.Chats.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable(AppRoute.Chats.route) {
                ChatsScreen(onConversationClick = { id ->
                    navController.navigate(AppRoute.Chat.create(id))
                })
            }
            composable(
                AppRoute.Chat.route,
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
            ) { entry ->
                val convId = entry.arguments?.getString("conversationId") ?: ""
                val name = when (convId) {
                    "conv-sara" -> "Sara"; "conv-ali" -> "Ali"; "conv-usman" -> "Usman"; else -> "Chat"
                }
                ChatScreen(contactName = name, onBack = { navController.popBackStack() })
            }
            composable(AppRoute.Scanner.route) {
                val connectViewModel: ConnectViewModel = hiltViewModel()
                ScannerScreen(
                    onScanResult = { raw ->
                        connectViewModel.importInvitation(raw)
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable(AppRoute.Connect.route) {
                val connectViewModel: ConnectViewModel = hiltViewModel()
                ConnectScreen(
                    onScanQr = { navController.navigate(AppRoute.Scanner.route) },
                    onImportInvitation = connectViewModel::importInvitation
                )
            }
            composable(AppRoute.Settings.route) { SettingsScreen() }
        }
    }
}
