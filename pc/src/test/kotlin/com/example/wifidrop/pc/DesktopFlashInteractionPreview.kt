package com.example.wifidrop.pc

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.example.wifidrop.protocol.flash.*
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import org.jetbrains.skia.EncodedImageFormat

/**
 * Manual interaction smoke tool for the production desktop composables; deliberately not JUnit.
 * Arguments: output directory, optional points.json, optional capture|run (default capture).
 * Coordinates must be measured from the current rendered workspace at the configured dimensions.
 * Example JSON shape (replace points with observed positions):
 * {"width":1160,"height":800,"editor":[0,0],"paste":[0,0],"receiver":[0,0],
 *  "receiverChoice":[0,0],"viewText":[0,0],"copyText":[0,0]}
 * The last two points are optional and refer to the received-text card and its open dialog.
 * No OS input, system clipboard, live transport, preferences, reflection or test UI dependencies.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
object DesktopFlashInteractionPreview {
    private const val INITIAL_DRAFT = "Revisión del proyecto: áéíóú 🙂\nConserva este borrador."
    private const val PASTED_TEXT = "Texto pegado — ñ, á y 🙂\nSegunda línea completa."
    private val RECEIVED_TEXT = "Texto recibido completo: áéíóú 🙂\n" +
        (1..12).joinToString("\n") { "Párrafo $it: conservar este contenido al expandirlo y copiarlo." }

    @JvmStatic
    fun main(args: Array<String>) {
        val output = File(args.getOrElse(0) { ".local/design-review/interaction" }).absoluteFile
        check(output.isDirectory || output.mkdirs())
        val points = Points(args.getOrNull(1)?.let { File(it).readText() }.orEmpty())
        val mode = args.getOrElse(2) { "capture" }
        require(mode in listOf("capture", "run")) { "Mode must be capture or run" }
        if (mode == "capture") {
            Fixture(output, points, selectPeer = false).use { fixture ->
                fixture.snapshot("interaction-baseline")
                if (points.has("receiver")) {
                    fixture.click("receiver")
                    fixture.snapshot("receiver-dialog")
                    fixture.press(Key.Escape)
                }
                fixture.receiveText()
                fixture.snapshot("received-text-baseline")
                if (points.has("viewText")) {
                    fixture.click("viewText")
                    fixture.snapshot("received-text-dialog")
                }
            }
            println("Captured production UI. Supply observed points and use mode run for interaction checks.")
            return
        }
        listOf("editor", "paste", "receiver", "receiverChoice").forEach { points.required(it) }
        val results = mutableListOf<String>()
        fun runCase(name: String, selectPeer: Boolean = true, hasReceiver: Boolean = true, action: Fixture.() -> Unit) {
            Fixture(output, points, selectPeer, hasReceiver).use { fixture ->
                try {
                    fixture.action()
                    fixture.snapshot("$name-passed")
                    results += "PASS $name"
                    println(results.last())
                } catch (failure: Throwable) {
                    fixture.snapshot("$name-failed")
                    results += "FAIL $name: ${failure.message}"
                    File(output, "interaction-results.txt").writeText(results.joinToString("\n") + "\n")
                    throw failure
                }
            }
        }
        runCase("editor-shortcut-and-paste") {
            check(clipboard.reads == 0) { "Rendering accessed the clipboard" }
            click("editor")
            moveCaretToEnd()
            press(Key.Enter)
            assertDraft(INITIAL_DRAFT + "\n", "Enter did not insert a newline in the focused editor")
            check(transport.sentTexts.isEmpty()) { "Plain Enter sent text" }
            press(Key.A, ctrl = true)
            assertDraft(INITIAL_DRAFT + "\n", "Selecting the draft changed its content")
            snapshot("editor-selected-before-paste")
            click("paste")
            check(clipboard.reads == 1) { "Pegar must read the clipboard exactly once" }
            assertDraft(PASTED_TEXT, "Pegar did not replace the selected draft exactly")
            // Pegar restores the editor's focus and caret; do not click the editor again here.
            press(Key.Enter)
            assertDraft(PASTED_TEXT + "\n", "Pegar did not restore focus at the insertion caret")
            ctrlEnter(repeats = 5)
            await("one text submission") { transport.sentTexts.size == 1 && !state().sendPending }
            check(transport.sentTexts.single() == PASTED_TEXT + "\n") { "Ctrl+Enter changed the Unicode/newline content" }
            ctrlEnter(repeats = 5)
            settle()
            check(transport.sentTexts.size == 1) { "Autorepeat or a busy editor submitted twice" }
            transport.completeOutgoing()
            await("confirmed text clears the draft") { !state().busy && state().textDraft.isEmpty() }
            click("editor")
            ctrlEnter()
            settle()
            check(transport.sentTexts.size == 1) { "An empty draft was submitted" }
            check(clipboard.reads == 1) { "Keyboard input accessed the clipboard without Pegar" }
        }
        runCase("shortcut-without-receiver", selectPeer = false, hasReceiver = false) {
            click("editor")
            ctrlEnter(repeats = 4)
            settle()
            check(transport.sentTexts.isEmpty()) { "Text was sent without a receiver" }
            check(state().textDraft == INITIAL_DRAFT) { "Blocked shortcut changed the draft" }
            check(clipboard.reads == 0)
        }
        runCase("selector-focus-and-escape", selectPeer = false) {
            click("editor")
            moveCaretToEnd()
            key(Key.CtrlLeft, KeyEventType.KeyDown, ctrl = true)
            key(Key.Enter, KeyEventType.KeyDown, ctrl = true)
            click("receiver")
            snapshot("selector-open-during-shortcut")
            key(Key.Enter, KeyEventType.KeyUp, ctrl = true)
            key(Key.CtrlLeft, KeyEventType.KeyUp)
            press(Key.Escape)
            settle()
            check(state().textDraft == INITIAL_DRAFT && state().selectedPeer == null) { "Escape changed the draft or receiver" }
            check(transport.sentTexts.isEmpty()) { "A shortcut completed after the editor lost focus" }
            click("receiver")
            click("receiverChoice")
            check(state().selectedPeer?.id == transport.peer.id) { "Receiver choice was not applied" }
            // Receiver selection restores focus. This edit proves it without an additional editor click.
            press(Key.Enter)
            assertDraft(INITIAL_DRAFT + "\n", "Receiver choice did not restore the editor's caret/focus")
            check(transport.sentTexts.isEmpty())
        }
        if (points.has("viewText") && points.has("copyText")) {
            runCase("expanded-text-copy") {
                receiveText()
                click("viewText")
                snapshot("expanded-text-before-copy")
                click("copyText")
                check(clipboard.writes.singleOrNull() == RECEIVED_TEXT) { "Copy did not preserve the complete received text" }
                check(clipboard.reads == 0) { "Copy read the clipboard" }
                press(Key.Escape)
                check(state().textDraft == INITIAL_DRAFT) { "Closing the text dialog changed the composer" }
            }
        } else {
            results += "SKIP expanded-text-copy: viewText and copyText points were not supplied"
        }
        File(output, "interaction-results.txt").writeText(
            "Production Compose UI, ${points.width}x${points.height}, density 1; synthetic input/clipboard/transport; no OS input or network.\n" +
                results.joinToString("\n") + "\n"
        )
    }

    /** This intentionally parses only the documented flat numeric configuration, not arbitrary JSON. */
    private class Points(json: String) {
        val width = Regex("\"width\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toInt() ?: 1160
        val height = Regex("\"height\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toInt() ?: 800
        private val points = Regex("\"([A-Za-z]+)\"\\s*:\\s*\\[\\s*(\\d+(?:\\.\\d+)?)\\s*,\\s*(\\d+(?:\\.\\d+)?)\\s*]")
            .findAll(json).associate { it.groupValues[1] to Offset(it.groupValues[2].toFloat(), it.groupValues[3].toFloat()) }
        init { require(width >= 800 && height >= 620) { "Use the supported desktop workspace dimensions" } }
        fun has(name: String) = points[name]?.let { it.x > 0 && it.y > 0 } == true
        fun required(name: String): Offset = checkNotNull(points[name]) { "Missing observed point: $name" }.also {
            require(it.x > 0 && it.x < width && it.y > 0 && it.y < height) { "Point $name is outside the scene or still a placeholder" }
        }
    }

    private class Fixture(
        private val output: File,
        private val points: Points,
        selectPeer: Boolean,
        hasReceiver: Boolean = true
    ) : AutoCloseable {
        val clipboard = MemoryClipboard()
        lateinit var transport: MemoryTransport
            private set
        private val controller = onEdt {
            DesktopFlashController(File(output, "fixtures"), clipboard) { _, _, listener ->
                MemoryTransport(listener, hasReceiver).also { transport = it }
            }
        }
        private val scene: ImageComposeScene
        private var frame = 0L

        init {
            onEdt { controller.start("Qetara de ejemplo") }
            await("activation") { state().session.active }
            onEdt {
                if (selectPeer) controller.choosePeer(transport.peer)
                controller.updateTextDraft(INITIAL_DRAFT)
            }
            scene = onEdt {
                ImageComposeScene(points.width, points.height, density = Density(1f)) {
                    MaterialTheme(colors = qetaraDesktopColors, typography = qetaraTypography, shapes = qetaraShapes) {
                        DesktopWorkspace(
                            state = DesktopDesignPreview.workspaceFixture(emptyList(), "flash-text")
                                .copy(flashActive = controller.state.session.active),
                            actions = DesktopDesignPreview.previewActions(), chatContent = {}, activityContent = {},
                            flashVisible = true,
                            flashContent = {
                                DesktopFlashPanel(controller, "Qetara de ejemplo", listOf("192.0.2.10"), {}, {}, {})
                            }
                        )
                    }
                }
            }
            pump()
        }

        fun state(): DesktopFlashUiState = onEdt { controller.state }
        fun assertDraft(expected: String, description: String) {
            val actual = state().textDraft
            fun visible(value: String) = value.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n")
            check(actual == expected) { "$description; expected=\"${visible(expected)}\", actual=\"${visible(actual)}\"" }
        }
        fun moveCaretToEnd() {
            val original = state().textDraft
            // Compose Desktop 1.8.2's Windows key map does not bind Ctrl+End. Select all,
            // then collapse to the right edge using its supported SELECT_ALL/RIGHT_CHAR actions.
            press(Key.A, ctrl = true)
            press(Key.DirectionRight)
            assertDraft(original, "Moving the caret changed the draft")
        }
        private fun pump() = onEdt { repeat(4) { scene.render(++frame * 16_666_667L).close() } }
        fun click(name: String) {
            val position = points.required(name)
            onEdt {
                scene.sendPointerEvent(PointerEventType.Move, position)
                scene.sendPointerEvent(PointerEventType.Press, position)
                scene.sendPointerEvent(PointerEventType.Release, position)
            }
            pump()
        }
        fun key(key: Key, type: KeyEventType, ctrl: Boolean = false) {
            onEdt { scene.sendKeyEvent(KeyEvent(key, type, isCtrlPressed = ctrl)) }
            pump()
        }
        fun press(pressedKey: Key, ctrl: Boolean = false) {
            if (ctrl) key(Key.CtrlLeft, KeyEventType.KeyDown, ctrl = true)
            key(pressedKey, KeyEventType.KeyDown, ctrl)
            key(pressedKey, KeyEventType.KeyUp, ctrl)
            if (ctrl) key(Key.CtrlLeft, KeyEventType.KeyUp)
        }
        fun ctrlEnter(repeats: Int = 1) {
            val submittedBefore = transport.sentTexts.size
            key(Key.CtrlLeft, KeyEventType.KeyDown, ctrl = true)
            repeat(repeats) { key(Key.Enter, KeyEventType.KeyDown, ctrl = true) }
            settle()
            check(transport.sentTexts.size == submittedBefore) { "Ctrl+Enter submitted before release; held keys can repeat the action" }
            key(Key.Enter, KeyEventType.KeyUp, ctrl = true)
            key(Key.CtrlLeft, KeyEventType.KeyUp)
        }
        fun settle() {
            repeat(10) { Thread.sleep(10); pump() }
        }
        fun await(description: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (!condition()) {
                check(System.nanoTime() < deadline) { "Timed out waiting for $description" }
                Thread.sleep(10)
            }
        }
        fun receiveText() {
            transport.receive(FlashText.createFile(File(output, "fixtures"), RECEIVED_TEXT))
            await("received text") { state().transfers.any { it.textBody == RECEIVED_TEXT && !it.busy } }
            pump()
        }
        fun snapshot(name: String) {
            pump()
            val png = onEdt {
                scene.render(++frame * 16_666_667L).use { image ->
                    checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { it.bytes }
                }
            }
            File(output, "$name-${points.width}x${points.height}.png").writeBytes(png)
        }
        override fun close() = onEdt { scene.close(); controller.close() }
    }

    private class MemoryClipboard : DesktopFlashClipboard {
        var reads = 0
            private set
        val writes = mutableListOf<String>()
        override fun readText(): String { reads++; return PASTED_TEXT }
        override fun writeText(text: String) { writes += text }
    }

    private class MemoryTransport(private val listener: FlashListener, hasReceiver: Boolean) : DesktopFlashTransport {
        val peer = FlashPeer("preview-peer", "Equipo de ejemplo", "192.0.2.20", 8989, System.currentTimeMillis() + 1_800_000)
        private val active = FlashState(true, peer.expiresAtMs, "preview-local", peers = if (hasReceiver) listOf(peer) else emptyList())
        val sentTexts = CopyOnWriteArrayList<String>()
        @Volatile private var outgoing: FlashOperation? = null
        override fun start() = listener.onState(active)
        override fun discover() = listener.onState(active)
        override fun stop() = listener.onState(FlashState())
        override fun discoverAt(address: String, port: Int) = error("No network in interaction preview")
        override fun approve(requestId: String, accepted: Boolean) = false
        override fun cancel(operationId: String) = false
        override fun sendBatch(files: List<File>, peer: FlashPeer): String {
            val file = files.single()
            val body = checkNotNull(FlashText.readReceived(file)) { "Expected a valid Flash text attachment" }
            sentTexts += body
            val operation = FlashOperation("preview-send-${sentTexts.size}", peer, file.name, file.length(), true)
            outgoing = operation
            listener.onState(active.copy(operations = listOf(operation)))
            listener.onProgress(FlashProgress(operation.id, operation.totalBytes / 2, operation.totalBytes, true))
            return operation.batchId
        }
        fun completeOutgoing() {
            val operation = checkNotNull(outgoing)
            listener.onCompleted(FlashCompleted(operation.id, peer, operation.fileName, true))
            listener.onState(active)
            outgoing = null
        }
        fun receive(file: File) {
            val operation = FlashOperation("preview-received-text", peer, file.name, file.length(), false)
            listener.onState(active.copy(operations = listOf(operation)))
            listener.onReceived(FlashReceived(operation.id, peer, file))
            listener.onCompleted(FlashCompleted(operation.id, peer, file.name, false))
            listener.onState(active)
        }
    }

    private fun <T> onEdt(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return action()
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }
}
