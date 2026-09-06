package com.example.wifidrop.presentation

import org.junit.Assert.*
import org.junit.Test

class P2pSavedRoutePolicyTest {
    private val now = 1_000_000L
    private fun session() = P2pSessionState(
        token = "QETARA88", pin = "123456", expiresAtMs = now + 60_000L, localDeviceIdShort = "local"
    )
    private fun item(name: String) = P2pSavedAttachment("content://documents/$name", name)

    @Test fun validSessionRestoresTheExactCredentialsAndOriginalExpiry() {
        val original = session().copy(confirmation = P2pSessionConfirmation(
            "192.168.1.8", "old-peer", "old-network", "QETARA88", "123456"
        ))
        assertEquals(P2pRestoredSession("QETARA88", "123456", now + 60_000L),
            restoreSessionAfterProcessDeath(saveSessionForRecreation(original), now))
        assertEquals(4, saveSessionForRecreation(original).size) // No destination or approval proof is serialized.
    }

    @Test fun incompleteAndCorruptSessionsAreRejectedOnRestore() {
        assertNull(restoreSessionAfterProcessDeath(saveSessionForRecreation(session().copy(pin = "123")), now))
        assertNull(restoreSessionAfterProcessDeath(listOf("2", "QETARA88", "123456", "2000000"), now))
        assertNull(restoreSessionAfterProcessDeath(listOf("1", "QETARA88", "123456", "not-a-time"), now))
        assertNull(restoreSessionAfterProcessDeath(listOf("1", "QETARA88", "123456", Long.MAX_VALUE.toString()), now))
    }

    @Test fun anExpiredSessionRemainsExpiredInsteadOfBeingRenewedByRecreation() {
        val expired = session().copy(expiresAtMs = now - 1L)
        val restored = restoreSessionAfterProcessDeath(saveSessionForRecreation(expired), now)
        assertEquals(P2pRestoredSession(expired.token, expired.pin, expired.expiresAtMs), restored)
        assertTrue(com.example.wifidrop.TransferSecurity.isExpired(restored!!.expiresAtMs, now))
    }

    @Test fun allThreeSelectionsAndTheLastConsumedShareRestoreIndependently() {
        val snapshot = P2pSavedAttachments(P2pAttachmentContext.DIRECT_CHAT, 42L, mapOf(
            P2pAttachmentContext.FILES to listOf(item("report.pdf")),
            P2pAttachmentContext.DIRECT_CHAT to listOf(item("private.png")),
            P2pAttachmentContext.CHANNEL to listOf(item("public.txt"))
        ))
        assertEquals(snapshot, restoreSavedAttachments(saveAttachmentsForRecreation(snapshot)))
    }

    @Test fun clearingSharedFilesDoesNotForgetThatTheIncomingIntentWasAlreadyConsumed() {
        val snapshot = P2pSavedAttachments(incomingShareEventId = 42L)
        val restored = restoreSavedAttachments(saveAttachmentsForRecreation(snapshot))
        assertTrue(restored.files.isEmpty())
        assertFalse(shouldConsumeIncomingShare(42L, restored.incomingShareEventId))
        assertTrue(shouldConsumeIncomingShare(43L, restored.incomingShareEventId))
    }

    @Test fun onlyContentUrisCanBeRestoredAndSkippedRecordsRemainVisibleAsAWarning() {
        val source = P2pSavedAttachments(files = mapOf(P2pAttachmentContext.FILES to listOf(
            item("ok.txt"), P2pSavedAttachment("https://example.test/file", "remote"),
            P2pSavedAttachment("file:///private/secret", "path"), P2pSavedAttachment("content:///empty-authority", "bad")
        )))
        val restored = restoreSavedAttachments(saveAttachmentsForRecreation(source))
        assertEquals(listOf(item("ok.txt")), restored.files[P2pAttachmentContext.FILES])
        assertEquals(3, restored.omittedCount)
    }

    @Test fun oversizedSelectionsStayBelowTheSavedStateBudgetWithoutHidingOmissions() {
        val files = (1..500).map { item("x".repeat(400) + it) }
        val encoded = saveAttachmentsForRecreation(P2pSavedAttachments(files = mapOf(P2pAttachmentContext.FILES to files)))
        val restored = restoreSavedAttachments(encoded)
        assertTrue(encoded.sumOf { it.length } < 64_064)
        assertEquals(500, restored.files.values.sumOf { it.size } + restored.omittedCount)
        assertTrue(restored.omittedCount > 0)
    }

