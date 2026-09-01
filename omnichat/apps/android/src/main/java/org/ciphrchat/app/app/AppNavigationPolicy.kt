package org.ciphrchat.app.app

import org.ciphrchat.app.ui.components.BottomNavItem

/**
 * Immutable navigation action descriptor representing target route and backstack manipulation rules.
 */
data class NavAction(
    val route: String,
    val popUpToRoute: String? = null,
    val inclusive: Boolean = false,
    val launchSingleTop: Boolean = false,
    val saveState: Boolean = false,
    val restoreState: Boolean = false
)

/**
 * Deterministic navigation policy for CiphrChat.
 *
 * Enforces production invariants:
 * 1. Chats tab always resolves to the root conversation list without restoring deep/stale conversations.
 * 2. Connect and Settings tabs pop to the root destination (Chats) with state saving and restoration.
 * 3. Opening a conversation and pressing Back returns to Chats.
 * 4. Scanner success opens the conversation on top of Chats (popping scanner and connect), so Back returns to Chats.
 * 5. Scanner cancellation returns to Connect.
 * 6. Bottom navigation tabs never accumulate unbounded back stack entries.
 * 7. Onboarding completion cleanly removes all onboarding screens from the back stack.
 */
object AppNavigationPolicy {
    val ROOT_DESTINATION: String = AppRoute.Chats.route

    val MAIN_ROUTES: Set<String> = setOf(
        AppRoute.Chats.route,
        AppRoute.Connect.route,
        AppRoute.Settings.route
    )

    fun isBottomBarVisible(currentRoute: String?): Boolean {
        return currentRoute in MAIN_ROUTES
    }

    /**
     * Determines navigation parameters when a bottom navigation tab is tapped.
     */
    fun onBottomNavTabSelected(item: BottomNavItem): NavAction {
        return when (item) {
            BottomNavItem.CHATS -> NavAction(
                route = AppRoute.Chats.route,
                popUpToRoute = ROOT_DESTINATION,
                inclusive = false,
                launchSingleTop = true,
                saveState = false,
                restoreState = false
            )
            BottomNavItem.CONNECT -> NavAction(
                route = AppRoute.Connect.route,
                popUpToRoute = ROOT_DESTINATION,
                inclusive = false,
                launchSingleTop = true,
                saveState = true,
                restoreState = true
            )
            BottomNavItem.SETTINGS -> NavAction(
                route = AppRoute.Settings.route,
                popUpToRoute = ROOT_DESTINATION,
                inclusive = false,
                launchSingleTop = true,
                saveState = true,
                restoreState = true
            )
        }
    }

    /**
     * Navigating to Add Contact from ChatsScreen.
     * Uses identical tab switching semantics as selecting the Connect tab.
     */
    fun onAddContact(): NavAction {
        return NavAction(
            route = AppRoute.Connect.route,
            popUpToRoute = ROOT_DESTINATION,
            inclusive = false,
            launchSingleTop = true,
            saveState = true,
            restoreState = true
        )
    }

    /**
     * Navigating to a specific conversation from ChatsScreen or contact click.
     */
    fun onOpenConversation(conversationId: String): NavAction {
        return NavAction(
            route = AppRoute.Chat.create(conversationId),
            popUpToRoute = null,
            inclusive = false,
            launchSingleTop = true,
            saveState = false,
            restoreState = false
        )
    }

    /**
     * Navigating to a conversation upon successful QR scan.
     * Pops all intermediate scanner and connect screens back to Chats,
     * ensuring that pressing Back in the opened conversation returns directly to Chats.
     */
    fun onQrScanSuccess(contactId: String): NavAction {
        return NavAction(
            route = AppRoute.Chat.create(contactId),
            popUpToRoute = ROOT_DESTINATION,
            inclusive = false,
            launchSingleTop = true,
            saveState = false,
            restoreState = false
        )
    }

    /**
     * Onboarding complete or identity restored navigation.
     * When already onboarded, returns to root Chats.
     * When completing initial onboarding, pops the entire Welcome flow inclusive.
     */
    fun onFinishOnboarding(isOnboarded: Boolean): NavAction {
        return if (isOnboarded) {
            NavAction(
                route = AppRoute.Chats.route,
                popUpToRoute = ROOT_DESTINATION,
                inclusive = false,
                launchSingleTop = true,
                saveState = false,
                restoreState = false
            )
        } else {
            NavAction(
                route = AppRoute.Chats.route,
                popUpToRoute = AppRoute.Welcome.route,
                inclusive = true,
                launchSingleTop = true,
                saveState = false,
                restoreState = false
            )
        }
    }
}
