package com.example.wifidrop

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChannelFileOffer(
    val id: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val senderId: String,
    val senderLabel: String,
    val senderIp: String?,
    val uri: String?,
    val createdAtMs: Long
)

data class ChannelFileRequest(
    val offerId: String,
    val requesterId: String,
    val requesterLabel: String,
    val requesterIp: String?
)

object ChannelFileOfferStore {
    private const val PREFS_NAME = "wifidrop_channel_file_offers"
    private const val KEY_JSON = "offers_json"
    private const val MAX_ITEMS = 80

    @Synchronized
    fun register(
        context: Context,
        uri: Uri,
        fileName: String,
        fileSizeBytes: Long,
        senderId: String,
        senderLabel: String,
        senderIp: String? = null
    ): ChannelFileOffer {
        val offer = ChannelFileOffer(
            id = UUID.randomUUID().toString(),
            fileName = sanitizeFileName(fileName),
            fileSizeBytes = fileSizeBytes,
            senderId = senderId.trim().take(80),
            senderLabel = sanitizeLabel(senderLabel),
            senderIp = senderIp?.trim()?.takeIf { it.isNotBlank() }?.take(64),
            uri = uri.toString(),
            createdAtMs = System.currentTimeMillis()
        )
        upsert(context, offer)
        return offer
    }

    @Synchronized
    fun upsert(context: Context, offer: ChannelFileOffer) {
        val next = (list(context).filterNot { it.id == offer.id } + sanitize(offer))
            .sortedByDescending { it.createdAtMs }
            .take(MAX_ITEMS)
        persist(context, next)
    }

    fun find(context: Context, offerIdRaw: String): ChannelFileOffer? {
        val offerId = offerIdRaw.trim()
        if (offerId.isBlank()) return null
        return list(context).firstOrNull { it.id == offerId }
    }

    @Synchronized
    fun list(context: Context): List<ChannelFileOffer> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null)
            .orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (index in 0 until arr.length()) {
                    val obj = arr.optJSONObject(index) ?: continue
                    val id = obj.optString("id").trim()
                    if (id.isBlank()) continue
                    add(
                        ChannelFileOffer(
                            id = id.take(120),
                            fileName = sanitizeFileName(obj.optString("file_name")),
                            fileSizeBytes = obj.optLong("file_size_bytes", -1L),
                            senderId = obj.optString("sender_id").trim().take(80),
                            senderLabel = sanitizeLabel(obj.optString("sender_label")),
                            senderIp = obj.optString("sender_ip").trim().takeIf { it.isNotBlank() }?.take(64),
                            uri = obj.optString("uri").trim().takeIf { it.isNotBlank() },
                            createdAtMs = obj.optLong("created_at_ms", 0L)
                        )
                    )
                }
            }.sortedByDescending { it.createdAtMs }.take(MAX_ITEMS)
        }.getOrElse { emptyList() }
    }

    private fun persist(context: Context, offers: List<ChannelFileOffer>) {
        val arr = JSONArray()
        offers.forEach { offer ->
            arr.put(
                JSONObject()
                    .put("id", offer.id)
                    .put("file_name", offer.fileName)
                    .put("file_size_bytes", offer.fileSizeBytes)
                    .put("sender_id", offer.senderId)
                    .put("sender_label", offer.senderLabel)
                    .put("sender_ip", offer.senderIp.orEmpty())
                    .put("uri", offer.uri.orEmpty())
                    .put("created_at_ms", offer.createdAtMs)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_JSON, arr.toString()) }
    }

    private fun sanitize(offer: ChannelFileOffer): ChannelFileOffer {
        return offer.copy(
            fileName = sanitizeFileName(offer.fileName),
            senderLabel = sanitizeLabel(offer.senderLabel),
            senderIp = offer.senderIp?.trim()?.takeIf { it.isNotBlank() }?.take(64),
            uri = offer.uri?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank {
            "archivo"
        }.take(160)
    }

    private fun sanitizeLabel(raw: String): String {
        return raw.trim().replace(Regex("\\s+"), " ").ifBlank { "Equipo" }.take(64)
    }
}