    @Test fun malformedSnapshotDoesNotInventASelection() {
        assertTrue(restoreSavedAttachments(listOf("1", "FILES", "", "0", "FILES", "uri-only")).files.isEmpty())
        assertTrue(restoreSavedAttachments(listOf("99", "FILES", "", "0")).files.isEmpty())
        assertTrue(restoreSavedAttachments(null).files.isEmpty())
    }

    @Test fun repeatedUriIsDeduplicatedWithinItsBankButMayBelongToTwoComposers() {
        val file = item("same.png")
        val restored = restoreSavedAttachments(saveAttachmentsForRecreation(P2pSavedAttachments(files = mapOf(
            P2pAttachmentContext.FILES to listOf(file, file),
            P2pAttachmentContext.DIRECT_CHAT to listOf(file)
        ))))
        assertEquals(listOf(file), restored.files[P2pAttachmentContext.FILES])
        assertEquals(listOf(file), restored.files[P2pAttachmentContext.DIRECT_CHAT])
    }

    @Test fun inaccessibleDocumentsAreRemovedOnlyFromTheirOwnSelection() {
        val readable = item("readable.png")
        val revoked = item("revoked.png")
        val snapshot = P2pSavedAttachments(files = mapOf(
            P2pAttachmentContext.DIRECT_CHAT to listOf(readable, revoked),
            P2pAttachmentContext.FILES to listOf(item("report.pdf"))
        ))
        val checked = mutableListOf<String>()
        val recovered = recoverAccessibleAttachments(snapshot) {
            checked += it.uri
            it != revoked
        }
        assertEquals(listOf(readable), recovered.files[P2pAttachmentContext.DIRECT_CHAT])
        assertEquals(listOf(item("report.pdf")), recovered.files[P2pAttachmentContext.FILES])
        assertEquals(1, recovered.unavailable[P2pAttachmentContext.DIRECT_CHAT])
        assertEquals(3, checked.size)
        assertTrue(restoredAttachmentStatus(0, 1).contains("tu texto se conserva"))
    }

    @Test fun unsafeUrisNeverReachTheAccessCheckerEvenIfTheDecoderWasBypassed() {
        var checks = 0
        val recovered = recoverAccessibleAttachments(P2pSavedAttachments(files = mapOf(
            P2pAttachmentContext.FILES to listOf(P2pSavedAttachment("https://example.test/file", "remote"))
        ))) { checks++; true }
        assertEquals(0, checks)
        assertTrue(recovered.files[P2pAttachmentContext.FILES].orEmpty().isEmpty())
        assertEquals(1, recovered.unavailable[P2pAttachmentContext.FILES])
    }

    @Test fun recreatingAfterAnAlreadyHandledShareDoesNotNavigateAwayFromTheCurrentComposer() {
        assertFalse(shouldNavigateToIncomingShare(42L, 42L))
        assertFalse(shouldNavigateToIncomingShare(null, 42L))
        assertTrue(shouldNavigateToIncomingShare(43L, 42L))
        assertTrue(shouldNavigateToIncomingShare(42L, null))
    }

    @Test fun incompleteRecoveryRemainsVisibleUntilTheSelectionStatusChanges() {
        assertTrue(isIncompleteAttachmentRecovery(restoredAttachmentStatus(0, 1)))
        assertTrue(isIncompleteAttachmentRecovery(restoredAttachmentStatus(1, 1)))
        assertFalse(isIncompleteAttachmentRecovery(restoredAttachmentStatus(1, 0)))
        assertFalse(isIncompleteAttachmentRecovery("1 archivo(s) seleccionado(s)."))
        assertFalse(isIncompleteAttachmentRecovery(""))
    }

    @Test fun incompleteRecoveryIsNeverClassifiedAsASilentSuccess() {
        assertTrue(P2pUiEffectsPresenter().relayShareStatus(restoredAttachmentStatus(1, 1))!!.isError)
        assertTrue(P2pUiEffectsPresenter().relayShareStatus(restoredAttachmentStatus(0, 1))!!.isError)
        assertFalse(P2pUiEffectsPresenter().relayShareStatus(restoredAttachmentStatus(1, 0))!!.isError)
    }

    @Test fun invalidDeliveryIdsCannotTriggerConsumptionOrNavigation() {
        assertFalse(shouldConsumeIncomingShare(0L, null))
        assertFalse(shouldConsumeIncomingShare(-4L, null))
        assertFalse(shouldNavigateToIncomingShare(0L, null))
    }
}
