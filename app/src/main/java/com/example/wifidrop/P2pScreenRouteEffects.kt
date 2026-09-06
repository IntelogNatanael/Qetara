package com.example.wifidrop

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.wifidrop.presentation.BindP2pSessionRuntimeEffects
import com.example.wifidrop.presentation.P2pFeedbackMessage
import com.example.wifidrop.presentation.P2pResolvedTarget
import com.example.wifidrop.presentation.P2pSessionRuntimeBindingsInput
import com.example.wifidrop.presentation.P2pTransientUiEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

data class P2pScreenRouteEffectsInput(
    val activity: Activity?,
    val appContext: Context,
    val presenters: P2pRoutePresenters,
    val wifiState: WifiDirectState,
    val transferState: TransferRuntimeState,
    val incomingShare: IncomingSharePayload?,
    val hasPermission: Boolean,
    val lanConnected: Boolean,
    val sessionState: com.example.wifidrop.presentation.P2pSessionState,
    val sessionExpired: Boolean,
    val latestLanDeviceLabel: String,
    val latestTargetIpInput: String,
    val latestSessionDeviceLabel: String,
    val receiveDirPath: String,
    val nowMs: Long,
    val uxPreferences: UxPreferences,
    val shareImportStatus: String,
    val directChatAvailablePeers: List<KnownPeerSnapshot>,
    val resolvedTarget: P2pResolvedTarget?,
    val sessionNetworkKey: String,
    val targetIpInput: String,
    val wifiPermissionAsked: Boolean,
    val notificationPermissionAsked: Boolean
)

