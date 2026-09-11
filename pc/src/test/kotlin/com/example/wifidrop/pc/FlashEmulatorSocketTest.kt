package com.example.wifidrop.pc

import com.example.wifidrop.protocol.flash.*
import com.example.wifidrop.protocol.sha256File
import java.io.*
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Requires the opt-in Android fixture and ADB forwards documented in docs/FLASH_SOCKET_QA.md. */
class FlashEmulatorSocketTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun windowsAndAndroidExchangeTwoFileBatchesOverRealSockets() {
        assumeTrue("Set QETARA_FLASH_EMULATOR_QA=1 only with the Android fixture running",
            System.getenv("QETARA_FLASH_EMULATOR_QA") == "1")
        val androidHost = System.getenv("QETARA_FLASH_ANDROID_HOST")?.trim()?.takeIf { it.isNotEmpty() } ?: "127.0.0.1"
        val discoveryCalls = AtomicInteger()
        val controller = edt {
            DesktopFlashController(temporary.newFolder("received")) { label, directory, listener ->
                val engine = FlashEngine(label, directory, listener, FlashConfig(port = 39893,
                    discoveryEnabled = false, bindAddress = if (androidHost == "127.0.0.1") "127.0.0.1" else null,
                    lifetimeMs = 180_000, approvalTimeoutMs = 60_000))
                object : DesktopFlashTransport {
                    override fun start() { engine.start() }
                    override fun stop() = engine.stop()
                    override fun send(file: File, peer: FlashPeer) = engine.send(file, peer)
                    override fun approve(requestId: String, accepted: Boolean) = engine.approve(requestId, accepted)
                    override fun cancel(operationId: String) = engine.cancel(operationId)
                    override fun discover() { discoveryCalls.incrementAndGet(); engine.discover() }
                    override fun discoverAt(address: String, port: Int) = engine.discoverAt(address, port)
                }
            }
        }
        try {
            Remote().use { android ->
                edt { controller.start("Qetara Windows socket QA") }
                await { edt { controller.state.session.active } && discoveryCalls.get() == 1 }
                edt { controller.discoverAt(androidHost, "39892") }
                android.command("DISCOVER")
                await { edt { controller.state.session.peers.size == 1 } && android.state().peers == 1 }
                // Starting an active session is idempotent; the manual discovery action remains available.
                edt { controller.start("Again"); controller.discover() }
                await { discoveryCalls.get() == 2 }
                val files = listOf(131_072, 524_319).mapIndexed { index, size ->
                    temporary.newFile("windows-${index + 1}.bin").apply {
                        writeBytes(ByteArray(size) { ((it * 17 + index) % 251).toByte() })
                    }
                }
                val expectedWindows = files.associate { it.name to sha256File(it) }
                edt {
                    controller.choosePeer(controller.state.session.peers.single())
                    controller.chooseFiles(files)
                    controller.send()
                }
                val comparedOperations = mutableSetOf<String>()
                val comparedAndroidRequests = mutableSetOf<String>()
                fun approvePending() {
                    val remote = android.state()
                    assertTrue(remote.errors.isEmpty(), remote.errors.toString())
                    val local = edt { controller.state }
                    assertTrue(!local.error, local.status)
                    val approval = local.session.approvals.singleOrNull() ?: return
                    val other = remote.approvals.singleOrNull { it.fileName == approval.fileName } ?: return
                    assertEquals(approval.verificationCode, other.code)
                    assertTrue(comparedOperations.add(approval.operationId), "Each file must have a distinct operation")
                    assertTrue(comparedAndroidRequests.add(other.id), "Each file must have a distinct approval request")
                    android.approve(other, approval.verificationCode)
                    edt { controller.decide(approval, true) }
                }
                await {
                    approvePending()
                    edt { !controller.state.busy && controller.state.selectedFiles.isEmpty() } && android.state().received.size == 2
                }
                assertEquals(expectedWindows, android.state().received)
                val expectedAndroid = android.batch()
                await {
                    approvePending()
                    val remote = android.state()
                    remote.completed == 2 && !remote.batchActive && edt {
                        !controller.state.busy && controller.state.transfers.count { !it.outgoing && it.phase == DesktopFlashPhase.COMPLETE } == 2
                    }
                }
                val actualAndroid = edt { controller.state.transfers.filter { !it.outgoing }.map { checkNotNull(it.file) } }
                    .associate { it.name to sha256File(it) }
                assertEquals(expectedAndroid, actualAndroid)
                assertEquals(4, comparedOperations.size)
                assertEquals(4, comparedAndroidRequests.size)
                assertEquals(4, edt { controller.state.transfers.count { it.phase == DesktopFlashPhase.COMPLETE } })
                println("PASS real PC <-> Android TCP; two files each direction; codes matched for four distinct operations")
                println("TRANSPORT ${if (androidHost == "127.0.0.1") "ADB_FORWARDED" else "LAN_TCP"} android=$androidHost control=ADB")
                println("HOST ${System.getProperty("os.name")} JAVA ${System.getProperty("java.runtime.version")}")
                (expectedWindows + actualAndroid).forEach { (name, hash) -> println("SHA256 $name $hash") }
                android.command("CLOSE")
            }
        } finally {
            edt { controller.close() }
        }
    }

    private data class Approval(val id: String, val fileName: String, val code: String)
    private data class State(val peers: Int, val completed: Int, val batchActive: Boolean,
        val approvals: List<Approval>, val received: Map<String, String>, val errors: List<String>)

    private class Remote : AutoCloseable {
        private val socket = Socket("127.0.0.1", 39891).apply { soTimeout = 15_000 }
        private val input = DataInputStream(socket.getInputStream())
        private val output = DataOutputStream(socket.getOutputStream())
        fun command(command: String, arguments: List<String> = emptyList()) {
            output.writeUTF(command); arguments.forEach(output::writeUTF); output.flush()
            check(input.readUTF() == "OK") { input.readUTF() }
        }
        fun approve(approval: Approval, expectedCode: String) = command("APPROVE", listOf(approval.id, expectedCode))
        fun batch(): Map<String, String> { command("BATCH"); return hashes() }
        private fun hashes() = buildMap { repeat(input.readInt()) { put(input.readUTF(), input.readUTF()) } }
        fun state(): State {
            command("STATE")
            check(input.readBoolean()) { "Android activation ended" }
            val peers = input.readInt()
            val completed = input.readInt()
            val active = input.readBoolean()
            val approvals = List(input.readInt()) { Approval(input.readUTF(), input.readUTF(), input.readUTF()) }
            val received = hashes()
            return State(peers, completed, active, approvals, received, List(input.readInt()) { input.readUTF() })
        }
        override fun close() = socket.close()
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 45_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Timed out waiting for Flash emulator fixture" }
            Thread.sleep(20)
        }
    }

    private fun <T> edt(action: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }
}
