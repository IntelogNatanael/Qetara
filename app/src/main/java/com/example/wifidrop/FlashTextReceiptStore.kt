package com.example.wifidrop

import com.example.wifidrop.protocol.flash.FLASH_TEXT_MAX_BYTES
import com.example.wifidrop.protocol.flash.FlashText as FlashTextAttachment
import java.io.File
import java.security.MessageDigest

/**
 * Private provenance for text actually consumed by Flash, never inferred from a filename alone.
 * Older unmarked files stay visible: normal file transfers can legitimately use the same names.
 */
internal class FlashTextReceiptStore(private val directory: File) {
    fun recordConsumed(file: File, textBody: String): Boolean = runCatching {
        if (!candidate(file) || FlashTextAttachment.readReceived(file) != textBody) return@runCatching false
        val bytes = textBody.toByteArray(Charsets.UTF_8)
        if (file.length() != bytes.size.toLong()) return@runCatching false
        if (!directory.isDirectory && !directory.mkdirs()) return@runCatching false
        // Hash the consumed body, not a later replacement at the same path.
        marker(file).writeText(fingerprint(file, bytes), Charsets.UTF_8)
        true
    }.getOrDefault(false)

    fun isConsumedText(file: File): Boolean = runCatching {
        if (!candidate(file)) return@runCatching false
        val receipt = marker(file)
        if (!receipt.isFile || receipt.length() !in 1..MAX_MARKER_BYTES) return@runCatching false
        val expected = receipt.readText(Charsets.UTF_8)
        // Only marked, size-bounded candidates are read; this also rejects invalid UTF-8.
        val body = FlashTextAttachment.readReceived(file) ?: return@runCatching false
        val bytes = body.toByteArray(Charsets.UTF_8)
        file.length() == bytes.size.toLong() && expected == fingerprint(file, bytes)
    }.getOrDefault(false)

    private fun candidate(file: File): Boolean = file.isFile &&
        FlashTextAttachment.isTextFileName(file.name) && file.length() in 0..FLASH_TEXT_MAX_BYTES.toLong()

    private fun marker(file: File): File = File(directory, sha256(file.canonicalPath.toByteArray(Charsets.UTF_8)) + ".receipt")

    private fun fingerprint(file: File, consumedBytes: ByteArray): String =
        "v1:${consumedBytes.size}:${file.lastModified()}:${sha256(consumedBytes)}"

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        const val DIRECTORY_NAME = "flash-text-receipts"
        private const val MAX_MARKER_BYTES = 128L
    }
}
