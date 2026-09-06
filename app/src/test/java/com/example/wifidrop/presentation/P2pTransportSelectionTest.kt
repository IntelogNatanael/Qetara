package com.example.wifidrop.presentation

import com.example.wifidrop.ChatChannel
import com.example.wifidrop.ConnectionMode
import com.example.wifidrop.ConnectionSnapshot
import com.example.wifidrop.ConnectionViewMode
import com.example.wifidrop.KnownPeerSnapshot
import com.example.wifidrop.P2pScreenRouteDerivationInput
import com.example.wifidrop.TransferRuntimeState
import com.example.wifidrop.UxPreferences
import com.example.wifidrop.WifiDirectState
import com.example.wifidrop.adjustedPreferencesForConnectionMode
import com.example.wifidrop.adjustedPreferencesForConnectionViewMode
import com.example.wifidrop.canUseLanTargetSuggestion
import com.example.wifidrop.deriveP2pScreenRouteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pTransportSelectionTest {
    private val directOwner = KnownPeerSnapshot("android-A", "Android A", "192.168.49.1", true, false, 100L)
    private val pc = KnownPeerSnapshot("pc", "PC", "192.168.1.8", true, false, 200L)
    private val connection = ConnectionSnapshot(true, false, directOwner.ip)

    private fun input(
        mode: ConnectionMode = ConnectionMode.WIFI_DIRECT,
        view: ConnectionViewMode = ConnectionViewMode.WIFI_DIRECT,
        manual: String = pc.ip,
        connectedDirect: Boolean = true,
        peers: List<KnownPeerSnapshot> = listOf(directOwner, pc),
        localIps: Set<String> = setOf("192.168.1.7"),
        confirmation: P2pSessionConfirmation? = null
    ) = P2pScreenRouteDerivationInput(
        wifiState = WifiDirectState(connection = connection.takeIf { connectedDirect }),
        transferState = TransferRuntimeState(knownPeers = peers, lastPeerIp = peers.firstOrNull()?.ip),
        lanDiscoveryState = P2pLanDiscoveryState(true, "192.168.1.7", localIpv4Addresses = localIps),
        sessionState = P2pSessionState("ABCD1234", "123456", 99_999_999_999_999L,
            localDeviceIdShort = "self", confirmation = confirmation),
        shareImportState = P2pShareImportState(),
        favoriteSuggestion = P2pFavoriteSuggestionState(null, null),
        hasPermission = true,
        sessionExpired = false,
        nowMs = 1_000L,
        targetIpInput = manual,
        chatDraft = "",
        chatChannel = ChatChannel.DIRECT,
        chatDirectLanTargetIp = null,
        chatDirectWifiTargetIps = emptyList(),
        uxPreferences = UxPreferences(activeConnectionMode = mode, connectionViewMode = view),
        receivedFiles = emptyList(),
        localDeviceId = "self"
    )

    @Test
    fun aStalePcAddressCannotOverrideTheDirectDeviceShownOrSentTo() {
        val state = deriveP2pScreenRouteState(input())
        assertEquals(directOwner.ip, state.uiState.resolvedTargetIp)
        assertEquals(state.uiState.resolvedTargetIp, state.outboundInput.resolvedTargetIp)
        assertEquals(state.uiState.directTargetIp, state.outboundInput.resolvedTargetIp)
        assertEquals(ConnectionMode.WIFI_DIRECT, state.uiState.resolvedTargetMode)
    }

    @Test
    fun directViewWithoutALinkDoesNotFallBackToThePreviousLanPc() {
        val state = deriveP2pScreenRouteState(input(connectedDirect = false))
        assertNull(state.uiState.resolvedTargetIp)
        assertNull(state.outboundInput.resolvedTargetIp)
        assertFalse(state.uiState.sessionReady)
    }

    @Test
    fun lanViewKeepsTheSelectedLanTargetEvenWhileADirectGroupExists() {
        val state = deriveP2pScreenRouteState(input(ConnectionMode.LAN, ConnectionViewMode.LAN))
        assertEquals(pc.ip, state.uiState.resolvedTargetIp)
        assertEquals(state.uiState.resolvedTargetIp, state.outboundInput.resolvedTargetIp)
        assertEquals(ConnectionMode.LAN, state.uiState.resolvedTargetMode)
        assertTrue(state.sessionNetworkKey.startsWith("lan:"))
    }

    @Test
    fun advancedViewKeepsExplicitManualDestinationAndUsesItsNetworkForConfirmation() {
        val state = deriveP2pScreenRouteState(input(view = ConnectionViewMode.ADVANCED))
        assertEquals(pc.ip, state.uiState.resolvedTargetIp)
        assertEquals(pc.ip, state.outboundInput.resolvedTargetIp)
        assertTrue(state.sessionNetworkKey.startsWith("lan:"))
    }

    @Test
    fun aPreviousLanConfirmationCannotEnableTheNewDirectDestination() {
        val proof = P2pSessionConfirmation(pc.ip, pc.id, "lan:true:192.168.1.7", "ABCD1234", "123456")
        val state = deriveP2pScreenRouteState(input(confirmation = proof))
        assertFalse(state.uiState.sessionReady)
    }

    @Test
    fun ownIdentityOnAnotherInterfaceAndAllLocalAddressesAreExcluded() {
        val self = KnownPeerSnapshot("self", "This Android", "10.0.2.15", true, false, 900L)
        val localAlias = KnownPeerSnapshot("alias", "Own interface", "10.0.2.16", true, false, 800L)
        val state = deriveP2pScreenRouteState(input(
            ConnectionMode.LAN, ConnectionViewMode.LAN, manual = self.ip,
            peers = listOf(self, localAlias, pc),
            localIps = setOf("10.0.2.16", "192.168.1.7")
        ))
        assertEquals(listOf(pc), state.uiState.knownServicePeers)
        assertEquals("", state.uiState.targetIp)
        assertEquals(pc.ip, state.outboundInput.resolvedTargetIp)
    }

    @Test
    fun selectingADisabledModeEnablesItInOnePreferenceUpdate() {
        val initial = UxPreferences(activeConnectionMode = ConnectionMode.LAN,
            connectionViewMode = ConnectionViewMode.ADVANCED, wifiDirectModeEnabled = false)
        val direct = adjustedPreferencesForConnectionMode(initial, ConnectionMode.WIFI_DIRECT)
        assertTrue(direct.wifiDirectModeEnabled)
        assertEquals(ConnectionMode.WIFI_DIRECT, direct.activeConnectionMode)
        assertEquals(ConnectionViewMode.ADVANCED, direct.connectionViewMode)
        val lan = adjustedPreferencesForConnectionViewMode(direct.copy(lanModeEnabled = false), ConnectionViewMode.LAN)
        assertTrue(lan.lanModeEnabled)
        assertEquals(ConnectionMode.LAN, lan.activeConnectionMode)
        assertEquals(ConnectionViewMode.LAN, lan.connectionViewMode)
    }

    @Test
    fun backgroundLanSuggestionsCannotSelectAReceiverInDirectView() {
        assertFalse(canUseLanTargetSuggestion(ConnectionViewMode.WIFI_DIRECT))
        assertTrue(canUseLanTargetSuggestion(ConnectionViewMode.LAN))
        assertTrue(canUseLanTargetSuggestion(ConnectionViewMode.ADVANCED))
    }
    @Test
    fun manualLanAddressWithoutDiscoveryUsesTheSameReceiverForFilesAndChat() {
        val address = "10.0.2.2"
        val proof = P2pSessionConfirmation(address, null, "lan:true:192.168.1.7", "ABCD1234", "123456")
        val state = deriveP2pScreenRouteState(input(
            ConnectionMode.LAN, ConnectionViewMode.LAN, manual = address,
            peers = emptyList(), confirmation = proof
        ))
        assertEquals(address, state.uiState.chatDirectTargetIp)
        assertEquals(state.uiState.resolvedTargetIp, state.outboundInput.chatDirectTargetIp)
        assertTrue(state.uiState.sessionReady)
        assertTrue(state.uiState.chatSessionReady)
    }

    @Test
    fun selectingAnAddressAloneDoesNotClaimTheChatSessionIsReady() {
        val state = deriveP2pScreenRouteState(input(
            ConnectionMode.LAN, ConnectionViewMode.LAN, manual = "10.0.2.2", peers = emptyList()
        ))
        assertEquals("10.0.2.2", state.uiState.chatDirectTargetIp)
        assertFalse(state.uiState.chatSessionReady)
    }

    @Test
    fun aChatSelectionCannotBorrowTheFileReceiversConfirmation() {
        val proof = P2pSessionConfirmation(pc.ip, pc.id, "lan:true:192.168.1.7", "ABCD1234", "123456")
        val state = deriveP2pScreenRouteState(input(
            ConnectionMode.LAN, ConnectionViewMode.LAN, confirmation = proof
        ).copy(chatDirectLanTargetIp = directOwner.ip))
        assertTrue(state.uiState.sessionReady)
        assertEquals(directOwner.ip, state.outboundInput.chatDirectTargetIp)
        assertFalse(state.uiState.chatSessionReady)
    }

    @Test
    fun manualOwnAddressCannotBecomeAChatTarget() {
        val state = deriveP2pScreenRouteState(input(
            ConnectionMode.LAN, ConnectionViewMode.LAN, manual = "192.168.1.7", peers = emptyList()
        ))
        assertNull(state.uiState.chatDirectTargetIp)
        assertFalse(state.uiState.chatSessionReady)
    }

    @Test
    fun aPersistedClosedSessionKeepsFilesAndChatBlockedDespiteAnOldConfirmation() {
        val proof = P2pSessionConfirmation(pc.ip, pc.id, "lan:true:192.168.1.7", "ABCD1234", "123456")
        val original = input(ConnectionMode.LAN, ConnectionViewMode.LAN, confirmation = proof)
        val closed = original.copy(uxPreferences = original.uxPreferences.copy(sessionEnabled = false))
        val rebuilt = deriveP2pScreenRouteState(closed)
        assertFalse(rebuilt.uiState.sessionEnabled)
        assertFalse(rebuilt.uiState.sessionReady)
        assertFalse(rebuilt.uiState.chatSessionReady)
        assertEquals(pc.ip, rebuilt.uiState.resolvedTargetIp)
        assertEquals(original.shareImportState, closed.shareImportState)
    }

}
