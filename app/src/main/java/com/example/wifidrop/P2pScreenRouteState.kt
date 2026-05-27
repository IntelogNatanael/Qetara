package com.example.wifidrop

import com.example.wifidrop.presentation.P2pChatSelectionInput
import com.example.wifidrop.presentation.P2pFavoriteSuggestionState
import com.example.wifidrop.presentation.P2pLanDiscoveryState
import com.example.wifidrop.presentation.P2pOutboundContext
import com.example.wifidrop.presentation.P2pOutboundOrchestrationInput
import com.example.wifidrop.presentation.P2pRoutingInput
import com.example.wifidrop.presentation.P2pRoutingState
import com.example.wifidrop.presentation.P2pScreenDraftState
import com.example.wifidrop.presentation.P2pScreenSessionState
import com.example.wifidrop.presentation.P2pScreenStateInput
import com.example.wifidrop.presentation.P2pSessionState
import com.example.wifidrop.presentation.P2pShareImportState
import com.example.wifidrop.presentation.buildP2pScreenState
import com.example.wifidrop.presentation.normalizeChatSelection
import com.example.wifidrop.presentation.resolveP2pRouting
import java.io.File

data class P2pScreenRouteDerivationInput(
    val wifiState: WifiDirectState,
    val transferState: TransferRuntimeState,
    val lanDiscoveryState: P2pLanDiscoveryState,
    val sessionState: P2pSessionState,
    val shareImportState: P2pShareImportState,
    val favoriteSuggestion: P2pFavoriteSuggestionState,
    val hasPermission: Boolean,
    val sessionExpired: Boolean,
    val nowMs: Long,
    val targetIpInput: String,
    val chatDraft: String,
    val chatChannel: ChatChannel,
    val chatDirectLanTargetIp: String?,
    val chatDirectWifiTargetIps: List<String>,
    val uxPreferences: UxPreferences,
    val receivedFiles: List<File>
)

data class P2pScreenRouteDerivedState(
    val uiState: P2pScreenState,
    val lanConnected: Boolean,
    val lanLocalIp: String?,
    val lanScanStatus: String,
    val lanScanning: Boolean,
    val selectedFiles: List<com.example.wifidrop.presentation.P2pOutboundSelection>,
    val shareImportStatus: String,
    val effectiveChatChannel: ChatChannel,
    val effectiveChatDirectLanTargetIp: String?,
    val effectiveChatDirectWifiTargetIps: List<String>,
    val routing: P2pRoutingState,
    val outboundInput: P2pOutboundOrchestrationInput,
    val localDeviceLabel: String,
    val latestLanDeviceLabel: String,
    val latestSessionDeviceLabel: String
)

