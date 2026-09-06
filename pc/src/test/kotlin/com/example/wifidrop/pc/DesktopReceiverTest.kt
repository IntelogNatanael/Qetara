package com.example.wifidrop.pc

import com.example.wifidrop.protocol.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.*

class DesktopReceiverTest {
    @Test
    fun legacyAndSecureProvisioningNeverExposeCredentialsOnDesktop() = withReceiver(allowCredentialsShare = true) { receiver ->
        for (packetType in listOf(PACKET_CREDENTIALS_REQUEST, PACKET_SECURE_CREDENTIALS_REQUEST)) {
            Socket("127.0.0.1", receiver.port).use { socket ->
                socket.soTimeout = 3000
                val output = DataOutputStream(socket.getOutputStream())
                output.writeInt(PROTOCOL_MAGIC)
                output.writeInt(PROTOCOL_VERSION)
                output.writeInt(packetType)
                output.writeUTF("test-client")
                output.writeUTF("Test client")
                output.writeUTF(randomNonce())
                output.flush()
                val input = DataInputStream(socket.getInputStream())
                assertEquals(PROTOCOL_MAGIC, input.readInt())
                assertEquals(PROTOCOL_VERSION, input.readInt())
                assertEquals(PACKET_RESULT, input.readInt())
                assertFalse(input.readBoolean())
                val message = input.readUTF()
                assertEquals(
                    if (packetType == PACKET_CREDENTIALS_REQUEST) "secure_credentials_required" else "emparejamiento_manual_requerido",
                    message
                )
                assertFalse(message.contains(receiver.sender.token))
                assertFalse(message.contains(receiver.sender.pin))
                assertEquals(-1, input.read(), "No extra credential bytes may follow the refusal")
            }
        }
    }

    @Test
    fun wrongCredentialsCannotPublishAFile() = withReceiver { receiver ->
        val payload = File(receiver.root, "private.txt").apply { writeText("Only the matching session may receive this") }
        assertFails {
            sendFileToPeer(payload, receiver.sender.copy(pin = "000000", retries = 1))
        }
        assertTrue(receiver.receiveDirectory.listFiles().orEmpty().none { it.isFile })
    }

    @Test
    fun completeTransferReportsProgressAndNeverOverwritesExistingFile() = withReceiver { receiver ->
        val payload = File(receiver.root, "informe.txt").apply {
            writeBytes(ByteArray(200_003) { (it % 251).toByte() })
        }
        val progress = mutableListOf<Long>()
        sendFileToPeer(payload, receiver.sender, onProgress = { sent, _ -> progress.add(sent) })
        val first = File(receiver.receiveDirectory, "informe.txt")
        assertTrue(first.isFile)
        assertContentEquals(payload.readBytes(), first.readBytes())
        assertEquals(0L, progress.first())
        assertEquals(payload.length(), progress.last())
        assertTrue(progress.zipWithNext().all { (before, after) -> after >= before })

        payload.writeText("The next version is a different file")
        sendFileToPeer(payload, receiver.sender)
        val published = receiver.receiveDirectory.listFiles().orEmpty().filter { it.isFile }
        assertEquals(2, published.size)
        assertEquals(200_003L, first.length(), "The original file must remain untouched")
        assertTrue(published.any { it.readBytes().contentEquals(payload.readBytes()) })
    }

    @Test
    fun emptyFileIsPublishedAndVerified() = withReceiver { receiver ->
        val payload = File(receiver.root, "empty.txt").apply { writeBytes(byteArrayOf()) }
        sendFileToPeer(payload, receiver.sender)
        assertEquals(0L, File(receiver.receiveDirectory, "empty.txt").length())
        assertTrue(File(receiver.receiveDirectory, "empty.txt").isFile)
    }

    @Test
    fun cancellingAnActiveSendDoesNotPublishIncompleteData() = withReceiver { receiver ->
        val payload = File(receiver.root, "cancelled.bin").apply { writeBytes(ByteArray(100_000) { 42 }) }
        val cancellation = DesktopTransferCancellation()
        assertFails {
            sendFileToPeer(payload, receiver.sender, cancellation) { _, _ -> cancellation.cancel() }
        }
        assertTrue(cancellation.isCancelled)
        assertFalse(File(receiver.receiveDirectory, payload.name).exists())
    }

    @Test
    fun receiverStopsAfterRealSessionExpiry() = withReceiver(sessionDurationMs = 50L) { receiver ->
        receiver.worker.join(2500)
        assertFalse(receiver.worker.isAlive, "Receiver must close after the session deadline")
        assertFails { Socket("127.0.0.1", receiver.port).close() }
    }

