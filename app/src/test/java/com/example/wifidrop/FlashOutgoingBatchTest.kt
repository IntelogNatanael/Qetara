package com.example.wifidrop

import com.example.wifidrop.protocol.flash.FlashPeer
import com.example.wifidrop.protocol.flash.FlashOperation
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FlashOutgoingBatchTest {
    private val peer = FlashPeer("receiver", "Receiver", "192.168.1.5", 8989, Long.MAX_VALUE)
    private val files = listOf(File("first.txt"), File("second.txt"), File("third.txt"))

    private fun operation(index: Int, batch: String = "batch") = FlashOperation(
        if (index == 0) batch else "$batch-$index", peer, files[index].name, 0, true,
        batchId = batch, fileIndex = index, fileCount = files.size
    )

    @Test fun oneSubmissionTracksDistinctFileResultsUntilTheBatchEnds() {
        val batch = FlashOutgoingBatch()
        assertTrue(batch.start(files))
        assertFalse(batch.start(listOf(File("replacement.txt"))))
        files.indices.forEach { index ->
            val current = operation(index)
            batch.observe(current)
            val result = batch.complete(current.id)!!
            assertEquals(files[index], result.file)
            assertEquals(index + 1, result.completed)
            assertEquals(files.size - index - 1, result.remaining)
        }
        assertFalse(batch.active)
        batch.started("batch") // A late transport return cannot resurrect the completed batch.
        assertFalse(batch.active)
    }

    @Test fun failureAfterTheFirstDeliveryReleasesTheRemainingSelectionForExplicitRetry() {
        val batch = FlashOutgoingBatch()
        batch.start(files)
        batch.started("batch")
        batch.observe(operation(0))
        assertEquals(files[0], batch.complete("batch")!!.file)
        batch.observe(operation(1))
        batch.fail("batch-1")
        assertFalse(batch.active)
        assertTrue(batch.start(files.drop(1)))
    }

    @Test fun cancellationStopsPendingFilesEvenWhenDeliveryAlreadyWonTheRace() {
        val batch = FlashOutgoingBatch()
        batch.start(files)
        batch.observe(operation(0))
        batch.cancelPending("batch")
        assertTrue(batch.active)
        val completion = batch.complete("batch")!!
        assertEquals(files[0], completion.file)
        assertEquals(1, completion.completed)
        assertEquals(0, completion.remaining)
        batch.observe(operation(1))
        assertEquals(files[1], batch.complete("batch-1")!!.file)
        batch.idle()
        assertFalse(batch.active)
    }

    @Test fun unrelatedOrRepeatedCallbacksCannotReleaseTheCurrentFile() {
        val batch = FlashOutgoingBatch()
        batch.start(files)
        batch.observe(operation(0))
        batch.fail("incoming")
        batch.cancelPending("incoming")
        assertNull(batch.complete("incoming"))
        assertEquals(2, batch.complete("batch")!!.remaining)
        batch.observe(operation(1))
        batch.observe(operation(2, "unrelated"))
        assertNull(batch.complete("batch"))
        assertNull(batch.complete("unrelated-2"))
        assertEquals(2, batch.complete("batch-1")!!.completed)
    }

    @Test fun idleAfterCancellationBetweenFilesCannotLeaveTheBatchActive() {
        val batch = FlashOutgoingBatch()
        batch.start(files)
        batch.observe(operation(0))
        batch.complete("batch")
        batch.idle()
        assertFalse(batch.active)
        assertTrue(batch.start(files))
        batch.observe(operation(0, "retry"))
        assertEquals(1, batch.complete("retry")!!.completed)
    }

    @Test fun previouslyQueuedIdleSnapshotDoesNotEraseASubmissionBeforeItsReservationIsObserved() {
        val batch = FlashOutgoingBatch()
        batch.start(files)
        batch.started("batch")
        batch.idle()
        assertTrue(batch.active)
        batch.observe(operation(0))
        assertEquals(files[0], batch.complete("batch")!!.file)
        batch.observe(operation(0))
        assertNull(batch.complete("batch"))
    }
}
