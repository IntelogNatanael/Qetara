package com.example.wifidrop.presentation

data class P2pTransientUiEffect(
    val feedback: P2pFeedbackMessage? = null,
    val triggerSuccessHaptic: Boolean = false
)

class P2pUiEffectsPresenter {
    private var wasConnected: Boolean = false
    private var lastTokenSyncFeedback: String = ""
    private var lastShareFeedback: String = ""
    private var lastMessageFeedback: String = ""

    fun onConnectionChanged(connectedNow: Boolean): P2pTransientUiEffect? {
        val shouldAnnounce = connectedNow && !wasConnected
        wasConnected = connectedNow
        return if (shouldAnnounce) {
            P2pTransientUiEffect(
                feedback = P2pFeedbackMessage("Conexion de red establecida."),
                triggerSuccessHaptic = true
            )
        } else {
            null
        }
    }

    fun relaySyncStatus(rawMessage: String): P2pFeedbackMessage? {
        val message = relayableFeedbackMessage(
            rawMessage = rawMessage,
            lastFeedback = lastTokenSyncFeedback,
            suppressProgressMessages = true
        ) ?: return null
        lastTokenSyncFeedback = message
        return P2pFeedbackMessage(
            message = message,
            isError = isLikelyErrorMessage(message)
        )
    }

    fun relayShareStatus(rawMessage: String): P2pFeedbackMessage? {
        val message = relayableFeedbackMessage(
            rawMessage = rawMessage,
            lastFeedback = lastShareFeedback
        ) ?: return null
        lastShareFeedback = message
        return P2pFeedbackMessage(
            message = message,
            isError = isIncompleteAttachmentRecovery(message) || isLikelyErrorMessage(message)
        )
    }

    fun relayMessageStatus(rawMessage: String): P2pFeedbackMessage? {
        val message = relayableFeedbackMessage(
            rawMessage = rawMessage,
            lastFeedback = lastMessageFeedback
        ) ?: return null
        if (message.contains("enviando", ignoreCase = true)) return null
        lastMessageFeedback = message
        return P2pFeedbackMessage(
            message = message,
            isError = isLikelyErrorMessage(message)
        )
    }
}
