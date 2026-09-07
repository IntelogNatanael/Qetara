package com.example.wifidrop.protocol.flash

import com.example.wifidrop.protocol.*
import kr.jclab.noise.protocol.Noise
import java.io.*
import java.net.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in, temporary local file exchange. No persistent trust, credentials, automatic acceptance or retries.
 * Both users must compare the complete verification code shown for EACH file before approving it.
 * start binds synchronously; all network discovery, hashing and transfers run off the caller's thread.
 */
class FlashEngine(
    label: String,
    private val receiveDirectory: File,
    private val listener: FlashListener = object : FlashListener {},
    private val config: FlashConfig = FlashConfig()
) {
    private val label = cleanFlashLabel(label)
    private val lock = Any()
    private val events = Any()
    private var session: Session? = null

    private class Operation(var info: FlashOperation, val socket: Socket) {
        val cancelled = AtomicReference<String?>(null)
        var lastProgressNanos = 0L
        var approval: FlashApproval? = null
        var approvalDeadlineNanos: Long = 0
        val localDecision = CompletableFuture<Boolean>()
    }
    private class Session(val id: String, val key: ByteArray, val expires: Long, val deadlineNanos: Long, val server: ServerSocket) {
        val sockets = mutableSetOf<Socket>()
        val operations = linkedMapOf<String, Operation>()
        val peers = linkedMapOf<String, FlashPeer>()
        val workers = ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(4), daemonFactory("flash-io"))
        val decisions = Executors.newFixedThreadPool(4, daemonFactory("flash-decision"))
        val timer = Executors.newSingleThreadScheduledExecutor(daemonFactory("flash-expiry"))
        var discovery: FlashDiscovery? = null
        var queryRunning = false
    }

    fun start(): FlashState {
        synchronized(lock) {
            session?.let { if (System.currentTimeMillis() < it.expires && System.nanoTime() < it.deadlineNanos) return snapshotLocked() else error("expired") }
            val server = ServerSocket()
            try {
                server.reuseAddress = false
                val bind = config.bindAddress?.let { InetAddress.getByName(requireFlashAddress(it)) }
                server.bind(InetSocketAddress(bind, config.port), 4)
                val dh = Noise.createDH("25519")
                val key = try { dh.generateKeyPair(); ByteArray(dh.privateKeyLength).also { dh.getPrivateKey(it, 0) } } finally { dh.destroy() }
                val next = Session(UUID.randomUUID().toString(), key, System.currentTimeMillis() + config.lifetimeMs, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(config.lifetimeMs), server)
                try {
                    if (config.discoveryEnabled) next.discovery = FlashDiscovery(config.discoveryPort,
                        ownPeer = { ownPeer(next) }, onPeer = { rememberPeer(next, it) }, isActive = { isActive(next) })
                    session = next
                    next.timer.schedule({ deactivate(next, "expired") }, config.lifetimeMs, TimeUnit.MILLISECONDS)
                    next.timer.scheduleAtFixedRate({
                        if (System.currentTimeMillis() >= next.expires || System.nanoTime() >= next.deadlineNanos) deactivate(next, "expired")
                        else expireApprovals(next)
                    }, 25, 100, TimeUnit.MILLISECONDS)
                    Thread({ acceptLoop(next) }, "qetara-flash-listener").apply { isDaemon = true; start() }
                    next.discovery?.start()
                } catch (error: Exception) {
                    next.discovery?.close(); next.workers.shutdownNow(); next.decisions.shutdownNow(); next.timer.shutdownNow(); key.fill(0)
                    session = null
                    throw error
                }
            } catch (error: Exception) { server.close(); throw error }
        }
        notifyState()
        return snapshot()
    }

    fun stop() { synchronized(lock) { session }?.let { deactivate(it, "cancelled") } }
    fun snapshot(): FlashState = synchronized(lock) { snapshotLocked() }
    private fun snapshotLocked(): FlashState {
        val current = session ?: return FlashState(port = config.port)
        val now = System.currentTimeMillis()
        return FlashState(now < current.expires && System.nanoTime() < current.deadlineNanos, current.expires, current.id, current.server.localPort,
            current.peers.values.filter { it.expiresAtMs > now }.toList(),
            current.operations.values.mapNotNull { it.approval }, current.operations.values.map { it.info })
    }
    private fun event(action: () -> Unit) { synchronized(events) { try { action() } catch (_: Exception) { /* A UI callback cannot break transport cleanup. */ } } }
    private fun notifyState() = event { listener.onState(snapshot()) }
    private fun isActive(current: Session): Boolean = synchronized(lock) { session === current && System.currentTimeMillis() < current.expires && System.nanoTime() < current.deadlineNanos }
    private fun activeSession(): Session = synchronized(lock) { session?.takeIf { System.currentTimeMillis() < it.expires && System.nanoTime() < it.deadlineNanos } ?: error("inactive") }
    private fun checkActive(current: Session, op: Operation? = null) {
        op?.cancelled?.get()?.let { throw FlashFailure(it) }
        if (!isActive(current)) throw FlashFailure(if (System.currentTimeMillis() >= current.expires || System.nanoTime() >= current.deadlineNanos) "expired" else "cancelled")
    }
    private fun ownPeer(current: Session) = FlashPeer(current.id, label, "", current.server.localPort, current.expires)
    private fun rememberPeer(current: Session, peer: FlashPeer) {
        synchronized(lock) {
            if (session !== current || peer.id == current.id || peer.expiresAtMs <= System.currentTimeMillis()) return
            current.peers.entries.removeAll { it.value.expiresAtMs <= System.currentTimeMillis() }
            if (current.peers.size >= 64 && peer.id !in current.peers) current.peers.remove(current.peers.keys.first())
            current.peers[peer.id] = peer
        }
        notifyState()
    }
    fun discover() {
        val current = activeSession()
        current.discovery?.discover()
        notifyState()
    }
    /** Explicit fallback for broadcast isolation. No DNS or public internet addresses are accepted. */
    fun discoverAt(host: String, port: Int = FLASH_PORT) {
        val address = requireFlashAddress(host); require(port in 1..65535) { "invalid_address" }
        val current = activeSession()
        synchronized(lock) {
            checkActive(current)
            if (current.queryRunning) return
            current.queryRunning = true
        }
        try {
            current.workers.execute {
                val socket = Socket()
                try {
                    registerSocket(current, socket)
                    socket.connect(InetSocketAddress(address, port), config.handshakeTimeoutMs)
                    socket.soTimeout = config.handshakeTimeoutMs
                    val output = DataOutputStream(socket.getOutputStream().buffered())
                    val input = DataInputStream(socket.getInputStream().buffered())
                    output.flashHeader(FLASH_PROBE); output.flush()
                    require(input.flashKind() == FLASH_ANNOUNCE) { "incompatible" }
                    val peer = readFlashPeer(input, address).copy(port = port) // Preserve explicit ADB/NAT forwarded endpoint.
                    checkActive(current)
                    rememberPeer(current, peer)
                } catch (error: Exception) { if (isActive(current)) reportError(null, error) }
                finally { releaseSocket(current, socket); synchronized(lock) { current.queryRunning = false }; notifyState() }
            }
        } catch (error: RejectedExecutionException) { synchronized(lock) { current.queryRunning = false }; reportError(null, FlashFailure("busy")) }
    }
    fun send(file: File, peer: FlashPeer): String {
        requireFlashAddress(peer.address); require(peer.port in 1..65535)
        require(file.isFile && file.length() <= config.maxFileBytes) { "invalid_file" }
        require(peer.expiresAtMs > System.currentTimeMillis()) { "expired" }
        val current = activeSession()
        val op = Operation(FlashOperation(UUID.randomUUID().toString(), peer, sanitizeFileName(file.name), file.length(), true), Socket())
        synchronized(lock) {
            checkActive(current); require(peer.id != current.id) { "self_peer" }
            if (current.operations.size >= config.maxOperations) throw FlashFailure("busy")
            registerSocket(current, op.socket); reserveOperation(current, op)
        }
        notifyState()
        try { current.workers.execute { executeOperation(current, op) { sendFile(current, op, file, peer) } } }
        catch (error: RejectedExecutionException) { finishOperation(current, op); throw FlashFailure("busy") }
        return op.info.id
    }
    fun approve(requestId: String, accepted: Boolean): Boolean {
        val applied = synchronized(lock) {
            val current = session ?: return false
            val op = current.operations.values.firstOrNull { it.approval?.requestId == requestId } ?: return false
            val approval = op.approval ?: return false
            if (!isActive(current) || approval.expiresAtMs <= System.currentTimeMillis() || System.nanoTime() >= op.approvalDeadlineNanos || op.cancelled.get() != null) return false
            val changed = op.localDecision.complete(accepted)
            if (changed) op.approval = null
            changed
        }
        if (applied) notifyState()
        return applied
    }
    fun cancel(operationId: String): Boolean {
        val op = synchronized(lock) {
            val current = session?.operations?.get(operationId) ?: return false
            current.cancelled.compareAndSet(null, "cancelled")
            current.localDecision.complete(false)
            current.approval = null
            current
        }
        runCatching { op.socket.close() }
        notifyState()
        return true
    }
    private fun expireApprovals(current: Session) {
        val expired = synchronized(lock) {
            if (session !== current) return
            current.operations.values.filter { it.approval?.let { approval -> approval.expiresAtMs <= System.currentTimeMillis() || System.nanoTime() >= it.approvalDeadlineNanos } == true }
        }
        expired.forEach { it.cancelled.compareAndSet(null, "approval_expired"); it.localDecision.complete(false); runCatching { it.socket.close() } }
        if (expired.isNotEmpty()) notifyState()
    }
    private fun deactivate(current: Session, reason: String) {
        val sockets = synchronized(lock) {
            if (session !== current) return
            session = null
            current.operations.values.forEach { it.cancelled.compareAndSet(null, reason); it.localDecision.complete(false); it.approval = null }
            current.key.fill(0)
            current.sockets.toList()
        }
        runCatching { current.server.close() }; current.discovery?.close()
        sockets.forEach { runCatching { it.close() } }
        current.workers.shutdownNow(); current.decisions.shutdownNow(); current.timer.shutdownNow()
        notifyState()
        if (reason == "expired") reportError(null, FlashFailure(reason))
    }
    private fun registerSocket(current: Session, socket: Socket) = synchronized(lock) {
        checkActive(current)
        if (current.sockets.size >= 4) throw FlashFailure("busy")
        current.sockets.add(socket)
    }
    private fun releaseSocket(current: Session, socket: Socket) { runCatching { socket.close() }; synchronized(lock) { current.sockets.remove(socket) } }
    private fun reserveOperation(current: Session, op: Operation) {
        checkActive(current)
        if (current.operations.size >= config.maxOperations) throw FlashFailure("busy")
        current.operations[op.info.id] = op
    }
    private fun acceptLoop(current: Session) {
        while (isActive(current)) {
            val socket = try { current.server.accept() } catch (_: IOException) { break }
            try {
                requireFlashAddress(socket.inetAddress.hostAddress ?: "")
                registerSocket(current, socket)
                current.workers.execute {
                    try {
                        socket.soTimeout = config.handshakeTimeoutMs
                        val input = DataInputStream(socket.getInputStream().buffered())
                        val output = DataOutputStream(socket.getOutputStream().buffered())
                        when (input.flashKind()) {
                            FLASH_PROBE -> { checkActive(current); output.flashHeader(FLASH_ANNOUNCE); writeFlashPeer(output, ownPeer(current)); output.flush() }
                            FLASH_TRANSFER -> {
                                val op = Operation(FlashOperation(UUID.randomUUID().toString(), null, "", 0, false), socket)
                                synchronized(lock) { reserveOperation(current, op) }
                                notifyState()
                                executeOperation(current, op) { receiveFile(current, op, input, output) }
                            }
                            else -> throw FlashFailure("incompatible")
                        }
                    } catch (_: Exception) { /* Unauthenticated malformed/idle probes never generate approval or user notification spam. */ }
                    finally { releaseSocket(current, socket) }
                }
            } catch (_: Exception) { releaseSocket(current, socket) }
        }
    }
    private fun executeOperation(current: Session, op: Operation, action: () -> Unit) {
        try { checkActive(current, op); action() }
        catch (error: Exception) { reportError(op.info.id, op.cancelled.get()?.let(::FlashFailure) ?: error) }
        finally { finishOperation(current, op) }
    }
    private fun finishOperation(current: Session, op: Operation) {
        releaseSocket(current, op.socket)
        synchronized(lock) { current.operations.remove(op.info.id); op.approval = null; op.localDecision.complete(false) }
        notifyState()
    }
    private fun handshake(current: Session, op: Operation, input: DataInputStream, output: DataOutputStream): FlashChannel {
        val key = synchronized(lock) { checkActive(current, op); current.key.copyOf() }
        return try { flashHandshake(input, output, op.info.outgoing, key) } finally { key.fill(0) }
    }
    private fun hello(current: Session, channel: FlashChannel, op: Operation): FlashPeer {
        channel.write { it.writeInt(FLASH_HELLO); writeFlashPeer(it, ownPeer(current)) }
        val frame = channel.read()
        require(frame.readInt() == FLASH_HELLO) { "invalid_frame" }
        val remote = readFlashPeer(frame, op.socket.inetAddress.hostAddress ?: "")
        frame.requireEnd()
        require(remote.id != current.id) { "self_peer" }
        return remote
    }
    private fun confirmBoth(current: Session, op: Operation, channel: FlashChannel, peer: FlashPeer) {
        val deadline = minOf(current.expires, System.currentTimeMillis() + config.approvalTimeoutMs)
        val deadlineNanos = minOf(current.deadlineNanos, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(config.approvalTimeoutMs))
        val approval = FlashApproval(UUID.randomUUID().toString(), op.info.id, peer, op.info.fileName, op.info.totalBytes,
            op.info.outgoing, channel.verificationCode, deadline)
        op.socket.soTimeout = (config.approvalTimeoutMs + 1000).toInt()
        // One reader is reserved for the remote decision so rejection/cancel clears a pending local prompt promptly.
        val readerFinished = CountDownLatch(1)
        val remoteDecision = current.decisions.submit<Boolean> {
            try {
                val frame = channel.read()
                require(frame.readInt() == FLASH_DECISION) { "invalid_frame" }
                val accepted = frame.readBoolean(); frame.requireEnd()
                if (!accepted) op.localDecision.complete(false)
                accepted
            } catch (error: Exception) { op.localDecision.complete(false); throw error }
            finally { readerFinished.countDown() }
        }
        try {
            synchronized(lock) { checkActive(current, op); op.approval = approval; op.approvalDeadlineNanos = deadlineNanos }
            notifyState()
            event { if (isActive(current) && !op.localDecision.isDone) listener.onApproval(approval) }
            val accepted = op.localDecision.get((deadlineNanos - System.nanoTime()).coerceAtLeast(1), TimeUnit.NANOSECONDS)
            checkActive(current, op)
            if (System.currentTimeMillis() >= deadline || System.nanoTime() >= deadlineNanos) throw FlashFailure("approval_expired")
            channel.write { it.writeInt(FLASH_DECISION); it.writeBoolean(accepted) }
            if (!accepted) throw FlashFailure("rejected")
            val remoteAccepted = remoteDecision.get((deadlineNanos - System.nanoTime()).coerceAtLeast(1), TimeUnit.NANOSECONDS)
            if (!remoteAccepted) throw FlashFailure("rejected")
            checkActive(current, op)
            if (System.currentTimeMillis() >= deadline || System.nanoTime() >= deadlineNanos) throw FlashFailure("approval_expired")
            op.socket.soTimeout = config.transferTimeoutMs
        } catch (error: TimeoutException) { throw FlashFailure("approval_expired") }
        finally {
            synchronized(lock) { op.approval = null }
            if (!remoteDecision.isDone) { runCatching { op.socket.close() }; remoteDecision.cancel(true) }
            // The cipher must outlive its sole reader, including cancellation during a blocked read.
            runCatching { readerFinished.await(1, TimeUnit.SECONDS) }
            notifyState()
        }
    }
    private fun sendFile(current: Session, op: Operation, file: File, target: FlashPeer) {
        val hash = sha256File(file) { checkActive(current, op) }
        require(file.length() == op.info.totalBytes) { "invalid_file" }
        op.socket.connect(InetSocketAddress(requireFlashAddress(target.address), target.port), config.handshakeTimeoutMs)
        op.socket.soTimeout = config.handshakeTimeoutMs
        val input = DataInputStream(op.socket.getInputStream().buffered())
        val output = DataOutputStream(op.socket.getOutputStream().buffered())
        output.flashHeader(FLASH_TRANSFER); output.flush()
        handshake(current, op, input, output).use { channel ->
            val remote = hello(current, channel, op).copy(address = target.address, port = target.port)
            require(remote.id == target.id) { "peer_changed" }
            synchronized(lock) { op.info = op.info.copy(peer = remote) }
            channel.write { it.writeInt(FLASH_OFFER); it.writeUTF(op.info.fileName); it.writeLong(op.info.totalBytes); it.writeUTF(hash) }
            confirmBoth(current, op, channel, remote)
            var sent = 0L
            val bytes = ByteArray(FLASH_CHUNK_BYTES)
            file.inputStream().use { source ->
                while (true) {
                    checkActive(current, op)
                    val count = source.read(bytes)
                    if (count < 0) break
                    requireValidFileChunk(op.info.totalBytes, sent, count)
                    channel.write { it.writeInt(FLASH_CHUNK); it.writeInt(count); it.write(bytes, 0, count) }
                    sent += count
                    progress(op, sent)
                }
            }
            require(sent == op.info.totalBytes) { "invalid_file" }
            checkActive(current, op)
            channel.write { it.writeInt(FLASH_DONE) }
            val ack = channel.read()
            require(ack.readInt() == FLASH_ACK && ack.readBoolean()) { "unconfirmed" }; ack.requireEnd()
            event { listener.onCompleted(FlashCompleted(op.info.id, remote, op.info.fileName, true)) }
        }
    }
    private fun receiveFile(current: Session, op: Operation, input: DataInputStream, output: DataOutputStream) {
        handshake(current, op, input, output).use { channel ->
            val remote = hello(current, channel, op)
            val offer = channel.read()
            require(offer.readInt() == FLASH_OFFER) { "invalid_frame" }
            val name = sanitizeFileName(offer.readUTF())
            val total = offer.readLong(); require(total in 0..config.maxFileBytes) { "invalid_file" }
            val expectedHash = requireValidFileHash(offer.readUTF()); offer.requireEnd()
            synchronized(lock) { op.info = op.info.copy(peer = remote, fileName = name, totalBytes = total) }
            confirmBoth(current, op, channel, remote)
            checkActive(current, op)
            val directory = receiveDirectory.canonicalFile
            check(directory.mkdirs() || directory.isDirectory) { "storage_unavailable" }
            requireReceiveCapacity(total, 0, directory.usableSpace)
            val stagingDir = File(directory, ".flash-partial")
            require(stagingDir.canonicalFile.parentFile == directory) { "invalid_file" }
            check(stagingDir.mkdirs() || stagingDir.isDirectory) { "storage_unavailable" }
            val partial = File.createTempFile("flash-", ".part", stagingDir)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                FileOutputStream(partial).use { destination ->
                    while (true) {
                        checkActive(current, op)
                        val frame = channel.read()
                        when (frame.readInt()) {
                            FLASH_DONE -> { frame.requireEnd(); break }
                            FLASH_CHUNK -> {
                                val count = requireValidFileChunk(total, received, frame.readInt())
                                require(frame.available() == count) { "invalid_frame" }
                                val bytes = ByteArray(count); frame.readFully(bytes)
                                destination.write(bytes); digest.update(bytes); received += count
                                progress(op, received)
                            }
                            else -> throw FlashFailure("invalid_frame")
                        }
                    }
                    destination.flush(); destination.fd.sync()
                }
                if (received != total || digest.digest().toHexLower() != expectedHash) throw FlashFailure("integrity_failed")
                // Publication and Stop admission share one lock. Once published, cancellation never deletes the file.
                val published = flashPublishAndNotify(events, lock, publish = {
                    checkActive(current, op)
                    publishReceivedFile(partial, directory, name) { checkActive(current, op) }
                }, received = { saved ->
                    event { listener.onReceived(FlashReceived(op.info.id, remote, saved)) }
                })
                // A lost ACK cannot undo a verified local delivery; sender reports unconfirmed and never auto-retries.
                channel.write { it.writeInt(FLASH_ACK); it.writeBoolean(true) }
                event { listener.onCompleted(FlashCompleted(op.info.id, remote, published.name, false)) }
            } finally { partial.delete() }
        }
    }
    private fun progress(op: Operation, transferred: Long) {
        val now = System.nanoTime()
        if (op.lastProgressNanos == 0L || transferred == op.info.totalBytes || now - op.lastProgressNanos >= TimeUnit.MILLISECONDS.toNanos(100)) {
            op.lastProgressNanos = now
            event { listener.onProgress(FlashProgress(op.info.id, transferred, op.info.totalBytes, op.info.outgoing)) }
        }
    }
    private fun reportError(operationId: String?, error: Exception) {
        val code = when (error) {
            is FlashFailure -> error.code
            is InterruptedException, is CancellationException -> "cancelled"
            is TimeoutException, is SocketTimeoutException -> "timeout"
            is javax.crypto.BadPaddingException -> "integrity_failed"
            else -> error.message?.takeIf { it in setOf("invalid_file", "invalid_address", "incompatible", "peer_changed", "storage_unavailable", "invalid_frame", "unconfirmed") } ?: "connection_failed"
        }
        val message = when (code) {
            "cancelled" -> "Transferencia cancelada."
            "rejected" -> "La transferencia no fue aprobada en ambos equipos."
            "expired" -> "Flash terminó. Actívalo de nuevo para compartir."
            "approval_expired" -> "La confirmación caducó. Vuelve a enviar y compara el código en ambos equipos."
            "busy" -> "Flash está ocupado. Termina o cancela la operación actual."
            "invalid_file" -> "El archivo no está disponible o supera el tamaño permitido."
            "invalid_address" -> "Usa una dirección IPv4 de tu red local."
            "peer_changed" -> "La activación del otro equipo cambió. Vuelve a buscarlo."
            "integrity_failed", "invalid_frame" -> "El archivo no superó la verificación y no se guardó."
            "storage_unavailable" -> "No se pudo guardar el archivo en la carpeta elegida."
            "incompatible" -> "El otro equipo no usa una versión compatible de Flash."
            else -> "La conexión se interrumpió. Comprueba en el receptor si llegó el archivo antes de volver a enviarlo."
        }
        event { listener.onError(FlashError(operationId, code, message)) }
    }
}

internal class FlashFailure(val code: String) : IOException(code)
internal fun daemonFactory(name: String) = ThreadFactory { task -> Thread(task, "qetara-$name").apply { isDaemon = true } }
internal fun cleanFlashLabel(value: String): String = sanitizePeerLabel(value.filter { !it.isISOControl() && Character.getType(it) != Character.FORMAT.toInt() })
internal fun writeFlashPeer(output: DataOutputStream, peer: FlashPeer) {
    output.writeUTF(peer.id); output.writeUTF(cleanFlashLabel(peer.label)); output.writeInt(peer.port)
    output.writeLong((peer.expiresAtMs - System.currentTimeMillis()).coerceIn(0, 1_800_000))
}
internal fun readFlashPeer(input: DataInputStream, address: String): FlashPeer {
    val id = input.readUTF(); require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) { "invalid_frame" }
    val label = cleanFlashLabel(input.readUTF()); val port = input.readInt(); require(port in 1..65535) { "invalid_frame" }
    val remaining = input.readLong(); require(remaining in 1..1_800_000) { "expired" }
    return FlashPeer(id, label, requireFlashAddress(address), port, System.currentTimeMillis() + remaining)
}
