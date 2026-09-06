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
    fun cancellationBeforeWorkerStartsKeepsConcurrencyBoundUntilExit() {
        val gate = DesktopTransferSlot()
        val first = assertNotNull(gate.tryAcquire())
        assertNull(gate.tryAcquire(), "Another request must not spawn a parallel upload")
        gate.cancel()
        assertFailsWith<CancellationException> { first.throwIfCancelled() }
        assertNull(gate.tryAcquire(), "Cancellation must not release a worker that is still exiting")
        gate.release(first)
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
