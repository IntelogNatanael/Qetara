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
    val receivedFiles: List<File>,
    val localDeviceId: String? = null
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
    val latestSessionDeviceLabel: String,
    val sessionNetworkKey: String
)

fun deriveP2pScreenRouteState(
    input: P2pScreenRouteDerivationInput
): P2pScreenRouteDerivedState {
    val localAddresses = com.example.wifidrop.presentation.localDiscoveryAddresses(
        input.transferState, input.localDeviceId,
        input.lanDiscoveryState.localIpv4Addresses + listOfNotNull(input.lanDiscoveryState.localIp)
    )
    val targetIp = input.targetIpInput.trim().takeUnless { it in localAddresses }.orEmpty()
    val transferState = com.example.wifidrop.presentation.withoutLocalDiscovery(
        input.transferState, input.localDeviceId,
        input.lanDiscoveryState.localIpv4Addresses + listOfNotNull(input.lanDiscoveryState.localIp)
    )
    val lanConnected = input.lanDiscoveryState.connected
    val selectedFiles = input.shareImportState.selectedFiles
    val shareImportStatus = input.shareImportState.shareImportStatus
    val latestLanDeviceLabel = input.wifiState.thisDeviceName.ifBlank { appString(R.string.pr_client) }
    val latestSessionDeviceLabel = latestLanDeviceLabel

    val verifiedDirectParticipants = resolveVerifiedDirectParticipants(input.wifiState.connection, transferState)
    val chatSelection = normalizeChatSelection(
        P2pChatSelectionInput(
            activeConnectionMode = input.uxPreferences.activeConnectionMode,
            lanConnected = lanConnected,
            wifiState = input.wifiState,
            knownPeers = transferState.knownPeers,
            directParticipants = verifiedDirectParticipants,
            manualLanTargetIp = targetIp.takeIf { input.uxPreferences.connectionViewMode != ConnectionViewMode.WIFI_DIRECT },
            chatChannel = input.chatChannel,
            chatDirectLanTargetIp = input.chatDirectLanTargetIp,
            chatDirectWifiTargetIps = input.chatDirectWifiTargetIps,
            lanLocalIp = input.lanDiscoveryState.localIp
        )
    )
    val effectiveChatChannel = chatSelection.chatChannel
    val effectiveChatDirectLanTargetIp = chatSelection.chatDirectLanTargetIp
    val effectiveChatDirectWifiTargetIps = chatSelection.chatDirectWifiTargetIps

    val routing = resolveP2pRouting(
        P2pRoutingInput(
            wifiState = input.wifiState,
            transferState = transferState,
            lanConnected = lanConnected,
            activeConnectionMode = input.uxPreferences.activeConnectionMode,
            connectionViewMode = input.uxPreferences.connectionViewMode,
            manualTargetIp = targetIp,
            chatDirectLanTargetIp = effectiveChatDirectLanTargetIp,
            chatDirectWifiTargetIps = effectiveChatDirectWifiTargetIps,
            lanLocalIp = input.lanDiscoveryState.localIp
        )
    )

    val effectiveConnectionMode = routing.targets.resolvedTarget?.mode ?: input.uxPreferences.activeConnectionMode
    val sessionNetworkKey = when (effectiveConnectionMode) {
        ConnectionMode.LAN -> "lan:" + lanConnected + ":" + input.lanDiscoveryState.localIp.orEmpty()
        ConnectionMode.WIFI_DIRECT -> "direct:" + input.wifiState.connection?.groupFormed + ":" +
            input.wifiState.connection?.groupOwnerAddress.orEmpty() + ":" + input.wifiState.connection?.isGroupOwner
    }
    val connectionTarget = routing.targets.resolvedTarget
    val currentCredentialShares = transferState.credentialSharedPeers.takeIf {
        transferState.activeToken == input.sessionState.token &&
            transferState.sessionExpiresAtMs == input.sessionState.expiresAtMs
    }.orEmpty()
    val sessionReady = input.uxPreferences.sessionEnabled && com.example.wifidrop.presentation.isSessionReadyForTarget(
        target = connectionTarget,
        token = input.sessionState.token,
        pin = input.sessionState.pin,
        sessionExpired = input.sessionExpired,
        networkKey = sessionNetworkKey,
        networkChangedAtMs = input.sessionState.readinessChangedAtMs,
        confirmation = input.sessionState.confirmation,
        sharedCredentials = currentCredentialShares
    )

    val chatSessionReady = input.uxPreferences.sessionEnabled && routing.targets.chatDirectTargets.isNotEmpty() && routing.targets.chatDirectTargets.all { target ->
        com.example.wifidrop.presentation.isSessionReadyForTarget(
            target = target,
            token = input.sessionState.token,
            pin = input.sessionState.pin,
            sessionExpired = input.sessionExpired,
            networkKey = sessionNetworkKey,
            networkChangedAtMs = input.sessionState.readinessChangedAtMs,
            confirmation = input.sessionState.confirmation,
            sharedCredentials = currentCredentialShares
        )
    }

    val localDeviceLabel = input.wifiState.thisDeviceName.ifBlank { appString(R.string.pr_device) }
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
        lastSendTargetIp = transferState.lastSendTargetIp,
        globalChatTargets = routing.globalChatTargets,
        directChannelTargets = routing.directChannelTargets
    )

    val uiState = buildP2pScreenState(
        P2pScreenStateInput(
            permissionGranted = input.hasPermission,
            permissionName = requiredPermissionHumanLabel(),
            wifiState = input.wifiState,
            transferState = transferState,
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
                tokenSyncStatus = input.sessionState.syncStatus,
                sessionReady = sessionReady,
                chatSessionReady = chatSessionReady,
                sessionSyncing = input.uxPreferences.sessionEnabled && input.sessionState.syncing
            ),
            draft = P2pScreenDraftState(
                targetIp = targetIp,
                selectedFileNames = selectedFiles.map { it.name },
                receivedFiles = input.receivedFiles,
                shareImportStatus = shareImportStatus,
                chatDraft = input.chatDraft,
                attachmentContext = input.shareImportState.attachmentContext,
                incomingShareEventId = input.shareImportState.incomingShareEventId
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
        latestSessionDeviceLabel = latestSessionDeviceLabel,
        sessionNetworkKey = sessionNetworkKey
    )
}
