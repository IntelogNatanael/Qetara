package com.example.wifidrop

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

enum class ChatMessageDirection {
    OUTGOING,
    INCOMING
}

enum class ChatMessageStatus {
    QUEUED,
    SENDING,
    PUBLISHED,
    SENT,
    RECEIVED,
    CANCELED,
    FAILED
}

enum class ChatMessageScope {
    DIRECT,
    GLOBAL_LAN,
    DIRECT_CHANNEL
}

data class ChatTransportPeer(
    val id: String,
    val label: String,
    val ip: String
)

object ChatMessageScopeCodec {
    private const val GLOBAL_LAN_MARKER = "\u2063QGL\u2063"
    private const val TRANSPORT_MARKER = "\u2063QCT\u2063"
    private const val KIND_USER = "user"
    private const val KIND_DIRECT_RELAY = "direct_relay"
    private const val KIND_CHANNEL_RELAY = "channel_relay"
    private const val KIND_ROSTER = "roster"
    private const val KIND_FILE_OFFER = "file_offer"
    private const val KIND_FILE_REQUEST = "file_request"

    sealed interface DecodedChatPayload {
        data class User(
            val scope: ChatMessageScope,
            val text: String,
            val senderId: String? = null,
            val senderLabel: String? = null,
            val senderIp: String? = null
        ) : DecodedChatPayload

        data class DirectRelayRequest(
            val text: String,
            val senderId: String,
            val senderLabel: String,
            val targetPeerId: String? = null,
            val targetIp: String,
            val targetLabel: String? = null
        ) : DecodedChatPayload

        data class ChannelRelayRequest(
            val text: String,
            val senderId: String,
            val senderLabel: String
        ) : DecodedChatPayload

        data class DirectRoster(
            val peers: List<ChatTransportPeer>
        ) : DecodedChatPayload

        data class FileOffer(
            val offer: ChannelFileOffer
        ) : DecodedChatPayload

        data class FileRequest(
            val request: ChannelFileRequest
        ) : DecodedChatPayload
    }

    fun encodeForTransport(textRaw: String, scope: ChatMessageScope): String {
        val text = sanitizeChatText(textRaw)
        if (text.startsWith(TRANSPORT_MARKER)) {
            return text
        }
        return when (scope) {
            ChatMessageScope.DIRECT -> text
            ChatMessageScope.GLOBAL_LAN -> GLOBAL_LAN_MARKER + text
            ChatMessageScope.DIRECT_CHANNEL -> encodeUserPayload(
                textRaw = text,
                scope = ChatMessageScope.DIRECT_CHANNEL
            )
        }
    }

    fun encodeUserPayload(
        textRaw: String,
        scope: ChatMessageScope,
        senderId: String? = null,
        senderLabel: String? = null,
        senderIp: String? = null
    ): String {
        val payload = JSONObject()
            .put("kind", KIND_USER)
            .put("scope", scope.name)
            .put("text", sanitizeChatText(textRaw))
        senderId?.trim()?.takeIf { it.isNotBlank() }?.let { payload.put("sender_id", it.take(80)) }
        senderLabel?.trim()?.takeIf { it.isNotBlank() }?.let { payload.put("sender_label", it.take(64)) }
        senderIp?.trim()?.takeIf { it.isNotBlank() }?.let { payload.put("sender_ip", it.take(64)) }
        return TRANSPORT_MARKER + payload.toString()
    }

    fun encodeDirectRelayRequest(
        textRaw: String,
        senderId: String,
        senderLabel: String,
        targetPeerId: String?,
        targetIp: String,
        targetLabel: String?
    ): String {
        val payload = JSONObject()
            .put("kind", KIND_DIRECT_RELAY)
            .put("text", sanitizeChatText(textRaw))
            .put("sender_id", senderId.trim().take(80))
            .put("sender_label", senderLabel.trim().take(64))
            .put("target_ip", targetIp.trim().take(64))
        targetPeerId?.trim()?.takeIf { it.isNotBlank() }?.let { payload.put("target_peer_id", it.take(80)) }
        targetLabel?.trim()?.takeIf { it.isNotBlank() }?.let { payload.put("target_label", it.take(64)) }
        return TRANSPORT_MARKER + payload.toString()
    }

    fun encodeDirectChannelRelayRequest(
        textRaw: String,
        senderId: String,
        senderLabel: String
    ): String {
        val payload = JSONObject()
            .put("kind", KIND_CHANNEL_RELAY)
            .put("text", sanitizeChatText(textRaw))
            .put("sender_id", senderId.trim().take(80))
            .put("sender_label", senderLabel.trim().take(64))
        return TRANSPORT_MARKER + payload.toString()
    }

