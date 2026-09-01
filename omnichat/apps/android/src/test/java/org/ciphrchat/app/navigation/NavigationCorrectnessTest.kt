package org.ciphrchat.app.navigation

import org.ciphrchat.app.app.AppNavigationPolicy
import org.ciphrchat.app.app.AppRoute
import org.ciphrchat.app.app.NavAction
import org.ciphrchat.app.ui.components.BottomNavItem
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression and invariant tests for Phase 1 Navigation Correctness.
 */
class NavigationCorrectnessTest {

    /**
     * Simulated Back Stack Manager that follows Android Navigation Component semantics
     * according to [NavAction] rules.
     */
    private class SimulatedNavBackStack(startRoute: String) {
        private val stack = mutableListOf<String>()
        private val savedStates = mutableMapOf<String, List<String>>()

        init {
            stack.add(startRoute)
        }

        val currentRoute: String? get() = stack.lastOrNull()
        val backStackEntries: List<String> get() = stack.toList()
        val depth: Int get() = stack.size

        fun execute(action: NavAction) {
            // 1. popUpTo handling
            if (action.popUpToRoute != null) {
                val index = stack.lastIndexOf(action.popUpToRoute)
                if (index != -1) {
                    val popFromIndex = if (action.inclusive) index else index + 1
                    if (popFromIndex < stack.size) {
                        val popped = stack.subList(popFromIndex, stack.size).toList()
                        if (action.saveState && popped.isNotEmpty()) {
                            // Save state keyed by the popped top destination
                            savedStates[popped.last()] = popped
                        }
                        while (stack.size > popFromIndex) {
                            stack.removeAt(stack.size - 1)
                        }
                    }
                }
            }

            // 2. singleTop check
            if (action.launchSingleTop && stack.isNotEmpty() && stack.last() == action.route) {
                // Destination is already at top of stack; do not push duplicate
                return
            }

            // 3. restoreState check
            if (action.restoreState && savedStates.containsKey(action.route)) {
                val restored = savedStates.remove(action.route)!!
                stack.addAll(restored)
            } else {
                stack.add(action.route)
            }
        }

        fun popBackStack(): Boolean {
            if (stack.size > 1) {
                stack.removeAt(stack.size - 1)
                return true
            }
            return false
        }
    }

    @Test
    fun `requirement 1 - Chats always navigates directly to root conversation list without restoring stale conversation`() {
        val action = AppNavigationPolicy.onBottomNavTabSelected(BottomNavItem.CHATS)
        assertEquals(AppRoute.Chats.route, action.route)
        assertEquals(AppRoute.Chats.route, action.popUpToRoute)
        assertFalse("Chats popUpTo must not be inclusive", action.inclusive)
        assertTrue("Chats must launchSingleTop to prevent duplicate root", action.launchSingleTop)
        assertFalse("Chats must not restore stale nested conversation states", action.restoreState)
        assertFalse("Chats must not save state when popping to root", action.saveState)
    }

    @Test
    fun `requirement 2 and 3 - Connect and Settings preserve tab states and pop to root destination`() {
        val connectAction = AppNavigationPolicy.onBottomNavTabSelected(BottomNavItem.CONNECT)
        assertEquals(AppRoute.Connect.route, connectAction.route)
        assertEquals(AppRoute.Chats.route, connectAction.popUpToRoute)
        assertFalse(connectAction.inclusive)
        assertTrue(connectAction.launchSingleTop)
        assertTrue(connectAction.saveState)
        assertTrue(connectAction.restoreState)

        val settingsAction = AppNavigationPolicy.onBottomNavTabSelected(BottomNavItem.SETTINGS)
        assertEquals(AppRoute.Settings.route, settingsAction.route)
        assertEquals(AppRoute.Chats.route, settingsAction.popUpToRoute)
        assertFalse(settingsAction.inclusive)
        assertTrue(settingsAction.launchSingleTop)
        assertTrue(settingsAction.saveState)
        assertTrue(settingsAction.restoreState)
    }

    @Test
    fun `requirement 4 - opening a conversation then pressing Back returns to Chats`() {
        val nav = SimulatedNavBackStack(AppRoute.Chats.route)
        assertEquals(AppRoute.Chats.route, nav.currentRoute)

        // Open conversation
        nav.execute(AppNavigationPolicy.onOpenConversation("user-alice-123"))
        assertEquals("chat/user-alice-123", nav.currentRoute)
        assertEquals(2, nav.depth)

        // Press Back
        val popped = nav.popBackStack()
        assertTrue(popped)
        assertEquals(AppRoute.Chats.route, nav.currentRoute)
        assertEquals(1, nav.depth)
    }

