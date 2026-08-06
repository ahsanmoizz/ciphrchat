package org.ciphrchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ciphrchat.app.ui.components.CiphrCard
import org.ciphrchat.app.ui.components.CiphrPrimaryButton
import org.ciphrchat.app.ui.components.CiphrSecondaryButton
import org.ciphrchat.app.ui.theme.*

@Composable
fun IdentityReadyScreen(
    displayName: String,
    fingerprint: String,
    onShowQr: () -> Unit,
    onStartMessaging: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CiphrBackground)
            .padding(horizontal = 32.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(80.dp))

        Text(
            text = "You're ready",
            style = MaterialTheme.typography.headlineLarge,
            color = CiphrText
        )

        Spacer(Modifier.height(32.dp))

        CiphrCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(CiphrPrimarySoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 28.sp,
                        color = CiphrText
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = CiphrText
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "ciphr:$fingerprint",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CiphrTextSecondary
                )

                Spacer(Modifier.height(16.dp))

                var qrBitmap by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                androidx.compose.runtime.LaunchedEffect(fingerprint) {
                    val bmp = org.ciphrchat.app.identity.QrCodeGenerator.generate("ciphr:$fingerprint", 400)
                    qrBitmap = bmp?.androidx.compose.ui.graphics.asImageBitmap()
                }

                if (qrBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = qrBitmap!!,
                        contentDescription = "Your QR Code",
                        modifier = Modifier.size(140.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .background(CiphrSurfaceMuted, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Generating QR...",
                            style = MaterialTheme.typography.labelMedium,
                            color = CiphrTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        CiphrSecondaryButton(
            text = "Show my QR",
            onClick = onShowQr
        )

        Spacer(Modifier.height(12.dp))

        CiphrPrimaryButton(
            text = "Start messaging",
            onClick = onStartMessaging
        )

        Spacer(Modifier.height(32.dp))
    }
}
