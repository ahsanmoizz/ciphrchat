package org.ciphrchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.ciphrchat.app.R
import org.ciphrchat.app.ui.components.CiphrPrimaryButton
import org.ciphrchat.app.ui.components.CiphrTextButton
import org.ciphrchat.app.ui.theme.CiphrBackground
import org.ciphrchat.app.ui.theme.CiphrText
import org.ciphrchat.app.ui.theme.CiphrTextSecondary

@Composable
fun WelcomeScreen(
    onCreateIdentity: () -> Unit,
    onRestore: () -> Unit,
    restoreMessage: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CiphrBackground)
    ) {
        // Ambient background glow
        Box(
            Modifier
                .size(360.dp)
                .offset(x = (-130).dp, y = 90.dp)
                .background(Color(0xFFE8EFFF), CircleShape)
                .blur(90.dp)
        )
        Box(
            Modifier
                .size(400.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 160.dp)
                .background(Color(0xFFEFE8FF), CircleShape)
                .blur(100.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(1f))

            // Logo placeholder — will use actual logo asset
            Image(
                painter = painterResource(R.drawable.ciphrchatlogo),
                contentDescription = "CiphrChat logo",
                modifier = Modifier.size(width = 240.dp, height = 150.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "One identity. Every connection.",
                style = MaterialTheme.typography.bodyLarge,
                color = CiphrTextSecondary
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Secure private messaging over the Internet, Wi-Fi, Bluetooth, and nearby connections.",
                style = MaterialTheme.typography.bodyMedium,
                color = CiphrTextSecondary,
                textAlign = TextAlign.Center
            )

            restoreMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = CiphrTextSecondary, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.weight(1f))

            CiphrPrimaryButton(
                text = "Create my identity",
                onClick = onCreateIdentity
            )

            Spacer(Modifier.height(12.dp))

            CiphrTextButton(
                text = "Restore an identity",
                onClick = onRestore
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Open source • Local first • No phone number",
                style = MaterialTheme.typography.labelMedium,
                color = CiphrTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
