package org.ciphrchat.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.ciphrchat.app.ui.theme.CiphrButtonShape
import org.ciphrchat.app.ui.theme.CiphrPrimary
import org.ciphrchat.app.ui.theme.CiphrText
import org.ciphrchat.app.ui.theme.CiphrBorder
import org.ciphrchat.app.ui.theme.CiphrSurface
import org.ciphrchat.app.ui.theme.CiphrTextSecondary

@Composable
fun CiphrPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = CiphrButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = CiphrPrimary,
            contentColor = CiphrText
        )
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun CiphrSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = CiphrButtonShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CiphrSurface,
            contentColor = CiphrText
        )
    ) {
        Text(text = text, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CiphrTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(text = text, color = CiphrTextSecondary)
    }
}
