package com.example.wifidrop.presentation

import com.example.wifidrop.R
import com.example.wifidrop.appString

import com.example.wifidrop.ChatChannel

data class P2pFeedbackMessage(
    val message: String,
    val isError: Boolean = false
)

data class P2pUndoFeedbackPlan(
    val promptMessage: String,
    val actionLabel: String = appString(R.string.pr_undo),
    val undoFeedback: P2pFeedbackMessage? = null,
    val committedFeedback: P2pFeedbackMessage? = null
)

class FeedbackDeduplicator {
    private var lastKey: String? = null
    private var lastShownAtMs: Long = 0L

    @Synchronized
    fun shouldShow(message: String, isError: Boolean, nowMs: Long, windowMs: Long = 1_500L): Boolean {
        val key = "${if (isError) "E" else "S"}:${message.trim()}"
        val recent = key == lastKey && nowMs - lastShownAtMs < windowMs
        if (recent) return false
        lastKey = key
        lastShownAtMs = nowMs
        return true
    }
}

fun relayableFeedbackMessage(
    rawMessage: String,
    lastFeedback: String,
    suppressProgressMessages: Boolean = false
): String? {
    val message = rawMessage.trim()
    if (message.isBlank() || message == lastFeedback) return null
    if (suppressProgressMessages && isSessionSyncProgress(message)) {
        return null
    }
    return message
}

fun isLikelyErrorMessage(message: String): Boolean {
    return isKnownPresentationFailure(message)
}

fun buildDeleteMessageUndoPlan(): P2pUndoFeedbackPlan {
    return P2pUndoFeedbackPlan(
        promptMessage = appString(R.string.pr_delete_prompt),
        undoFeedback = P2pFeedbackMessage(appString(R.string.pr_delete_canceled)),
        committedFeedback = P2pFeedbackMessage(appString(R.string.pr_message_deleted))
    )
}

fun buildClearMessagesUndoPlan(channel: ChatChannel): P2pUndoFeedbackPlan {
    val scopeLabel = when (channel) {
        ChatChannel.DIRECT -> appString(R.string.pr_direct_chat_scope)
        ChatChannel.GLOBAL -> appString(R.string.pr_channel_scope)
    }
    val committed = when (channel) {
        ChatChannel.DIRECT -> appString(R.string.pr_direct_chat_cleared)
        ChatChannel.GLOBAL -> appString(R.string.pr_channel_cleared)
    }
    return P2pUndoFeedbackPlan(
        promptMessage = appString(R.string.pr_clear_prompt, scopeLabel),
        undoFeedback = P2pFeedbackMessage(appString(R.string.pr_clear_canceled)),
        committedFeedback = P2pFeedbackMessage(committed)
    )
}

fun buildFavoriteRemovedUndoPlan(): P2pUndoFeedbackPlan {
    return P2pUndoFeedbackPlan(
        promptMessage = appString(R.string.pr_favorite_removed),
        undoFeedback = P2pFeedbackMessage(appString(R.string.pr_favorite_restored))
    )
}

fun normalizePeerAlias(raw: String): String {
    return raw.trim().replace(Regex("\\s+"), " ").take(48)
}

fun buildAliasUpdateFeedback(
    normalizedAlias: String,
    currentAlias: String
): P2pFeedbackMessage {
    return when {
        normalizedAlias == currentAlias -> P2pFeedbackMessage(appString(R.string.pr_alias_unchanged))
        normalizedAlias.isBlank() -> P2pFeedbackMessage(appString(R.string.pr_alias_removed))
        else -> P2pFeedbackMessage(appString(R.string.pr_alias_updated))
    }
}
