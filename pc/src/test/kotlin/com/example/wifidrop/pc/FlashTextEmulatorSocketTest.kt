package com.example.wifidrop.pc

import com.example.wifidrop.protocol.flash.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.Socket
import javax.swing.SwingUtilities
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opt-in integration with flash-text-service-sockets; no real desktop clipboard or user files.
 * Set QETARA_FLASH_ANDROID_HOST to the phone's LAN IPv4 for direct :8989 payloads; then only
 * control :39891 is ADB-forwarded, and the Android runner needs -e pc-host <PC LAN IPv4>.
 * Without that host, the historical emulator setup forwards :39892 to Android :8989.
 */
class FlashTextEmulatorSocketTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun windowsControllerAndAndroidServiceExchangeExactTextWithBilateralApproval() {
        assumeTrue("Requires the isolated Android service fixture and ADB control forward",
            System.getenv("QETARA_FLASH_TEXT_EMULATOR_QA") == "1")
        val androidHost = System.getenv("QETARA_FLASH_ANDROID_HOST")?.trim()?.takeIf { it.isNotEmpty() } ?: "127.0.0.1"
        val lanPayload = androidHost != "127.0.0.1"
        if (lanPayload) {
            require(androidHost.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+"))) { "Use the phone's local IPv4 address" }
            require(InetAddress.getByName(androidHost).isSiteLocalAddress) { "The Android payload endpoint must be a local network address" }
        }
        val transportDescription = "TRANSPORT ${if (lanPayload) "LAN_TCP" else "ADB_FORWARDED"} android=$androidHost payloadPort=${if (lanPayload) FLASH_PORT else 39892} pcBind=${if (lanPayload) "all-interfaces" else "127.0.0.1"}:$FLASH_PORT control=ADB:39891"
        println(transportDescription)
        val clipboard = object : DesktopFlashClipboard {
            var content = PC_TEXT
            override fun readText() = content
            override fun writeText(text: String) { content = text }
        }
        val controller = edt {
            DesktopFlashController(temporary.newFolder("received"), clipboard) { label, directory, listener ->
                val engine = FlashEngine(label, directory, listener, FlashConfig(port = FLASH_PORT,
                    discoveryEnabled = false, bindAddress = if (lanPayload) null else "127.0.0.1",
                    lifetimeMs = 180_000, approvalTimeoutMs = 60_000))
                object : DesktopFlashTransport {
                    override fun start() { engine.start() }
                    override fun stop() = engine.stop()
                    override fun sendBatch(files: List<File>, peer: FlashPeer) = engine.sendBatch(files, peer)
                    override fun approve(requestId: String, accepted: Boolean) = engine.approve(requestId, accepted)
                    override fun cancel(operationId: String) = engine.cancel(operationId)
                    override fun discover() = engine.discover()
                    override fun discoverAt(address: String, port: Int) = engine.discoverAt(address, port)
                }
            }
        }
        try {
            Remote().use { android ->
                edt { controller.start("Qetara Windows text QA") }
                await { edt { controller.state.session.active } }
                edt { controller.discoverAt(androidHost, if (lanPayload) FLASH_PORT.toString() else "39892") }
                android.command("DISCOVER")
                val localId = edt { controller.state.session.localId }
                var desktopDiscovery: DesktopFlashUiState? = null
                var androidDiscovery: State? = null
                await("bidirectional Flash discovery", diagnostics = {
                    val desktop = desktopDiscovery ?: edt { controller.state }
                    val phone = androidDiscovery ?: android.lastState
                    buildString {
                        appendLine(transportDescription)
                        appendLine("PC active=${desktop.session.active} starting=${desktop.starting} error=${desktop.error} status=${desktop.status}")
                        appendLine("PC localId=$localId peers=${desktop.session.peers.map { "${it.id}@${it.address}:${it.port}" }}")
                        if (phone == null) append("ANDROID state unavailable; no complete STATE response received")
                        else {
                            appendLine("ANDROID active=${phone.active} phase=${phone.phase} status=${phone.status}")
                            append("ANDROID peerIds=${phone.peerIds}; peer IPs are not exposed by this version of the Android fixture")
                        }
                    }
                }) {
                    // Read both ends even if PC discovery is still empty, so a timeout identifies the failing direction.
                    val desktop = edt { controller.state }
                    val phone = android.state()
                    desktopDiscovery = desktop
                    androidDiscovery = phone
                    desktop.session.peers.size == 1 && phone.peerIds.contains(localId)
                }
                if (lanPayload) {
                    val peer = edt { controller.state.session.peers.single() }
                    assertEquals(androidHost, peer.address)
                    assertEquals(FLASH_PORT, peer.port)
                    assertTrue(!InetAddress.getByName(peer.address).isLoopbackAddress)
                }
                edt { controller.choosePeer(controller.state.session.peers.single()); controller.pasteText() }
                await { edt { controller.state.textDraft == PC_TEXT } }
                edt { controller.sendText() }
                approve(android, controller, accepted = true)
                await {
                    android.state().results.any { it.kind == "RECEIVED" && it.body == PC_TEXT } &&
                        edt { !controller.state.busy && controller.state.textDraft.isEmpty() }
                }
                val pcSent = edt { controller.state.transfers.single { it.outgoing && it.phase == DesktopFlashPhase.COMPLETE } }
                assertEquals(PC_TEXT, pcSent.textBody)

                android.command("SEND_TEXT", localId, ANDROID_TEXT)
                await { android.state().sending }
                assertEquals(ANDROID_TEXT, android.state().draft)
                approve(android, controller, accepted = true)
                await {
                    android.state().let { !it.sending && it.draft.isEmpty() && it.results.any { item -> item.kind == "DELIVERED" && item.body == ANDROID_TEXT } } &&
                        edt { controller.state.transfers.any { !it.outgoing && it.phase == DesktopFlashPhase.COMPLETE && it.textBody == ANDROID_TEXT } }
                }
                val received = edt { controller.state.transfers.single { !it.outgoing && it.textBody == ANDROID_TEXT } }
                assertTrue(checkNotNull(received.file).readBytes().contentEquals(ANDROID_TEXT.toByteArray(Charsets.UTF_8)))
                edt { clipboard.content = "sentinel"; controller.copyText(received.id) }
                assertEquals(ANDROID_TEXT, clipboard.content)

                // An edit made while the first draft awaits approval must survive its ACK.
                android.command("SEND_TEXT", localId, "Texto enviado antes de editar 🚀\n")
                await { android.state().approvals.isNotEmpty() }
                android.command("UPDATE_TEXT", NEW_DRAFT)
                approve(android, controller, accepted = true)
                await { android.state().let { !it.sending && it.results.count { item -> item.kind == "DELIVERED" } == 2 } }
                assertEquals(NEW_DRAFT, android.state().draft)

                // A remote refusal must retain the complete sender draft.
                val receivedBeforeRefusal = edt { controller.state.transfers.count { !it.outgoing && it.phase == DesktopFlashPhase.COMPLETE } }
                android.command("SEND_TEXT", localId, REJECTED_TEXT)
                approve(android, controller, accepted = false)
                await { android.state().let { !it.sending && it.results.any { item -> item.kind == "CANCELLED" || item.kind == "FAILED" } } }
                assertEquals(REJECTED_TEXT, android.state().draft)
                assertEquals(receivedBeforeRefusal, edt { controller.state.transfers.count { !it.outgoing && it.phase == DesktopFlashPhase.COMPLETE } })
                android.finish()
                println("PASS production Android FlashForegroundService <-> DesktopFlashController over real Noise TCP")
                println("PASS Unicode, CRLF, LF and spaces exact; bilateral verification on 4 attempts; ACK clears only sent draft; refusal retains draft; desktop copy exact")
                println(transportDescription)
            }
        } finally {
            edt { controller.close() }
        }
    }

