package com.example.wifidrop

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TransferDirection {
    SENT,
    RECEIVED
}

enum class TransferOutcome {
    SUCCESS,
    FAILED,
    CANCELED
}

data class TransferHistoryEntry(
    val id: String,
    val direction: TransferDirection,
    val fileName: String,
    val bytes: Long,
    val outcome: TransferOutcome,
    val timestampMs: Long,
    val peerLabel: String?,
    val peerIp: String?,
    val route: String?,
    val errorCause: String?
)

object TransferHistoryStore {

    private const val PREFS_NAME = "wifidrop_history"
    private const val KEY_JSON = "history_json"
    private const val MAX_ITEMS = 250

    @Synchronized
    fun list(context: Context, limit: Int = 60): List<TransferHistoryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_JSON, null).orEmpty()
        if (json.isBlank()) return emptyList()

        return try {
            val arr = JSONArray(json)
            val items = mutableListOf<TransferHistoryEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                val direction = parseDirection(obj.optString("direction"))
                val outcome = parseOutcome(obj.optString("outcome"))
                val name = obj.optString("file_name").ifBlank { context.getString(R.string.rt_unnamed) }
                items.add(
                    TransferHistoryEntry(
                        id = id,
                        direction = direction,
                        fileName = name,
                        bytes = obj.optLong("bytes", -1L),
                        outcome = outcome,
                        timestampMs = obj.optLong("timestamp_ms", 0L),
                        peerLabel = obj.optString("peer_label").takeIf { it.isNotBlank() },
                        peerIp = obj.optString("peer_ip").takeIf { it.isNotBlank() },
                        route = obj.optString("route").takeIf { it.isNotBlank() },
                        errorCause = obj.optString("error").takeIf { it.isNotBlank() }
                    )
                )
            }
            items.sortedByDescending { it.timestampMs }.take(limit.coerceIn(1, MAX_ITEMS))
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun append(context: Context, item: TransferHistoryEntry) {
        val current = list(context, limit = MAX_ITEMS).toMutableList()
        current.add(0, item)
        persist(context, current.take(MAX_ITEMS))
    }

    fun newEntry(
        direction: TransferDirection,
        fileName: String,
        bytes: Long,
        outcome: TransferOutcome,
        peerLabel: String?,
        peerIp: String?,
        route: String?,
        errorCause: String?
    ): TransferHistoryEntry {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        return TransferHistoryEntry(
            id = id,
            direction = direction,
            fileName = fileName.take(180),
            bytes = bytes,
            outcome = outcome,
            timestampMs = now,
            peerLabel = peerLabel?.take(64),
            peerIp = peerIp?.take(64),
            route = route,
            errorCause = errorCause?.take(240)
        )
    }

    private fun persist(context: Context, items: List<TransferHistoryEntry>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("direction", item.direction.name)
                    .put("file_name", item.fileName)
                    .put("bytes", item.bytes)
                    .put("outcome", item.outcome.name)
                    .put("timestamp_ms", item.timestampMs)
                    .put("peer_label", item.peerLabel.orEmpty())
                    .put("peer_ip", item.peerIp.orEmpty())
                    .put("route", item.route.orEmpty())
                    .put("error", item.errorCause.orEmpty())
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_JSON, arr.toString())
            }
    }

    private fun parseDirection(raw: String): TransferDirection {
        return when (raw) {
            TransferDirection.RECEIVED.name -> TransferDirection.RECEIVED
            else -> TransferDirection.SENT
        }
    }

    private fun parseOutcome(raw: String): TransferOutcome {
        return when (raw) {
            TransferOutcome.SUCCESS.name -> TransferOutcome.SUCCESS
            TransferOutcome.CANCELED.name -> TransferOutcome.CANCELED
            else -> TransferOutcome.FAILED
        }
    }
}
