package com.example.wifidrop.protocol.flash

import com.example.wifidrop.protocol.sha256File
import kr.jclab.noise.protocol.Noise
import java.io.*
import java.net.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.*
import kotlin.test.*

class FlashEngineTest {
    private class Fixture(lifetime: Long = 60_000, approvalTimeout: Long = 10_000, discoveryPort: Int? = null,
                          val progressHook: (FlashProgress) -> Unit = {},
                          val completedHook: (FlashCompleted) -> Unit = {},
                          val receivedHook: (FlashReceived) -> Unit = {},
                          maxFileBytes: Long = 16L * 1024 * 1024 * 1024) : Closeable {
        val root = Files.createTempDirectory("qetara-flash-test-").toFile()
        val directory = File(root, "received")
        val approvals = LinkedBlockingQueue<FlashApproval>()
        val completed = LinkedBlockingQueue<FlashCompleted>()
        val received = LinkedBlockingQueue<FlashReceived>()
        val errors = LinkedBlockingQueue<FlashError>()
        val states = LinkedBlockingQueue<FlashState>()
        val engine = FlashEngine("Test device", directory, object : FlashListener {
            override fun onState(state: FlashState) { states.offer(state) }
            override fun onApproval(approval: FlashApproval) { approvals.offer(approval) }
            override fun onProgress(progress: FlashProgress) = progressHook(progress)
            override fun onCompleted(completed: FlashCompleted) { this@Fixture.completed.offer(completed); completedHook(completed) }
            override fun onReceived(received: FlashReceived) { this@Fixture.received.offer(received); receivedHook(received) }
            override fun onError(error: FlashError) { errors.offer(error) }
        }, FlashConfig(port = 0, bindAddress = "127.0.0.1", discoveryEnabled = discoveryPort != null,
            discoveryPort = discoveryPort ?: FLASH_PORT, lifetimeMs = lifetime, approvalTimeoutMs = approvalTimeout,
            handshakeTimeoutMs = 2_000, transferTimeoutMs = 3_000, maxFileBytes = maxFileBytes))
        val state = engine.start()
        fun peer() = FlashPeer(state.localId, "Test device", "127.0.0.1", state.port, state.expiresAtMs)
        fun source(name: String = "example.bin", size: Int = 190_000): File = File(root, name).apply {
            writeBytes(ByteArray(size) { ((it * 17 + 31) % 251).toByte() })
        }
        fun approval(): FlashApproval = assertNotNull(approvals.poll(10, TimeUnit.SECONDS), "approval was not emitted; errors=$errors")
        fun success(): FlashCompleted = assertNotNull(completed.poll(10, TimeUnit.SECONDS), "completion missing; errors=$errors")
        fun idle() = waitUntil { engine.snapshot().operations.isEmpty() }
        override fun close() { engine.stop(); root.deleteRecursively() }
    }
    companion object {
        private fun waitUntil(condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
            while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
            assertTrue(condition(), "condition timed out")
        }
    }
    private fun approveBoth(a: Fixture, b: Fixture): Pair<FlashApproval, FlashApproval> {
        val first = a.approval(); val second = b.approval()
        assertEquals(first.verificationCode, second.verificationCode)
        assertEquals(19, first.verificationCode.length)
        assertTrue(a.engine.approve(first.requestId, true))
        assertTrue(b.engine.approve(second.requestId, true))
        return first to second
    }

