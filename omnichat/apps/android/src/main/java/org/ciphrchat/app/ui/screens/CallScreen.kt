package org.ciphrchat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.ciphrchat.app.calling.CallState

@Composable
fun CallScreen(
    callState: CallState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val micPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onAccept()
        } else {
            onDecline()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                val (contactName, statusText) = when (callState) {
                    is CallState.OutgoingRinging -> Pair(callState.contactName, "Ringing...")
                    is CallState.IncomingRinging -> Pair(callState.contactName, "Incoming Audio Call...")
                    is CallState.Connecting -> Pair(callState.contactName, "Connecting...")
                    is CallState.Connected -> Pair(callState.contactName, formatDuration(callState.connectedAtEpochMs))
                    is CallState.Reconnecting -> Pair(callState.contactName, "Reconnecting (${callState.attempt}/${callState.maxAttempts})...")
                    is CallState.Ended -> Pair("Call Ended", callState.reason)
                    is CallState.Failed -> Pair("Call Failed", callState.error)
                    CallState.Idle -> Pair("", "")
                }

                Text(
                    text = contactName,
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (callState is CallState.Connected && callState.diagnostics.rttMs > 0L) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "RTT: ${callState.diagnostics.rttMs}ms | Loss: ${callState.diagnostics.packetsLost}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Controls
            when (callState) {
                is CallState.IncomingRinging -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FloatingActionButton(
                            onClick = onDecline,
                            containerColor = Color(0xFFE53935),
                            contentColor = Color.White,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "Decline", modifier = Modifier.size(32.dp))
                        }

                        FloatingActionButton(
                            onClick = {
                                val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasMicPermission) {
                                    onAccept()
                                } else {
                                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            containerColor = Color(0xFF43A047),
                            contentColor = Color.White,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Accept", modifier = Modifier.size(32.dp))
                        }
                    }
                }
                is CallState.Connected -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 32.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(
                                onClick = onToggleMute,
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(
                                        if (callState.isMuted) MaterialTheme.colorScheme.errorContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (callState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Mute",
                                    tint = if (callState.isMuted) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = onToggleSpeaker,
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(
                                        if (callState.isSpeakerOn) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Speaker",
                                    tint = if (callState.isSpeakerOn) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        FloatingActionButton(
                            onClick = onHangup,
                            containerColor = Color(0xFFE53935),
                            contentColor = Color.White,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "Hang up", modifier = Modifier.size(32.dp))
                        }
                    }
                }
                is CallState.OutgoingRinging, is CallState.Connecting, is CallState.Reconnecting -> {
                    FloatingActionButton(
                        onClick = onHangup,
                        containerColor = Color(0xFFE53935),
                        contentColor = Color.White,
                        modifier = Modifier
                            .padding(bottom = 32.dp)
                            .size(72.dp)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Cancel Call", modifier = Modifier.size(32.dp))
                    }
                }
                is CallState.Ended, is CallState.Failed -> {
                    FloatingActionButton(
                        onClick = onHangup,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(bottom = 32.dp)
                            .size(56.dp)
                    ) {
                        Text("Close")
                    }
                }
                CallState.Idle -> {}
            }
        }
    }
}

@Composable
private fun formatDuration(startEpochMs: Long): String {
    var elapsedSeconds by remember { mutableStateOf((System.currentTimeMillis() - startEpochMs) / 1000L) }

    LaunchedEffect(startEpochMs) {
        while (true) {
            delay(1000)
            elapsedSeconds = (System.currentTimeMillis() - startEpochMs) / 1000L
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
