package org.ciphrchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.ciphrchat.app.ui.components.CiphrPrimaryButton
import org.ciphrchat.app.ui.theme.CiphrBackground
import org.ciphrchat.app.ui.theme.CiphrControlShape
import org.ciphrchat.app.ui.theme.CiphrText
import org.ciphrchat.app.ui.theme.CiphrTextSecondary

@Composable
fun CreateIdentityScreen(
    onIdentityCreated: (displayName: String) -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

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
            text = "Create your identity",
            style = MaterialTheme.typography.headlineLarge,
            color = CiphrText
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Choose a display name. No phone number or email required.",
            style = MaterialTheme.typography.bodyMedium,
            color = CiphrTextSecondary
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = {
                displayName = it
                error = null
            },
            label = { Text("Display name") },
            placeholder = { Text("e.g. Ahsan") },
            singleLine = true,
            shape = CiphrControlShape,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))

        CiphrPrimaryButton(
            text = "Create identity",
            onClick = {
                val trimmed = displayName.trim()
                when {
                    trimmed.isEmpty() -> error = "Display name is required"
                    trimmed.length > 40 -> error = "Display name must be 40 characters or less"
                    else -> onIdentityCreated(trimmed)
                }
            },
            enabled = displayName.isNotBlank()
        )

        Spacer(Modifier.height(32.dp))
    }
}