    @Test fun bothUsersMustApproveBeforeBytesAreWrittenAndEachFileGetsAFreshCode() {
        Fixture().use { a -> Fixture().use { b ->
            val source = a.source()
            a.engine.send(source, b.peer())
            val outgoing = a.approval(); val incoming = b.approval()
            assertEquals(outgoing.verificationCode, incoming.verificationCode)
            assertFalse(b.directory.exists(), "even staging must wait for both decisions")
            assertTrue(a.engine.approve(outgoing.requestId, true))
            assertFalse(b.directory.exists())
            assertTrue(b.engine.approve(incoming.requestId, true))
            a.success(); b.success(); a.idle(); b.idle()
            val first = assertNotNull(b.received.poll(2, TimeUnit.SECONDS)).file
            assertContentEquals(source.readBytes(), first.readBytes())
            a.engine.send(source, b.peer())
            val (next, _) = approveBoth(a, b)
            assertNotEquals(outgoing.verificationCode, next.verificationCode)
            a.success(); b.success(); a.idle(); b.idle()
            val second = assertNotNull(b.received.poll(2, TimeUnit.SECONDS)).file
            assertNotEquals(first.name, second.name, "collision must not replace prior file")
            assertContentEquals(source.readBytes(), first.readBytes())
            assertContentEquals(source.readBytes(), second.readBytes())
        } }
    }

    @Test fun oneBilateralDecisionAuthorizesExactlyTheOfferedFilesAndANewBatchRequiresANewCode() {
        Fixture().use { sender -> Fixture().use { receiver ->
            val files = listOf(sender.source("one.txt", 17), sender.source("two.bin", 90_123), sender.source("empty.bin", 0))
            val batchId = sender.engine.sendBatch(files, receiver.peer())
            val outgoing = sender.approval(); val incoming = receiver.approval()
            assertEquals(files.map { FlashOfferedFile(it.name, it.length()) }, outgoing.files)
            assertEquals(outgoing.files, incoming.files)
            assertEquals(files.sumOf { it.length() }, incoming.totalBytes)
            assertEquals(outgoing.verificationCode, incoming.verificationCode)
            assertTrue(sender.engine.approve(outgoing.requestId, true))
            assertFalse(receiver.directory.exists(), "one decision must not even create staging")
            assertTrue(receiver.engine.approve(incoming.requestId, true))
            val sent = files.map { sender.success() }
            files.forEach { receiver.success() }
            sender.idle(); receiver.idle()
            assertEquals(batchId, sent.first().operationId)
            assertEquals(files.size, sent.map { it.operationId }.distinct().size)
            assertTrue(sender.approvals.isEmpty() && receiver.approvals.isEmpty(), "only one prompt per side")
            files.forEach { source -> assertContentEquals(source.readBytes(), File(receiver.directory, source.name).readBytes()) }
            val operations = sender.states.flatMap { it.operations }.filter { it.batchId == batchId }
            assertEquals(setOf(0, 1, 2), operations.map { it.fileIndex }.toSet())
            assertTrue(operations.all { it.fileCount == 3 })
            sender.engine.sendBatch(files, receiver.peer())
            val retry = sender.approval(); val retryIncoming = receiver.approval()
            assertNotEquals(outgoing.verificationCode, retry.verificationCode)
            assertFalse(sender.engine.approve(outgoing.requestId, true))
            receiver.engine.approve(retryIncoming.requestId, false)
            sender.idle(); receiver.idle()
            assertEquals(files.size, receiver.directory.listFiles()!!.count { it.isFile })
        } }
    }

    @Test fun rejectingABatchAfterOnlyTheSenderApprovesNeverCreatesPayloadFiles() {
        Fixture().use { sender -> Fixture().use { receiver ->
            sender.engine.sendBatch(listOf(sender.source("a", 1), sender.source("b", 1)), receiver.peer())
            val outgoing = sender.approval(); val incoming = receiver.approval()
            sender.engine.approve(outgoing.requestId, true)
            receiver.engine.approve(incoming.requestId, false)
            sender.idle(); receiver.idle()
            assertFalse(receiver.directory.exists())
            assertTrue(sender.completed.isEmpty() && receiver.completed.isEmpty())
        } }
    }