    @Test
    fun `requirement 6 - Scanner QR success navigates to chat and Back returns directly to Chats`() {
        val nav = SimulatedNavBackStack(AppRoute.Chats.route)

        // User goes to Connect
        nav.execute(AppNavigationPolicy.onBottomNavTabSelected(BottomNavItem.CONNECT))
        assertEquals(AppRoute.Connect.route, nav.currentRoute)

        // User clicks Scan QR
        nav.execute(NavAction(AppRoute.Scanner.route))
        assertEquals(AppRoute.Scanner.route, nav.currentRoute)

        // Scanner finds QR and pairs
        nav.execute(AppNavigationPolicy.onQrScanSuccess("new-contact-456"))
        assertEquals("chat/new-contact-456", nav.currentRoute)
        assertEquals(listOf(AppRoute.Chats.route, "chat/new-contact-456"), nav.backStackEntries)

        // User presses Back from Chat
        val popped = nav.popBackStack()
        assertTrue(popped)
        assertEquals(AppRoute.Chats.route, nav.currentRoute)
        assertEquals(listOf(AppRoute.Chats.route), nav.backStackEntries)
    }

    @Test
    fun `requirement 6 - Scanner cancellation returns to Connect`() {
        val nav = SimulatedNavBackStack(AppRoute.Chats.route)

        // User goes to Connect
        nav.execute(AppNavigationPolicy.onBottomNavTabSelected(BottomNavItem.CONNECT))
        assertEquals(AppRoute.Connect.route, nav.currentRoute)

        // User clicks Scan QR
        nav.execute(NavAction(AppRoute.Scanner.route))
        assertEquals(AppRoute.Scanner.route, nav.currentRoute)

        // User cancels Scanner
        val popped = nav.popBackStack()
        assertTrue(popped)
        assertEquals(AppRoute.Connect.route, nav.currentRoute)
    }

    @Test
    fun `requirement 7 - bottom bar visibility correctly matches main routes`() {
        assertTrue(AppNavigationPolicy.isBottomBarVisible(AppRoute.Chats.route))
        assertTrue(AppNavigationPolicy.isBottomBarVisible(AppRoute.Connect.route))
        assertTrue(AppNavigationPolicy.isBottomBarVisible(AppRoute.Settings.route))

        assertFalse(AppNavigationPolicy.isBottomBarVisible(AppRoute.Welcome.route))
        assertFalse(AppNavigationPolicy.isBottomBarVisible(AppRoute.CreateIdentity.route))
        assertFalse(AppNavigationPolicy.isBottomBarVisible(AppRoute.EnableConnections.route))
        assertFalse(AppNavigationPolicy.isBottomBarVisible(AppRoute.IdentityReady.route))
        assertFalse(AppNavigationPolicy.isBottomBarVisible(AppRoute.Scanner.route))
        assertFalse(AppNavigationPolicy.isBottomBarVisible("chat/alice"))
        assertFalse(AppNavigationPolicy.isBottomBarVisible(null))
    }

    @Test
    fun `requirement 8 and 9 - repeated switching between tabs never duplicates root or accumulates unbounded backstack`() {
        val nav = SimulatedNavBackStack(AppRoute.Chats.route)

        // Switch back and forth between Connect and Settings 50 times
        for (i in 0 until 50) {
            nav.execute(AppNavigationPolicy.onBottomNavTabSelected(BottomNavItem.CONNECT))
            assertEquals(AppRoute.Connect.route, nav.currentRoute)
            assertTrue("Stack depth must not exceed 2 during tab switching", nav.depth <= 2)

            nav.execute(AppNavigationPolicy.onBottomNavTabSelected(BottomNavItem.SETTINGS))
            assertEquals(AppRoute.Settings.route, nav.currentRoute)
            assertTrue("Stack depth must not exceed 2 during tab switching", nav.depth <= 2)
        }

        // Tap Chats
        nav.execute(AppNavigationPolicy.onBottomNavTabSelected(BottomNavItem.CHATS))
        assertEquals(AppRoute.Chats.route, nav.currentRoute)
        assertEquals(listOf(AppRoute.Chats.route), nav.backStackEntries)
    }

