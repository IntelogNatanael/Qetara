package com.example.wifidrop.pc

import java.util.Locale
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class DesktopFlashCopyTest {
    @Test
    fun contextualActionsNeverOfferAMessageForFilesOnlyOrAnEmptyDraft() {
        assertEquals(DesktopFlashSendActions.NONE, desktopFlashSendActions(" \n", 0))
        assertEquals(DesktopFlashSendActions.MESSAGE, desktopFlashSendActions("Hola 🙂", 0))
        assertEquals(DesktopFlashSendActions.FILES, desktopFlashSendActions(" \n", 3))
        assertEquals(DesktopFlashSendActions.BOTH, desktopFlashSendActions("Hola 🙂", 3))
    }

    @Test
    fun retainedCopyResolvesAtPresentationTimeAndUnknownLanguagesUseEnglish() {
        val retained = flashText("Archivo recibido: informe español.txt", "File received: informe español.txt")
        assertEquals("Archivo recibido: informe español.txt", retained.resolve(Locale.forLanguageTag("es-PE")))
        assertEquals("File received: informe español.txt", retained.resolve(Locale.US))
        assertEquals(retained.resolve(Locale.US), retained.resolve(Locale.JAPANESE))
    }

    @Test
    fun allTransportFailuresHaveBothLanguagesWithoutUsingRemoteDiagnostics() {
        listOf("discovery_failed", "cancelled", "rejected", "expired", "approval_expired", "busy",
            "invalid_file", "invalid_batch", "batch_incompatible", "invalid_address", "peer_changed",
            "integrity_failed", "invalid_frame", "storage_unavailable", "incompatible", "connection_failed",
            "unknown_code").forEach { code ->
            val text = flashFailureText(code)
            assertFalse(text.spanish.isBlank(), code)
            assertFalse(text.english.isBlank(), code)
            assertNotEquals(text.resolve(Locale.US), text.resolve(Locale.forLanguageTag("es")), code)
        }
        assertEquals("Text cannot exceed 16 KiB (16,384 bytes).", flashValidationText("text_too_large").resolve(Locale.US))
    }
}
