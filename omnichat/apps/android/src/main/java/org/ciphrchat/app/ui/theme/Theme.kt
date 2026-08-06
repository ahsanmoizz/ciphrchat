package org.ciphrchat.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CiphrColorScheme = lightColorScheme(
    primary = CiphrPrimary,
    onPrimary = CiphrText,
    primaryContainer = CiphrPrimarySoft,
    onPrimaryContainer = CiphrText,
    background = CiphrBackground,
    onBackground = CiphrText,
    surface = CiphrSurface,
    onSurface = CiphrText,
    surfaceVariant = CiphrSurfaceMuted,
    onSurfaceVariant = CiphrTextSecondary,
    outline = CiphrBorder,
    error = CiphrDanger
)

@Composable
fun CiphrChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CiphrColorScheme,
        typography = CiphrTypography,
        shapes = CiphrShapes,
        content = content
    )
}