    @Test
    fun stopClosesAnIdleUnauthenticatedClient() = withReceiver { receiver ->
        Socket("127.0.0.1", receiver.port).use { socket ->
            socket.soTimeout = 3000
            receiver.server.stop()
            receiver.worker.join(2000)
            assertFalse(receiver.worker.isAlive)
            val result = runCatching { socket.getInputStream().read() }
            assertTrue(result.isFailure || result.getOrNull() == -1)
        }
    }

    @Test
    fun directMessagePreservesParagraphsAndLength() = withReceiver { receiver ->
        val message = ("Párrafo uno.\n\nPárrafo dos con espacios. ".repeat(60)).take(2000).trim()
        sendMessageToPeer(message, DesktopChatScope.DIRECT, receiver.sender)
        assertEquals(message, receiver.messages.single().message)
        assertEquals(DesktopChatScope.DIRECT, receiver.messages.single().scope)
    }

    @Test
    fun channelMessagesAreRejectedWhenReceiverHasNotJoined() = withReceiver { receiver ->
        val error = assertFails {
            sendMessageToPeer("Solo para miembros", DesktopChatScope.GLOBAL_LAN, receiver.sender)
        }
        assertTrue(error.message.orEmpty().contains("canal_no_unido"))
        assertTrue(receiver.messages.isEmpty())
    }

    @Test
    fun repeatingACompletedFileAttemptReturnsReceiptWithoutAnotherCopy() = withReceiver { receiver ->
        val payload = File(receiver.root, "once.txt").apply { writeText("One completed attempt") }
        val attempt = "stable-test-attempt-1234"
        sendFileToPeer(payload, receiver.sender, attemptId = attempt)
        sendFileToPeer(payload, receiver.sender, attemptId = attempt)
        assertEquals(1, receiver.receiveDirectory.listFiles().orEmpty().count { it.isFile })
        assertContentEquals(payload.readBytes(), File(receiver.receiveDirectory, "once.txt").readBytes())
    }

    @Test
    fun repeatingAMessageAttemptDoesNotDuplicateTheConversation() = withReceiver { receiver ->
        sendMessageToPeer("Una vez", DesktopChatScope.DIRECT, receiver.sender, messageId = "same-message-attempt")
        sendMessageToPeer("Una vez", DesktopChatScope.DIRECT, receiver.sender, messageId = "same-message-attempt")
        assertEquals(listOf("Una vez"), receiver.messages.map { it.message })
    }

    @Test
    fun rejectedUiDeliveryIsNotAcknowledgedOrRememberedAsDelivered() =
        withReceiver(acceptMessage = {
            deliverDesktopMessageOnUi { throw IllegalStateException("canal_no_unido") }
        }) { receiver ->
            val failure = assertFails {
                sendMessageToPeer("Solicitud tardía", DesktopChatScope.DIRECT, receiver.sender, messageId = "rejected-ui-request")
            }
            assertTrue(failure.message.orEmpty().contains("canal_no_unido"))
            assertTrue(receiver.messages.isEmpty())
            assertTrue(File(receiver.receiveDirectory, ".partial").listFiles().orEmpty().none { it.name.startsWith("message_") })
        }

    private data class RunningReceiver(
        val root: File,
        val receiveDirectory: File,
        val port: Int,
        val sender: SenderConfig,
        val server: PcReceiverServer,
        val worker: Thread,
        val messages: List<DesktopChatEntry>
    )

    private fun withReceiver(
        allowCredentialsShare: Boolean = false,
        sessionDurationMs: Long = 60_000L,
        acceptMessage: (DesktopChatEntry) -> Unit = {},
        block: (RunningReceiver) -> Unit
    ) {
        val root = Files.createTempDirectory("qetara-desktop-test-").toFile()
        val destination = File(root, "received").apply { mkdirs() }
        val identity = DesktopIdentityStore.getOrCreateNoiseIdentity(File(root, "identity").apply { mkdirs() })
        val port = ServerSocket(0).use { it.localPort }
        val ready = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val messages = java.util.concurrent.CopyOnWriteArrayList<DesktopChatEntry>()
        val server = PcReceiverServer(
            ReceiverConfig("QETARA24", "927461", destination, port, "Test receiver", sessionDurationMs, allowCredentialsShare, "test-receiver"),
            identity,
            onReady = { ready.countDown() },
            onMessageReceived = { acceptMessage(it); messages.add(it) }
        )
        val worker = thread(name = "qetara-test-receiver", isDaemon = true) {
            try { server.runBlocking() } catch (error: Throwable) { failure.set(error); ready.countDown() }
        }
        try {
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Receiver did not become ready")
            failure.get()?.let { throw it }
            val sender = SenderConfig("QETARA24", "927461", "127.0.0.1", port, "test-client", "Test client", 1, identity)
            block(RunningReceiver(root, destination, port, sender, server, worker, messages))
        } finally {
            server.stop()
            worker.join(3000)
            root.deleteRecursively()
        }
    }
}