@Composable
fun BindP2pScreenRouteEffects(
    input: P2pScreenRouteEffectsInput,
    onNowTick: (Long) -> Unit,
    onSuggestedTarget: (String?) -> Unit,
    onApplyTransientUiEffect: (P2pTransientUiEffect?) -> Unit,
    onApplyWifiPermissionPlan: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onReceivedFilesChanged: () -> Unit,
    onShowFeedback: suspend (P2pFeedbackMessage) -> Unit,
    onConnectionHintTargetResolved: (String) -> Unit
) {
    DisposableEffect(input.presenters.backend, input.activity) {
        val receiver = input.presenters.backend.createWifiDirectBroadcastReceiver()
        ContextCompat.registerReceiver(
            input.appContext,
            receiver,
            WifiDirectBroadcastReceiver.buildIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            try {
                input.appContext.unregisterReceiver(receiver)
            } catch (_: Exception) {
                // ignored
            }

            input.presenters.lanDiscoveryPresenter.cancelScan()
            if (input.activity?.isFinishing == true) {
                input.presenters.backend.disconnectAll()
            }
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            onNowTick(System.currentTimeMillis())
            delay(1_000)
        }
    }

    LaunchedEffect(
        input.uxPreferences.sessionEnabled,
        input.sessionState.token,
        input.sessionState.pin,
        input.sessionExpired,
        input.uxPreferences.lanModeEnabled
    ) {
        input.presenters.lanDiscoveryPresenter.runAutoRefreshLoop(
            autoScanEnabled = input.uxPreferences.sessionEnabled && input.uxPreferences.lanModeEnabled,
            sessionToken = input.sessionState.token,
            sessionPin = input.sessionState.pin,
            sessionExpired = input.sessionExpired,
            deviceLabelProvider = { input.latestLanDeviceLabel },
            onSuggestedTarget = onSuggestedTarget
        )
    }

    LaunchedEffect(
        input.uxPreferences.sessionEnabled,
        input.hasPermission,
        input.uxPreferences.wifiDirectModeEnabled,
        input.wifiState.p2pEnabled
    ) {
        if (
            input.uxPreferences.sessionEnabled &&
            input.hasPermission &&
            input.uxPreferences.wifiDirectModeEnabled &&
            input.wifiState.p2pEnabled
        ) {
            input.presenters.backend.refreshWifiDirectState()
            input.presenters.backend.discoverPeers()
        }
    }

    LaunchedEffect(input.wifiState.connection?.groupFormed, input.lanConnected) {
        val connectedNow = input.wifiState.connection?.groupFormed == true || input.lanConnected
        onApplyTransientUiEffect(input.presenters.uiEffectsPresenter.onConnectionChanged(connectedNow))
    }

    LaunchedEffect(input.hasPermission, input.lanConnected) {
        onApplyWifiPermissionPlan()
    }

    LaunchedEffect(input.transferState.sending, input.transferState.receiving) {
        val activityVisible = (input.activity as? LifecycleOwner)?.lifecycle?.currentState
            ?.isAtLeast(Lifecycle.State.RESUMED) == true
        if (!activityVisible) return@LaunchedEffect
        val notificationPermission = postNotificationsPermission()
        val hasNotificationPermission = notificationPermission == null ||
            ContextCompat.checkSelfPermission(
                input.appContext,
                notificationPermission
            ) == PackageManager.PERMISSION_GRANTED

        if (
            com.example.wifidrop.presentation.shouldRequestNotificationPermission(
                transferInProgress = input.transferState.sending || input.transferState.receiving,
                sdkInt = Build.VERSION.SDK_INT,
                notificationPermissionGranted = hasNotificationPermission,
                permissionAlreadyRequested = input.notificationPermissionAsked
            )
        ) {
            onRequestNotificationPermission()
        }
    }

    LaunchedEffect(input.transferState.lastReceivedPath) {
        if (!input.transferState.lastReceivedPath.isNullOrBlank()) {
            onReceivedFilesChanged()
        }
    }

    LaunchedEffect(input.incomingShare?.eventId) {
        input.presenters.shareImportPresenter.consumeIncomingShare(input.incomingShare)
    }

    LaunchedEffect(input.sessionState.syncStatus) {
        val feedback = input.presenters.uiEffectsPresenter
            .relaySyncStatus(input.sessionState.syncStatus)
        if (feedback != null) {
            onShowFeedback(feedback)
        }
    }

    LaunchedEffect(input.shareImportStatus) {
        val feedback = input.presenters.uiEffectsPresenter
            .relayShareStatus(input.shareImportStatus)
        if (feedback != null) {
            onShowFeedback(feedback)
        }
    }

    LaunchedEffect(input.transferState.messageStatus) {
        val feedback = input.presenters.uiEffectsPresenter
            .relayMessageStatus(input.transferState.messageStatus)
        if (feedback != null) {
            onShowFeedback(feedback)
        }
    }

    LaunchedEffect(
        input.uxPreferences.sessionEnabled,
        input.wifiState.connection?.groupFormed,
        input.wifiState.connection?.isGroupOwner,
        input.sessionState.token,
        input.sessionState.pin,
        input.sessionExpired,
        input.directChatAvailablePeers.joinToString("|") { "${it.id}:${it.ip}:${it.label}" }
    ) {
        if (
            input.uxPreferences.sessionEnabled &&
            input.wifiState.connection?.groupFormed == true &&
            input.wifiState.connection.isGroupOwner &&
            !input.sessionExpired &&
            FileTransfer.isValidToken(input.sessionState.token) &&
            TransferSecurity.isValidPin(input.sessionState.pin)
        ) {
            val peers = input.directChatAvailablePeers
                .filter { it.id.isNotBlank() && it.ip.isNotBlank() }
                .map { peer ->
                    ChatTransportPeer(
                        id = peer.id,
                        label = peer.label.ifBlank { peer.ip },
                        ip = peer.ip
                    )
                }
            if (peers.isNotEmpty()) {
                val rosterPayload = ChatMessageScopeCodec.encodeDirectRoster(peers)
                peers.forEach { peer ->
                    input.presenters.backend.sendSilentMessage(
                        targetIp = peer.ip,
                        token = input.sessionState.token,
                        pin = input.sessionState.pin,
                        deviceLabel = input.latestSessionDeviceLabel,
                        message = rosterPayload,
                        peerLabelOverride = peer.label,
                        scope = ChatMessageScope.DIRECT
                    )
                }
            }
        }
    }

    BindP2pSessionRuntimeEffects(
        sessionPresenter = input.presenters.sessionPresenter,
        input = P2pSessionRuntimeBindingsInput(
            activeConnectionMode = input.resolvedTarget?.mode ?: input.uxPreferences.activeConnectionMode,
            sessionEnabled = input.uxPreferences.sessionEnabled,
            serviceRunning = input.transferState.serviceRunning,
            permissionGranted = input.hasPermission,
            lanConnected = input.lanConnected,
            connection = input.wifiState.connection,
            sessionToken = input.sessionState.token,
            sessionPin = input.sessionState.pin,
            sessionExpiresAtMs = input.sessionState.expiresAtMs,
            sessionExpired = input.sessionExpired,
            lastAutoSyncedPeerIp = input.sessionState.lastAutoSyncedPeerIp,
            syncing = input.sessionState.syncing,
            receiveDirPath = input.receiveDirPath,
            resolvedTarget = input.resolvedTarget,
            networkKey = input.sessionNetworkKey,
            deviceLabel = input.latestSessionDeviceLabel,
            nowMs = input.nowMs
        )
    )

    LaunchedEffect(
        input.wifiState.connection?.groupFormed,
        input.wifiState.connection?.isGroupOwner,
        input.wifiState.connection?.groupOwnerAddress,
        input.directChatAvailablePeers,
        input.transferState.trustedPeers
    ) {
        input.presenters.connectionHintsPresenter.onConnectionStateChanged(
            connection = input.wifiState.connection,
            knownPeers = input.directChatAvailablePeers,
            trustedPeers = input.transferState.trustedPeers,
            currentTargetIp = input.targetIpInput
        )?.let(onConnectionHintTargetResolved)
    }
}
