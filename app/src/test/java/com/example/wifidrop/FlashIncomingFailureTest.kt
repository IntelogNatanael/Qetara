package com.example.wifidrop

import com.example.wifidrop.protocol.flash.FLASH_TEXT_MAX_BYTES
import com.example.wifidrop.protocol.flash.FlashApproval
import com.example.wifidrop.protocol.flash.FlashConfig
import com.example.wifidrop.protocol.flash.FlashEngine
import com.example.wifidrop.protocol.flash.FlashError
import com.example.wifidrop.protocol.flash.FlashListener
import com.example.wifidrop.protocol.flash.FlashOperation
import com.example.wifidrop.protocol.flash.FlashPeer
import com.example.wifidrop.protocol.flash.FlashReceived
import com.example.wifidrop.protocol.flash.FlashState
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class FlashIncomingFailureTest : LocalizedResourcesTest() {
    private val textName = "qetara-flash-text-v1-12345678-1234-1234-1234-123456789abc.txt"

    @Test fun incomingTextIntegrityFailureIsClassifiedBeforePublicationEvenAfterTheEngineGoesIdle() {
        val failure = corruptIncoming(textName, FLASH_TEXT_MAX_BYTES)
        val copy = flashOperationErrorText(failure.error.code, failure.operation, false, false)
        val result = recordFlashFailure(emptyList(), failure.error.operationId!!,
            failure.operation!!.fileName, copy, false).single()

        assertEquals("El texto no superó la verificación. Solicita un nuevo envío.", result.detail)
        assertEquals(FlashResultKind.FAILED, result.kind)
        assertNull(result.textBody)
        assertNull(result.file)
        assertNull(result.downloadUri)
        assertFalse(result.confirmationIssue)
        useAppLocale("en")
        assertEquals("The text failed verification. Request it again.", result.detail)
    }

    @Test fun ordinaryFilesAndOversizedReservedAttachmentsKeepTheFileIntegrityFailure() {
        listOf(
            "ordinary.txt" to 32,
            textName to FLASH_TEXT_MAX_BYTES + 1,
            "qetara-flash-text-v1-invalid.txt" to 32
        ).forEach { (name, size) ->
            val failure = corruptIncoming(name, size)
            val copy = flashOperationErrorText(failure.error.code, failure.operation, false, false)
            assertEquals(flashErrorText("integrity_failed"), copy)
        }
    }

    @Test fun incomingMetadataDoesNotPromiseAnOutgoingDraft() {
        val incoming = FlashOperation("incoming", null, textName, 32, outgoing = false)
        for (language in listOf("es", "en")) {
            useAppLocale(language)
            for (code in listOf("rejected", "invalid_file")) {
                val incomingCopy = flashOperationErrorText(code, incoming, false, false)
                assertEquals(flashErrorText(code), incomingCopy)
                assertFalse(incomingCopy.resolve().contains(if (language == "es") "borrador" else "draft"))
                val outgoingCopy = flashOperationErrorText(code, incoming.copy(outgoing = true), true, false)
                assertEquals(flashTextErrorText(code), outgoingCopy)
                assertTrue(outgoingCopy.resolve().contains(if (language == "es") "borrador" else "draft"))
            }
        }
    }

    @Test fun missingMetadataAndOrdinaryOutgoingAttachmentsDoNotImplyAnIncomingText() {
        val incoming = FlashOperation("incoming", null, textName, 32, outgoing = false)
        for (operation in listOf(null, incoming.copy(outgoing = true), incoming.copy(totalBytes = -1))) {
            assertEquals(flashErrorText("integrity_failed"),
                flashOperationErrorText("integrity_failed", operation, false, false))
        }
    }

    @Test fun aVerifiedTextReceiptUsesReceiveConfirmationCopyWithoutDraftPromises() {
        val receivedCopy = flashOperationErrorText("rejected", null, false, true)
        assertEquals("Texto recibido y verificado. No se pudo confirmar la entrega al otro equipo.", receivedCopy.resolve())
        useAppLocale("en")
        assertEquals("Text received and verified. Could not confirm delivery to the other device.", receivedCopy.resolve())
    }

    private data class Failure(val error: FlashError, val operation: FlashOperation?)

    /** Mutate the offered bytes after hashing, retaining the agreed length and bilateral approval. */
    private fun corruptIncoming(name: String, size: Int): Failure {
        Endpoint().use { sender -> Endpoint().use { receiver ->
            val source = File(sender.root, name).apply { writeBytes(ByteArray(size) { 1 }) }
            sender.engine.send(source, receiver.peer())
            val outgoing = sender.approval()
            val incoming = receiver.approval()
            assertEquals(outgoing.verificationCode, incoming.verificationCode)
            source.writeBytes(ByteArray(size) { 2 })
            assertTrue(sender.engine.approve(outgoing.requestId, true))
            assertTrue(receiver.engine.approve(incoming.requestId, true))

            val failure = receiver.failures.poll(10, TimeUnit.SECONDS)
            assertNotNull("Receiver did not report the changed payload", failure)
            assertEquals("integrity_failed", failure!!.error.code)
            assertEquals(incoming.operationId, failure.error.operationId)
            assertEquals(name, failure.operation?.fileName)
            assertEquals(size.toLong(), failure.operation?.totalBytes)
            assertEquals(false, failure.operation?.outgoing)
            assertTrue("Receiver did not finish cleanup", receiver.idle.await(10, TimeUnit.SECONDS))
            assertTrue("Sender did not finish cleanup", sender.idle.await(10, TimeUnit.SECONDS))
            assertTrue(receiver.engine.snapshot().operations.isEmpty())
            assertTrue(receiver.received.isEmpty())
            assertTrue(receiver.directory.walkTopDown().none { it.isFile })
            // Presentation is deliberately evaluated by the caller only after the empty snapshot.
            return failure
        } }
    }

    private class Endpoint : Closeable {
        val root: File = Files.createTempDirectory("qetara-incoming-failure-").toFile()
        val directory = File(root, "received")
        val approvals = LinkedBlockingQueue<FlashApproval>()
        val failures = LinkedBlockingQueue<Failure>()
        val received = LinkedBlockingQueue<FlashReceived>()
        val idle = CountDownLatch(1)
        private val operations = ConcurrentHashMap<String, FlashOperation>()
        private var observedOperation = false
        val engine = FlashEngine("Test device", directory, object : FlashListener {
            override fun onState(state: FlashState) {
                state.operations.forEach { operations[it.id] = it }
                if (state.operations.isNotEmpty()) observedOperation = true
                else if (observedOperation) idle.countDown()
            }

            override fun onApproval(approval: FlashApproval) { approvals.offer(approval) }
            override fun onReceived(received: FlashReceived) { this@Endpoint.received.offer(received) }
            override fun onError(error: FlashError) {
                failures.offer(Failure(error, error.operationId?.let(operations::get)))
            }
        }, FlashConfig(port = 0, bindAddress = "127.0.0.1", discoveryEnabled = false,
            lifetimeMs = 60_000, approvalTimeoutMs = 10_000,
            handshakeTimeoutMs = 2_000, transferTimeoutMs = 3_000))
        private val started = engine.start()

        fun peer() = FlashPeer(started.localId, "Test device", "127.0.0.1", started.port, started.expiresAtMs)
        fun approval(): FlashApproval = checkNotNull(approvals.poll(10, TimeUnit.SECONDS)) {
            "Approval missing; failures=$failures"
        }

        override fun close() {
            engine.stop()
            root.deleteRecursively()
        }
    }
}
