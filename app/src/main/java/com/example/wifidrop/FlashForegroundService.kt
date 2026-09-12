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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Flash temporal", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Estado y solicitudes de la sesión Flash que activas." }
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
            ACTION_STOP -> stopFlash("Flash desactivado. Los archivos recibidos se conservan.")
            else -> if (engine == null) stopSelfResult(startId)
        }
        // Process death must never make a previous activation discoverable again.
        return START_NOT_STICKY
    }

    private fun startFlash(rawLabel: String) {
        ticket = fence.activate()
        val activation = ticket
        FlashAndroidRuntime.update {
            it.copy(phase = FlashAndroidPhase.STARTING, status = "Activando Flash…", engine = null,
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
                        status = "Flash activo. Elige un equipo o espera un archivo.", localAddresses = localAddresses)
                }
                refreshNotification()
                started.discover()
                while (isActive && fence.accepts(activation)) {
                    delay(1_000)
                    val snapshot = started.snapshot()
                    if (!snapshot.active || snapshot.expiresAtMs <= System.currentTimeMillis()) {
                        stopFlash("Terminó el tiempo de Flash. Actívalo de nuevo cuando lo necesites.")
                        break
                    }
                }
            } catch (error: Exception) {
                if (fence.accepts(activation)) stopFlash("No se pudo activar Flash. Comprueba la red y vuelve a intentar.")
            }
        }
    }

    private fun listener(activation: Long) = object : FlashListener {
        override fun onState(state: FlashState) {
            state.operations.forEach { operationNames[it.id] = it.fileName }
            post(activation) {
                FlashAndroidRuntime.update { old ->
                    old.copy(engine = state, progress = old.progress.filterKeys { id -> state.operations.any { it.id == id } })
                }
                if (!state.active && FlashAndroidRuntime.state.value.phase == FlashAndroidPhase.ACTIVE) {
                    stopFlash("Flash terminó. Los archivos recibidos se conservan.")
                }
                refreshNotification()
                if (state.operations.isEmpty()) {
                    pumpOutgoingBatch()
                }
            }
        }

        override fun onApproval(approval: FlashApproval) = post(activation) {
            FlashAndroidRuntime.update { it.copy(status = "Compara la verificación en los dos equipos.") }
            refreshNotification()
        }

        override fun onProgress(progress: FlashProgress) = post(activation) {
            FlashAndroidRuntime.update { it.copy(progress = it.progress + (progress.operationId to progress)) }
        }

        override fun onReceived(received: FlashReceived) {
            // Publication is terminal. Keep a known, verified receipt even when STOP won the UI epoch.
            if (!operationNames.containsKey(received.operationId)) return
            val result = FlashAndroidResult(received.operationId, received.file.name, FlashResultKind.RECEIVED,
                "Archivo verificado y recibido en Qetara.", file = received.file)
            addResult(result)
            post(activation) {
                FlashAndroidRuntime.update { it.copy(status = "Archivo recibido y verificado.") }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    exportJobs += scope.launch {
                        val publication = withContext(Dispatchers.IO) {
                            DownloadsExport.exportToDownloads(applicationContext, received.file) {
                                ensureActive()
                                check(fence.accepts(activation)) { "Flash terminó" }
                            }
                        }
                        if (fence.accepts(activation)) {
                            FlashAndroidRuntime.update { old -> old.copy(results = old.results.map {
                                if (it.id != received.operationId) it else it.copy(
                                    downloadUri = publication.getOrNull()?.toString(),
                                    detail = (if (publication.isSuccess) "Guardado en Descargas / Qetara."
                                    else "Recibido en Qetara. Puedes guardar una copia donde prefieras.") +
                                        if (it.confirmationIssue) " No se pudo confirmar la entrega al otro equipo." else ""
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
                    FlashResultKind.DELIVERED, "El otro equipo confirmó la recepción y verificación del archivo."))
                val batch = outgoingBatch.complete(completed.operationId)
                val sentFile = batch?.file
                FlashAndroidRuntime.update { old -> old.copy(
                    status = if (batch != null && batch.remaining > 0) {
                        "Entrega ${batch.completed} de ${batch.total} confirmada. Preparando el siguiente archivo."
                    } else if (batch != null && batch.completed > 1) {
                        "${batch.completed} archivos entregados y confirmados."
                    } else {
                        "Entrega confirmada por el otro equipo."
                    },
                    selectedFiles = old.selectedFiles.filterNot { it == sentFile }
                ) }
                sentFile?.let { file -> scope.launch(Dispatchers.IO) { file.delete(); file.parentFile?.delete() } }
                pumpOutgoingBatch()
            }
            operationNames.remove(completed.operationId)
            refreshNotification()
        }

        override fun onError(error: FlashError) = post(activation) {
            val copy = flashErrorCopy(error.code)
            error.operationId?.let { id ->
                val name = operationNames.remove(id) ?: "Archivo"
                FlashAndroidRuntime.update { it.copy(results = recordFlashFailure(it.results, id, name, copy, error.code == "cancelled")) }
                outgoingBatch.fail(id)
            }
            FlashAndroidRuntime.update { it.copy(status = copy) }
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
        FlashAndroidRuntime.update { it.copy(status = if (host == null) "Búsqueda solicitada. Activa Flash en el otro equipo si no aparece." else "Dirección solicitada. Espera a que aparezca el equipo o revisa su conexión.") }
        runCatching {
            if (host == null) currentEngine.discover() else currentEngine.discoverAt(host.trim())
        }.onFailure { FlashAndroidRuntime.update { it.copy(status = "Revisa la dirección del otro equipo e inténtalo de nuevo.") } }
    }

    private fun selectFiles(rawUris: List<Uri>) {
        val state = FlashAndroidRuntime.state.value
        if (!state.active || state.importing || outgoingBatch.active || state.engine?.operations?.isNotEmpty() == true) return
        if (rawUris.isEmpty()) return
        val pendingUris = importSources.pending(rawUris.map(Uri::toString), state.selectedFiles).toSet()
        val uris = rawUris.distinctBy(Uri::toString).filter { it.toString() in pendingUris }
        if (uris.isEmpty()) {
            FlashAndroidRuntime.update { it.copy(status = "Los archivos elegidos ya están preparados.") }
            return
        }
        val activation = ticket
        FlashAndroidRuntime.update { it.copy(importing = true, status = "Preparando ${uris.size} archivo(s)…") }
        importJob = scope.launch {
            val staged = mutableListOf<File>()
            var committed = false
            try {
                uris.forEachIndexed { index, uri ->
                    prepareFlashImportFile(staged) {
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
                            val input = contentResolver.openInputStream(uri) ?: error("No disponible")
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
                        FlashAndroidRuntime.update { it.copy(status = "Preparando ${index + 1} de ${uris.size} archivos…") }
                    }
                }
                if (fence.accepts(activation)) {
                    val previousSelection = FlashAndroidRuntime.state.value.selectedFiles
                    val selected = importSources.commit(uris.map(Uri::toString).zip(staged),
                        previousSelection)
                    FlashAndroidRuntime.update { old ->
                        old.copy(selectedFiles = selected, importing = false,
                            status = if (selected.size == 1) {
                                "Archivo preparado. Elige el equipo receptor y envía la solicitud."
                            } else {
                                "${selected.size} archivos preparados. Se enviarán uno por uno."
                            })
                    }
                    committed = true
                    val replaced = previousSelection.toSet() - selected.toSet()
                    if (replaced.isNotEmpty()) scope.launch(Dispatchers.IO) {
                        replaced.forEach { file -> file.delete(); file.parentFile?.delete() }
                    }
                }
            } catch (_: CancellationException) {
                if (fence.accepts(activation)) FlashAndroidRuntime.update { it.copy(importing = false, status = "Selección cancelada.") }
            } catch (_: Exception) {
                if (fence.accepts(activation)) FlashAndroidRuntime.update { it.copy(importing = false,
                    status = "No se pudo preparar el archivo. Comprueba que esté disponible y haya espacio, o elige otro.") }
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
        if (peer == null || files.isEmpty()) {
            FlashAndroidRuntime.update { it.copy(status = "Vuelve a elegir un equipo disponible y al menos un archivo.") }
            return
        }
        if (!canSendFlashFiles(files)) {
            FlashAndroidRuntime.update { it.copy(status = "Uno de los archivos ya no está disponible o no se puede leer. Revisa la selección.") }
            return
        }
        if (!outgoingBatch.start(files, peer)) return
        FlashAndroidRuntime.update { it.copy(status = if (files.size == 1) {
            "Preparando el envío. Compara la verificación en ambos equipos."
        } else {
            "Preparando ${files.size} envíos. Cada archivo tendrá su propia verificación."
        }) }
        pumpOutgoingBatch()
    }

    private fun pumpOutgoingBatch() {
        val state = FlashAndroidRuntime.state.value
        val currentEngine = engine ?: return
        if (!state.active || state.importing || currentEngine.snapshot().operations.isNotEmpty()) return
        val request = outgoingBatch.next() ?: return
        val file = request.file
        val peer = resolveSelectedFlashPeer(request.peer, currentEngine.snapshot().peers, System.currentTimeMillis())
        if (peer == null || !isReadableFlashFile(file)) {
            resetOutgoingBatch()
            FlashAndroidRuntime.update { it.copy(status = "El equipo o uno de los archivos ya no está disponible. Revisa la selección.") }
            return
        }
        runCatching { currentEngine.send(file, peer) }
            .onSuccess { id ->
                outgoingBatch.started(id)
                operationNames[id] = file.name
                FlashAndroidRuntime.update { it.copy(status = if (request.total > 1) {
                    "Archivo ${request.position} de ${request.total}: compara la verificación en ambos equipos."
                } else {
                    "Solicitando conexión. Compara la verificación en ambos equipos."
                }) }
            }
            .onFailure {
                resetOutgoingBatch()
                FlashAndroidRuntime.update { it.copy(status = "No se pudo iniciar. Comprueba el otro equipo y vuelve a intentar.") }
            }
    }

    private fun resetOutgoingBatch() {
        outgoingBatch.reset()
    }

    private fun answer(requestId: String, accepted: Boolean) {
        val state = FlashAndroidRuntime.state.value
        if (!canAnswerFlashApproval(state.active, requestId, state.engine?.approvals.orEmpty(), System.currentTimeMillis())) return
        val applied = engine?.approve(requestId, accepted) == true
        if (!applied) FlashAndroidRuntime.update { it.copy(status = "La solicitud ya terminó. Pide al otro equipo que vuelva a intentar.") }
    }

    private fun stopFlash(message: String) {
        if (stopping) return
        stopping = true
        fence.close()
        val previous = engine
        engine = null
        importJob?.cancel()
        resetOutgoingBatch()
        exportJobs.forEach { it.cancel() }
        FlashAndroidRuntime.update { it.copy(phase = FlashAndroidPhase.STOPPING, engine = null,
            importing = false, progress = emptyMap(), selectedPeer = null, status = message) }
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
        stopFlash("Android detuvo Flash. Puedes activarlo de nuevo cuando lo necesites.")
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
            state.engine?.approvals?.isNotEmpty() == true -> "Compara la verificación y decide sobre el archivo."
            state.engine?.operations?.isNotEmpty() == true -> "Transferencia en curso. Abre Flash para ver el progreso."
            else -> "Disponible temporalmente. Abre Flash o desactívalo."
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Qetara Flash")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, "Abrir Flash", open)
            .addAction(0, "Desactivar", stop)
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
            FlashAndroidRuntime.update { it.copy(phase = FlashAndroidPhase.STARTING, status = "Activando Flash…") }
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, FlashForegroundService::class.java)
                    .setAction(ACTION_START).putExtra(EXTRA_LABEL, label))
            }.onFailure { FlashAndroidRuntime.update { it.copy(phase = FlashAndroidPhase.OFF,
                status = "No se pudo activar Flash. Abre esta pantalla y vuelve a intentar.") } }
        }

        internal fun deactivate() { current?.stopFlash("Flash desactivado. Los archivos recibidos se conservan.") }
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
                status = "Selección vacía. Elige archivos para compartir.") }
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

internal fun flashErrorCopy(code: String): String = when (code) {
    "cancelled" -> "Transferencia cancelada. Puedes volver a intentarlo cuando quieras."
    "rejected" -> "La solicitud fue rechazada o la verificación no coincidió. No se envió el archivo."
    "expired" -> "La solicitud o sesión expiró. Activa Flash y vuelve a intentar."
    "approval_expired" -> "La confirmación caducó. Vuelve a solicitar el envío desde el equipo emisor y compara el nuevo código en ambos equipos."
    "busy" -> "El otro equipo ya está atendiendo una transferencia. Espera y vuelve a intentar."
    "invalid_file" -> "No se puede enviar ese archivo. Comprueba que esté disponible y haya espacio."
    "peer_changed" -> "La sesión de Flash del otro equipo ya no coincide con la que elegiste. Búscalo de nuevo y vuelve a seleccionarlo."
    "invalid_address" -> "La dirección no es válida. Escribe la IP local que aparece en Flash del otro equipo."
    "storage_unavailable" -> "No se pudo preparar la carpeta para recibir el archivo. Revisa que el almacenamiento de este equipo esté disponible y tenga espacio."
    "incompatible" -> "La respuesta del otro equipo no es compatible con Flash. Revisa la dirección y las versiones de Qetara en ambos equipos."
    "integrity_failed" -> "El archivo no superó la verificación. Vuelve a enviarlo."
    "unconfirmed" -> "No se pudo confirmar la entrega. Comprueba en el otro equipo si el archivo llegó antes de volver a enviarlo."
    else -> "No se pudo completar la transferencia. Revisa Flash y la conexión del otro equipo."
}
