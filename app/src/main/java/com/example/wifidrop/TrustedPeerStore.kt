package com.example.wifidrop

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class TrustedPeer(
    val id: String,
    val label: String,
    val trustedAtMs: Long,
    val alias: String = "",
    val favorite: Boolean = false,
    val lastKnownIp: String? = null,
    val lastSeenAtMs: Long = 0L,
    val noiseStaticKey: String? = null
)

object TrustedPeerStore {

    private const val PREFS_NAME = "wifidrop_trusted_peers"
    private const val KEY_JSON = "trusted_peers_json"
    private const val MAX_ITEMS = 200

    fun all(context: Context): List<TrustedPeer> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_JSON, null).orEmpty()
        if (json.isBlank()) return emptyList()

        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = sanitizeId(obj.optString("id"))
                    if (id.isBlank()) continue
                    val label = sanitizeLabel(obj.optString("label"))
                    val ts = obj.optLong("trusted_at", 0L)
                    val alias = sanitizeAlias(obj.optString("alias"))
                    val favorite = obj.optBoolean("favorite", false)
                    val lastKnownIp = sanitizeIp(obj.optString("last_ip")).ifBlank { null }
                    val lastSeenAt = obj.optLong("last_seen_at", 0L)
                    val noiseStaticKey = sanitizeNoiseKey(obj.optString("noise_key")).ifBlank { null }
                    add(
                        TrustedPeer(
                            id = id,
                            label = label,
                            trustedAtMs = ts,
                            alias = alias,
                            favorite = favorite,
                            lastKnownIp = lastKnownIp,
                            lastSeenAtMs = lastSeenAt,
                            noiseStaticKey = noiseStaticKey
                        )
                    )
                }
            }.sortedWith(
                compareByDescending<TrustedPeer> { it.favorite }
                    .thenByDescending { it.trustedAtMs }
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isTrusted(context: Context, peerIdRaw: String): Boolean {
        val peerId = sanitizeId(peerIdRaw)
        if (peerId.isBlank()) return false
        return all(context).any { it.id == peerId }
    }

    fun trust(context: Context, peerIdRaw: String, labelRaw: String) {
        val peerId = sanitizeId(peerIdRaw)
        if (peerId.isBlank()) return

        val label = sanitizeLabel(labelRaw)
        val now = System.currentTimeMillis()
        val existingSelf = all(context).firstOrNull { it.id == peerId }

        val existing = all(context)
            .filterNot { it.id == peerId }
            .toMutableList()
        existing.add(
            0,
            TrustedPeer(
                id = peerId,
                label = label,
                trustedAtMs = now,
                alias = existingSelf?.alias.orEmpty(),
                favorite = existingSelf?.favorite == true,
                lastKnownIp = existingSelf?.lastKnownIp,
                lastSeenAtMs = maxOf(existingSelf?.lastSeenAtMs ?: 0L, now),
                noiseStaticKey = existingSelf?.noiseStaticKey
            )
        )

        val trimmed = existing.take(MAX_ITEMS)
        persist(context, trimmed)
    }

    fun remove(context: Context, peerIdRaw: String) {
        val peerId = sanitizeId(peerIdRaw)
        if (peerId.isBlank()) return

        val next = all(context).filterNot { it.id == peerId }
        persist(context, next)
    }

    fun setFavorite(context: Context, peerIdRaw: String, favorite: Boolean) {
        val peerId = sanitizeId(peerIdRaw)
        if (peerId.isBlank()) return
        val next = all(context).map { peer ->
            if (peer.id == peerId) peer.copy(favorite = favorite) else peer
        }
        persist(context, next)
    }

    fun setAlias(context: Context, peerIdRaw: String, aliasRaw: String) {
        val peerId = sanitizeId(peerIdRaw)
        if (peerId.isBlank()) return
        val alias = sanitizeAlias(aliasRaw)
        val next = all(context).map { peer ->
            if (peer.id == peerId) peer.copy(alias = alias) else peer
        }
        persist(context, next)
    }

    fun updateSeen(context: Context, peerIdRaw: String, labelRaw: String, ipRaw: String) {
        val peerId = sanitizeId(peerIdRaw)
        if (peerId.isBlank()) return
        val ip = sanitizeIp(ipRaw).ifBlank { null }
        val label = sanitizeLabel(labelRaw)
        val now = System.currentTimeMillis()
        val next = all(context).map { peer ->
            if (peer.id == peerId) {
                peer.copy(
                    label = if (label.isNotBlank()) label else peer.label,
                    lastKnownIp = ip ?: peer.lastKnownIp,
                    lastSeenAtMs = now
                )
            } else {
                peer
            }
        }
        persist(context, next)
    }

    fun updateNoiseStaticKey(context: Context, peerIdRaw: String, noiseKeyRaw: String) {
        val peerId = sanitizeId(peerIdRaw)
        if (peerId.isBlank()) return
        val noiseKey = sanitizeNoiseKey(noiseKeyRaw)
        if (noiseKey.isBlank()) return
        val next = all(context).map { peer ->
            if (peer.id == peerId) peer.copy(noiseStaticKey = noiseKey) else peer
        }
        persist(context, next)
    }

    fun isNoiseKeyCompatible(context: Context, peerIdRaw: String, noiseKeyRaw: String): Boolean {
        val peerId = sanitizeId(peerIdRaw)
        val noiseKey = sanitizeNoiseKey(noiseKeyRaw)
        if (peerId.isBlank() || noiseKey.isBlank()) return false
        val peer = all(context).firstOrNull { it.id == peerId } ?: return false
        val existing = peer.noiseStaticKey
        return existing.isNullOrBlank() || existing == noiseKey
    }

    fun displayName(peer: TrustedPeer): String {
        return peer.alias.ifBlank { peer.label }
    }

    private fun persist(context: Context, peers: List<TrustedPeer>) {
        val arr = JSONArray()
        peers.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("label", p.label)
                    .put("trusted_at", p.trustedAtMs)
                    .put("alias", p.alias)
                    .put("favorite", p.favorite)
                    .put("last_ip", p.lastKnownIp.orEmpty())
                    .put("last_seen_at", p.lastSeenAtMs)
                    .put("noise_key", p.noiseStaticKey.orEmpty())
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_JSON, arr.toString())
            }
    }

    private fun sanitizeId(raw: String): String {
        return raw.trim().take(80)
    }

    private fun sanitizeLabel(raw: String): String {
        val clean = raw.trim().replace(Regex("\\s+"), " ").take(64)
        return if (clean.isBlank()) "peer" else clean
    }

    private fun sanitizeAlias(raw: String): String {
        return raw.trim().replace(Regex("\\s+"), " ").take(48)
    }

    private fun sanitizeIp(raw: String): String {
        return raw.trim().take(64)
    }

    private fun sanitizeNoiseKey(raw: String): String {
        return raw.trim().take(180)
    }
}
