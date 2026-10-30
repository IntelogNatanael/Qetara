package com.example.wifidrop.protocol.flash

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.UUID

const val FLASH_TEXT_MAX_BYTES = 16 * 1024

/**
 * An exact UTF-8 text attachment transported by the ordinary, bilaterally approved Flash transfer.
 * Older peers receive a readable TXT file. This filename convention classifies content; it does not
 * authenticate its author or authorize a transfer. No clipboard is read or written here.
 */
object FlashText {
    private const val PREFIX = "qetara-flash-text-v1-"
    private const val TOO_LARGE = "El texto no puede superar 16 KiB (16 384 bytes)."
    private const val INVALID_UNICODE = "El texto contiene caracteres Unicode no válidos."
    private class ValidationException(val code: String, message: String, cause: Throwable? = null) :
        IllegalArgumentException(message, cause)
    private val fileName = Regex(
        "qetara-flash-text-v1-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?: \\([1-9][0-9]{0,9}\\))?\\.txt"
    )

    /** Empty text is valid in the attachment format; a composer may require nonblank input. */
    fun validationError(text: String): String? = try {
        encode(text)
        null
    } catch (error: IllegalArgumentException) {
        error.message
    }

    /** Stable presentation code; callers choose copy in their current language. */
    fun validationCode(text: String): String? = try {
        encode(text)
        null
    } catch (error: ValidationException) {
        error.code
    }

    /**
     * Creates a new uniquely named attachment, preserving text byte-for-byte without a metadata
     * header or newline conversion. The caller owns its lifetime until the send has terminated.
     * Invalid input is rejected before creating the directory or a file.
     */
    fun createFile(directory: File, text: String): File {
        val bytes = encode(text)
        if (!directory.mkdirs() && !directory.isDirectory) {
            throw IOException("No se pudo crear la carpeta temporal del mensaje.")
        }
        val target = File(directory, "$PREFIX${UUID.randomUUID()}.txt")
        if (!target.createNewFile()) throw IOException("No se pudo reservar el archivo del mensaje.")
        try {
            FileOutputStream(target).use { it.write(bytes) }
            return target
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    /** Includes the no-overwrite publication helper's collision suffix, but never staging names. */
    fun isTextFileName(name: String): Boolean = fileName.matches(name)

    /**
     * Reads only completed reserved text attachments. Malformed, oversized, unavailable and ordinary
     * files return null so adapters can retain their normal file presentation. Reads at most the
     * limit plus one byte even if the file changes while open; never trust length() alone.
     */
    fun readReceived(file: File): String? {
        if (!isTextFileName(file.name)) return null
        return try {
            if (!file.isFile || file.length() > FLASH_TEXT_MAX_BYTES) return null
            val bytes = ByteArray(FLASH_TEXT_MAX_BYTES + 1)
            val size = FileInputStream(file).use { input ->
                var count = 0
                while (count < bytes.size) {
                    val read = input.read(bytes, count, bytes.size - count)
                    if (read < 0) break
                    count += read
                }
                count
            }
            if (size > FLASH_TEXT_MAX_BYTES) return null
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, 0, size))
                .toString()
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun encode(text: String): ByteArray {
        // Each UTF-16 code unit needs at least one UTF-8 byte; this precheck also bounds allocation.
        if (text.length > FLASH_TEXT_MAX_BYTES) throw ValidationException("text_too_large", TOO_LARGE)
        val encoded = try {
            Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(text))
        } catch (error: CharacterCodingException) {
            throw ValidationException("text_invalid_unicode", INVALID_UNICODE, error)
        }
        if (encoded.remaining() > FLASH_TEXT_MAX_BYTES) throw ValidationException("text_too_large", TOO_LARGE)
        return ByteArray(encoded.remaining()).also { encoded.get(it) }
    }
}
