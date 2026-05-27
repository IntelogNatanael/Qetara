package com.example.wifidrop

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class GlobalLanOutboxEntry(
    val chatMessageId: String,
    val text: String,
    val createdAtMs: Long,
    val dispatchedPeerIds: List<String>
)

object GlobalLanOutboxStore {

    private const val PREFS_NAME = "wifidrop_global_lan_outbox"
    private const val KEY_JSON = "global_lan_outbox_json"
    private const val MAX_ITEMS = 200

    fun list(context: Context): List<GlobalLanOutboxEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_JSON, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return try {
            val arr = JSONArray(raw)
            buildList {
                for (index in 0 until arr.length()) {
                    val obj = arr.optJSONObject(index) ?: continue
                    val chatMessageId = obj.optString("chat_message_id").trim()
                    val text = obj.optString("text").trim().replace(Regex("\\s+"), " ").take(2_000)
                    if (chatMessageId.isBlank() || text.isBlank()) continue
                    val peerIds = obj.optJSONArray("dispatched_peer_ids")?.let { peerArr ->
                        buildList {
                            for (peerIndex in 0 until peerArr.length()) {
                                peerArr.optString(peerIndex).trim().takeIf { it.isNotBlank() }?.let(::add)
                            }
                        }.distinct()
                    }.orEmpty()
                    add(
                        GlobalLanOutboxEntry(
                            chatMessageId = chatMessageId.take(80),
                            text = text,
                            createdAtMs = obj.optLong("created_at_ms", 0L).coerceAtLeast(0L),
                            dispatchedPeerIds = peerIds
                        )
                    )
                }
            }.sortedBy { it.createdAtMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun upsert(context: Context, entry: GlobalLanOutboxEntry) {
        val next = list(context)
            .filterNot { it.chatMessageId == entry.chatMessageId }
            .toMutableList()
        next.add(
            entry.copy(
                chatMessageId = entry.chatMessageId.trim().take(80),
                text = entry.text.trim().replace(Regex("\\s+"), " ").take(2_000),
                createdAtMs = entry.createdAtMs.coerceAtLeast(0L),
                dispatchedPeerIds = entry.dispatchedPeerIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            )
        )
        persist(context, next)
    }

    fun remove(context: Context, chatMessageIdRaw: String) {
        val chatMessageId = chatMessageIdRaw.trim()
        if (chatMessageId.isBlank()) return
        persist(context, list(context).filterNot { it.chatMessageId == chatMessageId })
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { remove(KEY_JSON) }
    }

    private fun persist(context: Context, entries: List<GlobalLanOutboxEntry>) {
        val arr = JSONArray()
        entries.sortedBy { it.createdAtMs }.takeLast(MAX_ITEMS).forEach { entry ->
            val peers = JSONArray()
            entry.dispatchedPeerIds.forEach(peers::put)
            arr.put(
                JSONObject()
                    .put("chat_message_id", entry.chatMessageId)
                    .put("text", entry.text)
                    .put("created_at_ms", entry.createdAtMs)
                    .put("dispatched_peer_ids", peers)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_JSON, arr.toString())
        }
    }
}
