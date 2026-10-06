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
 * Both users compare one verification code for the exact offered selection on one Noise connection.
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
        val batchId = info.batchId
        val fileIds = mutableSetOf(info.id)
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
                } catch (error: Exception) { if (isActive(current)) reportError(null, error, discovery = true) }
                finally { releaseSocket(current, socket); synchronized(lock) { current.queryRunning = false }; notifyState() }
            }
        } catch (error: RejectedExecutionException) { synchronized(lock) { current.queryRunning = false }; reportError(null, FlashFailure("busy")) }
    }
    fun send(file: File, peer: FlashPeer): String = sendBatch(listOf(file), peer)

    /** A bounded, immutable selection; approval never survives this connection or applies to another batch. */
    fun sendBatch(files: List<File>, peer: FlashPeer): String {
        val selection = files.toList()
        require(selection.size in 1..FLASH_MAX_BATCH_FILES) { "invalid_batch" }
        requireFlashAddress(peer.address); require(peer.port in 1..65535)
        require(selection.all { it.isFile && it.canRead() && it.length() <= config.maxFileBytes }) { "invalid_file" }
        require(peer.expiresAtMs > System.currentTimeMillis()) { "expired" }
        val current = activeSession()
        val file = selection.first()
        val op = Operation(FlashOperation(UUID.randomUUID().toString(), peer, sanitizeFileName(file.name), file.length(), true,
            fileCount = selection.size), Socket())
        synchronized(lock) {
            checkActive(current); require(peer.id != current.id) { "self_peer" }
            if (current.operations.size >= config.maxOperations) throw FlashFailure("busy")
            registerSocket(current, op.socket); reserveOperation(current, op)
        }
        notifyState()
        try { current.workers.execute { executeOperation(current, op) { sendFiles(current, op, selection, peer) } } }
        catch (error: RejectedExecutionException) { finishOperation(current, op); throw FlashFailure("busy") }
        return op.batchId
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
            val current = session?.operations?.values?.firstOrNull { operationId in it.fileIds } ?: return false
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
                        when (val kind = input.flashKind()) {
                            FLASH_PROBE -> { checkActive(current); output.flashHeader(FLASH_ANNOUNCE); writeFlashPeer(output, ownPeer(current)); output.flush() }
                            FLASH_TRANSFER, FLASH_BATCH_TRANSFER -> {
                                val op = Operation(FlashOperation(UUID.randomUUID().toString(), null, "", 0, false), socket)
                                synchronized(lock) { reserveOperation(current, op) }
                                notifyState()
                                executeOperation(current, op) {
                                    val batch = kind == FLASH_BATCH_TRANSFER
                                    if (batch) { output.writeInt(FLASH_BATCH_TRANSFER); output.flush() }
                                    receiveFiles(current, op, input, output, batch)
                                }
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
        synchronized(lock) { current.operations.remove(op.batchId); op.approval = null; op.localDecision.complete(false) }
        notifyState()
    }
    private fun handshake(current: Session, op: Operation, input: DataInputStream, output: DataOutputStream, batch: Boolean = false): FlashChannel {
        val key = synchronized(lock) { checkActive(current, op); current.key.copyOf() }
        return try { flashHandshake(input, output, op.info.outgoing, key, batch) } finally { key.fill(0) }
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
    private fun confirmBoth(current: Session, op: Operation, channel: FlashChannel, peer: FlashPeer, manifest: List<OfferedFile>) {
        val deadline = minOf(current.expires, System.currentTimeMillis() + config.approvalTimeoutMs)
        val deadlineNanos = minOf(current.deadlineNanos, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(config.approvalTimeoutMs))
        val approval = FlashApproval(UUID.randomUUID().toString(), op.info.id, peer, op.info.fileName, manifestTotal(manifest),
            op.info.outgoing, channel.verificationCode, deadline, manifest.map { FlashOfferedFile(it.name, it.bytes) })
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
    private data class OfferedFile(val name: String, val bytes: Long, val hash: String)

    private fun manifestTotal(manifest: List<OfferedFile>): Long = manifest.fold(0L) { total, file ->
        require(file.bytes >= 0 && file.bytes <= Long.MAX_VALUE - total) { "invalid_batch" }
        total + file.bytes
    }

    private fun writeOffer(output: DataOutputStream, offered: OfferedFile) {
        output.writeUTF(offered.name); output.writeLong(offered.bytes); output.writeUTF(offered.hash)
    }

    private fun readOffer(input: DataInputStream): OfferedFile {
        val name = sanitizeFileName(input.readUTF())
        val size = input.readLong(); require(size in 0..config.maxFileBytes) { "invalid_file" }
        return OfferedFile(name, size, requireValidFileHash(input.readUTF()))
    }

    private fun selectFile(current: Session, op: Operation, remote: FlashPeer, manifest: List<OfferedFile>, index: Int) {
        val file = manifest[index]
        synchronized(lock) {
            checkActive(current, op)
            val id = if (index == 0) op.batchId else UUID.randomUUID().toString()
            op.fileIds.add(id)
            op.info = FlashOperation(id, remote, file.name, file.bytes, op.info.outgoing, op.batchId, index, manifest.size)
            op.lastProgressNanos = 0
        }
        // File identity changes, but admission/cancellation and authorization remain bound to one batch/socket.
        notifyState()
    }

    private fun sendFiles(current: Session, op: Operation, files: List<File>, target: FlashPeer) {
        val manifest = files.map { file ->
            checkActive(current, op)
            val size = file.length()
            require(file.isFile && size in 0..config.maxFileBytes) { "invalid_file" }
            val hash = sha256File(file) { checkActive(current, op) }
            require(file.length() == size) { "invalid_file" }
            OfferedFile(sanitizeFileName(file.name), size, hash)
        }
        manifestTotal(manifest)
        val batch = files.size > 1
        val offerBytes = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { offer ->
                offer.writeInt(if (batch) FLASH_BATCH_OFFER else FLASH_OFFER)
                if (batch) offer.writeInt(manifest.size)
                manifest.forEach { writeOffer(offer, it) }
            }
        }.toByteArray()
        require(offerBytes.size <= FLASH_FRAME_BYTES) { "invalid_batch" }
        op.socket.connect(InetSocketAddress(requireFlashAddress(target.address), target.port), config.handshakeTimeoutMs)
        op.socket.soTimeout = config.handshakeTimeoutMs
        val input = DataInputStream(op.socket.getInputStream().buffered())
        val output = DataOutputStream(op.socket.getOutputStream().buffered())
        output.flashHeader(if (batch) FLASH_BATCH_TRANSFER else FLASH_TRANSFER); output.flush()
        if (batch) {
            // Legacy receivers close unknown packet kinds. Never fall back to approving files separately.
            val supported = try { input.readInt() == FLASH_BATCH_TRANSFER } catch (_: IOException) { false }
            if (!supported) throw FlashFailure("batch_incompatible")
        }
        handshake(current, op, input, output, batch).use { channel ->
            val remote = hello(current, channel, op).copy(address = target.address, port = target.port)
            require(remote.id == target.id) { "peer_changed" }
            selectFile(current, op, remote, manifest, 0)
            channel.write { it.write(offerBytes) }
            confirmBoth(current, op, channel, remote, manifest)
            files.forEachIndexed { index, file ->
                if (index > 0) selectFile(current, op, remote, manifest, index)
                sendPayload(current, op, file, channel, remote)
            }
        }
    }

    private fun sendPayload(current: Session, op: Operation, file: File, channel: FlashChannel, remote: FlashPeer) {
            require(file.isFile && file.length() == op.info.totalBytes) { "invalid_file" }
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
    private fun receiveFiles(current: Session, op: Operation, input: DataInputStream, output: DataOutputStream, batch: Boolean) {
        handshake(current, op, input, output, batch).use { channel ->
            val remote = hello(current, channel, op)
            val offer = channel.read()
            require(offer.readInt() == if (batch) FLASH_BATCH_OFFER else FLASH_OFFER) { "invalid_frame" }
            val count = if (batch) offer.readInt().also { require(it in 2..FLASH_MAX_BATCH_FILES) { "invalid_batch" } } else 1
            val manifest = List(count) { readOffer(offer) }
            offer.requireEnd(); manifestTotal(manifest)
            selectFile(current, op, remote, manifest, 0)
            confirmBoth(current, op, channel, remote, manifest)
            manifest.forEachIndexed { index, file ->
                if (index > 0) selectFile(current, op, remote, manifest, index)
                receivePayload(current, op, channel, remote, file)
            }
        }
    }

    private fun receivePayload(current: Session, op: Operation, channel: FlashChannel, remote: FlashPeer, offered: OfferedFile) {
            val name = offered.name
            val total = offered.bytes
            val expectedHash = offered.hash
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
    private fun progress(op: Operation, transferred: Long) {
        val now = System.nanoTime()
        if (op.lastProgressNanos == 0L || transferred == op.info.totalBytes || now - op.lastProgressNanos >= TimeUnit.MILLISECONDS.toNanos(100)) {
            op.lastProgressNanos = now
            event { listener.onProgress(FlashProgress(op.info.id, transferred, op.info.totalBytes, op.info.outgoing)) }
        }
    }
    private fun reportError(operationId: String?, error: Exception, discovery: Boolean = false) {
        val failureCode = when (error) {
            is FlashFailure -> error.code
            is InterruptedException, is CancellationException -> "cancelled"
            is TimeoutException, is SocketTimeoutException -> "timeout"
            is javax.crypto.BadPaddingException -> "integrity_failed"
            else -> error.message?.takeIf { it in setOf("invalid_file", "invalid_batch", "invalid_address", "incompatible", "peer_changed", "storage_unavailable", "invalid_frame", "unconfirmed") } ?: "connection_failed"
        }
        // A probe exchanges no file data, even when files are selected or another transfer is active.
        val code = if (discovery && failureCode !in setOf("busy", "incompatible", "invalid_address")) "discovery_failed" else failureCode
        val message = when (code) {
            "discovery_failed" -> "No se pudo buscar esa dirección. Comprueba la IP, la red y que Flash esté activo en el otro equipo."
            "cancelled" -> "Transferencia cancelada."
            "rejected" -> "La transferencia no fue aprobada en ambos equipos."
            "expired" -> "Flash terminó. Actívalo de nuevo para compartir."
            "approval_expired" -> "La confirmación caducó. Vuelve a enviar y compara el código en ambos equipos."
            "busy" -> "Flash está ocupado. Termina o cancela la operación actual."
            "invalid_file" -> "El archivo no está disponible o supera el tamaño permitido."
            "invalid_batch" -> "El lote supera el límite de 128 archivos o el tamaño permitido para sus nombres y datos. Reduce la selección."
            "batch_incompatible" -> "El otro equipo no admite una confirmación por lote. Actualiza Qetara en ambos equipos."
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
