package com.example.wifidrop.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pSessionSyncCoordinatorTest {
    @Test
    fun manualSelectionAndDiscoveryMakeOnlyOneConcurrentRequest() = runBlocking {
        val coordinator = P2pSessionSyncCoordinator()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var requests = 0
        val first = launch {
            coordinator.syncOnce {
                requests += 1
                started.complete(Unit)
                finish.await()
                true
            }
        }
        started.await()
        assertFalse(coordinator.syncOnce { requests += 1; true })
        assertEquals(1, requests)
        finish.complete(Unit)
        first.join()
        assertTrue(coordinator.syncOnce { requests += 1; true })
        assertEquals(2, requests)
    }

    @Test
    fun leavingAConnectionDuringARequestDoesNotBlockTheNextConnection() = runBlocking {
        val coordinator = P2pSessionSyncCoordinator()
        val started = CompletableDeferred<Unit>()
        val neverFinished = CompletableDeferred<Unit>()
        val request = launch {
            coordinator.syncOnce {
                started.complete(Unit)
                neverFinished.await()
                true
            }
        }
        started.await()
        request.cancelAndJoin()
        assertTrue(coordinator.syncOnce { true })
    }

    @Test
    fun failedRequestDoesNotLeaveSynchronizationLocked() = runBlocking {
        val coordinator = P2pSessionSyncCoordinator()
        runCatching { coordinator.syncOnce { error("Connection closed") } }
        assertTrue(coordinator.syncOnce { true })
    }

    @Test
    fun aChangedDirectGroupNeedsExplicitRecoveryInsteadOfRepeatedSessionRequests() {
        assertTrue(sessionSyncRequiresUserAction("grupo_direct_no_acreditado"))
        assertTrue(sessionSyncRequiresUserAction("grupo_direct_renovado"))
        assertTrue(sessionSyncRequiresUserAction("destino_fuera_grupo_direct"))
    }

    @Test
    fun manualPairingAndChangedIdentityKeepTheirRecoveryInstructions() {
        assertTrue(sessionSyncRequiresUserAction("emparejamiento_manual_requerido"))
        assertTrue(sessionSyncRequiresUserAction("secure_credentials_required"))
        assertTrue(sessionSyncRequiresUserAction("noise_key_mismatch"))
        assertFalse(sessionSyncRequiresUserAction("Connection timed out"))
        assertFalse(sessionSyncRequiresUserAction("confirmacion_host_requerida"))
    }
    @Test
    fun rebuildingDiscoveryDuringManualCredentialEditingDoesNotStartAnotherRequest() = runBlocking {
        val coordinator = P2pSessionSyncCoordinator()
        val endpoint = P2pSessionSyncEndpoint("lan:true:192.168.1.5", "192.168.1.8")
        var requests = 0
        var syncingStarts = 0
        suspend fun request() = coordinator.syncEndpointOnce(endpoint, manual = false) {
            requests += 1
            syncingStarts += 1
            P2pSessionSyncAttempt(synced = false, requiresUserAction = true)
        }
        assertTrue(request().requiresUserAction)
        // Each new loop has the same route, even if discovery lost/recovered the
        // peer ID or the edited code temporarily has fewer than four characters.
        repeat(4) { assertTrue(request().requiresUserAction) }
        assertEquals(1, requests)
        assertEquals(1, syncingStarts)
    }

    @Test
    fun explicitRetryCanRecoverAnEndpointThatNeedsManualAction() = runBlocking {
        val coordinator = P2pSessionSyncCoordinator()
        val endpoint = P2pSessionSyncEndpoint("lan:true:192.168.1.5", "192.168.1.8")
        coordinator.syncEndpointOnce(endpoint, manual = false) {
            P2pSessionSyncAttempt(synced = false, requiresUserAction = true)
        }
        var requests = 0
        assertTrue(coordinator.syncEndpointOnce(endpoint, manual = true) {
            requests += 1
            P2pSessionSyncAttempt(synced = true)
        }.synced)
        assertTrue(coordinator.syncEndpointOnce(endpoint, manual = false) {
            requests += 1
            P2pSessionSyncAttempt(synced = true)
        }.synced)
        assertEquals(2, requests)
    }

    @Test
    fun differentAddressNetworkOrTransportAllowsAFirstAutomaticRequest() = runBlocking {
        val coordinator = P2pSessionSyncCoordinator()
        val endpoints = listOf(
            P2pSessionSyncEndpoint("lan:true:192.168.1.5", "192.168.1.8"),
            P2pSessionSyncEndpoint("lan:true:192.168.1.5", "192.168.1.9"),
            P2pSessionSyncEndpoint("lan:true:192.168.2.5", "192.168.1.9"),
            P2pSessionSyncEndpoint("direct:true:192.168.1.9:false", "192.168.1.9")
        )
        var requests = 0
        endpoints.forEach { endpoint ->
            val attempt = coordinator.syncEndpointOnce(endpoint, manual = false) {
                requests += 1
                P2pSessionSyncAttempt(synced = false, requiresUserAction = true)
            }
            assertTrue(attempt.requiresUserAction)
        }
        assertEquals(endpoints.size, requests)
    }

    @Test
    fun transientNetworkFailureKeepsAutomaticRecoveryAvailable() = runBlocking {
        val coordinator = P2pSessionSyncCoordinator()
        val endpoint = P2pSessionSyncEndpoint("lan:true:192.168.1.5", "192.168.1.8")
        var requests = 0
        assertFalse(coordinator.syncEndpointOnce(endpoint, manual = false) {
            requests += 1
            P2pSessionSyncAttempt(synced = false)
        }.synced)
        assertTrue(coordinator.syncEndpointOnce(endpoint, manual = false) {
            requests += 1
            P2pSessionSyncAttempt(synced = true)
        }.synced)
        assertEquals(2, requests)
    }

}
