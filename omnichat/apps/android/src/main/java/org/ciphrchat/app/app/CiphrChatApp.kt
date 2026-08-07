package org.ciphrchat.app.app

import android.content.Context
import android.net.Uri
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import org.ciphrchat.app.identity.IdentityRepository
import org.ciphrchat.app.identity.InvitationService
import org.ciphrchat.app.identity.LocalIdentity
import org.ciphrchat.app.backup.RecoveryManager
import org.ciphrchat.app.ui.components.BottomNavItem
import org.ciphrchat.app.ui.components.CiphrBottomBar
import org.ciphrchat.app.ui.screens.*
import org.ciphrchat.app.ui.theme.CiphrBackground
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val appState: AppState,
    private val invitationService: InvitationService,
    private val recoveryManager: RecoveryManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    var identity by mutableStateOf<LocalIdentity?>(null)
        private set
    var invitation by mutableStateOf<String?>(null)
        private set
    var restoreMessage by mutableStateOf<String?>(null)
        private set
    var restoreCompleted by mutableStateOf(false)
        private set
    var backupMessage by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            identityRepository.current()?.let {
                identity = it
                appState.completeOnboarding()
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

    fun restore(uri: Uri, password: String) {
        viewModelScope.launch {
            restoreMessage = "Restoring encrypted identity…"
            val result = context.contentResolver.openInputStream(uri)?.let {
                recoveryManager.importRecoveryFile(it, password)
            } ?: Result.failure(IllegalStateException("Could not open recovery file"))
            result.onSuccess {
                identity = identityRepository.current()
                appState.completeOnboarding()
                restoreCompleted = true
                restoreMessage = null
            }.onFailure {
                restoreMessage = it.message ?: "Recovery failed"
            }
        }
    }

    fun exportBackup(uri: Uri, password: String) {
        viewModelScope.launch {
            backupMessage = "Creating encrypted backup…"
            val result = context.contentResolver.openOutputStream(uri)?.let {
                recoveryManager.exportRecoveryFile(it, password)
            } ?: Result.failure(IllegalStateException("Could not create backup file"))
            backupMessage = result.fold(
                onSuccess = { "Encrypted identity backup saved" },
                onFailure = { it.message ?: "Backup failed" }
            )
        }
    }
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

    var recoveryUri by remember { mutableStateOf<Uri?>(null) }
    var showRecoveryPassword by remember { mutableStateOf(false) }
    var recoveryPassword by remember { mutableStateOf("") }
    var backupUri by remember { mutableStateOf<Uri?>(null) }
    var showBackupPassword by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    val recoveryPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        recoveryUri = uri
        showRecoveryPassword = uri != null
    }
    val backupPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        backupUri = uri
        showBackupPassword = uri != null
    }

    LaunchedEffect(viewModel.restoreCompleted) {
        if (viewModel.restoreCompleted) {
            navController.navigate(AppRoute.Chats.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

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
                    onRestore = {
                        recoveryPicker.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
                    },
                    restoreMessage = viewModel.restoreMessage
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
            composable(AppRoute.Settings.route) {
                SettingsScreen(
                    onBackupIdentity = {
                        backupPicker.launch("ciphrchat-recovery.ciphr")
                    },
                    backupMessage = viewModel.backupMessage
                )
            }
        }
    }

    if (showRecoveryPassword) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showRecoveryPassword = false
                recoveryPassword = ""
            },
            title = { androidx.compose.material3.Text("Restore identity") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = recoveryPassword,
                    onValueChange = { recoveryPassword = it },
                    label = { androidx.compose.material3.Text("Recovery password") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = recoveryPassword.length >= 12 && recoveryUri != null,
                    onClick = {
                        val uri = recoveryUri
                        if (uri != null) viewModel.restore(uri, recoveryPassword)
                        showRecoveryPassword = false
                        recoveryPassword = ""
                    }
                ) { androidx.compose.material3.Text("Restore") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showRecoveryPassword = false
                    recoveryPassword = ""
                }) { androidx.compose.material3.Text("Cancel") }
            }
        )
    }

    if (showBackupPassword) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showBackupPassword = false
                backupPassword = ""
            },
            title = { androidx.compose.material3.Text("Back up identity") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = { androidx.compose.material3.Text("New recovery password") },
                    supportingText = { androidx.compose.material3.Text("Use at least 12 characters and keep it safe.") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = backupPassword.length >= 12 && backupUri != null,
                    onClick = {
                        val uri = backupUri
                        if (uri != null) viewModel.exportBackup(uri, backupPassword)
                        showBackupPassword = false
                        backupPassword = ""
                    }
                ) { androidx.compose.material3.Text("Back up") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showBackupPassword = false
                    backupPassword = ""
                }) { androidx.compose.material3.Text("Cancel") }
            }
        )
    }
}
