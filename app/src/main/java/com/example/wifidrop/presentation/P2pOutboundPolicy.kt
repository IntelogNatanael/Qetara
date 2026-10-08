package com.example.wifidrop.presentation

import com.example.wifidrop.R
import com.example.wifidrop.appString
import com.example.wifidrop.appQuantityString

import com.example.wifidrop.ConnectionSnapshot
import com.example.wifidrop.FileTransfer
import com.example.wifidrop.TransferSecurity

data class P2pOutboundContext(
    val permissionGranted: Boolean,
    val lanConnected: Boolean,
    val sessionToken: String,
    val sessionPin: String,
    val sessionExpired: Boolean,
    val connection: ConnectionSnapshot?
)

sealed interface P2pOutboundDecision<out T> {
    data class Ready<T>(val plan: T) : P2pOutboundDecision<T>
    data class Blocked(val message: String) : P2pOutboundDecision<Nothing>
}

data class P2pFileBatchPlan(
    val targetIp: String,
    val shareImportStatus: String,
    val feedbackMessage: String
)

data class P2pDirectMessagePlan(
    val targetIp: String,
    val message: String,
    val feedbackMessage: String
)

data class P2pGlobalMessagePlan(
    val message: String?,
    val includeFiles: Boolean,
    val feedbackMessage: String
)

data class P2pDirectChatComposerPlan(
    val targetIps: List<String>,
    val message: String?,
    val includeFiles: Boolean,
    val feedbackMessage: String
)

fun buildFileBatchSendPlan(
    context: P2pOutboundContext,
    targetIp: String?,
    fileCount: Int
): P2pOutboundDecision<P2pFileBatchPlan> {
    if (fileCount <= 0) {
        return P2pOutboundDecision.Blocked(appString(R.string.pr_file_required))
    }

    return when (val targetDecision = validateOutboundTarget(appString(R.string.pr_send_files_action), context, targetIp)) {
        is P2pOutboundDecision.Blocked -> targetDecision
        is P2pOutboundDecision.Ready -> P2pOutboundDecision.Ready(
            P2pFileBatchPlan(
                targetIp = targetDecision.plan,
                shareImportStatus = appQuantityString(R.plurals.pr_files_sending, fileCount, fileCount),
                feedbackMessage = appQuantityString(R.plurals.pr_files_queued_count, fileCount, fileCount)
            )
        )
    }
}

fun buildDirectMessageSendPlan(
    context: P2pOutboundContext,
    draft: String,
    targetIp: String?
): P2pOutboundDecision<P2pDirectMessagePlan> {
    val message = sanitizeOutgoingMessage(draft)
    if (message.isBlank()) {
        return P2pOutboundDecision.Blocked(appString(R.string.pr_message_required))
    }

    return when (val targetDecision = validateOutboundTarget(appString(R.string.pr_send_messages_action), context, targetIp)) {
        is P2pOutboundDecision.Blocked -> targetDecision
        is P2pOutboundDecision.Ready -> P2pOutboundDecision.Ready(
            P2pDirectMessagePlan(
                targetIp = targetDecision.plan,
                message = message,
                feedbackMessage = appString(R.string.pr_message_queued)
            )
        )
    }
}

fun buildGlobalMessageSendPlan(
    context: P2pOutboundContext,
    draft: String,
    selectedFilesCount: Int,
    globalLanJoined: Boolean,
    targetCount: Int
): P2pOutboundDecision<P2pGlobalMessagePlan> {
    val message = sanitizeOutgoingMessage(draft)
    val hasMessage = message.isNotBlank()
    val hasFiles = selectedFilesCount > 0
    if (!hasMessage && !hasFiles) {
        return P2pOutboundDecision.Blocked(appString(R.string.pr_message_or_file_required))
    }
    if (!context.lanConnected) {
        return P2pOutboundDecision.Blocked(appString(R.string.pr_channel_wifi_required))
    }
    if (!globalLanJoined) {
        return P2pOutboundDecision.Blocked(appString(R.string.pr_channel_join_required))
    }
    if (hasFiles && targetCount <= 0) {
        return P2pOutboundDecision.Blocked(appString(R.string.pr_channel_no_recipients))
    }

    return when (val sessionDecision = validateAuthenticatedSession(appString(R.string.pr_use_channel_action), context)) {
        is P2pOutboundDecision.Blocked -> sessionDecision
        is P2pOutboundDecision.Ready -> P2pOutboundDecision.Ready(
            P2pGlobalMessagePlan(
                message = message.takeIf { hasMessage },
                includeFiles = hasFiles,
                feedbackMessage = when {
                    hasMessage && hasFiles -> appQuantityString(
                        R.plurals.pr_channel_message_files, selectedFilesCount, selectedFilesCount
                    )
                    hasFiles -> appQuantityString(R.plurals.pr_channel_files, selectedFilesCount, selectedFilesCount)
                    targetCount == 0 -> appString(R.string.pr_channel_message_posted)
                    else -> appQuantityString(R.plurals.pr_channel_message_targets, targetCount, targetCount)
                }
            )
        )
    }
}

