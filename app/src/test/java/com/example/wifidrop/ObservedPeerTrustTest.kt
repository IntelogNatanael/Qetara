package com.example.wifidrop

import org.junit.Assert.*
import org.junit.Test

class ObservedPeerTrustTest {
    private val peer = KnownPeerSnapshot("peer-a", "A", "192.0.2.7", true, globalLanJoined = false, lastSeenAtMs = 10L)
    private val stored = TrustedPeer("peer-a", "A", 1L, noiseStaticKey = "key-a")

    @Test fun lateAuthenticatedObservationCannotRestoreForgottenTrust() {
        assertTrue(retainCurrentPeerTrust(listOf(peer), listOf(stored)).single().trusted)
        val afterForgetAndLateCallback = retainCurrentPeerTrust(listOf(peer.copy(lastSeenAtMs = 20L)), emptyList())
        assertFalse(afterForgetAndLateCallback.single().trusted)
        assertEquals(peer.ip, afterForgetAndLateCallback.single().ip)
    }

    @Test fun discoveryCannotAcquireTrustJustBecauseIdentityIsStored() {
        val unverified = peer.copy(trusted = false, ip = "192.0.2.8")
        assertFalse(retainCurrentPeerTrust(listOf(unverified), listOf(stored)).single().trusted)
    }

    @Test fun revocationOnlyRemovesTheAffectedIdentity() {
        val second = peer.copy(id = "peer-b")
        val current = retainCurrentPeerTrust(listOf(peer, second), listOf(stored))
        assertTrue(current.first { it.id == "peer-a" }.trusted)
        assertFalse(current.first { it.id == "peer-b" }.trusted)
    }
}
