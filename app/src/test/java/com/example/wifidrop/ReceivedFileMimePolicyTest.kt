package com.example.wifidrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceivedFileMimePolicyTest {
    @Test fun concretePhotoMetadataNeverWidensToEveryApplication() {
        assertEquals("image/png", chooseReceivedFileMimeType("image/png") { error("No name lookup needed") })
    }
    @Test fun concretePdfRemainsPdfDespiteMisleadingFileName() {
        assertEquals("application/pdf", chooseReceivedFileMimeType("application/pdf") { "image/jpeg" })
    }
    @Test fun genericOrMissingMetadataUsesKnownImageExtension() {
        listOf(null, "", "*/*", "image/*", "application/octet-stream", "binary/octet-stream").forEach {
            assertEquals("image/jpeg", chooseReceivedFileMimeType(it) { "image/jpeg" })
        }
    }
    @Test fun normalizesCaseWhitespaceAndParameters() {
        assertEquals("image/jpeg", chooseReceivedFileMimeType(" IMAGE/JPEG ; source=provider") { null })
    }
    @Test fun invalidMetadataCannotHideUsableFileType() {
        listOf("invalid", "image/", "/png", "image/png/extra", "image/png\ntext/html").forEach {
            assertEquals("image/webp", chooseReceivedFileMimeType(it) { "image/webp" })
        }
    }
    @Test fun genuinelyUnknownFileDoesNotBecomeAPhotoOrPdf() {
        assertNull(chooseReceivedFileMimeType(null) { null })
        assertNull(chooseReceivedFileMimeType("application/octet-stream") { "application/octet-stream" })
    }
    @Test fun keepsModernImagesAndNonImageAttachmentsSpecific() {
        listOf("image/heic", "image/heif", "image/avif", "video/mp4", "audio/mpeg", "application/vnd.oasis.opendocument.text").forEach {
            assertEquals(it, chooseReceivedFileMimeType(it) { null })
        }
    }
}
