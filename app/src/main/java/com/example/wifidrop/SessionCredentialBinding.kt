package com.example.wifidrop

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom

private val sessionBindingSalt = ByteArray(32).also { SecureRandom().nextBytes(it) }

/** Process-local comparison only: never persist, log or send this value over the wire. */
internal fun sessionCredentialBinding(token: String, pin: String, expiresAtMs: Long): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(sessionBindingSalt)
    for (value in listOf(token, pin)) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        digest.update(bytes)
    }
    digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(expiresAtMs).array())
    return digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
