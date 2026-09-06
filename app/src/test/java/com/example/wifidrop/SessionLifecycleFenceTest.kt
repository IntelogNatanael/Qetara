package com.example.wifidrop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class SessionLifecycleFenceTest {
    @Test fun oldStopCannotTerminateLaterStart() {
        val fence = SessionLifecycleFence()
        fence.recordCommand(10)
        fence.beginStart()
        fence.recordCommand(11)
        val stop = fence.beginStop()
        assertEquals(11, fence.stopStartId(stop))
        fence.recordCommand(12)
        val restart = fence.beginStart()
        assertNull(fence.stopStartId(stop))
        assertTrue(fence.canActivate(restart))
    }

    @Test fun newerCommandInvalidatesPreviousFrameworkStopId() {
        val fence = SessionLifecycleFence()
        fence.recordCommand(4)
        val stop = fence.beginStop()
        assertEquals(4, fence.stopStartId(stop))
        fence.recordCommand(5) // A newer request must not be stopped by the old close operation.
        assertNull(fence.stopStartId(stop))
        fence.recordCommand(3) // An older ID must not restore that expired stop permission.
        assertNull(fence.stopStartId(stop))
    }

    @Test fun rapidRestartWaitsForCancelledWorkerCallbacksBeforePublishingNewSession() = runBlocking {
        withTimeout(3000L) {
            val fence = SessionLifecycleFence()
            val entered = CompletableDeferred<Unit>()
            val releaseCleanup = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val oldWorker = launch {
                try { entered.complete(Unit); awaitCancellation() }
                finally { withContext(NonCancellable) {
                    releaseCleanup.await()
                    events += "old callback completed"
                } }
            }
            entered.await()
            fence.recordCommand(1)
            val stop = fence.beginStop()
            oldWorker.cancel()
            fence.recordCommand(2)
            val restart = fence.beginStart()
            val activation = async {
                val allowed = awaitSessionWorkers(listOf(oldWorker), fence, restart)
                if (allowed) events += "new session published"
                allowed
            }
            yield()
            assertFalse(activation.isCompleted)
            assertNull(fence.stopStartId(stop))
            releaseCleanup.complete(Unit)
            assertTrue(activation.await())
            assertEquals(listOf("old callback completed", "new session published"), events)
        }
    }

    @Test fun closingAgainWhileDrainWaitsPreventsObsoleteActivation() = runBlocking {
        withTimeout(3000L) {
            val fence = SessionLifecycleFence()
            val entered = CompletableDeferred<Unit>()
            val releaseCleanup = CompletableDeferred<Unit>()
            val worker = launch {
                try { entered.complete(Unit); awaitCancellation() }
                finally { withContext(NonCancellable) { releaseCleanup.await() } }
            }
            entered.await()
            worker.cancel()
            fence.recordCommand(3)
            val start = fence.beginStart()
            val activation = async { awaitSessionWorkers(listOf(worker), fence, start) }
            yield()
            fence.recordCommand(4)
            val latestStop = fence.beginStop()
            releaseCleanup.complete(Unit)
            assertFalse(activation.await())
            assertEquals(4, fence.stopStartId(latestStop))
        }
    }
}
