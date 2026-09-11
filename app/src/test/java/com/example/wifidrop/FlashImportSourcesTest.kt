package com.example.wifidrop

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FlashImportSourcesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun repeatedSelectionsImportOnlyNewUrisAndKeepDifferentFilesWithTheSameName() {
        val sources = FlashImportSources()
        val first = File(temporary.newFolder("first"), "photo.jpg").apply { writeText("first") }
        val second = File(temporary.newFolder("second"), "photo.jpg").apply { writeText("second") }
        var selection = sources.commit(listOf("content://first" to first), emptyList())
        assertEquals(listOf("content://second"),
            sources.pending(listOf("content://first", "content://second", "content://second"), selection))
        selection = sources.commit(listOf("content://second" to second), selection)
        assertEquals(listOf(first, second), selection)
        assertTrue(sources.pending(listOf("content://first", "content://second"), selection).isEmpty())
    }

    @Test fun failedPreparationDoesNotClaimSourcesOrDiscardPreviousSelections() {
        val sources = FlashImportSources()
        val first = temporary.newFile("first.txt")
        val selection = sources.commit(listOf("content://first" to first), emptyList())
        assertEquals(listOf("content://new"), sources.pending(listOf("content://new"), selection))
        // No commit occurs after cancellation or failure, so a later attempt can prepare it again.
        assertEquals(listOf("content://new"), sources.pending(listOf("content://new"), selection))
        assertTrue(sources.pending(listOf("content://first"), selection).isEmpty())
        assertEquals(listOf(first), selection)
    }

    @Test fun removingOrDeliveringAFileAllowsSelectingTheSameSourceAgain() {
        val sources = FlashImportSources()
        val first = temporary.newFile("first.txt")
        sources.commit(listOf("content://first" to first), emptyList())
        assertEquals(listOf("content://first"), sources.pending(listOf("content://first"), emptyList()))
    }

    @Test fun selectingASourceAgainCanReplaceItsMissingCacheCopy() {
        val sources = FlashImportSources()
        val missing = temporary.newFile("missing.txt")
        val selected = sources.commit(listOf("content://first" to missing), emptyList())
        assertTrue(missing.delete())
        assertEquals(listOf("content://first"), sources.pending(listOf("content://first"), selected))
        val replacement = temporary.newFile("replacement.txt")
        assertEquals(listOf(replacement), sources.commit(listOf("content://first" to replacement), selected))
    }

    @Test fun aMissingOrUnreadableFileRejectsTheWholeBatchWithoutDroppingTheSelection() {
        val readable = temporary.newFile("readable.txt")
        val missing = File(temporary.root, "missing.txt")
        val unreadable = object : File(temporary.root, "unreadable.txt") {
            override fun isFile() = true
            override fun canRead() = false
        }
        assertTrue(canSendFlashFiles(listOf(readable)))
        assertFalse(canSendFlashFiles(emptyList()))
        assertFalse(canSendFlashFiles(listOf(readable, temporary.root)))
        assertFalse(canSendFlashFiles(listOf(readable, unreadable)))
        val selection = listOf(readable, missing)
        assertFalse(canSendFlashFiles(selection))
        assertEquals(listOf(readable, missing), selection)
    }
}
