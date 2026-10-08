package com.example.wifidrop

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.wifidrop.protocol.sanitizeFileName
import com.example.wifidrop.protocol.sanitizePeerLabel
import com.example.wifidrop.protocol.flash.*
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap

/** Separate, opt-in receiver. No normal-session settings, identities or grants are reused. */
class FlashForegroundService : Service() {
    // Always enqueue engine callbacks: a synchronous send callback must not overtake earlier IO callbacks.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val fence = FlashAndroidSessionFence()
    private var engine: FlashEngine? = null
    private var startupJob: Job? = null
    private val pendingEngine = AtomicReference<FlashEngine?>()
    private var ticket = 0L
    private var importJob: Job? = null
    private val importStream = AtomicReference<InputStream?>()
    private val exportJobs = mutableListOf<Job>()
    private val outgoingBatch = FlashOutgoingBatch()
    private val operationNames = ConcurrentHashMap<String, String>()
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastStartId = 0
    private var stopping = false

    override fun onCreate() {
        super.onCreate()
        current = this
        updateNotificationChannel()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateNotificationChannel()
        if (ticket != 0L && !stopping) refreshNotification()
    }

    private fun updateNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.flash_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = getString(R.string.flash_channel_description) }
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_START -> {
                showForeground()
                if (engine == null && !stopping && ticket == 0L) {
                    startFlash(intent.getStringExtra(EXTRA_LABEL).orEmpty())
                }
            }
            ACTION_STOP -> stopFlash(flashText(R.string.flash_stopped))
            else -> if (engine == null) stopSelfResult(startId)
        }
        // Process death must never make a previous activation discoverable again.
        return START_NOT_STICKY
    }

    private fun startFlash(rawLabel: String) {
        ticket = fence.activate()
        val activation = ticket
        FlashAndroidRuntime.update {
            it.copy(phase = FlashAndroidPhase.STARTING, statusText = flashText(R.string.flash_activating), engine = null,
                selectedPeer = null, progress = emptyMap(), deviceLabel = sanitizePeerLabel(rawLabel.ifBlank { Build.MODEL }).take(60))
        }
        startupJob = scope.launch {
            try {
                val started = withContext(Dispatchers.IO) {
                    val folder = File(getExternalFilesDir(null) ?: filesDir, "WifiDropReceived").apply { mkdirs() }
                    val label = sanitizePeerLabel(rawLabel.ifBlank { Build.MODEL }).take(60)
                    val created = FlashEngine(label, folder, listener(activation))
                    pendingEngine.set(created)
                    try {
                        created.start()
                        check(fence.accepts(activation))
                        created
                    } catch (error: Throwable) {
                        created.stop()
                        throw error
                    } finally {
                        if (!fence.accepts(activation)) created.stop()
                    }
                }
                if (!fence.accepts(activation)) {
                    withContext(Dispatchers.IO) { started.stop() }
                    return@launch
                }
                engine = started
                pendingEngine.compareAndSet(started, null)
                acquireNetworkLocks()
                val localAddresses = withContext(Dispatchers.IO) { localAddresses() }
                FlashAndroidRuntime.update {
                    it.copy(phase = FlashAndroidPhase.ACTIVE, engine = started.snapshot(),
                        statusText = flashText(R.string.flash_active), localAddresses = localAddresses)
                }
                refreshNotification()
                started.discover()
                while (isActive && fence.accepts(activation)) {
                    delay(1_000)
                    val snapshot = started.snapshot()
                    if (!snapshot.active || snapshot.expiresAtMs <= System.currentTimeMillis()) {
                        stopFlash(flashText(R.string.flash_session_expired))
                        break
                    }
                }
            } catch (error: Exception) {
                if (fence.accepts(activation)) stopFlash(flashText(R.string.flash_activation_network_failed))
            }
        }
    }

    private fun listener(activation: Long) = object : FlashListener {
        override fun onState(state: FlashState) {
            state.operations.forEach { operationNames[it.id] = it.fileName }
            post(activation) {
                state.operations.forEach(outgoingBatch::observe)
                FlashAndroidRuntime.update { old ->
                    old.copy(engine = state, progress = old.progress.filterKeys { id -> state.operations.any { it.id == id } })
                }
                if (!state.active && FlashAndroidRuntime.state.value.phase == FlashAndroidPhase.ACTIVE) {
                    stopFlash(flashText(R.string.flash_session_ended))
                }
                refreshNotification()
                if (state.operations.isEmpty()) {
                    outgoingBatch.idle()
                }
            }
        }

        override fun onApproval(approval: FlashApproval) = post(activation) {
            FlashAndroidRuntime.update { it.copy(statusText = flashText(R.string.flash_compare_verification)) }
            refreshNotification()
        }

        override fun onProgress(progress: FlashProgress) = post(activation) {
            FlashAndroidRuntime.update { it.copy(progress = it.progress + (progress.operationId to progress)) }
        }

        override fun onReceived(received: FlashReceived) {
            // Publication is terminal. Keep a known, verified receipt even when STOP won the UI epoch.
            if (!operationNames.containsKey(received.operationId)) return
            val result = FlashAndroidResult(received.operationId, received.file.name, FlashResultKind.RECEIVED,
                flashText(R.string.flash_received_verified_in_qetara), file = received.file)
            addResult(result)
            post(activation) {
                FlashAndroidRuntime.update { it.copy(statusText = flashText(R.string.flash_received_verified)) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    exportJobs += scope.launch {
                        val publication = withContext(Dispatchers.IO) {
                            DownloadsExport.exportToDownloads(applicationContext, received.file) {
                                ensureActive()
                                check(fence.accepts(activation)) { "Flash session ended" }
                            }
                        }
                        if (fence.accepts(activation)) {
                            FlashAndroidRuntime.update { old -> old.copy(results = old.results.map {
                                if (it.id != received.operationId) it else it.copy(
                                    downloadUri = publication.getOrNull()?.toString(),
                                    detailText = flashText(when {
                                        publication.isSuccess && it.confirmationIssue -> R.string.flash_saved_downloads_unconfirmed
                                        publication.isSuccess -> R.string.flash_saved_downloads
                                        it.confirmationIssue -> R.string.flash_received_copy_unconfirmed
                                        else -> R.string.flash_received_copy
                                    })
                                )
                            }) }
                        }
                    }
                }
                refreshNotification()
                }
        }

        override fun onCompleted(completed: FlashCompleted) = post(activation) {
            if (completed.outgoing) {
                addResult(FlashAndroidResult(completed.operationId, completed.fileName,
                    FlashResultKind.DELIVERED, flashText(R.string.flash_delivery_confirmed_detail)))
                val batch = outgoingBatch.complete(completed.operationId)
                val sentFile = batch?.file
                FlashAndroidRuntime.update { old -> old.copy(
                    statusText = if (batch != null && batch.remaining > 0) {
                        flashText(R.string.flash_batch_delivery_progress, batch.completed, batch.total)
                    } else if (batch != null && batch.completed > 1) {
                        flashPlural(R.plurals.flash_files_delivered, batch.completed, batch.completed)
                    } else {
                        flashText(R.string.flash_delivery_confirmed)
                    },
                    selectedFiles = old.selectedFiles.filterNot { it == sentFile }
                ) }
                sentFile?.let { file -> scope.launch(Dispatchers.IO) { file.delete(); file.parentFile?.delete() } }
            }
            operationNames.remove(completed.operationId)
            refreshNotification()
        }

        override fun onError(error: FlashError) = post(activation) {
            val copy = flashErrorText(error.code)
            error.operationId?.let { id ->
                val name = operationNames.remove(id).orEmpty()
                FlashAndroidRuntime.update { it.copy(results = recordFlashFailure(it.results, id, name, copy, error.code == "cancelled")) }
                outgoingBatch.fail(id)
            }
            FlashAndroidRuntime.update { it.copy(statusText = copy) }
            refreshNotification()
        }
    }

    private fun post(activation: Long, action: () -> Unit) {
        scope.launch { if (fence.accepts(activation)) action() }
    }

    private fun addResult(result: FlashAndroidResult) {
        FlashAndroidRuntime.update { it.copy(results = (listOf(result) + it.results.filterNot { old -> old.id == result.id }).take(20)) }
    }

    private fun discover(host: String? = null) {
        val currentEngine = engine ?: return
        if (!FlashAndroidRuntime.state.value.active) return
        FlashAndroidRuntime.update { it.copy(statusText = if (host == null) flashText(R.string.flash_discovery_requested) else flashText(R.string.flash_address_requested)) }
        runCatching {
            if (host == null) currentEngine.discover() else currentEngine.discoverAt(host.trim())
        }.onFailure { FlashAndroidRuntime.update { it.copy(statusText = flashText(R.string.flash_check_address)) } }
    }

    private fun selectFiles(rawUris: List<Uri>) {
        val state = FlashAndroidRuntime.state.value
        if (!state.active || state.importing || outgoingBatch.active || state.engine?.operations?.isNotEmpty() == true) return
        if (rawUris.isEmpty()) return
        val pendingUris = importSources.pending(rawUris.map(Uri::toString), state.selectedFiles).toSet()
        val uris = rawUris.distinctBy(Uri::toString).filter { it.toString() in pendingUris }
        if (uris.isEmpty()) {
            FlashAndroidRuntime.update { it.copy(statusText = flashText(R.string.flash_selection_already_prepared)) }
            return
        }
        val activation = ticket
        FlashAndroidRuntime.update { it.copy(importing = true, statusText = flashPlural(R.plurals.flash_preparing_file_count, uris.size, uris.size)) }
        importJob = scope.launch {
            val staged = mutableListOf<File>()
            var committed = false
            try {
                uris.forEachIndexed { index, uri ->
                    prepareFlashImportFile(staged) {
                        // This fallback becomes the transferred file name; keep it locale-independent.
                        var name = "Archivo"
                        var knownSize: Long? = null
                        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) knownSize = cursor.getLong(sizeIndex)
                            }
                        }
                        val available = StatFs(cacheDir.absolutePath).availableBytes - 16L * 1024 * 1024
                        require(knownSize == null || knownSize!! in 0..minOf(MAX_FILE_BYTES, available))
                        val folder = File(cacheDir, "flash-outgoing/${UUID.randomUUID()}").apply { mkdirs() }
                        val file = File(folder, sanitizeFileName(name))
                        try {
                            val input = contentResolver.openInputStream(uri) ?: error("Input stream unavailable")
                            importStream.set(input)
                            input.use { source -> file.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var size = 0L
                                while (true) {
                                    ensureActive()
                                    check(fence.accepts(activation))
                                    val count = source.read(buffer)
                                    if (count < 0) break
                                    size += count
                                    require(size <= MAX_FILE_BYTES && size <= available)
                                    output.write(buffer, 0, count)
                                }
                            } }
                            file
                        } catch (error: Throwable) {
                            file.delete()
                            folder.delete()
                            throw error
                        } finally {
                            importStream.set(null)
                        }
                    }
                    if (fence.accepts(activation) && index + 1 < uris.size) {
                        FlashAndroidRuntime.update { it.copy(statusText = flashText(R.string.flash_preparing_file_progress, index + 1, uris.size)) }
                    }
                }
                if (fence.accepts(activation)) {
                    val previousSelection = FlashAndroidRuntime.state.value.selectedFiles
                    val selected = importSources.commit(uris.map(Uri::toString).zip(staged),
                        previousSelection)
                    FlashAndroidRuntime.update { old ->
                        old.copy(selectedFiles = selected, importing = false,
                            statusText = if (selected.size == 1) {
                                flashText(R.string.flash_file_prepared)
                            } else {
                                flashPlural(R.plurals.flash_files_prepared, selected.size, selected.size)
                            })
                    }
                    committed = true
                    val replaced = previousSelection.toSet() - selected.toSet()
                    if (replaced.isNotEmpty()) scope.launch(Dispatchers.IO) {
                        replaced.forEach { file -> file.delete(); file.parentFile?.delete() }
                    }
                }
            } catch (_: CancellationException) {
                if (fence.accepts(activation)) FlashAndroidRuntime.update { it.copy(importing = false, statusText = flashText(R.string.flash_selection_cancelled)) }
            } catch (_: Exception) {
                if (fence.accepts(activation)) FlashAndroidRuntime.update { it.copy(importing = false,
                    statusText = flashText(R.string.flash_file_preparation_failed)) }
            } finally {
                if (!committed) withContext(NonCancellable + Dispatchers.IO) {
                    staged.forEach { file -> file.delete(); file.parentFile?.delete() }
                }
            }
        }
    }

    private fun sendSelected() {
        val state = FlashAndroidRuntime.state.value
        val currentEngine = engine ?: return
        if (!state.active || state.importing || outgoingBatch.active || state.engine?.operations?.isNotEmpty() == true) return
        val peer = resolveSelectedFlashPeer(state.selectedPeer, currentEngine.snapshot().peers, System.currentTimeMillis())
        val files = state.selectedFiles
        if (files.size > FLASH_MAX_BATCH_FILES) {
            FlashAndroidRuntime.update { it.copy(statusText = flashPlural(R.plurals.flash_batch_limit, FLASH_MAX_BATCH_FILES, FLASH_MAX_BATCH_FILES)) }
            return
        }
        if (peer == null || files.isEmpty()) {
            FlashAndroidRuntime.update { it.copy(statusText = flashText(R.string.flash_choose_available_device)) }
            return
        }
        if (!canSendFlashFiles(files)) {
            FlashAndroidRuntime.update { it.copy(statusText = flashText(R.string.flash_selection_unreadable)) }
            return
        }
        if (!outgoingBatch.start(files)) return
        FlashAndroidRuntime.update { it.copy(statusText = if (files.size == 1) {
            flashText(R.string.flash_preparing_send)
        } else {
            flashPlural(R.plurals.flash_preparing_batch, files.size, files.size)
        }) }
        runCatching { currentEngine.sendBatch(files, peer) }
            .onSuccess { id ->
                outgoingBatch.started(id)
                operationNames[id] = files.first().name
                FlashAndroidRuntime.update { it.copy(statusText = if (files.size > 1) {
                    flashPlural(R.plurals.flash_verify_batch, files.size, files.size)
                } else {
                    flashText(R.string.flash_requesting_connection)
                }) }
            }
            .onFailure {
                resetOutgoingBatch()
                FlashAndroidRuntime.update { it.copy(statusText = flashText(R.string.flash_send_start_failed)) }
            }
    }

    private fun resetOutgoingBatch() {
        outgoingBatch.reset()
    }

    private fun answer(requestId: String, accepted: Boolean) {
        val state = FlashAndroidRuntime.state.value
        if (!canAnswerFlashApproval(state.active, requestId, state.engine?.approvals.orEmpty(), System.currentTimeMillis())) return
        val applied = engine?.approve(requestId, accepted) == true
        if (!applied) FlashAndroidRuntime.update { it.copy(statusText = flashText(R.string.flash_request_ended)) }
    }

    private fun stopFlash(message: FlashText) {
        if (stopping) return
        stopping = true
        fence.close()
        val previous = engine
        engine = null
        importJob?.cancel()
        resetOutgoingBatch()
        exportJobs.forEach { it.cancel() }
        FlashAndroidRuntime.update { it.copy(phase = FlashAndroidPhase.STOPPING, engine = null,
            importing = false, progress = emptyMap(), selectedPeer = null, statusText = message) }
        scope.launch {
            startupJob?.cancelAndJoin()
            withContext(Dispatchers.IO) {
                pendingEngine.getAndSet(null)?.stop()
                runCatching { importStream.getAndSet(null)?.close() }
                previous?.stop()
            }
            releaseNetworkLocks()
            ServiceCompat.stopForeground(this@FlashForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelfResult(lastStartId)
        }
    }

    override fun onDestroy() {
        fence.close()
        pendingEngine.getAndSet(null)?.stop()
        engine?.stop()
        engine = null
        importJob?.cancel()
        resetOutgoingBatch()
        runCatching { importStream.getAndSet(null)?.close() }
        releaseNetworkLocks()
        scope.cancel()
        if (current === this) {
            current = null
            FlashAndroidRuntime.update { it.copy(phase = FlashAndroidPhase.OFF, engine = null,
                importing = false, progress = emptyMap(), selectedPeer = null, localAddresses = emptyList()) }
        }
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopFlash(flashText(R.string.flash_android_stopped))
    }

    private fun acquireNetworkLocks() {
        runCatching {
            multicastLock = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
                .createMulticastLock("QetaraFlash").apply { setReferenceCounted(false); acquire() }
        }
        runCatching {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Qetara:Flash")
                .apply { setReferenceCounted(false); acquire(30 * 60 * 1000L) }
        }
    }

    private fun releaseNetworkLocks() {
        runCatching { multicastLock?.let { if (it.isHeld) it.release() } }
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
        multicastLock = null
        wakeLock = null
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 9100, Intent(this, FlashActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 9101, Intent(this, FlashForegroundService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val state = FlashAndroidRuntime.state.value
        val text = when {
            state.engine?.approvals?.isNotEmpty() == true -> getString(R.string.flash_notification_verification)
            state.engine?.operations?.isNotEmpty() == true -> getString(R.string.flash_notification_transfer)
            else -> getString(R.string.flash_notification_available)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.flash_notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, getString(R.string.flash_open_flash), open)
            .addAction(0, getString(R.string.flash_deactivate), stop)
            .build()
    }

    private fun showForeground() {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
    }

    private fun refreshNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        }
    }

    companion object {
        private const val CHANNEL = "qetara_flash"
        private const val NOTIFICATION_ID = 7391
        private const val ACTION_START = "com.example.wifidrop.flash.START"
        private const val ACTION_STOP = "com.example.wifidrop.flash.STOP"
        private const val EXTRA_LABEL = "label"
        private const val MAX_FILE_BYTES = 16L * 1024 * 1024 * 1024
        private val importSources = FlashImportSources()
        @Volatile private var current: FlashForegroundService? = null

        internal fun activate(context: Context, label: String) {
            if (FlashAndroidRuntime.state.value.phase != FlashAndroidPhase.OFF) return
            FlashAndroidRuntime.update { it.copy(phase = FlashAndroidPhase.STARTING, statusText = flashText(R.string.flash_activating)) }
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, FlashForegroundService::class.java)
                    .setAction(ACTION_START).putExtra(EXTRA_LABEL, label))
            }.onFailure { FlashAndroidRuntime.update { it.copy(phase = FlashAndroidPhase.OFF,
                statusText = flashText(R.string.flash_activation_failed)) } }
        }

        internal fun deactivate() { current?.stopFlash(flashText(R.string.flash_stopped)) }
        internal fun discoverPeers(host: String? = null) { current?.discover(host) }
        internal fun chooseFiles(uris: List<Uri>) { current?.selectFiles(uris) }
        internal fun send() { current?.sendSelected() }
        internal fun approve(requestId: String, accepted: Boolean) { current?.answer(requestId, accepted) }
        internal fun cancel(operationId: String) {
            val service = current ?: return
            service.outgoingBatch.cancelPending(operationId)
            service.engine?.cancel(operationId)
        }
        internal fun cancelImport() {
            val service = current ?: return
            service.importJob?.cancel()
            service.importStream.getAndSet(null)?.let { input -> service.scope.launch(Dispatchers.IO) { runCatching { input.close() } } }
        }
        internal fun selectPeer(peer: FlashPeer) {
            if (current?.outgoingBatch?.active != true && FlashAndroidRuntime.state.value.active && FlashAndroidRuntime.state.value.engine?.operations?.isEmpty() == true) {
                FlashAndroidRuntime.update { it.copy(selectedPeer = peer) }
            }
        }
        internal fun clearFiles() {
            val state = FlashAndroidRuntime.state.value
            if (current?.outgoingBatch?.active == true || state.engine?.operations?.isNotEmpty() == true || state.importing) return
            importSources.clear()
            FlashAndroidRuntime.update { it.copy(selectedFiles = emptyList(),
                statusText = flashText(R.string.flash_selection_empty)) }
            current?.scope?.launch(Dispatchers.IO) {
                state.selectedFiles.forEach { file -> file.delete(); file.parentFile?.delete() }
            }
        }
    }
}

