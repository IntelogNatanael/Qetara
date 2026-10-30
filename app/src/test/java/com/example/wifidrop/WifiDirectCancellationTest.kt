package com.example.wifidrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiDirectCancellationTest {
    private fun requestedActions(state: WifiDirectState): List<String> {
        val actions = mutableListOf<String>()
        runWifiDirectCancellation(
            state,
            removeGroup = { actions += "remove group" },
            stopDiscovery = { actions += "stop discovery" },
            cancelConnect = { actions += "cancel negotiation" }
        )
        return actions
    }

    @Test
    fun stopSearchRequestsDiscoveryStopInsteadOfCancellingANonexistentNegotiation() {
        val searching = WifiDirectState(discoveringPeers = true, statusMessage = "Searching")

        assertEquals(listOf("stop discovery"), requestedActions(searching))
    }

    @Test
    fun groupAndNegotiationCancellationKeepTheirExistingActions() {
        assertEquals(listOf("remove group"), requestedActions(WifiDirectState(
            connection = ConnectionSnapshot(true, true, "192.168.49.1"),
            discoveringPeers = true,
            statusMessage = "Group active"
        )))
        assertEquals(listOf("remove group"), requestedActions(WifiDirectState(
            creatingGroup = true, statusMessage = "Creating group"
        )))
        assertEquals(listOf("cancel negotiation"), requestedActions(WifiDirectState(
            connectingToPeer = true, statusMessage = "Joining"
        )))
    }

    @Test
    fun lateDiscoveryStartCannotUndoACompletedStop() {
        val searching = WifiDirectState(discoveringPeers = true, statusMessage = "Searching")
        val stopped = reconcileWifiDirectDiscoveryResult(searching, 2L, 2L, false, "Stopped")
        val lateStart = reconcileWifiDirectDiscoveryResult(stopped, 2L, 1L, true, "Searching")

        assertFalse(lateStart.discoveringPeers)
        assertEquals(stopped, lateStart)
    }

    @Test
    fun lateStopCannotClearANewerDiscoveryOrItsStatus() {
        val stopped = WifiDirectState(statusMessage = "Stopped")
        val restarted = reconcileWifiDirectDiscoveryResult(stopped, 3L, 3L, true, "Searching again")
        val lateStop = reconcileWifiDirectDiscoveryResult(restarted, 3L, 2L, false, "Stopped")

        assertTrue(lateStop.discoveringPeers)
        assertEquals(restarted, lateStop)
    }
}
