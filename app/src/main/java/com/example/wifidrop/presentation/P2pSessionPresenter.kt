package com.example.wifidrop.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.example.wifidrop.ConnectionSnapshot
import com.example.wifidrop.LocalDeviceIdentity
import com.example.wifidrop.TransferSecurity
import com.example.wifidrop.backend.P2pBackend
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive

data class P2pSessionState(
    val token: String,
    val pin: String,
    val expiresAtMs: Long,
    val syncStatus: String = "",
    val syncing: Boolean = false,
    val lastAutoSyncedPeerIp: String? = null,
    val localDeviceIdShort: String
)

data class P2pSessionFeedback(
    val message: String,
    val isError: Boolean = false
)

class P2pSessionPresenter(
    context: Context,
    private val backend: P2pBackend,
    private val localDeviceId: String
) {
    private val appContext = context.applicationContext
    private val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val _state = MutableStateFlow(
        P2pSessionState(
            token = generateSessionToken(),
            pin = generateSessionPin(),
            expiresAtMs = System.currentTimeMillis() + SESSION_DURATION_MS,
            localDeviceIdShort = LocalDeviceIdentity.short(localDeviceId)
        )
    )
    val state: StateFlow<P2pSessionState> = _state.asStateFlow()

    fun isSessionExpired(nowMs: Long): Boolean {
        return TransferSecurity.isExpired(_state.value.expiresAtMs, nowMs)
    }

    fun normalizeAndSetToken(raw: String) {
        _state.update { current -> current.copy(token = normalizeSessionToken(raw)) }
    }

    fun normalizeAndSetPin(raw: String) {
        _state.update { current -> current.copy(pin = normalizeSessionPin(raw)) }
    }

    fun renewSession(minutes: Int = 30, nowMs: Long = System.currentTimeMillis()) {
        _state.update { current ->
            current.copy(
                expiresAtMs = computeRenewedSessionExpiry(nowMs, minutes),
                syncStatus = buildSessionRenewedStatus(minutes)
            )
        }
    }

    fun generateToken() {
        _state.update { current -> current.copy(token = generateSessionToken()) }
    }

    fun generatePin() {
        _state.update { current -> current.copy(pin = generateSessionPin()) }
    }

    fun copyToken(): P2pSessionFeedback {
        clipboard.setPrimaryClip(ClipData.newPlainText("WifiDropToken", _state.value.token))
        return P2pSessionFeedback("Token copiado.")
    }

    fun pasteToken(): P2pSessionFeedback {
        val pasted = clipboard.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(appContext)
            ?.toString()
            .orEmpty()
        return if (pasted.isNotBlank()) {
            normalizeAndSetToken(pasted)
            P2pSessionFeedback("Token pegado y normalizado.")
        } else {
            P2pSessionFeedback("No hay texto valido en el portapapeles.", isError = true)
        }
    }

    fun markConnectingForSync() {
        _state.update { current ->
            current.copy(
                lastAutoSyncedPeerIp = null,
                syncStatus = "Conectando con el otro equipo y preparando la sincronización..."
            )
        }
    }

    fun markHostSharesSession() {
        _state.update { current -> current.copy(syncStatus = buildHostSharesSessionStatus()) }
    }

    fun markManualSyncHint() {
        _state.update { current -> current.copy(syncStatus = buildManualSyncHintStatus()) }
    }

    fun markInvalidPeerIp() {
        _state.update { current -> current.copy(syncStatus = buildInvalidPeerIpStatus()) }
    }

    fun resolveManualSyncHost(
        connection: ConnectionSnapshot?,
        resolvedTargetIp: String?,
        targetIpInput: String
    ): String? {
        return resolveManualSyncHostIp(
            connection = connection,
            resolvedTargetIp = resolvedTargetIp,
            targetIpInput = targetIpInput
        )
    }

    suspend fun syncTokenFromHost(
        hostIp: String,
        manual: Boolean,
        deviceLabel: String
    ): Boolean {
        if (hostIp.isBlank()) {
            _state.update { current -> current.copy(syncStatus = buildMissingSyncHostStatus()) }
            return false
        }
        if (_state.value.syncing) {
            if (manual) {
                _state.update { current -> current.copy(syncStatus = buildSyncBusyStatus()) }
            }
            return false
        }

        val currentState = _state.value
        _state.update { current ->
            current.copy(
                syncing = true,
                syncStatus = buildSyncStartStatus(manual)
            )
        }

        val result = backend.requestSessionCredentials(
            hostAddress = hostIp,
            clientId = localDeviceId,
            deviceLabel = deviceLabel
        )

        return try {
            var ok = false
            result.fold(
                onSuccess = { payload ->
                    val syncResult = buildSessionSyncSuccess(
                        payload = payload,
                        currentToken = currentState.token,
                        currentPin = currentState.pin
                    )
                    _state.update { current ->
                        current.copy(
                            token = syncResult.token,
                            pin = syncResult.pin ?: current.pin,
                            expiresAtMs = syncResult.expiresAtMs,
                            syncStatus = syncResult.statusMessage
                        )
                    }
                    ok = true
                },
                onFailure = { error ->
                    _state.update { current ->
                        current.copy(
                            syncStatus = buildSessionSyncFailureStatus(
                                error.message ?: error::class.java.simpleName
                            )
                        )
                    }
                }
            )
            ok
        } finally {
            _state.update { current -> current.copy(syncing = false) }
        }
    }

    suspend fun reconcileBackendSession(
        permissionGranted: Boolean,
        lanConnected: Boolean,
        connection: ConnectionSnapshot?,
        receiveDirPath: String,
        nowMs: Long
    ) {
        val current = _state.value
        val shouldRunSession = shouldStartBackendSession(
            P2pSessionServiceInput(
                permissionGranted = permissionGranted,
                lanConnected = lanConnected,
                connection = connection,
                token = current.token,
                pin = current.pin,
                sessionExpired = isSessionExpired(nowMs)
            )
        )

        if (shouldRunSession) {
            backend.startSession(
                token = current.token,
                pin = current.pin,
                sessionExpiresAtMs = current.expiresAtMs,
                receiveDirPath = receiveDirPath
            )
        } else {
            backend.stopSession()
        }
    }

    suspend fun runPresenceAnnouncementLoop(
        permissionGranted: Boolean,
        connection: ConnectionSnapshot?,
        deviceLabel: String,
        nowMs: Long
    ) {
        val current = _state.value
        val hostIp = resolvePresenceAnnouncementHost(
            P2pPresenceAnnouncementInput(
                permissionGranted = permissionGranted,
                connection = connection,
                token = current.token,
                pin = current.pin,
                sessionExpired = isSessionExpired(nowMs)
            )
        )

        if (!hostIp.isNullOrBlank()) {
            while (currentCoroutineContext().isActive) {
                val latest = _state.value
                backend.announcePresence(
                    hostAddress = hostIp,
                    token = latest.token,
                    pin = latest.pin,
                    clientId = localDeviceId,
                    deviceLabel = deviceLabel
                )
                delay(7_000)
            }
        }
    }

    suspend fun runDirectAutoSyncLoop(
        permissionGranted: Boolean,
        connection: ConnectionSnapshot?,
        lanConnected: Boolean,
        deviceLabel: String,
        nowMs: Long
    ) {
        val current = _state.value
        val directAutoSyncPlan = resolveDirectAutoSyncPlan(
            permissionGranted = permissionGranted,
            connection = connection,
            sessionExpired = isSessionExpired(nowMs),
            lastAutoSyncedPeerIp = current.lastAutoSyncedPeerIp
        )

        if (directAutoSyncPlan != null && !current.syncing) {
            val hostIp = directAutoSyncPlan.hostIp
            var attempts = 0
            while (currentCoroutineContext().isActive) {
                val latestConnection = connection
                val stillConnected = permissionGranted &&
                    latestConnection?.groupFormed == true &&
                    !latestConnection.isGroupOwner &&
                    latestConnection.groupOwnerAddress == hostIp
                if (!stillConnected) break

                attempts += 1
                val synced = syncTokenFromHost(
                    hostIp = hostIp,
                    manual = false,
                    deviceLabel = deviceLabel
                )
                if (synced) {
                    _state.update { state -> state.copy(lastAutoSyncedPeerIp = hostIp) }
                    break
                }

                if (attempts >= directAutoSyncPlan.maxAttempts) {
                    _state.update { state -> state.copy(syncStatus = directAutoSyncPlan.failureStatus) }
                    break
                }

                _state.update { state ->
                    state.copy(syncStatus = buildSyncRetryStatus(attempts, directAutoSyncPlan.maxAttempts))
                }
                delay(2_000)
            }
        } else {
            if (connection?.groupFormed != true && !lanConnected) {
                _state.update { state -> state.copy(syncStatus = "") }
            }
            if (!lanConnected) {
                _state.update { state -> state.copy(lastAutoSyncedPeerIp = null) }
            }
        }
    }

    suspend fun runLanAutoSyncLoop(
        lanConnected: Boolean,
        connection: ConnectionSnapshot?,
        resolvedTarget: P2pResolvedTarget?,
        deviceLabel: String,
        nowMs: Long
    ) {
        val current = _state.value
        val lanAutoSyncPlan = resolveLanAutoSyncPlan(
            lanConnected = lanConnected,
            resolvedTarget = resolvedTarget,
            sessionExpired = isSessionExpired(nowMs),
            lastAutoSyncedPeerIp = current.lastAutoSyncedPeerIp
        )

        if (lanAutoSyncPlan == null) {
            if (connection?.groupFormed != true) {
                _state.update { state -> state.copy(lastAutoSyncedPeerIp = null) }
            }
            return
        }

        if (current.syncing) return

        val targetIp = lanAutoSyncPlan.hostIp
        var attempts = 0
        while (currentCoroutineContext().isActive) {
            val latestTarget = resolvedTarget
            val stillReady = lanConnected &&
                latestTarget?.mode == com.example.wifidrop.ConnectionMode.LAN &&
                latestTarget.ip == targetIp
            if (!stillReady) break

            attempts += 1
            val synced = syncTokenFromHost(
                hostIp = targetIp,
                manual = false,
                deviceLabel = deviceLabel
            )
            if (synced) {
                _state.update { state -> state.copy(lastAutoSyncedPeerIp = targetIp) }
                break
            }

            if (attempts >= lanAutoSyncPlan.maxAttempts) {
                _state.update { state -> state.copy(syncStatus = lanAutoSyncPlan.failureStatus) }
                break
            }

            _state.update { state ->
                state.copy(syncStatus = buildSyncRetryStatus(attempts, lanAutoSyncPlan.maxAttempts))
            }
            delay(2_000)
        }
    }
}

private const val SESSION_DURATION_MS = 30 * 60 * 1000L