private fun localAddresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
        .filter { !it.isLoopbackAddress && !it.isAnyLocalAddress }.mapNotNull { it.hostAddress }.distinct()
}.getOrDefault(emptyList())

internal fun flashErrorCopy(code: String): String = flashErrorText(code).resolve()

internal fun flashErrorText(code: String): FlashText = when (code) {
    "discovery_failed" -> flashText(R.string.flash_error_discovery)
    "cancelled" -> flashText(R.string.flash_error_cancelled)
    "rejected" -> flashText(R.string.flash_error_rejected)
    "expired" -> flashText(R.string.flash_error_expired)
    "approval_expired" -> flashText(R.string.flash_error_approval_expired)
    "busy" -> flashText(R.string.flash_error_busy)
    "invalid_file" -> flashText(R.string.flash_error_invalid_file)
    "invalid_batch" -> flashText(R.string.flash_error_invalid_batch)
    "batch_incompatible" -> flashText(R.string.flash_error_batch_incompatible)
    "peer_changed" -> flashText(R.string.flash_error_peer_changed)
    "invalid_address" -> flashText(R.string.flash_error_invalid_address)
    "storage_unavailable" -> flashText(R.string.flash_error_storage)
    "incompatible" -> flashText(R.string.flash_error_incompatible)
    "integrity_failed" -> flashText(R.string.flash_error_integrity)
    "unconfirmed" -> flashText(R.string.flash_error_unconfirmed)
    else -> flashText(R.string.flash_error_generic)
}
