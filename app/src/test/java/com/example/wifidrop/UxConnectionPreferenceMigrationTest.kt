package com.example.wifidrop

import com.example.wifidrop.presentation.P2pRoutingInput
import com.example.wifidrop.presentation.resolveP2pRouting
import org.junit.Assert.*
import org.junit.Test

class UxConnectionPreferenceMigrationTest {
    private fun normalized(preferences: UxPreferences) =
        UxPreferencesStore.prepareForSave(preferences, currentSessionEnabled = false)

    @Test fun migratedLanWithAStaleDirectViewKeepsItsManualReceiver() {
        val saved = normalized(UxPreferences(activeConnectionMode = ConnectionMode.LAN,
            connectionViewMode = ConnectionViewMode.WIFI_DIRECT))
        assertEquals(ConnectionViewMode.LAN, saved.connectionViewMode)
        val target = resolveP2pRouting(P2pRoutingInput(
            wifiState = WifiDirectState(), transferState = TransferRuntimeState(), lanConnected = true,
            activeConnectionMode = saved.activeConnectionMode, connectionViewMode = saved.connectionViewMode,
            manualTargetIp = "10.0.2.2", chatDirectLanTargetIp = "10.0.2.2", chatDirectWifiTargetIps = emptyList()
        )).targets
        assertEquals("10.0.2.2", target.resolvedTarget?.ip)
        assertEquals(target.resolvedTarget, target.chatDirectTarget)
        assertFalse(saved.sessionEnabled)
    }

    @Test fun migratedDirectWithAStaleLanViewCannotResolveAnUnrelatedLanReceiver() {
        val saved = normalized(UxPreferences(activeConnectionMode = ConnectionMode.WIFI_DIRECT,
            connectionViewMode = ConnectionViewMode.LAN))
        assertEquals(ConnectionViewMode.WIFI_DIRECT, saved.connectionViewMode)
        val target = resolveP2pRouting(P2pRoutingInput(
            wifiState = WifiDirectState(), transferState = TransferRuntimeState(), lanConnected = true,
            activeConnectionMode = saved.activeConnectionMode, connectionViewMode = saved.connectionViewMode,
            manualTargetIp = "10.0.2.2", chatDirectLanTargetIp = null, chatDirectWifiTargetIps = emptyList()
        )).targets
        assertNull(target.resolvedTarget)
    }

    @Test fun anExplicitSwitchToDirectIsNotRevertedByTheOldLanPreference() {
        val old = UxPreferences(activeConnectionMode = ConnectionMode.LAN,
            connectionViewMode = ConnectionViewMode.LAN, wifiDirectModeEnabled = false)
        val saved = normalized(adjustedPreferencesForConnectionViewMode(old, ConnectionViewMode.WIFI_DIRECT))
        assertEquals(ConnectionMode.WIFI_DIRECT, saved.activeConnectionMode)
        assertEquals(ConnectionViewMode.WIFI_DIRECT, saved.connectionViewMode)
        assertTrue(saved.wifiDirectModeEnabled)
    }

    @Test fun advancedViewRemainsAdvancedForEitherTransport() {
        ConnectionMode.entries.forEach { mode ->
            val saved = normalized(UxPreferences(activeConnectionMode = mode,
                connectionViewMode = ConnectionViewMode.ADVANCED))
            assertEquals(mode, saved.activeConnectionMode)
            assertEquals(ConnectionViewMode.ADVANCED, saved.connectionViewMode)
        }
    }

    @Test fun aDisabledTransportFallsBackBeforeItsSimpleViewIsAligned() {
        val lan = normalized(UxPreferences(activeConnectionMode = ConnectionMode.WIFI_DIRECT,
            connectionViewMode = ConnectionViewMode.WIFI_DIRECT, wifiDirectModeEnabled = false))
        assertEquals(ConnectionMode.LAN, lan.activeConnectionMode)
        assertEquals(ConnectionViewMode.LAN, lan.connectionViewMode)
        val direct = normalized(UxPreferences(activeConnectionMode = ConnectionMode.LAN,
            connectionViewMode = ConnectionViewMode.LAN, lanModeEnabled = false))
        assertEquals(ConnectionMode.WIFI_DIRECT, direct.activeConnectionMode)
        assertEquals(ConnectionViewMode.WIFI_DIRECT, direct.connectionViewMode)
    }

    @Test fun whenBothTransportsWereDisabledTheChosenTransportIsEnabledWithoutOpeningTheSession() {
        val saved = normalized(UxPreferences(activeConnectionMode = ConnectionMode.LAN,
            connectionViewMode = ConnectionViewMode.WIFI_DIRECT,
            wifiDirectModeEnabled = false, lanModeEnabled = false, sessionEnabled = true))
        assertTrue(saved.lanModeEnabled)
        assertFalse(saved.wifiDirectModeEnabled)
        assertEquals(ConnectionMode.LAN, saved.activeConnectionMode)
        assertEquals(ConnectionViewMode.LAN, saved.connectionViewMode)
        assertFalse(saved.sessionEnabled)
    }
}
