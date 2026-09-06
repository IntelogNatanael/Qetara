package com.example.wifidrop

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class DirectGroupPolicyTest {
    private val owner = "10.77.8.1"
    private val group = DirectGroupContext("group-a", owner, true, "p2p-fixture", setOf(owner), 100L)
    private val first = peer("first", "10.77.8.4")
    private val second = peer("second", "10.77.8.9")
    private val lan = peer("pc-lan", "172.20.3.7")
    private val ownerConnection = ConnectionSnapshot(true, true, owner)
    private fun peer(id: String, ip: String, trusted: Boolean = true) = KnownPeerSnapshot(id, id, ip, trusted, false, 500L)
    private fun route(peer: KnownPeerSnapshot, localIp: String = owner, time: Long = 200L) =
        PeerRouteObservation(peer.id, peer.ip, localIp, time, "key-${peer.id}")
    private fun state() = TransferRuntimeState(
        directGroup = group, knownPeers = listOf(first, second, lan),
        trustedPeers = listOf(first, second, lan).map { TrustedPeer(it.id, it.label, 1L, noiseStaticKey = "key-${it.id}") },
        authenticatedPeerRoutes = listOf(route(first), route(second), route(lan, "172.20.3.1"))
    )
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected conservative rejection") } catch (_: SecurityException) { }
    }

    @Test fun trustedLanPeersNeverEnterDirectRoster() {
        assertEquals(listOf(first, second), resolveVerifiedDirectParticipants(ownerConnection, state()))
        assertTrue(resolveVerifiedDirectParticipants(ownerConnection, state().copy(authenticatedPeerRoutes = emptyList())).isEmpty())
    }

    @Test fun discoveryOrTrustAloneCannotEstablishMembership() {
        val untrusted = first.copy(trusted = false)
        val state = state().copy(knownPeers = listOf(untrusted, lan))
        assertTrue(resolveVerifiedDirectParticipants(ownerConnection, state).isEmpty())
    }

    @Test fun currentOwnerContextAndConnectionMustAgree() {
        assertTrue(resolveVerifiedDirectParticipants(ownerConnection, state().copy(directGroup = null)).isEmpty())
        assertTrue(resolveVerifiedDirectParticipants(ownerConnection.copy(groupOwnerAddress = "10.77.9.1"), state()).isEmpty())
        assertTrue(resolveVerifiedDirectParticipants(ownerConnection.copy(groupFormed = false), state()).isEmpty())
    }

    @Test fun oldAndBoundarySocketsCannotJoinNewGroup() {
        val state = state().copy(authenticatedPeerRoutes = listOf(route(first, time = 99), route(second, time = 100)))
        assertTrue(resolveVerifiedDirectParticipants(ownerConnection, state).isEmpty())
    }

    @Test fun clientKeepsOnlyPlatformOwnerBeforeApproval() {
        val client = ownerConnection.copy(isGroupOwner = false)
        val participants = resolveVerifiedDirectParticipants(client, TransferRuntimeState(knownPeers = listOf(lan)))
        assertEquals(listOf(owner), participants.map { it.ip })
        assertFalse(participants.single().trusted)
    }

    @Test fun authenticatedGroupClientCanRelayOnlyToOtherGroupClient() {
        assertEquals(listOf(second), requireDirectRelayTargets(state(), route(first)))
        assertEquals(listOf(second), requireDirectRelayTargets(state(), route(first), second.ip, second.id))
    }

    @Test fun lanSenderAndLanDestinationAreRejectedEvenWhenTrusted() {
        rejected { requireDirectRelayTargets(state(), route(lan, "172.20.3.1")) }
        rejected { requireDirectRelayTargets(state(), route(first), lan.ip, lan.id) }
        rejected { requireDirectRelayTargets(state(), route(first), second.ip, "another-peer") }
    }

    @Test fun currentMessageSocketMustUseGroupEndpoint() {
        // A saved observation for this identity cannot authorize a new request through LAN.
        rejected { requireDirectRelayTargets(state(), route(first, "172.20.3.1")) }
        rejected { requireDirectChannelSender(state(), route(first, time = 90)) }
    }

    @Test fun clientAcceptsChannelTrafficOnlyFromItsOwnerEndpoint() {
        val ownerPeer = peer("owner", owner)
        val clientGroup = group.copy(isOwner = false, localAddresses = setOf(first.ip))
        val state = state().copy(directGroup = clientGroup, knownPeers = listOf(ownerPeer, lan),
            trustedPeers = listOf(TrustedPeer(ownerPeer.id, ownerPeer.label, 1L, noiseStaticKey = "key-${ownerPeer.id}")))
        requireDirectChannelSender(state, route(ownerPeer, first.ip))
        rejected { requireDirectChannelSender(state, route(lan, first.ip)) }
        rejected { requireDirectRelayTargets(state, route(ownerPeer, first.ip)) }
    }

    @Test fun repeatedGroupObservationPreservesEpoch() {
        val epoch = DirectGroupEpoch()
        var ids = 0
        fun update(now: Long) = epoch.update(owner, true, "actual-interface", setOf(owner), "DIRECT-fixture", "owner-device", now) { "g${++ids}" }
        val firstGroup = update(100)
        assertSame(firstGroup, update(800))
        assertEquals(1, ids)
    }

    @Test fun disconnectAndRecreateRejectsPreviousMembership() {
        val epoch = DirectGroupEpoch()
        var ids = 0
        fun update(now: Long) = epoch.update(owner, true, "actual-interface", setOf(owner), "DIRECT-fixture", "owner-device", now) { "g${++ids}" }
        val old = update(100)!!
        epoch.clear()
        val renewed = update(500)!!
        assertNotEquals(old.sessionId, renewed.sessionId)
        assertTrue(resolveVerifiedDirectParticipants(ownerConnection, state().copy(directGroup = renewed)).isEmpty())
        assertEquals(listOf(first), resolveVerifiedDirectParticipants(ownerConnection,
            state().copy(directGroup = renewed, authenticatedPeerRoutes = listOf(route(first, time = 600)))))
    }

    @Test fun missingOrWrongInterfaceAddressCannotCreateGroupContext() {
        val epoch = DirectGroupEpoch()
        assertNull(epoch.update(owner, true, "", setOf(owner), "group", "owner", 100) { "id" })
        assertNull(epoch.update(owner, true, "actual-interface", setOf("172.20.3.1"), "group", "owner", 100) { "id" })
    }

    private fun queued(scope: ChatMessageScope = ChatMessageScope.DIRECT_CHANNEL) = PendingMessageTask(
        id = "message", chatMessageId = "chat", targetIp = second.ip, peerLabel = second.label,
        scope = scope, message = "Only this group", token = "not-used", pin = "not-used",
        clientId = "owner", deviceLabel = "owner", expectedPeerId = second.id, directGroupSessionId = group.sessionId
    )

    @Test fun queuedChannelIsRevalidatedForGroupAndExactIdentity() {
        validateDirectGroupDelivery(queued(), state())
        rejected { validateDirectGroupDelivery(queued(), state().copy(directGroup = group.copy(sessionId = "new-group"))) }
        rejected { validateDirectGroupDelivery(queued().copy(expectedPeerId = "other-identity"), state()) }
        rejected { validateDirectGroupDelivery(queued().copy(directGroupSessionId = null), state()) }
    }

    @Test fun explicitDirectAndLanMessagesRemainIndependentOfGroup() {
        validateDirectGroupDelivery(queued(ChatMessageScope.DIRECT).copy(directGroupSessionId = null), TransferRuntimeState())
        validateDirectGroupDelivery(queued(ChatMessageScope.GLOBAL_LAN).copy(directGroupSessionId = null), TransferRuntimeState())
    }

    @Test fun acceptedSocketPreservesItsActualLocalEndpoint() {
        // Loopback verifies Java's endpoint reporting; it does not simulate Wi-Fi hardware.
        ServerSocket(0).use { server ->
            server.soTimeout = 2000
            for (address in listOf("127.0.0.1", "127.0.0.2")) {
                Socket(InetAddress.getByName(address), server.localPort).use {
                    server.accept().use { accepted -> assertEquals(address, accepted.localAddress.hostAddress) }
                }
            }
        }
    }
    @Test fun forgetThenRetrustCannotReusePreviousRouteOrLateCallback() {
        val revoked = revokePeerRouteEvidence(state(), first.id, 300L)
        assertTrue(revoked.authenticatedPeerRoutes.none { it.peerId == first.id })
        val late = revoked.copy(authenticatedPeerRoutes = revoked.authenticatedPeerRoutes + route(first))
        assertEquals(listOf(second), resolveVerifiedDirectParticipants(ownerConnection, late))
        val lanOnly = revoked.copy(authenticatedPeerRoutes = revoked.authenticatedPeerRoutes + route(first, "172.20.3.1", 400L))
        assertEquals(listOf(second), resolveVerifiedDirectParticipants(ownerConnection, lanOnly))
        val reconnected = revoked.copy(authenticatedPeerRoutes = revoked.authenticatedPeerRoutes + route(first, time = 400L))
        assertEquals(listOf(first, second), resolveVerifiedDirectParticipants(ownerConnection, reconnected))
    }

    @Test fun newNoiseIdentityCannotInheritPreviousRouteEvidence() {
        val changed = state().copy(trustedPeers = state().trustedPeers.map {
            if (it.id == first.id) it.copy(noiseStaticKey = "replacement-key") else it
        })
        assertEquals(listOf(second), resolveVerifiedDirectParticipants(ownerConnection, changed))
        rejected { requireDirectRelayTargets(changed, route(first)) }
    }

    @Test fun reassigningPeerOrAddressRemovesConflictingRoutes() {
        assertTrue(removeReassignedPeerRoutes(state().authenticatedPeerRoutes, first.id, lan.ip).none {
            it.peerId == first.id || it.peerIp == lan.ip
        })
        assertTrue(removeReassignedPeerRoutes(state().authenticatedPeerRoutes, "replacement-id", first.ip).none { it.peerIp == first.ip })
    }

}
