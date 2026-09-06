package com.example.wifidrop

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.File
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

class TransferForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var shuttingDown = false

    private var receiverJob: Job? = null
    private var sessionStopBarrier: Job? = null
    private val sessionLifecycle = SessionLifecycleFence()
    private val sendJobs = linkedMapOf<String, Job>()
    private val sendSnapshots = linkedMapOf<String, SendTaskSnapshot>()
    private val sendPayloads = linkedMapOf<String, SendTaskPayload>()
    private val sendLock = Any()
    private var sendBatchTotal = 0
    private var sendBatchCompleted = 0
    private var sendBatchFailed = 0
    private var sendBatchCanceled = 0

    private val messageLock = Any()
    private val pendingMessageTasks = linkedMapOf<String, PendingMessageTask>()
    private var messageSenderJob: Job? = null
    private var messageTickerJob: Job? = null
    private val deniedCredentialShareUntilMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val credentialRequestPromptedAtMs = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private var currentToken: String = ""
    private var currentPin: String = ""
    private var currentSessionExpiresAtMs: Long = 0L
    private var receiveDir: File? = null

    private val knownPeersMap = java.util.concurrent.ConcurrentHashMap<String, KnownPeerSnapshot>()
    private var directGroupMonitor: WifiDirectGroupMonitor? = null

    @Volatile
    private var pausedTransfers = false

    @Volatile
    private var cancelCurrentTransfer = false
    private val receiveCancellationGeneration = java.util.concurrent.atomic.AtomicLong(0L)

    private var wakeLock: PowerManager.WakeLock? = null

    private val receiveTracker = ThroughputTracker()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Servicio listo"))
        directGroupMonitor = WifiDirectGroupMonitor(applicationContext) { group ->
            _state.update { state -> state.copy(
                directGroup = group,
                authenticatedPeerRoutes = if (state.directGroup?.sessionId == group?.sessionId) state.authenticatedPeerRoutes else emptyList()
            ) }
        }.also { it.start() }
        val trusted = TrustedPeerStore.all(applicationContext)
        val lastTarget = LastSendTargetStore.get(applicationContext)
        val pending = PendingMessageStore.list(applicationContext)
        synchronized(messageLock) {
            pendingMessageTasks.clear()
            pending.forEach { task ->
                pendingMessageTasks[task.id] = task
            }
        }
        startMessageTicker()

        _state.update {
            it.copy(
                serviceRunning = true,
                trustedPeers = trusted,
                favoritePeers = trusted.filter { p -> p.favorite },
                history = TransferHistoryStore.list(applicationContext),
                chatMessages = ChatMessageStore.list(applicationContext),
                pendingMessageCount = pending.size,
                messageStatus = if (pending.isNotEmpty()) {
                    "Mensajes pendientes: ${pending.size}"
                } else {
                    it.messageStatus
                },
                lastSendTargetIp = lastTarget?.ip,
                lastSendTargetLabel = lastTarget?.label,
                lastSendTargetAtMs = lastTarget?.updatedAtMs
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sessionLifecycle.recordCommand(startId)
        if (!UxPreferencesStore.load(applicationContext).sessionEnabled &&
            (intent == null || intent.action in SESSION_NETWORK_ACTIONS)) {
            stopSession()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_START_SESSION -> startOrUpdateSession(intent)
            ACTION_STOP_SESSION -> stopSession()
            ACTION_SEND_FILE -> startSend(intent)
            ACTION_SEND_MESSAGE -> startSendMessage(intent)
            ACTION_SEND_GLOBAL_MESSAGE -> startSendBroadcastMessage(intent)
            ACTION_SEND_SILENT_MESSAGE -> startSendSilentMessage(intent)
            ACTION_RETRY_MESSAGE -> retryMessage(intent)
            ACTION_CANCEL_PENDING_MESSAGE -> cancelPendingMessage(intent)
            ACTION_DELETE_CHAT_MESSAGE -> deleteChatMessage(intent)
            ACTION_CLEAR_CHAT_MESSAGES -> clearChatMessages(intent)
            ACTION_PAUSE_TRANSFERS -> setPaused(true)
            ACTION_RESUME_TRANSFERS -> setPaused(false)
            ACTION_CANCEL_ACTIVE -> requestCancelActiveTransfer()
            ACTION_OPEN_DOWNLOADS -> openDownloadsFolder()
            ACTION_TRUST_PEER -> trustPeer(intent)
            ACTION_FORGET_PEER -> forgetPeer(intent)
            ACTION_REPORT_DISCOVERED_PEER -> reportDiscoveredPeer(intent)
            ACTION_REPORT_AUTHENTICATED_PEER -> reportAuthenticatedPeer(intent)
            ACTION_APPROVE_CREDENTIAL_SHARE -> approveCredentialShare(intent)
            ACTION_REJECT_CREDENTIAL_SHARE -> rejectCredentialShare(intent)
            ACTION_SET_PEER_FAVORITE -> setPeerFavorite(intent)
            ACTION_SET_PEER_ALIAS -> setPeerAlias(intent)
            ACTION_SEND_QUEUE_PAUSE_ITEM -> pauseSendQueueItem(intent)
            ACTION_SEND_QUEUE_RESUME_ITEM -> resumeSendQueueItem(intent)
            ACTION_SEND_QUEUE_CANCEL_ITEM -> cancelSendQueueItem(intent)
            ACTION_SEND_QUEUE_MOVE_UP -> moveSendQueueItem(intent, up = true)
            ACTION_SEND_QUEUE_MOVE_DOWN -> moveSendQueueItem(intent, up = false)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        sessionLifecycle.beginStop()
        shuttingDown = true
        directGroupMonitor?.stop()
        directGroupMonitor = null
        receiverJob?.cancel()
        cancelAllSendJobs()
        messageSenderJob?.cancel()
        messageTickerJob?.cancel()
        synchronized(sendLock) {
            sendJobs.clear()
            sendSnapshots.clear()
            sendPayloads.clear()
            sendBatchTotal = 0
            sendBatchCompleted = 0
            sendBatchFailed = 0
            sendBatchCanceled = 0
        }
        synchronized(messageLock) {
            pendingMessageTasks.clear()
        }
        deniedCredentialShareUntilMs.clear()
        credentialRequestPromptedAtMs.clear()
        serviceScope.cancel()
        releaseWakeLock()
        val trusted = TrustedPeerStore.all(applicationContext)
        val lastTarget = LastSendTargetStore.get(applicationContext)
        val pending = PendingMessageStore.list(applicationContext)

        _state.value = TransferRuntimeState(
            history = TransferHistoryStore.list(applicationContext),
            chatMessages = ChatMessageStore.list(applicationContext),
            pendingMessageCount = pending.size,
            messageStatus = if (pending.isNotEmpty()) {
                "Mensajes pendientes: ${pending.size}"
            } else {
                ""
            },
            trustedPeers = trusted,
            favoritePeers = trusted.filter { it.favorite },
            lastSendTargetIp = lastTarget?.ip,
            lastSendTargetLabel = lastTarget?.label,
            lastSendTargetAtMs = lastTarget?.updatedAtMs
        )
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startOrUpdateSession(intent: Intent) {
        directGroupMonitor?.refresh()
        val tokenRaw = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        val pinRaw = intent.getStringExtra(EXTRA_PIN).orEmpty()
        val expiresAtMs = intent.getLongExtra(EXTRA_SESSION_EXPIRES_AT_MS, 0L)
        val dirPath = intent.getStringExtra(EXTRA_RECEIVE_DIR).orEmpty()

        if (!FileTransfer.isValidToken(tokenRaw)) {
            _state.update {
                it.copy(
                    receiverStatus = "Token invalido para sesion.",
                    activeToken = null
                )
            }
            updateForegroundNotification("Token invalido")
            return
        }

        if (!TransferSecurity.isValidPin(pinRaw)) {
            _state.update { it.copy(receiverStatus = "PIN invalido para sesion.") }
            updateForegroundNotification("PIN invalido")
            return
        }

        if (TransferSecurity.isExpired(expiresAtMs)) {
            _state.update { it.copy(receiverStatus = "Sesion expirada. Renueva credenciales.") }
            updateForegroundNotification("Sesion expirada")
            return
        }

        if (dirPath.isBlank()) {
            _state.update { it.copy(receiverStatus = "Directorio de recepcion invalido.") }
            updateForegroundNotification("Directorio invalido")
            return
        }

        val normalizedToken = FileTransfer.normalizeToken(tokenRaw)
        val normalizedPin = TransferSecurity.normalizePin(pinRaw)
        val nextDir = File(dirPath).apply { mkdirs() }

        val tokenChanged = normalizedToken != currentToken
        val pinChanged = normalizedPin != currentPin
        val expiresChanged = expiresAtMs != currentSessionExpiresAtMs
        val dirChanged = receiveDir?.absolutePath != nextDir.absolutePath

        if (!shuttingDown && !tokenChanged && !pinChanged && !expiresChanged && !dirChanged &&
            receiverJob?.isActive == true) {
            syncWakeLockAndLifetime()
            return
        }

        val reopeningAfterStop = shuttingDown
        val ticket = sessionLifecycle.beginStart()
        val previousReceiver = receiverJob
        val previousStop = sessionStopBarrier
        previousReceiver?.cancel()
        // A startForegroundService request must be acknowledged even while old workers drain.
        startForeground(NOTIFICATION_ID, buildNotification("Preparando sesion..."))
        receiverJob = serviceScope.launch(start = CoroutineStart.LAZY) {
            if (!awaitSessionWorkers(listOfNotNull(previousReceiver, previousStop), sessionLifecycle, ticket)) {
                return@launch
            }
            val activated = withContext(Dispatchers.Main.immediate) {
                if (!sessionLifecycle.canActivate(ticket) ||
                    !UxPreferencesStore.load(applicationContext).sessionEnabled) {
                    false
                } else {
                    // No cancelled worker from the previous session can publish past this point.
                    if (reopeningAfterStop) {
                        knownPeersMap.clear()
                        deniedCredentialShareUntilMs.clear()
                        credentialRequestPromptedAtMs.clear()
                    }
                    shuttingDown = false
                    currentToken = normalizedToken
                    currentPin = normalizedPin
                    currentSessionExpiresAtMs = expiresAtMs
                    receiveDir = nextDir
                    val trusted = TrustedPeerStore.all(applicationContext)
                    _state.update { state -> state.copy(
                        serviceRunning = true,
                        activeToken = normalizedToken,
                        sessionExpiresAtMs = expiresAtMs,
                        knownPeers = if (reopeningAfterStop) emptyList() else state.knownPeers,
                        pendingTrust = if (reopeningAfterStop) null else state.pendingTrust,
                        pendingCredentialShare = if (reopeningAfterStop) null else state.pendingCredentialShare,
                        authenticatedPeerRoutes = if (reopeningAfterStop) emptyList() else state.authenticatedPeerRoutes,
                        credentialSharedPeers = if (reopeningAfterStop || tokenChanged || pinChanged || expiresChanged || dirChanged)
                            emptyList() else state.credentialSharedPeers,
                        trustedPeers = trusted,
                        favoritePeers = trusted.filter { it.favorite },
                        history = TransferHistoryStore.list(applicationContext),
                        receiverStatus = "Preparando recepcion..."
                    ) }
                    if (messageTickerJob?.isActive != true) startMessageTicker()
                    syncWakeLockAndLifetime()
                    refreshMessageState()
                    pumpMessageQueue()
                    true
                }
            }
            if (activated) {
                currentCoroutineContext().ensureActive()
                runReceiverLoop(normalizedToken, normalizedPin, expiresAtMs, nextDir)
            }
        }.also { it.start() }
    }

    private suspend fun runReceiverLoop(token: String, pin: String, expiresAtMs: Long, dir: File) {
        val receiverContext = currentCoroutineContext()
        receiveTracker.reset()

        _state.update {
            it.copy(
                receiving = false,
                receiverListening = false,
                receiverStatus = "Preparando recepcion...",
                receiverProgress = null,
                receiverFileName = null,
                receiverInstantBps = 0L,
                receiverAverageBps = 0L,
                receiverEtaSeconds = null,
                receiverFailureCause = null
            )
        }

        while (serviceScope.isActive) {
            try {
                FileTransfer.receiveLoop(
                    context = applicationContext,
                    receiveDir = dir,
                    expectedTokenRaw = token,
                    expectedPinRaw = pin,
                    sessionExpiresAtMs = expiresAtMs,
                    isPeerTrusted = { peerId -> TrustedPeerStore.isTrusted(applicationContext, peerId) },
                    isGlobalLanJoined = { UxPreferencesStore.load(applicationContext).joinedGlobalLan },
                    isNoiseKeyCompatible = { peerId, noiseKey ->
                        TrustedPeerStore.isNoiseKeyCompatible(applicationContext, peerId, noiseKey)
                    },
                    onNoiseKeyObserved = { peerId, noiseKey ->
                        TrustedPeerStore.updateNoiseStaticKey(applicationContext, peerId, noiseKey)
                    },
                    onListening = {
                        _state.update { it.copy(receiverListening = true, receiverFailureCause = null) }
                        updateForegroundNotification()
                    },
                    onReceiveFailed = { error ->
                        receiveTracker.reset()
                        _state.update { state ->
                            state.copy(
                                receiving = false,
                                receiverFileName = null,
                                receiverProgress = null,
                                receiverInstantBps = 0L,
                                receiverAverageBps = 0L,
                                receiverEtaSeconds = null,
                                receiverFailureCause = if (error is CancellationException) null else error.message
                            )
                        }
                        updateForegroundNotification()
                    },
                    onStatus = { msg ->
                        // A discovery client's error must not replace an unrelated active file's status.
                        if (!_state.value.receiving) {
                            _state.update { state -> state.copy(receiverStatus = msg) }
                            updateForegroundNotification(msg)
                        }
                    },
                    onPeerSeen = { peerId, ip, label, trusted ->
                        onPeerSeen(peerId, ip, label, trusted)
                    },
                    onAuthenticatedPeerRoute = { route ->
                        if (receiverContext.isActive && !shuttingDown) {
                            _state.update { state -> state.copy(
                                authenticatedPeerRoutes = (state.authenticatedPeerRoutes.filterNot {
                                    it.peerId == route.peerId && it.peerIp == route.peerIp && it.localIp == route.localIp
                                } + route).takeLast(256)
                            ) }
                        }
                    },
                    onCredentialsRequested = credentialsRequest@ { peerId, ip, label, trusted, noiseKey ->
                        if (!receiverContext.isActive || shuttingDown) return@credentialsRequest false
                        if (trusted) {
                            val updatedTrusted = TrustedPeerStore.all(applicationContext)
                            _state.update { s ->
                                val pendingAfter = if (s.pendingCredentialShare?.id == peerId) null else s.pendingCredentialShare
                                s.copy(
                                    trustedPeers = updatedTrusted,
                                    favoritePeers = updatedTrusted.filter { p -> p.favorite },
                                    pendingCredentialShare = pendingAfter,
                                    receiverStatus = "Preparando conexion con $label ($ip)."
                                )
                            }
                            onPeerSeen(peerId, ip, label, trusted = true)
                            true
                        } else {
                            val now = System.currentTimeMillis()
                            val deniedUntil = deniedCredentialShareUntilMs[peerId] ?: 0L
                            if (now < deniedUntil) {
                                false
                            } else {
                                val recentlyPrompted = now - (credentialRequestPromptedAtMs[peerId] ?: 0L) <
                                    CREDENTIAL_REQUEST_PROMPT_DEBOUNCE_MS
                                val existing = _state.value.pendingCredentialShare
                                if (existing?.id == peerId && recentlyPrompted) {
                                    false
                                } else {
                                    credentialRequestPromptedAtMs[peerId] = now
                                    val req = PendingCredentialShareRequest(
                                        id = peerId,
                                        label = label,
                                        ip = ip,
                                        requestedAtMs = now,
                                        noiseStaticKey = noiseKey
                                    )
                                    _state.update { s ->
                                        s.copy(
                                            pendingCredentialShare = req,
                                            receiverStatus = "Solicitud de conexion de $label ($ip). Compara su huella antes de aprobar."
                                        )
                                    }
                                    updateForegroundNotification("Confirma la conexion: $label")
                                    false
                                }
                            }
                        }
                    },
                    onCredentialsShared = { peerId, ip, label ->
                        _state.update { state ->
                            if (!receiverContext.isActive || state.activeToken != token ||
                                state.sessionExpiresAtMs != expiresAtMs ||
                                !TrustedPeerStore.isTrusted(applicationContext, peerId)) {
                                state
                            } else {
                                state.copy(
                                    credentialSharedPeers = (state.credentialSharedPeers.filterNot {
                                        it.peerId == peerId || it.peerIp == ip
                                    } + SessionCredentialShare(peerId, ip, System.currentTimeMillis())).takeLast(200),
                                    receiverStatus = if (state.receiving) state.receiverStatus else "Credenciales compartidas con $label ($ip)."
                                )
                            }
                        }
                    },
                    onTrustRequired = { peerId, ip, label ->
                        val req = PendingTrustRequest(
                            id = peerId,
                            label = label,
                            ip = ip,
                            requestedAtMs = System.currentTimeMillis()
                        )
                        _state.update { s ->
                            s.copy(
                                pendingTrust = req,
                                receiverStatus = "Dispositivo no confiable detectado: $label ($ip)"
                            )
                        }
                        updateForegroundNotification("Confirma dispositivo: $label")
                    },
                    onMessageReceived = { peerId, ip, label, message, route ->
                        handleIncomingChatPayload(
                            peerId = peerId,
                            ip = ip,
                            label = label,
                            message = message,
                            route = route
                        )
                    },
                    onProgress = { fileName, received, total ->
                        if (!_state.value.receiving) receiveTracker.reset()
                        val snapshot = receiveTracker.update(received, total)
                        _state.update { s ->
                            s.copy(
                                receiving = true,
                                receiverFileName = fileName,
                                receiverFailureCause = null,
                                receiverStatus = "Recibiendo $fileName",
                                receiverProgress = if (total > 0) {
                                    (received.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    null
                                },
                                receiverInstantBps = snapshot.instantBps,
                                receiverAverageBps = snapshot.averageBps,
                                receiverEtaSeconds = snapshot.etaSeconds
                            )
                        }
                    },
                    onFileReceived = { file, _, peerAddress, peerLabel ->
                        val exportGeneration = receiveCancellationGeneration.get()
                        val exported = DownloadsExport.exportToDownloads(applicationContext, file) {
                            receiverContext.ensureActive()
                            if (receiveCancellationGeneration.get() != exportGeneration) {
                                throw CancellationException("exportacion cancelada; archivo original conservado")
                            }
                        }
                        val exportMessage = exported.fold(
                            onSuccess = { "Guardado en Descargas/Qetara." },
                            onFailure = {
                                "No pude copiar a Descargas: ${it.message ?: it::class.java.simpleName}."
                            }
                        )
                        val route = exported.getOrNull()?.toString() ?: file.absolutePath

                        val historyEntry = TransferHistoryStore.newEntry(
                            direction = TransferDirection.RECEIVED,
                            fileName = file.name,
                            bytes = file.length(),
                            outcome = TransferOutcome.SUCCESS,
                            peerLabel = peerLabel,
                            peerIp = peerAddress,
                            route = route,
                            errorCause = null
                        )
                        appendHistory(historyEntry)

                        _state.update { s ->
                            s.copy(
                                receiving = false,
                                receiverStatus = "Archivo recibido: ${file.name}. $exportMessage",
                                receiverProgress = null,
                                receiverFileName = null,
                                receiverInstantBps = 0L,
                                receiverEtaSeconds = null,
                                receiverFailureCause = null,
                                lastReceivedPath = route,
                                lastPeerIp = peerAddress,
                                lastPeerLabel = peerLabel
                            )
                        }
                        updateForegroundNotification("Recibido: ${file.name}")
                    },
                    awaitIfPaused = { awaitIfPaused() },
                    isCancelled = { cancelCurrentTransfer },
                    cancellationGeneration = { receiveCancellationGeneration.get() }
                )
            } catch (_: CancellationException) {
                break
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        receiving = false,
                        receiverListening = false,
                        receiverStatus = "Error receptor: ${e.message ?: e::class.java.simpleName}. Reintentando...",
                        receiverProgress = null,
                        receiverFileName = null,
                        receiverFailureCause = e.message ?: e::class.java.simpleName,
                        receiverInstantBps = 0L,
                        receiverEtaSeconds = null
                    )
                }
                updateForegroundNotification("Reintento de receptor")
                delay(1_000)
            }
        }

        _state.update {
            it.copy(
                receiving = false,
                receiverListening = false,
                receiverProgress = null,
                receiverFileName = null,
                receiverStatus = "Receptor detenido.",
                receiverInstantBps = 0L,
                receiverEtaSeconds = null
            )
        }
        syncWakeLockAndLifetime()
    }

    private fun startSend(intent: Intent) {
        val uri = readUriFromIntent(intent)
        val targetIp = intent.getStringExtra(EXTRA_TARGET_IP).orEmpty().trim()
        val tokenRaw = intent.getStringExtra(EXTRA_TOKEN).orEmpty().ifBlank { currentToken }
        val pinRaw = intent.getStringExtra(EXTRA_PIN).orEmpty().ifBlank { currentPin }
        val deviceLabel = intent.getStringExtra(EXTRA_DEVICE_LABEL).orEmpty().ifBlank { "peer" }
        val requestedName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()

        if (uri == null) {
            _state.update { it.copy(sendStatus = "No se recibio URI del archivo.") }
            return
        }

        if (targetIp.isBlank()) {
            _state.update { it.copy(sendStatus = "IP destino vacia.") }
            return
        }

        if (!FileTransfer.isValidToken(tokenRaw)) {
            _state.update { it.copy(sendStatus = "Token invalido para envio.") }
            return
        }

        if (!TransferSecurity.isValidPin(pinRaw)) {
            _state.update { it.copy(sendStatus = "PIN invalido para envio.") }
            return
        }

        val normalizedToken = FileTransfer.normalizeToken(tokenRaw)
        val normalizedPin = TransferSecurity.normalizePin(pinRaw)
        val clientId = LocalDeviceIdentity.getOrCreate(applicationContext)
        val peerLabel = findPeerLabelByIp(targetIp)
        val fallbackName = "file_${System.currentTimeMillis()}"
        val effectiveFileName = requestedName.ifBlank {
            FileTransfer.queryDisplayName(applicationContext, uri) ?: fallbackName
        }
        val transferId = UUID.randomUUID().toString()
        val initialBytes = queryUriSize(uri)

        cancelCurrentTransfer = false

        synchronized(sendLock) {
            if (!hasPendingBatchLocked()) {
                sendSnapshots.clear()
                sendPayloads.clear()
                sendBatchTotal = 0
                sendBatchCompleted = 0
                sendBatchFailed = 0
                sendBatchCanceled = 0
            }
            sendBatchTotal += 1
            sendSnapshots[transferId] = SendTaskSnapshot(
                id = transferId,
                fileName = effectiveFileName,
                targetIp = targetIp,
                peerLabel = peerLabel,
                totalBytes = initialBytes,
                sentBytes = 0L,
                instantBps = 0L,
                averageBps = 0L,
                etaSeconds = null,
                retriesUsed = 0,
                maxRetries = MAX_SEND_RETRIES,
                status = SendQueueStatus.QUEUED,
                lastError = null,
                pauseRequested = false,
                cancelRequested = false,
                done = false,
                addedAtMs = System.currentTimeMillis()
            )
            sendPayloads[transferId] = SendTaskPayload(
                uri = uri,
                fileName = effectiveFileName,
                targetIp = targetIp,
                token = normalizedToken,
                pin = normalizedPin,
                clientId = clientId,
                deviceLabel = deviceLabel,
                peerLabel = peerLabel
            )
        }

        updateSendAggregateState(
            statusOverride = "En cola: $effectiveFileName",
            clearFailure = true
        )
        pumpSendQueue()
        updateForegroundNotification("Enviando archivo(s)")
        syncWakeLockAndLifetime()
    }

    private fun startSendMessage(intent: Intent) {
        val targetIp = intent.getStringExtra(EXTRA_TARGET_IP).orEmpty().trim()
        val tokenRaw = intent.getStringExtra(EXTRA_TOKEN).orEmpty().ifBlank { currentToken }
        val pinRaw = intent.getStringExtra(EXTRA_PIN).orEmpty().ifBlank { currentPin }
        val deviceLabel = intent.getStringExtra(EXTRA_DEVICE_LABEL).orEmpty().ifBlank { "peer" }
        val message = intent.getStringExtra(EXTRA_MESSAGE_TEXT)
            .orEmpty()
            .trim()
            .take(2_000)

        if (targetIp.isBlank()) {
            _state.update { it.copy(messageStatus = "IP destino vacia para mensaje.") }
            return
        }

        if (!FileTransfer.isValidToken(tokenRaw)) {
            _state.update { it.copy(messageStatus = "Token invalido para mensaje.") }
            return
        }

        if (!TransferSecurity.isValidPin(pinRaw)) {
            _state.update { it.copy(messageStatus = "PIN invalido para mensaje.") }
            return
        }

        if (message.isBlank()) {
            _state.update { it.copy(messageStatus = "Mensaje vacio.") }
            return
        }

        val normalizedToken = FileTransfer.normalizeToken(tokenRaw)
        val normalizedPin = TransferSecurity.normalizePin(pinRaw)
        val clientId = LocalDeviceIdentity.getOrCreate(applicationContext)
        val peerLabel = intent.getStringExtra(EXTRA_PEER_LABEL_OVERRIDE)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: findPeerLabelByIp(targetIp)
            ?: targetIp
        val messageScope = parseMessageScope(intent.getStringExtra(EXTRA_MESSAGE_SCOPE))

        val chatEntry = ChatMessageStore.newOutgoing(
            text = message,
            peerLabel = peerLabel,
            peerIp = targetIp,
            scope = messageScope,
            status = ChatMessageStatus.QUEUED,
            errorCause = null
        )
        ChatMessageStore.append(applicationContext, chatEntry)

        val now = System.currentTimeMillis()
        val task = buildPendingMessageTask(
            chatMessageId = chatEntry.id,
            targetIp = targetIp,
            peerLabel = peerLabel,
            scope = messageScope,
            message = message,
            token = normalizedToken,
            pin = normalizedPin,
            clientId = clientId,
            deviceLabel = deviceLabel,
            trackChatStatus = true,
            now = now
        )
        enqueuePendingMessageTask(task)

        LastSendTargetStore.set(
            applicationContext,
            ipRaw = targetIp,
            labelRaw = peerLabel
        )
        _state.update {
            it.copy(
                lastSendTargetIp = targetIp,
                lastSendTargetLabel = peerLabel,
                lastSendTargetAtMs = now
            )
        }

        refreshMessageState("Mensaje en cola para $peerLabel")
        pumpMessageQueue()
        syncWakeLockAndLifetime()
    }

    private fun startSendSilentMessage(intent: Intent) {
        val targetIp = intent.getStringExtra(EXTRA_TARGET_IP).orEmpty().trim()
        val tokenRaw = intent.getStringExtra(EXTRA_TOKEN).orEmpty().ifBlank { currentToken }
        val pinRaw = intent.getStringExtra(EXTRA_PIN).orEmpty().ifBlank { currentPin }
        val deviceLabel = intent.getStringExtra(EXTRA_DEVICE_LABEL).orEmpty().ifBlank { "peer" }
        val message = intent.getStringExtra(EXTRA_MESSAGE_TEXT)
            .orEmpty()
            .trim()
        val peerLabel = intent.getStringExtra(EXTRA_PEER_LABEL_OVERRIDE)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: findPeerLabelByIp(targetIp)
            ?: targetIp
        val messageScope = parseMessageScope(intent.getStringExtra(EXTRA_MESSAGE_SCOPE))

        val queued = queueSilentMessage(
            targetIp = targetIp,
            peerLabel = peerLabel,
            scope = messageScope,
            message = message,
            tokenRaw = tokenRaw,
            pinRaw = pinRaw,
            deviceLabel = deviceLabel
        )
        if (queued) {
            pumpMessageQueue()
            syncWakeLockAndLifetime()
        }
    }

    private fun startSendBroadcastMessage(intent: Intent) {
        val tokenRaw = intent.getStringExtra(EXTRA_TOKEN).orEmpty().ifBlank { currentToken }
        val pinRaw = intent.getStringExtra(EXTRA_PIN).orEmpty().ifBlank { currentPin }
        val deviceLabel = intent.getStringExtra(EXTRA_DEVICE_LABEL).orEmpty().ifBlank { "peer" }
        val message = intent.getStringExtra(EXTRA_MESSAGE_TEXT)
            .orEmpty()
            .trim()
            .take(2_000)
        val messageScope = parseMessageScope(intent.getStringExtra(EXTRA_MESSAGE_SCOPE))
        val channelLabel = intent.getStringExtra(EXTRA_CHANNEL_LABEL)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: when (messageScope) {
                ChatMessageScope.GLOBAL_LAN -> "Global LAN"
                ChatMessageScope.DIRECT_CHANNEL -> "Canal Wi‑Fi Direct"
                ChatMessageScope.DIRECT -> "Canal"
            }

        if (!FileTransfer.isValidToken(tokenRaw)) {
            _state.update { it.copy(messageStatus = "Token invalido para $channelLabel.") }
            return
        }

        if (!TransferSecurity.isValidPin(pinRaw)) {
            _state.update { it.copy(messageStatus = "PIN invalido para $channelLabel.") }
            return
        }

        if (message.isBlank()) {
            _state.update { it.copy(messageStatus = "Mensaje vacio.") }
            return
        }

        val normalizedToken = FileTransfer.normalizeToken(tokenRaw)
        val normalizedPin = TransferSecurity.normalizePin(pinRaw)
        val clientId = LocalDeviceIdentity.getOrCreate(applicationContext)
        val targetPeerIds = intent.getStringArrayListExtra(EXTRA_TARGET_PEER_IDS)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val targetIps = intent.getStringArrayListExtra(EXTRA_TARGET_IPS)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
        val targetLabels = intent.getStringArrayListExtra(EXTRA_TARGET_LABELS).orEmpty()
        val chatEntry = ChatMessageStore.newOutgoing(
            text = message,
            peerLabel = channelLabel,
            peerIp = null,
            scope = messageScope,
            status = ChatMessageStatus.PUBLISHED,
            errorCause = null
        )
        ChatMessageStore.append(applicationContext, chatEntry)
        if (messageScope == ChatMessageScope.GLOBAL_LAN) {
            GlobalLanOutboxStore.upsert(
                applicationContext,
                GlobalLanOutboxEntry(
                    chatMessageId = chatEntry.id,
                    text = message,
                    createdAtMs = chatEntry.timestampMs,
                    dispatchedPeerIds = targetPeerIds.distinct()
                )
            )
        }

        val now = System.currentTimeMillis()
        targetIps.forEachIndexed { index, targetIp ->
            val peerLabel = targetLabels.getOrNull(index)?.trim().takeUnless { it.isNullOrBlank() }
                ?: findPeerLabelByIp(targetIp)
                ?: targetIp
            val task = buildPendingMessageTask(
                chatMessageId = chatEntry.id,
                targetIp = targetIp,
                peerLabel = peerLabel,
                scope = messageScope,
                message = message,
                token = normalizedToken,
                pin = normalizedPin,
                clientId = clientId,
                deviceLabel = deviceLabel,
                trackChatStatus = false,
                now = now
            )
            enqueuePendingMessageTask(task)
        }

        refreshMessageState(
            if (targetIps.isEmpty()) {
                "Mensaje publicado en $channelLabel."
            } else {
                "Mensaje publicado en $channelLabel para ${if (targetIps.size == 1) "1 equipo" else "${targetIps.size} equipos"}."
            }
        )
        if (targetIps.isNotEmpty()) {
            pumpMessageQueue()
            updateForegroundNotification("Mensaje de canal en curso")
            syncWakeLockAndLifetime()
        }
    }

    private fun parseMessageScope(raw: String?): ChatMessageScope {
        return when (raw?.trim()) {
            ChatMessageScope.GLOBAL_LAN.name -> ChatMessageScope.GLOBAL_LAN
            ChatMessageScope.DIRECT_CHANNEL.name -> ChatMessageScope.DIRECT_CHANNEL
            else -> ChatMessageScope.DIRECT
        }
    }

    private fun buildPendingMessageTask(
        chatMessageId: String,
        targetIp: String,
        peerLabel: String?,
        scope: ChatMessageScope,
        message: String,
        token: String,
        pin: String,
        clientId: String,
        deviceLabel: String,
        trackChatStatus: Boolean,
        now: Long
    ): PendingMessageTask {
        return PendingMessageTask(
            id = UUID.randomUUID().toString(),
            chatMessageId = chatMessageId.ifBlank { "system_${UUID.randomUUID()}" },
            targetIp = targetIp,
            peerLabel = peerLabel,
            scope = scope,
            message = message,
            token = token,
            pin = pin,
            clientId = clientId,
            deviceLabel = deviceLabel,
            trackChatStatus = trackChatStatus,
            retriesUsed = 0,
            maxRetries = MAX_MESSAGE_RETRIES,
            nextAttemptAtMs = now,
            createdAtMs = now,
            lastError = null,
            expectedPeerId = _state.value.knownPeers.firstOrNull { it.ip == targetIp }?.id,
            directGroupSessionId = if (requiresDirectGroupDelivery(message, scope, clientId)) _state.value.directGroup?.sessionId else null
        )
    }

    private fun enqueuePendingMessageTask(task: PendingMessageTask) {
        PendingMessageStore.upsert(applicationContext, task)
        synchronized(messageLock) {
            pendingMessageTasks[task.id] = task
        }
    }

    private fun queueSilentMessage(
        targetIp: String,
        peerLabel: String?,
        scope: ChatMessageScope,
        message: String,
        tokenRaw: String,
        pinRaw: String,
        deviceLabel: String
    ): Boolean {
        if (targetIp.isBlank()) return false
        if (!FileTransfer.isValidToken(tokenRaw) || !TransferSecurity.isValidPin(pinRaw)) return false
        if (message.isBlank()) return false

        val task = buildPendingMessageTask(
            chatMessageId = "system_${UUID.randomUUID()}",
            targetIp = targetIp,
            peerLabel = peerLabel,
            scope = scope,
            message = message,
            token = FileTransfer.normalizeToken(tokenRaw),
            pin = TransferSecurity.normalizePin(pinRaw),
            clientId = LocalDeviceIdentity.getOrCreate(applicationContext),
            deviceLabel = deviceLabel,
            trackChatStatus = false,
            now = System.currentTimeMillis()
        )
        enqueuePendingMessageTask(task)
        return true
    }

    private fun retryMessage(intent: Intent) {
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID).orEmpty().trim()
        val deviceLabel = intent.getStringExtra(EXTRA_DEVICE_LABEL).orEmpty().ifBlank { "peer" }
        if (messageId.isBlank()) return

        val queuedTask = synchronized(messageLock) {
            pendingMessageTasks.values.firstOrNull { it.chatMessageId == messageId }
        }
        if (queuedTask != null) {
            val now = System.currentTimeMillis()
            val refreshed = queuedTask.copy(
                retriesUsed = 0,
                nextAttemptAtMs = now,
                createdAtMs = now,
                maxRetries = MAX_MESSAGE_RETRIES,
                lastError = null
            )
            synchronized(messageLock) {
                pendingMessageTasks[refreshed.id] = refreshed
            }
            PendingMessageStore.upsert(applicationContext, refreshed)
            ChatMessageStore.updateStatus(
                applicationContext,
                messageIdRaw = messageId,
                status = ChatMessageStatus.QUEUED,
                errorCause = null
            )
            refreshMessageState("Reintento programado.")
            pumpMessageQueue()
            return
        }

        val chat = ChatMessageStore.list(applicationContext, limit = 300)
            .firstOrNull { it.id == messageId }
        if (chat == null || chat.direction != ChatMessageDirection.OUTGOING) {
            _state.update { it.copy(messageStatus = "No encontré ese mensaje para reintentar.") }
            return
        }

        val targetIp = chat.peerIp.orEmpty().trim()
        if (targetIp.isBlank()) {
            _state.update { it.copy(messageStatus = "El mensaje no tiene IP destino.") }
            return
        }
        if (!FileTransfer.isValidToken(currentToken) || !TransferSecurity.isValidPin(currentPin)) {
            _state.update {
                it.copy(messageStatus = "Sesion invalida para reintentar. Renueva token/PIN.")
            }
            return
        }

        val now = System.currentTimeMillis()
        val task = PendingMessageTask(
            id = UUID.randomUUID().toString(),
            chatMessageId = chat.id,
            targetIp = targetIp,
            peerLabel = chat.peerLabel,
            scope = chat.scope,
            message = chat.text,
            token = currentToken,
            pin = currentPin,
            clientId = LocalDeviceIdentity.getOrCreate(applicationContext),
            deviceLabel = deviceLabel,
            trackChatStatus = true,
            retriesUsed = 0,
            maxRetries = MAX_MESSAGE_RETRIES,
            nextAttemptAtMs = now,
            createdAtMs = now,
            lastError = null
        )
        synchronized(messageLock) {
            pendingMessageTasks[task.id] = task
        }
        PendingMessageStore.upsert(applicationContext, task)
        ChatMessageStore.updateStatus(
            applicationContext,
            messageIdRaw = chat.id,
            status = ChatMessageStatus.QUEUED,
            errorCause = null
        )
        refreshMessageState("Mensaje reencolado para ${chat.peerLabel ?: targetIp}")
        pumpMessageQueue()
    }

    private fun cancelPendingMessage(intent: Intent) {
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID).orEmpty().trim()
        if (messageId.isBlank()) return

        val removed = synchronized(messageLock) {
            val ids = pendingMessageTasks.values
                .filter { it.chatMessageId == messageId }
                .map { it.id }
            ids.forEach { pendingMessageTasks.remove(it) }
            ids.isNotEmpty()
        }
        PendingMessageStore.removeByChatMessageId(applicationContext, messageId)
        GlobalLanOutboxStore.remove(applicationContext, messageId)
        if (removed) {
            ChatMessageStore.updateStatus(
                applicationContext,
                messageIdRaw = messageId,
                status = ChatMessageStatus.CANCELED,
                errorCause = "cancelado por usuario"
            )
            refreshMessageState("Mensaje cancelado.")
        } else {
            refreshMessageState("El mensaje ya no estaba en cola.")
        }
    }

    private fun deleteChatMessage(intent: Intent) {
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID).orEmpty().trim()
        if (messageId.isBlank()) return

        val hadPending = synchronized(messageLock) {
            val ids = pendingMessageTasks.values
                .filter { it.chatMessageId == messageId }
                .map { it.id }
            ids.forEach { pendingMessageTasks.remove(it) }
            ids.isNotEmpty()
        }

        if (hadPending) {
            messageSenderJob?.cancel(CancellationException("mensaje eliminado"))
        }

        PendingMessageStore.removeByChatMessageId(applicationContext, messageId)
        GlobalLanOutboxStore.remove(applicationContext, messageId)
        ChatMessageStore.remove(applicationContext, messageId)
        refreshMessageState("Mensaje borrado.")
        pumpMessageQueue()
        syncWakeLockAndLifetime()
    }

    private fun clearChatMessages(intent: Intent) {
        val scope = intent.getStringExtra(EXTRA_MESSAGE_SCOPE)
            ?.trim()
            ?.let {
                when (it) {
                    ChatMessageScope.GLOBAL_LAN.name -> ChatMessageScope.GLOBAL_LAN
                    ChatMessageScope.DIRECT_CHANNEL.name -> ChatMessageScope.DIRECT_CHANNEL
                    ChatMessageScope.DIRECT.name -> ChatMessageScope.DIRECT
                    else -> null
                }
            }

        val hadPending = synchronized(messageLock) {
            if (scope == null) {
                val had = pendingMessageTasks.isNotEmpty()
                pendingMessageTasks.clear()
                had
            } else {
                val hasMatching = pendingMessageTasks.values.any { it.scope == scope }
                pendingMessageTasks.entries.removeAll { it.value.scope == scope }
                hasMatching
            }
        }
        if (hadPending) {
            messageSenderJob?.cancel(CancellationException("chat limpiado por usuario"))
        }

        if (scope == null) {
            PendingMessageStore.clear(applicationContext)
            ChatMessageStore.clear(applicationContext)
            GlobalLanOutboxStore.clear(applicationContext)
        } else {
            PendingMessageStore.clearScope(applicationContext, scope)
            ChatMessageStore.clearScope(applicationContext, scope)
            if (scope == ChatMessageScope.GLOBAL_LAN) {
                GlobalLanOutboxStore.clear(applicationContext)
            }
        }
        refreshMessageState(
            when (scope) {
                ChatMessageScope.DIRECT -> "Chat directo limpiado."
                ChatMessageScope.GLOBAL_LAN -> "Global LAN limpiado."
                ChatMessageScope.DIRECT_CHANNEL -> "Canal Wi‑Fi Direct limpiado."
                null -> "Mensajes borrados."
            }
        )
        pumpMessageQueue()
        syncWakeLockAndLifetime()
    }

    private fun hasPendingBatchLocked(): Boolean {
        return sendSnapshots.values.any { !it.done }
    }

    private fun pumpSendQueue() {
        if (shuttingDown || !UxPreferencesStore.load(applicationContext).sessionEnabled) return
        val toLaunch = mutableListOf<String>()
        synchronized(sendLock) {
            if (shuttingDown || !UxPreferencesStore.load(applicationContext).sessionEnabled) return
            val activeCount = sendJobs.size + sendSnapshots.values.count {
                it.status == SendQueueStatus.RUNNING && !it.done && it.id !in sendJobs
            }
            val slots = (MAX_PARALLEL_SENDS - activeCount).coerceAtLeast(0)
            if (slots <= 0) return
            sendSnapshots.values
                .filter {
                    it.status == SendQueueStatus.QUEUED &&
                        !it.pauseRequested &&
                        !it.cancelRequested &&
                        !it.done
                }
                .take(slots)
                .forEach { task ->
                    sendSnapshots[task.id] = task.copy(status = SendQueueStatus.RUNNING, lastError = null)
                    toLaunch += task.id
                }
        }
        toLaunch.forEach { transferId -> launchSendJob(transferId) }
        if (toLaunch.isNotEmpty()) {
            updateSendAggregateState()
            updateForegroundNotification()
            syncWakeLockAndLifetime()
        }
    }

    private fun launchSendJob(transferId: String) {
        val job = withSessionWorkerAdmission(sendLock, {
            shuttingDown || !UxPreferencesStore.load(applicationContext).sessionEnabled
        }) {
            serviceScope.launch(start = CoroutineStart.LAZY) {
                runSendJob(transferId)
            }.also { admittedJob ->
                sendJobs[transferId] = admittedJob
                admittedJob.invokeOnCompletion { cause ->
                    if (cause is CancellationException) markSendTaskCancelled(transferId, cause.message ?: "cancelado")
                    synchronized(sendLock) {
                        sendJobs.remove(transferId)
                        if (sendJobs.isEmpty() && cancelCurrentTransfer) {
                            cancelCurrentTransfer = false
                        }
                    }
                    updateSendAggregateState()
                    pumpSendQueue()
                    updateForegroundNotification()
                    syncWakeLockAndLifetime()
                }
            }
        } ?: return
        job.start()
    }

    private fun markSendTaskCancelled(transferId: String, reason: String) {
        val canceled = synchronized(sendLock) {
            val current = sendSnapshots[transferId] ?: return@synchronized null
            if (current.done) return@synchronized null
            sendBatchCanceled += 1
            sendSnapshots[transferId] = current.copy(
                status = SendQueueStatus.CANCELED, done = true, cancelRequested = true,
                pauseRequested = false, instantBps = 0L, averageBps = 0L,
                etaSeconds = null, lastError = reason
            )
            sendPayloads.remove(transferId)?.let { current to it }
        } ?: return
        val (snapshot, payload) = canceled
        appendHistory(TransferHistoryStore.newEntry(
            direction = TransferDirection.SENT, fileName = payload.fileName,
            bytes = snapshot.totalBytes, outcome = TransferOutcome.CANCELED,
            peerLabel = payload.peerLabel, peerIp = payload.targetIp,
            route = payload.uri.toString(), errorCause = reason
        ))
    }

    private suspend fun runSendJob(transferId: String) {
        val payload = synchronized(sendLock) { sendPayloads[transferId] } ?: return
        val tracker = ThroughputTracker()

        while (serviceScope.isActive) {
            val snapshot = synchronized(sendLock) { sendSnapshots[transferId] } ?: return
            if (snapshot.done || snapshot.cancelRequested) return

            val result = FileTransfer.sendFile(
                context = applicationContext,
                fileUri = payload.uri,
                fileNameRaw = payload.fileName,
                transferId = transferId,
                expectedPeerId = _state.value.knownPeers.firstOrNull { it.ip == payload.targetIp }?.id,
                hostAddress = payload.targetIp,
                tokenRaw = payload.token,
                pinRaw = payload.pin,
                clientIdRaw = payload.clientId,
                clientLabelRaw = payload.deviceLabel,
                onProgress = { sent, total ->
                    val t = tracker.update(sent, total)
                    synchronized(sendLock) {
                        val current = sendSnapshots[transferId] ?: return@synchronized
                        val normalizedTotal = if (total > 0L) total else current.totalBytes
                        val status = if (current.pauseRequested) SendQueueStatus.PAUSED else SendQueueStatus.RUNNING
                        sendSnapshots[transferId] = current.copy(
                            sentBytes = sent.coerceAtLeast(0L),
                            totalBytes = normalizedTotal,
                            instantBps = t.instantBps,
                            averageBps = t.averageBps,
                            etaSeconds = t.etaSeconds,
                            status = status
                        )
                    }
                    updateSendAggregateState()
                    updateForegroundNotification()
                },
                awaitIfPaused = {
                    awaitIfPaused()
                    awaitTaskResume(transferId)
                },
                isCancelled = {
                    cancelCurrentTransfer || isTaskCancelled(transferId)
                }
            )

            val current = synchronized(sendLock) { sendSnapshots[transferId] } ?: return
            val error = result.exceptionOrNull()
            val success = result.isSuccess
            val canceled = error is CancellationException || current.cancelRequested || cancelCurrentTransfer

            if (success) {
                val bytes = queryUriSize(payload.uri)
                var historyShouldAppend = false
                synchronized(sendLock) {
                    val latest = sendSnapshots[transferId] ?: return@synchronized
                    if (!latest.done) {
                        sendBatchCompleted += 1
                        val normalizedTotal = if (latest.totalBytes > 0L) latest.totalBytes else bytes
                        val normalizedSent = if (normalizedTotal > 0L) normalizedTotal else latest.sentBytes
                        sendSnapshots[transferId] = latest.copy(
                            totalBytes = normalizedTotal,
                            sentBytes = normalizedSent,
                            instantBps = 0L,
                            averageBps = 0L,
                            etaSeconds = null,
                            status = SendQueueStatus.SUCCESS,
                            done = true,
                            lastError = null
                        )
                        sendPayloads.remove(transferId)
                        historyShouldAppend = true
                    }
                }
                if (historyShouldAppend) {
                    appendHistory(
                        TransferHistoryStore.newEntry(
                            direction = TransferDirection.SENT,
                            fileName = payload.fileName,
                            bytes = bytes,
                            outcome = TransferOutcome.SUCCESS,
                            peerLabel = payload.peerLabel,
                            peerIp = payload.targetIp,
                            route = payload.uri.toString(),
                            errorCause = null
                        )
                    )
                    LastSendTargetStore.set(
                        applicationContext,
                        ipRaw = payload.targetIp,
                        labelRaw = payload.peerLabel ?: payload.targetIp
                    )
                    _state.update {
                        it.copy(
                            lastSendTargetIp = payload.targetIp,
                            lastSendTargetLabel = payload.peerLabel ?: payload.targetIp,
                            lastSendTargetAtMs = System.currentTimeMillis()
                        )
                    }
                    updateSendAggregateState(
                        statusOverride = "Envio OK: ${payload.fileName}",
                        clearFailure = true
                    )
                    updateForegroundNotification("Envio OK: ${payload.fileName}")
                }
                return
            }

            if (canceled) {
                var historyShouldAppend = false
                synchronized(sendLock) {
                    val latest = sendSnapshots[transferId] ?: return@synchronized
                    if (!latest.done) {
                        sendBatchCanceled += 1
                        sendSnapshots[transferId] = latest.copy(
                            status = SendQueueStatus.CANCELED,
                            done = true,
                            instantBps = 0L,
                            averageBps = 0L,
                            etaSeconds = null,
                            lastError = "cancelado por usuario"
                        )
                        sendPayloads.remove(transferId)
                        historyShouldAppend = true
                    }
                }
                if (historyShouldAppend) {
                    appendHistory(
                        TransferHistoryStore.newEntry(
                            direction = TransferDirection.SENT,
                            fileName = payload.fileName,
                            bytes = queryUriSize(payload.uri),
                            outcome = TransferOutcome.CANCELED,
                            peerLabel = payload.peerLabel,
                            peerIp = payload.targetIp,
                            route = payload.uri.toString(),
                            errorCause = "cancelado por usuario"
                        )
                    )
                    updateSendAggregateState(
                        statusOverride = "Envio cancelado: ${payload.fileName}",
                        failureCause = "cancelado por usuario"
                    )
                    updateForegroundNotification("Envio cancelado")
                }
                return
            }

            val errorCause = error?.message ?: error?.javaClass?.simpleName ?: "error_desconocido"
            val shouldRetry = synchronized(sendLock) {
                val latest = sendSnapshots[transferId] ?: return@synchronized false
                isRetryableSendError(error) && latest.retriesUsed < latest.maxRetries && !latest.cancelRequested
            }
            if (shouldRetry) {
                var retryDelayMs = SHORT_RETRY_BASE_DELAY_MS
                synchronized(sendLock) {
                    val latest = sendSnapshots[transferId] ?: return@synchronized
                    val nextRetries = latest.retriesUsed + 1
                    retryDelayMs = (SHORT_RETRY_BASE_DELAY_MS * nextRetries).coerceAtMost(2_500L)
                    sendSnapshots[transferId] = latest.copy(
                        retriesUsed = nextRetries,
                        status = SendQueueStatus.RETRY_WAIT,
                        lastError = errorCause,
                        instantBps = 0L,
                        averageBps = 0L,
                        etaSeconds = null
                    )
                }
                updateSendAggregateState(
                    statusOverride = "Reintentando ${payload.fileName}...",
                    failureCause = errorCause
                )
                updateForegroundNotification("Reintento de envio")
                delay(retryDelayMs)
                synchronized(sendLock) {
                    val latest = sendSnapshots[transferId]
                    if (latest != null && !latest.done && !latest.cancelRequested) {
                        sendSnapshots[transferId] = latest.copy(
                            status = if (latest.pauseRequested) SendQueueStatus.PAUSED else SendQueueStatus.RUNNING,
                            lastError = null
                        )
                    }
                }
                continue
            }

            var historyShouldAppend = false
            synchronized(sendLock) {
                val latest = sendSnapshots[transferId] ?: return@synchronized
                if (!latest.done) {
                    sendBatchFailed += 1
                    sendSnapshots[transferId] = latest.copy(
                        status = SendQueueStatus.FAILED,
                        done = true,
                        instantBps = 0L,
                        averageBps = 0L,
                        etaSeconds = null,
                        lastError = errorCause
                    )
                    sendPayloads.remove(transferId)
                    historyShouldAppend = true
                }
            }
            if (historyShouldAppend) {
                appendHistory(
                    TransferHistoryStore.newEntry(
                        direction = TransferDirection.SENT,
                        fileName = payload.fileName,
                        bytes = queryUriSize(payload.uri),
                        outcome = TransferOutcome.FAILED,
                        peerLabel = payload.peerLabel,
                        peerIp = payload.targetIp,
                        route = payload.uri.toString(),
                        errorCause = errorCause
                    )
                )
                updateSendAggregateState(
                    statusOverride = "Error envio (${payload.fileName}): $errorCause",
                    failureCause = errorCause
                )
                updateForegroundNotification("Error de envio")
            }
            return
        }
    }

    private suspend fun awaitTaskResume(transferId: String) {
        while (isTaskPaused(transferId) && !isTaskCancelled(transferId) && !cancelCurrentTransfer) {
            synchronized(sendLock) {
                val current = sendSnapshots[transferId] ?: return
                if (!current.done && current.status != SendQueueStatus.PAUSED) {
                    sendSnapshots[transferId] = current.copy(status = SendQueueStatus.PAUSED)
                }
            }
            currentCoroutineContext().ensureActive()
            delay(180)
        }

        synchronized(sendLock) {
            val current = sendSnapshots[transferId] ?: return
            if (!current.done && !current.pauseRequested && current.status == SendQueueStatus.PAUSED) {
                sendSnapshots[transferId] = current.copy(status = SendQueueStatus.RUNNING)
            }
        }
    }

    private fun isTaskPaused(transferId: String): Boolean {
        return synchronized(sendLock) { sendSnapshots[transferId]?.pauseRequested == true }
    }

    private fun isTaskCancelled(transferId: String): Boolean {
        return synchronized(sendLock) { sendSnapshots[transferId]?.cancelRequested == true }
    }

    private fun updateSendAggregateState(
        statusOverride: String? = null,
        failureCause: String? = null,
        clearFailure: Boolean = false
    ) {
        val aggregate = synchronized(sendLock) { buildSendAggregateLocked() }
        _state.update { current ->
            val statusText = statusOverride ?: when {
                aggregate.activeCount > 0 -> "Enviando ${aggregate.activeCount} archivo(s)..."
                aggregate.pendingCount > 0 -> "Cola pendiente: ${aggregate.pendingCount} archivo(s)."
                aggregate.batchTotal > 0 -> {
                    "Lote finalizado: ${aggregate.completed} OK, ${aggregate.failed} fallidos, ${aggregate.canceled} cancelados."
                }
                else -> current.sendStatus
            }

            current.copy(
                sending = aggregate.activeCount > 0 || aggregate.pendingCount > 0,
                sendActiveCount = aggregate.activeCount,
                sendBatchTotal = aggregate.batchTotal,
                sendBatchCompleted = aggregate.completed,
                sendBatchFailed = aggregate.failed,
                sendBatchCanceled = aggregate.canceled,
                sendProgress = aggregate.progress,
                sendInstantBps = aggregate.instantBps,
                sendAverageBps = aggregate.averageBps,
                sendEtaSeconds = aggregate.etaSeconds,
                sendStatus = statusText,
                sendFailureCause = when {
                    clearFailure -> null
                    failureCause != null -> failureCause
                    aggregate.activeCount > 0 || aggregate.pendingCount > 0 -> null
                    else -> current.sendFailureCause
                },
                sendQueue = aggregate.queue
            )
        }
    }

    private fun buildSendAggregateLocked(): SendAggregateSnapshot {
        val snapshots = sendSnapshots.values.toList()
        val activeIds = sendJobs
            .filterValues { it.isActive }
            .keys
            .toSet()
        val activeTasks = activeIds
            .mapNotNull { id -> sendSnapshots[id] }
            .filter { task -> !task.done }
        val pendingCount = snapshots.count { task ->
            !task.done &&
                task.id !in activeIds &&
                (task.status == SendQueueStatus.QUEUED ||
                    task.status == SendQueueStatus.PAUSED ||
                    task.status == SendQueueStatus.RETRY_WAIT)
        }
        val doneCount = sendBatchCompleted + sendBatchFailed + sendBatchCanceled

        val partialUnits = activeTasks.sumOf { task ->
            if (task.totalBytes > 0L) {
                (task.sentBytes.toDouble() / task.totalBytes.toDouble()).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
        }

        val progress = if (sendBatchTotal > 0) {
            ((doneCount + partialUnits) / sendBatchTotal.toDouble()).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }

        val instant = activeTasks.sumOf { it.instantBps.coerceAtLeast(0L) }
        val average = activeTasks.sumOf { it.averageBps.coerceAtLeast(0L) }
        val unknownSizeActive = activeTasks.any { it.totalBytes <= 0L }
        val remainingKnown = activeTasks.sumOf { task ->
            if (task.totalBytes > 0L) (task.totalBytes - task.sentBytes).coerceAtLeast(0L) else 0L
        }
        val eta = if (!unknownSizeActive && average > 0L && remainingKnown > 0L) {
            (remainingKnown / average).coerceAtLeast(0L)
        } else {
            null
        }

        val queue = snapshots.map { task ->
            SendQueueItemSnapshot(
                id = task.id,
                fileName = task.fileName,
                targetIp = task.targetIp,
                peerLabel = task.peerLabel,
                status = task.status,
                sentBytes = task.sentBytes,
                totalBytes = task.totalBytes,
                retriesUsed = task.retriesUsed,
                maxRetries = task.maxRetries,
                lastError = task.lastError,
                addedAtMs = task.addedAtMs
            )
        }

        return SendAggregateSnapshot(
            activeCount = activeTasks.size,
            pendingCount = pendingCount,
            batchTotal = sendBatchTotal,
            completed = sendBatchCompleted,
            failed = sendBatchFailed,
            canceled = sendBatchCanceled,
            progress = progress,
            instantBps = instant,
            averageBps = average,
            etaSeconds = eta,
            queue = queue
        )
    }

    private fun isRetryableSendError(error: Throwable?): Boolean {
        if (error == null) return false
        return error is SocketTimeoutException ||
            error is SocketException ||
            error is ConnectException ||
            error is UnknownHostException ||
            error is EOFException
    }

    private fun queryUriSize(uri: Uri): Long {
        return try {
            applicationContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val size = pfd.statSize
                if (size >= 0L) size else -1L
            } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun onPeerSeen(
        peerId: String,
        ip: String,
        label: String,
        trusted: Boolean,
        globalLanJoined: Boolean? = null,
        updateLastPeerHint: Boolean = true
    ) {
        // A device can discover itself through another active network interface.
        if (peerId == LocalDeviceIdentity.getOrCreate(applicationContext)) return
        // Publish trust while holding the same monitor used by trust/remove. A callback that
        // finishes after Forget must not restore a stale trusted flag or trustedPeers snapshot.
        synchronized(TrustedPeerStore) {
            val now = System.currentTimeMillis()
            if (trusted) {
                TrustedPeerStore.updateSeen(applicationContext, peerId, label, ip)
            }
            val currentTrusted = TrustedPeerStore.all(applicationContext)
            val existing = knownPeersMap[peerId]
            knownPeersMap[peerId] = KnownPeerSnapshot(
                id = peerId,
                label = label,
                ip = ip,
                trusted = trusted || (existing?.trusted == true && existing.ip == ip),
                globalLanJoined = globalLanJoined ?: existing?.globalLanJoined ?: false,
                lastSeenAtMs = now
            )
            val staleBefore = now - 15 * 60 * 1000L
            val cleaned = retainCurrentPeerTrust(
                knownPeersMap.values.filter { it.lastSeenAtMs >= staleBefore }, currentTrusted
            ).sortedWith(
                compareByDescending<KnownPeerSnapshot> { it.trusted }.thenByDescending { it.lastSeenAtMs }
            ).take(200)
            knownPeersMap.clear()
            cleaned.forEach { knownPeersMap[it.id] = it }
            _state.update { state ->
                state.copy(
                    knownPeers = cleaned,
                    authenticatedPeerRoutes = removeReassignedPeerRoutes(state.authenticatedPeerRoutes, peerId, ip),
                    credentialSharedPeers = state.credentialSharedPeers.filterNot {
                        (it.peerId == peerId && it.peerIp != ip) || (it.peerIp == ip && it.peerId != peerId)
                    },
                    lastPeerIp = if (updateLastPeerHint) ip else state.lastPeerIp,
                    lastPeerLabel = if (updateLastPeerHint) label else state.lastPeerLabel,
                    trustedPeers = currentTrusted,
                    favoritePeers = currentTrusted.filter { it.favorite }
                )
            }
        }
        if (globalLanJoined == true) {
            dispatchGlobalLanBacklogToPeer(peerId = peerId, ip = ip, label = label)
        }
        pumpMessageQueue()
    }

    private fun dispatchGlobalLanBacklogToPeer(
        peerId: String,
        ip: String,
        label: String
    ) {
        if (peerId.isBlank() || ip.isBlank()) return
        val peerDisplayLabel = label.trim().ifBlank { ip }
        if (!UxPreferencesStore.load(applicationContext).joinedGlobalLan) return
        if (!FileTransfer.isValidToken(currentToken) || !TransferSecurity.isValidPin(currentPin)) return
        if (TransferSecurity.isExpired(currentSessionExpiresAtMs)) return

        val now = System.currentTimeMillis()
        val entries = GlobalLanOutboxStore.list(applicationContext)
            .filter { now - it.createdAtMs <= MAX_GLOBAL_LAN_RETROACTIVE_AGE_MS }
            .filterNot { it.dispatchedPeerIds.contains(peerId) }
        if (entries.isEmpty()) return

        var queuedAny = false
        entries.forEach { entry ->
            val duplicate = synchronized(messageLock) {
                pendingMessageTasks.values.any {
                    !it.trackChatStatus &&
                        it.scope == ChatMessageScope.GLOBAL_LAN &&
                        it.chatMessageId == entry.chatMessageId &&
                        it.targetIp == ip
                }
            }
            if (!duplicate) {
                val task = PendingMessageTask(
                    id = UUID.randomUUID().toString(),
                    chatMessageId = entry.chatMessageId,
                    targetIp = ip,
                    peerLabel = "Global · $peerDisplayLabel",
                    scope = ChatMessageScope.GLOBAL_LAN,
                    message = entry.text,
                    token = currentToken,
                    pin = currentPin,
                    clientId = LocalDeviceIdentity.getOrCreate(applicationContext),
                    deviceLabel = "equipo",
                    trackChatStatus = false,
                    retriesUsed = 0,
                    maxRetries = MAX_MESSAGE_RETRIES,
                    nextAttemptAtMs = now,
                    createdAtMs = now,
                    lastError = null
                )
                synchronized(messageLock) {
                    pendingMessageTasks[task.id] = task
                }
                PendingMessageStore.upsert(applicationContext, task)
                queuedAny = true
            }
            GlobalLanOutboxStore.upsert(
                applicationContext,
                entry.copy(dispatchedPeerIds = entry.dispatchedPeerIds + peerId)
            )
        }

        if (queuedAny) {
            refreshMessageState("Global LAN listo para $peerDisplayLabel")
            syncWakeLockAndLifetime()
        }
    }

    private fun handleIncomingChatPayload(
        peerId: String,
        ip: String,
        label: String,
        message: String,
        route: PeerRouteObservation
    ) {
        when (val decoded = ChatMessageScopeCodec.decodeFromTransport(message)) {
            is ChatMessageScopeCodec.DecodedChatPayload.User -> {
                if (decoded.scope == ChatMessageScope.DIRECT_CHANNEL) requireDirectChannelSender(_state.value, route)
                if (decoded.scope == ChatMessageScope.DIRECT && !decoded.senderId.isNullOrBlank() && decoded.senderId != peerId) {
                    requireDirectChannelSender(_state.value, route)
                    if (_state.value.directGroup?.isOwner != false) throw SecurityException("grupo_direct_no_acreditado")
                }
                val effectivePeerId = decoded.senderId?.ifBlank { null } ?: peerId
                val effectivePeerLabel = decoded.senderLabel?.ifBlank { null } ?: label
                val effectivePeerIp = decoded.senderIp?.ifBlank { null } ?: ip
                val shouldIgnoreGlobalMessage =
                    decoded.scope == ChatMessageScope.GLOBAL_LAN &&
                        !UxPreferencesStore.load(applicationContext).joinedGlobalLan
                if (shouldIgnoreGlobalMessage) throw SecurityException("canal_no_unido")
                onPeerSeen(
                    peerId = peerId,
                    ip = ip,
                    label = label,
                    trusted = true,
                    globalLanJoined = if (decoded.scope == ChatMessageScope.GLOBAL_LAN) true else null
                )
                if (!shouldIgnoreGlobalMessage) {
                    val chatEntry = ChatMessageStore.newIncoming(
                        text = decoded.text,
                        peerLabel = effectivePeerLabel,
                        peerIp = effectivePeerIp,
                        scope = decoded.scope
                    )
                    ChatMessageStore.append(applicationContext, chatEntry)
                    refreshMessageState("Mensaje recibido de $effectivePeerLabel")
                    _state.update { s ->
                        s.copy(
                            lastPeerIp = effectivePeerIp,
                            lastPeerLabel = effectivePeerLabel
                        )
                    }
                }
            }

            is ChatMessageScopeCodec.DecodedChatPayload.DirectRelayRequest -> {
                onPeerSeen(
                    peerId = peerId,
                    ip = ip,
                    label = label,
                    trusted = true
                )
                require(decoded.senderId == peerId) { "identidad de remitente invalida" }
                relayDirectMessage(decoded.copy(senderLabel = label), senderRoute = route)
            }

            is ChatMessageScopeCodec.DecodedChatPayload.ChannelRelayRequest -> {
                requireDirectRelayTargets(_state.value, route)
                require(decoded.senderId == peerId) { "identidad de remitente invalida" }
                onPeerSeen(
                    peerId = peerId,
                    ip = ip,
                    label = label,
                    trusted = true
                )
                val chatEntry = ChatMessageStore.newIncoming(
                    text = decoded.text,
                    peerLabel = decoded.senderLabel.ifBlank { label },
                    peerIp = ip,
                    scope = ChatMessageScope.DIRECT_CHANNEL
                )
                ChatMessageStore.append(applicationContext, chatEntry)
                refreshMessageState("Mensaje recibido de ${decoded.senderLabel.ifBlank { label }}")
                _state.update { s ->
                    s.copy(
                        lastPeerIp = ip,
                        lastPeerLabel = decoded.senderLabel.ifBlank { label }
                    )
                }
                relayDirectChannelMessage(decoded.copy(senderLabel = label), senderRoute = route)
            }

            is ChatMessageScopeCodec.DecodedChatPayload.DirectRoster -> {
                requireDirectChannelSender(_state.value, route)
                if (_state.value.directGroup?.isOwner != false) throw SecurityException("grupo_direct_no_acreditado")
                // A roster from the owner is not evidence of a direct socket to its listed peers.
                // Clients keep only their platform-reported owner as an automatic Direct destination.
            }

            is ChatMessageScopeCodec.DecodedChatPayload.FileOffer -> {
                handleIncomingChannelFileOffer(
                    peerId = peerId,
                    ip = ip,
                    label = label,
                    offer = decoded.offer
                )
            }

            is ChatMessageScopeCodec.DecodedChatPayload.FileRequest -> {
                handleIncomingChannelFileRequest(
                    peerId = peerId,
                    ip = ip,
                    request = decoded.request
                )
            }
        }
    }

    private fun handleIncomingChannelFileOffer(
        peerId: String,
        ip: String,
        label: String,
        offer: ChannelFileOffer
    ) {
        if (offer.id.isBlank()) return
        if (!UxPreferencesStore.load(applicationContext).joinedGlobalLan) throw SecurityException("canal_no_unido")
        require(offer.senderId == peerId) { "identidad de oferta invalida" }
        val localDeviceId = LocalDeviceIdentity.getOrCreate(applicationContext)
        if (offer.senderId == localDeviceId) return

        val senderIp = ip
        val senderLabel = label.ifBlank { "Equipo" }
        val effectiveOffer = offer.copy(
            senderIp = senderIp,
            senderLabel = senderLabel,
            uri = null
        )
        val preferences = UxPreferencesStore.load(applicationContext)
        if (!preferences.joinedGlobalLan) return

        onPeerSeen(
            peerId = offer.senderId.ifBlank { peerId },
            ip = senderIp,
            label = senderLabel,
            trusted = true,
            globalLanJoined = true
        )
        ChannelFileOfferStore.upsert(applicationContext, effectiveOffer)

        val alreadyVisible = ChatMessageStore.list(applicationContext, limit = 300).any { entry ->
            val decoded = ChatMessageScopeCodec.decodeFromTransport(entry.text)
            decoded is ChatMessageScopeCodec.DecodedChatPayload.FileOffer &&
                decoded.offer.id == effectiveOffer.id
        }
        if (!alreadyVisible) {
            val chatEntry = ChatMessageStore.newIncoming(
                text = ChatMessageScopeCodec.encodeChannelFileOffer(effectiveOffer),
                peerLabel = senderLabel,
                peerIp = senderIp,
                scope = ChatMessageScope.GLOBAL_LAN
            )
            ChatMessageStore.append(applicationContext, chatEntry)
        }

        refreshMessageState("$senderLabel compartió ${effectiveOffer.fileName}.")
        _state.update { s ->
            s.copy(
                lastPeerIp = senderIp,
                lastPeerLabel = senderLabel
            )
        }

        if (preferences.autoDownloadChannelFiles) {
            queueChannelFileOfferRequest(
                offer = effectiveOffer,
                targetIp = senderIp,
                requesterLabel = Build.MODEL.ifBlank { "equipo" }
            )
            pumpMessageQueue()
            syncWakeLockAndLifetime()
        }
    }

    private fun handleIncomingChannelFileRequest(
        peerId: String,
        ip: String,
        request: ChannelFileRequest
    ) {
        if (request.offerId.isBlank()) return
        if (!UxPreferencesStore.load(applicationContext).joinedGlobalLan) throw SecurityException("canal_no_unido")
        require(request.requesterId == peerId) { "identidad de solicitud invalida" }
        val localDeviceId = LocalDeviceIdentity.getOrCreate(applicationContext)
        if (request.requesterId == localDeviceId) return
        val offer = ChannelFileOfferStore.find(applicationContext, request.offerId) ?: return
        if (offer.senderId != localDeviceId) return
        val uriRaw = offer.uri?.takeIf { it.isNotBlank() } ?: return
        val targetIp = ip
        val deviceLabel = offer.senderLabel.ifBlank { "equipo" }

        val intent = Intent(this, TransferForegroundService::class.java).apply {
            action = ACTION_SEND_FILE
            putExtra(EXTRA_FILE_URI, Uri.parse(uriRaw))
            putExtra(EXTRA_FILE_NAME, offer.fileName)
            putExtra(EXTRA_TARGET_IP, targetIp)
            putExtra(EXTRA_TOKEN, currentToken)
            putExtra(EXTRA_PIN, currentPin)
            putExtra(EXTRA_DEVICE_LABEL, deviceLabel)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startSend(intent)
        refreshMessageState("${request.requesterLabel.ifBlank { "Equipo" }} solicitó ${offer.fileName}.")
    }

    private fun queueChannelFileOfferRequest(
        offer: ChannelFileOffer,
        targetIp: String,
        requesterLabel: String,
        requesterIp: String? = null
    ): Boolean {
        val request = ChannelFileRequest(
            offerId = offer.id,
            requesterId = LocalDeviceIdentity.getOrCreate(applicationContext),
            requesterLabel = requesterLabel,
            requesterIp = requesterIp
        )
        return queueSilentMessage(
            targetIp = targetIp,
            peerLabel = offer.senderLabel,
            scope = ChatMessageScope.GLOBAL_LAN,
            message = ChatMessageScopeCodec.encodeChannelFileRequest(request),
            tokenRaw = currentToken,
            pinRaw = currentPin,
            deviceLabel = requesterLabel
        )
    }

    private fun relayDirectMessage(
        payload: ChatMessageScopeCodec.DecodedChatPayload.DirectRelayRequest,
        senderRoute: PeerRouteObservation
    ) {
        val target = requireDirectRelayTargets(_state.value, senderRoute, payload.targetIp, payload.targetPeerId).single()
        queueSilentMessage(
            targetIp = target.ip,
            peerLabel = payload.targetLabel ?: payload.targetIp,
            scope = ChatMessageScope.DIRECT,
            message = ChatMessageScopeCodec.encodeUserPayload(
                textRaw = payload.text,
                scope = ChatMessageScope.DIRECT,
                senderId = payload.senderId,
                senderLabel = payload.senderLabel,
                senderIp = senderRoute.peerIp
            ),
            tokenRaw = currentToken,
            pinRaw = currentPin,
            deviceLabel = "equipo"
        )
        pumpMessageQueue()
        syncWakeLockAndLifetime()
    }

    private fun relayDirectChannelMessage(
        payload: ChatMessageScopeCodec.DecodedChatPayload.ChannelRelayRequest,
        senderRoute: PeerRouteObservation
    ) {
        val encoded = ChatMessageScopeCodec.encodeUserPayload(
            textRaw = payload.text,
            scope = ChatMessageScope.DIRECT_CHANNEL,
            senderId = payload.senderId,
            senderLabel = payload.senderLabel,
            senderIp = senderRoute.peerIp
        )
        val targets = requireDirectRelayTargets(_state.value, senderRoute)

        var queuedAny = false
        targets.forEach { peer ->
            queuedAny = queueSilentMessage(
                targetIp = peer.ip,
                peerLabel = "Canal · ${peer.label.ifBlank { peer.ip }}",
                scope = ChatMessageScope.DIRECT_CHANNEL,
                message = encoded,
                tokenRaw = currentToken,
                pinRaw = currentPin,
                deviceLabel = "equipo"
            ) || queuedAny
        }
        if (queuedAny) {
            pumpMessageQueue()
            syncWakeLockAndLifetime()
        }
    }

    private fun trustPeer(intent: Intent) {
        val pending = _state.value.pendingTrust
        val peerId = intent.getStringExtra(EXTRA_TRUST_PEER_ID).orEmpty().ifBlank { pending?.id.orEmpty() }
        val peerLabel = intent.getStringExtra(EXTRA_TRUST_PEER_LABEL).orEmpty().ifBlank { pending?.label.orEmpty() }

        if (peerId.isBlank()) return

        TrustedPeerStore.trust(applicationContext, peerId, peerLabel)
        deniedCredentialShareUntilMs.remove(peerId)
        credentialRequestPromptedAtMs.remove(peerId)
        val peers = TrustedPeerStore.all(applicationContext)

        val pendingAfter = if (pending?.id == peerId) null else pending
        val pendingCredentialAfter = _state.value.pendingCredentialShare
            ?.takeIf { it.id != peerId }

        val updatedKnown = knownPeersMap.values.map { kp ->
            if (kp.id == peerId) kp.copy(trusted = true) else kp
        }
        knownPeersMap.clear()
        updatedKnown.forEach { knownPeersMap[it.id] = it }

        _state.update {
            it.copy(
                pendingTrust = pendingAfter,
                pendingCredentialShare = pendingCredentialAfter,
                trustedPeers = peers,
                favoritePeers = peers.filter { p -> p.favorite },
                knownPeers = updatedKnown,
                receiverStatus = "Dispositivo confiado: ${peerLabel.ifBlank { peerId }}"
            )
        }
        updateForegroundNotification("Dispositivo confiado")
    }

    private fun forgetPeer(intent: Intent) {
        val peerId = intent.getStringExtra(EXTRA_TRUST_PEER_ID).orEmpty().trim()
        if (peerId.isBlank()) return
        val peer = TrustedPeerStore.all(applicationContext).firstOrNull { it.id == peerId }
        val addresses = (_state.value.knownPeers.filter { it.id == peerId }.map { it.ip } +
            listOfNotNull(peer?.lastKnownIp)).toSet()
        TrustedPeerStore.remove(applicationContext, peerId)
        knownPeersMap.computeIfPresent(peerId) { _, known -> known.copy(trusted = false) }
        val canceledTransfers = synchronized(sendLock) {
            sendSnapshots.values.filter { !it.done && it.targetIp in addresses }.map { it.id }
        }
        canceledTransfers.forEach { transferId ->
            markSendTaskCancelled(transferId, "equipo olvidado")
            synchronized(sendLock) { sendJobs[transferId] }?.cancel(CancellationException("equipo olvidado"))
        }
        val canceledMessages = synchronized(messageLock) {
            val affected = pendingMessageTasks.values.filter { it.targetIp in addresses }
            affected.forEach { pendingMessageTasks.remove(it.id) }
            if (affected.isNotEmpty()) messageSenderJob?.cancel(CancellationException("equipo olvidado"))
            affected
        }
        canceledMessages.forEach { task ->
            PendingMessageStore.remove(applicationContext, task.id)
            if (task.trackChatStatus) ChatMessageStore.updateStatus(
                applicationContext, task.chatMessageId, ChatMessageStatus.CANCELED, "equipo olvidado"
            )
        }
        deniedCredentialShareUntilMs.remove(peerId)
        credentialRequestPromptedAtMs.remove(peerId)
        val peers = TrustedPeerStore.all(applicationContext)
        _state.update { state ->
            revokePeerRouteEvidence(state, peerId, android.os.SystemClock.elapsedRealtime()).copy(
                trustedPeers = peers,
                favoritePeers = peers.filter { it.favorite },
                knownPeers = state.knownPeers.map { if (it.id == peerId) it.copy(trusted = false) else it },
                pendingTrust = state.pendingTrust?.takeUnless { it.id == peerId },
                pendingCredentialShare = state.pendingCredentialShare?.takeUnless { it.id == peerId },
                credentialSharedPeers = state.credentialSharedPeers.filterNot { it.peerId == peerId },
                receiverStatus = "Equipo olvidado. Confirma su identidad para volver a emparejar."
            )
        }
        updateSendAggregateState()
        refreshMessageState()
        updateForegroundNotification()
    }

    private fun reportDiscoveredPeer(intent: Intent) {
        val peerId = intent.getStringExtra(EXTRA_TRUST_PEER_ID).orEmpty().trim()
        val peerLabel = intent.getStringExtra(EXTRA_TRUST_PEER_LABEL).orEmpty().trim()
        val peerIp = intent.getStringExtra(EXTRA_TARGET_IP).orEmpty().trim()
        if (peerId.isBlank() || peerIp.isBlank()) return
        val globalLanJoined = intent.getBooleanExtra(EXTRA_DISCOVERED_GLOBAL_LAN_JOINED, false)
        // Discovery is an unauthenticated hint even if the remote advertises trustedByHost.
        val trusted = false
        onPeerSeen(
            peerId = peerId,
            ip = peerIp,
            label = peerLabel.ifBlank { "peer" },
            trusted = trusted,
            globalLanJoined = globalLanJoined
        )
    }

    private fun reportAuthenticatedPeer(intent: Intent) {
        val peerId = intent.getStringExtra(EXTRA_TRUST_PEER_ID).orEmpty().trim()
        val peerIp = intent.getStringExtra(EXTRA_TARGET_IP).orEmpty().trim()
        val observedKey = intent.getStringExtra(EXTRA_CREDENTIAL_EXPECTED_KEY).orEmpty()
        if (peerId.isBlank() || peerIp.isBlank() || observedKey.isBlank()) return
        // Only a successful encrypted response can register this endpoint; a late report after
        // forgetting or replacing the identity must not recreate trust or group membership.
        val pinnedPeer = TrustedPeerStore.all(applicationContext).firstOrNull { it.id == peerId }
        if (pinnedPeer?.noiseStaticKey != observedKey) return
        onPeerSeen(peerId, peerIp, intent.getStringExtra(EXTRA_TRUST_PEER_LABEL).orEmpty(), trusted = true)
    }

    private fun approveCredentialShare(intent: Intent) {
        val peerId = intent.getStringExtra(EXTRA_TRUST_PEER_ID).orEmpty()
        val expectedKey = intent.getStringExtra(EXTRA_CREDENTIAL_EXPECTED_KEY).orEmpty()
        val requestedAtMs = intent.getLongExtra(EXTRA_CREDENTIAL_REQUESTED_AT, -1L)
        val pending = claimCredentialShareRequest(_state, peerId, expectedKey, requestedAtMs)
        if (pending == null) {
            _state.update { it.copy(receiverStatus = "La solicitud cambió. Revisa la huella del equipo antes de aprobar.") }
            return
        }
        val peerLabel = pending.label.ifBlank { peerId }
        if (!TrustedPeerStore.trustWithNoiseKey(applicationContext, peerId, peerLabel, expectedKey)) {
            _state.update { it.copy(receiverStatus = "La identidad del equipo cambió. Comprueba su huella.") }
            return
        }
        deniedCredentialShareUntilMs.remove(peerId)
        credentialRequestPromptedAtMs.remove(peerId)
        val peers = TrustedPeerStore.all(applicationContext)

        val updatedKnown = knownPeersMap.values.map { kp ->
            if (kp.id == peerId) kp.copy(trusted = true) else kp
        }
        knownPeersMap.clear()
        updatedKnown.forEach { knownPeersMap[it.id] = it }

        _state.update {
            it.copy(
                trustedPeers = peers,
                favoritePeers = peers.filter { p -> p.favorite },
                knownPeers = updatedKnown,
                receiverStatus = "Conexion aprobada para $peerLabel. Vuelve a enviar desde ese equipo."
            )
        }
        updateForegroundNotification("Conexion autorizada")
    }

    private fun rejectCredentialShare(intent: Intent) {
        val pending = _state.value.pendingCredentialShare ?: return
        val peerId = intent.getStringExtra(EXTRA_TRUST_PEER_ID).orEmpty().ifBlank { pending.id }
        deniedCredentialShareUntilMs[peerId] = System.currentTimeMillis() + CREDENTIAL_REQUEST_DENY_COOLDOWN_MS
        credentialRequestPromptedAtMs[peerId] = System.currentTimeMillis()

        _state.update {
            it.copy(
                pendingCredentialShare = if (pending.id == peerId) null else pending,
                receiverStatus = "Solicitud de conexion rechazada para ${pending.label}."
            )
        }
        updateForegroundNotification("Solicitud de conexion rechazada")
    }

    private fun setPeerFavorite(intent: Intent) {
        val peerId = intent.getStringExtra(EXTRA_TRUST_PEER_ID).orEmpty()
        if (peerId.isBlank()) return
        val favorite = intent.getBooleanExtra(EXTRA_FAVORITE_ENABLED, false)
        TrustedPeerStore.setFavorite(applicationContext, peerId, favorite)
        val peers = TrustedPeerStore.all(applicationContext)
        _state.update {
            it.copy(
                trustedPeers = peers,
                favoritePeers = peers.filter { p -> p.favorite },
                receiverStatus = if (favorite) "Peer marcado como favorito." else "Peer removido de favoritos."
            )
        }
    }

    private fun setPeerAlias(intent: Intent) {
        val peerId = intent.getStringExtra(EXTRA_TRUST_PEER_ID).orEmpty()
        if (peerId.isBlank()) return
        val alias = intent.getStringExtra(EXTRA_PEER_ALIAS).orEmpty()
        val normalizeAlias: (String) -> String = { raw ->
            raw.trim().replace(Regex("\\s+"), " ").take(48)
        }
        val currentAlias = TrustedPeerStore.all(applicationContext)
            .firstOrNull { it.id == peerId }
            ?.alias
            .orEmpty()
        if (normalizeAlias(alias) == normalizeAlias(currentAlias)) {
            _state.update { it.copy(receiverStatus = "Apodo sin cambios.") }
            return
        }
        TrustedPeerStore.setAlias(applicationContext, peerId, alias)
        val peers = TrustedPeerStore.all(applicationContext)
        _state.update {
            it.copy(
                trustedPeers = peers,
                favoritePeers = peers.filter { p -> p.favorite },
                receiverStatus = "Apodo actualizado."
            )
        }
    }

    private fun pauseSendQueueItem(intent: Intent) {
        val transferId = intent.getStringExtra(EXTRA_SEND_QUEUE_ITEM_ID).orEmpty()
        if (transferId.isBlank()) return
        synchronized(sendLock) {
            val current = sendSnapshots[transferId] ?: return@synchronized
            if (current.done) return@synchronized
            val nextStatus = when (current.status) {
                SendQueueStatus.QUEUED -> SendQueueStatus.PAUSED
                SendQueueStatus.RUNNING -> SendQueueStatus.PAUSED
                SendQueueStatus.RETRY_WAIT -> SendQueueStatus.PAUSED
                else -> current.status
            }
            sendSnapshots[transferId] = current.copy(
                pauseRequested = true,
                status = nextStatus
            )
        }
        updateSendAggregateState(statusOverride = "Pausado en cola.")
        updateForegroundNotification()
    }

    private fun resumeSendQueueItem(intent: Intent) {
        val transferId = intent.getStringExtra(EXTRA_SEND_QUEUE_ITEM_ID).orEmpty()
        if (transferId.isBlank()) return
        synchronized(sendLock) {
            val current = sendSnapshots[transferId] ?: return@synchronized
            if (current.done) return@synchronized
            val isRunning = sendJobs[transferId]?.isActive == true
            val nextStatus = if (isRunning) SendQueueStatus.RUNNING else SendQueueStatus.QUEUED
            sendSnapshots[transferId] = current.copy(
                pauseRequested = false,
                status = nextStatus
            )
        }
        updateSendAggregateState(statusOverride = "Reanudado en cola.")
        pumpSendQueue()
        updateForegroundNotification()
    }

    private fun cancelSendQueueItem(intent: Intent) {
        val transferId = intent.getStringExtra(EXTRA_SEND_QUEUE_ITEM_ID).orEmpty()
        if (transferId.isBlank()) return
        var shouldAppendHistory = false
        var historyFileName = ""
        var historyPeerLabel: String? = null
        var historyPeerIp = ""
        var historyRoute = ""

        synchronized(sendLock) {
            val current = sendSnapshots[transferId] ?: return@synchronized
            if (current.done) return@synchronized
            val running = sendJobs[transferId]?.isActive == true
            if (running) {
                sendSnapshots[transferId] = current.copy(cancelRequested = true, pauseRequested = false)
            } else {
                sendBatchCanceled += 1
                sendSnapshots[transferId] = current.copy(
                    cancelRequested = true,
                    pauseRequested = false,
                    status = SendQueueStatus.CANCELED,
                    done = true,
                    lastError = "cancelado por usuario"
                )
                val payload = sendPayloads.remove(transferId)
                if (payload != null) {
                    shouldAppendHistory = true
                    historyFileName = payload.fileName
                    historyPeerLabel = payload.peerLabel
                    historyPeerIp = payload.targetIp
                    historyRoute = payload.uri.toString()
                }
            }
        }

        if (shouldAppendHistory) {
            appendHistory(
                TransferHistoryStore.newEntry(
                    direction = TransferDirection.SENT,
                    fileName = historyFileName,
                    bytes = -1L,
                    outcome = TransferOutcome.CANCELED,
                    peerLabel = historyPeerLabel,
                    peerIp = historyPeerIp,
                    route = historyRoute,
                    errorCause = "cancelado por usuario"
                )
            )
        } else {
            synchronized(sendLock) {
                sendJobs[transferId]?.cancel(CancellationException("cancelado por usuario"))
            }
        }

        updateSendAggregateState(statusOverride = "Elemento cancelado.")
        pumpSendQueue()
        updateForegroundNotification()
    }

    private fun moveSendQueueItem(intent: Intent, up: Boolean) {
        val transferId = intent.getStringExtra(EXTRA_SEND_QUEUE_ITEM_ID).orEmpty()
        if (transferId.isBlank()) return

        synchronized(sendLock) {
            val currentOrder = sendSnapshots.values.toMutableList()
            val index = currentOrder.indexOfFirst { it.id == transferId }
            if (index < 0) return@synchronized
            val item = currentOrder[index]
            if (item.done || item.status == SendQueueStatus.RUNNING) return@synchronized

            val swapIndex = if (up) index - 1 else index + 1
            if (swapIndex !in currentOrder.indices) return@synchronized

            val candidate = currentOrder[swapIndex]
            if (candidate.status == SendQueueStatus.RUNNING || candidate.done) return@synchronized

            currentOrder[index] = candidate
            currentOrder[swapIndex] = item

            val reorderedSnapshots = linkedMapOf<String, SendTaskSnapshot>()
            val reorderedPayloads = linkedMapOf<String, SendTaskPayload>()
            currentOrder.forEach { task ->
                reorderedSnapshots[task.id] = task
                sendPayloads[task.id]?.let { payload ->
                    reorderedPayloads[task.id] = payload
                }
            }
            sendSnapshots.clear()
            sendSnapshots.putAll(reorderedSnapshots)
            sendPayloads.clear()
            sendPayloads.putAll(reorderedPayloads)
        }

        updateSendAggregateState(statusOverride = "Cola reordenada.")
        pumpSendQueue()
        updateForegroundNotification()
    }

    private fun setPaused(value: Boolean) {
        pausedTransfers = value
        _state.update {
            it.copy(
                paused = value,
                sendStatus = if (value && it.sending) "Envio en pausa." else it.sendStatus,
                receiverStatus = if (value && it.receiving) "Receptor en pausa." else it.receiverStatus
            )
        }
        updateForegroundNotification()
    }

    private fun requestCancelActiveTransfer() {
        receiveCancellationGeneration.incrementAndGet()
        cancelCurrentTransfer = true
        // A paused receiver must leave its pause loop to release the file mutex and client slot.
        pausedTransfers = false
        synchronized(sendLock) {
            sendSnapshots.values.toList().forEach { task ->
                if (task.done) return@forEach
                val running = sendJobs[task.id]?.isActive == true
                if (running) {
                    sendSnapshots[task.id] = task.copy(cancelRequested = true, pauseRequested = false)
                } else {
                    sendBatchCanceled += 1
                    sendSnapshots[task.id] = task.copy(
                        cancelRequested = true,
                        pauseRequested = false,
                        status = SendQueueStatus.CANCELED,
                        done = true,
                        lastError = "cancelado por usuario"
                    )
                    sendPayloads.remove(task.id)
                }
            }
        }
        cancelAllSendJobs(CancellationException("cancelado por usuario"))

        _state.update {
            it.copy(
                paused = false,
                sendStatus = if (it.sending) "Cancelando envios..." else it.sendStatus,
                receiverStatus = if (it.receiving) "Cancelando transferencia recibida..." else it.receiverStatus
            )
        }
        updateSendAggregateState()
        updateForegroundNotification("Cancelando transferencia...")

        serviceScope.launch {
            delay(1_500)
            cancelCurrentTransfer = false
        }
    }

    private fun openDownloadsFolder() {
        try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            // ignored
        }
    }

    private suspend fun awaitIfPaused() {
        while (pausedTransfers) {
            currentCoroutineContext().ensureActive()
            delay(180)
        }
    }

    private fun appendHistory(entry: TransferHistoryEntry) {
        TransferHistoryStore.append(applicationContext, entry)
        _state.update { it.copy(history = TransferHistoryStore.list(applicationContext)) }
    }

    private fun findPeerLabelByIp(ip: String): String? {
        return _state.value.knownPeers.firstOrNull { it.ip == ip }?.label
    }

    private fun cancelAllSendJobs(
        cause: CancellationException = CancellationException("cancelado")
    ) {
        val jobs = synchronized(sendLock) { sendJobs.toMap() }
        jobs.keys.forEach { markSendTaskCancelled(it, cause.message ?: "cancelado") }
        jobs.values.forEach { it.cancel(cause) }
    }

    private fun startMessageTicker() {
        messageTickerJob?.cancel()
        messageTickerJob = serviceScope.launch {
            while (isActive) {
                pumpMessageQueue()
                delay(1_000)
            }
        }
    }

    private fun pumpMessageQueue() {
        if (shuttingDown || !UxPreferencesStore.load(applicationContext).sessionEnabled) return
        val job = withSessionWorkerAdmission(messageLock, {
            shuttingDown || !UxPreferencesStore.load(applicationContext).sessionEnabled
        }) {
            if (messageSenderJob?.isCompleted == false) return
            val now = System.currentTimeMillis()
            val candidate = pendingMessageTasks.values
                .filter { it.nextAttemptAtMs <= now }
                .minByOrNull { it.nextAttemptAtMs } ?: return
            serviceScope.launch(start = CoroutineStart.LAZY) {
                runPendingMessageTask(candidate.id)
            }.also { admittedJob ->
                messageSenderJob = admittedJob
                admittedJob.invokeOnCompletion {
                    synchronized(messageLock) {
                        if (messageSenderJob === admittedJob) messageSenderJob = null
                    }
                    refreshMessageState()
                    syncWakeLockAndLifetime()
                    pumpMessageQueue()
                }
            }
        } ?: return
        job.start()
        syncWakeLockAndLifetime()
    }

    private suspend fun runPendingMessageTask(taskId: String) {
        val task = synchronized(messageLock) { pendingMessageTasks[taskId] } ?: return
        if (task.trackChatStatus) {
            ChatMessageStore.updateStatus(
                applicationContext,
                messageIdRaw = task.chatMessageId,
                status = ChatMessageStatus.SENDING,
                errorCause = null
            )
        }
        refreshMessageState("Enviando mensaje a ${task.peerLabel ?: task.targetIp}...")

        val directDelivery = requiresDirectGroupDelivery(task.message, task.scope, task.clientId)
        val groupAtSend = _state.value.directGroup
        val validationError = runCatching { validateDirectGroupDelivery(task, _state.value) }.exceptionOrNull()
        val result = if (validationError != null) Result.failure(validationError) else FileTransfer.sendMessage(
            context = applicationContext,
            hostAddress = task.targetIp,
            expectedPeerId = task.expectedPeerId ?: _state.value.knownPeers.firstOrNull { it.ip == task.targetIp }?.id,
            tokenRaw = task.token,
            pinRaw = task.pin,
            clientIdRaw = task.clientId,
            clientLabelRaw = task.deviceLabel,
            messageRaw = ChatMessageScopeCodec.encodeForTransport(task.message, task.scope),
            messageId = task.id,
            localBindAddress = if (directDelivery) {
                if (groupAtSend?.isOwner == true) groupAtSend.ownerIp else groupAtSend?.localAddresses?.sorted()?.firstOrNull()
            } else null,
            validateDestination = { validateDirectGroupDelivery(task, _state.value) }
        )

        result.fold(
            onSuccess = {
                synchronized(messageLock) {
                    pendingMessageTasks.remove(taskId)
                }
                PendingMessageStore.remove(applicationContext, taskId)
                if (task.trackChatStatus) {
                    ChatMessageStore.updateStatus(
                        applicationContext,
                        messageIdRaw = task.chatMessageId,
                        status = ChatMessageStatus.SENT,
                        errorCause = null
                    )
                }
                LastSendTargetStore.set(
                    applicationContext,
                    ipRaw = task.targetIp,
                    labelRaw = task.peerLabel ?: task.targetIp
                )
                _state.update {
                    it.copy(
                        lastSendTargetIp = task.targetIp,
                        lastSendTargetLabel = task.peerLabel ?: task.targetIp,
                        lastSendTargetAtMs = System.currentTimeMillis()
                    )
                }
                refreshMessageState("Mensaje enviado a ${task.peerLabel ?: task.targetIp}")
                updateForegroundNotification("Mensaje enviado")
            },
            onFailure = { error ->
                val cause = error.message ?: error::class.java.simpleName
                val retryable = isRetryableSendError(error)
                val now = System.currentTimeMillis()
                val maxRetries = minOf(task.maxRetries, MAX_MESSAGE_RETRIES)
                val retryDeadlineAtMs = task.createdAtMs + MAX_MESSAGE_RETRY_AGE_MS
                val withinRetryWindow = now < retryDeadlineAtMs
                val hasRetriesLeft = task.retriesUsed < maxRetries
                val willRetry = retryable && withinRetryWindow && hasRetriesLeft

                if (willRetry) {
                    val nextRetries = task.retriesUsed + 1
                    val retryDelayMs = (2_000L * nextRetries).coerceAtMost(45_000L)
                    val nextAttemptAt = (now + retryDelayMs).coerceAtMost(retryDeadlineAtMs)
                    val nextTask = task.copy(
                        retriesUsed = nextRetries,
                        nextAttemptAtMs = nextAttemptAt,
                        lastError = cause
                    )
                    synchronized(messageLock) {
                        pendingMessageTasks[taskId] = nextTask
                    }
                    PendingMessageStore.upsert(applicationContext, nextTask)
                    if (task.trackChatStatus) {
                        ChatMessageStore.updateStatus(
                            applicationContext,
                            messageIdRaw = task.chatMessageId,
                            status = ChatMessageStatus.QUEUED,
                            errorCause = cause
                        )
                    }
                    refreshMessageState(
                        "Sin conexion a ${task.peerLabel ?: task.targetIp}. Reintento en ${(nextAttemptAt - now).coerceAtLeast(0L) / 1000}s."
                    )
                    updateForegroundNotification("Mensaje en cola")
                } else {
                    synchronized(messageLock) {
                        pendingMessageTasks.remove(taskId)
                    }
                    PendingMessageStore.remove(applicationContext, taskId)
                    val finalCause = when {
                        !retryable -> cause
                        !withinRetryWindow -> "vencido: mas de 24h en cola"
                        !hasRetriesLeft -> "agotado: maximo 4 reintentos"
                        else -> cause
                    }
                    if (task.trackChatStatus) {
                        ChatMessageStore.updateStatus(
                            applicationContext,
                            messageIdRaw = task.chatMessageId,
                            status = ChatMessageStatus.FAILED,
                            errorCause = finalCause
                        )
                    }
                    refreshMessageState("Error enviando mensaje: $finalCause")
                    updateForegroundNotification("Error de mensaje")
                }
            }
        )
    }

    private fun refreshMessageState(statusOverride: String? = null) {
        val pending = synchronized(messageLock) { pendingMessageTasks.values.sortedBy { it.createdAtMs } }
        val pendingCount = pending.size
        val now = System.currentTimeMillis()
        val nextDelaySec = pending.minOfOrNull { task ->
            ((task.nextAttemptAtMs - now).coerceAtLeast(0L) / 1000L)
        }

        _state.update { current ->
            val computed = statusOverride ?: when {
                pendingCount > 0 && nextDelaySec != null && nextDelaySec > 0L ->
                    "Mensajes pendientes: $pendingCount · reintento en ${nextDelaySec}s"

                pendingCount > 0 -> "Mensajes pendientes: $pendingCount"
                else -> current.messageStatus.takeIf { it.isNotBlank() } ?: ""
            }

            current.copy(
                chatMessages = ChatMessageStore.list(applicationContext),
                messageStatus = computed,
                pendingMessageCount = pendingCount
            )
        }
    }

    private fun stopSession() {
        startForeground(NOTIFICATION_ID, buildNotification("Cerrando sesion..."))
        val ticket = sessionLifecycle.beginStop()
        shuttingDown = true
        // Close admission before taking either lock. The only nested queue-lock order is
        // sendLock -> messageLock; queue callbacks release their lock before checking the other.
        // A child and its completion callback are registered atomically with this snapshot.
        val drainingWorkers = synchronized(sendLock) {
            synchronized(messageLock) {
                serviceScope.coroutineContext[Job]?.children?.toList().orEmpty()
            }
        }
        cancelAllSendJobs(CancellationException("sesion detenida"))
        drainingWorkers.forEach { it.cancel(CancellationException("sesion detenida")) }
        receiverJob = null
        messageSenderJob = null
        messageTickerJob = null
        synchronized(sendLock) {
            sendJobs.clear()
            sendSnapshots.clear()
            sendPayloads.clear()
            sendBatchTotal = 0
            sendBatchCompleted = 0
            sendBatchFailed = 0
            sendBatchCanceled = 0
        }

        currentToken = ""
        currentPin = ""
        currentSessionExpiresAtMs = 0L
        receiveDir = null
        knownPeersMap.clear()
        pausedTransfers = false
        cancelCurrentTransfer = false
        deniedCredentialShareUntilMs.clear()
        credentialRequestPromptedAtMs.clear()
        val trusted = TrustedPeerStore.all(applicationContext)
        val lastTarget = LastSendTargetStore.get(applicationContext)
        val pending = PendingMessageStore.list(applicationContext)

        _state.update {
            it.copy(
                receiving = false,
                receiverListening = false,
                receiverStatus = "Sesion detenida.",
                receiverProgress = null,
                receiverFileName = null,
                receiverInstantBps = 0L,
                receiverAverageBps = 0L,
                receiverEtaSeconds = null,
                sending = false,
                sendProgress = 0f,
                sendStatus = "",
                sendInstantBps = 0L,
                sendAverageBps = 0L,
                sendEtaSeconds = null,
                sendFailureCause = null,
                sendActiveCount = 0,
                sendBatchTotal = 0,
                sendBatchCompleted = 0,
                sendBatchFailed = 0,
                sendBatchCanceled = 0,
                sendQueue = emptyList(),
                activeToken = null,
                sessionExpiresAtMs = null,
                knownPeers = emptyList(),
                pendingTrust = null,
                pendingCredentialShare = null,
                credentialSharedPeers = emptyList(),
                paused = false,
                trustedPeers = trusted,
                favoritePeers = trusted.filter { p -> p.favorite },
                history = TransferHistoryStore.list(applicationContext),
                chatMessages = ChatMessageStore.list(applicationContext),
                pendingMessageCount = pending.size,
                messageStatus = if (pending.isNotEmpty()) {
                    "Mensajes pendientes: ${pending.size}"
                } else {
                    ""
                },
                lastSendTargetIp = lastTarget?.ip,
                lastSendTargetLabel = lastTarget?.label,
                lastSendTargetAtMs = lastTarget?.updatedAtMs
            )
        }

        releaseWakeLock()
        updateForegroundNotification("Sesion detenida")
        stopForeground(STOP_FOREGROUND_REMOVE)
        sessionStopBarrier = serviceScope.launch(start = CoroutineStart.LAZY) {
            drainingWorkers.forEach { it.join() }
            withContext(Dispatchers.Main.immediate) {
                // A later START invalidates this ticket. A command queued by Android but not yet
                // delivered is also protected by stopSelfResult's framework start-id check.
                sessionLifecycle.stopStartId(ticket)?.let { stopStartId ->
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(stopStartId)
                }
            }
        }.also { it.start() }
    }

    private fun syncWakeLockAndLifetime() {
        val hasActiveSend = synchronized(sendLock) { sendJobs.values.any { it.isActive } }
        val hasActiveMessageSend = synchronized(messageLock) { messageSenderJob?.isActive == true }
        val shouldHold = receiverJob?.isActive == true || hasActiveSend || hasActiveMessageSend
        if (shouldHold) {
            ensureWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    private fun ensureWakeLock() {
        if (wakeLock?.isHeld == true) return

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { wl ->
            if (wl.isHeld) {
                wl.release()
            }
        }
        wakeLock = null
    }

    private fun readUriFromIntent(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_FILE_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_FILE_URI)
        }
    }

    private fun updateForegroundNotification(overrideText: String? = null) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(overrideText))
    }

    private fun buildNotification(overrideText: String? = null): Notification {
        val state = _state.value
        val openAppIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = buildTransferNotificationText(state, overrideText)

        val pauseOrResumeAction = if (state.paused) {
            NotificationCompat.Action(
                0,
                "Reanudar",
                servicePendingIntent(ACTION_RESUME_TRANSFERS, 21)
            )
        } else {
            NotificationCompat.Action(
                0,
                "Pausar",
                servicePendingIntent(ACTION_PAUSE_TRANSFERS, 22)
            )
        }

        val cancelAction = NotificationCompat.Action(
            0,
            "Cancelar",
            servicePendingIntent(ACTION_CANCEL_ACTIVE, 23)
        )

        val downloadsAction = NotificationCompat.Action(
            0,
            "Descargas",
            servicePendingIntent(ACTION_OPEN_DOWNLOADS, 24)
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Qetara activo")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(pauseOrResumeAction)
            .addAction(downloadsAction)

        if (state.sending || state.receiving) {
            builder.addAction(cancelAction)
        }

        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TransferForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Transferencias Qetara",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Estado de transferencia Wi-Fi Direct"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "wifidrop_transfer_channel"
        private const val NOTIFICATION_ID = 7301
        private const val WAKE_LOCK_TAG = "WifiDrop::TransferWakeLock"
        private const val WAKELOCK_TIMEOUT_MS = 2 * 60 * 60 * 1000L
        private const val MAX_PARALLEL_SENDS = 2
        private const val MAX_SEND_RETRIES = 2
        private const val SHORT_RETRY_BASE_DELAY_MS = 650L
        private const val MAX_MESSAGE_RETRIES = 4
        private const val MAX_MESSAGE_RETRY_AGE_MS = 24 * 60 * 60 * 1000L
        private const val CREDENTIAL_REQUEST_PROMPT_DEBOUNCE_MS = 4_000L
        private const val CREDENTIAL_REQUEST_DENY_COOLDOWN_MS = 20_000L

        private const val ACTION_START_SESSION = "com.example.wifidrop.action.START_SESSION"
        private const val ACTION_STOP_SESSION = "com.example.wifidrop.action.STOP_SESSION"
        private const val ACTION_SEND_FILE = "com.example.wifidrop.action.SEND_FILE"
        private const val ACTION_SEND_MESSAGE = "com.example.wifidrop.action.SEND_MESSAGE"
        private const val ACTION_SEND_GLOBAL_MESSAGE = "com.example.wifidrop.action.SEND_GLOBAL_MESSAGE"
        private const val ACTION_SEND_SILENT_MESSAGE = "com.example.wifidrop.action.SEND_SILENT_MESSAGE"
        private const val ACTION_RETRY_MESSAGE = "com.example.wifidrop.action.RETRY_MESSAGE"
        private const val ACTION_CANCEL_PENDING_MESSAGE = "com.example.wifidrop.action.CANCEL_PENDING_MESSAGE"
        private const val ACTION_DELETE_CHAT_MESSAGE = "com.example.wifidrop.action.DELETE_CHAT_MESSAGE"
        private const val ACTION_CLEAR_CHAT_MESSAGES = "com.example.wifidrop.action.CLEAR_CHAT_MESSAGES"
        private const val ACTION_PAUSE_TRANSFERS = "com.example.wifidrop.action.PAUSE_TRANSFERS"
        private const val ACTION_RESUME_TRANSFERS = "com.example.wifidrop.action.RESUME_TRANSFERS"
        private const val ACTION_CANCEL_ACTIVE = "com.example.wifidrop.action.CANCEL_ACTIVE"
        private const val ACTION_OPEN_DOWNLOADS = "com.example.wifidrop.action.OPEN_DOWNLOADS"
        private const val ACTION_TRUST_PEER = "com.example.wifidrop.action.TRUST_PEER"
        private const val ACTION_FORGET_PEER = "com.example.wifidrop.action.FORGET_PEER"
        private const val ACTION_REPORT_DISCOVERED_PEER = "com.example.wifidrop.action.REPORT_DISCOVERED_PEER"
        private const val ACTION_REPORT_AUTHENTICATED_PEER = "com.example.wifidrop.action.REPORT_AUTHENTICATED_PEER"
        private const val ACTION_APPROVE_CREDENTIAL_SHARE = "com.example.wifidrop.action.APPROVE_CREDENTIAL_SHARE"
        private const val ACTION_REJECT_CREDENTIAL_SHARE = "com.example.wifidrop.action.REJECT_CREDENTIAL_SHARE"
        private const val ACTION_SET_PEER_FAVORITE = "com.example.wifidrop.action.SET_PEER_FAVORITE"
        private const val ACTION_SET_PEER_ALIAS = "com.example.wifidrop.action.SET_PEER_ALIAS"
        private const val ACTION_SEND_QUEUE_PAUSE_ITEM = "com.example.wifidrop.action.SEND_QUEUE_PAUSE_ITEM"
        private const val ACTION_SEND_QUEUE_RESUME_ITEM = "com.example.wifidrop.action.SEND_QUEUE_RESUME_ITEM"
        private const val ACTION_SEND_QUEUE_CANCEL_ITEM = "com.example.wifidrop.action.SEND_QUEUE_CANCEL_ITEM"
        private const val ACTION_SEND_QUEUE_MOVE_UP = "com.example.wifidrop.action.SEND_QUEUE_MOVE_UP"
        private const val ACTION_SEND_QUEUE_MOVE_DOWN = "com.example.wifidrop.action.SEND_QUEUE_MOVE_DOWN"

        private val SESSION_NETWORK_ACTIONS = setOf(
            ACTION_START_SESSION, ACTION_SEND_FILE, ACTION_SEND_MESSAGE, ACTION_SEND_GLOBAL_MESSAGE,
            ACTION_SEND_SILENT_MESSAGE, ACTION_RETRY_MESSAGE, ACTION_RESUME_TRANSFERS,
            ACTION_SEND_QUEUE_RESUME_ITEM, ACTION_REPORT_DISCOVERED_PEER,
            ACTION_REPORT_AUTHENTICATED_PEER, ACTION_APPROVE_CREDENTIAL_SHARE
        )

        private const val EXTRA_TOKEN = "extra_token"
        private const val EXTRA_PIN = "extra_pin"
        private const val EXTRA_SESSION_EXPIRES_AT_MS = "extra_session_expires_at_ms"
        private const val EXTRA_RECEIVE_DIR = "extra_receive_dir"
        private const val EXTRA_FILE_URI = "extra_file_uri"
        private const val EXTRA_FILE_NAME = "extra_file_name"
        private const val EXTRA_TARGET_IP = "extra_target_ip"
        private const val EXTRA_DEVICE_LABEL = "extra_device_label"
        private const val EXTRA_MESSAGE_TEXT = "extra_message_text"
        private const val EXTRA_PEER_LABEL_OVERRIDE = "extra_peer_label_override"
        private const val EXTRA_MESSAGE_SCOPE = "extra_message_scope"
        private const val EXTRA_CHANNEL_LABEL = "extra_channel_label"
        private const val EXTRA_TARGET_IPS = "extra_target_ips"
        private const val EXTRA_TARGET_LABELS = "extra_target_labels"
        private const val EXTRA_TARGET_PEER_IDS = "extra_target_peer_ids"
        private const val MAX_GLOBAL_LAN_RETROACTIVE_AGE_MS = 24 * 60 * 60 * 1000L
        private const val EXTRA_MESSAGE_ID = "extra_message_id"
        private const val EXTRA_TRUST_PEER_ID = "extra_trust_peer_id"
        private const val EXTRA_TRUST_PEER_LABEL = "extra_trust_peer_label"
        private const val EXTRA_FAVORITE_ENABLED = "extra_favorite_enabled"
        private const val EXTRA_DISCOVERED_TRUSTED = "extra_discovered_trusted"
        private const val EXTRA_DISCOVERED_GLOBAL_LAN_JOINED = "extra_discovered_global_lan_joined"
        private const val EXTRA_PEER_ALIAS = "extra_peer_alias"
        private const val EXTRA_CREDENTIAL_EXPECTED_KEY = "extra_credential_expected_key"
        private const val EXTRA_CREDENTIAL_REQUESTED_AT = "extra_credential_requested_at"
        private const val EXTRA_SEND_QUEUE_ITEM_ID = "extra_send_queue_item_id"

        private val _state = MutableStateFlow(TransferRuntimeState())
        val state: StateFlow<TransferRuntimeState> = _state.asStateFlow()

        fun startSession(
            context: Context,
            token: String,
            pin: String,
            sessionExpiresAtMs: Long,
            receiveDirPath: String
        ) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_START_SESSION
                putExtra(EXTRA_TOKEN, token)
                putExtra(EXTRA_PIN, pin)
                putExtra(EXTRA_SESSION_EXPIRES_AT_MS, sessionExpiresAtMs)
                putExtra(EXTRA_RECEIVE_DIR, receiveDirPath)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopSession(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_STOP_SESSION
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun sendFile(
            context: Context,
            fileUri: Uri,
            fileName: String,
            targetIp: String,
            token: String,
            pin: String,
            deviceLabel: String
        ) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_SEND_FILE
                putExtra(EXTRA_FILE_URI, fileUri)
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_TARGET_IP, targetIp)
                putExtra(EXTRA_TOKEN, token)
                putExtra(EXTRA_PIN, pin)
                putExtra(EXTRA_DEVICE_LABEL, deviceLabel)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun sendMessage(
            context: Context,
            targetIp: String,
            token: String,
            pin: String,
            deviceLabel: String,
            message: String,
            peerLabelOverride: String? = null,
            scope: ChatMessageScope = ChatMessageScope.DIRECT
        ) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_SEND_MESSAGE
                putExtra(EXTRA_TARGET_IP, targetIp)
                putExtra(EXTRA_TOKEN, token)
                putExtra(EXTRA_PIN, pin)
                putExtra(EXTRA_DEVICE_LABEL, deviceLabel)
                putExtra(EXTRA_MESSAGE_TEXT, message)
                putExtra(EXTRA_MESSAGE_SCOPE, scope.name)
                if (!peerLabelOverride.isNullOrBlank()) {
                    putExtra(EXTRA_PEER_LABEL_OVERRIDE, peerLabelOverride)
                }
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun sendBroadcastMessage(
            context: Context,
            token: String,
            pin: String,
            deviceLabel: String,
            message: String,
            scope: ChatMessageScope,
            channelLabel: String,
            targetPeerIds: List<String>,
            targetIps: List<String>,
            targetLabels: List<String>
        ) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_SEND_GLOBAL_MESSAGE
                putExtra(EXTRA_TOKEN, token)
                putExtra(EXTRA_PIN, pin)
                putExtra(EXTRA_DEVICE_LABEL, deviceLabel)
                putExtra(EXTRA_MESSAGE_TEXT, message)
                putExtra(EXTRA_MESSAGE_SCOPE, scope.name)
                putExtra(EXTRA_CHANNEL_LABEL, channelLabel)
                putStringArrayListExtra(EXTRA_TARGET_PEER_IDS, ArrayList(targetPeerIds))
                putStringArrayListExtra(EXTRA_TARGET_IPS, ArrayList(targetIps))
                putStringArrayListExtra(EXTRA_TARGET_LABELS, ArrayList(targetLabels))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun sendSilentMessage(
            context: Context,
            targetIp: String,
            token: String,
            pin: String,
            deviceLabel: String,
            message: String,
            peerLabelOverride: String? = null,
            scope: ChatMessageScope = ChatMessageScope.DIRECT
        ) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_SEND_SILENT_MESSAGE
                putExtra(EXTRA_TARGET_IP, targetIp)
                putExtra(EXTRA_TOKEN, token)
                putExtra(EXTRA_PIN, pin)
                putExtra(EXTRA_DEVICE_LABEL, deviceLabel)
                putExtra(EXTRA_MESSAGE_TEXT, message)
                putExtra(EXTRA_MESSAGE_SCOPE, scope.name)
                if (!peerLabelOverride.isNullOrBlank()) {
                    putExtra(EXTRA_PEER_LABEL_OVERRIDE, peerLabelOverride)
                }
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun requestChannelFileOffer(
            context: Context,
            offer: ChannelFileOffer,
            targetIp: String,
            token: String,
            pin: String,
            deviceLabel: String
        ) {
            val request = ChannelFileRequest(
                offerId = offer.id,
                requesterId = LocalDeviceIdentity.getOrCreate(context.applicationContext),
                requesterLabel = deviceLabel,
                requesterIp = null
            )
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_SEND_SILENT_MESSAGE
                putExtra(EXTRA_TARGET_IP, targetIp)
                putExtra(EXTRA_TOKEN, token)
                putExtra(EXTRA_PIN, pin)
                putExtra(EXTRA_DEVICE_LABEL, deviceLabel)
                putExtra(EXTRA_MESSAGE_TEXT, ChatMessageScopeCodec.encodeChannelFileRequest(request))
                putExtra(EXTRA_MESSAGE_SCOPE, ChatMessageScope.GLOBAL_LAN.name)
                if (offer.senderLabel.isNotBlank()) {
                    putExtra(EXTRA_PEER_LABEL_OVERRIDE, offer.senderLabel)
                }
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun retryMessage(
            context: Context,
            messageId: String,
            deviceLabel: String
        ) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_RETRY_MESSAGE
                putExtra(EXTRA_MESSAGE_ID, messageId)
                putExtra(EXTRA_DEVICE_LABEL, deviceLabel)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancelPendingMessage(
            context: Context,
            messageId: String
        ) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_CANCEL_PENDING_MESSAGE
                putExtra(EXTRA_MESSAGE_ID, messageId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun deleteChatMessage(
            context: Context,
            messageId: String
        ) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_DELETE_CHAT_MESSAGE
                putExtra(EXTRA_MESSAGE_ID, messageId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun clearChatMessages(
            context: Context,
            scope: ChatMessageScope? = null
        ) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_CLEAR_CHAT_MESSAGES
                if (scope != null) {
                    putExtra(EXTRA_MESSAGE_SCOPE, scope.name)
                }
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pauseTransfers(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_PAUSE_TRANSFERS
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun resumeTransfers(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_RESUME_TRANSFERS
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancelActive(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_CANCEL_ACTIVE
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun openDownloads(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_OPEN_DOWNLOADS
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun trustPeer(context: Context, peerId: String, peerLabel: String) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_TRUST_PEER
                putExtra(EXTRA_TRUST_PEER_ID, peerId)
                putExtra(EXTRA_TRUST_PEER_LABEL, peerLabel)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun forgetPeer(context: Context, peerId: String) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_FORGET_PEER
                putExtra(EXTRA_TRUST_PEER_ID, peerId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun reportDiscoveredPeer(
            context: Context,
            peerId: String,
            peerLabel: String,
            peerIp: String,
            trusted: Boolean = false,
            globalLanJoined: Boolean = false
        ) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_REPORT_DISCOVERED_PEER
                putExtra(EXTRA_TRUST_PEER_ID, peerId)
                putExtra(EXTRA_TRUST_PEER_LABEL, peerLabel)
                putExtra(EXTRA_TARGET_IP, peerIp)
                putExtra(EXTRA_DISCOVERED_TRUSTED, trusted)
                putExtra(EXTRA_DISCOVERED_GLOBAL_LAN_JOINED, globalLanJoined)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        internal fun reportAuthenticatedPeer(
            context: Context, peerId: String, peerLabel: String, peerIp: String, noiseStaticKey: String
        ) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_REPORT_AUTHENTICATED_PEER
                putExtra(EXTRA_TRUST_PEER_ID, peerId)
                putExtra(EXTRA_TRUST_PEER_LABEL, peerLabel)
                putExtra(EXTRA_TARGET_IP, peerIp)
                putExtra(EXTRA_CREDENTIAL_EXPECTED_KEY, noiseStaticKey)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun approveCredentialShare(
            context: Context, peerId: String, peerLabel: String,
            expectedNoiseStaticKey: String, requestedAtMs: Long
        ) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_APPROVE_CREDENTIAL_SHARE
                putExtra(EXTRA_TRUST_PEER_ID, peerId)
                putExtra(EXTRA_TRUST_PEER_LABEL, peerLabel)
                putExtra(EXTRA_CREDENTIAL_EXPECTED_KEY, expectedNoiseStaticKey)
                putExtra(EXTRA_CREDENTIAL_REQUESTED_AT, requestedAtMs)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun rejectCredentialShare(context: Context, peerId: String) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_REJECT_CREDENTIAL_SHARE
                putExtra(EXTRA_TRUST_PEER_ID, peerId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun setPeerFavorite(context: Context, peerId: String, favorite: Boolean) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_SET_PEER_FAVORITE
                putExtra(EXTRA_TRUST_PEER_ID, peerId)
                putExtra(EXTRA_FAVORITE_ENABLED, favorite)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun setPeerAlias(context: Context, peerId: String, alias: String) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_SET_PEER_ALIAS
                putExtra(EXTRA_TRUST_PEER_ID, peerId)
                putExtra(EXTRA_PEER_ALIAS, alias)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pauseQueueItem(context: Context, transferId: String) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_SEND_QUEUE_PAUSE_ITEM
                putExtra(EXTRA_SEND_QUEUE_ITEM_ID, transferId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun resumeQueueItem(context: Context, transferId: String) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_SEND_QUEUE_RESUME_ITEM
                putExtra(EXTRA_SEND_QUEUE_ITEM_ID, transferId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancelQueueItem(context: Context, transferId: String) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_SEND_QUEUE_CANCEL_ITEM
                putExtra(EXTRA_SEND_QUEUE_ITEM_ID, transferId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun moveQueueItemUp(context: Context, transferId: String) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_SEND_QUEUE_MOVE_UP
                putExtra(EXTRA_SEND_QUEUE_ITEM_ID, transferId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun moveQueueItemDown(context: Context, transferId: String) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_SEND_QUEUE_MOVE_DOWN
                putExtra(EXTRA_SEND_QUEUE_ITEM_ID, transferId)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
