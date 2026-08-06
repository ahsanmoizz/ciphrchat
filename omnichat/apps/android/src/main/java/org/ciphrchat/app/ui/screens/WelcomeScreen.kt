package org.ciphrchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ciphrchat.app.ui.components.CiphrPrimaryButton
import org.ciphrchat.app.ui.components.CiphrTextButton
import org.ciphrchat.app.ui.theme.CiphrBackground
import org.ciphrchat.app.ui.theme.CiphrText
import org.ciphrchat.app.ui.theme.CiphrTextSecondary

@Composable
fun WelcomeScreen(
    onCreateIdentity: () -> Unit,
    onRestore: () -> Unit
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
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFFC7B5FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("C", fontSize = 36.sp, color = CiphrText)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "CiphrChat",
                style = MaterialTheme.typography.displayLarge,
                color = CiphrText
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "One identity. Every connection.",
                style = MaterialTheme.typography.bodyLarge,
                color = CiphrTextSecondary
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Communicate through internet, Wi-Fi, Bluetooth\nand every supported connection around you.",
                style = MaterialTheme.typography.bodyMedium,
                color = CiphrTextSecondary,
                textAlign = TextAlign.Center
            )

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
