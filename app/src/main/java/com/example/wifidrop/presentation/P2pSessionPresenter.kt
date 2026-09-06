package com.example.wifidrop.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.example.wifidrop.ConnectionSnapshot
import com.example.wifidrop.LocalDeviceIdentity
import com.example.wifidrop.TransferSecurity
import com.example.wifidrop.backend.P2pBackend
import kotlinx.coroutines.CancellationException
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
    val localDeviceIdShort: String,
    val confirmation: P2pSessionConfirmation? = null,
    val connectionNetworkKey: String = "",
    val connectionTargetIp: String? = null,
    val connectionTargetPeerId: String? = null,
    val readinessChangedAtMs: Long = System.currentTimeMillis()
)

data class P2pSessionFeedback(
    val message: String,
    val isError: Boolean = false
)

class P2pSessionPresenter(
    context: Context,
    private val backend: P2pBackend,
    private val localDeviceId: String,
    restoredSession: P2pRestoredSession? = null
) {
    private val appContext = context.applicationContext
    private val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val _state = MutableStateFlow(
        P2pSessionState(
            token = restoredSession?.token ?: generateSessionToken(),
            pin = restoredSession?.pin ?: generateSessionPin(),
            expiresAtMs = restoredSession?.expiresAtMs ?: (System.currentTimeMillis() + SESSION_DURATION_MS),
            localDeviceIdShort = LocalDeviceIdentity.short(localDeviceId)
        )
    )
    val state: StateFlow<P2pSessionState> = _state.asStateFlow()
    private val syncCoordinator = P2pSessionSyncCoordinator()
    private var lastSyncNeedsUserAction = false

    fun isSessionExpired(nowMs: Long): Boolean {
        return TransferSecurity.isExpired(_state.value.expiresAtMs, nowMs)
    }

    fun normalizeAndSetToken(raw: String) {
        val token = normalizeSessionToken(raw)
        _state.update { current -> if (current.token == token) current else current.copy(
            token = token, confirmation = null, lastAutoSyncedPeerIp = null, readinessChangedAtMs = System.currentTimeMillis()
        ) }
    }

    fun normalizeAndSetPin(raw: String) {
        val pin = normalizeSessionPin(raw)
        _state.update { current -> if (current.pin == pin) current else current.copy(
            pin = pin, confirmation = null, lastAutoSyncedPeerIp = null, readinessChangedAtMs = System.currentTimeMillis()
        ) }
    }

    fun renewSession(minutes: Int = 30, nowMs: Long = System.currentTimeMillis()) {
        _state.update { current ->
            current.copy(
                expiresAtMs = computeRenewedSessionExpiry(nowMs, minutes),
                syncStatus = buildSessionRenewedStatus(minutes),
                confirmation = null,
                lastAutoSyncedPeerIp = null,
                readinessChangedAtMs = nowMs
            )
        }
    }

    fun generateToken() {
        normalizeAndSetToken(generateSessionToken())
    }

    fun generatePin() {
        normalizeAndSetPin(generateSessionPin())
    }

    fun observeConnectionContext(networkKey: String, target: P2pResolvedTarget?) {
        _state.update { current ->
            val networkChanged = current.connectionNetworkKey != networkKey
            val targetChanged = current.connectionTargetIp != target?.ip || current.connectionTargetPeerId != target?.peerId
            if (!networkChanged && !targetChanged) current else current.copy(
                connectionNetworkKey = networkKey,
                connectionTargetIp = target?.ip,
                connectionTargetPeerId = target?.peerId,
                confirmation = null,
                lastAutoSyncedPeerIp = null,
                readinessChangedAtMs = if (networkChanged) System.currentTimeMillis() else current.readinessChangedAtMs
            )
        }
    }

    fun clearSessionConfirmation() {
        lastSyncNeedsUserAction = false
        _state.update { it.copy(
            confirmation = null,
            lastAutoSyncedPeerIp = null,
            readinessChangedAtMs = System.currentTimeMillis(),
            syncStatus = if (it.syncing) it.syncStatus else ""
        ) }
    }

    fun confirmManualSession(target: P2pResolvedTarget?, networkKey: String): P2pSessionFeedback {
        val current = _state.value
        if (target == null || !com.example.wifidrop.FileTransfer.isValidToken(current.token) ||
            !TransferSecurity.isValidPin(current.pin) || isSessionExpired(System.currentTimeMillis())) {
            return P2pSessionFeedback("Revisa el equipo, el código y el PIN antes de continuar.", isError = true)
        }
        observeConnectionContext(networkKey, target)
        _state.update { state -> state.copy(
            confirmation = P2pSessionConfirmation(target.ip, target.peerId, networkKey, state.token, state.pin),
            lastAutoSyncedPeerIp = target.ip,
            syncStatus = "Datos de sesión confirmados para "+ (target.label ?: target.ip) + "."
        ) }
        return P2pSessionFeedback("Sesión preparada con los datos que confirmaste.")
    }

    fun copyToken(): P2pSessionFeedback {
        clipboard.setPrimaryClip(ClipData.newPlainText("WifiDropToken", _state.value.token))
        return P2pSessionFeedback("Código de sesión copiado.")
    }

    fun pasteToken(): P2pSessionFeedback {
        val pasted = clipboard.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(appContext)
            ?.toString()
            .orEmpty()
        return if (pasted.isNotBlank()) {
            normalizeAndSetToken(pasted)
            P2pSessionFeedback("Código de sesión pegado.")
        } else {
            P2pSessionFeedback("No hay texto valido en el portapapeles.", isError = true)
        }
    }

    fun markConnectingForSync() {
        _state.update { current ->
            current.copy(
                lastAutoSyncedPeerIp = null,
                confirmation = null,
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
        return syncCoordinator.syncOnce {
            val currentState = _state.value
            lastSyncNeedsUserAction = false
            _state.update { current ->
                current.copy(syncing = true, confirmation = null, syncStatus = buildSyncStartStatus(manual))
            }
            try {
                val result = backend.requestSessionCredentials(
                    hostAddress = hostIp,
                    clientId = localDeviceId,
                    deviceLabel = deviceLabel
                )
                result.fold(
                    onSuccess = { payload ->
                        var applied = false
                        _state.update { current ->
                            val next = applySessionSyncResult(current, currentState, hostIp, payload)
                            applied = next !== current
                            next
                        }
                        applied
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        val cause = error.message ?: error::class.java.simpleName
                        lastSyncNeedsUserAction = sessionSyncRequiresUserAction(cause)
                        _state.update { current -> current.copy(syncStatus = buildSessionSyncFailureStatus(cause)) }
                        false
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val cause = error.message ?: error::class.java.simpleName
                lastSyncNeedsUserAction = sessionSyncRequiresUserAction(cause)
                _state.update { current -> current.copy(syncStatus = buildSessionSyncFailureStatus(cause)) }
                false
            } finally {
                _state.update { current -> current.copy(syncing = false) }
            }
        }
    }

    suspend fun reconcileBackendSession(
        permissionGranted: Boolean,
        lanConnected: Boolean,
        connection: ConnectionSnapshot?,
        receiveDirPath: String,
        nowMs: Long,
        sessionEnabled: Boolean = true,
        serviceRunning: Boolean = false
    ) {
        val current = _state.value
        val serviceInput = P2pSessionServiceInput(
            permissionGranted = permissionGranted,
            lanConnected = lanConnected,
            connection = connection,
            token = current.token,
            pin = current.pin,
            sessionExpired = isSessionExpired(nowMs),
            sessionEnabled = sessionEnabled
        )
        when (resolveSessionServiceAction(serviceInput, serviceRunning)) {
            P2pSessionServiceAction.START -> backend.startSession(
                token = current.token,
                pin = current.pin,
                sessionExpiresAtMs = current.expiresAtMs,
                receiveDirPath = receiveDirPath
            )
            P2pSessionServiceAction.STOP -> backend.stopSession()
            P2pSessionServiceAction.NONE -> Unit
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

                if (lastSyncNeedsUserAction || attempts >= directAutoSyncPlan.maxAttempts) break

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
            if (!lanConnected && connection?.groupFormed != true) {
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

            if (lastSyncNeedsUserAction || attempts >= lanAutoSyncPlan.maxAttempts) break

            _state.update { state ->
                state.copy(syncStatus = buildSyncRetryStatus(attempts, lanAutoSyncPlan.maxAttempts))
            }
            delay(2_000)
        }
    }
}

private const val SESSION_DURATION_MS = 30 * 60 * 1000L
