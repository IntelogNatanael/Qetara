package com.example.wifidrop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

/** Real loopback reads prove cancellation releases blocking I/O instead of waiting for SO_TIMEOUT. */
class SocketCancellationTest {
    @Test fun coroutineCancellationClosesBlockedReadAndItsPeer() = runBlocking {
        withSocketPair { client, receiver ->
            val reading = CompletableDeferred<Unit>()
            val worker = launch(Dispatchers.IO) {
                FileTransfer.withSocketCancellation(receiver) { socket ->
                    reading.complete(Unit)
                    socket.getInputStream().read()
                }
            }
            withTimeout(2_000) { reading.await() }
            withTimeout(2_000) { worker.cancelAndJoin() }
            assertTrue(receiver.isClosed)
            client.soTimeout = 1_000
            assertEquals(-1, client.getInputStream().read())
        }
    }

    @Test fun generationChangeAbortsBlockedReadEvenAfterTransientCancelFlagClears() = runBlocking {
        withSocketPair { client, receiver ->
            val generation = AtomicLong(0)
            val acceptedGeneration = generation.get()
            val reading = CompletableDeferred<Unit>()
            val worker = async(Dispatchers.IO) {
                FileTransfer.withSocketCancellation(receiver, { generation.get() != acceptedGeneration }) { socket ->
                    reading.complete(Unit)
                    socket.getInputStream().read()
                }
            }
            withTimeout(2_000) { reading.await() }
            generation.incrementAndGet()
            val failure = runCatching { withTimeout(2_000) { worker.await() } }.exceptionOrNull()
            assertTrue(failure is CancellationException)
            assertEquals("cancelado por usuario", failure?.message)
            assertTrue(receiver.isClosed)
            client.soTimeout = 1_000
            assertEquals(-1, client.getInputStream().read())
        }
    }

    @Test fun normalCompletionAlsoClosesTheConnection() = runBlocking {
        withSocketPair { client, receiver ->
            client.getOutputStream().write(42)
            val value = FileTransfer.withSocketCancellation(receiver) { socket -> socket.getInputStream().read() }
            assertEquals(42, value)
            assertTrue(receiver.isClosed)
            client.soTimeout = 1_000
            assertEquals(-1, client.getInputStream().read())
        }
    }

    private suspend fun withSocketPair(block: suspend (Socket, Socket) -> Unit) {
        val address = InetAddress.getLoopbackAddress()
        ServerSocket(0, 1, address).use { listener ->
            Socket(address, listener.localPort).use { client ->
                listener.accept().use { receiver ->
                    receiver.soTimeout = 30_000
                    block(client, receiver)
                }
            }
        }
    }
}