fun deriveP2pScreenRouteState(
    input: P2pScreenRouteDerivationInput
): P2pScreenRouteDerivedState {
    val lanConnected = input.lanDiscoveryState.connected
    val selectedFiles = input.shareImportState.selectedFiles
    val shareImportStatus = input.shareImportState.shareImportStatus
    val latestLanDeviceLabel = input.wifiState.thisDeviceName.ifBlank { "cliente" }
    val latestSessionDeviceLabel = latestLanDeviceLabel

    val chatSelection = normalizeChatSelection(
        P2pChatSelectionInput(
            activeConnectionMode = input.uxPreferences.activeConnectionMode,
            lanConnected = lanConnected,
            wifiState = input.wifiState,
            knownPeers = input.transferState.knownPeers,
            chatChannel = input.chatChannel,
            chatDirectLanTargetIp = input.chatDirectLanTargetIp,
            chatDirectWifiTargetIps = input.chatDirectWifiTargetIps
        )
    )
    val effectiveChatChannel = chatSelection.chatChannel
    val effectiveChatDirectLanTargetIp = chatSelection.chatDirectLanTargetIp
    val effectiveChatDirectWifiTargetIps = chatSelection.chatDirectWifiTargetIps

    val routing = resolveP2pRouting(
        P2pRoutingInput(
            wifiState = input.wifiState,
            transferState = input.transferState,
            lanConnected = lanConnected,
            activeConnectionMode = input.uxPreferences.activeConnectionMode,
            manualTargetIp = input.targetIpInput,
            chatDirectLanTargetIp = effectiveChatDirectLanTargetIp,
            chatDirectWifiTargetIps = effectiveChatDirectWifiTargetIps
        )
    )

    val localDeviceLabel = input.wifiState.thisDeviceName.ifBlank { "equipo" }
    val outboundInput = P2pOutboundOrchestrationInput(
        outboundContext = P2pOutboundContext(
            permissionGranted = input.hasPermission,
            lanConnected = lanConnected,
            sessionToken = input.sessionState.token,
            sessionPin = input.sessionState.pin,
            sessionExpired = input.sessionExpired,
            connection = input.wifiState.connection
        ),
        sessionToken = input.sessionState.token,
        sessionPin = input.sessionState.pin,
        deviceLabel = localDeviceLabel,
        chatDraft = input.chatDraft,
        selectedFiles = selectedFiles,
        resolvedTargetIp = routing.targets.resolvedTarget?.ip,
        chatDirectTargetIp = routing.targets.chatDirectTarget?.ip,
        chatDirectTargetIps = routing.targets.chatDirectTargets.map { it.ip },
        chatDirectTargets = routing.targets.chatDirectTargets,
        activeConnectionMode = input.uxPreferences.activeConnectionMode,
        chatChannel = effectiveChatChannel,
        globalLanJoined = input.uxPreferences.joinedGlobalLan,
        lastSendTargetIp = input.transferState.lastSendTargetIp,
        globalChatTargets = routing.globalChatTargets,
        directChannelTargets = routing.directChannelTargets
    )

    val uiState = buildP2pScreenState(
        P2pScreenStateInput(
            permissionGranted = input.hasPermission,
            permissionName = requiredPermissionHumanLabel(),
            wifiState = input.wifiState,
            transferState = input.transferState,
            lanConnected = lanConnected,
            lanLocalIp = input.lanDiscoveryState.localIp,
            lanScanStatus = input.lanDiscoveryState.scanStatus,
            lanScanning = input.lanDiscoveryState.scanning,
            session = P2pScreenSessionState(
                authToken = input.sessionState.token,
                sessionPin = input.sessionState.pin,
                sessionExpiresAtMs = input.sessionState.expiresAtMs,
                nowMs = input.nowMs,
                sessionExpired = input.sessionExpired,
                localDeviceIdShort = input.sessionState.localDeviceIdShort,
                tokenSyncStatus = input.sessionState.syncStatus
            ),
            draft = P2pScreenDraftState(
                targetIp = input.targetIpInput,
                selectedFileNames = selectedFiles.map { it.name },
                receivedFiles = input.receivedFiles,
                shareImportStatus = shareImportStatus,
                chatDraft = input.chatDraft
            ),
            chatChannel = effectiveChatChannel,
            globalLanJoined = input.uxPreferences.joinedGlobalLan,
            favoriteSuggestion = P2pFavoriteSuggestionState(
                peerId = input.favoriteSuggestion.peerId,
                label = input.favoriteSuggestion.label
            ),
            uxPreferences = input.uxPreferences,
            targets = routing.targets
        )
    )

    return P2pScreenRouteDerivedState(
        uiState = uiState,
        lanConnected = lanConnected,
        lanLocalIp = input.lanDiscoveryState.localIp,
        lanScanStatus = input.lanDiscoveryState.scanStatus,
        lanScanning = input.lanDiscoveryState.scanning,
        selectedFiles = selectedFiles,
        shareImportStatus = shareImportStatus,
        effectiveChatChannel = effectiveChatChannel,
        effectiveChatDirectLanTargetIp = effectiveChatDirectLanTargetIp,
        effectiveChatDirectWifiTargetIps = effectiveChatDirectWifiTargetIps,
        routing = routing,
        outboundInput = outboundInput,
        localDeviceLabel = localDeviceLabel,
        latestLanDeviceLabel = latestLanDeviceLabel,
        latestSessionDeviceLabel = latestSessionDeviceLabel
    )
}
