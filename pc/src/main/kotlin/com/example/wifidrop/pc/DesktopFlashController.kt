package com.example.wifidrop.pc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.wifidrop.protocol.flash.*
import java.io.File
import java.net.BindException
import java.nio.file.Files
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
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
    val detailText: DesktopFlashText = flashText("Preparando conexión y verificación…", "Preparing connection and verification…"),
    val file: File? = null,
    val cancelling: Boolean = false,
    val textBody: String? = null
) {
    val detail: String get() = detailText.resolve()
    val busy: Boolean get() = phase == DesktopFlashPhase.WAITING || phase == DesktopFlashPhase.TRANSFERRING
}

internal data class DesktopFlashUiState(
    val session: FlashState = FlashState(),
    val starting: Boolean = false,
    val sendPending: Boolean = false,
    val batchSending: Boolean = false,
    val directory: File,
    val selectedFiles: List<File> = emptyList(),
    val receiverSelection: FlashPeer? = null,
    val textDraft: String = "",
    val transfers: List<DesktopFlashTransfer> = emptyList(),
    val statusText: DesktopFlashText = flashText("Flash está desactivado.", "Flash is off."),
    val statusRevision: Long = 0,
    val error: Boolean = false
) {
    val status: String get() = statusText.resolve()
    val busy: Boolean get() = sendPending || batchSending || session.operations.isNotEmpty() || transfers.any { it.busy }
    val canAttachFiles: Boolean get() = !starting && !busy
    val selectedFile: File? get() = selectedFiles.firstOrNull()
    // A discovery refresh may update the label or expiry, but must not silently change the destination.
    val selectedPeer: FlashPeer? get() = receiverSelection?.let { selected ->
        session.peers.firstOrNull {
            it.id == selected.id && it.address == selected.address && it.port == selected.port
        }
    }
}

/** The system clipboard is touched only by the explicit Paste and Copy actions. */
internal interface DesktopFlashClipboard {
    fun readText(): String?
    fun writeText(text: String)
}

private object SystemDesktopFlashClipboard : DesktopFlashClipboard {
    override fun readText(): String? {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        return if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) clipboard.getData(DataFlavor.stringFlavor) as? String else null
    }
    override fun writeText(text: String) = Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

