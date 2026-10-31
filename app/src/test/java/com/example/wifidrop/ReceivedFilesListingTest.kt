package com.example.wifidrop

import com.example.wifidrop.protocol.flash.FLASH_TEXT_MAX_BYTES
import com.example.wifidrop.protocol.flash.FlashText as FlashTextAttachment
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ReceivedFilesListingTest {
    @Test fun fiveDocumentsAndTwoConsumedTextsListOnlyDocumentsWithoutChangingAnyBytes() = fixture { dir, receipts ->
        val documents = (1..5).map { index -> File(dir, "document-$index.txt").apply { writeText("Document $index") } }
        val bodies = listOf("  Hola, Perú 👋\r\n漢字  ", "Second Flash message")
        val texts = bodies.map { body ->
            FlashTextAttachment.createFile(dir, body).also { assertTrue(receipts.recordConsumed(it, body)) }
        }
        val originalBytes = (documents + texts).associateWith { it.readBytes() }
        val listed = loadReceivedFiles(dir, receipts::isConsumedText)
        assertEquals(documents.toSet(), listed.toSet())
        assertEquals(5, listed.size)
        assertEquals(documents.sumOf { it.length() }, listed.sumOf { it.length() })
        assertEquals(7, dir.listFiles()!!.count { it.isFile })
        originalBytes.forEach { (file, bytes) -> assertArrayEquals(bytes, file.readBytes()) }
        texts.zip(bodies).forEach { (file, body) -> assertEquals(body, FlashTextAttachment.readReceived(file)) }
    }

    @Test fun legacyAndOrdinaryFilesAreNotHiddenByAReservedNameOrPrefixAlone() = fixture { dir, receipts ->
        val ordinary = File(dir, "normal.txt").apply { writeText("Normal file") }
        val prefixOnly = File(dir, "qetara-flash-text-v1-not-a-uuid.txt").apply { writeText("Normal file") }
        // This could be a legacy Flash container OR a normally transferred file. Without provenance,
        // the library must not guess, even though the reserved name and UTF-8 body are valid.
        val ambiguous = FlashTextAttachment.createFile(dir, "Unmarked content")
        assertFalse(receipts.recordConsumed(ordinary, "Normal file"))
        assertFalse(receipts.recordConsumed(prefixOnly, "Normal file"))
        assertEquals(setOf(ordinary, prefixOnly, ambiguous), loadReceivedFiles(dir, receipts::isConsumedText).toSet())
    }

    @Test fun replacementAtTheSamePathWithSameSizeAndTimestampIsVisibleWhenBytesDiffer() = fixture { dir, receipts ->
        val text = FlashTextAttachment.createFile(dir, "AAAA")
        assertTrue(receipts.recordConsumed(text, "AAAA"))
        val originalTime = text.lastModified()
        text.writeText("BBBB")
        assertTrue(text.setLastModified(originalTime))
        assertFalse(receipts.isConsumedText(text))
        assertEquals(listOf(text), loadReceivedFiles(dir, receipts::isConsumedText))
        assertEquals("BBBB", text.readText())
    }

    @Test fun aReceiptSurvivesReloadButDoesNotFollowANormallyReceivedCopyAtAnotherPath() = fixture { dir, receipts ->
        val text = FlashTextAttachment.createFile(dir, "Exact body")
        assertTrue(receipts.recordConsumed(text, "Exact body"))
        val reloaded = FlashTextReceiptStore(File(dir.parentFile, "private-receipts"))
        assertTrue(reloaded.isConsumedText(text))
        val otherDir = File(dir.parentFile, "other-received").apply { mkdirs() }
        val copy = File(otherDir, text.name).apply { writeBytes(text.readBytes()) }
        assertFalse(reloaded.isConsumedText(copy))
        assertEquals(listOf(copy), loadReceivedFiles(otherDir, reloaded::isConsumedText))
    }

    @Test fun aChangedFileCannotBeMarkedAsThePreviouslyConsumedBody() = fixture { dir, receipts ->
        val text = FlashTextAttachment.createFile(dir, "AAAA")
        val consumed = FlashTextAttachment.readReceived(text)!!
        text.writeText("BBBB")
        assertFalse(receipts.recordConsumed(text, consumed))
        assertEquals(listOf(text), loadReceivedFiles(dir, receipts::isConsumedText))
        assertEquals("AAAA", consumed)
        assertEquals("BBBB", text.readText())
    }

    @Test fun oversizedOrInvalidUtf8ReplacementsAreVisibleAndTheSizeLimitIsInclusive() = fixture { dir, receipts ->
        val text = FlashTextAttachment.createFile(dir, "x".repeat(FLASH_TEXT_MAX_BYTES))
        assertTrue(receipts.recordConsumed(text, "x".repeat(FLASH_TEXT_MAX_BYTES)))
        assertTrue(loadReceivedFiles(dir, receipts::isConsumedText).isEmpty())
        text.writeBytes(ByteArray(FLASH_TEXT_MAX_BYTES + 1) { 120 })
        assertFalse(receipts.recordConsumed(text, "x".repeat(FLASH_TEXT_MAX_BYTES)))
        assertEquals(listOf(text), loadReceivedFiles(dir, receipts::isConsumedText))
        text.writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
        assertEquals(listOf(text), loadReceivedFiles(dir, receipts::isConsumedText))
        assertArrayEquals(byteArrayOf(0xc3.toByte(), 0x28), text.readBytes())
    }

    private fun fixture(block: (File, FlashTextReceiptStore) -> Unit) {
        val root = Files.createTempDirectory("qetara-received-files-").toFile()
        try {
            val received = File(root, "received").apply { mkdirs() }
            block(received, FlashTextReceiptStore(File(root, "private-receipts")))
        } finally {
            root.deleteRecursively()
        }
    }
}
