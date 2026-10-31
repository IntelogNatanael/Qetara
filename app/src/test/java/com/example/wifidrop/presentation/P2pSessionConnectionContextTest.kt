package com.example.wifidrop.presentation

import com.example.wifidrop.ConnectionMode
import com.example.wifidrop.KnownPeerSnapshot
import com.example.wifidrop.PeerRouteObservation
import com.example.wifidrop.TransferRuntimeState
import com.example.wifidrop.TrustedPeer
import com.example.wifidrop.sessionCredentialBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pSessionConnectionContextTest : com.example.wifidrop.LocalizedResourcesTest() {
    private val target = P2pResolvedTarget("pc-A", "192.168.1.8", "PC", ConnectionMode.LAN)
    private val network = "lan:true:192.168.1.7"
    private val manual = P2pSessionState(
        "ABCD1234", "123456", 9_999L, localDeviceIdShort = "self",
        connectionNetworkKey = network, connectionTargetIp = target.ip,
        lastAutoSyncedPeerIp = target.ip, readinessChangedAtMs = 900L,
        confirmation = P2pSessionConfirmation(target.ip, null, network, "ABCD1234", "123456", 100L)
    )
    private val route = PeerRouteObservation("pc-A", target.ip, "192.168.1.7", 101L, "noise-key-A",
        sessionCredentialBinding(manual.token, manual.pin, manual.expiresAtMs))
    private fun authenticated() = TransferRuntimeState(
        serviceRunning = true, activeToken = manual.token, sessionExpiresAtMs = manual.expiresAtMs,
        knownPeers = listOf(KnownPeerSnapshot("pc-A", "PC", target.ip, true, false, 1_000L)),
        trustedPeers = listOf(TrustedPeer("pc-A", "PC", 900L, noiseStaticKey = "noise-key-A")),
        authenticatedPeerRoutes = listOf(route)
    )
    private fun observe(
        state: P2pSessionState = manual,
        target: P2pResolvedTarget? = this.target,
        evidence: TransferRuntimeState? = authenticated(),
        network: String = this.network
    ) = observeSessionConnectionContext(state, network, target, evidence, nowMs = 1_000L)

    private fun ready(state: P2pSessionState, target: P2pResolvedTarget = this.target) = isSessionReadyForTarget(
        target, state.token, state.pin, false, state.connectionNetworkKey,
        state.readinessChangedAtMs, state.confirmation, emptyList()
    )

    @Test
    fun manualEndpointLearnsItsAuthenticatedIdentityWithoutLosingPreparation() {
        assertTrue(ready(manual, target.copy(peerId = null)))
        val learned = observe()
        assertTrue(ready(learned))
        assertEquals("pc-A", learned.confirmation?.peerId)
        assertEquals("pc-A", learned.connectionTargetPeerId)
        assertFalse(learned.confirmation!!.awaitingAuthenticatedIdentity)
        assertEquals(manual.token, learned.token)
        assertEquals(manual.pin, learned.pin)
        assertEquals(manual.expiresAtMs, learned.expiresAtMs)
        assertEquals(manual.readinessChangedAtMs, learned.readinessChangedAtMs)
        assertEquals(target.ip, learned.lastAutoSyncedPeerIp)
        assertNull(resolveLanAutoSyncPlan(true, target, false, learned.lastAutoSyncedPeerIp))
    }

    @Test
    fun discoveryBeforeAuthenticatedCallbackKeepsOnlyAPendingConfirmation() {
        val discovered = observe(evidence = authenticated().copy(authenticatedPeerRoutes = emptyList()))
        assertEquals(manual.confirmation!!.copy(awaitingAuthenticatedIdentity = true), discovered.confirmation)
        assertFalse(ready(discovered))
        assertFalse(ready(discovered, target.copy(peerId = null)))
        assertTrue(ready(observe(discovered)))
    }

    @Test
    fun addressDiscoveryAndStoredTrustAloneCannotBindAnIdentity() {
        listOf(null, authenticated().copy(authenticatedPeerRoutes = emptyList())).forEach { evidence ->
            val observed = observe(evidence = evidence)
            assertNull(observed.confirmation?.peerId)
            assertFalse(ready(observed))
        }
    }

    @Test
    fun proofMustMatchTheEndpointLocalInterfaceAndCurrentTrustedNoiseKey() {
        val invalidRoutes = listOf(
            route.copy(peerId = "pc-B"), route.copy(peerIp = "192.168.1.9"),
            route.copy(localIp = "192.168.2.7"), route.copy(noiseStaticKey = "noise-key-B"),
            route.copy(noiseStaticKey = "")
        )
        invalidRoutes.forEachIndexed { index, invalid ->
            assertFalse("Invalid route $index", ready(observe(evidence = authenticated().copy(authenticatedPeerRoutes = listOf(invalid)))))
        }
        assertFalse(ready(observe(evidence = authenticated().copy(trustedPeers = emptyList()))))
    }

    @Test
    fun proofFromBeforeManualConfirmationOrRevocationCannotBeReused() {
        listOf(99L, 100L).forEach { acceptedAt ->
            assertFalse(ready(observe(evidence = authenticated().copy(
                authenticatedPeerRoutes = listOf(route.copy(acceptedAtElapsedMs = acceptedAt))
            ))))
        }
        assertFalse(ready(observe(evidence = authenticated().copy(peerRouteRevocations = mapOf("pc-A" to 101L)))))
        assertFalse(ready(observe(state = manual.copy(confirmation = manual.confirmation!!.copy(confirmedAtElapsedMs = null)))))
    }

    @Test
    fun proofFromInactiveOrDifferentSessionCannotBindTheConfirmation() {
        listOf(
            authenticated().copy(serviceRunning = false),
            authenticated().copy(activeToken = "OTHER123"),
            authenticated().copy(sessionExpiresAtMs = 8_888L)
        ).forEach { evidence -> assertFalse(ready(observe(evidence = evidence))) }
        assertNull(observe(state = manual.copy(expiresAtMs = 999L)).confirmation)
    }

    @Test
    fun aSocketUsingThePreviousPinCannotBindANewManualConfirmationBeforeReceiverRestart() {
        val changedPin = manual.copy(pin = "654321", confirmation = manual.confirmation!!.copy(pin = "654321"))
        // The old receiver still publishes the same token and expiry, but authenticated with the old PIN.
        assertFalse(ready(observe(changedPin)))
        val currentRoute = route.copy(sessionBinding = sessionCredentialBinding(
            changedPin.token, changedPin.pin, changedPin.expiresAtMs
        ))
        assertTrue(ready(observe(changedPin, evidence = authenticated().copy(authenticatedPeerRoutes = listOf(currentRoute)))))
    }

    @Test
    fun oldRouteCannotBindAfterRuntimeChangesTokenOrExpiry() {
        val newToken = manual.copy(token = "NEW12345", confirmation = manual.confirmation!!.copy(token = "NEW12345"))
        assertFalse(ready(observe(newToken, evidence = authenticated().copy(activeToken = newToken.token))))
        val renewed = manual.copy(expiresAtMs = 19_999L)
        assertFalse(ready(observe(renewed, evidence = authenticated().copy(sessionExpiresAtMs = renewed.expiresAtMs))))
    }

    @Test
    fun authenticatedCredentialsExchangeWithoutPskProofDoesNotBindManualPreparation() {
        assertFalse(ready(observe(evidence = authenticated().copy(authenticatedPeerRoutes = listOf(route.copy(sessionBinding = null))))))
    }

    @Test
    fun credentialBindingHasUnambiguousFieldBoundaries() {
        assertNotEquals(sessionCredentialBinding("AB", "C", 1L), sessionCredentialBinding("A", "BC", 1L))
    }

    @Test
    fun changingIpNetworkOrTransportInvalidatesEvenWithValidIdentityEvidence() {
        assertNull(observe(target = target.copy(ip = "192.168.1.9")).confirmation)
        assertNull(observe(network = "lan:true:192.168.2.7").confirmation)
        assertNull(observe(target = target.copy(mode = ConnectionMode.WIFI_DIRECT), network = "direct:true:192.168.1.8:false").confirmation)
    }

    @Test
    fun aDifferentIdentityAtTheSameAddressNeverInheritsConfirmedOrPendingPreparation() {
        val bound = observe()
        val discovered = observe(evidence = null)
        listOf(bound, discovered).forEach { state ->
            val changed = observe(state, target.copy(peerId = "pc-B"))
            assertNull(changed.confirmation)
            assertNull(changed.lastAutoSyncedPeerIp)
            assertFalse(ready(observe(changed)))
        }
    }

    @Test
    fun explicitlyChangingReceiverThenReturningCannotRestoreAnOldConfirmation() {
        val different = observe(target = target.copy(peerId = null, ip = "192.168.1.9"))
        val returned = observe(different, target.copy(peerId = null))
        assertNull(returned.confirmation)
        assertFalse(ready(observe(returned)))
        assertFalse(ready(observe(observe(target = null))))
    }

    @Test
    fun authenticatedObservationCannotCreatePreparationOrRestoreEditedCredentials() {
        assertFalse(ready(observe(state = manual.copy(confirmation = null))))
        assertFalse(ready(observe(state = manual.copy(token = "EDIT1234"))))
        assertFalse(ready(observe(state = manual.copy(pin = "654321"))))
        val bound = observe()
        assertNull(observe(bound, target.copy(peerId = null)).confirmation)
    }
}