    private fun approve(android: Remote, controller: DesktopFlashController, accepted: Boolean) {
        var local: FlashApproval? = null
        var remote: Approval? = null
        await {
            local = edt { controller.state.session.approvals.singleOrNull() }
            remote = android.state().approvals.singleOrNull()
            local != null && remote != null
        }
        val desktop = checkNotNull(local)
        val phone = checkNotNull(remote)
        assertEquals(desktop.fileName, phone.fileName)
        assertEquals(desktop.verificationCode, phone.code)
        assertTrue(desktop.outgoing != phone.outgoing)
        assertTrue(FlashText.isTextFileName(desktop.fileName))
        android.command("APPROVE", phone.id, desktop.verificationCode, "true")
        edt { controller.decide(desktop, accepted) }
    }

    private data class Approval(val id: String, val fileName: String, val code: String, val outgoing: Boolean)
    private data class TextResult(val id: String, val kind: String, val body: String?)
    private data class State(val active: Boolean, val phase: String, val status: String,
        val draft: String, val sending: Boolean, val peerIds: List<String>,
        val approvals: List<Approval>, val results: List<TextResult>)

    private class Remote : AutoCloseable {
        private val socket = Socket("127.0.0.1", 39891).apply { soTimeout = 15_000 }
        private val input = DataInputStream(socket.getInputStream())
        private val output = DataOutputStream(socket.getOutputStream())
        private var finishRequested = false
        var lastState: State? = null
            private set
        fun command(command: String, vararg args: String) {
            output.writeUTF(command); args.forEach(output::writeUTF); output.flush()
            check(input.readUTF() == "OK") { input.readUTF() }
        }
        fun state(): State {
            command("STATE")
            val active = input.readBoolean()
            val phase = input.readUTF()
            val status = input.readUTF()
            val draft = input.readUTF()
            val sending = input.readBoolean()
            val peerIds = List(input.readInt()) { input.readUTF().also { input.readUTF() } }
            val approvals = List(input.readInt()) { Approval(input.readUTF(), input.readUTF(), input.readUTF(), input.readBoolean()) }
            val results = List(input.readInt()) { TextResult(input.readUTF(), input.readUTF(), if (input.readBoolean()) input.readUTF() else null) }
            val state = State(active, phase, status, draft, sending, peerIds, approvals, results)
            lastState = state
            // Consume the full response before rejecting an inactive state, keeping CLOSE framed correctly.
            check(active) { "Android Flash inactive: $phase / $status" }
            return state
        }
        fun finish() {
            if (finishRequested) return
            finishRequested = true
            command("CLOSE")
        }
        override fun close() {
            try {
                // use() runs this on failures too. Ask the runner to stop explicitly instead of leaving it an EOF.
                // CLOSE still enforces its success checks; a failed exchange must remain a failed Android run.
                if (!finishRequested) runCatching { finish() }.onFailure {
                    println("ANDROID CLEANUP: CLOSE returned ${it.javaClass.simpleName}: ${it.message?.lineSequence()?.firstOrNull()}")
                }
            } finally {
                socket.close()
            }
        }
    }

    private fun await(description: String = "production Flash text exchange", diagnostics: () -> String = { "" }, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 45_000_000_000L
        while (true) {
            val complete = try { condition() } catch (failure: Exception) {
                throw IllegalStateException("Failed while waiting for $description\n${diagnostics()}", failure)
            }
            if (complete) return
            check(System.nanoTime() < deadline) { "Timed out waiting for $description\n${diagnostics()}" }
            Thread.sleep(40)
        }
    }

    private fun <T> edt(action: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }

    companion object {
        private const val PC_TEXT = "  Desde Windows: Perú 👋\r\n漢字 y ñ\n  final  "
        private const val ANDROID_TEXT = "  Desde Android: 日本語 🚀\nLínea dos\r\n"
        private const val NEW_DRAFT = "Borrador más reciente 👋\nNo borrar."
        private const val REJECTED_TEXT = "Texto rechazado\nConservar íntegro: á, 漢字, 🚀."
    }
}
