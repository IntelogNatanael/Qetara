package com.example.wifidrop.pc

import com.example.wifidrop.protocol.flash.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Uses physical LAN UDP only for peer discovery; no target address enters either Flash engine. */
class FlashLanUdpTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun androidServiceAndDesktopControllerDiscoverFreshSessionsByLanUdp() {
        assumeTrue("Set QETARA_FLASH_UDP_QA=1 only in the coordinated physical LAN test window",
            System.getenv("QETARA_FLASH_UDP_QA") == "1")
        val queries = AtomicInteger()
        val explicitQueries = AtomicInteger()
        val controller = edt {
            DesktopFlashController(temporary.newFolder("udp-received")) { label, directory, listener ->
                val engine = FlashEngine(label, directory, listener, FlashConfig(lifetimeMs = 180_000))
                object : DesktopFlashTransport {
                    override fun start() { engine.start() }
                    override fun stop() = engine.stop()
                    override fun discover() { queries.incrementAndGet(); engine.discover() }
                    override fun discoverAt(address: String, port: Int) {
                        explicitQueries.incrementAndGet()
                        error("Explicit TCP discovery is forbidden in UDP QA")
                    }
                    override fun send(file: File, peer: FlashPeer): String = error("UDP QA does not transfer files")
                    override fun approve(requestId: String, accepted: Boolean): Boolean = error("UDP QA does not approve files")
                    override fun cancel(operationId: String) = engine.cancel(operationId)
                }
            }
        }
        fun startPc(label: String): String {
            val previousQueries = queries.get()
            edt { controller.start(label) }
            await("PC Flash did not activate and request automatic UDP discovery") {
                edt { controller.state.session.active && !controller.state.starting } && queries.get() == previousQueries + 1
            }
            return edt { controller.state.session.localId }
        }
        fun restartPc(label: String): String {
            edt { controller.stop() }
            return startPc(label)
        }
        fun expectPcPeer(id: String) {
            await("PC did not discover the fresh Android session by UDP") {
                edt {
                    val state = controller.state
                    check(!state.error) { state.status }
                    check(state.session.operations.isEmpty() && state.session.approvals.isEmpty()) { "UDP QA must remain idle" }
                    state.session.peers.any { peer ->
                        peer.id == id && peer.port == FLASH_PORT && !InetAddress.getByName(peer.address).isLoopbackAddress
                    }
                }
            }
        }
        try {
            Remote().use { android ->
                val pcFirst = startPc("Qetara PC UDP QA first")
                settleResponseLimit()
                android.command("START")
                val androidFirst = android.sessionId()
                android.command("EXPECT_PEER", pcFirst)
                println("PASS UDP Android activation discovered the existing PC session")

                settleResponseLimit()
                val pcSecond = restartPc("Qetara PC UDP QA second")
                assertNotEquals(pcFirst, pcSecond)
                expectPcPeer(androidFirst)
                println("PASS UDP PC activation discovered the existing Android session")

                // Flash accepts replies for 10 s. Expire that window before making a new remote ID.
                Thread.sleep(10_500)
                android.command("STOP")
                android.command("START")
                val androidSecond = android.sessionId()
                assertNotEquals(androidFirst, androidSecond)
                android.command("EXPECT_PEER", pcSecond)
                assertTrue(edt { controller.state.session.peers.none { it.id == androidSecond } },
                    "New Android session must not already be cached before manual discovery")
                settleResponseLimit()
                edt { controller.discover() }
                expectPcPeer(androidSecond)
                println("PASS UDP PC manual discovery found a new Android activation ID")

                Thread.sleep(10_500)
                val pcThird = restartPc("Qetara PC UDP QA third")
                assertNotEquals(pcSecond, pcThird)
                expectPcPeer(androidSecond)
                android.command("ABSENT_PEER", pcThird)
                settleResponseLimit()
                android.command("DISCOVER")
                android.command("EXPECT_PEER", pcThird)
                println("PASS UDP Android manual discovery found a new PC activation ID")

                assertEquals(4, queries.get(), "Three activations and one manual PC query")
                assertEquals(0, explicitQueries.get())
                android.command("FINISH")
                println("PASS LAN_UDP checks=4 port=8989 explicit_discovery=0 payload_forwarding=0 control=ADB")
            }
        } finally {
            edt { controller.close() }
        }
    }

    private class Remote : AutoCloseable {
        private val socket = Socket("127.0.0.1", 39894).apply { soTimeout = 30_000 }
        private val input = DataInputStream(socket.getInputStream())
        private val output = DataOutputStream(socket.getOutputStream())
        init { command("Qetara UDP QA 1") }
        fun command(name: String, argument: String? = null) {
            output.writeUTF(name)
            argument?.let(output::writeUTF)
            output.flush()
            check(input.readUTF() == "OK") { input.readUTF() }
        }
        fun sessionId(): String {
            command("STATE")
            val id = input.readUTF()
            assertEquals(FLASH_PORT, input.readInt())
            return id
        }
        override fun close() = socket.close()
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!condition()) {
            check(System.nanoTime() < deadline) { message }
            Thread.sleep(25)
        }
    }

    // The production responder rate-limits all responses (including self-broadcast) to one per 200 ms.
    private fun settleResponseLimit() = Thread.sleep(350)

    private fun <T> edt(action: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }
}
