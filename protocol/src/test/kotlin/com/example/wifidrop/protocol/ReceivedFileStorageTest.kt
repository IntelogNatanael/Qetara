package com.example.wifidrop.protocol

import java.io.File
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceivedFileStorageTest {
    @Test fun userNamesRemainReadableAndPortable() {
        assertEquals("Informe de Espa\u00f1a.pdf", sanitizeFileName("Informe de Espa\u00f1a.pdf"))
        assertEquals("_CON.txt", sanitizeFileName("CON.txt"))
        assertEquals("_lpt1.log", sanitizeFileName("lpt1.log"))
        assertEquals("file", sanitizeFileName("...", "..."))
        for (name in listOf("../../notes.txt", "C:\\windows\\system.ini", "\u202Etxt.exe", ".partial", "a/b.txt")) {
            val safe = sanitizeFileName(name)
            assertFalse(safe.contains('/'))
            assertFalse(safe.contains('\\'))
            assertFalse(safe.startsWith('.'))
            assertFalse(safe.endsWith('.'))
            assertFalse(safe.contains(".."))
        }
        assertTrue(sanitizeFileName("\u4e2d".repeat(180)).toByteArray().size <= 180)
    }

    @Test fun filenameCollisionsNeverReplaceExistingContents() = withDirectory { directory ->
        val old = File(directory, "report.txt").apply { writeText("original") }
        val partial = File(directory, "temp.part").apply { writeText("new contents") }
        val saved = publishReceivedFile(partial, directory, "report.txt")
        assertEquals("original", old.readText())
        assertEquals("report (1).txt", saved.name)
        assertEquals("new contents", saved.readText())
        assertFalse(partial.exists())
    }

    @Test fun racingReceiversGetDifferentFilesWithoutLoss() = withDirectory { directory ->
        val executor = Executors.newFixedThreadPool(4)
        try {
            val jobs = (1..12).map { index -> Callable {
                val source = File(directory, "source-$index.part").apply { writeText("payload-$index") }
                publishReceivedFile(source, directory, "document.txt")
            } }
            val results = executor.invokeAll(jobs).map { it.get() }
            assertEquals(12, results.map { it.name }.toSet().size)
            assertEquals((1..12).map { "payload-$it" }.toSet(), results.map { it.readText() }.toSet())
        } finally { executor.shutdownNow() }
    }

    @Test fun lostAcknowledgementResumesCompletedAttemptWithoutDuplicate() = withDirectory { directory ->
        val target = File(directory, "received.txt").apply { writeText("verified content") }
        val hash = sha256File(target)
        val receipts = CompletedTransferReceipts(directory)
        receipts.remember("peer", "attempt", "received.txt", target.length(), hash, target)
        assertEquals(target.canonicalFile, assertNotNull(receipts.find("peer", "attempt", "received.txt", target.length(), hash)).canonicalFile)
        assertNull(receipts.find("peer", "new-intentional-send", "received.txt", target.length(), hash))
        assertNull(receipts.find("another-peer", "attempt", "received.txt", target.length(), hash))
        assertEquals(1, directory.listFiles()!!.count { it.isFile })
    }

    @Test fun modifiedOrDeletedCompletedFilesAreNeverAcceptedAsSuccessful() = withDirectory { directory ->
        val target = File(directory, "received.txt").apply { writeText("original") }
        val originalSize = target.length()
        val hash = sha256File(target)
        val receipts = CompletedTransferReceipts(directory)
        receipts.remember("peer", "attempt", target.name, originalSize, hash, target)
        target.writeText("modified")
        assertNull(receipts.find("peer", "attempt", target.name, originalSize, hash))
        target.delete()
        assertNull(receipts.find("peer", "attempt", target.name, originalSize, hash))
    }

    @Test fun exportRetainsOriginalAndPublishesOnlyACompleteUniqueCopy() = withDirectory { directory ->
        val source = File(directory, "source.txt").apply { writeText("verified original") }
        val exports = File(directory, "exports").apply { mkdirs() }
        File(exports, "source.txt").writeText("existing user file")
        val copy = copyReceivedFile(source, exports)
        assertEquals("verified original", source.readText())
        assertEquals("verified original", copy.readText())
        assertEquals("existing user file", File(exports, "source.txt").readText())
        assertEquals("source (1).txt", copy.name)
        assertFalse(exports.listFiles()!!.any { it.name.startsWith(".qetara-export-") })
    }

    @Test fun publicationFallbackMovesVerifiedBytesWithoutStreamingIntoFinalName() = withDirectory { directory ->
        val original = File(directory, "verified.part").apply { writeText("complete data") }
        val cannotRename = object : File(original.absolutePath) {
            override fun renameTo(destination: File): Boolean = false
        }
        val saved = publishReceivedFile(cannotRename, directory, "saved.txt")
        assertEquals("complete data", saved.readText())
        assertFalse(original.exists())
    }

    @Test fun canceledReceiptVerificationDoesNotSilentlyRestartOrDeleteTheSavedFile() = withDirectory { directory ->
        val target = File(directory, "received.txt").apply { writeText("original") }
        val hash = sha256File(target)
        val receipts = CompletedTransferReceipts(directory)
        receipts.remember("peer", "attempt", target.name, target.length(), hash, target)
        assertFailsWith<java.util.concurrent.CancellationException> {
            receipts.find("peer", "attempt", target.name, target.length(), hash) {
                throw java.util.concurrent.CancellationException("user canceled")
            }
        }
        assertEquals("original", target.readText())
    }

    @Test fun cancelingExportRemovesStagingAndKeepsOriginal() = withDirectory { directory ->
        val source = File(directory, "source.txt").apply { writeText("original") }
        val exports = File(directory, "exports").apply { mkdirs() }
        assertFailsWith<java.util.concurrent.CancellationException> {
            copyReceivedFile(source, exports) { throw java.util.concurrent.CancellationException("stop") }
        }
        assertEquals("original", source.readText())
        assertEquals(0, exports.listFiles()!!.size)
    }

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("qetara-storage-test-").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }
}
