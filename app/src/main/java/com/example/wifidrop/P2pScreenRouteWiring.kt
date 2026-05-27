package com.example.wifidrop

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.wifidrop.presentation.P2pFeedbackMessage
import com.example.wifidrop.presentation.P2pOutboundOrchestrationResult
import com.example.wifidrop.presentation.P2pUndoFeedbackPlan
import com.example.wifidrop.presentation.normalizeRequestedChatChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

typealias P2pUndoExecutor = suspend (
    P2pUndoFeedbackPlan,
    suspend () -> Unit,
    suspend () -> Unit
) -> Unit

data class P2pScreenEventWiringInput(
    val context: Context,
    val appContext: Context,
    val scope: CoroutineScope,
    val presenters: P2pRoutePresenters,
    val wifiState: WifiDirectState,
    val transferState: TransferRuntimeState,
    val uxPreferences: UxPreferences,
    val routeState: P2pScreenRouteDerivedState,
    val hasPermission: Boolean,
    val targetIpInput: String,
    val rawChatChannel: ChatChannel,
    val persistUxPreferences: (UxPreferences) -> Unit,
    val applyConnectionViewMode: (ConnectionViewMode) -> Unit,
    val setGlobalLanJoined: (Boolean) -> Unit,
    val setTargetIpInput: (String) -> Unit,
    val setChatDraft: (String) -> Unit,
    val setChatChannel: (ChatChannel) -> Unit,
    val setChatDirectLanTargetIp: (String?) -> Unit,
    val setChatDirectWifiTargetIps: (List<String>) -> Unit,
    val pushFeedback: (String, Boolean) -> Unit,
    val pushFeedbackMessage: (P2pFeedbackMessage?) -> Unit,
    val runUndoPlan: P2pUndoExecutor,
    val openWifiSettings: () -> Unit,
    val refreshReceivedFiles: () -> Unit,
    val handleOutboundResult: (P2pOutboundOrchestrationResult) -> Unit,
    val handleLanSuggestedTarget: (String?) -> Unit,
    val requestWifiPermissions: () -> Unit,
    val pickFiles: () -> Unit
)

data class P2pScreenEventWiring(
    val initialFocusStage: FocusStage,
    val onRequestPermission: () -> Unit,
    val onOpenWifiSettings: () -> Unit,
    val onFontScaleChange: (Float) -> Unit,
    val onCompactModeChange: (Boolean) -> Unit,
    val onVibrateOnConnectChange: (Boolean) -> Unit,
    val onVibrateOnErrorChange: (Boolean) -> Unit,
    val onSilentSuccessFeedbackChange: (Boolean) -> Unit,
    val onFocusStageChange: (FocusStage) -> Unit,
    val onConnectionViewModeChange: (ConnectionViewMode) -> Unit,
    val onConnectionModeChange: (ConnectionMode) -> Unit,
    val onWifiDirectModeEnabledChange: (Boolean) -> Unit,
    val onLanModeEnabledChange: (Boolean) -> Unit,
    val onStartHost: () -> Unit,
    val onStartClient: () -> Unit,
    val onScanLanPeers: () -> Unit,
    val onCancelLanScan: () -> Unit,
    val onDisconnect: () -> Unit,
    val onRefreshState: () -> Unit,
    val onCancelConnect: () -> Unit,
    val onConnectToPeer: (String) -> Unit,
    val onTokenChange: (String) -> Unit,
    val onPinChange: (String) -> Unit,
    val onGenerateToken: () -> Unit,
    val onGeneratePin: () -> Unit,
    val onRenewSession: () -> Unit,
    val onCopyToken: () -> Unit,
    val onPasteToken: () -> Unit,
    val onSyncToken: () -> Unit,
    val onSyncFromPeerIp: (String) -> Unit,
    val onTargetIpChange: (String) -> Unit,
    val onUseSuggestedTarget: () -> Unit,
    val onPickFile: () -> Unit,
    val onClearSelectedFiles: () -> Unit,
    val onSendFile: () -> Unit,
    val onRefreshReceived: () -> Unit,
    val onShareReceivedFile: (File) -> Unit,
    val onPauseTransfers: () -> Unit,
    val onResumeTransfers: () -> Unit,
    val onCancelActiveTransfer: () -> Unit,
    val onSendToLastTarget: () -> Unit,
    val onSetPeerFavorite: (String, Boolean) -> Unit,
    val onSetPeerAlias: (String, String) -> Unit,
    val onPauseQueueItem: (String) -> Unit,
    val onResumeQueueItem: (String) -> Unit,
    val onCancelQueueItem: (String) -> Unit,
    val onMoveQueueItemUp: (String) -> Unit,
    val onMoveQueueItemDown: (String) -> Unit,
    val onChatChannelChange: (ChatChannel) -> Unit,
    val onSetGlobalLanJoined: (Boolean) -> Unit,
    val onChatDraftChange: (String) -> Unit,
    val onSelectChatDirectPeer: (String) -> Unit,
    val onSendMessage: () -> Unit,
    val onRetryMessage: (String) -> Unit,
    val onCancelQueuedMessage: (String) -> Unit,
    val onDeleteMessage: (String) -> Unit,
    val onClearMessages: (ChatChannel) -> Unit,
    val onSaveSuggestedFavorite: (String) -> Unit,
    val onSkipSuggestedFavorite: (String) -> Unit,
    val onOpenDownloads: () -> Unit,
    val onTrustPeer: (PendingTrustRequest) -> Unit,
    val onApproveCredentialShare: (PendingCredentialShareRequest) -> Unit,
    val onRejectCredentialShare: (PendingCredentialShareRequest) -> Unit,
    val onOpenHistoryItem: (TransferHistoryEntry) -> Unit
)

