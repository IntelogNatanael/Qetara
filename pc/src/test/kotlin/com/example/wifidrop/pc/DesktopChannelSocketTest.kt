package com.example.wifidrop.pc

import com.example.wifidrop.protocol.sha256File
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.test.*

/** Real encrypted PC sockets and the production queue; no Android, app preferences, or UI interaction. */
class DesktopChannelSocketTest {
    @Test
    fun overlappingOffersAreRequestedOnceAndTheirAcknowledgementsDoNotClaimFileDelivery() {
        ChannelSockets().use { fixture ->
            fixture.offer("first")
            fixture.awaitFirstRequest()
            fixture.offer("second")
            fixture.offer("first") // A fresh transport nonce must still deduplicate this offer.

            assertEquals(listOf("first"), fixture.requests.toList())
            assertEquals(1, fixture.pendingCount())
            fixture.allowFirstAcknowledgement()
            fixture.awaitIdle()
            fixture.offer("first") // Deduplication must also survive an accepted request.

            assertEquals(listOf("first", "second"), fixture.requests.toList())
            assertEquals(4, fixture.offerDeliveries.size, "All fresh message attempts reached the offer handler")
            assertEquals(2, fixture.offerDeliveries.count { it == DesktopChannelEnqueueResult.DUPLICATE })
            assertEquals(DesktopChannelRequestPhase.REQUESTED, fixture.phase("first"))
            assertEquals(DesktopChannelRequestPhase.REQUESTED, fixture.phase("second"))
            assertTrue(fixture.receivedFiles.isEmpty(), "A file_request ACK is not a received file")
            assertTrue(fixture.publishedFiles().isEmpty())

            val sent = fixture.returnFirstFile()
            val received = fixture.receivedFiles.single()
            assertEquals(sent.name, received.name)
            assertEquals(sha256File(sent), sha256File(received))
            assertEquals(1, fixture.publishedFiles().size)
        }
    }

    @Test
    fun revokingARequestWhoseRemoteAcknowledgementIsPendingPreventsTheNextRequest() {
        ChannelSockets().use { fixture ->
            fixture.offer("first")
            fixture.awaitFirstRequest()
            fixture.offer("second")

            fixture.leaveChannel()
            fixture.awaitIdle()
            fixture.allowFirstAcknowledgement()
            fixture.awaitFirstHandlerExit()

            assertEquals(listOf("first"), fixture.requests.toList())
            assertEquals(DesktopChannelRequestPhase.MANUAL, fixture.phase("first"))
            assertEquals(DesktopChannelRequestPhase.MANUAL, fixture.phase("second"))
            val completion = fixture.completions.single()
            assertNotNull(completion.networkError, "Revocation must close the request socket before the ACK")
            assertFalse(completion.applied)
            assertTrue(fixture.publishedFiles().isEmpty())
        }
    }

    @Test
    fun successfulAcknowledgementWithLateCompletionCannotReviveThePreviousChannelQueue() {
        ChannelSockets(holdFirstCompletion = true).use { fixture ->
            fixture.offer("first")
            fixture.awaitFirstRequest()
            fixture.offer("second")
            fixture.allowFirstAcknowledgement()
            fixture.awaitFirstAcknowledgement()

            fixture.leaveChannel()
            fixture.rejoinChannel()
            fixture.offer("third")
            assertEquals(listOf("first"), fixture.requests.toList(), "The old worker still owns its slot")
            assertEquals(1, fixture.pendingCount())
            fixture.allowFirstCompletion()
            fixture.awaitIdle()

            assertEquals(listOf("first", "third"), fixture.requests.toList())
            assertEquals(DesktopChannelRequestPhase.MANUAL, fixture.phase("first"))
            assertEquals(DesktopChannelRequestPhase.MANUAL, fixture.phase("second"))
            assertEquals(DesktopChannelRequestPhase.REQUESTED, fixture.phase("third"))
            val old = fixture.completions.single { it.offerId == "first" }
            assertNull(old.networkError, "The first encrypted ACK succeeded before leaving")
            assertFalse(old.applied, "That success no longer belongs to the active channel")
            assertTrue(fixture.completions.single { it.offerId == "third" }.applied)
            assertTrue(fixture.receivedFiles.isEmpty())
        }
    }

    private data class Completion(val offerId: String, val networkError: Throwable?, val applied: Boolean)

