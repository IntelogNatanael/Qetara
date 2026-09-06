package com.example.wifidrop

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class PendingMessageTask(
    val id: String,
    val chatMessageId: String,
    val targetIp: String,
    val peerLabel: String?,
    val scope: ChatMessageScope,
    val message: String,
    val token: String,
    val pin: String,
    val clientId: String,
    val deviceLabel: String,
    val trackChatStatus: Boolean = true,
    val retriesUsed: Int = 0,
    val maxRetries: Int = 4,
    val nextAttemptAtMs: Long = 0L,
    val createdAtMs: Long = 0L,
    val lastError: String? = null,
    val expectedPeerId: String? = null,
    val directGroupSessionId: String? = null
)

object PendingMessageStore {

    private const val PREFS_NAME = "wifidrop_pending_messages"
    private const val KEY_JSON = "pending_json"
    private const val MAX_ITEMS = 300

    @Synchronized
    fun list(context: Context): List<PendingMessageTask> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_JSON, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return try {
            val arr = JSONArray(raw)
            buildList {
                for (index in 0 until arr.length()) {
                    val obj = arr.optJSONObject(index) ?: continue
                    val id = obj.optString("id").ifBlank { continue }
                    val chatMessageId = obj.optString("chat_id").ifBlank { continue }
                    val targetIp = sanitizeIp(obj.optString("target_ip"))
                    val message = runCatching { sanitizeMessage(obj.optString("message")) }.getOrNull() ?: continue
                    val token = FileTransfer.normalizeToken(obj.optString("token"))
                    val pin = TransferSecurity.normalizePin(obj.optString("pin"))
                    val clientId = sanitizeClientId(obj.optString("client_id"))
                    val deviceLabel = sanitizeLabel(obj.optString("device_label"))

                    if (targetIp.isBlank() || message.isBlank()) continue
                    if (!FileTransfer.isValidToken(token) || !TransferSecurity.isValidPin(pin)) continue
                    if (clientId.isBlank() || deviceLabel.isBlank()) continue

                    add(
                        PendingMessageTask(
                            id = id,
                            chatMessageId = chatMessageId,
                            targetIp = targetIp,
                            peerLabel = sanitizeLabel(obj.optString("peer_label")).ifBlank { null },
                            scope = parseScope(obj.optString("scope")),
                            message = message,
                            token = token,
                            pin = pin,
                            clientId = clientId,
                            deviceLabel = deviceLabel,
                            trackChatStatus = obj.optBoolean("track_chat_status", true),
                            retriesUsed = obj.optInt("retries_used", 0).coerceAtLeast(0),
                            maxRetries = obj.optInt("max_retries", 4).coerceIn(1, 4),
                            nextAttemptAtMs = obj.optLong("next_attempt_at_ms", 0L),
                            createdAtMs = obj.optLong("created_at_ms", 0L),
                            lastError = obj.optString("last_error").takeIf { it.isNotBlank() }?.take(240),
                            expectedPeerId = obj.optString("expected_peer_id").takeIf { it.isNotBlank() }?.take(80),
                            directGroupSessionId = obj.optString("direct_group_session_id").takeIf { it.isNotBlank() }?.take(80)
                        )
                    )
                }
            }.sortedBy { it.createdAtMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun upsert(context: Context, task: PendingMessageTask) {
        val current = list(context)
            .filterNot { it.id == task.id }
            .toMutableList()
        current.add(sanitize(task))
        persist(context, current)
    }

    @Synchronized
    fun remove(context: Context, taskId: String) {
        val normalized = taskId.trim()
        if (normalized.isBlank()) return
        val next = list(context).filterNot { it.id == normalized }
        persist(context, next)
    }

    @Synchronized
    fun removeByChatMessageId(context: Context, chatMessageIdRaw: String) {
        val chatMessageId = chatMessageIdRaw.trim()
        if (chatMessageId.isBlank()) return
        val next = list(context).filterNot { it.chatMessageId == chatMessageId }
        persist(context, next)
    }

    fun findByChatMessageId(context: Context, chatMessageIdRaw: String): PendingMessageTask? {
        val chatMessageId = chatMessageIdRaw.trim()
        if (chatMessageId.isBlank()) return null
        return list(context).firstOrNull { it.chatMessageId == chatMessageId }
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                remove(KEY_JSON)
            }
    }

    @Synchronized
    fun clearScope(context: Context, scope: ChatMessageScope) {
        val next = list(context).filterNot { it.scope == scope }
        persist(context, next)
    }

    private fun persist(context: Context, tasks: List<PendingMessageTask>) {
        val arr = JSONArray()
        tasks.sortedBy { it.createdAtMs }.take(MAX_ITEMS).forEach { task ->
            arr.put(
                JSONObject()
                    .put("id", task.id)
                    .put("chat_id", task.chatMessageId)
                    .put("target_ip", task.targetIp)
                    .put("peer_label", task.peerLabel.orEmpty())
                    .put("scope", task.scope.name)
                    .put("message", task.message)
                    .put("token", task.token)
                    .put("pin", task.pin)
                    .put("client_id", task.clientId)
                    .put("device_label", task.deviceLabel)
                    .put("track_chat_status", task.trackChatStatus)
                    .put("retries_used", task.retriesUsed)
                    .put("max_retries", task.maxRetries)
                    .put("next_attempt_at_ms", task.nextAttemptAtMs)
                    .put("created_at_ms", task.createdAtMs)
                    .put("last_error", task.lastError.orEmpty())
                    .put("expected_peer_id", task.expectedPeerId.orEmpty())
                    .put("direct_group_session_id", task.directGroupSessionId.orEmpty())
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_JSON, arr.toString())
            }
    }

    private fun sanitize(task: PendingMessageTask): PendingMessageTask {
        return task.copy(
            chatMessageId = task.chatMessageId.trim().take(80),
            targetIp = sanitizeIp(task.targetIp),
            peerLabel = sanitizeLabel(task.peerLabel.orEmpty()).ifBlank { null },
            scope = task.scope,
            message = sanitizeMessage(task.message),
            token = FileTransfer.normalizeToken(task.token),
            pin = TransferSecurity.normalizePin(task.pin),
            clientId = sanitizeClientId(task.clientId),
            deviceLabel = sanitizeLabel(task.deviceLabel),
            trackChatStatus = task.trackChatStatus,
            retriesUsed = task.retriesUsed.coerceAtLeast(0),
            maxRetries = task.maxRetries.coerceIn(1, 4),
            nextAttemptAtMs = task.nextAttemptAtMs.coerceAtLeast(0L),
            createdAtMs = task.createdAtMs.coerceAtLeast(0L),
            lastError = task.lastError?.take(240)
        )
    }

    private fun sanitizeMessage(raw: String): String {
        return com.example.wifidrop.protocol.requireValidTransportMessage(raw)
    }

    private fun sanitizeIp(raw: String): String {
        return raw.trim().take(64)
    }

    private fun sanitizeLabel(raw: String): String {
        val clean = raw.trim().replace(Regex("\\s+"), " ").take(64)
        return if (clean.isBlank()) "peer" else clean
    }

    private fun sanitizeClientId(raw: String): String {
        return raw.trim().take(80)
    }

    private fun parseScope(raw: String): ChatMessageScope {
        return when (raw) {
            ChatMessageScope.GLOBAL_LAN.name -> ChatMessageScope.GLOBAL_LAN
            ChatMessageScope.DIRECT_CHANNEL.name -> ChatMessageScope.DIRECT_CHANNEL
            else -> ChatMessageScope.DIRECT
        }
    }
}
