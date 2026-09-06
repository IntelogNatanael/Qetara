package com.example.wifidrop.pc

import com.example.wifidrop.protocol.PACKET_MESSAGE
import com.example.wifidrop.protocol.PROTOCOL_MAGIC
import com.example.wifidrop.protocol.PROTOCOL_VERSION
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.*

class DesktopMessageCancellationTest {
    @Test
    fun cancelledBeforeStartDoesNotContactTheRecipient() = withSender { config, listener ->
        val cancellation = DesktopTransferCancellation().apply { cancel() }
        assertFailsWith<CancellationException> {
            sendMessageToPeer("Do not deliver", DesktopChatScope.DIRECT, config, cancellation = cancellation)
        }
        listener.soTimeout = 200
        assertFailsWith<SocketTimeoutException> { listener.accept().close() }
    }

    @Test
    fun cancellationClosesAStalledMessageSocketAndPreventsRetry() = withSender { config, listener ->
        val connected = CountDownLatch(1)
        val remoteClosed = CountDownLatch(1)
        val senderDone = CountDownLatch(1)
        val accepted = AtomicReference<Socket?>()
        val result = AtomicReference<Throwable?>()
        val cancellation = DesktopTransferCancellation()
        val recipient = thread(isDaemon = true, name = "qetara-test-stalled-recipient") {
            runCatching {
                listener.accept().use { socket ->
                    accepted.set(socket)
                    val input = DataInputStream(socket.getInputStream())
                    assertEquals(PROTOCOL_MAGIC, input.readInt())
                    assertEquals(PROTOCOL_VERSION, input.readInt())
                    assertEquals(PACKET_MESSAGE, input.readInt())
                    repeat(3) { input.readUTF() }
                    connected.countDown()
                    // Deliberately withhold the challenge; cancellation must close this read.
                    if (input.read() == -1) remoteClosed.countDown()
                }
            }
        }
        val sender = thread(isDaemon = true, name = "qetara-test-message-sender") {
            result.set(runCatching {
                sendMessageToPeer("Pending", DesktopChatScope.DIRECT, config, cancellation = cancellation)
            }.exceptionOrNull())
            senderDone.countDown()
        }
        try {
            assertTrue(connected.await(3, TimeUnit.SECONDS), "Sender must reach the blocked handshake")
            cancellation.cancel()
            assertTrue(senderDone.await(3, TimeUnit.SECONDS), "Cancel must not wait for the 60-second socket timeout")
            assertIs<CancellationException>(result.get())
            assertTrue(remoteClosed.await(2, TimeUnit.SECONDS), "The recipient must observe the socket closing")
            listener.soTimeout = 200
            assertFailsWith<SocketTimeoutException> { listener.accept().close() }
        } finally {
            cancellation.cancel()
            accepted.get()?.close()
            listener.close()
            sender.join(1000)
            recipient.join(1000)
        }
    }

    private fun withSender(block: (SenderConfig, ServerSocket) -> Unit) {
        val state = Files.createTempDirectory("qetara-message-cancel-test").toFile()
        try {
            ServerSocket(0).use { listener ->
                listener.soTimeout = 3000
                block(
                    SenderConfig(
                        token = "TESTCODE",
                        pin = "123456",
                        targetHost = "127.0.0.1",
                        port = listener.localPort,
                        clientId = "message-cancel-fixture",
                        clientLabel = "Test sender",
                        retries = 3,
                        localNoiseIdentity = DesktopIdentityStore.getOrCreateNoiseIdentity(state)
                    ),
                    listener
                )
            }
        } finally {
            state.deleteRecursively()
        }
    }
}
