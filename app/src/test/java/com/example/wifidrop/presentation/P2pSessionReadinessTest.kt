package com.example.wifidrop.presentation

import com.example.wifidrop.ConnectionMode
import com.example.wifidrop.KnownPeerSnapshot
import com.example.wifidrop.SessionCredentialShare
import com.example.wifidrop.TransferRuntimeState
import com.example.wifidrop.WifiDirectState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pSessionReadinessTest {
    private val target = P2pResolvedTarget(peerId = "peer-A", ip = "192.168.1.8", label = "PC", mode = ConnectionMode.LAN)
    private val confirmed = P2pSessionConfirmation(target.ip, target.peerId, "network-A", "ABCD1234", "123456")

    private fun ready(
        confirmation: P2pSessionConfirmation? = null,
        target: P2pResolvedTarget? = this.target,
        token: String = "ABCD1234",
        pin: String = "123456",
        network: String = "network-A",
        expired: Boolean = false,
        shared: List<SessionCredentialShare> = emptyList(),
        changedAt: Long = 100L
    ) = isSessionReadyForTarget(target, token, pin, expired, network, changedAt, confirmation, shared)

    @Test
    fun selectingAnAddressOrWaitingForApprovalNeverMeansSessionReady() {
        assertFalse(ready())
        assertFalse(ready(target = null))
    }

    @Test
    fun successfulSyncOrExplicitManualConfirmationEnablesTheMatchingSession() {
        assertTrue(ready(confirmation = confirmed))
        assertFalse(ready(confirmation = confirmed, token = "NEWCODE8"))
        assertFalse(ready(confirmation = confirmed, pin = "654321"))
        assertFalse(ready(confirmation = confirmed, expired = true))
    }

    @Test
    fun changingDeviceOrNetworkInvalidatesThePreviousConfirmation() {
        assertFalse(ready(confirmation = confirmed, target = target.copy(ip = "192.168.1.9")))
        assertFalse(ready(confirmation = confirmed, target = target.copy(peerId = "different-identity")))
        assertFalse(ready(confirmation = confirmed, network = "network-B"))
    }

    @Test
    fun androidHostNeedsACompletedCredentialResponseForTheSelectedIdentityAndIp() {
        val evidence = SessionCredentialShare("peer-A", "192.168.1.8", 150L)
        assertTrue(ready(shared = listOf(evidence)))
        assertFalse(ready(shared = listOf(evidence.copy(peerId = "different-identity"))))
        assertFalse(ready(shared = listOf(evidence.copy(peerIp = "192.168.1.9"))))
        assertFalse(ready(shared = listOf(evidence), target = target.copy(peerId = null)))
        assertFalse(ready(shared = listOf(evidence), changedAt = 200L))
        assertFalse(ready(shared = listOf(evidence), expired = true))
    }

    @Test
    fun loopbackWildcardMulticastAndLocalAddressAreNeverAutomaticTargets() {
        listOf("127.0.0.1", "127.1.2.3", "0.0.0.0", "::1", "localhost", "224.0.0.1", "255.255.255.255").forEach {
            assertFalse(it, isAutomaticConnectionAddress(it))
        }
        assertFalse(isAutomaticConnectionAddress("192.168.1.7", "192.168.1.7"))
        assertTrue(isAutomaticConnectionAddress("192.168.1.8", "192.168.1.7"))
        assertTrue(isAutomaticConnectionAddress("10.0.2.2", "10.0.2.15"))
    }

    @Test
    fun aLoopbackDiscoveryCannotTriggerAutomaticCredentialSync() {
        assertNull(resolveLanAutoSyncPlan(true, target.copy(ip = "127.0.0.1"), false, null))
    }

    @Test
    fun routingSkipsLoopbackAndThisAndroidEvenIfTheyAreMoreRecent() {
        val peers = listOf(
            KnownPeerSnapshot("self", "This Android", "192.168.1.7", true, false, 300L),
            KnownPeerSnapshot("probe", "QA", "127.0.0.1", true, false, 200L),
            KnownPeerSnapshot("remote", "Other Android", "192.168.1.8", false, false, 100L)
        )
        val routing = resolveP2pRouting(P2pRoutingInput(
            wifiState = WifiDirectState(),
            transferState = TransferRuntimeState(knownPeers = peers, lastPeerIp = "127.0.0.1"),
            lanConnected = true,
            activeConnectionMode = ConnectionMode.LAN,
            manualTargetIp = "",
            chatDirectLanTargetIp = null,
            chatDirectWifiTargetIps = emptyList(),
            lanLocalIp = "192.168.1.7"
        ))
        assertEquals("192.168.1.8", routing.targets.resolvedTarget?.ip)
    }
}
