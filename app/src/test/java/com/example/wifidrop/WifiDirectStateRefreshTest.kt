package com.example.wifidrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiDirectStateRefreshTest {
    @Test
    fun frameworkEnabledObservationRecoversMissingBroadcastWithoutInventingAGroup() {
        val before = WifiDirectState(statusMessage = "initial state")

        val refreshed = reconcileP2pEnabledState(before, enabled = true, statusMessage = "P2P enabled")

        assertTrue(refreshed.p2pEnabled)
        assertNull(refreshed.connection)
        assertNull(refreshed.group)
        assertTrue(refreshed.peers.isEmpty())
        assertFalse(refreshed.connectingToPeer)
    }

    @Test
    fun repeatedEnabledObservationPreservesSearchConnectionAndGroupCreationProgress() {
        val operations = listOf(
            WifiDirectState(p2pEnabled = true, discoveringPeers = true, statusMessage = "Searching"),
            WifiDirectState(p2pEnabled = true, connectingToPeer = true, statusMessage = "Joining"),
            WifiDirectState(p2pEnabled = true, creatingGroup = true, statusMessage = "Creating group")
        )

        operations.forEach { active ->
            val refreshed = reconcileP2pEnabledState(active, enabled = true, statusMessage = "P2P enabled")
            assertEquals(active, refreshed)
        }
    }

    @Test
    fun disabledObservationClearsConnectionAndOperationsWithoutChangingDeviceIdentity() {
        val active = WifiDirectState(
            p2pEnabled = true,
            thisDeviceName = "This Android",
            thisDeviceAddress = "02:00:00:00:00:01",
            peers = listOf(PeerDevice("Other Android", "02:00:00:00:00:02", 0)),
            connection = ConnectionSnapshot(true, true, "192.168.49.1"),
            group = GroupSnapshot(null, null, "02:00:00:00:00:01", emptyList()),
            discoveringPeers = true,
            connectingToPeer = true,
            creatingGroup = true,
            statusMessage = "Group active"
        )

        val refreshed = reconcileP2pEnabledState(active, enabled = false, statusMessage = "P2P disabled")

        assertFalse(refreshed.p2pEnabled)
        assertTrue(refreshed.peers.isEmpty())
        assertNull(refreshed.connection)
        assertNull(refreshed.group)
        assertFalse(refreshed.discoveringPeers)
        assertFalse(refreshed.connectingToPeer)
        assertFalse(refreshed.creatingGroup)
        assertEquals(active.thisDeviceName, refreshed.thisDeviceName)
        assertEquals(active.thisDeviceAddress, refreshed.thisDeviceAddress)
    }
}
