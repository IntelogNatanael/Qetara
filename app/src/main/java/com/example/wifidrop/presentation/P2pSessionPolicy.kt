package com.example.wifidrop.presentation

import com.example.wifidrop.R
import com.example.wifidrop.appString

import com.example.wifidrop.ConnectionMode
import com.example.wifidrop.ConnectionSnapshot
import com.example.wifidrop.FileTransfer
import com.example.wifidrop.SessionCredentialsPayload
import com.example.wifidrop.TransferSecurity

data class P2pSessionServiceInput(
    val permissionGranted: Boolean,
    val lanConnected: Boolean,
    val connection: ConnectionSnapshot?,
    val token: String,
    val pin: String,
    val sessionExpired: Boolean,
    val sessionEnabled: Boolean = true
)

data class P2pPresenceAnnouncementInput(
    val permissionGranted: Boolean,
    val connection: ConnectionSnapshot?,
    val token: String,
    val pin: String,
    val sessionExpired: Boolean
)

data class P2pSessionSyncRequest(
    val hostIp: String,
    val manual: Boolean,
    val currentToken: String,
    val currentPin: String
)

data class P2pSessionSyncSuccess(
    val token: String,
    val pin: String?,
    val expiresAtMs: Long,
    val statusMessage: String
)

data class P2pAutoSyncPlan(
    val hostIp: String,
    val maxAttempts: Int,
    val failureStatus: String
)

fun normalizeSessionToken(raw: String): String = FileTransfer.normalizeToken(raw)

fun normalizeSessionPin(raw: String): String = TransferSecurity.normalizePin(raw)

fun generateSessionToken(): String = FileTransfer.randomToken(8)

fun generateSessionPin(): String = TransferSecurity.randomPin()

fun computeRenewedSessionExpiry(nowMs: Long, minutes: Int): Long {
    return nowMs + minutes * 60 * 1000L
}

fun buildSessionRenewedStatus(minutes: Int): String = appString(R.string.pr_session_renewed, minutes)

fun shouldStartBackendSession(input: P2pSessionServiceInput): Boolean {
    val connected = input.lanConnected || (input.permissionGranted && input.connection?.groupFormed == true)
    val validSecurity = FileTransfer.isValidToken(input.token) &&
        TransferSecurity.isValidPin(input.pin) &&
        !input.sessionExpired
    return input.sessionEnabled && connected && validSecurity
}

enum class P2pSessionServiceAction { START, STOP, NONE }

fun resolveSessionServiceAction(input: P2pSessionServiceInput, serviceRunning: Boolean): P2pSessionServiceAction = when {
    shouldStartBackendSession(input) -> P2pSessionServiceAction.START
    serviceRunning -> P2pSessionServiceAction.STOP
    else -> P2pSessionServiceAction.NONE
}

fun resolvePresenceAnnouncementHost(input: P2pPresenceAnnouncementInput): String? {
    val connection = input.connection
    val shouldAnnounce = input.permissionGranted &&
        connection?.groupFormed == true &&
        !connection.isGroupOwner &&
        !connection.groupOwnerAddress.isNullOrBlank() &&
        FileTransfer.isValidToken(input.token) &&
        TransferSecurity.isValidPin(input.pin) &&
        !input.sessionExpired
    return if (shouldAnnounce) connection?.groupOwnerAddress.orEmpty() else null
}

fun resolveManualSyncHostIp(
    connection: ConnectionSnapshot?,
    resolvedTargetIp: String?,
    targetIpInput: String
): String? {
    return when {
        connection?.groupFormed == true && !connection.isGroupOwner -> connection.groupOwnerAddress?.trim()
        !resolvedTargetIp.isNullOrBlank() -> resolvedTargetIp.trim()
        else -> targetIpInput.trim().ifBlank { null }
    }?.takeUnless { it.isBlank() }
}

fun resolveDirectAutoSyncPlan(
    permissionGranted: Boolean,
    connection: ConnectionSnapshot?,
    sessionExpired: Boolean,
    lastAutoSyncedPeerIp: String?
): P2pAutoSyncPlan? {
    val directConnection = connection ?: return null
    val hostIp = directConnection.groupOwnerAddress?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    val isClientConnected = permissionGranted &&
        directConnection.groupFormed &&
        !directConnection.isGroupOwner
    if (!isClientConnected || sessionExpired || lastAutoSyncedPeerIp == hostIp) return null
    return P2pAutoSyncPlan(
        hostIp = hostIp,
        maxAttempts = 4,
        failureStatus = appString(R.string.pr_sync_auto_failed_manual)
    )
}

