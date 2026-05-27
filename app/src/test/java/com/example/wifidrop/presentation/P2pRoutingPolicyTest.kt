package com.example.wifidrop.presentation

import com.example.wifidrop.ConnectionMode
import com.example.wifidrop.ConnectionSnapshot
import com.example.wifidrop.KnownPeerSnapshot
import com.example.wifidrop.TransferRuntimeState
import com.example.wifidrop.WifiDirectState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class P2pRoutingPolicyTest {

    @Test
    fun lanRoutingPrefersTrustedRecentPeer() {
        val state = resolveP2pRouting(
            P2pRoutingInput(
                wifiState = WifiDirectState(),
                transferState = TransferRuntimeState(
                    knownPeers = listOf(
                        peer("old", "Old", "192.168.1.10", trusted = false, lastSeenAtMs = 200),
                        peer("trusted", "Trusted", "192.168.1.20", trusted = true, lastSeenAtMs = 100),
                        peer("new", "New", "192.168.1.30", trusted = false, lastSeenAtMs = 300)
                    )
                ),
                lanConnected = true,
                activeConnectionMode = ConnectionMode.LAN,
                manualTargetIp = "",
                chatDirectLanTargetIp = null,
                chatDirectWifiTargetIps = emptyList()
            )
        )

        assertEquals("192.168.1.20", state.targets.suggestedTargetIp)
        assertEquals(ConnectionMode.LAN, state.targets.resolvedTarget?.mode)
    }

    @Test
    fun wifiDirectClientTargetsGroupOwner() {
        val state = resolveP2pRouting(
            P2pRoutingInput(
                wifiState = WifiDirectState(
                    connection = ConnectionSnapshot(
                        groupFormed = true,
                        isGroupOwner = false,
                        groupOwnerAddress = "192.168.49.1"
                    )
                ),
                transferState = TransferRuntimeState(),
                lanConnected = false,
                activeConnectionMode = ConnectionMode.WIFI_DIRECT,
                manualTargetIp = "",
                chatDirectLanTargetIp = null,
                chatDirectWifiTargetIps = emptyList()
            )
        )

        assertEquals("192.168.49.1", state.targets.directTarget?.ip)
        assertEquals(ConnectionMode.WIFI_DIRECT, state.targets.resolvedTarget?.mode)
    }

    @Test
    fun manualTargetWinsOverSuggestions() {
        val state = resolveP2pRouting(
            P2pRoutingInput(
                wifiState = WifiDirectState(),
                transferState = TransferRuntimeState(
                    knownPeers = listOf(peer("lan", "PC", "192.168.1.20", trusted = true))
                ),
                lanConnected = true,
                activeConnectionMode = ConnectionMode.LAN,
                manualTargetIp = "192.168.1.99",
                chatDirectLanTargetIp = null,
                chatDirectWifiTargetIps = emptyList()
            )
        )

        assertEquals("192.168.1.99", state.targets.resolvedTarget?.ip)
        assertEquals(ConnectionMode.LAN, state.targets.resolvedTarget?.mode)
    }

    @Test
    fun directChatRequiresExplicitLanPeerToExist() {
        val state = resolveP2pRouting(
            P2pRoutingInput(
                wifiState = WifiDirectState(),
                transferState = TransferRuntimeState(),
                lanConnected = true,
                activeConnectionMode = ConnectionMode.LAN,
                manualTargetIp = "",
                chatDirectLanTargetIp = "192.168.1.50",
                chatDirectWifiTargetIps = emptyList()
            )
        )

        assertNull(state.targets.chatDirectTarget)
    }

    private fun peer(
        id: String,
        label: String,
        ip: String,
        trusted: Boolean,
        lastSeenAtMs: Long = 0
    ) = KnownPeerSnapshot(
        id = id,
        label = label,
        ip = ip,
        trusted = trusted,
        globalLanJoined = true,
        lastSeenAtMs = lastSeenAtMs
    )
}
