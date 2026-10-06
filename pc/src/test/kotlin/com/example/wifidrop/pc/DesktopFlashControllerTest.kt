package com.example.wifidrop.pc

import com.example.wifidrop.protocol.flash.*
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopFlashControllerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun activationFindsReceiversWithoutAManualSearch() {
        Fixture().use { fixture ->
            assertEquals(1, fixture.transport.discoverCalls.get())
            assertEquals(listOf(fixture.transport.peer), fixture.state().session.peers)
        }
    }

    @Test
    fun manualSearchCanFindReceiversAgainAfterActivation() {
        Fixture().use { fixture ->
            fixture.transport.listener.onState(FlashState(active = true))
            fixture.await { session.peers.isEmpty() }
            onEdt { fixture.controller.discover() }
            fixture.await { session.peers == listOf(fixture.transport.peer) }
            assertEquals(2, fixture.transport.discoverCalls.get())
            assertEquals(1, fixture.transport.startCalls.get())
        }
    }

    @Test
    fun startingAnActiveSessionDoesNotRepeatTheInitialSearch() {
        Fixture().use { fixture ->
            onEdt { fixture.controller.start("Test") }
            assertEquals(1, fixture.transport.startCalls.get())
            assertEquals(1, fixture.transport.discoverCalls.get())
        }
    }

    @Test
    fun stoppingDuringStartupPreventsTheAutomaticSearch() {
        val enteredStart = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        Fixture(autoStart = false) { transport ->
            transport.onStart = {
                enteredStart.countDown()
                assertTrue(releaseStart.await(5, TimeUnit.SECONDS))
            }
        }.use { fixture ->
            try {
                onEdt { fixture.controller.start("Test") }
                assertTrue(enteredStart.await(5, TimeUnit.SECONDS))
                onEdt { fixture.controller.stop() }
                releaseStart.countDown()
                fixture.await { fixture.transport.stopCalls.get() > 0 }
                assertFalse(fixture.state().session.active)
                assertFalse(fixture.state().starting)
                assertEquals(0, fixture.transport.discoverCalls.get())
            } finally {
                releaseStart.countDown()
            }
        }
    }

    @Test
    fun completionBeforeSendReturnsKeepsEveryReceiptAndUsesOneSubmission() {
        Fixture().use { fixture ->
            val first = temporary.newFile("first.txt")
            val second = temporary.newFile("second.txt")
            val releaseReturn = CountDownLatch(1)
            fixture.transport.onSend = { id, file ->
                fixture.transport.announce(id, file)
                fixture.transport.complete(id, file)
                assertTrue(releaseReturn.await(5, TimeUnit.SECONDS))
            }
            try {
                fixture.send(first, second)
                fixture.await { selectedFiles == listOf(second) && sendPending }
                fixture.await { transfers.any { it.id == "out-2" && it.file == second } }
                fixture.transport.complete("out-2", second)
                fixture.transport.empty()
                fixture.await { !busy }
                assertTrue(fixture.state().selectedFiles.isEmpty())
                assertEquals(listOf(first, second), fixture.transport.sent.toList())
                assertEquals(listOf(listOf(first, second)), fixture.transport.batches.toList())
                releaseReturn.countDown()
                onEdt { }
                assertFalse(fixture.state().busy)
            } finally {
                releaseReturn.countDown()
            }
        }
    }

    @Test
    fun cancellationStopsTheBatchEvenWhenTheReceiptWinsTheRace() {
        Fixture().use { fixture ->
            val first = temporary.newFile("first.txt")
            val second = temporary.newFile("second.txt")
            fixture.send(first, second)
            fixture.await { transfers.any { it.id == "out-1" && it.file == first } }
            onEdt { fixture.controller.cancel("out-1") }
            fixture.await { fixture.transport.cancelCalls.get() == 1 }
            fixture.transport.complete("out-1", first)
            fixture.transport.empty()
            fixture.await { !busy }
            assertEquals(listOf(second), fixture.state().selectedFiles)
            assertEquals(listOf(first), fixture.transport.sent.toList())
            assertFalse(fixture.state().batchSending)
        }
    }

    @Test
    fun failurePreservesOnlyUnconfirmedFilesForAnExplicitRetry() {
        Fixture().use { fixture ->
            val first = temporary.newFile("first.txt")
            val second = temporary.newFile("second.txt")
            val third = temporary.newFile("third.txt")
            fixture.send(first, second, third)
            fixture.await { transfers.any { it.id == "out-1" && it.file == first } }
            fixture.transport.complete("out-1", first)
            fixture.await { transfers.any { it.id == "out-2" && it.file == second } }
            fixture.transport.fail("out-2")
            fixture.transport.empty()
            fixture.await { !busy }
            assertEquals(listOf(second, third), fixture.state().selectedFiles)
            assertEquals(listOf(first, second), fixture.transport.sent.toList())

            onEdt { fixture.controller.send() }
            fixture.await { transfers.any { it.id == "out-4" && it.file == second } }
            // A late error from the previous attempt must not tear down the retry.
            fixture.transport.fail("out-2")
            onEdt { }
            assertTrue(fixture.state().batchSending)
            fixture.transport.complete("out-4", second)
            fixture.await { transfers.any { it.id == "out-5" && it.file == third } }
            fixture.transport.complete("out-5", third)
            fixture.transport.empty()
            fixture.await { !busy }
            assertTrue(fixture.state().selectedFiles.isEmpty())
            assertEquals(listOf(first, second, second, third), fixture.transport.sent.toList())
        }
    }

    @Test
    fun missingSelectedFileDoesNotSilentlySendAPartialBatch() {
        Fixture().use { fixture ->
            val first = temporary.newFile("first.txt")
            val missing = temporary.newFile("missing.txt")
            onEdt { fixture.controller.chooseFiles(listOf(first, missing)) }
            assertTrue(missing.delete())
            onEdt { fixture.controller.send() }
            assertTrue(fixture.state().error)
            assertFalse(fixture.state().busy)
            assertEquals(listOf(first, missing), fixture.state().selectedFiles)
            assertTrue(fixture.transport.sent.isEmpty())
        }
    }

    @Test
    fun unrelatedCallbacksCannotClaimOrReleaseAReservedOutgoingFile() {
        Fixture().use { fixture ->
            val first = temporary.newFile("first.txt")
            val second = temporary.newFile("second.txt")
            val releaseSend = CountDownLatch(1)
            fixture.transport.onSend = { id, file ->
                fixture.transport.announce("incoming", File("incoming.txt"), outgoing = false)
                fixture.transport.complete("incoming", File("incoming.txt"), outgoing = false)
                fixture.transport.fail(null)
                fixture.transport.empty()
                assertTrue(releaseSend.await(5, TimeUnit.SECONDS))
                fixture.transport.announce(id, file)
            }
            try {
                fixture.send(first, second)
                fixture.await { transfers.any { it.id == "incoming" && it.phase == DesktopFlashPhase.COMPLETE } }
                assertNull(fixture.state().transfers.single { it.id == "incoming" }.file)
                assertTrue(fixture.state().sendPending)
                assertEquals(listOf(first, second), fixture.state().selectedFiles)
                assertEquals(listOf(first), fixture.transport.sent.toList())
            } finally {
                releaseSend.countDown()
            }
        }
    }

    @Test
    fun throwingAfterPublishingAnOperationDoesNotLeaveABusyTransfer() {
        Fixture().use { fixture ->
            val first = temporary.newFile("first.txt")
            val second = temporary.newFile("second.txt")
            fixture.transport.onSend = { id, file ->
                fixture.transport.announce(id, file)
                fixture.transport.empty()
                throw IllegalStateException("worker unavailable")
            }
            fixture.send(first, second)
            fixture.await { error && !busy }
            assertEquals(listOf(first, second), fixture.state().selectedFiles)
            assertEquals(DesktopFlashPhase.FAILED, fixture.state().transfers.single().phase)
        }
    }

    private inner class Fixture(
        autoStart: Boolean = true,
        configureTransport: (FakeTransport) -> Unit = {}
    ) : AutoCloseable {
        lateinit var transport: FakeTransport
        val controller = onEdt {
            DesktopFlashController(temporary.root) { _, _, listener ->
                FakeTransport(listener).also { transport = it; configureTransport(it) }
            }
        }

        init {
            if (autoStart) {
                onEdt { controller.start("Test") }
                await { session.active && session.peers.isNotEmpty() }
                onEdt { controller.choosePeer(transport.peer) }
            }
        }

        fun send(vararg files: File) = onEdt {
            controller.chooseFiles(files.toList())
            controller.send()
        }

        fun state() = onEdt { controller.state }

        fun await(predicate: DesktopFlashUiState.() -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline) {
                if (state().predicate()) return
                Thread.sleep(5)
            }
            assertTrue(state().predicate(), "Controller did not reach expected state: ${state()}")
        }

        override fun close() = onEdt { controller.close() }
    }

    private class FakeTransport(val listener: FlashListener) : DesktopFlashTransport {
        val peer = FlashPeer("peer", "Test peer", "127.0.0.1", 8989, Long.MAX_VALUE)
        val sent = CopyOnWriteArrayList<File>()
        val batches = CopyOnWriteArrayList<List<File>>()
        private data class Batch(val files: List<File>, val ids: List<String>, var cancelled: Boolean = false)
        private val operationBatches = java.util.concurrent.ConcurrentHashMap<String, Batch>()
        private val sequence = AtomicInteger()
        val startCalls = AtomicInteger()
        val stopCalls = AtomicInteger()
        val discoverCalls = AtomicInteger()
        val cancelCalls = AtomicInteger()
        var onStart: (() -> Unit)? = null
        var onSend: ((String, File) -> Unit)? = null

        override fun start() {
            startCalls.incrementAndGet()
            onStart?.invoke()
            listener.onState(FlashState(active = true))
        }
        override fun stop() { stopCalls.incrementAndGet() }
        override fun sendBatch(files: List<File>, peer: FlashPeer): String {
            batches += files.toList()
            val batch = Batch(files, files.map { "out-${sequence.incrementAndGet()}" })
            batch.ids.forEach { operationBatches[it] = batch }
            val file = files.first()
            sent += file
            val id = batch.ids.first()
            onSend?.invoke(id, file) ?: announce(id, file)
            return id
        }

        fun announce(id: String, file: File, outgoing: Boolean = true) {
            val batch = operationBatches[id]
            listener.onState(FlashState(active = true, peers = listOf(peer), operations = listOf(
                FlashOperation(id, peer, file.name, file.length(), outgoing,
                    batchId = batch?.ids?.first() ?: id, fileIndex = batch?.ids?.indexOf(id) ?: 0,
                    fileCount = batch?.files?.size ?: 1))))
        }

        fun empty() = listener.onState(FlashState(active = true, peers = listOf(peer)))
        fun complete(id: String, file: File, outgoing: Boolean = true) {
            listener.onCompleted(FlashCompleted(id, peer, file.name, outgoing))
            val batch = operationBatches[id] ?: return
            val next = batch.ids.indexOf(id) + 1
            if (outgoing && !batch.cancelled && next < batch.files.size) {
                sent += batch.files[next]
                announce(batch.ids[next], batch.files[next])
            }
        }
        fun fail(id: String?) = listener.onError(FlashError(id, "connection_failed", "Connection failed"))
        override fun approve(requestId: String, accepted: Boolean) = true
        override fun cancel(operationId: String): Boolean {
            operationBatches[operationId]?.cancelled = true
            cancelCalls.incrementAndGet()
            return true
        }
        override fun discover() {
            discoverCalls.incrementAndGet()
            empty()
        }
        override fun discoverAt(address: String, port: Int) = Unit
    }

    private companion object {
        fun <T> onEdt(action: () -> T): T {
            if (SwingUtilities.isEventDispatchThread()) return action()
            var result: Result<T>? = null
            SwingUtilities.invokeAndWait { result = runCatching(action) }
            return result!!.getOrThrow()
        }
    }
}
