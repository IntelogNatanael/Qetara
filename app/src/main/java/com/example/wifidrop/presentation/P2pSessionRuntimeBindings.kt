package com.example.wifidrop.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.wifidrop.ConnectionSnapshot

data class P2pSessionRuntimeBindingsInput(
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
    val deviceLabel: String,
    val nowMs: Long
)

@Composable
fun BindP2pSessionRuntimeEffects(
    sessionPresenter: P2pSessionPresenter,
    input: P2pSessionRuntimeBindingsInput
) {
    LaunchedEffect(
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
            nowMs = input.nowMs
        )
    }

    LaunchedEffect(
        input.permissionGranted,
        input.connection?.groupFormed,
        input.connection?.isGroupOwner,
        input.connection?.groupOwnerAddress,
        input.sessionToken,
        input.sessionPin,
        input.sessionExpiresAtMs,
        input.deviceLabel,
        input.nowMs
    ) {
        sessionPresenter.runPresenceAnnouncementLoop(
            permissionGranted = input.permissionGranted,
            connection = input.connection,
            deviceLabel = input.deviceLabel,
            nowMs = input.nowMs
        )
    }

    LaunchedEffect(
        input.permissionGranted,
        input.connection?.groupFormed,
        input.connection?.isGroupOwner,
        input.connection?.groupOwnerAddress,
        input.deviceLabel,
        input.sessionExpired,
        input.lastAutoSyncedPeerIp,
        input.syncing,
        input.nowMs
    ) {
        sessionPresenter.runDirectAutoSyncLoop(
            permissionGranted = input.permissionGranted,
            connection = input.connection,
            lanConnected = input.lanConnected,
            deviceLabel = input.deviceLabel,
            nowMs = input.nowMs
        )
    }

    LaunchedEffect(
        input.lanConnected,
        input.connection?.groupFormed,
        input.connection?.groupOwnerAddress,
        input.resolvedTarget,
        input.sessionExpired,
        input.lastAutoSyncedPeerIp,
        input.syncing,
        input.nowMs
    ) {
        sessionPresenter.runLanAutoSyncLoop(
            lanConnected = input.lanConnected,
            connection = input.connection,
            resolvedTarget = input.resolvedTarget,
            deviceLabel = input.deviceLabel,
            nowMs = input.nowMs
        )
    }
}
