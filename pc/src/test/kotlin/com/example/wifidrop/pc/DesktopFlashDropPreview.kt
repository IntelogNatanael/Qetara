package com.example.wifidrop.pc

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.unit.dp
import com.example.wifidrop.protocol.flash.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.dnd.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.*

/**
 * Manual native DnD fixture. Run with an output directory and optionally "legacy" to reproduce
 * the old JFrame-only drop target. A single window contains native file sources and the production
 * Flash composer/drop modifier. No OS input, production preferences, clipboard or sockets are used.
 * Drag sources with the mouse; choose a receiver and press Send explicitly in the real composer.
 * The transport is synthetic; event/state evidence is written to native-drop-events.txt.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
object DesktopFlashDropPreview {
    private const val DRAFT = "Borrador intacto: ñ, 你好 👋\nSegunda línea."

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isNotEmpty()) { "Supply a fixture output directory" }
        val output = File(args[0]).absoluteFile
        check(output.isDirectory || output.mkdirs())
        val legacy = args.getOrNull(1) == "legacy"
        val first = File(output, "arrastrado-uno.txt").apply { writeText("Primer archivo original.\n") }
        val second = File(output, "arrastrado-dos-ñ.txt").apply { writeText("Segundo archivo original.\n") }
        val third = File(output, "arrastrado-tres.txt").apply { writeText("Tercer archivo original.\n") }
        val directory = File(output, "carpeta-no-adjuntable").apply { mkdirs() }
        val log = File(output, "native-drop-events.txt").apply { writeText("mode=${if (legacy) "legacy" else "compose"}\n") }
        SwingUtilities.invokeLater {
            var transport: PreviewTransport? = null
            fun record(message: String) { log.appendText("$message\n") }
            val controller = DesktopFlashController(output) { _, _, listener ->
                PreviewTransport(listener, ::record).also { transport = it }
            }
            controller.updateTextDraft(DRAFT)
            controller.start("Prueba de arrastre")
            val frame = JFrame("Qetara · prueba de arrastre ${if (legacy) "anterior" else "corregido"}")
            frame.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
            frame.layout = BorderLayout()
            val sourcePanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = BorderFactory.createEmptyBorder(16, 12, 16, 12)
                preferredSize = Dimension(230, 780)
            }
            fun source(title: String, transferable: Transferable, actions: Int = DnDConstants.ACTION_COPY_OR_MOVE) {
                val label = JLabel(title, SwingConstants.CENTER).apply {
                    maximumSize = Dimension(206, 54)
                    preferredSize = Dimension(206, 54)
                    minimumSize = preferredSize
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(java.awt.Color(0x0A6B77)),
                        BorderFactory.createEmptyBorder(10, 6, 10, 6)
                    )
                }
                DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(
                    label, actions
                ) { gesture ->
                    record("start=$title proposedAction=${gesture.dragAction}")
                    gesture.startDrag(null, transferable, object : DragSourceAdapter() {
                        override fun dragDropEnd(event: DragSourceDropEvent) {
                            record("end=$title success=${event.dropSuccess} action=${event.dropAction}")
                        }
                    })
                }
                sourcePanel.add(label)
                sourcePanel.add(javax.swing.Box.createVerticalStrut(10))
            }
            source("Arrastrar 1 archivo", FileTransfer(listOf(first)))
            source("Arrastrar 2 archivos", FileTransfer(listOf(second, third)))
            source("Arrastrar duplicados", FileTransfer(listOf(first, second, first)))
            source("Arrastrar carpeta", FileTransfer(listOf(directory)))
            source("Arrastrar archivo + carpeta", FileTransfer(listOf(first, directory)))
            source("Arrastrar texto (rechazar)", StringSelection("No debe reemplazar el borrador"))
            source("Solo mover (rechazar)", FileTransfer(listOf(third)), DnDConstants.ACTION_MOVE)
            fun action(title: String, block: () -> Unit) {
                sourcePanel.add(JButton(title).apply {
                    alignmentX = 0.5f
                    addActionListener { block() }
                })
                sourcePanel.add(javax.swing.Box.createVerticalStrut(8))
            }
            action("Quitar adjuntos") { controller.clearFiles() }
            action("Simular ocupado / libre") { transport?.toggleBusy() }
            action("Confirmar envío simulado") { transport?.completeBatch() }
            val evidence = JLabel().apply { maximumSize = Dimension(206, 140) }
            sourcePanel.add(evidence)
            frame.add(sourcePanel, BorderLayout.WEST)
            val panel = ComposePanel()
            panel.setContent {
                var dragging by remember { mutableStateOf(false) }
                MaterialTheme(colors = qetaraDesktopColors, typography = qetaraTypography, shapes = qetaraShapes) {
                    val modifier = if (legacy) Modifier else Modifier.desktopFileDropTarget(
                        enabled = controller.state.canAttachFiles,
                        onDragActiveChange = { dragging = it },
                        onFilesDropped = { files ->
                            controller.chooseDroppedFiles(files).also { accepted ->
                                record("drop accepted=$accepted files=${files.map { it.name }}")
                            }
                        },
                        onFailure = { controller.fileDropFailed(); record("drop error=${it.javaClass.simpleName}") }
                    )
                    Box(modifier.fillMaxSize().padding(16.dp)) {
                        DesktopFlashPanel(controller, "Prueba de arrastre", emptyList(), {},
                            onChooseFile = { controller.chooseFiles(listOf(first)) },
                            onChooseDirectory = {}, isFileDragActive = dragging)
                    }
                }
            }
            frame.add(panel, BorderLayout.CENTER)
            if (legacy) {
                DropTarget(frame, object : DropTargetAdapter() {
                    override fun dragEnter(event: DropTargetDragEvent) {
                        if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) event.acceptDrag(DnDConstants.ACTION_COPY)
                        else event.rejectDrag()
                    }
                    override fun drop(event: DropTargetDropEvent) {
                        event.acceptDrop(DnDConstants.ACTION_COPY)
                        val files = (event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>()
                        record("legacy frame received=${files.map { it.name }}")
                        event.dropComplete(controller.chooseDroppedFiles(files))
                    }
                })
            }
            var lastEvidence = ""
            val timer = Timer(100) {
                val state = controller.state
                val current = "files=${state.selectedFiles.map { it.name }} draftIntact=${state.textDraft == DRAFT} " +
                    "receiver=${state.selectedPeer?.label} busy=${state.busy} batches=${transport?.batches?.size ?: 0}"
                if (current != lastEvidence) {
                    lastEvidence = current
                    record(current)
                }
                evidence.text = "<html>Adjuntos: ${state.selectedFiles.size}<br>Borrador intacto: ${state.textDraft == DRAFT}" +
                    "<br>Envíos explícitos: ${transport?.batches?.size ?: 0}<br>Ocupado: ${state.busy}</html>"
            }
            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(event: WindowEvent) {
                    timer.stop()
                    controller.close()
                    panel.dispose()
                }
            })
            frame.setSize(1260, 820)
            frame.setLocationRelativeTo(null)
            frame.isVisible = true
            timer.start()
        }
    }

    private class FileTransfer(private val files: List<File>) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor
        override fun getTransferData(flavor: DataFlavor): Any {
            if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
            return files
        }
    }

    private class PreviewTransport(val listener: FlashListener, private val record: (String) -> Unit) : DesktopFlashTransport {
        val batches = CopyOnWriteArrayList<List<File>>()
        private val peer = FlashPeer("drop-preview", "Receptor de prueba", "127.0.0.2", 8989, Long.MAX_VALUE)
        private var operations = emptyList<FlashOperation>()
        private var activeBatch = emptyList<File>()
        private fun publish() = listener.onState(FlashState(active = true, expiresAtMs = System.currentTimeMillis() + 1_800_000,
            peers = listOf(peer), operations = operations))
        override fun start() { publish() }
        override fun stop() = Unit
        override fun discover() { publish() }
        override fun discoverAt(address: String, port: Int) { publish() }
        override fun approve(requestId: String, accepted: Boolean) = true
        override fun cancel(operationId: String): Boolean {
            listener.onError(FlashError(operationId, "cancelled", "Transferencia de prueba cancelada."))
            operations = emptyList()
            activeBatch = emptyList()
            publish()
            return true
        }
        override fun sendBatch(files: List<File>, peer: FlashPeer): String {
            batches += files.toList()
            activeBatch = files.toList()
            record("explicit-send=${files.map { it.name }}")
            operations = listOf(operation(0))
            publish()
            return "preview-batch-${batches.size}"
        }
        private fun operation(index: Int): FlashOperation {
            val file = activeBatch[index]
            val batch = "preview-batch-${batches.size}"
            return FlashOperation("$batch-$index", peer, file.name, file.length(), true,
                batchId = batch, fileIndex = index, fileCount = activeBatch.size)
        }
        fun completeBatch() {
            activeBatch.indices.forEach { index ->
                val operation = operation(index)
                operations = listOf(operation)
                publish()
                listener.onCompleted(FlashCompleted(operation.id, peer, operation.fileName, true))
            }
            activeBatch = emptyList()
            operations = emptyList()
            publish()
        }
        fun toggleBusy() {
            if (activeBatch.isNotEmpty()) return
            operations = if (operations.isEmpty()) listOf(FlashOperation("preview-incoming", peer, "en-curso.txt", 100, false))
            else {
                listener.onError(FlashError("preview-incoming", "cancelled", "Fin de la transferencia de prueba."))
                emptyList()
            }
            publish()
        }
    }
}