    fun encodeDirectRoster(peers: List<ChatTransportPeer>): String {
        val arr = JSONArray()
        peers.forEach { peer ->
            if (peer.id.isBlank() || peer.ip.isBlank()) return@forEach
            arr.put(
                JSONObject()
                    .put("id", peer.id.trim().take(80))
                    .put("label", peer.label.trim().take(64))
                    .put("ip", peer.ip.trim().take(64))
            )
        }
        return TRANSPORT_MARKER + JSONObject()
            .put("kind", KIND_ROSTER)
            .put("peers", arr)
            .toString()
    }

    fun encodeChannelFileOffer(offer: ChannelFileOffer): String {
        return TRANSPORT_MARKER + JSONObject()
            .put("kind", KIND_FILE_OFFER)
            .put("scope", ChatMessageScope.GLOBAL_LAN.name)
            .put("offer_id", offer.id.trim().take(120))
            .put("file_name", offer.fileName.trim().take(160))
            .put("file_size_bytes", offer.fileSizeBytes)
            .put("sender_id", offer.senderId.trim().take(80))
            .put("sender_label", offer.senderLabel.trim().take(64))
            .put("sender_ip", offer.senderIp.orEmpty().trim().take(64))
            .put("created_at_ms", offer.createdAtMs)
            .toString()
    }

    fun encodeChannelFileRequest(request: ChannelFileRequest): String {
        return TRANSPORT_MARKER + JSONObject()
            .put("kind", KIND_FILE_REQUEST)
            .put("offer_id", request.offerId.trim().take(120))
            .put("requester_id", request.requesterId.trim().take(80))
            .put("requester_label", request.requesterLabel.trim().take(64))
            .put("requester_ip", request.requesterIp.orEmpty().trim().take(64))
            .toString()
    }

    fun decodeFromTransport(messageRaw: String): DecodedChatPayload {
        val raw = messageRaw.trim()
        if (raw.startsWith(TRANSPORT_MARKER)) {
            return decodeStructuredPayload(raw.removePrefix(TRANSPORT_MARKER))
        }
        return if (raw.startsWith(GLOBAL_LAN_MARKER)) {
            DecodedChatPayload.User(
                scope = ChatMessageScope.GLOBAL_LAN,
                text = sanitizeChatText(raw.removePrefix(GLOBAL_LAN_MARKER))
            )
        } else {
            DecodedChatPayload.User(
                scope = ChatMessageScope.DIRECT,
                text = sanitizeChatText(raw)
            )
        }
    }

