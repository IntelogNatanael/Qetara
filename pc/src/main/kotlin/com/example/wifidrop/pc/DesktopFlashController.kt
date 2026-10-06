package com.example.wifidrop.pc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.wifidrop.protocol.flash.*
import java.io.File
import java.net.BindException
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

internal enum class DesktopFlashPhase { WAITING, TRANSFERRING, COMPLETE, CANCELLED, FAILED }

internal data class DesktopFlashTransfer(
    val id: String,
    val fileName: String,
    val peerLabel: String,
    val totalBytes: Long,
    val outgoing: Boolean,
    val transferredBytes: Long = 0,
    val phase: DesktopFlashPhase = DesktopFlashPhase.WAITING,
    val detail: String = "Preparando conexión y verificación…",
    val file: File? = null,
    val cancelling: Boolean = false
) {
    val busy: Boolean get() = phase == DesktopFlashPhase.WAITING || phase == DesktopFlashPhase.TRANSFERRING
}

internal data class DesktopFlashUiState(
    val session: FlashState = FlashState(),
    val starting: Boolean = false,
    val sendPending: Boolean = false,
    val batchSending: Boolean = false,
    val directory: File,
    val selectedFiles: List<File> = emptyList(),
    val selectedPeerId: String? = null,
    val transfers: List<DesktopFlashTransfer> = emptyList(),
    val status: String = "Flash está desactivado.",
    val error: Boolean = false
) {
    val busy: Boolean get() = sendPending || batchSending || session.operations.isNotEmpty() || transfers.any { it.busy }
    val selectedFile: File? get() = selectedFiles.firstOrNull()
    val selectedPeer: FlashPeer? get() = session.peers.firstOrNull { it.id == selectedPeerId }
}

