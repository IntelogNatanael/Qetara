package com.example.wifidrop.presentation

import com.example.wifidrop.ConnectionSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pSessionControlPolicyTest {
    private fun input(lan: Boolean, direct: Boolean, enabled: Boolean) = P2pSessionServiceInput(
        permissionGranted = true,
        lanConnected = lan,
        connection = ConnectionSnapshot(true, true, "192.168.49.1").takeIf { direct },
        token = "ABCD1234",
        pin = "123456",
        sessionExpired = false,
        sessionEnabled = enabled
    )

    @Test
    fun aClosedSessionDoesNotRestartWhenEitherNetworkBecomesAvailable() {
        for (lan in listOf(false, true)) {
            for (direct in listOf(false, true)) {
                assertFalse(shouldStartBackendSession(input(lan, direct, enabled = false)))
            }
        }
    }

    @Test
    fun anExplicitReactivationAllowsAValidConnectedSession() {
        assertTrue(shouldStartBackendSession(input(lan = true, direct = false, enabled = true)))
        assertTrue(shouldStartBackendSession(input(lan = false, direct = true, enabled = true)))
    }

    @Test
    fun enablingTheSessionStillRequiresNetworkAndValidCredentials() {
        assertFalse(shouldStartBackendSession(input(lan = false, direct = false, enabled = true)))
        assertFalse(shouldStartBackendSession(input(lan = true, direct = false, enabled = true).copy(sessionExpired = true)))
        assertFalse(shouldStartBackendSession(input(lan = true, direct = false, enabled = true).copy(pin = "")))
    }
    @Test
    fun anAlreadyStoppedSessionDoesNotCreateAServiceJustToStopIt() {
        val closed = input(lan = true, direct = false, enabled = false)
        org.junit.Assert.assertEquals(P2pSessionServiceAction.NONE, resolveSessionServiceAction(closed, serviceRunning = false))
        org.junit.Assert.assertEquals(P2pSessionServiceAction.STOP, resolveSessionServiceAction(closed, serviceRunning = true))
    }

    @Test
    fun anExplicitActivationIsReconciledAgainAfterTheOldServiceDisappears() {
        val active = input(lan = true, direct = false, enabled = true)
        org.junit.Assert.assertEquals(P2pSessionServiceAction.START, resolveSessionServiceAction(active, serviceRunning = true))
        org.junit.Assert.assertEquals(P2pSessionServiceAction.START, resolveSessionServiceAction(active, serviceRunning = false))
    }

    @Test
    fun losingTheNetworkStopsOnceAndDoesNotRecreateAnIdleService() {
        val disconnected = input(lan = false, direct = false, enabled = true)
        org.junit.Assert.assertEquals(P2pSessionServiceAction.STOP, resolveSessionServiceAction(disconnected, serviceRunning = true))
        org.junit.Assert.assertEquals(P2pSessionServiceAction.NONE, resolveSessionServiceAction(disconnected, serviceRunning = false))
    }

}