    private fun decodeStructuredPayload(rawPayload: String): DecodedChatPayload {
        return runCatching {
            val payload = JSONObject(rawPayload)
            when (payload.optString("kind")) {
                KIND_DIRECT_RELAY -> DecodedChatPayload.DirectRelayRequest(
                    text = sanitizeChatText(payload.optString("text")),
                    senderId = payload.optString("sender_id").trim().take(80),
                    senderLabel = payload.optString("sender_label").trim().take(64),
                    targetPeerId = payload.optString("target_peer_id").trim().takeIf { it.isNotBlank() }?.take(80),
                    targetIp = payload.optString("target_ip").trim().take(64),
                    targetLabel = payload.optString("target_label").trim().takeIf { it.isNotBlank() }?.take(64)
                )

                KIND_CHANNEL_RELAY -> DecodedChatPayload.ChannelRelayRequest(
                    text = sanitizeChatText(payload.optString("text")),
                    senderId = payload.optString("sender_id").trim().take(80),
                    senderLabel = payload.optString("sender_label").trim().take(64)
                )

                KIND_ROSTER -> {
                    val peers = payload.optJSONArray("peers")
                    val normalizedPeers = buildList {
                        if (peers == null) return@buildList
                        for (index in 0 until peers.length()) {
                            val item = peers.optJSONObject(index) ?: continue
                            val id = item.optString("id").trim().take(80)
                            val ip = item.optString("ip").trim().take(64)
                            if (id.isBlank() || ip.isBlank()) continue
                            add(
                                ChatTransportPeer(
                                    id = id,
                                    label = item.optString("label").trim().take(64).ifBlank { ip },
                                    ip = ip
                                )
                            )
                        }
                    }
                    DecodedChatPayload.DirectRoster(normalizedPeers)
                }

                KIND_FILE_OFFER -> DecodedChatPayload.FileOffer(
                    ChannelFileOffer(
                        id = payload.optString("offer_id").trim().take(120),
                        fileName = payload.optString("file_name").trim().take(160).ifBlank { "archivo" },
                        fileSizeBytes = payload.optLong("file_size_bytes", -1L),
                        senderId = payload.optString("sender_id").trim().take(80),
                        senderLabel = payload.optString("sender_label").trim().take(64).ifBlank { "Equipo" },
                        senderIp = payload.optString("sender_ip").trim().takeIf { it.isNotBlank() }?.take(64),
                        uri = null,
                        createdAtMs = payload.optLong("created_at_ms", System.currentTimeMillis())
                    )
                )

                KIND_FILE_REQUEST -> DecodedChatPayload.FileRequest(
                    ChannelFileRequest(
                        offerId = payload.optString("offer_id").trim().take(120),
                        requesterId = payload.optString("requester_id").trim().take(80),
                        requesterLabel = payload.optString("requester_label").trim().take(64).ifBlank { "Equipo" },
                        requesterIp = payload.optString("requester_ip").trim().takeIf { it.isNotBlank() }?.take(64)
                    )
                )

                else -> DecodedChatPayload.User(
                    scope = when (payload.optString("scope")) {
                        ChatMessageScope.GLOBAL_LAN.name -> ChatMessageScope.GLOBAL_LAN
                        ChatMessageScope.DIRECT_CHANNEL.name -> ChatMessageScope.DIRECT_CHANNEL
                        else -> ChatMessageScope.DIRECT
                    },
                    text = sanitizeChatText(payload.optString("text")),
                    senderId = payload.optString("sender_id").trim().takeIf { it.isNotBlank() }?.take(80),
                    senderLabel = payload.optString("sender_label").trim().takeIf { it.isNotBlank() }?.take(64),
                    senderIp = payload.optString("sender_ip").trim().takeIf { it.isNotBlank() }?.take(64)
                )
            }
        }.getOrElse {
            DecodedChatPayload.User(
                scope = ChatMessageScope.DIRECT,
                text = sanitizeChatText(rawPayload)
            )
        }
    }
}

data class ChatMessageEntry(
    val id: String,
    val direction: ChatMessageDirection,
    val scope: ChatMessageScope,
    val status: ChatMessageStatus,
    val text: String,
    val timestampMs: Long,
    val peerLabel: String?,
    val peerIp: String?,
    val errorCause: String?
)

object ChatMessageStore {

    private const val PREFS_NAME = "wifidrop_chat"
    private const val KEY_JSON = "chat_json"
    private const val MAX_ITEMS = 300

    fun list(context: Context, limit: Int = 120): List<ChatMessageEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_JSON, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return try {
            val arr = JSONArray(raw)
            buildList {
                for (index in 0 until arr.length()) {
                    val obj = arr.optJSONObject(index) ?: continue
                    val id = obj.optString("id").ifBlank { continue }
                    val direction = parseDirection(obj.optString("direction"))
                    val status = parseStatus(obj.optString("status"))
                    val text = sanitizeChatText(obj.optString("text"))
                    if (text.isBlank()) continue
                    add(
                        ChatMessageEntry(
                            id = id,
                            direction = direction,
                            scope = parseScope(obj.optString("scope")),
                            status = status,
                            text = text,
                            timestampMs = obj.optLong("timestamp_ms", 0L),
                            peerLabel = sanitizeLabel(obj.optString("peer_label")).ifBlank { null },
                            peerIp = sanitizeIp(obj.optString("peer_ip")).ifBlank { null },
                            errorCause = obj.optString("error").takeIf { it.isNotBlank() }?.take(240)
                        )
                    )
                }
            }.sortedByDescending { it.timestampMs }.take(limit.coerceIn(1, MAX_ITEMS))
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun append(context: Context, entry: ChatMessageEntry) {
        val current = list(context, limit = MAX_ITEMS).toMutableList()
        current.add(0, sanitize(entry))
        persist(context, current.take(MAX_ITEMS))
    }

    fun newOutgoing(
        text: String,
        peerLabel: String?,
        peerIp: String?,
        scope: ChatMessageScope,
        status: ChatMessageStatus,
        errorCause: String?
    ): ChatMessageEntry {
        val now = System.currentTimeMillis()
        val id = "${now}_${Random.nextInt(1000, 9999)}"
        return ChatMessageEntry(
            id = id,
            direction = ChatMessageDirection.OUTGOING,
            scope = scope,
            status = status,
            text = sanitizeChatText(text),
            timestampMs = now,
            peerLabel = sanitizeLabel(peerLabel.orEmpty()).ifBlank { null },
            peerIp = sanitizeIp(peerIp.orEmpty()).ifBlank { null },
            errorCause = errorCause?.take(240)
        )
    }

    fun newIncoming(
        text: String,
        peerLabel: String?,
        peerIp: String?,
        scope: ChatMessageScope
    ): ChatMessageEntry {
        val now = System.currentTimeMillis()
        val id = "${now}_${Random.nextInt(1000, 9999)}"
        return ChatMessageEntry(
            id = id,
            direction = ChatMessageDirection.INCOMING,
            scope = scope,
            status = ChatMessageStatus.RECEIVED,
            text = sanitizeChatText(text),
            timestampMs = now,
            peerLabel = sanitizeLabel(peerLabel.orEmpty()).ifBlank { null },
            peerIp = sanitizeIp(peerIp.orEmpty()).ifBlank { null },
            errorCause = null
        )
    }

    fun updateStatus(
        context: Context,
        messageIdRaw: String,
        status: ChatMessageStatus,
        errorCause: String?
    ) {
        val messageId = messageIdRaw.trim()
        if (messageId.isBlank()) return
        val next = list(context, limit = MAX_ITEMS).map { item ->
            if (item.id == messageId) {
                item.copy(
                    status = status,
                    errorCause = errorCause?.take(240)
                )
            } else {
                item
            }
        }
        persist(context, next)
    }

    fun remove(context: Context, messageIdRaw: String) {
        val messageId = messageIdRaw.trim()
        if (messageId.isBlank()) return
        val next = list(context, limit = MAX_ITEMS).filterNot { it.id == messageId }
        persist(context, next)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                remove(KEY_JSON)
            }
    }

