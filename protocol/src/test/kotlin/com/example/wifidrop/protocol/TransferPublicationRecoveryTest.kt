package com.example.wifidrop.protocol

import java.io.File
import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransferPublicationRecoveryTest {
    private class SimulatedProcessTermination : Error()

    @Test fun restartAfterMoveBeforeCallerAcknowledgementRecoversTheSameAttempt() = withDirectory { directory ->
        val source = File(directory, "verified.part").apply { writeText("complete verified bytes") }
        val total = source.length()
        val hash = sha256File(source)
        val stopsAfterMove = object : File(source.absolutePath) {
            override fun renameTo(destination: File): Boolean {
                Files.move(toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                throw SimulatedProcessTermination()
            }
        }
        assertFailsWith<SimulatedProcessTermination> {
            CompletedTransferReceipts(directory).publishVerified(stopsAfterMove, "peer", "attempt", "report.txt", total, hash)
        }
        val restarted = CompletedTransferReceipts(directory)
        val saved = assertNotNull(restarted.find("peer", "attempt", "report.txt", total, hash))
        assertEquals("complete verified bytes", saved.readText())
        assertFalse(source.exists())
        assertEquals(1, directory.listFiles()!!.count { it.isFile })
        assertNull(restarted.find("peer", "another-send", "report.txt", total, hash))
        assertNull(restarted.find("another-peer", "attempt", "report.txt", total, hash))
    }

    @Test fun receiptFailureRetainsVerifiedSourceAndDoesNotPublishEvenAnEmptyFile() {
        for (content in listOf("", "verified bytes")) withDirectory { directory ->
            val source = File(directory, "verified.part").apply { writeText(content) }
            val total = source.length()
            val hash = sha256File(source)
            // A filesystem error at the receipt location must happen before moving the payload.
            val blockedReceipts = File(directory, ".partial").apply { writeText("unavailable metadata directory") }
            val receipts = CompletedTransferReceipts(directory)
            assertFailsWith<IllegalStateException> {
                receipts.publishVerified(source, "peer", "attempt", "report.txt", total, hash)
            }
            assertEquals(content, source.readText())
            assertFalse(File(directory, "report.txt").exists())
            assertNull(receipts.find("peer", "attempt", "report.txt", total, hash))
            assertTrue(blockedReceipts.delete())
            val saved = receipts.publishVerified(source, "peer", "attempt", "report.txt", total, hash)
            assertEquals(content, saved.readText())
            assertNotNull(CompletedTransferReceipts(directory).find("peer", "attempt", "report.txt", total, hash))
        }
    }

    @Test fun cancellationAfterReceiptPreparationRemovesReservationAndKeepsResumableBytes() {
        for (content in listOf("", "verified bytes")) withDirectory { directory ->
            val source = File(directory, "verified.part").apply { writeText(content) }
            val total = source.length()
            val hash = sha256File(source)
            val receipts = CompletedTransferReceipts(directory)
            assertFailsWith<CancellationException> {
                receipts.publishVerified(source, "peer", "attempt", "report.txt", total, hash) {
                    val prepared = File(directory, ".partial").listFiles()?.any { it.name.endsWith(".receipt") } == true
                    if (prepared) throw CancellationException("cancel before publication")
                }
            }
            assertEquals(content, source.readText())
            assertFalse(File(directory, "report.txt").exists())
            assertNull(CompletedTransferReceipts(directory).find("peer", "attempt", "report.txt", total, hash))
            val saved = receipts.publishVerified(source, "peer", "attempt", "report.txt", total, hash)
            assertEquals(content, saved.readText())
            assertEquals(1, directory.listFiles()!!.count { it.isFile })
        }
    }

    @Test fun interruptedMoveCannotConfirmNonemptyContentFromAReservedName() = withDirectory { directory ->
        val source = File(directory, "verified.part").apply { writeText("verified bytes") }
        val hash = sha256File(source)
        val stopsBeforeMove = object : File(source.absolutePath) {
            override fun renameTo(destination: File): Boolean = throw SimulatedProcessTermination()
        }
        assertFailsWith<SimulatedProcessTermination> {
            CompletedTransferReceipts(directory).publishVerified(stopsBeforeMove, "peer", "attempt", "report.txt", source.length(), hash)
        }
        assertTrue(source.isFile)
        assertEquals(0L, File(directory, "report.txt").length())
        assertNull(CompletedTransferReceipts(directory).find("peer", "attempt", "report.txt", source.length(), hash))
        // Recovery does not overwrite or remove an existing file, even an empty reservation.
        val saved = CompletedTransferReceipts(directory).publishVerified(source, "peer", "attempt", "report.txt", source.length(), hash)
        assertEquals("report (1).txt", saved.name)
        assertEquals("verified bytes", saved.readText())
        assertEquals(0L, File(directory, "report.txt").length())
    }

    @Test fun verifiedZeroBytePayloadWithPreparedReceiptIsCompleteAtItsReservedDestination() = withDirectory { directory ->
        val source = File(directory, "verified.part").apply { writeBytes(byteArrayOf()) }
        val hash = sha256File(source)
        val stopsBeforeMove = object : File(source.absolutePath) {
            override fun renameTo(destination: File): Boolean = throw SimulatedProcessTermination()
        }
        assertFailsWith<SimulatedProcessTermination> {
            CompletedTransferReceipts(directory).publishVerified(stopsBeforeMove, "peer", "attempt", "empty.txt", 0L, hash)
        }
        // The caller has already received DONE and verified the empty payload before commit.
        // The reserved zero-byte file contains those complete bytes; cancellation/error above
        // instead removes the reservation, so those cases cannot be mistaken for acceptance.
        val saved = assertNotNull(CompletedTransferReceipts(directory).find("peer", "attempt", "empty.txt", 0L, hash))
        assertEquals("empty.txt", saved.name)
        assertEquals(0L, saved.length())
    }

    @Test fun preparedReceiptsPreserveTheExistingOnDiskFormat() = withDirectory { directory ->
        val source = File(directory, "verified.part").apply { writeText("verified bytes") }
        val total = source.length()
        val hash = sha256File(source)
        val saved = CompletedTransferReceipts(directory).publishVerified(source, "peer", "attempt", "report.txt", total, hash)
        val receipt = File(directory, ".partial").listFiles()!!.single { it.name.endsWith(".receipt") }
        DataInputStream(receipt.inputStream()).use { input ->
            assertEquals(saved.name, input.readUTF())
            assertEquals(total, input.readLong())
            assertEquals(hash, input.readUTF())
            assertEquals(-1, input.read())
        }
        assertFalse(File(directory, ".partial").listFiles()!!.any { it.name.endsWith(".pending") })
    }

    @Test fun changedPublishedFileIsNotAcknowledgedAfterRestart() = withDirectory { directory ->
        val source = File(directory, "verified.part").apply { writeText("original") }
        val total = source.length()
        val hash = sha256File(source)
        val target = CompletedTransferReceipts(directory).publishVerified(source, "peer", "attempt", "report.txt", total, hash)
        target.writeText("modified")
        assertNull(CompletedTransferReceipts(directory).find("peer", "attempt", "report.txt", total, hash))
        assertEquals("modified", target.readText())
    }

    @Test fun concurrentCollisionsKeepEachAttemptsReceiptBoundToItsOwnBytes() = withDirectory { directory ->
        val original = File(directory, "report.txt").apply { writeText("existing user document") }
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = executor.invokeAll((1..12).map { index -> Callable {
                val content = "payload-$index"
                val source = File(directory, "source-$index.part").apply { writeText(content) }
                val hash = sha256File(source)
                val total = source.length()
                val saved = CompletedTransferReceipts(directory).publishVerified(source, "peer", "attempt-$index", "report.txt", total, hash)
                val recovered = assertNotNull(CompletedTransferReceipts(directory).find("peer", "attempt-$index", "report.txt", total, hash))
                assertEquals(saved.canonicalFile, recovered.canonicalFile)
                assertEquals(content, recovered.readText())
                saved
            } }).map { it.get() }
            assertEquals(12, results.map { it.name }.toSet().size)
            assertEquals("existing user document", original.readText())
        } finally { executor.shutdownNow() }
    }

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("qetara-publication-recovery-").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }
}
