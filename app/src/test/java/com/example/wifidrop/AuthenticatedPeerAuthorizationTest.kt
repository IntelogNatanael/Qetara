package com.example.wifidrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthenticatedPeerAuthorizationTest {
    @Test fun unknownManualPeerRequiresApprovalBeforePayload() {
        var requested = 0
        assertFalse(authorizeAuthenticatedPeer("new-key", { false }, { null }, { requested++; false }))
        assertEquals(1, requested)
    }

    @Test fun approvedAndPinnedPeerCanSendWithoutAnotherPrompt() {
        assertTrue(authorizeAuthenticatedPeer("known-key", { true }, { "known-key" }, {
            fail("An approved identity should not prompt again")
            false
        }))
    }

    @Test fun legacyTrustWithoutPinnedIdentityStillRequiresApproval() {
        var requested = false
        assertFalse(authorizeAuthenticatedPeer("new-key", { true }, { null }, { requested = true; false }))
        assertTrue(requested)
    }

    @Test fun callbackSuccessCannotBypassExactPinnedApproval() {
        assertFalse(authorizeAuthenticatedPeer("new-key", { false }, { null }, { true }))
        var pinned: String? = null
        var trusted = false
        assertFalse(authorizeAuthenticatedPeer("new-key", { trusted }, { pinned }, {
            trusted = true
            pinned = "different-key"
            true
        }))
        pinned = null
        assertTrue(authorizeAuthenticatedPeer("new-key", { trusted }, { pinned }, {
            trusted = true
            pinned = "new-key"
            true
        }))
    }

    @Test fun changedPinnedKeyIsRejectedWithoutShowingApproval() {
        var requested = false
        try {
            authorizeAuthenticatedPeer("replacement-key", { true }, { "original-key" }, { requested = true; true })
            fail("Changed identity should fail")
        } catch (error: SecurityException) {
            assertEquals("noise_key_mismatch", error.message)
        }
        assertFalse(requested)
    }
}