fun resolveLanAutoSyncPlan(
    lanConnected: Boolean,
    resolvedTarget: P2pResolvedTarget?,
    sessionExpired: Boolean,
    lastAutoSyncedPeerIp: String?
): P2pAutoSyncPlan? {
    val targetIp = resolvedTarget?.ip?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    val shouldAutoSyncLan = isAutomaticConnectionAddress(targetIp) &&
        lanConnected &&
        resolvedTarget?.mode == ConnectionMode.LAN &&
        !sessionExpired &&
        lastAutoSyncedPeerIp != targetIp
    if (!shouldAutoSyncLan) return null
    return P2pAutoSyncPlan(
        hostIp = targetIp,
        maxAttempts = 3,
        failureStatus = appString(R.string.pr_sync_auto_failed)
    )
}

fun buildSyncStartStatus(manual: Boolean): String {
    return if (manual) {
        appString(R.string.pr_sync_start)
    } else {
        appString(R.string.pr_sync_auto_start)
    }
}

fun buildSyncBusyStatus(): String = appString(R.string.pr_sync_busy)

fun buildMissingSyncHostStatus(): String = appString(R.string.pr_sync_host_missing)

fun buildManualSyncHintStatus(): String =
    appString(R.string.pr_sync_manual_hint)

fun buildHostSharesSessionStatus(): String =
    appString(R.string.pr_sync_host_shares)

fun buildInvalidPeerIpStatus(): String =
    appString(R.string.pr_sync_invalid_peer)

fun buildSyncRetryStatus(attempt: Int, maxAttempts: Int): String {
    return appString(R.string.pr_sync_retry, attempt, maxAttempts)
}

fun buildSessionSyncSuccess(
    payload: SessionCredentialsPayload,
    currentToken: String,
    currentPin: String
): P2pSessionSyncSuccess {
    val normalizedToken = FileTransfer.normalizeToken(payload.token)
    val normalizedPin = TransferSecurity.normalizePin(payload.pin)
    val tokenChanged = normalizedToken != currentToken
    val pinChanged = TransferSecurity.isValidPin(normalizedPin) && normalizedPin != currentPin
    return P2pSessionSyncSuccess(
        token = normalizedToken,
        pin = normalizedPin.takeIf { TransferSecurity.isValidPin(it) },
        expiresAtMs = payload.expiresAtMs,
        statusMessage = if (tokenChanged || pinChanged) {
            appString(R.string.pr_sync_success)
        } else {
            appString(R.string.pr_sync_already_success)
        }
    )
}

fun buildSessionSyncFailureStatus(errorMessage: String?): String {
    return actionableSessionSyncFailure(errorMessage)
}

/** Retrying cannot resolve compatibility or a changed cryptographic identity. */
fun sessionSyncRequiresUserAction(error: String): Boolean = listOf(
    "sesion_cerrada",
    "grupo_direct_no_acreditado",
    "grupo_direct_renovado",
    "destino_fuera_grupo_direct",
    "secure_credentials_required",
    "emparejamiento_manual_requerido",
    "noise_key_mismatch",
    "clave noise"
).any { error.contains(it, ignoreCase = true) }

/** Bind a successful exchange to the authenticated identity at the requested socket endpoint. */
internal fun applySessionSyncResult(
    current: P2pSessionState,
    started: P2pSessionState,
    hostIp: String,
    payload: SessionCredentialsPayload
): P2pSessionState {
    if (current.connectionNetworkKey != started.connectionNetworkKey ||
        current.connectionTargetIp != hostIp ||
        current.token != started.token || current.pin != started.pin ||
        current.expiresAtMs != started.expiresAtMs) return current
    val verifiedPeerId = payload.peerId?.takeIf { it.isNotBlank() }
        ?: started.connectionTargetPeerId.takeIf { started.connectionTargetIp == hostIp }
    if (current.connectionTargetPeerId != null && verifiedPeerId != null &&
        current.connectionTargetPeerId != verifiedPeerId) return current
    val synced = buildSessionSyncSuccess(payload, started.token, started.pin)
    val pin = synced.pin ?: current.pin
    return current.copy(
        token = synced.token,
        pin = pin,
        expiresAtMs = synced.expiresAtMs,
        syncStatus = synced.statusMessage,
        lastAutoSyncedPeerIp = hostIp,
        connectionTargetPeerId = verifiedPeerId,
        confirmation = P2pSessionConfirmation(
            peerIp = hostIp,
            peerId = verifiedPeerId,
            networkKey = started.connectionNetworkKey,
            token = synced.token,
            pin = pin
        )
    )
}