    @Test fun cancellingAfterTheFirstAckPreservesItAndRevokesTheRestOfTheBatch() {
        lateinit var sender: Fixture
        sender = Fixture(completedHook = { if (it.outgoing) sender.engine.cancel(it.operationId) })
        sender.use { a -> Fixture().use { receiver ->
            val files = listOf(a.source("first", 29), a.source("second", 70_000), a.source("third", 11))
            a.engine.sendBatch(files, receiver.peer())
            val (approval, _) = approveBoth(a, receiver)
            assertEquals("first", a.success().fileName)
            a.idle(); receiver.idle()
            assertContentEquals(files.first().readBytes(), File(receiver.directory, "first").readBytes())
            assertFalse(File(receiver.directory, "second").exists())
            assertFalse(File(receiver.directory, "third").exists())
            assertTrue(a.completed.isEmpty())
            a.engine.sendBatch(files.drop(1), receiver.peer())
            val next = a.approval(); val nextIncoming = receiver.approval()
            assertNotEquals(approval.verificationCode, next.verificationCode)
            assertEquals(files.drop(1).map { it.name }, next.files.map { it.fileName })
            receiver.engine.approve(nextIncoming.requestId, false)
            a.idle(); receiver.idle()
        } }
    }

    @Test fun mutatingALaterFileCannotChangeTheApprovedManifestAndStopsFollowingFiles() {
        lateinit var next: File
        Fixture(completedHook = { if (it.fileName == "first") next.writeBytes(ByteArray(61) { 9 }) }).use { sender ->
            Fixture().use { receiver ->
                val first = sender.source("first", 13)
                next = sender.source("second", 61)
                val third = sender.source("third", 2)
                sender.engine.sendBatch(listOf(first, next, third), receiver.peer())
                approveBoth(sender, receiver)
                assertEquals("first", sender.success().fileName)
                sender.idle(); receiver.idle()
                assertEquals("integrity_failed", receiver.errors.poll(2, TimeUnit.SECONDS)?.code)
                assertContentEquals(first.readBytes(), File(receiver.directory, "first").readBytes())
                assertFalse(File(receiver.directory, "second").exists())
                assertFalse(File(receiver.directory, "third").exists())
            }
        }
    }

    @Test fun batchLimitsAreEnforcedBeforeConnecting() {
        Fixture().use { sender -> Fixture().use { receiver ->
            val file = sender.source(size = 0)
            assertFails { sender.engine.sendBatch(emptyList(), receiver.peer()) }
            assertFails { sender.engine.sendBatch(List(FLASH_MAX_BATCH_FILES + 1) { file }, receiver.peer()) }
            assertTrue(sender.engine.snapshot().operations.isEmpty())
            assertTrue(receiver.engine.snapshot().operations.isEmpty())
        } }
    }

