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
}
