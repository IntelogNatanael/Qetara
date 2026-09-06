package com.example.wifidrop.presentation

import com.example.wifidrop.SessionCredentialsPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pSessionSyncIdentityTest {
    private val started = P2pSessionState(
        "OLD12345", "123456", 999_999L, localDeviceIdShort = "self",
        connectionNetworkKey = "lan:true:10.0.2.16", connectionTargetIp = "10.0.2.2"
    )
    private val payload = SessionCredentialsPayload(
        "NEW12345", "654321", 9_999_999L, peerId = "android-A", peerLabel = "Android A"
    )

    @Test
    fun firstManualSyncKeepsTheSocketEndpointAndBindsItsAuthenticatedIdentity() {
        val synced = applySessionSyncResult(started, started, "10.0.2.2", payload)
        assertEquals("10.0.2.2", synced.confirmation?.peerIp)
        assertEquals("android-A", synced.connectionTargetPeerId)
        assertEquals("android-A", synced.confirmation?.peerId)
        assertTrue(isSessionReadyForTarget(
            P2pResolvedTarget("android-A", "10.0.2.2", "Android A", com.example.wifidrop.ConnectionMode.LAN),
            synced.token, synced.pin, false, synced.connectionNetworkKey, 0L, synced.confirmation, emptyList()
        ))
    }

    @Test
    fun lateResponseCannotReplaceTheSessionAfterChangingReceiverOrNetwork() {
        val otherReceiver = started.copy(connectionTargetIp = "10.0.2.3")
        assertSame(otherReceiver, applySessionSyncResult(otherReceiver, started, "10.0.2.2", payload))
        val otherNetwork = started.copy(connectionNetworkKey = "lan:true:192.168.1.8")
        assertSame(otherNetwork, applySessionSyncResult(otherNetwork, started, "10.0.2.2", payload))
    }

    @Test
    fun lateResponseCannotOverwriteCredentialsEditedOrRenewedByTheUser() {
        val edited = started.copy(token = "EDIT1234")
        assertSame(edited, applySessionSyncResult(edited, started, "10.0.2.2", payload))
        val renewed = started.copy(expiresAtMs = started.expiresAtMs + 1_000L)
        assertSame(renewed, applySessionSyncResult(renewed, started, "10.0.2.2", payload))
    }

    @Test
    fun aDifferentCurrentIdentityAtTheSameAddressCannotReceiveTheConfirmation() {
        val replaced = started.copy(connectionTargetPeerId = "android-B")
        assertSame(replaced, applySessionSyncResult(replaced, started, "10.0.2.2", payload))
    }
}
