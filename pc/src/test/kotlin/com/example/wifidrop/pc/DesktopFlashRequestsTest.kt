package com.example.wifidrop.pc

import com.example.wifidrop.protocol.flash.FlashApproval
import com.example.wifidrop.protocol.flash.FlashOfferedFile
import com.example.wifidrop.protocol.flash.FlashPeer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopFlashRequestsTest {
    @Test
    fun aVisibleApprovalCannotAuthorizeAReplacementWithTheSameId() {
        val requests = DesktopFlashRequests { 100L }
        val token = requests.begin()
        val shown = approval()
        requests.update(token, listOf(shown))
        val replacement = shown.copy(peer = shown.peer.copy(id = "another-key"), fileName = "other.bin")
        requests.update(token, listOf(replacement))
        assertFalse(requests.decide(token, shown))
        assertTrue(requests.decide(token, replacement))
    }

    @Test
    fun changingTheVerificationCodeOrFileSizeRequiresANewDecision() {
        val requests = DesktopFlashRequests { 100L }
        val token = requests.begin()
        val shown = approval()
        requests.update(token, listOf(shown.copy(verificationCode = "9999 9999 9999 9999", totalBytes = 900L)))
        assertFalse(requests.decide(token, shown))
    }

    @Test
    fun changingAnotherFileInTheBatchRequiresANewDecisionEvenWithTheSameTotal() {
        val requests = DesktopFlashRequests { 100L }
        val token = requests.begin()
        val shown = approval().copy(totalBytes = 256L, files = listOf(
            FlashOfferedFile("fixture.bin", 128L), FlashOfferedFile("second.bin", 128L)
        ))
        requests.update(token, listOf(shown))
        val replacement = shown.copy(files = listOf(
            FlashOfferedFile("fixture.bin", 128L), FlashOfferedFile("different.bin", 128L)
        ))
        requests.update(token, listOf(replacement))
        assertFalse(requests.decide(token, shown))
        assertTrue(requests.decide(token, replacement))
    }

    @Test
    fun changingBatchOrderRequiresANewDecision() {
        val requests = DesktopFlashRequests { 100L }
        val token = requests.begin()
        val first = FlashOfferedFile("fixture.bin", 128L)
        val second = FlashOfferedFile("second.bin", 128L)
        val third = FlashOfferedFile("third.bin", 128L)
        val shown = approval().copy(totalBytes = 384L, files = listOf(first, second, third))
        requests.update(token, listOf(shown))
        val replacement = shown.copy(files = listOf(first, third, second))
        requests.update(token, listOf(replacement))
        assertFalse(requests.decide(token, shown))
        assertTrue(requests.decide(token, replacement))
    }

    @Test
    fun expiredDialogCannotApproveAndIsRemovedOnRefresh() {
        var clock = 100L
        val requests = DesktopFlashRequests { clock }
        val token = requests.begin()
        val shown = approval()
        requests.update(token, listOf(shown))
        clock = shown.expiresAtMs
        assertFalse(requests.decide(token, shown))
        assertTrue(requests.update(token, listOf(shown)).isEmpty())
    }

    @Test
    fun deactivationAndRestartRejectQueuedCallbacksAndOldApprovals() {
        val requests = DesktopFlashRequests { 100L }
        val old = requests.begin()
        val shown = approval()
        requests.update(old, listOf(shown))
        requests.invalidate()
        assertFalse(requests.isCurrent(old))
        val current = requests.begin()
        requests.update(current, listOf(shown))
        assertTrue(requests.update(old, listOf(shown)).isEmpty())
        assertFalse(requests.decide(old, shown))
        assertTrue(requests.decide(current, shown))
    }

    @Test
    fun doubleClickAndAnAlreadyQueuedSnapshotCannotReopenTheDecision() {
        val requests = DesktopFlashRequests { 100L }
        val token = requests.begin()
        val shown = approval()
        requests.update(token, listOf(shown))
        assertTrue(requests.decide(token, shown))
        assertFalse(requests.decide(token, shown))
        assertTrue(requests.update(token, listOf(shown)).isEmpty())
        val next = shown.copy(requestId = "next", operationId = "next-operation")
        assertEquals(listOf(next), requests.update(token, listOf(next)))
        assertTrue(requests.decide(token, next))
    }

    private fun approval() = FlashApproval(
        requestId = "request", operationId = "operation",
        peer = FlashPeer("peer-key", "Equipo QA", "127.0.0.1", 8989, 10_000),
        fileName = "fixture.bin", totalBytes = 128L, outgoing = false,
        verificationCode = "0123 4567 89AB CDEF", expiresAtMs = 1_000
    )
}
