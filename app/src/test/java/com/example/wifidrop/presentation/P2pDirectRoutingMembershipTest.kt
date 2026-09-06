package com.example.wifidrop.presentation

import com.example.wifidrop.ChatChannel
import com.example.wifidrop.ConnectionMode
import com.example.wifidrop.ConnectionSnapshot
import com.example.wifidrop.ConnectionViewMode
import com.example.wifidrop.KnownPeerSnapshot
import com.example.wifidrop.TransferRuntimeState
import com.example.wifidrop.WifiDirectState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pDirectRoutingMembershipTest {
    private val owner = "192.168.49.1"
    private val lanPeer = KnownPeerSnapshot("pc-lan", "PC in LAN", "192.168.1.8", true, true, System.currentTimeMillis())

    private fun routing(isOwner: Boolean): P2pRoutingState = resolveP2pRouting(P2pRoutingInput(
        wifiState = WifiDirectState(connection = ConnectionSnapshot(true, isOwner, owner)),
        transferState = TransferRuntimeState(knownPeers = listOf(lanPeer), lastPeerIp = lanPeer.ip),
        lanConnected = true,
        activeConnectionMode = ConnectionMode.WIFI_DIRECT,
        connectionViewMode = ConnectionViewMode.WIFI_DIRECT,
        manualTargetIp = "",
        chatDirectLanTargetIp = null,
        chatDirectWifiTargetIps = listOf(lanPeer.ip)
    ))

    @Test
    fun ownerWithoutCurrentGroupEvidenceDoesNotChooseTheMostRecentLanPeer() {
        val routing = routing(isOwner = true)
        assertNull(routing.targets.directTarget)
        assertNull(routing.targets.resolvedTarget)
        assertTrue(routing.targets.chatDirectAvailablePeers.isEmpty())
        assertTrue(routing.targets.chatDirectTargets.isEmpty())
        assertTrue(routing.directChannelTargets.isEmpty())
    }

    @Test
    fun advancedDirectModeAlsoWaitsForAnAccreditedClientUntilTheUserExplicitlyChoosesAnotherRoute() {
        val routing = resolveP2pRouting(P2pRoutingInput(
            wifiState = WifiDirectState(connection = ConnectionSnapshot(true, true, owner)),
            transferState = TransferRuntimeState(knownPeers = listOf(lanPeer), lastPeerIp = lanPeer.ip),
            lanConnected = true,
            activeConnectionMode = ConnectionMode.WIFI_DIRECT,
            connectionViewMode = ConnectionViewMode.ADVANCED,
            manualTargetIp = "",
            chatDirectLanTargetIp = null,
            chatDirectWifiTargetIps = emptyList()
        ))
        assertNull(routing.targets.resolvedTarget)
    }

    @Test
    fun directMembershipFilteringPreservesIndependentLanChannelTargets() {
        assertEquals(listOf(lanPeer), routing(isOwner = true).globalChatTargets)
    }

    @Test
    fun clientKeepsOnlyTheOfficialOwnerEvenBeforeTheGroupMonitorCatchesUp() {
        val routing = routing(isOwner = false)
        assertEquals(owner, routing.targets.resolvedTarget?.ip)
        assertEquals(listOf(owner), routing.targets.chatDirectAvailablePeers.map { it.ip })
        assertEquals(listOf(owner), routing.directChannelTargets.map { it.ip })
    }

    @Test
    fun staleSelectionRemainsBlockedInsteadOfBeingRedirectedAfterSwitchingToDirect() {
        val direct = routing(isOwner = false).targets.chatDirectAvailablePeers
        val normalized = normalizeChatSelection(P2pChatSelectionInput(
            activeConnectionMode = ConnectionMode.WIFI_DIRECT,
            lanConnected = true,
            wifiState = WifiDirectState(connection = ConnectionSnapshot(true, false, owner)),
            knownPeers = listOf(lanPeer),
            chatChannel = ChatChannel.DIRECT,
            chatDirectLanTargetIp = lanPeer.ip,
            chatDirectWifiTargetIps = listOf(lanPeer.ip),
            directParticipants = direct
        ))
        assertNull(normalized.chatDirectLanTargetIp)
        assertEquals(listOf(lanPeer.ip), normalized.chatDirectWifiTargetIps)
        assertTrue(resolveExplicitDirectChatTargets(normalized.chatDirectWifiTargetIps, direct).isEmpty())
    }

    @Test
    fun hostChatKeepsTheIntendedSelectionWhileNoMembersAreAccredited() {
        val normalized = normalizeChatSelection(P2pChatSelectionInput(
            activeConnectionMode = ConnectionMode.WIFI_DIRECT,
            lanConnected = true,
            wifiState = WifiDirectState(connection = ConnectionSnapshot(true, true, owner)),
            knownPeers = listOf(lanPeer),
            chatChannel = ChatChannel.DIRECT,
            chatDirectLanTargetIp = null,
            chatDirectWifiTargetIps = listOf(lanPeer.ip),
            directParticipants = emptyList()
        ))
        assertEquals(listOf(lanPeer.ip), normalized.chatDirectWifiTargetIps)
        assertTrue(resolveExplicitDirectChatTargets(normalized.chatDirectWifiTargetIps, emptyList()).isEmpty())
    }

    @Test
    fun aMissingSelectedMemberCannotBeReplacedByTheOnlyOtherAvailableMember() {
        val other = KnownPeerSnapshot("b", "Other Android", "192.168.49.3", true, false, 10L)
        val normalized = normalizeChatSelection(P2pChatSelectionInput(
            activeConnectionMode = ConnectionMode.WIFI_DIRECT, lanConnected = false,
            wifiState = WifiDirectState(connection = ConnectionSnapshot(true, true, owner)),
            knownPeers = listOf(other), chatChannel = ChatChannel.DIRECT, chatDirectLanTargetIp = null,
            chatDirectWifiTargetIps = listOf("192.168.49.2"), directParticipants = listOf(other)
        ))
        assertEquals(listOf("192.168.49.2"), normalized.chatDirectWifiTargetIps)
        assertTrue(resolveExplicitDirectChatTargets(normalized.chatDirectWifiTargetIps, listOf(other)).isEmpty())
    }

    @Test
    fun partiallyMissingRecipientsDoNotSilentlyBecomeASmallerAudience() {
        val a = KnownPeerSnapshot("a", "A", "192.168.49.2", true, false, 10L)
        val b = KnownPeerSnapshot("b", "B", "192.168.49.3", true, false, 10L)
        assertTrue(resolveExplicitDirectChatTargets(listOf(a.ip, b.ip), listOf(b)).isEmpty())
        assertEquals(listOf(a.ip, b.ip), resolveExplicitDirectChatTargets(listOf(a.ip, b.ip), listOf(a, b)).map { it.ip })
    }

    @Test
    fun fileRecipientStaysSelectedEvenIfAnotherMemberBecomesTheMostRecent() {
        val a = KnownPeerSnapshot("a", "A", "192.168.49.2", true, false, 1L)
        val b = KnownPeerSnapshot("b", "B", "192.168.49.3", true, false, 999L)
        assertEquals(a, resolveSelectedDirectFilePeer(a.ip, listOf(b, a)))
        assertNull(resolveSelectedDirectFilePeer(a.ip, listOf(b)))
    }

    @Test
    fun firstFileRecipientMayBeInferredOnlyForAnUnambiguousGroup() {
        val a = KnownPeerSnapshot("a", "A", "192.168.49.2", true, false, 1L)
        val b = KnownPeerSnapshot("b", "B", "192.168.49.3", true, false, 2L)
        assertEquals(a, resolveSelectedDirectFilePeer(null, listOf(a)))
        assertNull(resolveSelectedDirectFilePeer(null, listOf(a, b)))
        assertNull(resolveSelectedDirectFilePeer(null, emptyList()))
    }
}
