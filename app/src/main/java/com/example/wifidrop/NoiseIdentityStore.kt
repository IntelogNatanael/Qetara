package com.example.wifidrop

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import kr.jclab.noise.protocol.Noise

data class NoiseStaticIdentity(
    val privateKey: ByteArray,
    val publicKey: ByteArray
)

object NoiseIdentityStore {

    private const val PREFS_NAME = "wifidrop_noise_identity"
    private const val KEY_PRIVATE = "noise_private"
    private const val KEY_PUBLIC = "noise_public"

    fun getOrCreate(context: Context): NoiseStaticIdentity {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingPrivate = prefs.getString(KEY_PRIVATE, null)
        val existingPublic = prefs.getString(KEY_PUBLIC, null)

        if (!existingPrivate.isNullOrBlank() && !existingPublic.isNullOrBlank()) {
            val decoded = decode(existingPrivate, existingPublic)
            if (decoded != null) return decoded
        }

        val dh = Noise.createDH("25519")
        dh.generateKeyPair()
        val privateKey = ByteArray(dh.privateKeyLength)
        val publicKey = ByteArray(dh.publicKeyLength)
        dh.getPrivateKey(privateKey, 0)
        dh.getPublicKey(publicKey, 0)
        dh.destroy()

        prefs.edit {
            putString(KEY_PRIVATE, Base64.encodeToString(privateKey, Base64.NO_WRAP))
            putString(KEY_PUBLIC, Base64.encodeToString(publicKey, Base64.NO_WRAP))
        }

        return NoiseStaticIdentity(
            privateKey = privateKey,
            publicKey = publicKey
        )
    }

    fun fingerprintShort(publicKeyRaw: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(publicKeyRaw)
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    private fun decode(privateB64: String, publicB64: String): NoiseStaticIdentity? {
        return try {
            val privateKey = Base64.decode(privateB64, Base64.DEFAULT)
            val publicKey = Base64.decode(publicB64, Base64.DEFAULT)
            if (privateKey.size != 32 || publicKey.size != 32) {
                null
            } else {
                NoiseStaticIdentity(privateKey = privateKey, publicKey = publicKey)
            }
        } catch (_: Exception) {
            null
        }
    }
}
