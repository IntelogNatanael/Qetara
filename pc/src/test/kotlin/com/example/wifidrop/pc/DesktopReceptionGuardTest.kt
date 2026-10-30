package com.example.wifidrop.pc

import java.util.concurrent.CancellationException
import kotlin.test.*

class DesktopReceptionGuardTest {
    private val active = DesktopMessageReception(4, true, 2000L, true)

    @Test
    fun queuedChannelRequestIsRejectedAfterLeaving() {
        active.requireDelivery(4, DesktopChatScope.GLOBAL_LAN, 1000L)
        assertFailsWith<IllegalStateException> {
            active.copy(channelJoined = false).requireDelivery(4, DesktopChatScope.GLOBAL_LAN, 1000L)
        }
        active.copy(channelJoined = false).requireDelivery(4, DesktopChatScope.DIRECT, 1000L)
    }

    @Test
    fun queuedCallbackCannotRunAgainstNewOrStoppedSession() {
        assertFailsWith<IllegalStateException> {
            active.copy(generation = 5).requireDelivery(4, DesktopChatScope.DIRECT, 1000L)
        }
        assertFailsWith<IllegalStateException> {
            active.copy(receiving = false).requireDelivery(4, DesktopChatScope.DIRECT, 1000L)
        }
        assertFailsWith<IllegalStateException> {
            active.requireDelivery(4, DesktopChatScope.DIRECT, 2000L)
        }
    }

    @Test
    fun queuedOfferCannotSurviveLeavingAndRejoiningTheSameReceiverSession() {
        val rejoined = active.copy(channelGeneration = 2)
        assertFalse(rejoined.isChannelActiveFor(4, 1, 1000L), "A released upload must not publish feedback after rejoining")
        assertTrue(rejoined.isChannelActiveFor(4, 2, 1000L))
        assertFalse(rejoined.isChannelActiveFor(4, 2, 2000L), "Expired reception must not publish available-channel feedback")
        assertFailsWith<IllegalStateException> {
            rejoined.requireDelivery(4, DesktopChatScope.GLOBAL_LAN, 1000L, expectedChannelGeneration = 1)
        }
        rejoined.requireDelivery(4, DesktopChatScope.GLOBAL_LAN, 1000L, expectedChannelGeneration = 2)
        rejoined.requireDelivery(4, DesktopChatScope.DIRECT, 1000L, expectedChannelGeneration = 1)
    }

    @Test
    fun cancellationBeforeWorkerStartsKeepsConcurrencyBoundUntilExit() {
        val gate = DesktopTransferSlot()
        val first = assertNotNull(gate.tryAcquire())
        assertTrue(gate.hasActive, "A channel upload requires exit confirmation")
        assertNull(gate.tryAcquire(), "Another request must not spawn a parallel upload")
        gate.cancel()
        assertTrue(gate.hasActive, "Cancel must not hide a worker that is still exiting")
        assertFailsWith<CancellationException> { first.throwIfCancelled() }
        assertNull(gate.tryAcquire(), "Cancellation must not release a worker that is still exiting")
        gate.release(first)
        assertFalse(gate.hasActive)
        assertNotNull(gate.tryAcquire())
        gate.cancel()
    }

    @Test
    fun lateWorkerReleaseDoesNotReleaseTheNextUpload() {
        val gate = DesktopTransferSlot()
        val first = assertNotNull(gate.tryAcquire())
        gate.release(first)
        val second = assertNotNull(gate.tryAcquire())
        gate.release(first)
        assertNull(gate.tryAcquire())
        gate.cancel()
        assertTrue(second.isCancelled)
        gate.release(second)
    }

    @Test
    fun transferFeedbackQueuedBeforeExpiryCannotClaimTheReceiverIsAvailable() {
        val first = DesktopMessageReception(7, true, 2000L, false)
        var current = first
        var availableNoticePublished = false
        val queuedFeedback = {
            if (current.isActiveFor(7, nowMs = 2001L)) availableNoticePublished = true
        }
        assertTrue(first.isActiveFor(7, nowMs = 1999L))
        current = first.copy(receiving = false, expiresAtMs = null)
        deliverDesktopMessageOnUi(queuedFeedback)
        assertFalse(availableNoticePublished)
    }

    @Test
    fun fingerprintMatchesAndroidSixByteSha256Convention() {
        assertEquals("6668 7aad f862", desktopIdentityFingerprint(ByteArray(32)))
    }
}
