package org.ciphrchat.app.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ciphrchat.app.backup.RecoveryManager
import org.ciphrchat.app.calling.AudioCallManager
import org.ciphrchat.app.calling.CallState
import org.ciphrchat.app.identity.IdentityRepository
import org.ciphrchat.app.identity.InvitationService
import org.ciphrchat.app.identity.LocalIdentity
import org.ciphrchat.app.privacy.PrivacyManager
import org.ciphrchat.app.transport.AndroidCapabilityDetector
import org.ciphrchat.app.transport.TransportRegistry
import org.ciphrchat.app.transport.TransportRuntimeManager
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
    private val transportRuntime: TransportRuntimeManager,
    private val transportRegistry: TransportRegistry,
    private val capabilityDetector: AndroidCapabilityDetector,
    private val privacyManager: PrivacyManager,
    private val audioCallManager: AudioCallManager,
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
    var connectionPermissions by mutableStateOf<List<String>>(emptyList())
        private set
    var startupMessage by mutableStateOf<String?>(null)
        private set

    val transportStates = transportRegistry.states()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isIpPrivacyEnabled = privacyManager.isIpPrivacyEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val callState = audioCallManager.callState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallState.Idle)

    init {
        viewModelScope.launch {
            runCatching { identityRepository.current() }
                .onSuccess { current ->
                    current?.let {
                        identity = it
                        appState.completeOnboarding()
                        invitationService.createInvitation().onSuccess { value -> invitation = value }
                        transportRuntime.startAll()
                    }
                }
                .onFailure { startupMessage = it.message ?: "Could not open the local identity" }
        }
        refreshConnections()
    }

    fun createIdentity(name: String) {
        viewModelScope.launch {
            identityRepository.create(name).onSuccess {
                identity = it
                invitationService.createInvitation().onSuccess { value -> invitation = value }
            }
        }
    }

    fun finishOnboarding() {
        appState.completeOnboarding()
        transportRuntime.startAll()
    }

    fun refreshConnections() {
        connectionPermissions = capabilityDetector.refresh().missingPermissions
        transportRuntime.startAll()
    }

    fun restore(uri: Uri, password: String) {
        viewModelScope.launch {
            restoreMessage = "Restoring encrypted identity…"
            val result = context.contentResolver.openInputStream(uri)?.let {
                recoveryManager.importRecoveryFile(it, password)
            } ?: Result.failure(IllegalStateException("Could not open recovery file"))
            result.onSuccess {
                identity = identityRepository.current()
                appState.completeOnboarding()
                transportRuntime.startAll()
                restoreCompleted = true
                restoreMessage = null
            }.onFailure {
                restoreMessage = it.message ?: "Recovery failed"
            }
        }
    }

    fun exportBackup(uri: Uri, password: String) {
        viewModelScope.launch {
            backupMessage = "Exporting encrypted backup…"
            val result = context.contentResolver.openOutputStream(uri)?.let {
                recoveryManager.exportRecoveryFile(it, password)
            } ?: Result.failure(IllegalStateException("Could not open backup destination"))
            result.onSuccess {
                backupMessage = "Identity backed up securely"
            }.onFailure {
                backupMessage = it.message ?: "Backup failed"
            }
        }
    }

    fun toggleIpPrivacy(enabled: Boolean) {
        privacyManager.setIpPrivacyEnabled(enabled)
    }

    fun startCall(contactId: String, contactName: String) {
        val senderId = identity?.publicId ?: return
        audioCallManager.startCall(contactId, contactName, senderId)
    }

    fun acceptCall() {
        val senderId = identity?.publicId ?: return
        audioCallManager.acceptCall(senderId)
    }

    fun declineCall() {
        val senderId = identity?.publicId ?: return
        audioCallManager.declineCall(senderId)
    }

    fun hangup() {
        val senderId = identity?.publicId ?: return
        audioCallManager.hangup(senderId)
    }

    fun toggleMute() {
        audioCallManager.toggleMute()
    }

    fun toggleSpeaker() {
        audioCallManager.toggleSpeaker()
    }

    fun isOnboarded(): Boolean = appState.isOnboarded.value
}