/** One controller survives navigation; engine callbacks never mutate Compose state off the EDT. */
internal class DesktopFlashController(
    initialDirectory: File,
    private val engineFactory: (String, File, FlashListener) -> DesktopFlashTransport = ::createDesktopFlashTransport
) : AutoCloseable {
    var state by mutableStateOf(DesktopFlashUiState(directory = initialDirectory))
        private set
    private val requests = DesktopFlashRequests()
    private val closed = AtomicBoolean(false)
    private val progressBuffer = DesktopFlashProgressBuffer()
    private val progressTimer = javax.swing.Timer(100) { flushProgress() }
    private val knownOperations = ConcurrentHashMap<Pair<Long, String>, FlashOperation>()
    private var outgoingFiles = emptyList<File>()
    private var activeOutgoingBatchId: String? = null
    private var outgoingBatchTotal = 0
    private var outgoingBatchCompleted = 0
    private var activeOutgoingOperationId: String? = null
    private var outgoingSubmission = 0L
    private val commands = Executors.newSingleThreadExecutor { task ->
        Thread(task, "qetara-desktop-flash-control").apply { isDaemon = true }
    }
    @Volatile private var engine: DesktopFlashTransport? = null
    private var generation = 0L

    fun start(label: String) {
        if (closed.get() || state.starting || state.session.active) return
        val token = requests.begin()
        generation = token
        progressBuffer.clear()
        progressTimer.start()
        val directory = state.directory
        state = state.copy(starting = true, status = "Activando Flash…", error = false)
        commands.execute {
            if (!requests.isCurrent(token)) return@execute
            try {
                engine?.stop()
                val next = engineFactory(label.ifBlank { "Qetara PC" }, directory, listener(token))
                engine = next
                if (!requests.isCurrent(token)) {
                    next.stop()
                    return@execute
                }
                // start emits onState before returning. A second snapshot could overtake queued events.
                next.start()
                if (requests.isCurrent(token)) next.discover()
            } catch (error: Exception) {
                runCatching { engine?.stop() }
                engine = null
                dispatch(token) {
                    requests.invalidate()
                    progressTimer.stop()
                    progressBuffer.clear()
                    state = state.copy(
                        starting = false, session = FlashState(), error = true,
                        status = if (error is BindException) "Flash no pudo abrir el puerto 8989. Cierra otra instancia de Qetara con Flash activo y vuelve a intentar."
                        else "No se pudo activar Flash. Comprueba la carpeta de destino y vuelve a intentar."
                    )
                }
            }
        }
    }

    fun stop() {
        requests.invalidate()
        progressTimer.stop()
        progressBuffer.clear()
        resetOutgoingBatch()
        state = state.copy(
            session = FlashState(), starting = false, sendPending = false, batchSending = false,
            selectedPeerId = null, error = false,
            status = "Flash desactivado. Los archivos ya guardados se conservan.",
            transfers = state.transfers.map {
                if (it.busy) it.copy(phase = DesktopFlashPhase.CANCELLED, detail = "Flash se desactivó. Comprueba el otro equipo antes de repetir.", cancelling = false) else it
            }
        )
        if (!closed.get()) commands.execute {
            engine?.stop()
            engine = null
        }
    }

    fun chooseDirectory(directory: File) {
        if (!state.session.active && !state.starting) state = state.copy(directory = directory, error = false)
    }

    fun chooseFile(file: File) = chooseFiles(listOf(file))

    fun chooseFiles(files: List<File>) {
        if (state.busy) return
        if (files.isEmpty()) return
        val readable = files.filter { it.isFile && it.canRead() }
        if (readable.isEmpty()) {
            state = state.copy(status = "Elige un archivo que puedas abrir. Para compartir una carpeta, comprímela primero.", error = true)
            return
        }
        val selected = mergeDesktopFileSelections(state.selectedFiles, readable)
        val ignored = files.size - readable.size
        state = state.copy(
            selectedFiles = selected,
            status = when {
                ignored > 0 -> "${selected.size} archivos preparados; se omitieron $ignored elementos no legibles."
                selected.size == 1 -> "Archivo elegido. Selecciona el otro equipo."
                else -> "${selected.size} archivos preparados. Se enviarán uno por uno."
            },
            error = false
        )
    }

    fun clearFiles() {
        if (!state.busy) state = state.copy(selectedFiles = emptyList())
    }

    fun chooseDroppedFiles(files: List<File>) {
        if (state.busy) return
        if (files.none { it.isFile && it.canRead() }) {
            state = state.copy(status = "Suelta un archivo. Para compartir una carpeta, comprímela primero.", error = true)
            return
        }
        chooseFiles(files)
    }

    fun choosePeer(peer: FlashPeer) {
        if (!state.busy && state.session.peers.any { it.id == peer.id && it.address == peer.address && it.port == peer.port }) {
            state = state.copy(selectedPeerId = peer.id, error = false)
        }
    }

    fun discover() {
        if (!state.session.active) return
        state = state.copy(status = "Búsqueda solicitada. Los equipos con Flash activo aparecerán aquí.", error = false)
        command("No se pudo buscar. Comprueba la red e inténtalo de nuevo.") { it.discover() }
    }

    fun discoverAt(address: String, port: String) {
        val number = port.toIntOrNull()
        if (number == null || number !in 1..65535) {
            state = state.copy(status = "El puerto debe estar entre 1 y 65535.", error = true)
            return
        }
        if (!state.session.active) return
        state = state.copy(status = "Búsqueda solicitada para esa dirección. Selecciona el equipo cuando aparezca.", error = false)
        command("No se pudo buscar esa dirección. Usa una IP local y comprueba que Flash esté activo allí.") {
            it.discoverAt(address.trim(), number)
        }
    }

    fun send() {
        val current = state
        val files = current.selectedFiles
        if (files.isEmpty()) return
        if (files.size > FLASH_MAX_BATCH_FILES) {
            state = state.copy(status = "Puedes enviar hasta $FLASH_MAX_BATCH_FILES archivos por lote. Reduce la selección.", error = true)
            return
        }
        val peer = current.selectedPeer ?: return
        if (!current.session.active || current.busy || peer.expiresAtMs <= System.currentTimeMillis()) return
        if (files.any { !it.isFile || !it.canRead() }) {
            state = state.copy(status = "Uno de los archivos ya no está disponible. Revisa la selección.", error = true)
            return
        }
        outgoingFiles = files.toList()
        outgoingBatchTotal = files.size
        outgoingBatchCompleted = 0
        state = state.copy(
            batchSending = true, sendPending = true,
            status = if (files.size == 1) "Preparando el archivo. Después compara el código en ambos equipos."
            else "Preparando ${files.size} archivos. Una verificación autorizará este lote.",
            error = false
        )
        val token = generation
        activeOutgoingOperationId = null
        activeOutgoingBatchId = null
        val submission = ++outgoingSubmission
        commands.execute {
            if (!requests.isCurrent(token)) return@execute
            try {
                val batchId = checkNotNull(engine).sendBatch(files, peer)
                dispatch(token) {
                    // All callbacks, including completion, can arrive before sendBatch() returns.
                    if (submission != outgoingSubmission || outgoingFiles.isEmpty()) return@dispatch
                    activeOutgoingBatchId = batchId
                    state = state.copy(sendPending = false)
                }
            } catch (_: Exception) {
                dispatch(token) {
                    if (submission != outgoingSubmission || outgoingFiles.isEmpty()) return@dispatch
                    state.transfers.firstOrNull { it.id == activeOutgoingOperationId && it.busy }?.let {
                        upsert(it.copy(phase = DesktopFlashPhase.FAILED, detail = "No se pudo preparar el envío.", cancelling = false))
                    }
                    resetOutgoingBatch()
                    state = state.copy(batchSending = false, sendPending = false,
                        status = "No se pudo preparar el envío. Comprueba el archivo y que Flash siga activo en el otro equipo.",
                        error = true)
                }
            }
        }
    }

    private fun resetOutgoingBatch() {
        outgoingFiles = emptyList()
        activeOutgoingBatchId = null
        outgoingBatchTotal = 0
        outgoingBatchCompleted = 0
        activeOutgoingOperationId = null
        outgoingSubmission++
    }

    fun decide(displayed: FlashApproval, accepted: Boolean) {
        val token = generation
        if (!requests.decide(token, displayed)) {
            state = state.copy(status = "Esta solicitud cambió o caducó. Espera una nueva solicitud para decidir.", error = true)
            return
        }
        state = state.copy(
            session = state.session.copy(approvals = state.session.approvals.filterNot { it == displayed }),
            status = if (accepted) "Confirmación enviada. Esperando la decisión del otro equipo…" else "Solicitud rechazada.",
            error = false
        )
        command("La solicitud ya terminó. Vuelve a enviar el archivo si todavía lo necesitas.") { currentEngine ->
            val applied = currentEngine.approve(displayed.requestId, accepted)
            if (!applied) dispatch(token) { state = state.copy(status = "La solicitud ya terminó o caducó. No se aplicó la aprobación.", error = true) }
        }
    }

    fun cancel(operationId: String) {
        if (operationId == activeOutgoingOperationId) {
            // Stop the batch now even if an already queued receipt wins the transport cancellation race.
            state = state.copy(batchSending = false)
        }
        state.transfers.firstOrNull { it.id == operationId }?.let { upsert(it.copy(cancelling = true, detail = "Cancelando…")) }
        val batchId = if (operationId == activeOutgoingOperationId) activeOutgoingBatchId ?: operationId else operationId
        command("No se pudo cancelar esta operación. Comprueba su resultado antes de repetir.") { it.cancel(batchId) }
    }

    fun showFolder(file: File = state.directory) {
        runCatching {
            val folder = if (file.isDirectory) file else file.parentFile
            require(folder != null && folder.isDirectory)
            java.awt.Desktop.getDesktop().open(folder)
        }.onFailure { state = state.copy(status = "No se pudo abrir la carpeta. Comprueba su ubicación en Flash.", error = true) }
    }

    private fun command(failure: String, action: (DesktopFlashTransport) -> Unit) {
        if (closed.get()) return
        val token = generation
        commands.execute {
            if (!requests.isCurrent(token)) return@execute
            try {
                val current = engine ?: return@execute
                action(current)
            } catch (_: Exception) {
                dispatch(token) { state = state.copy(status = failure, error = true) }
            }
        }
    }

    private fun dispatch(token: Long, action: () -> Unit) {
        SwingUtilities.invokeLater { if (!closed.get() && requests.isCurrent(token)) action() }
    }

    private fun dispatchResult(action: () -> Unit) {
        SwingUtilities.invokeLater { if (!closed.get()) action() }
    }

    private fun upsert(transfer: DesktopFlashTransfer) {
        val previous = state.transfers.indexOfFirst { it.id == transfer.id }
        val entries = state.transfers.toMutableList()
        if (previous < 0) entries.add(transfer) else entries[previous] = transfer
        state = state.copy(transfers = entries.takeLast(20))
    }

    private fun applySnapshot(token: Long, emitted: FlashState) {
        // Engine callbacks are ordered. Never read a future snapshot ahead of a published receipt.
        val latest = emitted
        val approvals = requests.update(token, latest.approvals)
        latest.operations.forEach { operation ->
            val previous = state.transfers.firstOrNull { it.id == operation.id }
            val sourceFile = if (operation.outgoing && outgoingFiles.isNotEmpty() &&
                (activeOutgoingBatchId == operation.batchId || activeOutgoingBatchId == null)) {
                activeOutgoingBatchId = operation.batchId
                activeOutgoingOperationId = operation.id
                outgoingFiles.getOrNull(operation.fileIndex)
            } else null
            if (previous == null) {
                upsert(DesktopFlashTransfer(operation.id, operation.fileName, operation.peer?.label.orEmpty(), operation.totalBytes, operation.outgoing, file = sourceFile))
            } else if (previous.busy) {
                upsert(previous.copy(fileName = operation.fileName, peerLabel = operation.peer?.label.orEmpty(), totalBytes = operation.totalBytes, outgoing = operation.outgoing, file = previous.file ?: sourceFile))
            }
        }
        val justActivated = state.starting && latest.active
        val ended = state.session.active && !latest.active
        state = state.copy(
            session = latest.copy(approvals = approvals), starting = false,
            status = when {
                ended -> "El tiempo de Flash terminó. Actívalo de nuevo cuando lo necesites."
                justActivated -> "Flash activo durante 30 minutos. Actívalo también en el otro equipo."
                else -> state.status
            },
            error = if (justActivated || ended) false else state.error
        )
        if (ended) {
            requests.invalidate()
            progressTimer.stop()
            progressBuffer.clear()
            resetOutgoingBatch()
            state = state.copy(sendPending = false, batchSending = false, transfers = state.transfers.map {
                if (it.busy) it.copy(phase = DesktopFlashPhase.CANCELLED, detail = "La activación de Flash terminó.", cancelling = false) else it
            })
        }
        if (latest.operations.isEmpty() && activeOutgoingBatchId != null && !state.sendPending) {
            resetOutgoingBatch()
            state = state.copy(sendPending = false, batchSending = false)
        }
    }

    private fun listener(token: Long) = object : FlashListener {
        override fun onState(state: FlashState) {
            state.operations.forEach { knownOperations[token to it.id] = it }
            dispatch(token) { applySnapshot(token, state) }
        }

        override fun onApproval(approval: FlashApproval) = dispatch(token) {
            // The matching onState already registered this exact request before this event.
            state.transfers.firstOrNull { it.id == approval.operationId }?.let {
                upsert(it.copy(detail = "Compara el código de verificación en ambos equipos."))
            }
        }

        override fun onProgress(progress: FlashProgress) {
            if (!closed.get() && requests.isCurrent(token)) progressBuffer.offer(token, progress)
        }

        override fun onReceived(received: FlashReceived) {
            val known = knownOperations[token to received.operationId] ?: return
            // Stop can win the UI race after publication. Preserve this known operation's result only.
            dispatchResult {
                val previous = state.transfers.firstOrNull { it.id == received.operationId }
                    ?: DesktopFlashTransfer(received.operationId, received.file.name, received.peer.label, known.totalBytes, false)
                upsert(previous.copy(fileName = received.file.name, file = received.file, phase = DesktopFlashPhase.COMPLETE, transferredBytes = known.totalBytes, detail = "Archivo recibido y verificado.", cancelling = false))
                if (requests.isCurrent(token)) state = state.copy(status = "Archivo recibido y verificado: ${received.file.name}", error = false)
            }
        }

        override fun onCompleted(completed: FlashCompleted) {
            val known = knownOperations.remove(token to completed.operationId) ?: return
            dispatchResult {
                val previous = state.transfers.firstOrNull { it.id == completed.operationId }
                    ?: DesktopFlashTransfer(completed.operationId, completed.fileName, completed.peer.label, known.totalBytes, completed.outgoing)
                upsert(previous.copy(phase = DesktopFlashPhase.COMPLETE, transferredBytes = previous.totalBytes, detail = if (completed.outgoing) "Recepción confirmada por el otro equipo." else "Archivo recibido y verificado.", cancelling = false))
                if (requests.isCurrent(token)) {
                    val outgoingResult = completed.outgoing && known.batchId == activeOutgoingBatchId
                    val completedFile = if (outgoingResult) outgoingFiles.getOrNull(known.fileIndex) else previous.file
                    if (outgoingResult) {
                        outgoingBatchCompleted += 1
                    }
                    val remaining = (outgoingBatchTotal - outgoingBatchCompleted).coerceAtLeast(0)
                    val total = outgoingBatchTotal
                    if (outgoingResult && remaining == 0) resetOutgoingBatch()
                    state = state.copy(
                        selectedFiles = if (completed.outgoing) state.selectedFiles.filterNot { it == completedFile } else state.selectedFiles,
                        status = when {
                            !completed.outgoing -> state.status
                            outgoingResult && remaining > 0 -> "Entrega $outgoingBatchCompleted de $total confirmada. Preparando el siguiente archivo."
                            outgoingResult && total > 1 -> "$total archivos enviados y confirmados."
                            else -> "Archivo enviado. El otro equipo confirmó la recepción."
                        },
                        error = false,
                        sendPending = if (outgoingResult && remaining == 0) false else state.sendPending,
                        batchSending = if (outgoingResult) remaining > 0 && state.batchSending else state.batchSending
                    )
                }
            }
        }

        override fun onError(error: FlashError) {
            error.operationId?.let { knownOperations.remove(token to it) }
            dispatch(token) {
            val received = state.transfers.firstOrNull { it.id == error.operationId && !it.outgoing && it.phase == DesktopFlashPhase.COMPLETE && it.file != null }
            error.operationId?.let { id ->
                state.transfers.firstOrNull { it.id == id }?.let {
                    if (it.phase != DesktopFlashPhase.COMPLETE) upsert(it.copy(
                        phase = if (error.code == "cancelled") DesktopFlashPhase.CANCELLED else DesktopFlashPhase.FAILED,
                        detail = error.message, cancelling = false
                    ))
                }
            }
            val outgoingFailure = error.operationId != null && error.operationId == activeOutgoingOperationId
            if (outgoingFailure) resetOutgoingBatch()
            state = state.copy(
                status = if (received != null) "${received.fileName} se recibió y verificó. No se pudo confirmar el resultado al emisor." else error.message,
                error = received == null && error.code != "cancelled" && error.code != "rejected",
                sendPending = if (outgoingFailure) false else state.sendPending,
                batchSending = if (outgoingFailure) false else state.batchSending
            )
            }
        }
    }

    private fun flushProgress() {
        progressBuffer.drain().forEach { (token, progress) ->
            if (requests.isCurrent(token)) {
                state.transfers.firstOrNull { it.id == progress.operationId }?.let {
                    if (it.busy) upsert(it.copy(transferredBytes = progress.transferredBytes, totalBytes = progress.totalBytes, phase = DesktopFlashPhase.TRANSFERRING, detail = "Transfiriendo…"))
                }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        requests.invalidate()
        progressTimer.stop()
        progressBuffer.clear()
        knownOperations.clear()
        resetOutgoingBatch()
        commands.execute { runCatching { engine?.stop() }; engine = null }
        commands.shutdown()
    }
}
