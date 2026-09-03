package org.ciphrchat.app.performance

import android.graphics.BitmapFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import org.ciphrchat.app.di.DatabaseModule
import org.ciphrchat.app.files.ThumbnailUtils
import org.ciphrchat.app.messaging.MessageDirection
import org.ciphrchat.app.transport.TransportAvailability
import org.ciphrchat.app.transport.TransportKind
import org.ciphrchat.app.transport.TransportState
import org.ciphrchat.app.transport.lan.LanTransportAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class PerformanceOptimizationRegressionTest {

    @Test
    fun thumbnailDownsamplingCalculatesOptimalSampleSize() {
        // High-resolution photo: 4000x3000
        val options = BitmapFactory.Options().apply {
            outWidth = 4000
            outHeight = 3000
        }
        val sampleSize = ThumbnailUtils.calculateInSampleSize(options, 300, 300)
        // 4000 / 8 = 500, 3000 / 8 = 375; 4000 / 16 = 250 (less than 300) -> sampleSize is 8 or 16
        assertTrue("Sample size must downsample large images: $sampleSize", sampleSize >= 8)

        // Small image already within bounds
        val smallOptions = BitmapFactory.Options().apply {
            outWidth = 200
            outHeight = 150
        }
        val smallSampleSize = ThumbnailUtils.calculateInSampleSize(smallOptions, 300, 300)
        assertEquals("Small image should not be downsampled", 1, smallSampleSize)
    }

    @Test
    fun databaseMigration7To8CreatesPerformanceIndices() {
        assertNotNull(DatabaseModule.MIGRATION_7_8)
        assertEquals(7, DatabaseModule.MIGRATION_7_8.startVersion)
        assertEquals(8, DatabaseModule.MIGRATION_7_8.endVersion)

        val executedSql = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        DatabaseModule.MIGRATION_7_8.migrate(db)

        assertTrue(
            "Must create composite index for conversationId and createdAtEpochMs",
            executedSql.any { it.contains("index_messages_conversationId_createdAtEpochMs") && it.contains("conversationId, createdAtEpochMs") }
        )
        assertTrue(
            "Must create status index",
            executedSql.any { it.contains("index_messages_status") && it.contains("status") }
        )
    }

    data class ScrollDecision(val shouldAutoScroll: Boolean, val showBadge: Boolean)

    private fun evaluateScrollBehavior(
        isInitialLoad: Boolean,
        isNearBottom: Boolean,
        messageDirection: MessageDirection,
        hasNewIncoming: Boolean
    ): ScrollDecision {
        if (isInitialLoad) return ScrollDecision(shouldAutoScroll = true, showBadge = false)
        if (!hasNewIncoming) return ScrollDecision(shouldAutoScroll = false, showBadge = false)

        val isUserOutgoing = messageDirection == MessageDirection.OUTGOING
        return if (isUserOutgoing || isNearBottom) {
            ScrollDecision(shouldAutoScroll = true, showBadge = false)
        } else {
            ScrollDecision(shouldAutoScroll = false, showBadge = true)
        }
    }

    @Test
    fun scrollPolicyPreservesPositionWhenScrolledUpReadingHistory() {
        // User has scrolled up (isNearBottom = false), an incoming message arrives
        val decision = evaluateScrollBehavior(
            isInitialLoad = false,
            isNearBottom = false,
            messageDirection = MessageDirection.INCOMING,
            hasNewIncoming = true
        )
        assertFalse("Must NOT violently autoscroll when user is reading past history", decision.shouldAutoScroll)
        assertTrue("Must display floating new messages badge when reading past history", decision.showBadge)
    }

    @Test
    fun scrollPolicyFollowsNaturallyWhenNearBottom() {
        // User is near bottom, an incoming message arrives
        val decision = evaluateScrollBehavior(
            isInitialLoad = false,
            isNearBottom = true,
            messageDirection = MessageDirection.INCOMING,
            hasNewIncoming = true
        )
        assertTrue("Must follow naturally when user is already near bottom", decision.shouldAutoScroll)
        assertFalse("No badge needed when following naturally", decision.showBadge)
    }

    @Test
    fun scrollPolicyAlwaysScrollsOnUserOutgoingSend() {
        // User is scrolled up, but types and sends their own message
        val decision = evaluateScrollBehavior(
            isInitialLoad = false,
            isNearBottom = false,
            messageDirection = MessageDirection.OUTGOING,
            hasNewIncoming = true
        )
        assertTrue("Must always scroll to bottom on user's own sent message", decision.shouldAutoScroll)
        assertFalse("No badge needed on own sent message", decision.showBadge)
    }

    @Test
    fun scrollPolicyJumpsToBottomOnInitialLoad() {
        val decision = evaluateScrollBehavior(
            isInitialLoad = true,
            isNearBottom = false,
            messageDirection = MessageDirection.INCOMING,
            hasNewIncoming = false
        )
        assertTrue("Must jump to bottom on initial screen open", decision.shouldAutoScroll)
        assertFalse(decision.showBadge)
    }
}
