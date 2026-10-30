package com.example.wifidrop

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Opt-in production Android service bridge for FlashTextEmulatorSocketTest on the JVM.
 * Control: ADB forward tcp:39891 tcp:39891. Payload: forward tcp:39892 tcp:8989.
 * Start with -e scenario flash-text-service-sockets -e pc-host 10.0.2.2 on an isolated emulator.
 * The Windows test peer listens on host loopback:8989; only synthetic fixture text is exchanged.
 */
internal class FlashTextServiceSocketScenario(
    private val instrumentation: Instrumentation,
    private val pcHost: String
) {
    fun run() {
        val result = Bundle()
        val original = FlashAndroidRuntime.state.value
        val originalFiles = original.results.mapNotNull { it.file?.canonicalPath }.toSet()
        var activity: Activity? = null
        var activated = false
        val createdFiles = mutableSetOf<File>()
        val context = instrumentation.targetContext
        try {
            check(original.phase == FlashAndroidPhase.OFF && !original.importing) { "Close Flash first" }
            activity = instrumentation.startActivitySync(Intent(context, FlashActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.runOnMainSync {
                FlashForegroundService.activate(context, "Qetara Android text QA")
                activated = true
            }
            await("production service active") { FlashAndroidRuntime.state.value.active }
            ServerSocket(39891, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 120_000
                server.accept().use { socket ->
                    socket.soTimeout = 90_000
                    val input = DataInputStream(socket.getInputStream())
                    val output = DataOutputStream(socket.getOutputStream())
                    var done = false
                    while (!done) {
                        val command = input.readUTF()
                        val args = List(when (command) { "SEND_TEXT" -> 2; "UPDATE_TEXT" -> 1; "APPROVE" -> 3; else -> 0 }) {
                            input.readUTF()
                        }
                        val bytes = ByteArrayOutputStream()
                        val payload = DataOutputStream(bytes)
                        try {
                            onMain {
                                when (command) {
                                    "DISCOVER" -> FlashForegroundService.discoverPeers(pcHost)
                                    "SEND_TEXT" -> {
                                        val peer = checkNotNull(FlashAndroidRuntime.state.value.engine).peers.single { it.id == args[0] }
                                        FlashForegroundService.selectPeer(peer)
                                        FlashForegroundService.updateTextDraft(args[1])
                                        check(FlashAndroidRuntime.state.value.textDraft == args[1])
                                        FlashForegroundService.sendText()
                                    }
                                    "UPDATE_TEXT" -> FlashForegroundService.updateTextDraft(args[0])
                                    "APPROVE" -> {
                                        val approval = checkNotNull(FlashAndroidRuntime.state.value.engine).approvals.single { it.requestId == args[0] }
                                        check(approval.verificationCode == args[1]) { "Bilateral verification codes differ" }
                                        FlashForegroundService.approve(approval.requestId, args[2].toBooleanStrict())
                                    }
                                    "STATE" -> {
                                        val state = FlashAndroidRuntime.state.value
                                        payload.writeBoolean(state.active)
                                        payload.writeUTF(state.phase.name)
                                        payload.writeUTF(state.status)
                                        payload.writeUTF(state.textDraft)
                                        payload.writeBoolean(state.sendingText)
                                        val peers = state.engine?.peers.orEmpty()
                                        payload.writeInt(peers.size)
                                        peers.forEach { payload.writeUTF(it.id); payload.writeUTF(it.label) }
                                        val approvals = state.engine?.approvals.orEmpty()
                                        payload.writeInt(approvals.size)
                                        approvals.forEach {
                                            payload.writeUTF(it.requestId); payload.writeUTF(it.fileName)
                                            payload.writeUTF(it.verificationCode); payload.writeBoolean(it.outgoing)
                                        }
                                        payload.writeInt(state.results.size)
                                        state.results.forEach {
                                            payload.writeUTF(it.id); payload.writeUTF(it.kind.name)
                                            payload.writeBoolean(it.textBody != null)
                                            it.textBody?.let(payload::writeUTF)
                                        }
                                    }
                                    "CLOSE" -> {
                                        val state = FlashAndroidRuntime.state.value
                                        check(!state.sendingText && state.engine?.operations?.isEmpty() == true)
                                        check(state.results.any { it.kind == FlashResultKind.RECEIVED && it.textBody != null })
                                        check(state.results.any { it.kind == FlashResultKind.DELIVERED && it.textBody != null })
                                        result.putInt("texts_received", state.results.count { it.kind == FlashResultKind.RECEIVED && it.textBody != null })
                                        result.putInt("texts_delivered", state.results.count { it.kind == FlashResultKind.DELIVERED && it.textBody != null })
                                    }
                                    else -> error("Unknown fixture command: $command")
                                }
                            }
                            output.writeUTF("OK")
                            output.write(bytes.toByteArray())
                        } catch (failure: Throwable) {
                            output.writeUTF("ERROR")
                            output.writeUTF(failure.stackTraceToString().take(8000))
                            throw failure
                        } finally {
                            FlashAndroidRuntime.state.value.results.mapNotNullTo(createdFiles) { it.file }
                            output.flush()
                        }
                        done = command == "CLOSE"
                    }
                }
            }
            result.putString("result", "PASS")
            result.putString("transport", "Production Android FlashForegroundService <-> JVM controller; real Noise TCP; bilateral approvals")
        } catch (failure: Throwable) {
            result.putString("result", "FAIL")
            result.putString("failure", failure.stackTraceToString())
        } finally {
            FlashAndroidRuntime.state.value.results.mapNotNullTo(createdFiles) { it.file }
            if (activated) {
                instrumentation.runOnMainSync { FlashForegroundService.deactivate() }
                runCatching { await("service stopped") { FlashAndroidRuntime.state.value.phase == FlashAndroidPhase.OFF } }
                    .onFailure { result.putString("result", "FAIL"); result.putString("cleanup_failure", it.toString()) }
            }
            FlashAndroidRuntime.state.value.results.mapNotNullTo(createdFiles) { it.file }
            val receivedRoot = File(context.getExternalFilesDir(null) ?: context.filesDir, "WifiDropReceived").canonicalFile
            val cleaned = createdFiles.filter { it.canonicalPath !in originalFiles }.all { file ->
                val safe = file.canonicalFile.parentFile == receivedRoot
                safe && (!file.exists() || file.delete())
            }
            result.putBoolean("created_received_files_removed", cleaned)
            if (!cleaned) result.putString("result", "FAIL")
            instrumentation.runOnMainSync {
                activity?.finish()
                if (activated && FlashAndroidRuntime.state.value.phase == FlashAndroidPhase.OFF) FlashAndroidRuntime.update { original }
            }
            result.putBoolean("process_state_restored", FlashAndroidRuntime.state.value == original)
            instrumentation.finish(if (result.getString("result") == "PASS") Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        }
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Timed out: $description; ${FlashAndroidRuntime.state.value.status}" }
            SystemClock.sleep(50)
        }
    }

    private fun onMain(action: () -> Unit) {
        var outcome: Result<Unit>? = null
        instrumentation.runOnMainSync { outcome = runCatching(action) }
        checkNotNull(outcome).getOrThrow()
    }
}
