package com.example.wifidrop

import com.example.wifidrop.protocol.flash.FlashApproval
import com.example.wifidrop.protocol.flash.FlashPeer
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FlashAndroidPolicyTest {
    private val peer = FlashPeer("peer-A", "Equipo A", "192.168.1.5", 8989, 5_000)
    private val request = FlashApproval("request-A", "operation-A", peer, "foto.png", 256,
        false, "1234 5678 9ABC DEF0", 2_000)

    @Test fun stoppedSessionRejectsCallbacksAndDoesNotRestoreApprovalOnNextActivation() {
        val fence = FlashAndroidSessionFence()
        val first = fence.activate()
        assertTrue(fence.accepts(first))
        fence.close()
        assertFalse(fence.accepts(first))
        val second = fence.activate()
        assertFalse(fence.accepts(first))
        assertTrue(fence.accepts(second))
    }

    @Test fun disappearingSelectedPeerNeverFallsBackToAnotherDevice() {
        val other = peer.copy(id = "peer-B", address = "192.168.1.6")
        assertNull(resolveSelectedFlashPeer(peer, listOf(other), 1_000))
    }

    @Test fun reusedAddressWithDifferentIdentityCannotReceiveSelectedFile() {
        assertNull(resolveSelectedFlashPeer(peer, listOf(peer.copy(id = "peer-B")), 1_000))
    }

    @Test fun changedEndpointRequiresExplicitSelectionEvenForSameIdentity() {
        assertNull(resolveSelectedFlashPeer(peer, listOf(peer.copy(address = "192.168.1.7")), 1_000))
        assertNull(resolveSelectedFlashPeer(peer, listOf(peer.copy(port = 8990)), 1_000))
        assertEquals(peer, resolveSelectedFlashPeer(peer, listOf(peer), 1_000))
    }

    @Test fun expiredDiscoveryCannotEnableSend() {
        assertNull(resolveSelectedFlashPeer(peer, listOf(peer), 5_000))
    }

    @Test fun discoveryFailureDoesNotImplyATransferAndUnconfirmedDeliveryKeepsItsWarning() {
        val discovery = flashErrorCopy("discovery_failed")
        assertTrue(discovery.contains("buscar esa dirección"))
        assertFalse(discovery.contains("transferencia") || discovery.contains("archivo"))
        val unconfirmed = flashErrorCopy("unconfirmed")
        assertTrue(unconfirmed.contains("Comprueba en el otro equipo si el archivo llegó antes de volver a enviarlo"))
    }

    @Test fun approvalRequiresCurrentRequestActiveSessionAndUnexpiredDeadline() {
        assertTrue(canAnswerFlashApproval(true, "request-A", listOf(request), 1_999))
        assertFalse(canAnswerFlashApproval(true, "request-A", listOf(request), 2_000))
        assertFalse(canAnswerFlashApproval(false, "request-A", listOf(request), 1_000))
        assertFalse(canAnswerFlashApproval(true, "old-request", listOf(request), 1_000))
        assertFalse(canAnswerFlashApproval(true, "request-A", emptyList(), 1_000))
    }

    @Test fun ackFailurePreservesVerifiedFileAndPublishedDownload() {
        val received = FlashAndroidResult("operation-A", "foto.png", FlashResultKind.RECEIVED,
            "Guardado", File("kept-photo.png"), "content://downloads/42")
        val result = recordFlashFailure(listOf(received), "operation-A", "foto.png", "Conexión interrumpida", false).single()
        assertEquals(FlashResultKind.RECEIVED, result.kind)
        assertEquals(received.file, result.file)
        assertEquals(received.downloadUri, result.downloadUri)
        assertTrue(result.confirmationIssue)
    }

    @Test fun cancellingAfterReceiveCannotDeleteACompletedReceipt() {
        val received = FlashAndroidResult("operation-A", "foto.png", FlashResultKind.RECEIVED,
            "Recibido", File("kept-photo.png"))
        val result = recordFlashFailure(listOf(received), "operation-A", "foto.png", "Cancelado", true).single()
        assertEquals(received.file, result.file)
        assertEquals(FlashResultKind.RECEIVED, result.kind)
    }

    @Test fun failureBeforePublicationNeverClaimsAReceivedFile() {
        val failure = recordFlashFailure(emptyList(), "operation-A", "foto.png", "Sin conexión", false).single()
        assertEquals(FlashResultKind.FAILED, failure.kind)
        assertNull(failure.file)
        assertNull(failure.downloadUri)
        val cancelled = recordFlashFailure(listOf(failure), "operation-A", "foto.png", "Cancelado", true)
        assertEquals(1, cancelled.size)
        assertEquals(FlashResultKind.CANCELLED, cancelled.single().kind)
    }

    @Test fun cancellationBetweenBatchFilesCannotReplaceAConfirmedDelivery() {
        val delivered = FlashAndroidResult("operation-A", "first.txt", FlashResultKind.DELIVERED, "Confirmado")
        assertEquals(listOf(delivered), recordFlashFailure(listOf(delivered), "operation-A", "first.txt", "Cancelado", true))
        assertEquals(listOf(delivered), recordFlashFailure(listOf(delivered), "operation-A", "first.txt", "Conexión interrumpida", false))
    }
}
