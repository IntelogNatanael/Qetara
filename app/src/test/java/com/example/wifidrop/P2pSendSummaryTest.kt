package com.example.wifidrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class P2pSendSummaryTest : com.example.wifidrop.LocalizedResourcesTest() {
    @Test fun anEmptyOrMerelyPreparedSelectionHasNoDeliveryConfirmation() {
        assertNull(buildP2pSendSummary(0, 0, 0, 0, false, false))
    }
    @Test fun progressAndPausedFilesNeverAppearAsSuccessfulDelivery() {
        assertEquals(P2pSendSummaryKind.ACTIVE, buildP2pSendSummary(2, 1, 0, 0, true, false)?.kind)
        assertEquals(P2pSendSummaryKind.ACTIVE, buildP2pSendSummary(2, 0, 0, 0, true, true)?.kind)
        assertEquals(P2pSendSummaryKind.ACTIVE, buildP2pSendSummary(2, 2, 0, 0, true, false)?.kind)
    }
    @Test fun aFullyAcknowledgedBatchKeepsASuccessSummaryAfterActivityStops() {
        assertEquals(P2pSendSummaryKind.SUCCESS, buildP2pSendSummary(2, 2, 0, 0, false, false)?.kind)
    }
    @Test fun failureOrCancellationCannotBeReportedAsSuccess() {
        assertEquals(P2pSendSummaryKind.ATTENTION, buildP2pSendSummary(2, 1, 1, 0, false, false)?.kind)
        assertEquals(P2pSendSummaryKind.ATTENTION, buildP2pSendSummary(2, 1, 0, 1, false, false)?.kind)
        assertEquals(P2pSendSummaryKind.ATTENTION, buildP2pSendSummary(1, 0, 0, 1, false, false)?.kind)
    }

    @Test fun completedShareActivityReplacesTheQueuedSendingTextInBothLanguages() {
        for (language in listOf("es", "en")) {
            useAppLocale(language)
            val sending = appQuantityString(R.plurals.pr_files_sending, 5, 5)
            val summary = buildP2pSendSummary(5, 5, 0, 0, false, false)
            assertEquals(
                appString(R.string.msg_last_send_complete),
                resolveP2pShareActivityStatus(sending, summary)
            )
        }
    }

    @Test fun failedAndCanceledShareActivityUsesTheIncompleteResult() {
        val sending = appQuantityString(R.plurals.pr_files_sending, 5, 5)
        for ((failed, canceled) in listOf(1 to 0, 0 to 1)) {
            assertEquals(
                appString(R.string.msg_last_send_incomplete),
                resolveP2pShareActivityStatus(sending, buildP2pSendSummary(5, 4, failed, canceled, false, false))
            )
        }
    }

    @Test fun unfinishedBatchesAndNewImportFeedbackAreNotReplacedByThePreviousResult() {
        val sending = appQuantityString(R.plurals.pr_files_sending, 5, 5)
        assertEquals(sending, resolveP2pShareActivityStatus(sending, null))
        assertEquals(sending, resolveP2pShareActivityStatus(sending, buildP2pSendSummary(5, 4, 0, 0, true, false)))
        assertEquals(sending, resolveP2pShareActivityStatus(sending, buildP2pSendSummary(5, 4, 0, 0, false, true)))
        val completed = buildP2pSendSummary(5, 5, 0, 0, false, false)
        for (newStatus in listOf(
            appQuantityString(R.plurals.pr_files_shared_imported, 2, 2),
            appQuantityString(R.plurals.pr_files_selected, 2, 2),
            appQuantityString(R.plurals.pr_files_unavailable_reselect, 1, 1)
        )) {
            assertEquals(newStatus, resolveP2pShareActivityStatus(newStatus, completed))
        }
    }
}
