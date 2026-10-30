package com.example.wifidrop.pc

import kotlin.test.*

class DesktopChannelRequestsTest {
    private fun key(id: String, peer: String = "192.0.2.1") = DesktopChannelOfferKey(peer, id)

    @Test
    fun overlappingOffersAreRequestedOnceEachInArrivalOrder() {
        val queue = DesktopChannelRequestQueue<String>()
        assertEquals(DesktopChannelEnqueueResult.QUEUED, queue.enqueue(key("a"), "first", true))
        val first = assertNotNull(queue.next())
        assertEquals(DesktopChannelEnqueueResult.QUEUED, queue.enqueue(key("b"), "second", true))
        assertEquals(DesktopChannelEnqueueResult.DUPLICATE, queue.enqueue(key("b"), "duplicate", true))
        assertNull(queue.next())
        assertTrue(queue.complete(first))
        val second = assertNotNull(queue.next())
        assertEquals("second", second.value)
        assertTrue(queue.complete(second))
        assertEquals(DesktopChannelEnqueueResult.DUPLICATE, queue.enqueue(key("a"), "duplicate", true))
        assertEquals(DesktopChannelEnqueueResult.DUPLICATE, queue.enqueue(key("b"), "duplicate", true))
        assertNull(queue.next())
        assertFalse(queue.hasActivity)
        assertEquals(DesktopChannelRequestPhase.REQUESTED, queue.snapshot()[key("b")]?.phase)
        assertContains(queue.snapshot()[key("b")]!!.detail, "La recepción se confirma en Recibir")
    }

    @Test
    fun offerIdsFromDifferentPeersAreIndependent() {
        val queue = DesktopChannelRequestQueue<String>()
        queue.enqueue(key("same"), "first peer", true)
        assertEquals(DesktopChannelEnqueueResult.QUEUED, queue.enqueue(key("same", "192.0.2.2"), "second peer", true))
        val first = assertNotNull(queue.next())
        queue.complete(first)
        assertEquals("second peer", queue.next()?.value)
    }

    @Test
    fun busyRemoteIsAnExplicitManualRetryWithoutAutomaticLoop() {
        val queue = DesktopChannelRequestQueue<String>()
        queue.enqueue(key("a"), "offer", true)
        val work = assertNotNull(queue.next())
        assertTrue(queue.complete(work, "El equipo está enviando otro archivo."))
        assertEquals(DesktopChannelRequestPhase.MANUAL, queue.snapshot()[key("a")]?.phase)
        assertContains(queue.snapshot()[key("a")]!!.detail, "Usa Descargar")
        assertEquals(DesktopChannelEnqueueResult.DUPLICATE, queue.enqueue(key("a"), "offer", true))
        assertEquals(DesktopChannelEnqueueResult.QUEUED, queue.enqueue(key("a"), "offer", false))
        assertNotNull(queue.next())
    }

    @Test
    fun fullQueueKeepsOverflowOfferAvailableForManualAction() {
        val queue = DesktopChannelRequestQueue<String>(capacity = 1, historyCapacity = 3)
        queue.enqueue(key("a"), "active", true)
        val active = assertNotNull(queue.next())
        queue.enqueue(key("b"), "pending", true)
        assertEquals(DesktopChannelEnqueueResult.FULL, queue.enqueue(key("c"), "overflow", true))
        assertEquals(1, queue.pendingCount)
        assertEquals(DesktopChannelRequestPhase.MANUAL, queue.snapshot()[key("c")]?.phase)
        queue.complete(active)
        queue.complete(assertNotNull(queue.next()))
        assertEquals(DesktopChannelEnqueueResult.QUEUED, queue.enqueue(key("c"), "retry", false))
    }

    @Test
    fun leavingAndRejoiningCannotRestartOldOffersOrPublishLateSuccess() {
        val queue = DesktopChannelRequestQueue<String>()
        queue.enqueue(key("a"), "old active", true)
        val old = assertNotNull(queue.next())
        queue.enqueue(key("b"), "old pending", true)
        queue.revoke("Saliste del canal. Usa Descargar al volver.")
        assertTrue(old.cancellation.isCancelled)
        assertEquals(0, queue.pendingCount)
        assertTrue(queue.hasActivity, "The cancelled worker still counts for the exit confirmation")
        queue.enqueue(key("c"), "new membership", true)
        assertNull(queue.next(), "The cancelled worker must exit before a replacement starts")
        assertFalse(queue.complete(old))
        val current = assertNotNull(queue.next())
        assertEquals("new membership", current.value)
        assertFalse(queue.complete(old), "A second late completion must not release the new request")
        assertNull(queue.next())
        assertTrue(queue.complete(current))
        assertEquals(DesktopChannelRequestPhase.MANUAL, queue.snapshot()[key("b")]?.phase)
    }