fun buildP2pScreenEventWiring(
    input: P2pScreenEventWiringInput
): P2pScreenEventWiring {
    val backend = input.presenters.backend
    val lanDiscoveryPresenter = input.presenters.lanDiscoveryPresenter
    val sessionPresenter = input.presenters.sessionPresenter
    val peerActionsPresenter = input.presenters.peerActionsPresenter
    val connectionHintsPresenter = input.presenters.connectionHintsPresenter
    val shareImportPresenter = input.presenters.shareImportPresenter
    val trustPresenter = input.presenters.trustPresenter
    val outboundPresenter = input.presenters.outboundPresenter

    fun launchLanScanIfEnabled() {
        if (input.uxPreferences.lanModeEnabled) {
            lanDiscoveryPresenter.startScan(
                scope = input.scope,
                manual = true,
                deviceLabel = input.routeState.latestLanDeviceLabel,
                onSuggestedTarget = input.handleLanSuggestedTarget
            )
        }
    }

    return P2pScreenEventWiring(
        initialFocusStage = input.uxPreferences.lastFocusStage,
        onRequestPermission = input.requestWifiPermissions,
        onOpenWifiSettings = input.openWifiSettings,
        onFontScaleChange = { scale ->
            input.persistUxPreferences(input.uxPreferences.copy(fontScale = scale))
        },
        onCompactModeChange = { enabled ->
            input.persistUxPreferences(input.uxPreferences.copy(compactMode = enabled))
        },
        onVibrateOnConnectChange = { enabled ->
            input.persistUxPreferences(input.uxPreferences.copy(vibrateOnConnect = enabled))
        },
        onVibrateOnErrorChange = { enabled ->
            input.persistUxPreferences(input.uxPreferences.copy(vibrateOnError = enabled))
        },
        onSilentSuccessFeedbackChange = { enabled ->
            input.persistUxPreferences(input.uxPreferences.copy(silentSuccessFeedback = enabled))
        },
        onFocusStageChange = { stage ->
            input.persistUxPreferences(input.uxPreferences.copy(lastFocusStage = stage))
        },
        onConnectionViewModeChange = input.applyConnectionViewMode,
        onConnectionModeChange = { mode ->
            input.persistUxPreferences(input.uxPreferences.copy(activeConnectionMode = mode))
            input.setChatChannel(normalizeRequestedChatChannel(mode, input.rawChatChannel))
            if (mode == ConnectionMode.LAN) {
                input.setChatDirectWifiTargetIps(emptyList())
            } else {
                input.setChatDirectLanTargetIp(null)
            }
        },
        onWifiDirectModeEnabledChange = { enabled ->
            input.persistUxPreferences(input.uxPreferences.copy(wifiDirectModeEnabled = enabled))
        },
        onLanModeEnabledChange = { enabled ->
            input.persistUxPreferences(input.uxPreferences.copy(lanModeEnabled = enabled))
        },
        onStartHost = {
            if (input.hasPermission) {
                backend.createGroup()
            } else {
                input.requestWifiPermissions()
            }
        },
        onStartClient = {
            if (input.uxPreferences.wifiDirectModeEnabled && input.hasPermission) {
                backend.discoverPeers()
            } else if (input.uxPreferences.wifiDirectModeEnabled) {
                input.requestWifiPermissions()
            }
            if (
                input.uxPreferences.connectionViewMode == ConnectionViewMode.ADVANCED &&
                input.uxPreferences.lanModeEnabled
            ) {
                launchLanScanIfEnabled()
            }
        },
        onScanLanPeers = ::launchLanScanIfEnabled,
        onCancelLanScan = { lanDiscoveryPresenter.cancelScan() },
        onDisconnect = {
            backend.disconnectAll()
            input.pushFeedback("Conexion finalizada.", false)
        },
        onRefreshState = {
            backend.refreshWifiDirectState()
            if (
                input.uxPreferences.lanModeEnabled &&
                (
                    input.uxPreferences.activeConnectionMode == ConnectionMode.LAN ||
                        input.uxPreferences.connectionViewMode == ConnectionViewMode.ADVANCED
                    )
            ) {
                launchLanScanIfEnabled()
            }
        },
        onCancelConnect = { backend.cancelDirectAttempt() },
        onConnectToPeer = { address ->
            sessionPresenter.markConnectingForSync()
            if (input.hasPermission) {
                backend.connect(address)
                input.pushFeedback("Conectando con ${address.take(8)}...", false)
            } else {
                input.requestWifiPermissions()
            }
        },
        onTokenChange = { sessionPresenter.normalizeAndSetToken(it) },
        onPinChange = { sessionPresenter.normalizeAndSetPin(it) },
        onGenerateToken = { sessionPresenter.generateToken() },
        onGeneratePin = { sessionPresenter.generatePin() },
        onRenewSession = { sessionPresenter.renewSession(minutes = 30) },
        onCopyToken = {
            val feedback = sessionPresenter.copyToken()
            input.pushFeedback(feedback.message, feedback.isError)
        },
        onPasteToken = {
            val feedback = sessionPresenter.pasteToken()
            input.pushFeedback(feedback.message, feedback.isError)
        },
        onSyncToken = {
            val connection = input.wifiState.connection
            val hostIp = sessionPresenter.resolveManualSyncHost(
                connection = connection,
                resolvedTargetIp = input.routeState.routing.targets.resolvedTarget?.ip,
                targetIpInput = input.targetIpInput
            )
            if (connection?.groupFormed == true && connection.isGroupOwner) {
                sessionPresenter.markHostSharesSession()
            } else if (hostIp.isNullOrBlank()) {
                sessionPresenter.markManualSyncHint()
            } else {
                input.scope.launch {
                    sessionPresenter.syncTokenFromHost(
                        hostIp = hostIp,
                        manual = true,
                        deviceLabel = input.routeState.latestSessionDeviceLabel
                    )
                }
            }
        },
        onSyncFromPeerIp = { peerIp ->
            val normalized = peerIp.trim()
            if (normalized.isBlank()) {
                sessionPresenter.markInvalidPeerIp()
            } else {
                input.setTargetIpInput(normalized)
                input.scope.launch {
                    sessionPresenter.syncTokenFromHost(
                        hostIp = normalized,
                        manual = true,
                        deviceLabel = input.routeState.latestSessionDeviceLabel
                    )
                }
            }
        },
        onTargetIpChange = { input.setTargetIpInput(it.trim()) },
        onUseSuggestedTarget = {
            connectionHintsPresenter.useSuggestedTarget(
                input.routeState.routing.targets.suggestedTargetIp
            )?.let(input.setTargetIpInput)
        },
        onPickFile = input.pickFiles,
        onClearSelectedFiles = { shareImportPresenter.clearSelectedFiles() },
        onSendFile = {
            input.handleOutboundResult(
                outboundPresenter.sendSelectedFiles(input.routeState.outboundInput)
            )
        },
        onRefreshReceived = input.refreshReceivedFiles,
        onShareReceivedFile = { file -> FileShareUtils.shareFile(input.context, file) },
        onPauseTransfers = { backend.pauseTransfers() },
        onResumeTransfers = { backend.resumeTransfers() },
        onCancelActiveTransfer = { backend.cancelActiveTransfer() },
        onSendToLastTarget = {
            input.handleOutboundResult(
                outboundPresenter.sendToLastTarget(input.routeState.outboundInput)
            )
        },
        onSetPeerFavorite = { peerId, favorite ->
            val result = peerActionsPresenter.updatePeerFavorite(peerId, favorite)
            input.pushFeedbackMessage(result.feedback)
            if (result.undoPlan != null) {
                input.scope.launch {
                    input.runUndoPlan(result.undoPlan, {}, { result.onUndo?.invoke() })
                }
            }
        },
        onSetPeerAlias = { peerId, alias ->
            input.pushFeedbackMessage(
                peerActionsPresenter.updatePeerAlias(
                    peerId = peerId,
                    aliasRaw = alias,
                    trustedPeers = input.transferState.trustedPeers
                )
            )
        },
        onPauseQueueItem = { transferId -> backend.pauseQueueItem(transferId) },
        onResumeQueueItem = { transferId -> backend.resumeQueueItem(transferId) },
        onCancelQueueItem = { transferId -> backend.cancelQueueItem(transferId) },
        onMoveQueueItemUp = { transferId -> backend.moveQueueItemUp(transferId) },
        onMoveQueueItemDown = { transferId -> backend.moveQueueItemDown(transferId) },
        onChatChannelChange = { nextChannel ->
            val normalizedChannel = normalizeRequestedChatChannel(
                input.uxPreferences.activeConnectionMode,
                nextChannel
            )
            input.setChatChannel(normalizedChannel)
        },
        onSetGlobalLanJoined = { enabled ->
            input.setGlobalLanJoined(enabled)
            if (enabled && input.routeState.lanConnected) {
                launchLanScanIfEnabled()
            }
            if (enabled) {
                input.pushFeedback("Entraste al canal Wi‑Fi.", false)
            } else {
                input.pushFeedback("Saliste del canal Wi‑Fi.", false)
            }
        },
        onChatDraftChange = input.setChatDraft,
        onSelectChatDirectPeer = { ip ->
            if (input.uxPreferences.activeConnectionMode == ConnectionMode.WIFI_DIRECT) {
                val normalized = ip.trim()
                if (normalized.isNotBlank()) {
                    val nextTargets = if (input.routeState.effectiveChatDirectWifiTargetIps.contains(normalized)) {
                        input.routeState.effectiveChatDirectWifiTargetIps - normalized
                    } else {
                        (input.routeState.effectiveChatDirectWifiTargetIps + normalized).distinct()
                    }
                    input.setChatDirectWifiTargetIps(nextTargets)
                    if (nextTargets.size == 1) {
                        input.setTargetIpInput(nextTargets.first())
                    }
                }
            } else {
                val normalized = connectionHintsPresenter.onPeerTargetSelected(ip)
                if (normalized == null) {
                    input.setChatDirectLanTargetIp(null)
                } else {
                    input.setChatDirectLanTargetIp(normalized)
                    input.setTargetIpInput(normalized)
                }
            }
        },
        onSendMessage = {
            input.handleOutboundResult(
                outboundPresenter.sendChatComposerPayload(
                    input = input.routeState.outboundInput,
                    channel = input.routeState.effectiveChatChannel
                )
            )
        },
        onRetryMessage = { messageId ->
            input.pushFeedbackMessage(
                outboundPresenter.retryMessage(messageId, input.routeState.localDeviceLabel)
            )
        },
        onCancelQueuedMessage = { messageId ->
            input.pushFeedbackMessage(outboundPresenter.cancelQueuedMessage(messageId))
        },
        onDeleteMessage = { messageId ->
            input.scope.launch {
                input.runUndoPlan(
                    com.example.wifidrop.presentation.buildDeleteMessageUndoPlan(),
                    { backend.deleteChatMessage(messageId) },
                    {}
                )
            }
        },
        onClearMessages = { channel ->
            input.scope.launch {
                val messageScope = when (channel) {
                    ChatChannel.DIRECT -> ChatMessageScope.DIRECT
                    ChatChannel.GLOBAL -> ChatMessageScope.GLOBAL_LAN
                }
                input.runUndoPlan(
                    com.example.wifidrop.presentation.buildClearMessagesUndoPlan(channel),
                    { backend.clearChatMessages(messageScope) },
                    {}
                )
            }
        },
        onSaveSuggestedFavorite = { peerId ->
            input.pushFeedbackMessage(peerActionsPresenter.saveSuggestedFavorite(peerId))
        },
        onSkipSuggestedFavorite = { peerId ->
            input.pushFeedbackMessage(peerActionsPresenter.skipSuggestedFavorite(peerId))
        },
        onOpenDownloads = { backend.openDownloads() },
        onTrustPeer = { req -> input.pushFeedbackMessage(trustPresenter.trustPeer(req)) },
        onApproveCredentialShare = { req ->
            input.pushFeedbackMessage(trustPresenter.approveCredentialShare(req))
        },
        onRejectCredentialShare = { req ->
            input.pushFeedbackMessage(trustPresenter.rejectCredentialShare(req))
        },
        onOpenHistoryItem = { item -> ExternalOpenUtils.openRoute(input.appContext, item.route) }
    )
}

