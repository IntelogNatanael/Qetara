package com.example.wifidrop

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.Build
import com.example.wifidrop.protocol.flash.*
import com.example.wifidrop.protocol.sha256File
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Opt-in emulator fixture. Only test-created private cache files and loopback sockets are used. */
internal class FlashSocketInstrumentationScenario(private val instrumentation: Instrumentation) {
    fun run() {
        val directory = File(instrumentation.targetContext.cacheDir, "flash-sockets-${System.nanoTime()}")
        val receivedDirectory = File(directory, "received").apply { check(mkdirs()) }
        val serial = Executors.newSingleThreadExecutor()
        val batch = FlashOutgoingBatch()
        val receivedHashes = linkedMapOf<String, String>()
        val errors = mutableListOf<String>()
        var outgoingCompleted = 0
        lateinit var engine: FlashEngine
        fun pump() {
            if (engine.snapshot().operations.isNotEmpty()) return
            val next = batch.next() ?: return
            batch.started(engine.send(next.file, next.peer))
        }
        fun post(action: () -> Unit) {
            serial.execute { runCatching(action).onFailure { errors += it.toString() } }
        }
        engine = FlashEngine("Qetara Android socket QA", receivedDirectory, object : FlashListener {
            override fun onState(state: FlashState) = post { if (state.active) pump() }
            override fun onReceived(received: FlashReceived) = post {
                receivedHashes[received.file.name] = sha256File(received.file)
            }
            override fun onCompleted(completed: FlashCompleted) = post {
                if (completed.outgoing) {
                    checkNotNull(batch.complete(completed.operationId))
                    outgoingCompleted++
                    pump()
                }
            }
            override fun onError(error: FlashError) = post { errors += "${error.code}: ${error.message}" }
        }, FlashConfig(port = 39892, discoveryEnabled = false, bindAddress = "127.0.0.1",
            lifetimeMs = 180_000, approvalTimeoutMs = 60_000))
        val result = Bundle()
        try {
            serial.submit { engine.start() }.get(10, TimeUnit.SECONDS)
            ServerSocket(39891, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 120_000
                server.accept().use { socket ->
                    socket.soTimeout = 60_000
                    val input = DataInputStream(socket.getInputStream())
                    val output = DataOutputStream(socket.getOutputStream())
                    var done = false
                    while (!done) {
                        val command = input.readUTF()
                        val requestId = if (command == "APPROVE") input.readUTF() else ""
                        val expectedCode = if (command == "APPROVE") input.readUTF() else ""
                        val response = serial.submit<ByteArray> {
                            val bytes = ByteArrayOutputStream()
                            val data = DataOutputStream(bytes)
                            try {
                                val payload = ByteArrayOutputStream()
                                val body = DataOutputStream(payload)
                                when (command) {
                                    "DISCOVER" -> engine.discoverAt("127.0.0.1", 39893)
                                    "BATCH" -> {
                                        val peer = engine.snapshot().peers.single()
                                        val files = listOf(131_072, 524_319).mapIndexed { index, size ->
                                            File(directory, "android-${index + 1}.bin").apply {
                                                writeBytes(ByteArray(size) { ((it * 31 + index) % 251).toByte() })
                                            }
                                        }
                                        check(batch.start(files, peer))
                                        body.writeInt(files.size)
                                        files.forEach { body.writeUTF(it.name); body.writeUTF(sha256File(it)) }
                                        pump()
                                    }
                                    "APPROVE" -> {
                                        val approval = engine.snapshot().approvals.single { it.requestId == requestId }
                                        check(approval.verificationCode == expectedCode) { "Verification codes differ" }
                                        check(engine.approve(requestId, true))
                                    }
                                    "STATE" -> {
                                        val state = engine.snapshot()
                                        body.writeBoolean(state.active)
                                        body.writeInt(state.peers.size)
                                        body.writeInt(outgoingCompleted)
                                        body.writeBoolean(batch.active)
                                        body.writeInt(state.approvals.size)
                                        state.approvals.forEach {
                                            body.writeUTF(it.requestId); body.writeUTF(it.fileName); body.writeUTF(it.verificationCode)
                                        }
                                        body.writeInt(receivedHashes.size)
                                        receivedHashes.forEach { (name, hash) -> body.writeUTF(name); body.writeUTF(hash) }
                                        body.writeInt(errors.size)
                                        errors.forEach(body::writeUTF)
                                    }
                                    "CLOSE" -> {
                                        check(receivedHashes.size == 2 && outgoingCompleted == 2 && !batch.active && errors.isEmpty())
                                    }
                                    else -> error("Unknown fixture command: $command")
                                }
                                data.writeUTF("OK")
                                data.write(payload.toByteArray())
                            } catch (failure: Throwable) {
                                errors += failure.toString()
                                data.writeUTF("ERROR"); data.writeUTF(failure.stackTraceToString().take(8000))
                            }
                            bytes.toByteArray()
                        }.get(15, TimeUnit.SECONDS)
                        output.write(response); output.flush()
                        done = command == "CLOSE"
                    }
                }
            }
            check(errors.isEmpty()) { errors.joinToString() }
            result.putString("result", "PASS")
            result.putInt("files_received", receivedHashes.size)
            result.putInt("files_sent", outgoingCompleted)
            result.putInt("android_sdk", Build.VERSION.SDK_INT)
            result.putString("transport", "Flash real TCP via ADB forwarding; Android batch helper; no physical Wi-Fi discovery")
        } catch (failure: Throwable) {
            result.putString("result", "FAIL")
            result.putString("failure", failure.stackTraceToString())
        } finally {
            engine.stop()
            serial.shutdown()
            serial.awaitTermination(5, TimeUnit.SECONDS)
            val cleaned = directory.deleteRecursively()
            result.putBoolean("created_fixtures_removed", cleaned)
            if (!cleaned) result.putString("result", "FAIL")
            instrumentation.finish(if (result.getString("result") == "PASS") Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        }
    }
}