    @Test
    fun disablingAutomaticDownloadsRemovesOnlyAutomaticRequests() {
        val queue = DesktopChannelRequestQueue<String>()
        queue.enqueue(key("a"), "automatic active", true)
        val automatic = assertNotNull(queue.next())
        queue.enqueue(key("b"), "automatic pending", true)
        queue.enqueue(key("c"), "manual pending", false)
        queue.cancelAutomatic()
        assertTrue(automatic.cancellation.isCancelled)
        assertFalse(queue.complete(automatic))
        assertEquals("manual pending", queue.next()?.value)
        assertEquals(DesktopChannelEnqueueResult.DUPLICATE, queue.enqueue(key("b"), "not restored after enabling", true))
        assertEquals(DesktopChannelRequestPhase.MANUAL, queue.snapshot()[key("b")]?.phase)
    }

    @Test
    fun disablingAutomaticDownloadsPreservesAnActiveManualRequest() {
        val queue = DesktopChannelRequestQueue<String>()
        queue.enqueue(key("a"), "manual", false)
        val manual = assertNotNull(queue.next())
        queue.enqueue(key("b"), "automatic", true)
        queue.cancelAutomatic()
        assertFalse(manual.cancellation.isCancelled)
        assertTrue(queue.complete(manual))
        assertNull(queue.next())
    }

    @Test
    fun stoppedExpiredOrReplacedReceiverRevokesQueueAndFeedback() {
        listOf("Recepción detenida.", "Sesión expirada.", "Código o PIN cambiado.").forEach { reason ->
            val queue = DesktopChannelRequestQueue<String>()
            queue.enqueue(key("a"), "active", true)
            val active = assertNotNull(queue.next())
            queue.enqueue(key("b"), "pending", true)
            queue.revoke(reason)
            assertFalse(queue.complete(active))
            assertNull(queue.next())
            assertEquals(reason, queue.snapshot()[key("b")]?.detail)
        }
    }

    @Test
    fun statusHistoryIsBoundedEvenWhenManyOffersNeedManualAction() {
        val queue = DesktopChannelRequestQueue<String>(capacity = 1, historyCapacity = 3)
        queue.enqueue(key("active"), "active", true)
        val work = assertNotNull(queue.next())
        queue.enqueue(key("pending"), "pending", true)
        repeat(30) { queue.enqueue(key("overflow-$it"), "overflow", true) }
        assertEquals(3, queue.snapshot().size)
        assertEquals(DesktopChannelRequestPhase.REQUESTING, queue.snapshot()[work.key]?.phase)
        assertEquals(DesktopChannelRequestPhase.QUEUED, queue.snapshot()[key("pending")]?.phase)
    }

    @Test
    fun onlyChannelAttachmentsRequireAnActiveUnexpiredReceiver() {
        val reception = DesktopMessageReception(1, true, 2000L, true)
        assertNull(desktopChannelPublicationIssue(DesktopChatScope.GLOBAL_LAN, true, reception.isActiveFor(1, 1999L)))
        assertNotNull(desktopChannelPublicationIssue(DesktopChatScope.GLOBAL_LAN, true, reception.isActiveFor(1, 2000L)))
        assertNotNull(desktopChannelPublicationIssue(DesktopChatScope.GLOBAL_LAN, true, false))
        assertNull(desktopChannelPublicationIssue(DesktopChatScope.GLOBAL_LAN, false, false))
        assertNull(desktopChannelPublicationIssue(DesktopChatScope.DIRECT, true, false))
    }

    @Test
    fun channelUploadAloneRequiresConfirmationUntilWorkerExits() {
        val uploads = DesktopTransferSlot()
        val requests = DesktopChannelRequestQueue<String>()
        assertFalse(desktopExitNeedsConfirmation(false, uploads, requests))
        val upload = assertNotNull(uploads.tryAcquire())
        assertTrue(desktopExitNeedsConfirmation(false, uploads, requests))
        // Choosing to continue only dismisses the dialog; the upload remains usable.
        upload.throwIfCancelled()
        uploads.cancel()
        assertTrue(desktopExitNeedsConfirmation(false, uploads, requests))
        uploads.release(upload)
        assertFalse(desktopExitNeedsConfirmation(false, uploads, requests))
    }

    @Test
    fun pendingOrCancellingChannelRequestAloneRequiresConfirmation() {
        val uploads = DesktopTransferSlot()
        val requests = DesktopChannelRequestQueue<String>()
        requests.enqueue(key("a"), "pending", true)
        assertTrue(desktopExitNeedsConfirmation(false, uploads, requests))
        val active = assertNotNull(requests.next())
        requests.revoke("Exit requested")
        assertTrue(desktopExitNeedsConfirmation(false, uploads, requests))
        requests.complete(active)
        assertFalse(desktopExitNeedsConfirmation(false, uploads, requests))
        assertTrue(desktopExitNeedsConfirmation(true, uploads, requests))
    }
}