/** One controller survives navigation; engine callbacks never mutate Compose state off the EDT. */
internal class DesktopFlashController(
    initialDirectory: File,
    private val clipboard: DesktopFlashClipboard = SystemDesktopFlashClipboard,
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
    private class OutgoingText(val body: String) {
        val abandoned = AtomicBoolean(false)
        @Volatile var directory: File? = null
        @Volatile var file: File? = null
    }
    private var outgoingText: OutgoingText? = null
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
        state = state.copy(starting = true, statusText = flashText("Activando Flash…", "Turning on Flash…"), error = false, receiverSelection = null,
            transfers = state.transfers.filter { it.textBody == null })
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
                        statusText = if (error is BindException) flashText("Flash no pudo abrir el puerto 8989. Cierra otra instancia de Qetara con Flash activo y vuelve a intentar.", "Flash could not open port 8989. Close another Qetara instance with Flash active and try again.")
                        else flashText("No se pudo activar Flash. Comprueba la carpeta de destino y vuelve a intentar.", "Could not turn on Flash. Check the destination folder and try again.")
                    )
                }
            }
        }
    }

    fun stop() {
        requests.invalidate()
        // Stop the transport before queued cleanup can encounter its still-open source handle.
        if (!closed.get()) commands.execute {
            engine?.stop()
            engine = null
        }
        progressTimer.stop()
        progressBuffer.clear()
        resetOutgoingBatch()
        state = state.copy(
            session = FlashState(), starting = false, sendPending = false, batchSending = false,
            receiverSelection = null, error = false,
            statusText = flashText("Flash desactivado. Los archivos ya guardados se conservan.", "Flash is off. Files already saved are kept."),
            transfers = state.transfers.map {
                if (it.busy) it.copy(phase = DesktopFlashPhase.CANCELLED, detailText = flashText("Flash se desactivó. Comprueba el otro equipo antes de repetir.", "Flash was turned off. Check the other device before trying again."), cancelling = false) else it
            }
        )
    }

    fun chooseDirectory(directory: File) {
        if (!state.session.active && !state.starting) state = state.copy(directory = directory, error = false)
    }

    fun chooseFile(file: File) = chooseFiles(listOf(file))

    fun chooseFiles(files: List<File>) {
        if (!state.canAttachFiles) return
        if (files.isEmpty()) return
        val readable = files.filter { it.isFile && it.canRead() }
        if (readable.isEmpty()) {
            state = state.copy(statusText = flashText("Elige un archivo que puedas abrir. Para compartir una carpeta, comprímela primero.", "Choose a readable file. To share a folder, compress it first."), error = true)
            return
        }
        val selected = mergeDesktopFileSelections(state.selectedFiles, readable)
        val ignored = files.size - readable.size
        state = state.copy(
            selectedFiles = selected,
            statusText = when {
                ignored > 0 -> flashText("${selected.size} archivos preparados; se omitieron $ignored elementos no legibles.", "${selected.size} files ready; $ignored unreadable items were skipped.")
                selected.size == 1 -> flashText("Archivo elegido. Selecciona el otro equipo.", "File attached. Choose the other device.")
                else -> flashText("${selected.size} archivos preparados. Se enviarán en un lote, uno después de otro.", "${selected.size} files ready. They will be sent in one batch, one after another.")
            },
            error = false
        )
    }

    fun removeFile(file: File) {
        if (!state.canAttachFiles || file !in state.selectedFiles) return
        state = state.copy(selectedFiles = state.selectedFiles.filterNot { it == file }, error = false,
            statusText = flashText("Archivo quitado de la selección.", "File removed from the selection."))
    }

    fun clearFiles() {
        if (!state.busy) state = state.copy(selectedFiles = emptyList())
    }

    fun updateTextDraft(text: String) {
        val error = FlashText.validationCode(text)
        if (error != null) {
            state = state.copy(statusText = flashValidationText(error), error = true)
            return
        }
        state = state.copy(textDraft = text, error = false)
    }

    fun pasteText(selectionStart: Int = state.textDraft.length, selectionEnd: Int = selectionStart): Int? {
        if (!state.session.active || state.busy) return null
        return runCatching { clipboard.readText() }.fold(
            onSuccess = { text ->
                if (text == null) {
                    state = state.copy(statusText = flashText("El portapapeles no contiene texto.", "The clipboard does not contain text."), error = false)
                    null
                }
                else {
                    val draft = state.textDraft
                    val start = minOf(selectionStart, selectionEnd).coerceIn(0, draft.length)
                    val end = maxOf(selectionStart, selectionEnd).coerceIn(start, draft.length)
                    // Validate the clipboard before concatenation, so an oversized value is never copied into draft state.
                    val error = FlashText.validationCode(text)
                    if (error != null) state = state.copy(statusText = flashValidationText(error), error = true)
                    else updateTextDraft(draft.substring(0, start) + text + draft.substring(end))
                    if (!state.error) {
                        state = state.copy(statusText = flashText("Texto pegado. Revísalo antes de enviar.", "Text pasted. Review it before sending."))
                        start + text.length
                    } else null
                }
            },
            onFailure = {
                state = state.copy(statusText = flashText("No se pudo leer el portapapeles. Vuelve a pulsar Pegar.", "Could not read the clipboard. Try Paste again."), error = true)
                null
            }
        )
    }

    fun copyText(operationId: String) {
        val body = state.transfers.firstOrNull { it.id == operationId }?.textBody ?: return
        runCatching { clipboard.writeText(body) }.fold(
            // Repeated explicit actions must reannounce feedback even when their copy is identical.
            onSuccess = { state = state.copy(statusText = flashText("Texto copiado.", "Text copied."), statusRevision = state.statusRevision + 1, error = false) },
            onFailure = { state = state.copy(statusText = flashText("No se pudo copiar el texto. Vuelve a intentarlo.", "Could not copy the text. Try again."), statusRevision = state.statusRevision + 1, error = true) }
        )
    }

    fun sendText() {
        val current = state
        val peer = current.selectedPeer ?: return
        if (!current.session.active || current.busy || peer.expiresAtMs <= System.currentTimeMillis() || current.textDraft.isBlank()) return
        val error = FlashText.validationCode(current.textDraft)
        if (error != null) {
            state = state.copy(statusText = flashValidationText(error), error = true)
            return
        }
        beginOutgoing(emptyList(), peer, OutgoingText(current.textDraft))
    }

    fun chooseDroppedFiles(files: List<File>): Boolean {
        if (!state.canAttachFiles) return false
        if (files.none { it.isFile && it.canRead() }) {
            state = state.copy(statusText = flashText("Suelta un archivo. Para compartir una carpeta, comprímela primero.", "Drop a file. To share a folder, compress it first."), error = true)
            return false
        }
        chooseFiles(files)
        return true
    }

    fun fileDropFailed() {
        state = state.copy(statusText = flashText("No se pudieron leer los archivos soltados. Vuelve a arrastrarlos o usa Adjuntar.", "Could not read the dropped files. Drop them again or use Attach."), error = true)
    }

    fun choosePeer(peer: FlashPeer) {
        if (!state.session.active || state.busy) return
        val available = state.session.peers.firstOrNull {
            it.id == peer.id && it.address == peer.address && it.port == peer.port &&
                it.expiresAtMs > System.currentTimeMillis()
        } ?: return
        state = state.copy(receiverSelection = available, error = false)
    }

    fun discover() {
        if (!state.session.active) return
        state = state.copy(statusText = flashText("Búsqueda solicitada. Los equipos con Flash activo aparecerán aquí.", "Searching. Devices with Flash active will appear here."), error = false)
        command(flashText("No se pudo buscar. Comprueba la red e inténtalo de nuevo.", "Could not search. Check the network and try again.")) { it.discover() }
    }

    fun discoverAt(address: String, port: String) {
        val number = port.toIntOrNull()
        if (number == null || number !in 1..65535) {
            state = state.copy(statusText = flashText("El puerto debe estar entre 1 y 65535.", "The port must be between 1 and 65535."), error = true)
            return
        }
        if (!state.session.active) return
        state = state.copy(statusText = flashText("Búsqueda solicitada para esa dirección. Selecciona el equipo cuando aparezca.", "Searching that address. Choose the device when it appears."), error = false)
        command(flashText("No se pudo buscar esa dirección. Usa una IP local y comprueba que Flash esté activo allí.", "Could not search that address. Use a local IP and check that Flash is active there.")) {
            it.discoverAt(address.trim(), number)
        }
    }

    fun send() {
        val current = state
        val files = current.selectedFiles
        if (files.isEmpty()) return
        if (files.size > FLASH_MAX_BATCH_FILES) {
            state = state.copy(statusText = flashText("Puedes enviar hasta $FLASH_MAX_BATCH_FILES archivos por lote. Reduce la selección.", "You can send up to $FLASH_MAX_BATCH_FILES files per batch. Select fewer files."), error = true)
            return
        }
        val peer = current.selectedPeer ?: return
        if (!current.session.active || current.busy || peer.expiresAtMs <= System.currentTimeMillis()) return
        if (files.any { !it.isFile || !it.canRead() }) {
            state = state.copy(statusText = flashText("Uno de los archivos ya no está disponible. Revisa la selección.", "One file is no longer available. Review your selection."), error = true)
            return
        }
        beginOutgoing(files, peer)
    }

    private fun beginOutgoing(files: List<File>, peer: FlashPeer, text: OutgoingText? = null) {
        outgoingText = text
        outgoingFiles = files.toList()
        outgoingBatchTotal = if (text != null) 1 else files.size
        outgoingBatchCompleted = 0
        state = state.copy(
            batchSending = true, sendPending = true,
            statusText = if (text != null) flashText("Preparando el texto. Después compara el código en ambos equipos.", "Preparing the message. Then compare the code on both devices.")
            else if (files.size == 1) flashText("Preparando el archivo. Después compara el código en ambos equipos.", "Preparing the file. Then compare the code on both devices.")
            else flashText("Preparando ${files.size} archivos. Una verificación autorizará este lote.", "Preparing ${files.size} files. One verification will approve this batch."),
            error = false
        )
        val token = generation
        activeOutgoingOperationId = null
        activeOutgoingBatchId = null
        val submission = ++outgoingSubmission
        commands.execute {
            if (!requests.isCurrent(token)) return@execute
            try {
                val sources = if (text != null) {
                    val directory = Files.createTempDirectory("qetara-flash-text-").toFile()
                    text.directory = directory
                    listOf(FlashText.createFile(directory, text.body).also { text.file = it })
                } else files
                if (!requests.isCurrent(token) || text?.abandoned?.get() == true) return@execute
                val batchId = checkNotNull(engine).sendBatch(sources, peer)
                dispatch(token) {
                    // All callbacks, including completion, can arrive before sendBatch() returns.
                    if (submission != outgoingSubmission) return@dispatch
                    outgoingFiles = sources
                    activeOutgoingBatchId = batchId
                    state = state.copy(sendPending = false)
                }
            } catch (_: Exception) {
                dispatch(token) {
                    if (submission != outgoingSubmission) return@dispatch
                    state.transfers.firstOrNull { it.id == activeOutgoingOperationId && it.busy }?.let {
                        upsert(it.copy(phase = DesktopFlashPhase.FAILED, detailText = flashText("No se pudo preparar el envío.", "Could not prepare the transfer."), cancelling = false))
                    }
                    resetOutgoingBatch()
                    state = state.copy(batchSending = false, sendPending = false,
                        statusText = flashText("No se pudo preparar el envío. Comprueba el archivo y que Flash siga activo en el otro equipo.", "Could not prepare the transfer. Check the file and that Flash is still active on the other device."),
                        error = true)
                }
            }
        }
    }

    private fun resetOutgoingBatch() {
        outgoingText?.let { text ->
            text.abandoned.set(true)
            // Serialized after staging, so Stop cannot leave a file created by an in-flight preparation.
            commands.execute {
                val file = text.file
                var deleted = file == null || !file.exists()
                repeat(10) {
                    if (!deleted) {
                        deleted = file?.delete() == true || file?.exists() == false
                        if (!deleted) Thread.sleep(100)
                    }
                }
                text.directory?.let { if (!it.delete() && it.exists()) it.deleteOnExit() }
                if (!deleted) file?.deleteOnExit()
            }
        }
        outgoingText = null
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
            state = state.copy(statusText = flashText("Esta solicitud cambió o caducó. Espera una nueva solicitud para decidir.", "This request changed or expired. Wait for a new request before deciding."), error = true)
            return
        }
        state = state.copy(
            session = state.session.copy(approvals = state.session.approvals.filterNot { it == displayed }),
            statusText = if (accepted) flashText("Confirmación enviada. Esperando la decisión del otro equipo…", "Approval sent. Waiting for the other device…") else flashText("Solicitud rechazada.", "Request rejected."),
            error = false
        )
        command(flashText("La solicitud ya terminó. Vuelve a enviar el archivo si todavía lo necesitas.", "The request has ended. Send the file again if needed.")) { currentEngine ->
            val applied = currentEngine.approve(displayed.requestId, accepted)
            if (!applied) dispatch(token) { state = state.copy(statusText = flashText("La solicitud ya terminó o caducó. No se aplicó la aprobación.", "The request has ended or expired. Approval was not applied."), error = true) }
        }
    }

    fun cancel(operationId: String) {
        if (operationId == activeOutgoingOperationId) {
            // Stop the batch now even if an already queued receipt wins the transport cancellation race.
            state = state.copy(batchSending = false)
        }
        state.transfers.firstOrNull { it.id == operationId }?.let { upsert(it.copy(cancelling = true, detailText = flashText("Cancelando…", "Cancelling…"))) }
        val batchId = if (operationId == activeOutgoingOperationId) activeOutgoingBatchId ?: operationId else operationId
        command(flashText("No se pudo cancelar esta operación. Comprueba su resultado antes de repetir.", "Could not cancel this operation. Check its result before trying again.")) { it.cancel(batchId) }
    }

    fun showFolder(file: File = state.directory) {
        runCatching {
            val folder = if (file.isDirectory) file else file.parentFile
            require(folder != null && folder.isDirectory)
            java.awt.Desktop.getDesktop().open(folder)
        }.onFailure { state = state.copy(statusText = flashText("No se pudo abrir la carpeta. Comprueba su ubicación en Flash.", "Could not open the folder. Check its location in Flash."), error = true) }
    }

    private fun command(failure: DesktopFlashText, action: (DesktopFlashTransport) -> Unit) {
        if (closed.get()) return
        val token = generation
        commands.execute {
            if (!requests.isCurrent(token)) return@execute
            try {
                val current = engine ?: return@execute
                action(current)
            } catch (_: Exception) {
                dispatch(token) { state = state.copy(statusText = failure, error = true) }
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
            val sources = outgoingText?.file?.let(::listOf) ?: outgoingFiles
            val sourceFile = if (operation.outgoing && sources.isNotEmpty() &&
                (outgoingText == null || sources.getOrNull(operation.fileIndex)?.name == operation.fileName) &&
                (activeOutgoingBatchId == operation.batchId || activeOutgoingBatchId == null)) {
                activeOutgoingBatchId = operation.batchId
                activeOutgoingOperationId = operation.id
                sources.getOrNull(operation.fileIndex)
            } else null
            val body = outgoingText?.takeIf { sourceFile != null && it.file == sourceFile }?.body
            if (previous == null) {
                upsert(DesktopFlashTransfer(operation.id, operation.fileName, operation.peer?.label.orEmpty(), operation.totalBytes, operation.outgoing, file = sourceFile, textBody = body))
            } else if (previous.busy) {
                upsert(previous.copy(fileName = operation.fileName, peerLabel = operation.peer?.label.orEmpty(), totalBytes = operation.totalBytes, outgoing = operation.outgoing, file = previous.file ?: sourceFile, textBody = previous.textBody ?: body))
            }
        }
        val justActivated = state.starting && latest.active
        val ended = state.session.active && !latest.active
        state = state.copy(
            session = latest.copy(approvals = approvals), starting = false,
            receiverSelection = if (ended) null else state.receiverSelection,
            statusText = when {
                ended -> flashText("El tiempo de Flash terminó. Actívalo de nuevo cuando lo necesites.", "Flash expired. Turn it on again when needed.")
                justActivated -> flashText("Flash activo durante 30 minutos. Actívalo también en el otro equipo.", "Flash is active for 30 minutes. Turn it on on the other device too.")
                else -> state.statusText
            },
            error = if (justActivated || ended) false else state.error
        )
        if (ended) {
            requests.invalidate()
            progressTimer.stop()
            progressBuffer.clear()
            resetOutgoingBatch()
            state = state.copy(sendPending = false, batchSending = false, transfers = state.transfers.map {
                if (it.busy) it.copy(phase = DesktopFlashPhase.CANCELLED, detailText = flashText("La activación de Flash terminó.", "The Flash session ended."), cancelling = false) else it
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
                upsert(it.copy(detailText = flashText("Compara el código de verificación en ambos equipos.", "Compare the verification code on both devices.")))
            }
        }

        override fun onProgress(progress: FlashProgress) {
            if (!closed.get() && requests.isCurrent(token)) progressBuffer.offer(token, progress)
        }

        override fun onReceived(received: FlashReceived) {
            val known = knownOperations[token to received.operationId] ?: return
            val body = FlashText.readReceived(received.file)
            // Stop can win the UI race after publication. Preserve this known operation's result only.
            dispatchResult {
                val previous = state.transfers.firstOrNull { it.id == received.operationId }
                    ?: DesktopFlashTransfer(received.operationId, received.file.name, received.peer.label, known.totalBytes, false)
                upsert(previous.copy(fileName = received.file.name, file = received.file, phase = DesktopFlashPhase.COMPLETE, transferredBytes = known.totalBytes, detailText = if (body != null) flashText("Texto recibido y verificado.", "Text received and verified.") else flashText("Archivo recibido y verificado.", "File received and verified."), cancelling = false, textBody = body))
                if (requests.isCurrent(token)) state = state.copy(statusText = if (body != null) flashText("Texto recibido y verificado.", "Text received and verified.") else flashText("Archivo recibido y verificado: ${received.file.name}", "File received and verified: ${received.file.name}"), error = false)
            }
        }

        override fun onCompleted(completed: FlashCompleted) {
            val known = knownOperations.remove(token to completed.operationId) ?: return
            dispatchResult {
                val previous = state.transfers.firstOrNull { it.id == completed.operationId }
                    ?: DesktopFlashTransfer(completed.operationId, completed.fileName, completed.peer.label, known.totalBytes, completed.outgoing)
                upsert(previous.copy(phase = DesktopFlashPhase.COMPLETE, transferredBytes = previous.totalBytes, detailText = if (completed.outgoing) flashText("Recepción confirmada por el otro equipo.", "Receipt confirmed by the other device.") else if (previous.textBody != null) flashText("Texto recibido y verificado.", "Text received and verified.") else flashText("Archivo recibido y verificado.", "File received and verified."), cancelling = false))
                if (requests.isCurrent(token)) {
                    val outgoingResult = completed.outgoing && known.batchId == activeOutgoingBatchId
                    val completedFile = if (outgoingResult) outgoingText?.file ?: outgoingFiles.getOrNull(known.fileIndex) else previous.file
                    val sentText = if (outgoingResult) outgoingText?.body else null
                    if (outgoingResult) {
                        outgoingBatchCompleted += 1
                    }
                    val remaining = (outgoingBatchTotal - outgoingBatchCompleted).coerceAtLeast(0)
                    val total = outgoingBatchTotal
                    if (outgoingResult && remaining == 0) resetOutgoingBatch()
                    state = state.copy(
                        selectedFiles = if (completed.outgoing) state.selectedFiles.filterNot { it == completedFile } else state.selectedFiles,
                        textDraft = if (sentText != null && remaining == 0 && state.textDraft == sentText) "" else state.textDraft,
                        statusText = when {
                            !completed.outgoing -> state.statusText
                            sentText != null -> flashText("Texto enviado. El otro equipo confirmó la recepción.", "Message sent. The other device confirmed receipt.")
                            outgoingResult && remaining > 0 -> flashText("Entrega $outgoingBatchCompleted de $total confirmada. Preparando el siguiente archivo.", "Delivery $outgoingBatchCompleted of $total confirmed. Preparing the next file.")
                            outgoingResult && total > 1 -> flashText("$total archivos enviados y confirmados.", "$total files sent and confirmed.")
                            else -> flashText("Archivo enviado. El otro equipo confirmó la recepción.", "File sent. The other device confirmed receipt.")
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
                        detailText = flashFailureText(error.code), cancelling = false
                    ))
                }
            }
            val outgoingFailure = error.operationId != null && error.operationId == activeOutgoingOperationId
            if (outgoingFailure) resetOutgoingBatch()
            state = state.copy(
                statusText = if (received != null) flashText("${if (received.textBody != null) "El texto" else received.fileName} se recibió y verificó. No se pudo confirmar el resultado al emisor.", "${if (received.textBody != null) "The text" else received.fileName} was received and verified. The sender could not be notified.") else flashFailureText(error.code),
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
                    if (it.busy) upsert(it.copy(transferredBytes = progress.transferredBytes, totalBytes = progress.totalBytes, phase = DesktopFlashPhase.TRANSFERRING, detailText = flashText("Transfiriendo…", "Transferring…")))
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
        commands.execute { runCatching { engine?.stop() }; engine = null }
        resetOutgoingBatch()
        commands.shutdown()
    }
}