    @Test
    fun `requirement 11 - cycle 1 repeated navigation Chats to Settings to Chats`() {
        val nav = SimulatedNavBackStack(AppRoute.Chats.route)

        for (i in 0 until 100) {
            nav.execute(AppNavigationPolicy.onBottomNavTabSelected(BottomNavItem.SETTINGS))
            assertEquals(AppRoute.Settings.route, nav.currentRoute)

            nav.execute(AppNavigationPolicy.onBottomNavTabSelected(BottomNavItem.CHATS))
            assertEquals(AppRoute.Chats.route, nav.currentRoute)
            assertEquals(listOf(AppRoute.Chats.route), nav.backStackEntries)
        }
    }

    @Test
    fun `requirement 11 - cycle 2 repeated navigation Chats to Connect to Chats`() {
        val nav = SimulatedNavBackStack(AppRoute.Chats.route)

        for (i in 0 until 100) {
            nav.execute(AppNavigationPolicy.onBottomNavTabSelected(BottomNavItem.CONNECT))
            assertEquals(AppRoute.Connect.route, nav.currentRoute)

            nav.execute(AppNavigationPolicy.onBottomNavTabSelected(BottomNavItem.CHATS))
            assertEquals(AppRoute.Chats.route, nav.currentRoute)
            assertEquals(listOf(AppRoute.Chats.route), nav.backStackEntries)
        }
    }

    @Test
    fun `requirement 11 - cycle 3 repeated navigation Chats to conversation to Back`() {
        val nav = SimulatedNavBackStack(AppRoute.Chats.route)

        for (i in 0 until 50) {
            nav.execute(AppNavigationPolicy.onOpenConversation("peer-$i"))
            assertEquals("chat/peer-$i", nav.currentRoute)

            val popped = nav.popBackStack()
            assertTrue(popped)
            assertEquals(AppRoute.Chats.route, nav.currentRoute)
            assertEquals(listOf(AppRoute.Chats.route), nav.backStackEntries)
        }
    }

    @Test
    fun `requirement 11 - cycle 4 repeated navigation Chats to Settings to Connect to Chats`() {
        val nav = SimulatedNavBackStack(AppRoute.Chats.route)

        for (i in 0 until 50) {
            nav.execute(AppNavigationPolicy.onBottomNavTabSelected(BottomNavItem.SETTINGS))
            assertEquals(AppRoute.Settings.route, nav.currentRoute)

            nav.execute(AppNavigationPolicy.onBottomNavTabSelected(BottomNavItem.CONNECT))
            assertEquals(AppRoute.Connect.route, nav.currentRoute)

            nav.execute(AppNavigationPolicy.onBottomNavTabSelected(BottomNavItem.CHATS))
            assertEquals(AppRoute.Chats.route, nav.currentRoute)
            assertEquals(listOf(AppRoute.Chats.route), nav.backStackEntries)
        }
    }

    @Test
    fun `requirement 13 - onboarding flow cleanly pops Welcome inclusive on completion`() {
        val nav = SimulatedNavBackStack(AppRoute.Welcome.route)
        assertEquals(AppRoute.Welcome.route, nav.currentRoute)

        // Welcome -> CreateIdentity
        nav.execute(NavAction(AppRoute.CreateIdentity.route))
        // CreateIdentity -> EnableConnections (pops CreateIdentity inclusive)
        nav.execute(NavAction(AppRoute.EnableConnections.route, popUpToRoute = AppRoute.CreateIdentity.route, inclusive = true))
        // EnableConnections -> IdentityReady
        nav.execute(NavAction(AppRoute.IdentityReady.route))

        assertEquals(listOf(AppRoute.Welcome.route, AppRoute.EnableConnections.route, AppRoute.IdentityReady.route), nav.backStackEntries)

        // Finish onboarding
        nav.execute(AppNavigationPolicy.onFinishOnboarding(isOnboarded = false))
        assertEquals(AppRoute.Chats.route, nav.currentRoute)
        assertEquals(listOf(AppRoute.Chats.route), nav.backStackEntries)
    }

    @Test
    fun `Chats Add Contact button uses consistent Connect tab navigation semantics`() {
        val addContactAction = AppNavigationPolicy.onAddContact()
        val connectTabAction = AppNavigationPolicy.onBottomNavTabSelected(BottomNavItem.CONNECT)

        assertEquals(connectTabAction.route, addContactAction.route)
        assertEquals(connectTabAction.popUpToRoute, addContactAction.popUpToRoute)
        assertEquals(connectTabAction.inclusive, addContactAction.inclusive)
        assertEquals(connectTabAction.launchSingleTop, addContactAction.launchSingleTop)
        assertEquals(connectTabAction.saveState, addContactAction.saveState)
        assertEquals(connectTabAction.restoreState, addContactAction.restoreState)
    }
}
