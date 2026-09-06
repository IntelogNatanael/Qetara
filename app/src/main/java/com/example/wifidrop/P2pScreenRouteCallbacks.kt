package com.example.wifidrop

import com.example.wifidrop.presentation.P2pConnectionHintsPresenter
import com.example.wifidrop.presentation.P2pFeedbackMessage
import com.example.wifidrop.presentation.P2pOutboundOrchestrationResult
import com.example.wifidrop.presentation.P2pTransientUiEffect

fun adjustedPreferencesForConnectionViewMode(
    current: UxPreferences,
    next: ConnectionViewMode
): UxPreferences {
    return when (next) {
        ConnectionViewMode.WIFI_DIRECT -> current.copy(
            connectionViewMode = next,
            activeConnectionMode = ConnectionMode.WIFI_DIRECT,
            wifiDirectModeEnabled = true
        )

        ConnectionViewMode.LAN -> current.copy(
            connectionViewMode = next,
            activeConnectionMode = ConnectionMode.LAN,
            lanModeEnabled = true
        )

        ConnectionViewMode.ADVANCED -> current.copy(
            connectionViewMode = next
        )
    }
}

fun normalizedPreferencesForGlobalLan(
    current: UxPreferences,
    enabled: Boolean
): UxPreferences? {
    val normalized = enabled && current.lanModeEnabled
    if (current.joinedGlobalLan == normalized) return null
    return current.copy(joinedGlobalLan = normalized)
}

fun applyOutboundResult(
    result: P2pOutboundOrchestrationResult,
    clearChatDraft: () -> Unit,
    pushFeedback: (P2pFeedbackMessage?) -> Unit
) {
    if (result.clearChatDraft) {
        clearChatDraft()
    }
    pushFeedback(result.feedback)
}

fun applyLanSuggestedTarget(
    presenter: P2pConnectionHintsPresenter,
    currentTargetIp: String,
    suggestedIp: String?,
    onResolved: (String) -> Unit
) {
    presenter.onLanTargetSuggested(
        currentTargetIp = currentTargetIp,
        suggestedIp = suggestedIp
    )?.let(onResolved)
}

fun applyTransientUiEffect(
    effect: P2pTransientUiEffect?,
    hapticFeedback: (Boolean) -> Unit,
    pushFeedback: (P2pFeedbackMessage?) -> Unit
) {
    effect ?: return
    if (effect.triggerSuccessHaptic) {
        hapticFeedback(true)
    }
    pushFeedback(effect.feedback)
}

fun adjustedPreferencesForConnectionMode(
    current: UxPreferences,
    next: ConnectionMode
): UxPreferences = when (next) {
    ConnectionMode.WIFI_DIRECT -> current.copy(activeConnectionMode = next, wifiDirectModeEnabled = true)
    ConnectionMode.LAN -> current.copy(activeConnectionMode = next, lanModeEnabled = true)
}

fun canUseLanTargetSuggestion(viewMode: ConnectionViewMode): Boolean =
    viewMode != ConnectionViewMode.WIFI_DIRECT
