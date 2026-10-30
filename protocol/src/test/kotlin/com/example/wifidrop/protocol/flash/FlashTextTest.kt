package com.example.wifidrop.protocol.flash

import com.example.wifidrop.protocol.publishReceivedFile
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlashTextTest {
    private val reservedName = "qetara-flash-text-v1-01234567-89ab-4cde-8fab-0123456789ab.txt"

    private fun withDirectory(action: (File) -> Unit) {
        val directory = Files.createTempDirectory("qetara-flash-text-test-").toFile()
        try {
            action(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun exactUtf8TextRemainsReadableByLegacyPeers() = withDirectory { directory ->
        val text = "  Hola, Perú 👋\r\n第二行\n\nCafe\u0301\t🙂\n  "
        val file = FlashText.createFile(directory, text)
        assertTrue(FlashText.isTextFileName(file.name))
        assertContentEquals(text.toByteArray(Charsets.UTF_8), file.readBytes())
        assertEquals(text, file.readText(Charsets.UTF_8), "legacy peer sees the exact text, with no header")
        assertEquals(text, FlashText.readReceived(file))
        assertNull(FlashText.validationError(text))
        assertNull(FlashText.validationCode(text))
    }

    @Test fun emptyAndWhitespacePayloadsPreserveTheirExactContents() = withDirectory { directory ->
        listOf("", " \t\r\n", "\uFEFFtexto").forEach { text ->
            val file = FlashText.createFile(directory, text)
            assertEquals(text, FlashText.readReceived(file))
        }
    }

    @Test fun eachMessageOwnsANewFileWithoutOverwritingPreviousText() = withDirectory { directory ->
        val first = FlashText.createFile(directory, "primero")
        val second = FlashText.createFile(directory, "segundo")
        assertNotEquals(first.name, second.name)
        assertEquals("primero", FlashText.readReceived(first))
        assertEquals("segundo", FlashText.readReceived(second))
    }

    @Test fun byteLimitAcceptsAsciiAndMultibyteBoundary() = withDirectory { directory ->
        listOf("a".repeat(FLASH_TEXT_MAX_BYTES), "🙂".repeat(FLASH_TEXT_MAX_BYTES / 4)).forEach { text ->
            val file = FlashText.createFile(directory, text)
            assertEquals(FLASH_TEXT_MAX_BYTES.toLong(), file.length())
            assertEquals(text, FlashText.readReceived(file))
            assertNull(FlashText.validationError(text))
            assertNull(FlashText.validationCode(text))
        }
    }

    @Test fun oversizeTextIsRejectedBeforeCreatingADirectoryOrFile() = withDirectory { directory ->
        val destination = File(directory, "not-yet-created")
        listOf("a".repeat(FLASH_TEXT_MAX_BYTES + 1), "🙂".repeat(FLASH_TEXT_MAX_BYTES / 4) + "a").forEach { text ->
            assertNotNull(FlashText.validationError(text))
            assertEquals("text_too_large", FlashText.validationCode(text))
            assertFailsWith<IllegalArgumentException> { FlashText.createFile(destination, text) }
            assertFalse(destination.exists())
        }
    }

    @Test fun unpairedSurrogatesAreRejectedInsteadOfBeingSilentlyReplaced() = withDirectory { directory ->
        val destination = File(directory, "not-yet-created")
        listOf("\uD800", "\uDC00", "antes\uD800después").forEach { text ->
            assertNotNull(FlashText.validationError(text))
            assertEquals("text_invalid_unicode", FlashText.validationCode(text))
            assertFailsWith<IllegalArgumentException> { FlashText.createFile(destination, text) }
            assertFalse(destination.exists())
        }
    }

    @Test fun ordinaryTextAndNearMatchingNamesRemainFiles() = withDirectory { directory ->
        val names = listOf(
            "message.txt", "qetara-flash-text-v1-not-a-uuid.txt", "prefix-$reservedName",
            "$reservedName.part", reservedName.replace("v1-", "v2-"), reservedName.replace(".txt", ".bin"),
            reservedName.replace(".txt", " (0).txt"), reservedName.replace(".txt", " (-1).txt")
        )
        names.forEach { name ->
            val file = File(directory, name).apply { writeText("texto") }
            assertFalse(FlashText.isTextFileName(name), name)
            assertNull(FlashText.readReceived(file), name)
            assertEquals("texto", file.readText(), "classification must not alter a file")
        }
        assertFalse(FlashText.isTextFileName("../$reservedName"))
        assertFalse(FlashText.isTextFileName("folder/$reservedName"))
    }

    @Test fun publicationCollisionsRemainRecognizableWithoutReplacingPriorMessages() = withDirectory { directory ->
        val inbox = File(directory, "received")
        val first = File(directory, "first.part").apply { writeText("primero") }
        val second = File(directory, "second.part").apply { writeText("segundo") }
        val publishedFirst = publishReceivedFile(first, inbox, reservedName)
        val publishedSecond = publishReceivedFile(second, inbox, reservedName)
        assertNotEquals(publishedFirst.name, publishedSecond.name)
        assertTrue(publishedSecond.name.endsWith(" (1).txt"))
        assertEquals("primero", FlashText.readReceived(publishedFirst))
        assertEquals("segundo", FlashText.readReceived(publishedSecond))
    }

    @Test fun malformedUtf8RemainsAnOrdinaryFileWithOriginalBytes() = withDirectory { directory ->
        val file = File(directory, reservedName)
        val invalidPayloads = listOf(
            byteArrayOf(0xc3.toByte(), 0x28), // Invalid continuation.
            byteArrayOf(0xf0.toByte(), 0x9f.toByte(), 0x99.toByte()), // Truncated code point.
            byteArrayOf(0xc0.toByte(), 0xaf.toByte()), // Overlong encoding.
            byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()) // UTF-8 encoded surrogate.
        )
        invalidPayloads.forEach { bytes ->
            file.writeBytes(bytes)
            assertNull(FlashText.readReceived(file))
            assertContentEquals(bytes, file.readBytes())
        }
    }

    @Test fun oversizedReceivedTextRemainsAFile() = withDirectory { directory ->
        val file = File(directory, reservedName).apply { writeBytes(ByteArray(FLASH_TEXT_MAX_BYTES + 1) { 65 }) }
        assertNull(FlashText.readReceived(file))
        assertEquals((FLASH_TEXT_MAX_BYTES + 1).toLong(), file.length())
    }

    @Test fun boundedReadRejectsPayloadEvenWhenTheReportedLengthIsStale() = withDirectory { directory ->
        File(directory, reservedName).writeBytes(ByteArray(FLASH_TEXT_MAX_BYTES + 1) { 65 })
        val staleLength = object : File(directory, reservedName) {
            override fun length(): Long = 0
        }
        assertNull(FlashText.readReceived(staleLength), "the payload limit must not depend on the length precheck")
    }

    @Test fun directoriesAndMissingFilesAreNotMessages() = withDirectory { directory ->
        val file = File(directory, reservedName)
        assertNull(FlashText.readReceived(file))
        assertTrue(file.mkdir())
        assertNull(FlashText.readReceived(file))
    }
}