    @Test fun legacyReceiverRejectsBatchClearlyWithoutFallingBackToSeparateTransfers() {
        Fixture().use { sender -> ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val legacy = CompletableFuture.runAsync {
                server.accept().use { socket -> assertEquals(FLASH_BATCH_TRANSFER, DataInputStream(socket.getInputStream()).flashKind()) }
            }
            val peer = FlashPeer(UUID.randomUUID().toString(), "Legacy", "127.0.0.1", server.localPort, System.currentTimeMillis() + 60_000)
            sender.engine.sendBatch(listOf(sender.source("a", 1), sender.source("b", 1)), peer)
            sender.idle(); legacy.get(3, TimeUnit.SECONDS)
            assertEquals("batch_incompatible", sender.errors.poll(2, TimeUnit.SECONDS)?.code)
            assertTrue(sender.approvals.isEmpty() && sender.completed.isEmpty())
            server.soTimeout = 150
            assertFailsWith<SocketTimeoutException> { server.accept() }
        } }
    }

    private fun withRawBatch(receiver: Fixture, action: (FlashChannel) -> Unit) {
        Socket("127.0.0.1", receiver.state.port).use { socket ->
            socket.soTimeout = 4_000
            val output = DataOutputStream(socket.getOutputStream().buffered())
            val input = DataInputStream(socket.getInputStream().buffered())
            output.flashHeader(FLASH_BATCH_TRANSFER); output.flush()
            assertEquals(FLASH_BATCH_TRANSFER, input.readInt())
            val dh = Noise.createDH("25519")
            val key = try { dh.generateKeyPair(); ByteArray(dh.privateKeyLength).also { dh.getPrivateKey(it, 0) } } finally { dh.destroy() }
            flashHandshake(input, output, true, key, batch = true).use { channel ->
                key.fill(0)
                channel.write { it.writeInt(FLASH_HELLO); writeFlashPeer(it, FlashPeer(UUID.randomUUID().toString(), "Raw batch", "127.0.0.1", 8989, System.currentTimeMillis() + 60_000)) }
                assertEquals(FLASH_HELLO, channel.read().readInt())
                action(channel)
            }
        }
    }

    @Test fun malformedOrOverflowingManifestsCannotPromptOrCreateFiles() {
        val invalidOffers: List<(DataOutputStream) -> Unit> = listOf(
            { it.writeInt(0) },
            { it.writeInt(FLASH_MAX_BATCH_FILES + 1) },
            { it.writeInt(2); it.writeUTF("missing size and digest") },
            { frame ->
                frame.writeInt(2)
                repeat(2) { frame.writeUTF("file-$it"); frame.writeLong(Long.MAX_VALUE); frame.writeUTF("0".repeat(64)) }
            },
            { frame ->
                frame.writeInt(2)
                repeat(2) { frame.writeUTF("file-$it"); frame.writeLong(0); frame.writeUTF("invalid-hash") }
            }
        )
        invalidOffers.forEach { invalid -> Fixture(maxFileBytes = Long.MAX_VALUE).use { receiver ->
            withRawBatch(receiver) { channel ->
                channel.write { it.writeInt(FLASH_BATCH_OFFER); invalid(it) }
                assertFails { channel.read() }
            }
            receiver.idle()
            assertTrue(receiver.approvals.isEmpty() && receiver.received.isEmpty())
            assertFalse(receiver.directory.exists())
        } }
    }

    @Test fun outOfOrderBatchPayloadFailsTheFirstApprovedHashAndNeverPublishes() {
        Fixture().use { receiver ->
            withRawBatch(receiver) { channel ->
                val payloads = listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6))
                channel.write { frame ->
                    frame.writeInt(FLASH_BATCH_OFFER); frame.writeInt(2)
                    payloads.forEachIndexed { index, bytes ->
                        frame.writeUTF("file-$index"); frame.writeLong(bytes.size.toLong())
                        frame.writeUTF(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) })
                    }
                }
                val approval = receiver.approval()
                assertEquals(2, approval.files.size)
                receiver.engine.approve(approval.requestId, true)
                channel.write { it.writeInt(FLASH_DECISION); it.writeBoolean(true) }
                val decision = channel.read()
                assertEquals(FLASH_DECISION, decision.readInt()); assertTrue(decision.readBoolean())
                channel.write { it.writeInt(FLASH_CHUNK); it.writeInt(3); it.write(payloads[1]) }
                channel.write { it.writeInt(FLASH_DONE) }
                assertFails { channel.read() }
            }
            receiver.idle()
            assertEquals("integrity_failed", receiver.errors.poll(2, TimeUnit.SECONDS)?.code)
            assertTrue(receiver.received.isEmpty())
            assertTrue(receiver.directory.walkTopDown().none { it.isFile })
        }
    }
    @Test fun receiverRejectionClearsBothPromptsWithoutWritingAFile() {
        Fixture().use { a -> Fixture().use { b ->
            a.engine.send(a.source(), b.peer())
            val outgoing = a.approval(); val incoming = b.approval()
            assertTrue(b.engine.approve(incoming.requestId, false))
            a.idle(); b.idle()
            assertFalse(a.engine.approve(outgoing.requestId, true))
            assertTrue(a.engine.snapshot().approvals.isEmpty())
            assertFalse(b.directory.exists())
            assertTrue(a.completed.isEmpty() && b.completed.isEmpty())
            assertEquals("rejected", assertNotNull(b.errors.poll(2, TimeUnit.SECONDS)).code)
        } }
    }
    @Test fun cancelPendingRequestAndRestartNeverReusesApprovalOrIdentity() {
        Fixture().use { a -> Fixture().use { b ->
            val op = a.engine.send(a.source(), b.peer())
            val old = a.approval(); b.approval()
            assertTrue(a.engine.cancel(op)); a.idle(); b.idle()
            assertFalse(a.engine.approve(old.requestId, true))
            val previousId = a.engine.snapshot().localId
            a.engine.stop(); a.engine.start()
            assertNotEquals(previousId, a.engine.snapshot().localId)
            assertFalse(a.engine.approve(old.requestId, true))
            assertTrue(a.completed.isEmpty() && b.completed.isEmpty())
            assertFalse(b.directory.exists())
        } }
    }
    @Test fun activationExpiryClosesPendingSocketAndRejectsLateApproval() {
        Fixture().use { a -> Fixture(lifetime = 1_800, approvalTimeout = 5_000).use { b ->
            a.engine.send(a.source(size = 0), b.peer())
            a.approval(); val incoming = b.approval()
            waitUntil { !b.engine.snapshot().active }
            a.idle()
            assertFalse(b.engine.approve(incoming.requestId, true))
            assertFalse(b.directory.exists())
            assertTrue(b.completed.isEmpty())
            assertFails { a.engine.discoverAt("203.0.113.1") }
        } }
    }
    @Test fun emptyFileIsAcceptedOnlyAfterBothConfirmations() {
        Fixture().use { a -> Fixture().use { b ->
            a.engine.send(a.source("empty.txt", 0), b.peer())
            approveBoth(a, b); a.success(); b.success(); b.idle()
            assertEquals(0L, assertNotNull(b.received.poll(2, TimeUnit.SECONDS)).file.length())
        } }
    }
    @Test fun modificationAfterApprovalMetadataFailsHashAndDoesNotPublish() {
        Fixture().use { a -> Fixture().use { b ->
            val source = a.source(size = 2000)
            a.engine.send(source, b.peer())
            val outgoing = a.approval(); val incoming = b.approval()
            source.writeBytes(ByteArray(2000) { 9 })
            a.engine.approve(outgoing.requestId, true); b.engine.approve(incoming.requestId, true)
            a.idle(); b.idle()
            assertEquals("integrity_failed", assertNotNull(b.errors.poll(2, TimeUnit.SECONDS)).code)
            assertTrue(b.received.isEmpty())
            assertTrue(b.directory.walkTopDown().none { it.isFile })
        } }
    }
    @Test fun stopDuringPayloadClosesAdmissionAndNeverPublishesPartialBytes() {
        val firstChunk = CountDownLatch(1); val release = CountDownLatch(1)
        Fixture().use { a -> Fixture(progressHook = { if (!it.outgoing) { firstChunk.countDown(); release.await(5, TimeUnit.SECONDS) } }).use { b ->
            a.engine.send(a.source(size = 8 * 1024 * 1024), b.peer())
            approveBoth(a, b)
            assertTrue(firstChunk.await(8, TimeUnit.SECONDS))
            val stopped = CompletableFuture.runAsync { b.engine.stop() }
            waitUntil { !b.engine.snapshot().active }
            release.countDown(); stopped.get(5, TimeUnit.SECONDS)
            a.idle()
            waitUntil { b.directory.walkTopDown().none { it.isFile } }
            assertTrue(b.received.isEmpty() && b.completed.isEmpty())
        } }
    }
    @Test fun manualDiscoveryReportsEphemeralIdentityWithoutStartingTransferAndStopsWithActivation() {
        fun probe(port: Int): FlashPeer = Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
            socket.soTimeout = 2_000
            val output = DataOutputStream(socket.getOutputStream())
            output.flashHeader(FLASH_PROBE); output.flush()
            val input = DataInputStream(socket.getInputStream())
            assertEquals(FLASH_ANNOUNCE, input.flashKind())
            readFlashPeer(input, "127.0.0.1")
        }
        Fixture().use { a -> Fixture().use { b ->
            a.engine.discoverAt("127.0.0.1", b.state.port)
            waitUntil { a.engine.snapshot().peers.isNotEmpty() }
            val peer = a.engine.snapshot().peers.single()
            assertEquals(b.state.localId, peer.id); assertEquals(b.state.port, peer.port)
            assertEquals(b.state.localId, probe(b.state.port).id)
            assertTrue(a.engine.snapshot().operations.isEmpty() && b.engine.snapshot().operations.isEmpty())
            assertTrue(b.approvals.isEmpty() && !b.directory.exists())
            b.engine.stop()
            assertFalse(b.engine.snapshot().active)
            // A closing listener may finish a TCP connect before its blocked accept unwinds.
            // Require transport closure without a Flash response; a timeout is not closure.
            val stopped = assertFailsWith<IOException> { probe(b.state.port) }
            assertFalse(stopped is SocketTimeoutException, "Stopped listener did not close the probe")
            assertTrue(b.engine.snapshot().operations.isEmpty() && b.approvals.isEmpty())
            assertTrue(b.received.isEmpty() && !b.directory.exists())
            val restarted = b.engine.start()
            assertTrue(restarted.active)
            assertNotEquals(b.state.localId, restarted.localId)
            assertEquals(restarted.localId, probe(restarted.port).id)
            assertTrue(b.engine.snapshot().operations.isEmpty() && b.approvals.isEmpty())
        } }
    }
    @Test fun failedManualDiscoveryCannotClaimAFileTransferForClosedTimedOutOrMalformedReplies() {
        listOf("closed", "timeout", "invalid_peer").forEach { reply ->
            Fixture().use { fixture -> ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                val release = CountDownLatch(1)
                val responder = CompletableFuture.runAsync {
                    server.accept().use { socket ->
                        socket.soTimeout = 3_000
                        assertEquals(FLASH_PROBE, DataInputStream(socket.getInputStream()).flashKind())
                        when (reply) {
                            "timeout" -> assertTrue(release.await(8, TimeUnit.SECONDS))
                            "invalid_peer" -> DataOutputStream(socket.getOutputStream()).apply {
                                flashHeader(FLASH_ANNOUNCE); writeUTF("not-a-valid-identity"); flush()
                            }
                            else -> Unit
                        }
                    }
                }
                try {
                    fixture.engine.discoverAt("127.0.0.1", server.localPort)
                    val error = assertNotNull(fixture.errors.poll(6, TimeUnit.SECONDS), "missing discovery error: $reply")
                    assertNull(error.operationId)
                    assertEquals("discovery_failed", error.code, reply)
                    assertContains(error.message, "buscar esa dirección")
                    assertFalse(error.message.contains("archivo"), error.message)
                    assertTrue(fixture.engine.snapshot().active)
                    assertTrue(fixture.engine.snapshot().operations.isEmpty())
                    assertTrue(fixture.approvals.isEmpty() && fixture.received.isEmpty() && fixture.completed.isEmpty())
                    assertFalse(fixture.directory.exists())
                } finally {
                    release.countDown()
                    responder.get(5, TimeUnit.SECONDS)
                }
            } }
        }
    }

    @Test fun connectionLostAfterPublicationStillWarnsTheSenderToCheckTheReceiver() {
        lateinit var receiver: Fixture
        receiver = Fixture(receivedHook = { receiver.engine.stop() })
        receiver.use { b -> Fixture().use { a ->
            val source = a.source("published-before-lost-ack.txt", 1_234)
            val operationId = a.engine.send(source, b.peer())
            approveBoth(a, b)
            val saved = assertNotNull(b.received.poll(5, TimeUnit.SECONDS)).file
            val error = assertNotNull(a.errors.poll(5, TimeUnit.SECONDS))
            assertEquals(operationId, error.operationId)
            assertEquals("connection_failed", error.code)
            assertContains(error.message, "Comprueba en el receptor si llegó el archivo antes de volver a enviarlo")
            assertContentEquals(source.readBytes(), saved.readBytes())
            assertTrue(a.completed.isEmpty(), "the sender must not claim delivery without the ACK")
            a.idle()
        } }
    }

    @Test fun udpDiscoveryRepliesOnlyWithPublicActivationAndEchoesNonce() {
        val udpPort = DatagramSocket(0).use { it.localPort }
        Fixture(discoveryPort = udpPort).use { fixture -> DatagramSocket().use { client ->
            client.soTimeout = 2_000
            val nonce = UUID.randomUUID().toString()
            val bytes = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { it.flashHeader(FLASH_DISCOVER); it.writeUTF(nonce) } }.toByteArray()
            client.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("127.0.0.1"), udpPort))
            val reply = DatagramPacket(ByteArray(1024), 1024); client.receive(reply)
            val input = DataInputStream(ByteArrayInputStream(reply.data, 0, reply.length))
            assertEquals(FLASH_ANNOUNCE, input.flashKind()); assertEquals(nonce, input.readUTF())
            val peer = readFlashPeer(input, "127.0.0.1"); input.requireEnd()
            assertEquals(fixture.state.localId, peer.id); assertEquals("Test device", peer.label)
            assertTrue(fixture.approvals.isEmpty() && fixture.engine.snapshot().operations.isEmpty())
        } }
    }
    @Test fun publicDnsIpv6AndMalformedAddressesAreRejectedBeforeNetworkUse() {
        listOf("example.com", "8.8.8.8", "203.0.113.1", "::1", "192.168.1.256", "127.0.0.1.example", " 127.0.0.1").forEach { assertFails { requireFlashAddress(it) } }
        listOf("127.0.0.1", "10.2.3.4", "172.16.0.1", "192.168.2.4", "169.254.1.2").forEach { assertEquals(it, requireFlashAddress(it)) }
    }

    private fun rawTransfer(receiver: Fixture, fileName: String, bytes: ByteArray, corruptFrame: Boolean = false, premature: Boolean = false) {
        Socket("127.0.0.1", receiver.state.port).use { socket ->
            socket.soTimeout = 4_000
            val output = DataOutputStream(socket.getOutputStream().buffered()); val input = DataInputStream(socket.getInputStream().buffered())
            output.flashHeader(FLASH_TRANSFER); output.flush()
            val dh = Noise.createDH("25519")
            val key = try { dh.generateKeyPair(); ByteArray(dh.privateKeyLength).also { dh.getPrivateKey(it, 0) } } finally { dh.destroy() }
            flashHandshake(input, output, true, key).use { channel ->
                key.fill(0)
                channel.write { it.writeInt(FLASH_HELLO); writeFlashPeer(it, FlashPeer(UUID.randomUUID().toString(), "Raw test", "127.0.0.1", 8989, System.currentTimeMillis() + 60_000)) }
                assertEquals(FLASH_HELLO, channel.read().readInt())
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                channel.write { it.writeInt(FLASH_OFFER); it.writeUTF(fileName); it.writeLong(bytes.size.toLong()); it.writeUTF(hash) }
                val approval = receiver.approval()
                assertEquals(channel.verificationCode, approval.verificationCode)
                if (premature) {
                    channel.write { it.writeInt(FLASH_CHUNK); it.writeInt(bytes.size); it.write(bytes) }
                    val reply = runCatching { channel.read() }.getOrNull()
                    if (reply != null) { assertEquals(FLASH_DECISION, reply.readInt()); assertFalse(reply.readBoolean()) }
                } else {
                    assertTrue(receiver.engine.approve(approval.requestId, true))
                    channel.write { it.writeInt(FLASH_DECISION); it.writeBoolean(true) }
                    val decision = channel.read(); assertEquals(FLASH_DECISION, decision.readInt()); assertTrue(decision.readBoolean())
                    if (corruptFrame) {
                        output.writeInt(FLASH_FRAME_BYTES + 17); output.flush()
                        assertFails { channel.read() }
                    } else {
                        if (bytes.isNotEmpty()) channel.write { it.writeInt(FLASH_CHUNK); it.writeInt(bytes.size); it.write(bytes) }
                        channel.write { it.writeInt(FLASH_DONE) }
                        assertEquals(FLASH_ACK, channel.read().readInt())
                    }
                }
            }
        }
    }
    @Test fun hostileRemotePathIsSanitizedAndExistingFileIsNeverOverwritten() {
        Fixture().use { receiver ->
            val preserved = File(receiver.root, "outside.txt").apply { writeText("original") }
            rawTransfer(receiver, "../../outside.txt", byteArrayOf(1, 2, 3))
            receiver.success(); receiver.idle()
            val saved = assertNotNull(receiver.received.poll(2, TimeUnit.SECONDS)).file
            assertEquals(receiver.directory.canonicalFile, saved.canonicalFile.parentFile)
            assertEquals("original", preserved.readText())
            assertContentEquals(byteArrayOf(1, 2, 3), saved.readBytes())
        }
    }
    @Test fun oversizedCiphertextIsRejectedBeforeAllocationAndPublication() {
        Fixture().use { receiver ->
            rawTransfer(receiver, "large-frame.bin", byteArrayOf(1), corruptFrame = true)
            receiver.idle(); assertTrue(receiver.received.isEmpty())
            assertTrue(receiver.directory.walkTopDown().none { it.isFile })
        }
    }
    @Test fun payloadInsteadOfSecondApprovalIsRejectedWithoutWritingAnything() {
        Fixture().use { receiver ->
            rawTransfer(receiver, "premature.bin", byteArrayOf(1), premature = true)
            receiver.idle(); assertTrue(receiver.received.isEmpty()); assertFalse(receiver.directory.exists())
        }
    }
    @Test fun committedReceiptPrecedesStopNotificationEvenWhenStopWinsStateLockNext() {
        val eventLock = Any(); val stateLock = Any()
        val commitEntered = CountDownLatch(1); val releaseCommit = CountDownLatch(1)
        val stopAttempted = CountDownLatch(1); val stateStopped = CountDownLatch(1)
        val order = CopyOnWriteArrayList<String>()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val publication = pool.submit {
                flashPublishAndNotify<String>(eventLock, stateLock, publish = {
                    assertTrue(Thread.holdsLock(eventLock), "receipt admission must already fence stopped-state callbacks")
                    assertTrue(Thread.holdsLock(stateLock))
                    commitEntered.countDown()
                    assertTrue(releaseCommit.await(3, TimeUnit.SECONDS))
                    "verified-file"
                }, received = { saved ->
                    assertEquals("verified-file", saved)
                    assertTrue(Thread.holdsLock(eventLock))
                    assertFalse(Thread.holdsLock(stateLock), "listener must not run under state lock")
                    assertTrue(stateStopped.await(3, TimeUnit.SECONDS))
                    order.add("received")
                })
            }
            assertTrue(commitEntered.await(3, TimeUnit.SECONDS))
            val stop = pool.submit {
                stopAttempted.countDown()
                synchronized(stateLock) { stateStopped.countDown() }
                synchronized(eventLock) { order.add("inactive") }
            }
            assertTrue(stopAttempted.await(3, TimeUnit.SECONDS))
            releaseCommit.countDown()
            publication.get(4, TimeUnit.SECONDS); stop.get(4, TimeUnit.SECONDS)
            assertEquals(listOf("received", "inactive"), order.toList())
        } finally { releaseCommit.countDown(); stateStopped.countDown(); pool.shutdownNow() }
    }
    @Test fun failedUdpBindRollsBackTcpActivationAndCanBeRetried() {
        DatagramSocket(0).use { occupied ->
            val root = Files.createTempDirectory("flash-bind-test-").toFile()
            val engine = FlashEngine("Test", root, config = FlashConfig(port = 0, bindAddress = "127.0.0.1", discoveryPort = occupied.localPort))
            try {
                repeat(3) { assertFails { engine.start() }; assertFalse(engine.snapshot().active) }
                occupied.close()
                assertTrue(engine.start().active)
            } finally { engine.stop(); root.deleteRecursively() }
        }
    }

}
