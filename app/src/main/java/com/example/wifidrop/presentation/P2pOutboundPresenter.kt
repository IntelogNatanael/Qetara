package com.example.wifidrop.presentation

import com.example.wifidrop.ChatChannel
import com.example.wifidrop.ChatMessageScope
import com.example.wifidrop.ChatMessageScopeCodec
import com.example.wifidrop.ConnectionMode
import com.example.wifidrop.KnownPeerSnapshot
import com.example.wifidrop.backend.P2pBackend

data class P2pOutboundOrchestrationInput(
    val outboundContext: P2pOutboundContext,
    val sessionToken: String,
    val sessionPin: String,
    val deviceLabel: String,
    val chatDraft: String,
    val selectedFiles: List<P2pOutboundSelection>,
    val resolvedTargetIp: String?,
    val chatDirectTargetIp: String?,
    val chatDirectTargetIps: List<String>,
    val chatDirectTargets: List<P2pResolvedTarget>,
    val activeConnectionMode: ConnectionMode,
    val chatChannel: ChatChannel,
    val globalLanJoined: Boolean,
    val lastSendTargetIp: String?,
    val globalChatTargets: List<KnownPeerSnapshot>,
    val directChannelTargets: List<KnownPeerSnapshot>
)

data class P2pOutboundOrchestrationResult(
    val feedback: P2pFeedbackMessage? = null,
    val clearChatDraft: Boolean = false
)

