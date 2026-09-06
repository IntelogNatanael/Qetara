package com.example.wifidrop.pc

internal fun desktopConversationKey(scope: DesktopChatScope, peerIp: String? = null): String =
    scope.name + ":" + if (scope == DesktopChatScope.GLOBAL_LAN) "*" else peerIp.orEmpty()

internal data class DesktopChatDraft(val text: String = "", val attachmentPath: String = "")

/** Ephemeral drafts are scoped to their recipient; sending one never clears another. */
internal class DesktopConversationDrafts(private val capacity: Int = 64) {
    private val drafts = LinkedHashMap<String, DesktopChatDraft>()

    fun save(key: String, draft: DesktopChatDraft) {
        drafts.remove(key)
        if (draft.text.isNotEmpty() || draft.attachmentPath.isNotEmpty()) drafts[key] = draft
        while (drafts.size > capacity.coerceAtLeast(1)) drafts.remove(drafts.keys.first())
    }

    fun restore(key: String): DesktopChatDraft = drafts[key] ?: DesktopChatDraft()

    fun acknowledgeDelivery(
        key: String, expectedRecipients: Set<String>,
        textRecipients: Set<String>, attachmentRecipients: Set<String>,
        sentText: String?, sentAttachmentPath: String?
    ) {
        if (expectedRecipients.isEmpty()) return
        acknowledge(
            key,
            sentText = sentText?.takeIf { textRecipients.containsAll(expectedRecipients) },
            sentAttachmentPath = sentAttachmentPath?.takeIf { attachmentRecipients.containsAll(expectedRecipients) }
        )
    }

    fun acknowledge(key: String, sentText: String?, sentAttachmentPath: String?) {
        val existing = restore(key)
        save(
            key,
            existing.copy(
                text = if (sentText != null && existing.text.trim() == sentText) "" else existing.text,
                attachmentPath = if (sentAttachmentPath != null && existing.attachmentPath == sentAttachmentPath) "" else existing.attachmentPath
            )
        )
    }
}

internal fun desktopConversationPeers(
    peers: List<DesktopLanPeer>,
    messages: List<DesktopChatEntry>
): List<DesktopLanPeer> {
    val known = peers.associateByTo(LinkedHashMap()) { it.ip }
    messages.filter { it.scope == DesktopChatScope.DIRECT }.asReversed().forEach { message ->
        val address = message.peerAddress.trim()
        if (address.isNotBlank() && address !in known) {
            known[address] = DesktopLanPeer(
                id = address, label = message.peerLabel, ip = address,
                sessionActive = false, trustedByHost = false, globalLanJoined = false,
                lastSeenAtMs = 0L
            )
        }
    }
    return known.values.toList()
}

internal fun desktopDirectTarget(peers: List<DesktopLanPeer>, selectedIp: String?): DesktopLanPeer? {
    val active = peers.filter { it.sessionActive && it.ip.isNotBlank() }
    return if (selectedIp.isNullOrBlank()) active.firstOrNull()
        else active.firstOrNull { it.ip == selectedIp }
}

internal data class DesktopChatCompletionFeedback(
    val phase: DesktopTaskPhase, val status: String, val notice: String? = null
)

/** A completed send belongs to its original conversation, even when another draft is visible. */
internal fun desktopChatCompletionFeedback(
    originKey: String, visibleKey: String, recipientLabel: String,
    phase: DesktopTaskPhase, status: String
): DesktopChatCompletionFeedback = if (originKey == visibleKey) {
    DesktopChatCompletionFeedback(phase, status)
} else {
    DesktopChatCompletionFeedback(
        DesktopTaskPhase.IDLE,
        if (visibleKey == desktopConversationKey(DesktopChatScope.GLOBAL_LAN)) "Conversación del Canal Wi-Fi."
        else "Escribe un mensaje para este equipo.",
        "$recipientLabel: $status"
    )
}
