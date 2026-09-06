package com.example.wifidrop

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

object TransferSecurity {

    fun normalizePin(raw: String): String {
        return com.example.wifidrop.protocol.normalizePin(raw)
    }

    fun isValidPin(raw: String): Boolean {
        return com.example.wifidrop.protocol.isValidPin(raw)
    }

    fun randomPin(): String {
        return com.example.wifidrop.protocol.randomPin()
    }

    fun randomNonce(length: Int = 16): String {
        return com.example.wifidrop.protocol.randomNonce(length)
    }

    fun computeDigest(
        purpose: String,
        clientNonce: String,
        serverNonce: String,
        clientId: String,
        tokenOrBlank: String,
        pin: String
    ): String {
        return com.example.wifidrop.protocol.computeDigest(
            purpose = purpose,
            clientNonce = clientNonce,
            serverNonce = serverNonce,
            clientId = clientId,
            tokenOrBlank = tokenOrBlank,
            pin = pin
        )
    }

    fun isExpired(expiresAtMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        return nowMs >= expiresAtMs
    }
}

object LocalDeviceIdentity {

    private const val PREFS_NAME = "wifidrop_identity"
    private const val KEY_DEVICE_ID = "device_id"

    @Synchronized
    fun getOrCreate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }

        val generated = UUID.randomUUID().toString()
        prefs.edit { putString(KEY_DEVICE_ID, generated) }
        return generated
    }

    fun short(id: String): String {
        return id.take(8)
    }
}
