package com.example.wifidrop

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SessionWorkerAdmissionTest {
    @Test fun queuedCallerThatPassedOuterCheckCannotCreateWorkerAfterStopSnapshot() {
        // Exercise the same admission boundary for each independent queue lock.
        listOf(Any(), Any()).forEach { queueLock ->
            val closing = AtomicBoolean(false)
            val passedOuterCheck = CountDownLatch(1)
            val bodyCalls = AtomicInteger()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val executor = Executors.newSingleThreadExecutor()
            try {
                val caller = synchronized(queueLock) {
                    val pending = executor.submit(Callable<Job?> {
                        if (closing.get()) return@Callable null
                        passedOuterCheck.countDown()
                        withSessionWorkerAdmission(queueLock, closing::get) {
                            scope.launch(start = CoroutineStart.LAZY) { bodyCalls.incrementAndGet() }
                        }
                    })
                    assertTrue(passedOuterCheck.await(3, TimeUnit.SECONDS))
                    closing.set(true)
                    assertTrue(scope.coroutineContext[Job]!!.children.toList().isEmpty())
                    pending
                }
                assertNull(caller.get(3, TimeUnit.SECONDS))
                assertTrue(scope.coroutineContext[Job]!!.children.toList().isEmpty())
                assertEquals(0, bodyCalls.get())
            } finally {
                scope.cancel()
                executor.shutdownNow()
            }
        }
    }

    @Test fun stopWaitsForAdmissionAndDrainsLazyWorkerWithItsRegisteredCallback() {
        val queueLock = Any()
        val closing = AtomicBoolean(false)
        val registered = CountDownLatch(1)
        val releaseAdmission = CountDownLatch(1)
        val stopAttempted = CountDownLatch(1)
        val bodyCalls = AtomicInteger()
        val completionCalls = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val creation = executor.submit(Callable<Job?> {
                withSessionWorkerAdmission(queueLock, closing::get) {
                    scope.launch(start = CoroutineStart.LAZY) { bodyCalls.incrementAndGet() }.also {
                        it.invokeOnCompletion { completionCalls.incrementAndGet() }
                        registered.countDown()
                        check(releaseAdmission.await(3, TimeUnit.SECONDS))
                    }
                }
            })
            assertTrue(registered.await(3, TimeUnit.SECONDS))
            val stop = executor.submit(Callable<List<Job>> {
                closing.set(true)
                stopAttempted.countDown()
                val children = synchronized(queueLock) {
                    scope.coroutineContext[Job]!!.children.toList()
                }
                children.forEach { it.cancel() }
                runBlocking { children.forEach { it.join() } }
                children
            })
            assertTrue(stopAttempted.await(3, TimeUnit.SECONDS))
            releaseAdmission.countDown()
            val admitted = creation.get(3, TimeUnit.SECONDS)!!
            assertEquals(listOf(admitted), stop.get(3, TimeUnit.SECONDS))
            assertEquals(1, completionCalls.get())
            assertFalse(admitted.start())
            assertEquals(0, bodyCalls.get())
            assertTrue(scope.coroutineContext[Job]!!.children.toList().isEmpty())
        } finally {
            releaseAdmission.countDown()
            scope.cancel()
            executor.shutdownNow()
        }
    }
}