fun buildDirectChatComposerPlan(
    context: P2pOutboundContext,
    draft: String,
    selectedFilesCount: Int,
    targetIps: List<String>
): P2pOutboundDecision<P2pDirectChatComposerPlan> {
    val message = sanitizeOutgoingMessage(draft)
    val hasDraft = message.isNotBlank()
    val hasFiles = selectedFilesCount > 0
    if (!hasDraft && !hasFiles) {
        return P2pOutboundDecision.Blocked(appString(R.string.pr_message_or_file_required))
    }

    val actionLabel = when {
        hasDraft && hasFiles -> appString(R.string.pr_send_message_files_action)
        hasFiles -> appString(R.string.pr_send_files_action)
        else -> appString(R.string.pr_send_messages_action)
    }

    val normalizedTargets = targetIps
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    if (normalizedTargets.isEmpty()) {
        return P2pOutboundDecision.Blocked(appString(R.string.pr_target_missing_action, actionLabel))
    }

    normalizedTargets.forEach { targetIp ->
        when (val targetDecision = validateOutboundTarget(actionLabel, context, targetIp)) {
            is P2pOutboundDecision.Blocked -> return targetDecision
            is P2pOutboundDecision.Ready -> Unit
        }
    }

    return P2pOutboundDecision.Ready(
        P2pDirectChatComposerPlan(
            targetIps = normalizedTargets,
            message = message.takeIf { hasDraft },
            includeFiles = hasFiles,
            feedbackMessage = when {
                hasDraft && hasFiles && normalizedTargets.size > 1 ->
                    appQuantityString(R.plurals.pr_message_files_targets, normalizedTargets.size, normalizedTargets.size)
                hasDraft && hasFiles -> appString(R.string.pr_message_files_queued)
                hasDraft && normalizedTargets.size > 1 -> appQuantityString(R.plurals.pr_message_targets, normalizedTargets.size, normalizedTargets.size)
                hasDraft -> appString(R.string.pr_message_queued)
                normalizedTargets.size > 1 -> appQuantityString(R.plurals.pr_files_targets, normalizedTargets.size, normalizedTargets.size)
                else -> appString(R.string.pr_files_queued)
            }
        )
    )
}

private fun validateOutboundTarget(
    actionLabel: String,
    context: P2pOutboundContext,
    targetIp: String?
): P2pOutboundDecision<String> {
    if (!context.permissionGranted && !context.lanConnected) {
        return P2pOutboundDecision.Blocked(
            appString(R.string.pr_permissions_action, actionLabel)
        )
    }

    when (val sessionDecision = validateAuthenticatedSession(actionLabel, context)) {
        is P2pOutboundDecision.Blocked -> return sessionDecision
        is P2pOutboundDecision.Ready -> Unit
    }

    val p2pLinked = context.connection?.groupFormed == true
    if (!p2pLinked && !context.lanConnected) {
        return P2pOutboundDecision.Blocked(
            appString(R.string.pr_connect_action, actionLabel)
        )
    }

    val normalizedTarget = targetIp?.trim().takeUnless { it.isNullOrBlank() }
        ?: return P2pOutboundDecision.Blocked(
            appString(R.string.pr_target_missing_hint, actionLabel)
        )

    if (
        p2pLinked &&
        context.connection?.isGroupOwner == true &&
        normalizedTarget == context.connection.groupOwnerAddress
    ) {
        return P2pOutboundDecision.Blocked(
            appString(R.string.pr_invalid_self_target)
        )
    }

    return P2pOutboundDecision.Ready(normalizedTarget)
}

internal fun validateAuthenticatedSession(
    actionLabel: String,
    context: P2pOutboundContext
): P2pOutboundDecision<Unit> {
    if (!FileTransfer.isValidToken(context.sessionToken)) {
        return P2pOutboundDecision.Blocked(
            appString(R.string.pr_session_invalid)
        )
    }
    if (!TransferSecurity.isValidPin(context.sessionPin)) {
        return P2pOutboundDecision.Blocked(appString(R.string.pr_pin_six_digits))
    }
    if (context.sessionExpired) {
        return P2pOutboundDecision.Blocked(
            appString(R.string.pr_expired_action, actionLabel)
        )
    }

    return P2pOutboundDecision.Ready(Unit)
}

private fun sanitizeOutgoingMessage(raw: String): String {
    return raw.trim().replace(Regex("\\s+"), " ").take(2_000)
}