    fun clearScope(context: Context, scope: ChatMessageScope) {
        val next = list(context, limit = MAX_ITEMS).filterNot { it.scope == scope }
        persist(context, next)
    }

    private fun persist(context: Context, items: List<ChatMessageEntry>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("direction", item.direction.name)
                    .put("scope", item.scope.name)
                    .put("status", item.status.name)
                    .put("text", item.text)
                    .put("timestamp_ms", item.timestampMs)
                    .put("peer_label", item.peerLabel.orEmpty())
                    .put("peer_ip", item.peerIp.orEmpty())
                    .put("error", item.errorCause.orEmpty())
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_JSON, arr.toString())
            }
    }

    private fun sanitize(entry: ChatMessageEntry): ChatMessageEntry {
        return entry.copy(
            text = sanitizeChatText(entry.text),
            peerLabel = sanitizeLabel(entry.peerLabel.orEmpty()).ifBlank { null },
            peerIp = sanitizeIp(entry.peerIp.orEmpty()).ifBlank { null },
            errorCause = entry.errorCause?.take(240)
        )
    }

    private fun parseDirection(raw: String): ChatMessageDirection {
        return when (raw) {
            ChatMessageDirection.INCOMING.name -> ChatMessageDirection.INCOMING
            else -> ChatMessageDirection.OUTGOING
        }
    }

    private fun parseStatus(raw: String): ChatMessageStatus {
        return when (raw) {
            ChatMessageStatus.QUEUED.name -> ChatMessageStatus.QUEUED
            ChatMessageStatus.SENDING.name -> ChatMessageStatus.SENDING
            ChatMessageStatus.PUBLISHED.name -> ChatMessageStatus.PUBLISHED
            ChatMessageStatus.RECEIVED.name -> ChatMessageStatus.RECEIVED
            ChatMessageStatus.CANCELED.name -> ChatMessageStatus.CANCELED
            ChatMessageStatus.FAILED.name -> ChatMessageStatus.FAILED
            else -> ChatMessageStatus.SENT
        }
    }

    private fun parseScope(raw: String): ChatMessageScope {
        return when (raw) {
            ChatMessageScope.GLOBAL_LAN.name -> ChatMessageScope.GLOBAL_LAN
            ChatMessageScope.DIRECT_CHANNEL.name -> ChatMessageScope.DIRECT_CHANNEL
            else -> ChatMessageScope.DIRECT
        }
    }

    private fun sanitizeLabel(raw: String): String {
        return raw.trim().replace(Regex("\\s+"), " ").take(64)
    }

    private fun sanitizeIp(raw: String): String {
        return raw.trim().take(64)
    }
}

private fun sanitizeChatText(raw: String): String {
    return raw.trim().replace(Regex("\\s+"), " ").take(2_000)
}
