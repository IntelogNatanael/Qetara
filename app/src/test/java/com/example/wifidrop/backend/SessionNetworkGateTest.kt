package com.example.wifidrop.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class SessionNetworkGateTest {
    @Test fun closedSessionRejectsNetworkWorkUntilExplicitActivation() = runBlocking {
        var enabled = false
        val gate = SessionNetworkGate({ enabled }, { enabled = it })
        var started = 0
        val rejected = gate.run { started++; Result.success("unexpected") }
        assertEquals("sesion_cerrada", rejected.exceptionOrNull()?.message)
        assertEquals(0, started)
        gate.setEnabled(true)
        assertEquals("allowed", gate.run { started++; Result.success("allowed") }.getOrThrow())
        assertEquals(1, started)
    }

    @Test fun closingCancelsAllExistingRequestsAndRejectsNewOnes() = runBlocking {
        withTimeout(2000L) {
            var enabled = true
            val gate = SessionNetworkGate({ enabled }, { enabled = it })
            val firstStarted = CompletableDeferred<Unit>()
            val secondStarted = CompletableDeferred<Unit>()
            val first = async { gate.run<Unit> { firstStarted.complete(Unit); awaitCancellation() } }
            val second = async { gate.run<Unit> { secondStarted.complete(Unit); awaitCancellation() } }
            firstStarted.await()
            secondStarted.await()
            gate.setEnabled(false)
            first.join()
            second.join()
            assertTrue(first.isCancelled)
            assertTrue(second.isCancelled)
            assertFalse(enabled)
            assertTrue(gate.run { Result.success(Unit) }.isFailure)
        }
    }

    @Test fun cancelledRequestCannotPublishSuccessEvenIfTransportCatchesCancellation() = runBlocking {
        withTimeout(2000L) {
            var enabled = true
            val gate = SessionNetworkGate({ enabled }, { enabled = it })
            val started = CompletableDeferred<Unit>()
            var published = false
            val worker = async {
                gate.run {
                    started.complete(Unit)
                    try { awaitCancellation() } catch (_: CancellationException) { Result.success("late") }
                }.onSuccess { published = true }
            }
            started.await()
            gate.setEnabled(false)
            worker.join()
            assertTrue(worker.isCancelled)
            assertFalse(published)
            gate.setEnabled(true)
            assertTrue(gate.run { Result.success("new") }.isSuccess)
            assertFalse(published)
        }
    }
}
