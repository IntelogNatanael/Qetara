package com.example.wifidrop

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import com.example.wifidrop.protocol.flash.FLASH_PORT
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Opt-in backend fixture: real foreground service and UDP; ADB carries only fixture commands. */
internal class FlashUdpInstrumentationScenario(private val instrumentation: Instrumentation) {
    private var ownsActivation = false
    private var activations = 0
    private var manualQueries = 0
    private var peerChecks = 0

    fun run() {
        val result = Bundle()
        try {
            val initial = FlashAndroidRuntime.state.value
            check(initial.phase == FlashAndroidPhase.OFF && initial.selectedFiles.isEmpty()) {
                "Close Flash and clear its draft before starting UDP QA"
            }
            ServerSocket(CONTROL_PORT, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 120_000
                server.accept().use { socket ->
                    socket.soTimeout = 45_000
                    val input = DataInputStream(socket.getInputStream())
                    val output = DataOutputStream(socket.getOutputStream())
                    check(input.readUTF() == "Qetara UDP QA 1") { "Unexpected fixture client" }
                    output.writeUTF("OK")
                    output.flush()
                    val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3)
                    var finished = false
                    while (!finished) {
                        check(System.nanoTime() < deadline) { "UDP QA lifetime exceeded" }
                        val command = input.readUTF()
                        try {
                            when (command) {
                                "START" -> startService()
                                "STOP" -> stopService()
                                "DISCOVER" -> {
                                    requireIdleActive()
                                    instrumentation.runOnMainSync { FlashForegroundService.discoverPeers() }
                                    manualQueries++
                                }
                                "EXPECT_PEER" -> {
                                    val expectedId = input.readUTF().also { UUID.fromString(it) }
                                    await("Android did not discover the expected peer by UDP") {
                                        requireIdleActive()
                                        FlashAndroidRuntime.state.value.engine?.peers.orEmpty().any { peer ->
                                            peer.id == expectedId && peer.port == FLASH_PORT &&
                                                !InetAddress.getByName(peer.address).isLoopbackAddress
                                        }
                                    }
                                    peerChecks++
                                }
                                "ABSENT_PEER" -> {
                                    val expectedId = input.readUTF().also { UUID.fromString(it) }
                                    requireIdleActive()
                                    check(FlashAndroidRuntime.state.value.engine?.peers.orEmpty().none { it.id == expectedId }) {
                                        "Fresh peer appeared before the manual UDP query"
                                    }
                                }
                                "STATE" -> requireIdleActive()
                                "FINISH" -> {
                                    requireIdleActive()
                                    check(activations == 2 && manualQueries == 1 && peerChecks == 3) {
                                        "UDP QA phases were not completed"
                                    }
                                    finished = true
                                }
                                else -> error("Unknown UDP fixture command")
                            }
                            output.writeUTF("OK")
                            if (command == "STATE") {
                                val state = checkNotNull(FlashAndroidRuntime.state.value.engine)
                                output.writeUTF(state.localId)
                                output.writeInt(state.port)
                            }
                            output.flush()
                        } catch (failure: Throwable) {
                            output.writeUTF("ERROR")
                            output.writeUTF(failure.message.orEmpty().take(500))
                            output.flush()
                            throw failure
                        }
                    }
                }
            }
            result.putString("result", "PASS")
            result.putInt("service_activations", activations)
            result.putInt("manual_discovery_requests", manualQueries)
            result.putInt("peer_checks_passed", peerChecks)
            result.putString("network_locks", "production service activation; no extra test lock")
            result.putString("transport", "LAN UDP broadcast and nonce-correlated replies; ADB control only; no discoverAt")
        } catch (failure: Throwable) {
            result.putString("result", "FAIL")
            result.putString("failure", failure.message.orEmpty().take(500))
        } finally {
            val stopped = runCatching { stopService() }.isSuccess
            result.putBoolean("owned_service_stopped", stopped)
            if (!stopped) result.putString("result", "FAIL")
            instrumentation.finish(if (result.getString("result") == "PASS") Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        }
    }

    private fun startService() {
        check(FlashAndroidRuntime.state.value.phase == FlashAndroidPhase.OFF) { "Flash is already active" }
        ownsActivation = true
        instrumentation.runOnMainSync {
            FlashForegroundService.activate(instrumentation.targetContext, "Qetara Android UDP QA")
        }
        await("The production Flash service did not activate") { FlashAndroidRuntime.state.value.active }
        requireIdleActive()
        activations++
    }

    private fun stopService() {
        if (!ownsActivation) return
        instrumentation.runOnMainSync { FlashForegroundService.deactivate() }
        await("The owned Flash service did not stop") { FlashAndroidRuntime.state.value.phase == FlashAndroidPhase.OFF }
        ownsActivation = false
    }

    private fun requireIdleActive() {
        val state = FlashAndroidRuntime.state.value
        check(state.active && !state.importing && state.engine?.operations.orEmpty().isEmpty() &&
            state.engine?.approvals.orEmpty().isEmpty()) { "Flash must remain active and idle during UDP QA" }
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!condition()) {
            check(System.nanoTime() < deadline) { message }
            Thread.sleep(25)
        }
    }

    private companion object { const val CONTROL_PORT = 39894 }
}
