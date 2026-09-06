package com.example.wifidrop.protocol

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceivedMessageReceiptsTest {
    @Test fun acknowledgedDeliveryIsNotRepeatedWhenTheSameAttemptReconnects() = withDirectory { directory ->
        val receipts = ReceivedMessageReceipts(directory)
        var delivered = 0
        assertTrue(receipts.deliverOnce("sender", "attempt-1", "Hello") { delivered++ })
        assertFalse(receipts.deliverOnce("sender", "attempt-1", "Hello") { delivered++ })
        val restartedReceiver = ReceivedMessageReceipts(directory)
        assertFalse(restartedReceiver.deliverOnce("sender", "attempt-1", "Hello") { delivered++ })
        assertTrue(restartedReceiver.deliverOnce("sender", "attempt-2", "Hello") { delivered++ })
        assertEquals(2, delivered)
    }

    @Test fun failedDeliveryCanBeRetriedAndOtherSendersRemainIndependent() = withDirectory { directory ->
        val receipts = ReceivedMessageReceipts(directory)
        assertFailsWith<IllegalStateException> {
            receipts.deliverOnce("sender", "attempt", "Hello") { error("storage unavailable") }
        }
        assertTrue(receipts.deliverOnce("sender", "attempt", "Hello") {})
        assertTrue(receipts.deliverOnce("other-sender", "attempt", "Hello") {})
    }

    @Test fun transportMetadataAndParagraphsArePreservedWithoutSilentTruncation() {
        val envelope = "\u2063QCT\u2063" + "{\"text\":\"" + "x".repeat(2000) + "\",\"kind\":\"user\"}"
        assertEquals(envelope, requireValidTransportMessage(envelope))
        assertEquals("first\nsecond  paragraph", requireValidTransportMessage("first\nsecond  paragraph"))
        assertFailsWith<IllegalArgumentException> { requireValidTransportMessage("x".repeat(MAX_TRANSPORT_MESSAGE_CHARS + 1)) }
    }

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("qetara-message-test-").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }
}
