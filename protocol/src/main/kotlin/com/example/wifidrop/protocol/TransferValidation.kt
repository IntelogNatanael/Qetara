package com.example.wifidrop.protocol

import java.security.MessageDigest

/** Validates before allocation or disk writes, including empty files and Long overflow. */
fun requireValidFileChunk(totalBytes: Long, receivedBytes: Long, chunkBytes: Int): Int {
    require(totalBytes >= 0L && receivedBytes in 0L..totalBytes) { "tamano de archivo invalido" }
    require(chunkBytes in 1..MAX_SECURE_FILE_CHUNK_BYTES) { "chunk de archivo invalido" }
    require(chunkBytes.toLong() <= totalBytes - receivedBytes) { "transferencia excede tamano esperado" }
    return chunkBytes
}

fun requireValidResumeOffset(offset: Long, totalBytes: Long): Long {
    require(totalBytes >= 0L && offset in 0L..totalBytes) { "posicion de reanudacion invalida" }
    return offset
}

fun requireValidFileHash(raw: String): String {
    require(isSha256Hex(raw)) { "hash de archivo invalido" }
    return raw.lowercase(java.util.Locale.ROOT)
}

/** Avoids an early-exit string comparison for the challenge response. */
fun digestMatches(actual: String, expected: String): Boolean {
    if (!isSha256Hex(actual) || !isSha256Hex(expected)) return false
    return MessageDigest.isEqual(
        actual.lowercase(java.util.Locale.ROOT).toByteArray(Charsets.US_ASCII),
        expected.lowercase(java.util.Locale.ROOT).toByteArray(Charsets.US_ASCII)
    )
}

/** Unknown identities need explicit approval; an existing key can never be silently replaced. */
fun isPinnedIdentityCompatible(pinnedKey: String?, observedKey: String): Boolean {
    return observedKey.isNotBlank() && (pinnedKey.isNullOrBlank() || pinnedKey == observedKey)
}

fun requiresEncryptedCredentials(packetType: Int): Boolean = packetType == PACKET_CREDENTIALS_REQUEST