    private class ChannelSockets(holdFirstCompletion: Boolean = false) : AutoCloseable {
        private val root = Files.createTempDirectory("qetara-channel-sockets-").toFile()
        private val queue = DesktopChannelRequestQueue<DesktopChatEntry>()
        private val joined = AtomicBoolean(true)
        private val failure = AtomicReference<Throwable?>()
        private val firstRequest = CountDownLatch(1)
        private val firstAckRelease = CountDownLatch(1)
        private val firstHandlerExit = CountDownLatch(1)
        private val firstAcknowledged = CountDownLatch(1)
        private val firstCompletionRelease = CountDownLatch(if (holdFirstCompletion) 1 else 0)
        private val requestWorkers = CopyOnWriteArrayList<Thread>()
        val requests = CopyOnWriteArrayList<String>()
        val offerDeliveries = CopyOnWriteArrayList<DesktopChannelEnqueueResult>()
        val completions = CopyOnWriteArrayList<Completion>()
        val receivedFiles = CopyOnWriteArrayList<File>()
        private val subscriber = Endpoint("subscriber", { joined.get() }, ::receiveOffer, receivedFiles::add)
        private val publisher = Endpoint("publisher", { true }, ::receiveRequest)

        fun offer(id: String) {
            sendMessageToPeer(
                offerPayload(id), DesktopChatScope.GLOBAL_LAN, publisher.senderTo(subscriber),
                messageId = "offer-$id-${System.nanoTime()}"
            )
        }

        private fun receiveOffer(entry: DesktopChatEntry) = edt {
            assertEquals(DesktopChatScope.GLOBAL_LAN, entry.scope)
            val key = DesktopChannelOfferKey(entry.peerAddress, offerId(entry.message))
            offerDeliveries += queue.enqueue(key, entry, automatic = true)
            pump()
        }

        private fun receiveRequest(entry: DesktopChatEntry) {
            val id = offerId(entry.message)
            assertEquals(DesktopChatScope.GLOBAL_LAN, entry.scope)
            requests += id
            if (id == "first") {
                firstRequest.countDown()
                try {
                    check(firstAckRelease.await(15, TimeUnit.SECONDS)) { "Test did not release the first ACK" }
                } finally {
                    firstHandlerExit.countDown()
                }
            }
            // Deliberately return without sending bytes: accepting a request alone is not delivery.
        }

        private fun pump() {
            check(SwingUtilities.isEventDispatchThread())
            if (!joined.get()) return
            val work = queue.next() ?: return
            val worker = thread(start = false, isDaemon = true, name = "qetara-channel-request-test") {
                val result = runCatching {
                    sendMessageToPeer(
                        requestPayload(work.key.offerId), DesktopChatScope.GLOBAL_LAN,
                        subscriber.senderTo(publisher), cancellation = work.cancellation
                    )
                }
                if (work.key.offerId == "first" && result.isSuccess) {
                    firstAcknowledged.countDown()
                    if (!firstCompletionRelease.await(15, TimeUnit.SECONDS)) {
                        failure.compareAndSet(null, AssertionError("Test did not release the first completion"))
                    }
                }
                SwingUtilities.invokeLater {
                    runCatching {
                        val error = result.exceptionOrNull()
                        val applied = queue.complete(work, error?.message)
                        completions += Completion(work.key.offerId, error, applied)
                        pump()
                    }.onFailure { failure.compareAndSet(null, it) }
                }
            }
            requestWorkers += worker
            worker.start()
        }

        fun leaveChannel() = edt {
            joined.set(false)
            queue.revoke("Saliste del canal. Solicita el archivo cuando vuelvas a participar.")
        }

        fun rejoinChannel() = edt { joined.set(true) }
        fun pendingCount(): Int = edt { queue.pendingCount }
        fun phase(id: String): DesktopChannelRequestPhase? = edt { queue.snapshot()[key(id)]?.phase }
        fun awaitFirstRequest() = awaitLatch(firstRequest, "First encrypted request was not received")
        fun allowFirstAcknowledgement() = firstAckRelease.countDown()
        fun awaitFirstHandlerExit() = awaitLatch(firstHandlerExit, "First request handler did not finish")
        fun awaitFirstAcknowledgement() = awaitLatch(firstAcknowledged, "First encrypted request was not acknowledged")
        fun allowFirstCompletion() = firstCompletionRelease.countDown()

