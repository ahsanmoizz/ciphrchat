package org.ciphrchat.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.ciphrchat.app.ui.theme.CiphrPrimary
import org.ciphrchat.app.ui.theme.CiphrSurface
import org.ciphrchat.app.ui.theme.CiphrText
import org.ciphrchat.app.ui.theme.CiphrTextSecondary

enum class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
) {
    CHATS("Chats", Icons.Default.Chat, "chats"),
    CONNECT("Connect", Icons.Default.Link, "connect"),
    SETTINGS("Settings", Icons.Default.Settings, "settings")
}

@Composable
fun CiphrBottomBar(
    currentRoute: String?,
    onNavigate: (BottomNavItem) -> Unit
) {
    NavigationBar(
        containerColor = CiphrSurface,
        contentColor = CiphrText
    ) {
        BottomNavItem.entries.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { onNavigate(item) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CiphrText,
                    selectedTextColor = CiphrText,
                    unselectedIconColor = CiphrTextSecondary,
                    unselectedTextColor = CiphrTextSecondary,
                    indicatorColor = CiphrPrimary
                )
            )
        }
    }
}