@Composable
fun CiphrChatApp(viewModel: OnboardingViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current
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

    val callState by viewModel.callState.collectAsState()
    val isIpPrivacyEnabled by viewModel.isIpPrivacyEnabled.collectAsState()

    LaunchedEffect(viewModel.restoreCompleted) {
        if (viewModel.restoreCompleted) {
            navController.navigate(AppRoute.Chats.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    LaunchedEffect(viewModel.identity, currentRoute) {
        if (currentRoute == AppRoute.CreateIdentity.route && viewModel.identity != null) {
            navController.navigate(AppRoute.EnableConnections.route) {
                popUpTo(AppRoute.CreateIdentity.route) { inclusive = true }
            }
        }
    }

    val mainRoutes = setOf(AppRoute.Chats.route, AppRoute.Connect.route, AppRoute.Settings.route)
    val showBottomBar = currentRoute in mainRoutes

    val startDest = if (viewModel.isOnboarded()) AppRoute.Chats.route else AppRoute.Welcome.route

    Box(modifier = Modifier.fillMaxSize()) {
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
                    }
                }
                composable(AppRoute.EnableConnections.route) {
                    EnableConnectionsScreen(
                        transportStates = viewModel.transportStates,
                        missingPermissions = viewModel.connectionPermissions,
                        onPermissionsChanged = viewModel::refreshConnections,
                        onEnable = { navController.navigate(AppRoute.IdentityReady.route) }
                    )
                }
                composable(AppRoute.IdentityReady.route) {
                    val id = viewModel.identity
                    IdentityReadyScreen(
                        displayName = id?.displayName ?: "User",
                        fingerprint = id?.fingerprint ?: "0000-0000-0000-0000",
                        qrContent = viewModel.invitation,
                        onStartMessaging = {
                            viewModel.finishOnboarding()
                            navController.navigate(AppRoute.Chats.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
                composable(AppRoute.Chats.route) {
                    ChatsScreen(
                        onConversationClick = { id -> navController.navigate(AppRoute.Chat.create(id)) },
                        onAddContact = { navController.navigate(AppRoute.Connect.route) }
                    )
                }
                composable(
                    AppRoute.Chat.route,
                    arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                ) { entry ->
                    val convId = entry.arguments?.getString("conversationId") ?: ""
                    ChatScreen(
                        contactName = convId,
                        onBack = { navController.popBackStack() },
                        onStartCall = { contactName -> viewModel.startCall(convId, contactName) }
                    )
                }
                composable(AppRoute.Scanner.route) {
                    val connectViewModel: ConnectViewModel = hiltViewModel()
                    ScannerScreen(
                        onScanResult = { raw ->
                            connectViewModel.importInvitationResult(raw).fold(
                                onSuccess = { contactId ->
                                    navController.navigate(AppRoute.Chat.create(contactId)) {
                                        popUpTo(AppRoute.Scanner.route) { inclusive = true }
                                    }
                                    Result.success(Unit)
                                },
                                onFailure = { Result.failure(it) }
                            )
                        },
                        onCancel = { navController.popBackStack() }
                    )
                }
                composable(AppRoute.Connect.route) {
                    val connectViewModel: ConnectViewModel = hiltViewModel()
                    ConnectScreen(
                        onScanQr = { navController.navigate(AppRoute.Scanner.route) },
                        onImportInvitation = connectViewModel::importInvitation,
                        onShowMyQr = { navController.navigate(AppRoute.IdentityReady.route) },
                        onFindNearby = connectViewModel::findNearby,
                        statusMessage = connectViewModel.status,
                        nearbyStatus = connectViewModel.nearbyStatus,
                        transportStates = connectViewModel.transportStates,
                        missingPermissions = connectViewModel.connectionPermissions,
                        onPermissionsChanged = connectViewModel::refreshConnections
                    )
                }
                composable(AppRoute.Settings.route) {
                    SettingsScreen(
                        onShareApp = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Install CiphrChat: https://github.com/ahsanmoizz/ciphrchat/releases")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share CiphrChat"))
                        },
                        onShowQr = { navController.navigate(AppRoute.IdentityReady.route) },
                        onRestoreIdentity = {
                            recoveryPicker.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
                        },
                        onBackupIdentity = {
                            backupPicker.launch("ciphrchat-recovery.ciphr")
                        },
                        isIpPrivacyEnabled = isIpPrivacyEnabled,
                        onToggleIpPrivacy = viewModel::toggleIpPrivacy,
                        backupMessage = viewModel.backupMessage
                    )
                }
            }
        }

        // Active Call Overlay Screen
        if (callState != CallState.Idle) {
            CallScreen(
                callState = callState,
                onAccept = viewModel::acceptCall,
                onDecline = viewModel::declineCall,
                onHangup = viewModel::hangup,
                onToggleMute = viewModel::toggleMute,
                onToggleSpeaker = viewModel::toggleSpeaker
            )
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
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text("Enter the password chosen when this recovery file was exported.")
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = recoveryPassword,
                        onValueChange = { recoveryPassword = it },
                        label = { androidx.compose.material3.Text("Recovery password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val uri = recoveryUri
                        val pass = recoveryPassword
                        showRecoveryPassword = false
                        recoveryPassword = ""
                        if (uri != null) viewModel.restore(uri, pass)
                    },
                    enabled = recoveryPassword.length >= 12
                ) {
                    androidx.compose.material3.Text("Restore")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showRecoveryPassword = false
                    recoveryPassword = ""
                }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }

    if (showBackupPassword) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showBackupPassword = false
                backupPassword = ""
            },
            title = { androidx.compose.material3.Text("Protect your backup") },
            text = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text("Choose a strong passphrase (at least 12 characters). It will encrypt your complete identity and conversation history.")
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { androidx.compose.material3.Text("Passphrase") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val uri = backupUri
                        val pass = backupPassword
                        showBackupPassword = false
                        backupPassword = ""
                        if (uri != null) viewModel.exportBackup(uri, pass)
                    },
                    enabled = backupPassword.length >= 12
                ) {
                    androidx.compose.material3.Text("Save encrypted backup")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showBackupPassword = false
                    backupPassword = ""
                }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }
}
