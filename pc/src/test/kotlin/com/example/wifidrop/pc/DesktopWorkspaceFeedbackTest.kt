package com.example.wifidrop.pc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopWorkspaceFeedbackTest {
    @Test
    fun collapsedDiscoveryKeepsASelectedDestinationBeyondTheFirstEight() {
        val peers = (1..10).map(::peer)
        val visible = desktopVisiblePeers(peers, peers.last().ip, expanded = false)
        assertEquals(8, visible.size)
        assertEquals(peers.take(7), visible.take(7))
        assertTrue(peers.last() in visible)
        assertEquals(peers, desktopVisiblePeers(peers, peers.last().ip, expanded = true))
    }

    @Test
    fun manualDestinationDoesNotFabricateADiscoveredPeer() {
        val peers = (1..10).map(::peer)
        assertEquals(peers.take(8), desktopVisiblePeers(peers, "192.0.2.200", expanded = false))
        assertEquals(peers.take(2), desktopVisiblePeers(peers.take(2), "", expanded = false))
    }

    @Test
    fun failedSendRetainsItsErrorAndExplainsTheCurrentRetryBlocker() {
        val error = "El equipo no responde."
        val issue = "Busca un equipo receptor o escribe su IP."
        assertEquals(
            DesktopSendFeedback(error, issue),
            desktopSendFeedback(DesktopTaskPhase.ERROR, error, listOf(issue))
        )
        assertNull(desktopSendFeedback(DesktopTaskPhase.ERROR, error, listOf(error)).nextIssue)
        assertNull(desktopSendFeedback(DesktopTaskPhase.ERROR, error, emptyList()).nextIssue)
    }

    @Test
    fun activeTransferShowsProgressUntilItHasStopped() {
        val issue = "Selecciona al menos un archivo."
        for (phase in listOf(DesktopTaskPhase.STARTING, DesktopTaskPhase.RUNNING, DesktopTaskPhase.STOPPING)) {
            assertEquals(DesktopSendFeedback("En curso"), desktopSendFeedback(phase, "En curso", listOf(issue)))
        }
        assertEquals(DesktopSendFeedback(issue), desktopSendFeedback(DesktopTaskPhase.IDLE, "Listo", listOf(issue)))
    }

    private fun peer(index: Int) = DesktopLanPeer(
        "peer-$index", "Equipo $index", "192.0.2.$index", true, true, false, 0L
    )
}
