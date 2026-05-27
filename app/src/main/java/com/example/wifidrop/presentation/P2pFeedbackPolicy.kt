package com.example.wifidrop.presentation

import com.example.wifidrop.ChatChannel
import java.util.Locale

data class P2pFeedbackMessage(
    val message: String,
    val isError: Boolean = false
)

data class P2pUndoFeedbackPlan(
    val promptMessage: String,
    val actionLabel: String = "Deshacer",
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
    if (
        suppressProgressMessages &&
        (
            message.contains("sincronizando", ignoreCase = true) ||
                message.contains("reintentando", ignoreCase = true)
            )
    ) {
        return null
    }
    return message
}

fun isLikelyErrorMessage(message: String): Boolean {
    val normalized = message.lowercase(Locale.ROOT)
    return normalized.contains("error") ||
        normalized.contains("fall") ||
        normalized.contains("inval") ||
        normalized.contains("expir") ||
        normalized.contains("no se pudo") ||
        normalized.startsWith("no ") ||
        normalized.contains("faltan") ||
        normalized.contains("inactivo") ||
        normalized.contains("cancelad")
}

fun buildDeleteMessageUndoPlan(): P2pUndoFeedbackPlan {
    return P2pUndoFeedbackPlan(
        promptMessage = "Se eliminara el mensaje.",
        undoFeedback = P2pFeedbackMessage("Eliminacion cancelada."),
        committedFeedback = P2pFeedbackMessage("Mensaje eliminado.")
    )
}

fun buildClearMessagesUndoPlan(channel: ChatChannel): P2pUndoFeedbackPlan {
    val scopeLabel = when (channel) {
        ChatChannel.DIRECT -> "el chat directo"
        ChatChannel.GLOBAL -> "el canal"
    }
    val committed = when (channel) {
        ChatChannel.DIRECT -> "Chat directo limpio."
        ChatChannel.GLOBAL -> "Canal limpio."
    }
    return P2pUndoFeedbackPlan(
        promptMessage = "Se limpiara $scopeLabel.",
        undoFeedback = P2pFeedbackMessage("Limpieza cancelada."),
        committedFeedback = P2pFeedbackMessage(committed)
    )
}

fun buildFavoriteRemovedUndoPlan(): P2pUndoFeedbackPlan {
    return P2pUndoFeedbackPlan(
        promptMessage = "Favorito removido.",
        undoFeedback = P2pFeedbackMessage("Favorito restaurado.")
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
        normalizedAlias == currentAlias -> P2pFeedbackMessage("Apodo sin cambios.")
        normalizedAlias.isBlank() -> P2pFeedbackMessage("Apodo eliminado.")
        else -> P2pFeedbackMessage("Apodo actualizado.")
    }
}
