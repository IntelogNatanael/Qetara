package com.example.wifidrop.pc

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopFileSelectionTest {
    @Test
    fun sequentialSelectionsAccumulateInTheirOriginalOrder() {
        val first = listOf(File("photo.jpg"), File("report.pdf"))
        val accumulated = mergeDesktopFileSelections(
            existing = first,
            added = listOf(File("notes.txt"), File("diagram.png"))
        )

        assertEquals(
            listOf("photo.jpg", "report.pdf", "notes.txt", "diagram.png"),
            accumulated.map(File::getName)
        )
    }

    @Test
    fun selectingTheSameFileAgainDoesNotDuplicateIt() {
        val accumulated = mergeDesktopFileSelections(
            existing = listOf(File("photo.jpg"), File("report.pdf")),
            added = listOf(File("report.pdf"), File("photo.jpg"), File("notes.txt"), File("notes.txt"))
        )

        assertEquals(listOf("photo.jpg", "report.pdf", "notes.txt"), accumulated.map(File::getName))
    }
}