        fun awaitIdle() {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline) {
                failure.get()?.let { throw AssertionError("Socket fixture failed", it) }
                if (edt { !queue.hasActivity }) return
                Thread.sleep(10)
            }
            fail("Channel requests did not become idle: ${edt { queue.snapshot() }}")
        }

        fun publishedFiles(): List<File> = subscriber.directory.listFiles().orEmpty().filter { it.isFile }

        fun returnFirstFile(): File {
            val payload = File(root, "first.bin").apply {
                writeBytes(ByteArray(FILE_SIZE) { ((it * 17 + 11) % 251).toByte() })
            }
            sendFileToPeer(payload, publisher.senderTo(subscriber))
            return payload
        }

        override fun close() {
            leaveChannel()
            firstAckRelease.countDown()
            firstCompletionRelease.countDown()
            try {
                publisher.close()
                subscriber.close()
                requestWorkers.forEach { it.join(3000) }
                edt { } // Drain callbacks before removing the fixture directories.
                check(requestWorkers.none { it.isAlive }) { "A channel request fixture worker did not stop" }
            } finally {
                root.deleteRecursively()
            }
        }

        private inner class Endpoint(
            private val id: String,
            isJoined: () -> Boolean,
            onMessage: (DesktopChatEntry) -> Unit,
            onFile: (File) -> Unit = {}
        ) : AutoCloseable {
            val directory = File(root, "$id-received").apply { check(mkdirs()) }
            private val identity = DesktopIdentityStore.getOrCreateNoiseIdentity(
                File(root, "$id-identity").apply { check(mkdirs()) }
            )
            private val port = ServerSocket(0).use { it.localPort }
            private val ready = CountDownLatch(1)
            private val server = PcReceiverServer(
                ReceiverConfig(TOKEN, PIN, directory, port, id, 60_000L, false, id), identity,
                isGlobalLanJoined = isJoined,
                onReady = { ready.countDown() },
                onMessageReceived = onMessage,
                onFileReceived = { file, _ -> onFile(file) }
            )
            private val worker = thread(isDaemon = true, name = "qetara-channel-$id-test") {
                runCatching { server.runBlocking() }.onFailure {
                    failure.compareAndSet(null, it)
                    ready.countDown()
                }
            }

            init {
                awaitLatch(ready, "$id receiver did not become ready")
                failure.get()?.let { throw AssertionError("Receiver startup failed", it) }
            }

            fun senderTo(other: Endpoint) = SenderConfig(
                TOKEN, PIN, "127.0.0.1", other.port, id, id, 1, identity
            )

            override fun close() {
                server.stop()
                worker.join(3000)
                check(!worker.isAlive) { "$id receiver did not stop" }
            }
        }

        private fun key(id: String) = DesktopChannelOfferKey("127.0.0.1", id)

        private fun offerPayload(id: String): String =
            "\u2063QCT\u2063{\"kind\":\"file_offer\",\"scope\":\"GLOBAL_LAN\",\"offer_id\":\"$id\"," +
                "\"file_name\":\"$id.bin\",\"file_size_bytes\":$FILE_SIZE,\"sender_id\":\"publisher\"," +
                "\"sender_label\":\"Publisher\",\"created_at_ms\":1}"

        private fun requestPayload(id: String): String =
            "\u2063QCT\u2063{\"kind\":\"file_request\",\"offer_id\":\"$id\"," +
                "\"requester_id\":\"subscriber\",\"requester_label\":\"Subscriber\"}"

        private fun offerId(payload: String): String =
            checkNotNull(Regex("\"offer_id\":\"([^\"]+)\"").find(payload)?.groupValues?.get(1))

        private fun awaitLatch(latch: CountDownLatch, message: String) {
            assertTrue(latch.await(5, TimeUnit.SECONDS), message)
            failure.get()?.let { throw AssertionError("Socket fixture failed", it) }
        }
    }

    private companion object {
        const val TOKEN = "CHANNELQA"
        const val PIN = "927461"
        const val FILE_SIZE = 200_003

        fun <T> edt(action: () -> T): T {
            if (SwingUtilities.isEventDispatchThread()) return action()
            var result: Result<T>? = null
            SwingUtilities.invokeAndWait { result = runCatching(action) }
            return checkNotNull(result).getOrThrow()
        }
    }
}
