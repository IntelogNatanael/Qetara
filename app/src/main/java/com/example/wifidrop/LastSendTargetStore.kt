package com.example.wifidrop

import android.content.Context
import androidx.core.content.edit

data class LastSendTarget(
    val ip: String,
    val label: String,
    val updatedAtMs: Long
)

object LastSendTargetStore {
    private const val PREFS_NAME = "wifidrop_last_send_target"
    private const val KEY_IP = "ip"
    private const val KEY_LABEL = "label"
    private const val KEY_UPDATED_AT = "updated_at"

    fun get(context: Context): LastSendTarget? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ip = prefs.getString(KEY_IP, null).orEmpty().trim()
        if (ip.isBlank()) return null
        val label = prefs.getString(KEY_LABEL, null).orEmpty().trim().ifBlank { "Ultimo destino" }
        val updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        return LastSendTarget(ip = ip, label = label, updatedAtMs = updatedAt)
    }

    fun set(context: Context, ipRaw: String, labelRaw: String) {
        val ip = ipRaw.trim().take(64)
        if (ip.isBlank()) return
        val label = labelRaw.trim().take(64).ifBlank { "Ultimo destino" }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_IP, ip)
                putString(KEY_LABEL, label)
                putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            }
    }
}
