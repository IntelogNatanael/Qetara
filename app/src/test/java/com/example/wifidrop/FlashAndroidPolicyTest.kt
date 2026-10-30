package com.example.wifidrop

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.wifidrop.protocol.flash.FlashApproval
import com.example.wifidrop.protocol.flash.FlashPeer
import com.example.wifidrop.protocol.flash.FLASH_TEXT_MAX_BYTES
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FlashAndroidPolicyTest : LocalizedResourcesTest() {
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

    @Test fun retainedSessionAndReceiptUseTheCurrentLanguage() {
        val state = FlashAndroidState(statusText = flashPlural(R.plurals.flash_files_delivered, 2, 2))
        val received = FlashAndroidResult("operation-A", "foto.png", FlashResultKind.RECEIVED,
            flashText(R.string.flash_received_verified), File("kept-photo.png"))
        val result = recordFlashFailure(listOf(received), "operation-A", "foto.png",
            flashErrorText("unconfirmed"), false).single()

        assertEquals("2 archivos entregados y confirmados.", state.status)
        assertEquals("Archivo recibido y verificado. No se pudo confirmar la entrega al otro equipo.", result.detail)
        useAppLocale("en")
        assertEquals("2 files delivered and confirmed.", state.status)
        assertEquals("File received and verified. Could not confirm delivery to the other device.", result.detail)
        assertEquals("foto.png", result.fileName)
        assertEquals(received.file, result.file)
        assertEquals("1 file ready to send", flashPlural(R.plurals.flash_files_ready_to_send, 1, 1).resolve())
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

    @Test fun textReceiptConsumesOnlyTheMatchingDraft() {
        val body = "  Hola,\n你好 👋  "
        val unchangedDraft = FlashAndroidState(textDraft = body, sendingText = true).completeTextDraft(body)
        assertEquals("", unchangedDraft.textDraft)
        assertFalse(unchangedDraft.sendingText)
        val editedDraft = FlashAndroidState(textDraft = "otro texto", sendingText = true).completeTextDraft(body)
        assertEquals("otro texto", editedDraft.textDraft)
        assertFalse(editedDraft.sendingText)
    }

    @Test fun textAckFailurePreservesExactVerifiedBodyAndDoesNotClaimDelivery() {
        val body = "  Hola,\r\n你好 👋  "
        val received = FlashAndroidResult("message-A", "message.txt", FlashResultKind.RECEIVED,
            "Recibido", file = File("kept-message.txt"), textBody = body)
        val result = recordFlashFailure(listOf(received), "message-A", "message.txt", "Sin confirmación", false).single()
        assertEquals(FlashResultKind.RECEIVED, result.kind)
        assertEquals(body, result.textBody)
        assertTrue(result.confirmationIssue)
        assertTrue(result.detail.startsWith("Texto recibido y verificado."))
    }

    @Test fun retainedTextReceiptAndFailureFollowLanguageWithoutChangingBody() {
        val body = "  Hola,\r\n你好 👋  "
        val received = FlashAndroidResult("text-A", "message.txt", FlashResultKind.RECEIVED,
            flashText(R.string.flash_text_received_verified), textBody = body)
        val result = recordFlashFailure(listOf(received), received.id, received.fileName,
            flashTextErrorText("unconfirmed"), false).single()
        val status = FlashAndroidState(statusText = flashTextErrorText("rejected"), textDraft = body)

        assertTrue(result.detail.startsWith("Texto recibido y verificado."))
        assertTrue(status.status.contains("El texto sigue en el borrador."))
        useAppLocale("en")
        assertEquals("Text received and verified. Could not confirm delivery to the other device.", result.detail)
        assertTrue(status.status.contains("The text is still in your draft."))
        assertEquals(body, result.textBody)
        assertEquals(body, status.textDraft)
        assertTrue(result.confirmationIssue)
        assertEquals(FlashResultKind.RECEIVED, result.kind)
    }

    @Test fun retainedRejectedEditsFollowLanguageAndPreserveSelection() {
        val current = TextFieldValue("Conservar", TextRange(2, 5))
        val oversized = pasteFlashText(current, "x".repeat(FLASH_TEXT_MAX_BYTES + 1))
        val malformed = acceptFlashTextEdit(current, TextFieldValue("\uD800"))

        assertTrue(oversized.error!!.contains("El borrador no cambió."))
        assertTrue(malformed.error!!.contains("caracteres Unicode no válidos"))
        useAppLocale("en")
        assertEquals("Text cannot exceed 16 KiB (16,384 bytes). Your draft was not changed.", oversized.error)
        assertEquals("The text contains invalid Unicode characters. Your draft was not changed.", malformed.error)
        assertEquals(current, oversized.value)
        assertEquals(current, malformed.value)
    }

    @Test fun failedTextRetainsBodyWithinExistingResultLimit() {
        val previous = (1..20).map {
            FlashAndroidResult("text-$it", "message.txt", FlashResultKind.RECEIVED, "Recibido", textBody = "Texto $it")
        }
        val updated = recordFlashFailure(previous, "failed-text", "message.txt", "Sin conexión", false, textBody = "  exacto\n")
        assertEquals(20, updated.size)
        assertEquals(FlashResultKind.FAILED, updated.first().kind)
        assertEquals("  exacto\n", updated.first().textBody)
        assertFalse(updated.any { it.id == "text-20" })
    }

    @Test fun oversizePastePreservesDraftAndSelectionWithoutTruncation() {
        val current = TextFieldValue("Un borrador", TextRange(3, 7))
        val result = pasteFlashText(current, "x".repeat(FLASH_TEXT_MAX_BYTES + 1))
        assertEquals(current, result.value)
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("El borrador no cambió"))
    }

    @Test fun pasteValidatesCombinedUtf8SizeBeforeAcceptingIt() {
        val current = TextFieldValue("é".repeat(FLASH_TEXT_MAX_BYTES / 2), TextRange(0))
        val result = pasteFlashText(current, "a")
        assertEquals(current, result.value)
        assertNotNull(result.error)
    }

    @Test fun malformedUnicodeAndOversizeTypedEditPreservePreviousCursor() {
        val current = TextFieldValue("Conservar", TextRange(2))
        val malformed = acceptFlashTextEdit(current, TextFieldValue("\uD800"))
        assertEquals(current, malformed.value)
        assertNotNull(malformed.error)
        val oversized = acceptFlashTextEdit(current, TextFieldValue("é".repeat(FLASH_TEXT_MAX_BYTES)))
        assertEquals(current, oversized.value)
        assertNotNull(oversized.error)
    }

    @Test fun validPasteReplacesOnlySelectedRangeAndPreservesExactText() {
        val current = TextFieldValue("Inicio FIN final", TextRange(7, 10))
        val pasted = "  你好 👋\r\n"
        val result = pasteFlashText(current, pasted)
        assertNull(result.error)
        assertEquals("Inicio " + pasted + " final", result.value.text)
        assertEquals(TextRange(7 + pasted.length), result.value.selection)
    }
}