class P2pOutboundPresenter(
    private val backend: P2pBackend,
    private val shareImportPresenter: P2pShareImportPresenter,
    private val localDeviceId: String
) {
    fun sendSelectedFiles(
        input: P2pOutboundOrchestrationInput,
        targetIpOverride: String? = null,
        announce: Boolean = true,
        clearSelectionAfterSend: Boolean = false
    ): P2pOutboundOrchestrationResult {
        val decision = buildFileBatchSendPlan(
            context = input.outboundContext,
            targetIp = targetIpOverride?.trim().takeUnless { it.isNullOrBlank() }
                ?: input.resolvedTargetIp,
            fileCount = input.selectedFiles.size
        )
        val plan = when (decision) {
            is P2pOutboundDecision.Blocked -> {
                return P2pOutboundOrchestrationResult(
                    feedback = P2pFeedbackMessage(decision.message, isError = true)
                )
            }
            is P2pOutboundDecision.Ready -> decision.plan
        }

        queueSelectedFiles(input, plan.targetIp)
        shareImportPresenter.markFilesQueued(
            status = plan.shareImportStatus,
            clearSelectionAfterSend = clearSelectionAfterSend
        )
        return P2pOutboundOrchestrationResult(
            feedback = plan.feedbackMessage
                .takeIf { announce }
                ?.let(::P2pFeedbackMessage)
        )
    }

    fun sendToLastTarget(input: P2pOutboundOrchestrationInput): P2pOutboundOrchestrationResult {
        val lastIp = input.lastSendTargetIp?.trim().takeUnless { it.isNullOrBlank() }
        if (lastIp == null) {
            shareImportPresenter.updateStatus("Aún no hay un último destino guardado.")
            return P2pOutboundOrchestrationResult()
        }
        return sendSelectedFiles(
            input = input,
            targetIpOverride = lastIp
        )
    }

    fun sendGlobalMessage(input: P2pOutboundOrchestrationInput): P2pOutboundOrchestrationResult {
        val decision = buildGlobalMessageSendPlan(
            context = input.outboundContext,
            draft = input.chatDraft,
            selectedFilesCount = input.selectedFiles.size,
            globalLanJoined = input.globalLanJoined,
            targetCount = input.globalChatTargets.size
        )
        val plan = when (decision) {
            is P2pOutboundDecision.Blocked -> {
                return P2pOutboundOrchestrationResult(
                    feedback = P2pFeedbackMessage(decision.message, isError = true)
                )
            }
            is P2pOutboundDecision.Ready -> decision.plan
        }

        plan.message?.let { message ->
            backend.sendBroadcastMessage(
                token = input.sessionToken,
                pin = input.sessionPin,
                deviceLabel = input.deviceLabel,
                message = message,
                scope = ChatMessageScope.GLOBAL_LAN,
                channelLabel = "Canal Wi‑Fi",
                targetPeerIds = input.globalChatTargets.map { it.id },
                targetIps = input.globalChatTargets.map { it.ip },
                targetLabels = input.globalChatTargets.map { it.label.ifBlank { it.ip } }
            )
        }
        if (plan.includeFiles) {
            input.selectedFiles.forEach { item ->
                val offer = backend.registerChannelFileOffer(
                    fileUri = item.uri,
                    fileName = item.name,
                    deviceLabel = input.deviceLabel
                )
                backend.sendBroadcastMessage(
                    token = input.sessionToken,
                    pin = input.sessionPin,
                    deviceLabel = input.deviceLabel,
                    message = ChatMessageScopeCodec.encodeChannelFileOffer(offer),
                    scope = ChatMessageScope.GLOBAL_LAN,
                    channelLabel = "Canal Wi‑Fi",
                    targetPeerIds = input.globalChatTargets.map { it.id },
                    targetIps = input.globalChatTargets.map { it.ip },
                    targetLabels = input.globalChatTargets.map { it.label.ifBlank { it.ip } }
                )
            }
            shareImportPresenter.markChannelComposerFilesQueued(
                input.selectedFiles.size
            )
        }
        return P2pOutboundOrchestrationResult(
            feedback = P2pFeedbackMessage(plan.feedbackMessage),
            clearChatDraft = plan.message != null
        )
    }

    fun sendChatComposerPayload(
        input: P2pOutboundOrchestrationInput,
        channel: ChatChannel
    ): P2pOutboundOrchestrationResult {
        return when (channel) {
            ChatChannel.GLOBAL -> sendGlobalMessage(input)

            ChatChannel.DIRECT -> sendDirectChatPayload(input)
        }
    }

    fun retryMessage(messageId: String, deviceLabel: String): P2pFeedbackMessage {
        backend.retryMessage(messageId, deviceLabel)
        return P2pFeedbackMessage("Reintento programado.")
    }

    fun cancelQueuedMessage(messageId: String): P2pFeedbackMessage {
        backend.cancelPendingMessage(messageId)
        return P2pFeedbackMessage("Cancelacion solicitada para el mensaje.")
    }

    private fun sendDirectChatPayload(
        input: P2pOutboundOrchestrationInput
    ): P2pOutboundOrchestrationResult {
        val decision = buildDirectChatComposerPlan(
            context = input.outboundContext,
            draft = input.chatDraft,
            selectedFilesCount = input.selectedFiles.size,
            targetIps = input.chatDirectTargetIps.ifEmpty {
                listOfNotNull(input.chatDirectTargetIp)
            }
        )
        val plan = when (decision) {
            is P2pOutboundDecision.Blocked -> {
                return P2pOutboundOrchestrationResult(
                    feedback = P2pFeedbackMessage(decision.message, isError = true)
                )
            }
            is P2pOutboundDecision.Ready -> decision.plan
        }

        val connection = input.outboundContext.connection
        val isWifiDirectOwner = input.activeConnectionMode == ConnectionMode.WIFI_DIRECT &&
            connection?.groupFormed == true &&
            connection.isGroupOwner
        val ownerIp = connection?.groupOwnerAddress?.trim().takeUnless { it.isNullOrBlank() }
        val selectedTargets = input.chatDirectTargets.associateBy { it.ip }

        if (
            input.activeConnectionMode == ConnectionMode.WIFI_DIRECT &&
            plan.includeFiles &&
            !canSendDirectWifiFiles(
                isGroupOwner = isWifiDirectOwner,
                ownerIp = ownerIp,
                targetIps = plan.targetIps
            )
        ) {
            return P2pOutboundOrchestrationResult(
                feedback = P2pFeedbackMessage(
                    message = if (plan.targetIps.size > 1) {
                        "Por ahora los archivos por Wi‑Fi Direct salen a un solo equipo desde el anfitrión."
                    } else {
                        "Desde clientes, los archivos por Wi‑Fi Direct solo salen directo al anfitrión."
                    },
                    isError = true
                )
            )
        }

        plan.message?.let { message ->
            plan.targetIps.forEach { targetIp ->
                val resolvedTarget = selectedTargets[targetIp]
                val targetLabel = resolvedTarget?.label ?: targetIp
                val relayViaOwner = shouldRelayDirectMessageViaOwner(
                    activeConnectionMode = input.activeConnectionMode,
                    isGroupOwner = isWifiDirectOwner,
                    ownerIp = ownerIp,
                    targetIp = targetIp
                )

                if (relayViaOwner) {
                    backend.sendMessage(
                        targetIp = ownerIp.orEmpty(),
                        token = input.sessionToken,
                        pin = input.sessionPin,
                        deviceLabel = input.deviceLabel,
                        message = ChatMessageScopeCodec.encodeDirectRelayRequest(
                            textRaw = message,
                            senderId = localDeviceId,
                            senderLabel = input.deviceLabel,
                            targetPeerId = resolvedTarget?.peerId,
                            targetIp = targetIp,
                            targetLabel = targetLabel
                        ),
                        peerLabelOverride = targetLabel,
                        scope = ChatMessageScope.DIRECT
                    )
                } else {
                    backend.sendMessage(
                        targetIp = targetIp,
                        token = input.sessionToken,
                        pin = input.sessionPin,
                        deviceLabel = input.deviceLabel,
                        message = message,
                        peerLabelOverride = targetLabel,
                        scope = ChatMessageScope.DIRECT
                    )
                }
            }
        }

        if (plan.includeFiles) {
            plan.targetIps.forEach { targetIp ->
                queueSelectedFiles(input, targetIp)
            }
            shareImportPresenter.markDirectComposerFilesQueued(input.selectedFiles.size * plan.targetIps.size)
        }

        return P2pOutboundOrchestrationResult(
            feedback = P2pFeedbackMessage(plan.feedbackMessage),
            clearChatDraft = plan.message != null
        )
    }

    private fun shouldRelayDirectMessageViaOwner(
        activeConnectionMode: ConnectionMode,
        isGroupOwner: Boolean,
        ownerIp: String?,
        targetIp: String
    ): Boolean {
        return activeConnectionMode == ConnectionMode.WIFI_DIRECT &&
            !isGroupOwner &&
            !ownerIp.isNullOrBlank() &&
            targetIp != ownerIp
    }

    private fun canSendDirectWifiFiles(
        isGroupOwner: Boolean,
        ownerIp: String?,
        targetIps: List<String>
    ): Boolean {
        if (targetIps.isEmpty()) return false
        return if (isGroupOwner) {
            true
        } else {
            targetIps.size == 1 && !ownerIp.isNullOrBlank() && targetIps.first() == ownerIp
        }
    }

    private fun queueSelectedFiles(
        input: P2pOutboundOrchestrationInput,
        targetIp: String
    ) {
        input.selectedFiles.forEach { item ->
            backend.sendFile(
                fileUri = item.uri,
                fileName = item.name,
                targetIp = targetIp,
                token = input.sessionToken,
                pin = input.sessionPin,
                deviceLabel = input.deviceLabel
            )
        }
    }
}
