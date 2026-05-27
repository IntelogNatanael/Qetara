package com.example.wifidrop.presentation

import com.example.wifidrop.ChatChannel
import com.example.wifidrop.ConnectionMode
import com.example.wifidrop.ConnectionSnapshot
import com.example.wifidrop.KnownPeerSnapshot
import com.example.wifidrop.P2pScreenState
import com.example.wifidrop.TransferRuntimeState
import com.example.wifidrop.UxPreferences
import com.example.wifidrop.WifiDirectState
import java.io.File

data class P2pResolvedTarget(
    val peerId: String? = null,
    val ip: String,
    val label: String?,
    val mode: ConnectionMode
)

data class P2pScreenTargetsState(
    val suggestedTargetIp: String?,
    val resolvedTarget: P2pResolvedTarget?,
    val directTarget: P2pResolvedTarget?,
    val chatDirectTarget: P2pResolvedTarget?,
    val chatDirectTargets: List<P2pResolvedTarget> = emptyList(),
    val chatDirectAvailablePeers: List<KnownPeerSnapshot> = emptyList(),
    val globalChatPeerCount: Int,
    val directChannelPeerCount: Int = 0
)

data class P2pScreenSessionState(
    val authToken: String,
    val sessionPin: String,
    val sessionExpiresAtMs: Long,
    val nowMs: Long,
    val sessionExpired: Boolean,
    val localDeviceIdShort: String,
    val tokenSyncStatus: String
)

data class P2pScreenDraftState(
    val targetIp: String,
    val selectedFileNames: List<String>,
    val receivedFiles: List<File>,
    val shareImportStatus: String,
    val chatDraft: String
)

data class P2pFavoriteSuggestionState(
    val peerId: String?,
    val label: String?
)

data class P2pScreenStateInput(
    val permissionGranted: Boolean,
    val permissionName: String,
    val wifiState: WifiDirectState,
    val transferState: TransferRuntimeState,
    val lanConnected: Boolean,
    val lanLocalIp: String?,
    val lanScanStatus: String,
    val lanScanning: Boolean,
    val session: P2pScreenSessionState,
    val draft: P2pScreenDraftState,
    val chatChannel: ChatChannel,
    val globalLanJoined: Boolean,
    val favoriteSuggestion: P2pFavoriteSuggestionState,
    val uxPreferences: UxPreferences,
    val targets: P2pScreenTargetsState
)

