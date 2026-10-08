package com.example.wifidrop.presentation

import com.example.wifidrop.LocalizedResourcesTest
import com.example.wifidrop.R
import com.example.wifidrop.appString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pLocalizedStatusTest : LocalizedResourcesTest() {
    @Test
    fun retainedSyncMessagesKeepTheirMeaningAfterChangingLanguage() {
        val start = buildSyncStartStatus(manual = true)
        val retry = buildSyncRetryStatus(2, 4)
        val failure = buildSessionSyncFailureStatus("timeout")
        val manual = buildSessionSyncFailureStatus("secure_credentials_required")
        val approval = buildSessionSyncFailureStatus("confirmacion_host_requerida")
        val confirmed = appString(R.string.pr_session_confirmed, "Equipo A")

        useAppLocale("en")
        assertTrue(isSessionSyncProgress(start))
        assertTrue(isSessionSyncRetrying(retry))
        assertTrue(isSessionSyncFailure(failure))
        assertTrue(isSessionSyncManualPairingRequired(manual))
        assertTrue(isSessionSyncApprovalRequired(approval))
        assertTrue(isSessionSyncConfirmed(confirmed))
        assertFalse(isSessionSyncConfirmed(failure))
        assertFalse(isSessionSyncFailure(confirmed))
    }

    @Test
    fun retainedSyncMessagesRenderInTheNewLanguageWithTheirOriginalArguments() {
        val start = buildSyncStartStatus(manual = true)
        val retry = buildSyncRetryStatus(2, 4)
        val failure = buildSessionSyncFailureStatus("timeout")

        useAppLocale("en")
        assertEquals(buildSyncStartStatus(manual = true), localizePresentationStatus(start))
        assertEquals(buildSyncRetryStatus(2, 4), localizePresentationStatus(retry))
        assertEquals(buildSessionSyncFailureStatus("timeout"), localizePresentationStatus(failure))

        val englishRetry = buildSyncRetryStatus(2, 4)
        useAppLocale("es")
        assertEquals(retry, localizePresentationStatus(englishRetry))
    }

    @Test
    fun partialAttachmentRecoveryLocalizesBothCountsAndKeepsItsWarning() {
        val counts = listOf(1 to 1, 1 to 2, 2 to 1, 2 to 3, 0 to 1, 0 to 2)
        val original = counts.map { (available, unavailable) -> restoredAttachmentStatus(available, unavailable) }

        useAppLocale("en")
        counts.zip(original).forEach { (count, status) ->
            assertEquals(restoredAttachmentStatus(count.first, count.second), localizePresentationStatus(status))
            assertTrue(isIncompleteAttachmentRecovery(status))
            assertTrue(P2pUiEffectsPresenter().relayShareStatus(status)!!.isError)
        }
        assertFalse(isIncompleteAttachmentRecovery(restoredAttachmentStatus(2, 0)))
    }

    @Test
    fun nestedActionCopyChangesLanguageAlongWithItsSentence() {
        val spanish = appString(R.string.pr_expired_action, appString(R.string.pr_send_messages_action))
        useAppLocale("en")
        assertEquals(
            appString(R.string.pr_expired_action, appString(R.string.pr_send_messages_action)),
            localizePresentationStatus(spanish)
        )
    }

    @Test
    fun deviceNamesAndUserContentAreNotTranslated() {
        val deviceName = buildSessionSyncFailureStatus("timeout")
        val knownStatus = appString(R.string.pr_session_confirmed, deviceName)
        val userContent = "My note: ${buildSyncStartStatus(manual = true)}"

        useAppLocale("en")
        assertEquals(appString(R.string.pr_session_confirmed, deviceName), localizePresentationStatus(knownStatus))
        assertEquals(userContent, localizePresentationStatus(userContent))
        assertEquals("secure_credentials_required", localizePresentationStatus("secure_credentials_required"))
        assertFalse(isSessionSyncProgress(userContent))
    }
}
