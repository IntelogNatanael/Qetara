package com.example.wifidrop

import com.example.wifidrop.protocol.flash.FlashPeer
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FlashOutgoingBatchTest {
    private val peer = FlashPeer("receiver", "Receiver", "192.168.1.5", 8989, Long.MAX_VALUE)
    private val files = listOf(File("first.txt"), File("second.txt"), File("third.txt"))

    @Test fun idleSnapshotsCannotAdvanceUntilThePreviousResultIsConsumed() {
        val batch = FlashOutgoingBatch()
        assertTrue(batch.start(files, peer))
        assertEquals(files[0], batch.next()!!.file)
        // send() itself can emit a synchronous state callback before returning the operation ID.
        assertNull(batch.next())
        batch.started("first")
        // The transport is idle, but its terminal callback is still waiting on the main thread.
        assertNull(batch.next())
        assertFalse(batch.start(listOf(File("replacement.txt")), peer))
        val first = batch.complete("first")!!
        assertEquals(1, first.completed)
        assertEquals(2, first.remaining)
        val next = batch.next()!!
        assertEquals(files[1], next.file)
        assertEquals(2, next.position)
        assertEquals(3, next.total)
    }

    @Test fun failureAfterIdleSnapshotNeverSendsTheNextFile() {
        val batch = FlashOutgoingBatch()
        batch.start(files, peer)
        batch.next()
        batch.started("first")
        assertNull(batch.next())
        batch.fail("first")
        assertNull(batch.next())
        assertFalse(batch.active)
        // A failed batch leaves the caller's selection intact and can be retried.
        assertTrue(batch.start(files, peer))
        assertEquals(files[0], batch.next()!!.file)
    }

    @Test fun cancellationStopsPendingFilesEvenWhenDeliveryAlreadyWonTheRace() {
        val batch = FlashOutgoingBatch()
        batch.start(files, peer)
        batch.next()
        batch.started("first")
        batch.cancelPending("first")
        assertTrue(batch.active)
        val completion = batch.complete("first")!!
        assertEquals(files[0], completion.file)
        assertEquals(1, completion.completed)
        assertEquals(0, completion.remaining)
        assertNull(batch.next())
        assertFalse(batch.active)
    }

    @Test fun unrelatedOrRepeatedCallbacksCannotReleaseTheCurrentFile() {
        val batch = FlashOutgoingBatch()
        batch.start(files, peer)
        batch.next()
        batch.started("first")
        batch.fail("incoming")
        batch.cancelPending("incoming")
        assertNull(batch.complete("incoming"))
        assertNull(batch.next())
        assertEquals(2, batch.complete("first")!!.remaining)
        batch.next()
        batch.started("second")
        assertNull(batch.complete("first"))
        batch.fail("first")
        assertNull(batch.next())
        assertEquals(2, batch.complete("second")!!.completed)
    }

    @Test fun completedBatchReleasesTheQueueAndRestartsItsCounters() {
        val batch = FlashOutgoingBatch()
        batch.start(files.take(2), peer)
        batch.next()
        batch.started("first")
        batch.complete("first")
        batch.next()
        batch.started("second")
        val result = batch.complete("second")!!
        assertEquals(2, result.completed)
        assertEquals(2, result.total)
        assertFalse(batch.active)
        assertTrue(batch.start(files.takeLast(1), peer))
        val next = batch.next()!!
        assertEquals(1, next.position)
        assertEquals(1, next.total)
    }
}