fun buildP2pScreenState(input: P2pScreenStateInput): P2pScreenState {
    val directTargetReady = !input.targets.directTarget?.ip.isNullOrBlank()

    return P2pScreenState(
        permissionGranted = input.permissionGranted,
        permissionName = input.permissionName,
        p2pEnabled = input.wifiState.p2pEnabled,
        lanConnected = input.lanConnected,
        lanLocalIp = input.lanLocalIp,
        lanScanStatus = input.lanScanStatus,
        lanScanning = input.lanScanning,
        statusMessage = input.wifiState.statusMessage,
        quickHint = buildQuickHint(
            permissionGranted = input.permissionGranted,
            p2pEnabled = input.wifiState.p2pEnabled,
            lanConnected = input.lanConnected,
            connection = input.wifiState.connection,
            directPeerReady = directTargetReady,
            receiving = input.transferState.receiving,
            sending = input.transferState.sending,
            paused = input.transferState.paused,
            sessionExpired = input.session.sessionExpired
        ),
        thisDeviceName = input.wifiState.thisDeviceName,
        thisDeviceAddress = input.wifiState.thisDeviceAddress,
        peers = input.wifiState.peers,
        connection = input.wifiState.connection,
        directDiscovering = input.wifiState.discoveringPeers,
        directConnecting = input.wifiState.connectingToPeer,
        directCreatingGroup = input.wifiState.creatingGroup,
        authToken = input.session.authToken,
        sessionPin = input.session.sessionPin,
        sessionExpiresAtMs = input.session.sessionExpiresAtMs,
        nowMs = input.session.nowMs,
        sessionExpired = input.session.sessionExpired,
        localDeviceIdShort = input.session.localDeviceIdShort,
        tokenSyncStatus = input.session.tokenSyncStatus,
        targetIp = input.draft.targetIp,
        suggestedTargetIp = input.targets.suggestedTargetIp,
        resolvedTargetIp = input.targets.resolvedTarget?.ip,
        resolvedTargetLabel = input.targets.resolvedTarget?.label,
        resolvedTargetMode = input.targets.resolvedTarget?.mode,
        directTargetIp = input.targets.directTarget?.ip,
        directTargetLabel = input.targets.directTarget?.label,
        chatDirectTargetIp = input.targets.chatDirectTarget?.ip,
        chatDirectTargetIps = input.targets.chatDirectTargets.map { it.ip },
        chatDirectTargetLabel = input.targets.chatDirectTarget?.label,
        chatDirectTargetMode = input.targets.chatDirectTarget?.mode,
        chatDirectAvailablePeers = input.targets.chatDirectAvailablePeers,
        lastPeerLabel = input.transferState.lastPeerLabel,
        knownServicePeers = input.transferState.knownPeers,
        pendingTrust = input.transferState.pendingTrust,
        pendingCredentialShare = input.transferState.pendingCredentialShare,
        trustedPeers = input.transferState.trustedPeers,
        favoritePeers = input.transferState.favoritePeers,
        favoriteSuggestionPeerId = input.favoriteSuggestion.peerId,
        favoriteSuggestionLabel = input.favoriteSuggestion.label,
        lastSendTargetIp = input.transferState.lastSendTargetIp,
        lastSendTargetLabel = input.transferState.lastSendTargetLabel,
        receiving = input.transferState.receiving,
        receiverStatus = input.transferState.receiverStatus,
        receiverProgress = input.transferState.receiverProgress,
        receiverFileName = input.transferState.receiverFileName,
        receiverInstantBps = input.transferState.receiverInstantBps,
        receiverAverageBps = input.transferState.receiverAverageBps,
        receiverEtaSeconds = input.transferState.receiverEtaSeconds,
        receiverFailureCause = input.transferState.receiverFailureCause,
        receivedFiles = input.draft.receivedFiles,
        selectedFileNames = input.draft.selectedFileNames,
        selectedFilesCount = input.draft.selectedFileNames.size,
        sending = input.transferState.sending,
        sendActiveCount = input.transferState.sendActiveCount,
        sendBatchTotal = input.transferState.sendBatchTotal,
        sendBatchCompleted = input.transferState.sendBatchCompleted,
        sendBatchFailed = input.transferState.sendBatchFailed,
        sendBatchCanceled = input.transferState.sendBatchCanceled,
        sendQueue = input.transferState.sendQueue,
        sendProgress = input.transferState.sendProgress,
        sendStatus = input.transferState.sendStatus,
        sendInstantBps = input.transferState.sendInstantBps,
        sendAverageBps = input.transferState.sendAverageBps,
        sendEtaSeconds = input.transferState.sendEtaSeconds,
        sendFailureCause = input.transferState.sendFailureCause,
        shareImportStatus = input.draft.shareImportStatus,
        paused = input.transferState.paused,
        history = input.transferState.history,
        chatChannel = input.chatChannel,
        globalLanJoined = input.globalLanJoined,
        globalChatPeerCount = input.targets.globalChatPeerCount,
        directChannelPeerCount = input.targets.directChannelPeerCount,
        chatDraft = input.draft.chatDraft,
        chatMessages = input.transferState.chatMessages,
        messageStatus = input.transferState.messageStatus,
        activeConnectionMode = input.uxPreferences.activeConnectionMode,
        connectionViewMode = input.uxPreferences.connectionViewMode,
        wifiDirectModeEnabled = input.uxPreferences.wifiDirectModeEnabled,
        lanModeEnabled = input.uxPreferences.lanModeEnabled,
        fontScale = input.uxPreferences.fontScale,
        compactMode = input.uxPreferences.compactMode,
        vibrateOnConnect = input.uxPreferences.vibrateOnConnect,
        vibrateOnError = input.uxPreferences.vibrateOnError,
        silentSuccessFeedback = input.uxPreferences.silentSuccessFeedback
    )
}

private fun buildQuickHint(
    permissionGranted: Boolean,
    p2pEnabled: Boolean,
    lanConnected: Boolean,
    connection: ConnectionSnapshot?,
    directPeerReady: Boolean,
    receiving: Boolean,
    sending: Boolean,
    paused: Boolean,
    sessionExpired: Boolean
): String {
    if (!permissionGranted && !lanConnected) return "Concede permisos para empezar."
    if (sessionExpired) return "Renueva la sesión para seguir."
    if (paused) return "Transferencias pausadas. Usa Reanudar."

    if (connection?.groupFormed == true) {
        if (sending) return "Enviando en segundo plano."
        if (receiving) return "Recibiendo en segundo plano."
        return if (connection.isGroupOwner) {
            if (directPeerReady) {
                "Equipo directo listo. Ya puedes enviar o chatear."
            } else {
                "Enlace Wi-Fi Direct listo. Esperando al otro equipo."
            }
        } else {
            "Conexión directa lista. La sesión se sincroniza sola."
        }
    }

    if (lanConnected) {
        if (sending) return "Enviando por Wi-Fi normal."
        if (receiving) return "Recibiendo por Wi-Fi normal."
        return "Wi-Fi normal lista. Busca dispositivos y la sesión se sincronizará al dejar un equipo listo."
    }

    if (!p2pEnabled) return "Activa Wi-Fi o Wi-Fi Direct para detectar equipos."

    return "Rápido: 1) Un equipo crea el enlace. 2) El otro lo busca y conecta."
}