@Composable
fun RenderP2pScreen(
    state: P2pScreenState,
    wiring: P2pScreenEventWiring,
    modifier: Modifier = Modifier
) {
    P2pScreen(
        state = state,
        onRequestPermission = wiring.onRequestPermission,
        onOpenWifiSettings = wiring.onOpenWifiSettings,
        onFontScaleChange = wiring.onFontScaleChange,
        onCompactModeChange = wiring.onCompactModeChange,
        onVibrateOnConnectChange = wiring.onVibrateOnConnectChange,
        onVibrateOnErrorChange = wiring.onVibrateOnErrorChange,
        onSilentSuccessFeedbackChange = wiring.onSilentSuccessFeedbackChange,
        initialFocusStage = wiring.initialFocusStage,
        onFocusStageChange = wiring.onFocusStageChange,
        onConnectionViewModeChange = wiring.onConnectionViewModeChange,
        onConnectionModeChange = wiring.onConnectionModeChange,
        onWifiDirectModeEnabledChange = wiring.onWifiDirectModeEnabledChange,
        onLanModeEnabledChange = wiring.onLanModeEnabledChange,
        onStartHost = wiring.onStartHost,
        onStartClient = wiring.onStartClient,
        onScanLanPeers = wiring.onScanLanPeers,
        onCancelLanScan = wiring.onCancelLanScan,
        onDisconnect = wiring.onDisconnect,
        onRefreshState = wiring.onRefreshState,
        onCancelConnect = wiring.onCancelConnect,
        onConnectToPeer = wiring.onConnectToPeer,
        onTokenChange = wiring.onTokenChange,
        onPinChange = wiring.onPinChange,
        onGenerateToken = wiring.onGenerateToken,
        onGeneratePin = wiring.onGeneratePin,
        onRenewSession = wiring.onRenewSession,
        onCopyToken = wiring.onCopyToken,
        onPasteToken = wiring.onPasteToken,
        onSyncToken = wiring.onSyncToken,
        onSyncFromPeerIp = wiring.onSyncFromPeerIp,
        onTargetIpChange = wiring.onTargetIpChange,
        onUseSuggestedTarget = wiring.onUseSuggestedTarget,
        onPickFile = wiring.onPickFile,
        onClearSelectedFiles = wiring.onClearSelectedFiles,
        onSendFile = wiring.onSendFile,
        onRefreshReceived = wiring.onRefreshReceived,
        onShareReceivedFile = wiring.onShareReceivedFile,
        onPauseTransfers = wiring.onPauseTransfers,
        onResumeTransfers = wiring.onResumeTransfers,
        onCancelActiveTransfer = wiring.onCancelActiveTransfer,
        onSendToLastTarget = wiring.onSendToLastTarget,
        onSetPeerFavorite = wiring.onSetPeerFavorite,
        onSetPeerAlias = wiring.onSetPeerAlias,
        onPauseQueueItem = wiring.onPauseQueueItem,
        onResumeQueueItem = wiring.onResumeQueueItem,
        onCancelQueueItem = wiring.onCancelQueueItem,
        onMoveQueueItemUp = wiring.onMoveQueueItemUp,
        onMoveQueueItemDown = wiring.onMoveQueueItemDown,
        onChatChannelChange = wiring.onChatChannelChange,
        onSetGlobalLanJoined = wiring.onSetGlobalLanJoined,
        onChatDraftChange = wiring.onChatDraftChange,
        onSelectChatDirectPeer = wiring.onSelectChatDirectPeer,
        onSendMessage = wiring.onSendMessage,
        onRetryMessage = wiring.onRetryMessage,
        onCancelQueuedMessage = wiring.onCancelQueuedMessage,
        onDeleteMessage = wiring.onDeleteMessage,
        onClearMessages = wiring.onClearMessages,
        onSaveSuggestedFavorite = wiring.onSaveSuggestedFavorite,
        onSkipSuggestedFavorite = wiring.onSkipSuggestedFavorite,
        onOpenDownloads = wiring.onOpenDownloads,
        onTrustPeer = wiring.onTrustPeer,
        onApproveCredentialShare = wiring.onApproveCredentialShare,
        onRejectCredentialShare = wiring.onRejectCredentialShare,
        onOpenHistoryItem = wiring.onOpenHistoryItem,
        modifier = modifier
    )
}
