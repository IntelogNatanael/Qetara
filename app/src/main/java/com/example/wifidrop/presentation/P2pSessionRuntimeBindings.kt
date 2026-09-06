package com.example.wifidrop.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.wifidrop.ConnectionMode
import com.example.wifidrop.ConnectionSnapshot

data class P2pSessionRuntimeBindingsInput(
    val activeConnectionMode: ConnectionMode,
    val permissionGranted: Boolean,
    val lanConnected: Boolean,
    val connection: ConnectionSnapshot?,
    val sessionToken: String,
    val sessionPin: String,
    val sessionExpiresAtMs: Long,
    val sessionExpired: Boolean,
    val lastAutoSyncedPeerIp: String?,
    val syncing: Boolean,
    val receiveDirPath: String,
    val resolvedTarget: P2pResolvedTarget?,
    val networkKey: String,
    val deviceLabel: String,
    val nowMs: Long,
    val sessionEnabled: Boolean = true,
    val serviceRunning: Boolean = false
)

@Composable
fun BindP2pSessionRuntimeEffects(
    sessionPresenter: P2pSessionPresenter,
    input: P2pSessionRuntimeBindingsInput
) {
    val selectedConnection = input.connection.takeIf { input.activeConnectionMode == ConnectionMode.WIFI_DIRECT }
    LaunchedEffect(input.networkKey, input.resolvedTarget?.ip, input.resolvedTarget?.peerId) {
        sessionPresenter.observeConnectionContext(input.networkKey, input.resolvedTarget)
    }
    LaunchedEffect(
        input.serviceRunning,
        input.sessionEnabled,
        input.networkKey,
        input.permissionGranted,
        input.connection?.groupFormed,
        input.lanConnected,
        input.sessionToken,
        input.sessionPin,
        input.sessionExpiresAtMs,
        input.receiveDirPath,
        input.sessionExpired
    ) {
        sessionPresenter.reconcileBackendSession(
            permissionGranted = input.permissionGranted,
            lanConnected = input.lanConnected,
            connection = input.connection,
            receiveDirPath = input.receiveDirPath,
            nowMs = input.nowMs,
            sessionEnabled = input.sessionEnabled,
            serviceRunning = input.serviceRunning
        )
    }

    // Loop keys describe the connection, never clock ticks or state mutated by the loop.
    LaunchedEffect(
        input.sessionEnabled,
        input.networkKey,
        input.permissionGranted,
        selectedConnection?.groupFormed,
        selectedConnection?.isGroupOwner,
        selectedConnection?.groupOwnerAddress,
        input.sessionToken,
        input.sessionPin,
        input.sessionExpiresAtMs,
        input.deviceLabel,
        input.sessionExpired
    ) {
        if (!input.sessionEnabled) return@LaunchedEffect
        sessionPresenter.runPresenceAnnouncementLoop(
            permissionGranted = input.permissionGranted,
            connection = selectedConnection,
            deviceLabel = input.deviceLabel,
            nowMs = input.nowMs
        )
    }

    LaunchedEffect(
        input.sessionEnabled,
        input.networkKey,
        input.permissionGranted,
        selectedConnection?.groupFormed,
        selectedConnection?.isGroupOwner,
        selectedConnection?.groupOwnerAddress,
        input.deviceLabel,
        input.resolvedTarget?.peerId,
        input.sessionExpired
    ) {
        if (!input.sessionEnabled) return@LaunchedEffect
        sessionPresenter.runDirectAutoSyncLoop(
            permissionGranted = input.permissionGranted,
            connection = selectedConnection,
            lanConnected = input.lanConnected,
            deviceLabel = input.deviceLabel,
            nowMs = input.nowMs
        )
    }

    LaunchedEffect(
        input.sessionEnabled,
        input.networkKey,
        input.lanConnected,
        selectedConnection?.groupFormed,
        selectedConnection?.groupOwnerAddress,
        input.resolvedTarget?.ip,
        input.resolvedTarget?.mode,
        input.resolvedTarget?.peerId,
        input.sessionExpired
    ) {
        if (!input.sessionEnabled) return@LaunchedEffect
        sessionPresenter.runLanAutoSyncLoop(
            lanConnected = input.lanConnected,
            connection = selectedConnection,
            resolvedTarget = input.resolvedTarget,
            deviceLabel = input.deviceLabel,
            nowMs = input.nowMs
        )
    }
}
