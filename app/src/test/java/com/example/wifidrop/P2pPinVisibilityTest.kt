package com.example.wifidrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pPinVisibilityTest {
    private val session = P2pPinSessionKey("SYNTHETIC", 30_000L, ConnectionMode.LAN, "192.0.2.10", true, false, false)

    @Test
    fun aNewFieldAndABackgroundedFieldAlwaysConcealThePin() {
        assertFalse(P2pPinVisibilityState(session, "123456").revealed)
        val revealed = P2pPinVisibilityState(session, "123456", revealed = true)
        assertFalse(revealed.hidden().forSession(session, "123456").revealed)
        assertEquals("123456", revealed.hidden().observedPin)
    }

    @Test
    fun editingTheSamePinKeepsTheExplicitRevealChoice() {
        val revealed = P2pPinVisibilityState(session, "123456", revealed = true)
        val edited = revealed.edited("12345")
        assertTrue(edited.forSession(session, "12345").revealed)
        assertEquals("12345", edited.observedPin)
        assertFalse(revealed.hidden().edited("12345").forSession(session, "12345").revealed)
    }

    @Test
    fun aProgrammaticallyReplacedPinDoesNotInheritTheRevealChoice() {
        val revealed = P2pPinVisibilityState(session, "123456", revealed = true)
        val replaced = revealed.forSession(session, "654321")
        assertFalse(replaced.revealed)
        assertFalse(replaced.forSession(session, "123456").revealed)
    }

    @Test
    fun renewalRecipientTransportAndSessionStateChangesConcealThePin() {
        val revealed = P2pPinVisibilityState(session, "123456", revealed = true)
        for (changed in listOf(
            session.copy(token = "NEWCODE"),
            session.copy(expiresAtMs = 60_000L),
            session.copy(targetIp = "192.0.2.11"),
            session.copy(mode = ConnectionMode.WIFI_DIRECT),
            session.copy(enabled = false),
            session.copy(expired = true),
            session.copy(syncing = true)
        )) {
            assertFalse(revealed.forSession(changed, "123456").revealed)
        }
    }
    @Test
    fun aLateRevealCallbackCannotUndoPauseConcealment() {
        val beforePause = P2pPinVisibilityState(session, "123456", revealed = true)
        val paused = beforePause.hidden()
        assertFalse(paused.toggled(isResumed = false).revealed)
        assertFalse(beforePause.toggled(isResumed = false).revealed)
        assertTrue(paused.toggled(isResumed = true).revealed)
    }
}
