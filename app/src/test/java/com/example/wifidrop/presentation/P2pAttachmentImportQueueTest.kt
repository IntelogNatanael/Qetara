package com.example.wifidrop.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class P2pAttachmentImportQueueTest {
    @Test
    fun cancellingOneProviderReadAllowsTheNextSelectionToFinish() = runBlocking {
        val queue = P2pAttachmentImportQueue()
        val blocked = CompletableDeferred<String>()
        val selected = mutableListOf<String>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            queue.append(load = { blocked.await() }) { selected.add(it) }
        }
        val second = launch(start = CoroutineStart.UNDISPATCHED) {
            queue.append(load = { "next.pdf" }) { selected.add(it) }
        }
        first.cancelAndJoin()
        second.join()
        assertEquals(listOf("next.pdf"), selected)
    }

    @Test
    fun aSecondSelectionWaitsForAndAccumulatesTheFirstSelection() = runBlocking {
        val queue = P2pAttachmentImportQueue()
        val firstResult = CompletableDeferred<List<String>>()
        var selected = emptyList<String>()
        val commit: (List<String>) -> Unit = { selected = mergeAttachmentFiles(selected, it) { file -> file } }
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            queue.append(load = { firstResult.await() }, commit = commit)
        }
        val second = launch(start = CoroutineStart.UNDISPATCHED) {
            queue.append(load = { listOf("report.pdf", "notes.txt") }, commit = commit)
        }
        assertEquals(emptyList<String>(), selected)
        firstResult.complete(listOf("photo.jpg", "report.pdf"))
        first.join()
        second.join()
        assertEquals(listOf("photo.jpg", "report.pdf", "notes.txt"), selected)
    }

    @Test
    fun clearingDiscardsBothLoadingAndWaitingSelectionsButAllowsNewFiles() = runBlocking {
        val queue = P2pAttachmentImportQueue()
        val firstResult = CompletableDeferred<String>()
        val selected = mutableListOf<String>()
        var obsoleteLoadStarted = false
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            queue.append(load = { firstResult.await() }) { selected.add(it) }
        }
        val waiting = launch(start = CoroutineStart.UNDISPATCHED) {
            queue.append(load = { obsoleteLoadStarted = true; "discarded.pdf" }) { selected.add(it) }
        }
        queue.replace()
        val fresh = launch(start = CoroutineStart.UNDISPATCHED) {
            queue.append(load = { "new.pdf" }) { selected.add(it) }
        }
        // A slow discarded provider must not block the user's new selection.
        assertEquals(listOf("new.pdf"), selected)
        firstResult.complete("old.pdf")
        first.join()
        waiting.join()
        fresh.join()
        assertFalse(obsoleteLoadStarted)
        assertEquals(listOf("new.pdf"), selected)
    }

    @Test
    fun pickingFilesInvalidatesAnOlderRestoreOrExternalShare() = runBlocking {
        val queue = P2pAttachmentImportQueue()
        val restoration = queue.replace()
        queue.append(load = { "new.pdf" }) {}
        assertFalse(queue.isCurrentReplacement(restoration))
    }

    @Test
    fun replacingOneContextDoesNotDiscardAnImportInAnotherContext() = runBlocking {
        val files = P2pAttachmentImportQueue()
        val chat = P2pAttachmentImportQueue()
        val loaded = CompletableDeferred<String>()
        val selected = mutableListOf<String>()
        val pending = launch(start = CoroutineStart.UNDISPATCHED) {
            chat.append(load = { loaded.await() }) { selected.add(it) }
        }
        files.replace()
        loaded.complete("private.pdf")
        pending.join()
        assertEquals(listOf("private.pdf"), selected)
    }
}
