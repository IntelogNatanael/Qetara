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
        assertFalse(paused.toggled(isResumed = false, hasWindowFocus = true).revealed)
        assertFalse(beforePause.toggled(isResumed = false, hasWindowFocus = true).revealed)
        assertTrue(paused.toggled(isResumed = true, hasWindowFocus = true).revealed)
    }

    @Test
    fun losingAndRegainingWindowFocusDoesNotRestoreRevealWithoutPausing() {
        val revealed = P2pPinVisibilityState(session, "123456", revealed = true)
        val unfocused = revealed.forWindowFocus(hasWindowFocus = false)
        val returned = unfocused.forWindowFocus(hasWindowFocus = true)
        assertFalse(unfocused.revealed)
        assertFalse(returned.revealed)
        assertEquals(session, returned.session)
        assertEquals("123456", returned.observedPin)
        // Returning focus requires an explicit reveal; an ordinary focus notification preserves it.
        val explicitlyShown = returned.toggled(isResumed = true, hasWindowFocus = true)
        assertTrue(explicitlyShown.forWindowFocus(hasWindowFocus = true).revealed)
    }

    @Test
    fun aLateCallbackCannotRevealWhileResumedWithoutWindowFocus() {
        for (previouslyRevealed in listOf(false, true)) {
            val state = P2pPinVisibilityState(session, "123456", revealed = previouslyRevealed)
            val afterCallback = state.toggled(isResumed = true, hasWindowFocus = false)
            assertFalse(afterCallback.revealed)
            assertFalse(afterCallback.forWindowFocus(hasWindowFocus = true).revealed)
        }
    }
}
