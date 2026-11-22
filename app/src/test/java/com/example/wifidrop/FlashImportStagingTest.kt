package com.example.wifidrop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FlashImportStagingTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun cancellationAfterWritingStillRegistersTheFileForRollback() = runBlocking {
        val folder = temporary.newFolder("cancelled-import")
        val file = File(folder, "photo.txt")
        val staged = mutableListOf<File>()
        try {
            prepareFlashImportFile(staged) {
                file.writeText("Complete provider result")
                // Reproduce prompt cancellation at the IO-to-main return boundary.
                coroutineContext.cancel()
                file
            }
            fail("The cancelled IO context must not publish a successful import")
        } catch (_: CancellationException) {
            assertEquals(listOf(file), staged)
        } finally {
            staged.forEach { it.delete(); it.parentFile?.delete() }
        }
        assertFalse(file.exists())
        assertFalse(folder.exists())
    }

    @Test fun successfulImportsAccumulateWithoutReplacingEarlierCleanupOwnership() = runBlocking {
        val staged = mutableListOf<File>()
        val first = temporary.newFile("first.txt")
        val second = temporary.newFile("second.txt")
        prepareFlashImportFile(staged) { first.apply { writeText("first") } }
        prepareFlashImportFile(staged) { second.apply { writeText("second") } }
        assertEquals(listOf(first, second), staged)
        assertEquals("first", first.readText())
        assertEquals("second", second.readText())
    }

    @Test fun removingPreparedCopyCannotDeleteProviderOriginalOrReattachedCopy() {
        val cache = temporary.newFolder("cache")
        val original = temporary.newFile("provider-original.txt").apply { writeText("original") }
        val removed = File(cache, "flash-outgoing/old-import/photo.txt").apply {
            parentFile!!.mkdirs(); writeText("old prepared copy")
        }
        val reattached = File(cache, "flash-outgoing/new-import/photo.txt").apply {
            parentFile!!.mkdirs(); writeText("new prepared copy")
        }
        val unrelated = File(cache, "other/photo.txt").apply { parentFile!!.mkdirs(); writeText("keep") }
        assertFalse(deletePreparedFlashFile(original, cache))
        assertFalse(deletePreparedFlashFile(unrelated, cache))
        assertTrue(deletePreparedFlashFile(removed, cache))
        assertFalse(removed.exists())
        assertFalse(removed.parentFile!!.exists())
        assertEquals("original", original.readText())
        assertEquals("new prepared copy", reattached.readText())
        assertEquals("keep", unrelated.readText())
    }
}
