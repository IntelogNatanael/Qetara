package com.example.wifidrop.presentation

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
    val sessionExpired: Boolean
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

fun buildSessionRenewedStatus(minutes: Int): String = "Sesión renovada por $minutes min."

fun shouldStartBackendSession(input: P2pSessionServiceInput): Boolean {
    val connected = input.lanConnected || (input.permissionGranted && input.connection?.groupFormed == true)
    val validSecurity = FileTransfer.isValidToken(input.token) &&
        TransferSecurity.isValidPin(input.pin) &&
        !input.sessionExpired
    return connected && validSecurity
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
        failureStatus = "No pude sincronizar automaticamente. Usa 'Sync token'."
    )
}

fun resolveLanAutoSyncPlan(
    lanConnected: Boolean,
    resolvedTarget: P2pResolvedTarget?,
    sessionExpired: Boolean,
    lastAutoSyncedPeerIp: String?
): P2pAutoSyncPlan? {
    val targetIp = resolvedTarget?.ip?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    val shouldAutoSyncLan = lanConnected &&
        resolvedTarget?.mode == ConnectionMode.LAN &&
        !sessionExpired &&
        lastAutoSyncedPeerIp != targetIp
    if (!shouldAutoSyncLan) return null
    return P2pAutoSyncPlan(
        hostIp = targetIp,
        maxAttempts = 3,
        failureStatus = "No pude sincronizar la sesión automáticamente."
    )
}

fun buildSyncStartStatus(manual: Boolean): String {
    return if (manual) {
        "Sincronizando la sesión..."
    } else {
        "Sincronizando la sesión automáticamente..."
    }
}

fun buildSyncBusyStatus(): String = "Ya hay una sincronización en curso."

fun buildMissingSyncHostStatus(): String = "No pude encontrar la IP del otro equipo."

fun buildManualSyncHintStatus(): String =
    "Indica o detecta la IP del otro equipo para sincronizar la sesión."

fun buildHostSharesSessionStatus(): String =
    "Este equipo comparte la sesión. El otro la recibirá al conectarse."

fun buildInvalidPeerIpStatus(): String =
    "La dirección del equipo no es válida."

fun buildSyncRetryStatus(attempt: Int, maxAttempts: Int): String {
    return "Reintentando sincronizacion automatica ($attempt/$maxAttempts)..."
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
            "Credenciales sincronizadas automáticamente."
        } else {
            "Credenciales ya sincronizadas; sesión renovada."
        }
    )
}

fun buildSessionSyncFailureStatus(errorMessage: String?): String {
    return actionableSessionSyncFailure(errorMessage)
}
