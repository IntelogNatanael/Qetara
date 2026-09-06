package com.example.wifidrop

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CredentialApprovalPolicyTest {
    private val shown = PendingCredentialShareRequest("peer", "Displayed peer", "10.0.0.8", 123L, "key-A")
    private fun state(request: PendingCredentialShareRequest? = shown) = MutableStateFlow(TransferRuntimeState(pendingCredentialShare = request))

    @Test fun approvingDisplayedRequestConsumesItOnce() {
        val state = state()
        assertEquals(shown, claimCredentialShareRequest(state, shown.id, "key-A", 123L))
        assertNull(state.value.pendingCredentialShare)
        assertNull(claimCredentialShareRequest(state, shown.id, "key-A", 123L))
    }

    @Test fun replacementKeyWithSameIdCannotInheritApproval() {
        val replacement = shown.copy(noiseStaticKey = "key-B", requestedAtMs = 200L)
        val state = state(replacement)
        assertNull(claimCredentialShareRequest(state, shown.id, "key-A", 123L))
        assertEquals(replacement, state.value.pendingCredentialShare)
    }

    @Test fun laterRequestWithSameKeyNeedsItsOwnApproval() {
        val replacement = shown.copy(requestedAtMs = 200L)
        val state = state(replacement)
        assertNull(claimCredentialShareRequest(state, shown.id, "key-A", 123L))
        assertEquals(replacement, state.value.pendingCredentialShare)
    }

    @Test fun absentOrIncompleteApprovalNeverConsumesPendingRequest() {
        val state = state()
        assertNull(claimCredentialShareRequest(state, "another-id", "key-A", 123L))
        assertNull(claimCredentialShareRequest(state, shown.id, "", 123L))
        assertNull(claimCredentialShareRequest(state, shown.id, "key-A", -1L))
        assertEquals(shown, state.value.pendingCredentialShare)
    }

    @Test fun concurrentClicksCanClaimOnlyOneExactRequest() {
        val state = state()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val claims = pool.invokeAll(List(12) { Callable { claimCredentialShareRequest(state, shown.id, "key-A", 123L) } })
            assertEquals(1, claims.count { it.get(2, TimeUnit.SECONDS) != null })
            assertNull(state.value.pendingCredentialShare)
        } finally { pool.shutdownNow() }
    }
}
