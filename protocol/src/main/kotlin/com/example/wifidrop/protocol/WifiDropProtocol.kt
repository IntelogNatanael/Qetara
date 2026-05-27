package com.example.wifidrop.protocol

import java.security.MessageDigest
import kotlin.random.Random

const val DEFAULT_PORT = 8988

const val PROTOCOL_MAGIC = 0x57445250 // WDRP
const val PROTOCOL_VERSION = 4

const val PACKET_HELLO = 1
const val PACKET_FILE = 2
const val PACKET_TOKEN_RESPONSE = 4
const val PACKET_CHALLENGE = 5
const val PACKET_CREDENTIALS_REQUEST = 6
const val PACKET_CREDENTIALS_RESPONSE = 7
const val PACKET_DISCOVERY_REQUEST = 8
const val PACKET_DISCOVERY_RESPONSE = 9
const val PACKET_RESULT = 10
const val PACKET_MESSAGE = 11

const val SECURE_FRAME_FILE_META = 1
const val SECURE_FRAME_FILE_CHUNK = 2
const val SECURE_FRAME_FILE_DONE = 3
const val SECURE_FRAME_MESSAGE = 4
const val SECURE_FRAME_RESULT = 5
const val SECURE_FRAME_HELLO = 6
const val SECURE_FRAME_FILE_RESUME = 7

const val NOISE_PROTOCOL_WITH_PSK = "NoisePSK_XX_25519_ChaChaPoly_SHA256"
const val NOISE_PROTOCOL_NO_PSK = "Noise_XX_25519_ChaChaPoly_SHA256"
const val NOISE_PROLOGUE_PREFIX = "WifiDrop/v4"

const val NOISE_HANDSHAKE_MAX_FRAME = 4096
const val MAX_SECURE_FRAME_BYTES = 128 * 1024
const val MAX_SECURE_FILE_CHUNK_BYTES = 48 * 1024
const val MAX_MESSAGE_CHARS = 2000

private val tokenRegex = Regex("^[A-Z0-9]{4,32}$")
private val pinRegex = Regex("^\\d{6}$")
private val sha256Regex = Regex("^[0-9a-f]{64}$")
private val hexAlphabet = "0123456789abcdef".toCharArray()
private const val TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

fun randomToken(length: Int = 8): String {
    val n = length.coerceIn(4, 32)
    return buildString {
        repeat(n) {
            append(TOKEN_ALPHABET[Random.nextInt(TOKEN_ALPHABET.length)])
        }
    }
}

fun normalizeToken(raw: String): String {
    return raw
        .uppercase()
        .filter { it in 'A'..'Z' || it in '0'..'9' }
        .take(32)
}

fun isValidToken(raw: String): Boolean {
    return tokenRegex.matches(normalizeToken(raw))
}

fun requireValidToken(raw: String): String {
    val normalized = normalizeToken(raw)
    require(tokenRegex.matches(normalized)) {
        "Token invalido. Usa 4-32 caracteres A-Z y 0-9."
    }
    return normalized
}

fun normalizePin(raw: String): String {
    return raw.filter { it.isDigit() }.take(6)
}

fun isValidPin(raw: String): Boolean {
    return pinRegex.matches(normalizePin(raw))
}

fun requireValidPin(raw: String): String {
    val normalized = normalizePin(raw)
    require(pinRegex.matches(normalized)) {
        "PIN invalido. Usa 6 digitos."
    }
    return normalized
}

fun randomPin(): String {
    return (100000 + Random.nextInt(900000)).toString()
}

fun randomNonce(length: Int = 16): String {
    val n = length.coerceIn(8, 64)
    return buildString {
        repeat(n) {
            append(TOKEN_ALPHABET[Random.nextInt(TOKEN_ALPHABET.length)])
        }
    }
}

fun sanitizeClientId(raw: String): String {
    val clean = raw.trim().take(80)
    require(clean.isNotBlank()) { "clientId vacio" }
    return clean
}

fun sanitizePeerLabel(name: String): String {
    val cleaned = name.trim().replace(Regex("\\s+"), " ").take(64)
    return if (cleaned.isBlank()) "peer" else cleaned
}

fun sanitizeFileName(name: String, fallbackName: String = "file"): String {
    val trimmed = name.trim().take(180)
    val noSlashes = trimmed.replace(Regex("[\\\\/]+"), "_")
    val noDots = noSlashes.replace("..", "_")
    val cleaned = noDots.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim()
    return if (cleaned.isBlank()) fallbackName else cleaned
}

fun requireValidMessage(raw: String): String {
    val normalized = raw.trim().replace(Regex("\\s+"), " ").take(MAX_MESSAGE_CHARS)
    require(normalized.isNotBlank()) { "Mensaje vacio" }
    return normalized
}

fun isSha256Hex(raw: String): Boolean {
    return sha256Regex.matches(raw.lowercase())
}

fun computeDigest(
    purpose: String,
    clientNonce: String,
    serverNonce: String,
    clientId: String,
    tokenOrBlank: String,
    pin: String
): String {
    val raw = listOf(
        purpose.trim().uppercase(),
        clientNonce.trim(),
        serverNonce.trim(),
        clientId.trim(),
        tokenOrBlank.trim(),
        pin.trim()
    ).joinToString("|")

    return MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .toHexLower()
}

fun ByteArray.toHexLower(): String {
    val out = CharArray(size * 2)
    var i = 0
    for (byte in this) {
        val value = byte.toInt() and 0xFF
        out[i++] = hexAlphabet[value ushr 4]
        out[i++] = hexAlphabet[value and 0x0F]
    }
    return String(out)
}
