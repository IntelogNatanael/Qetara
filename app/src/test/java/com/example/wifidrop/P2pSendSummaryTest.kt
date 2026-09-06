package com.example.wifidrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class P2pSendSummaryTest {
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
}
