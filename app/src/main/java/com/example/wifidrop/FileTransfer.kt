package com.example.wifidrop

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import com.example.wifidrop.protocol.DEFAULT_PORT as WDRP_DEFAULT_PORT
import com.example.wifidrop.protocol.MAX_SECURE_FILE_CHUNK_BYTES
import com.example.wifidrop.protocol.MAX_SECURE_FRAME_BYTES
import com.example.wifidrop.protocol.NOISE_HANDSHAKE_MAX_FRAME
import com.example.wifidrop.protocol.NOISE_PROLOGUE_PREFIX
import com.example.wifidrop.protocol.NOISE_PROTOCOL_NO_PSK
import com.example.wifidrop.protocol.NOISE_PROTOCOL_WITH_PSK
import com.example.wifidrop.protocol.PACKET_CHALLENGE
import com.example.wifidrop.protocol.PACKET_CREDENTIALS_REQUEST
import com.example.wifidrop.protocol.PACKET_CREDENTIALS_RESPONSE
import com.example.wifidrop.protocol.PACKET_DISCOVERY_REQUEST
import com.example.wifidrop.protocol.PACKET_DISCOVERY_RESPONSE
import com.example.wifidrop.protocol.PACKET_FILE
import com.example.wifidrop.protocol.PACKET_HELLO
import com.example.wifidrop.protocol.PACKET_MESSAGE
import com.example.wifidrop.protocol.PACKET_RESULT
import com.example.wifidrop.protocol.PACKET_TOKEN_RESPONSE
import com.example.wifidrop.protocol.PROTOCOL_MAGIC
import com.example.wifidrop.protocol.PROTOCOL_VERSION
import com.example.wifidrop.protocol.SECURE_FRAME_FILE_CHUNK
import com.example.wifidrop.protocol.SECURE_FRAME_FILE_DONE
import com.example.wifidrop.protocol.SECURE_FRAME_FILE_META
import com.example.wifidrop.protocol.SECURE_FRAME_FILE_RESUME
import com.example.wifidrop.protocol.SECURE_FRAME_HELLO
import com.example.wifidrop.protocol.SECURE_FRAME_MESSAGE
import com.example.wifidrop.protocol.SECURE_FRAME_RESULT
import com.example.wifidrop.protocol.isSha256Hex
import com.example.wifidrop.protocol.normalizeToken
import com.example.wifidrop.protocol.randomToken
import com.example.wifidrop.protocol.requireValidMessage
import com.example.wifidrop.protocol.requireValidPin
import com.example.wifidrop.protocol.requireValidToken
import com.example.wifidrop.protocol.sanitizeClientId
import com.example.wifidrop.protocol.sanitizeFileName
import com.example.wifidrop.protocol.sanitizePeerLabel
import com.example.wifidrop.protocol.toHexLower
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import javax.crypto.BadPaddingException
import javax.crypto.ShortBufferException
import kr.jclab.noise.protocol.CipherState
import kr.jclab.noise.protocol.HandshakeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

data class SessionCredentialsPayload(
    val token: String,
    val pin: String,
    val expiresAtMs: Long
)

data class PeerDiscoveryPayload(
    val peerId: String,
    val peerLabel: String,
    val sessionActive: Boolean,
    val trustedByHost: Boolean,
    val globalLanJoined: Boolean
)

object FileTransfer {

    const val DEFAULT_PORT = WDRP_DEFAULT_PORT

    suspend fun sendFile(
        context: Context,
        fileUri: Uri,
        fileNameRaw: String,
        hostAddress: String,
        tokenRaw: String,
        pinRaw: String,
        clientIdRaw: String,
        clientLabelRaw: String,
        port: Int = DEFAULT_PORT,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit,
        awaitIfPaused: suspend () -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = requireValidToken(tokenRaw)
            val pin = requireValidPin(pinRaw)
            val clientId = sanitizeClientId(clientIdRaw)
            val clientLabel = sanitizePeerLabel(clientLabelRaw)
            val safeName = sanitizeFileName(
                fileNameRaw.ifBlank { "file_${System.currentTimeMillis()}" }
            )
            val (total, fileHash) = computeUriSha256AndLength(context, fileUri)
            val clientNonce = TransferSecurity.randomNonce()

            withRetry {
                Socket().use { socket ->
                    configureSocket(socket)
                    socket.connect(InetSocketAddress(hostAddress, port), 10_000)
                    socket.soTimeout = 120_000

                    val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                    val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

                    writeClientEnvelope(
                        output = out,
                        packetType = PACKET_FILE,
                        clientId = clientId,
                        clientLabel = clientLabel,
                        clientNonce = clientNonce
                    )

                    val challenge = readChallengeOrFailure(input)
                    val digest = TransferSecurity.computeDigest(
                        purpose = "FILE",
                        clientNonce = clientNonce,
                        serverNonce = challenge.serverNonce,
                        clientId = clientId,
                        tokenOrBlank = token,
                        pin = pin
                    )
                    out.writeUTF(digest)
                    out.flush()

                    establishSecureChannel(
                        context = context,
                        input = input,
                        output = out,
                        initiator = true,
                        token = token,
                        pin = pin,
                        usePsk = true,
                        purpose = "FILE"
                    ).use { channel ->
                        channel.writeFrame { frame ->
                            frame.writeInt(SECURE_FRAME_FILE_META)
                            frame.writeUTF(safeName)
                            frame.writeLong(total)
                            frame.writeUTF(fileHash)
                        }
                        val resumeOffset = readResumeOffset(channel, total)

                        context.contentResolver.openInputStream(fileUri)?.use { fileInput ->
                            val buffer = ByteArray(MAX_SECURE_FILE_CHUNK_BYTES)
                            if (resumeOffset > 0L) {
                                skipExactly(fileInput, resumeOffset)
                            }
                            var sent = resumeOffset
                            onProgress(sent, total)
                            while (true) {
                                awaitIfPaused()
                                currentCoroutineContext().ensureActive()
                                if (isCancelled()) throw CancellationException("cancelado por usuario")
                                val read = fileInput.read(buffer)
                                if (read <= 0) break

                                channel.writeFrame { frame ->
                                    frame.writeInt(SECURE_FRAME_FILE_CHUNK)
                                    frame.writeInt(read)
                                    frame.write(buffer, 0, read)
                                }
                                sent += read
                                onProgress(sent, total)
                            }
                        } ?: error("No se pudo abrir InputStream del archivo")

                        channel.writeFrame { it.writeInt(SECURE_FRAME_FILE_DONE) }
                        val (ok, message) = readSecureResult(channel)
                        check(ok) { "Receptor rechazo archivo: $message" }
                    }
                }
            }

            "Archivo enviado a $hostAddress:$port"
        }
    }

    suspend fun sendMessage(
        context: Context,
        hostAddress: String,
        tokenRaw: String,
        pinRaw: String,
        clientIdRaw: String,
        clientLabelRaw: String,
        messageRaw: String,
        port: Int = DEFAULT_PORT
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = requireValidToken(tokenRaw)
            val pin = requireValidPin(pinRaw)
            val clientId = sanitizeClientId(clientIdRaw)
            val clientLabel = sanitizePeerLabel(clientLabelRaw)
            val message = requireValidMessage(messageRaw)
            val clientNonce = TransferSecurity.randomNonce()

            withRetry {
                Socket().use { socket ->
                    configureSocket(socket)
                    socket.connect(InetSocketAddress(hostAddress, port), 8_000)
                    socket.soTimeout = 60_000

                    val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                    val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

                    writeClientEnvelope(
                        output = out,
                        packetType = PACKET_MESSAGE,
                        clientId = clientId,
                        clientLabel = clientLabel,
                        clientNonce = clientNonce
                    )

                    val challenge = readChallengeOrFailure(input)
                    val digest = TransferSecurity.computeDigest(
                        purpose = "MESSAGE",
                        clientNonce = clientNonce,
                        serverNonce = challenge.serverNonce,
                        clientId = clientId,
                        tokenOrBlank = token,
                        pin = pin
                    )
                    out.writeUTF(digest)
                    out.flush()

                    establishSecureChannel(
                        context = context,
                        input = input,
                        output = out,
                        initiator = true,
                        token = token,
                        pin = pin,
                        usePsk = true,
                        purpose = "MESSAGE"
                    ).use { channel ->
                        channel.writeFrame { frame ->
                            frame.writeInt(SECURE_FRAME_MESSAGE)
                            frame.writeLong(System.currentTimeMillis())
                            frame.writeUTF(message)
                        }
                        val (ok, ack) = readSecureResult(channel)
                        check(ok) { "Receptor rechazo mensaje: $ack" }
                    }
                }
            }

            "Mensaje enviado a $hostAddress:$port"
        }
    }

    suspend fun announcePresence(
        context: Context,
        hostAddress: String,
        tokenRaw: String,
        pinRaw: String,
        clientIdRaw: String,
        deviceLabelRaw: String,
        port: Int = DEFAULT_PORT
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = requireValidToken(tokenRaw)
            val pin = requireValidPin(pinRaw)
            val clientId = sanitizeClientId(clientIdRaw)
            val label = sanitizePeerLabel(deviceLabelRaw)
            val clientNonce = TransferSecurity.randomNonce()

            withRetry {
                Socket().use { socket ->
                    configureSocket(socket)
                    socket.connect(InetSocketAddress(hostAddress, port), 8_000)
                    socket.soTimeout = 20_000

                    val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                    val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

                    writeClientEnvelope(
                        output = out,
                        packetType = PACKET_HELLO,
                        clientId = clientId,
                        clientLabel = label,
                        clientNonce = clientNonce
                    )

                    val challenge = readChallengeOrFailure(input)
                    val digest = TransferSecurity.computeDigest(
                        purpose = "HELLO",
                        clientNonce = clientNonce,
                        serverNonce = challenge.serverNonce,
                        clientId = clientId,
                        tokenOrBlank = token,
                        pin = pin
                    )
                    out.writeUTF(digest)
                    out.flush()

                    establishSecureChannel(
                        context = context,
                        input = input,
                        output = out,
                        initiator = true,
                        token = token,
                        pin = pin,
                        usePsk = true,
                        purpose = "HELLO"
                    ).use { channel ->
                        channel.writeFrame { frame ->
                            frame.writeInt(SECURE_FRAME_HELLO)
                            frame.writeLong(System.currentTimeMillis())
                        }
                        val (ok, message) = readSecureResult(channel)
                        check(ok) { "HELLO rechazado: $message" }
                    }
                }
            }
        }
    }

    suspend fun requestSessionToken(
        hostAddress: String,
        clientIdRaw: String,
        deviceLabelRaw: String,
        port: Int = DEFAULT_PORT
    ): Result<SessionCredentialsPayload> = withContext(Dispatchers.IO) {
        requestSessionCredentials(
            hostAddress = hostAddress,
            clientIdRaw = clientIdRaw,
            deviceLabelRaw = deviceLabelRaw,
            port = port
        )
    }

    suspend fun requestSessionCredentials(
        hostAddress: String,
        clientIdRaw: String,
        deviceLabelRaw: String,
        port: Int = DEFAULT_PORT
    ): Result<SessionCredentialsPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val clientId = sanitizeClientId(clientIdRaw)
            val label = sanitizePeerLabel(deviceLabelRaw)
            val clientNonce = TransferSecurity.randomNonce()

            withRetry {
                Socket().use { socket ->
                    configureSocket(socket)
                    socket.connect(InetSocketAddress(hostAddress, port), 8_000)
                    socket.soTimeout = 12_000

                    val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                    val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

                    writeClientEnvelope(
                        output = out,
                        packetType = PACKET_CREDENTIALS_REQUEST,
                        clientId = clientId,
                        clientLabel = label,
                        clientNonce = clientNonce
                    )

                    out.flush()
                    socket.shutdownOutput()
                    readCredentialsResponseOrFailure(input)
                }
            }
        }
    }

    suspend fun probePeer(
        hostAddress: String,
        clientIdRaw: String,
        deviceLabelRaw: String,
        port: Int = DEFAULT_PORT
    ): Result<PeerDiscoveryPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val clientId = sanitizeClientId(clientIdRaw)
            val label = sanitizePeerLabel(deviceLabelRaw)
            val clientNonce = TransferSecurity.randomNonce()

            Socket().use { socket ->
                configureSocket(socket)
                socket.connect(InetSocketAddress(hostAddress, port), 450)
                socket.soTimeout = 900

                val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

                writeClientEnvelope(
                    output = out,
                    packetType = PACKET_DISCOVERY_REQUEST,
                    clientId = clientId,
                    clientLabel = label,
                    clientNonce = clientNonce
                )
                out.flush()
                socket.shutdownOutput()
                readDiscoveryResponseOrFailure(input)
            }
        }
    }

    suspend fun receiveLoop(
        context: Context,
        receiveDir: File,
        expectedTokenRaw: String,
        expectedPinRaw: String,
        sessionExpiresAtMs: Long,
        isPeerTrusted: (peerId: String) -> Boolean,
        isGlobalLanJoined: () -> Boolean = { false },
        isNoiseKeyCompatible: (peerId: String, noiseStaticKey: String) -> Boolean = { _, _ -> true },
        onNoiseKeyObserved: (peerId: String, noiseStaticKey: String) -> Unit = { _, _ -> },
        port: Int = DEFAULT_PORT,
        onStatus: (String) -> Unit,
        onPeerSeen: (peerId: String, peerAddress: String, peerLabel: String, trusted: Boolean) -> Unit,
        onCredentialsRequested: (peerId: String, peerAddress: String, peerLabel: String, trusted: Boolean) -> Boolean,
        onTrustRequired: (peerId: String, peerAddress: String, peerLabel: String) -> Unit,
        onMessageReceived: (peerId: String, peerAddress: String, peerLabel: String, message: String) -> Unit = { _, _, _, _ -> },
        onProgress: (fileName: String, receivedBytes: Long, totalBytes: Long) -> Unit,
        onFileReceived: (File) -> Unit,
        awaitIfPaused: suspend () -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ) = withContext(Dispatchers.IO) {
        val expectedToken = requireValidToken(expectedTokenRaw)
        val expectedPin = requireValidPin(expectedPinRaw)

        receiveDir.mkdirs()

        ServerSocket().use { server ->
            server.reuseAddress = true
            server.bind(InetSocketAddress(port))
            server.soTimeout = 1_000

            onStatus("Escuchando en puerto $port")

            while (currentCoroutineContext().isActive) {
                awaitIfPaused()
                currentCoroutineContext().ensureActive()

                try {
                    val client = server.accept()
                    client.use { socket ->
                        configureSocket(socket)
                        socket.soTimeout = 120_000

                        val remoteIp = socket.inetAddress?.hostAddress ?: "desconocido"
                        try {
                            receiveOne(
                                context = context,
                                socket = socket,
                                remoteIp = remoteIp,
                                receiveDir = receiveDir,
                                expectedToken = expectedToken,
                                expectedPin = expectedPin,
                                sessionExpiresAtMs = sessionExpiresAtMs,
                                isPeerTrusted = isPeerTrusted,
                                isGlobalLanJoined = isGlobalLanJoined,
                                isNoiseKeyCompatible = isNoiseKeyCompatible,
                                onNoiseKeyObserved = onNoiseKeyObserved,
                                onPeerSeen = onPeerSeen,
                                onCredentialsRequested = onCredentialsRequested,
                                onTrustRequired = onTrustRequired,
                                onMessageReceived = onMessageReceived,
                                onProgress = onProgress,
                                onFileReceived = onFileReceived,
                                awaitIfPaused = awaitIfPaused,
                                isCancelled = isCancelled
                            )
                        } catch (se: SecurityException) {
                            onStatus("Conexion rechazada de $remoteIp: ${se.message ?: "no autorizada"}")
                        } catch (_: CancellationException) {
                            onStatus("Transferencia cancelada por usuario.")
                        } catch (e: Exception) {
                            onStatus("Error procesando cliente $remoteIp: ${e.message ?: e::class.java.simpleName}")
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // Loop heartbeat for cancellation + pause checks.
                } catch (e: SocketException) {
                    if (currentCoroutineContext().isActive) {
                        throw e
                    }
                }
            }
        }
    }

    fun queryDisplayName(context: Context, uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    fun randomToken(length: Int = 8): String {
        return com.example.wifidrop.protocol.randomToken(length)
    }

    fun normalizeToken(raw: String): String {
        return com.example.wifidrop.protocol.normalizeToken(raw)
    }

    fun isValidToken(raw: String): Boolean {
        return com.example.wifidrop.protocol.isValidToken(raw)
    }

    private suspend fun receiveOne(
        context: Context,
        socket: Socket,
        remoteIp: String,
        receiveDir: File,
        expectedToken: String,
        expectedPin: String,
        sessionExpiresAtMs: Long,
        isPeerTrusted: (peerId: String) -> Boolean,
        isGlobalLanJoined: () -> Boolean,
        isNoiseKeyCompatible: (peerId: String, noiseStaticKey: String) -> Boolean,
        onNoiseKeyObserved: (peerId: String, noiseStaticKey: String) -> Unit,
        onPeerSeen: (peerId: String, peerAddress: String, peerLabel: String, trusted: Boolean) -> Unit,
        onCredentialsRequested: (peerId: String, peerAddress: String, peerLabel: String, trusted: Boolean) -> Boolean,
        onTrustRequired: (peerId: String, peerAddress: String, peerLabel: String) -> Unit,
        onMessageReceived: (peerId: String, peerAddress: String, peerLabel: String, message: String) -> Unit,
        onProgress: (fileName: String, receivedBytes: Long, totalBytes: Long) -> Unit,
        onFileReceived: (File) -> Unit,
        awaitIfPaused: suspend () -> Unit,
        isCancelled: () -> Boolean
    ) {
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

        input.use {
            val magic = input.readInt()
            if (magic != PROTOCOL_MAGIC) {
                throw SecurityException("protocolo no valido")
            }

            val version = input.readInt()
            if (version != PROTOCOL_VERSION) {
                throw SecurityException("version de protocolo no compatible")
            }

            val packetType = input.readInt()
            val clientId = sanitizeClientId(input.readUTF())
            val clientLabel = sanitizePeerLabel(input.readUTF())
            val clientNonce = input.readUTF().take(64)

            val trusted = isPeerTrusted(clientId)
            val isCredentialsRequest = packetType == PACKET_CREDENTIALS_REQUEST
            onPeerSeen(clientId, remoteIp, clientLabel, trusted)

            if (packetType == PACKET_DISCOVERY_REQUEST) {
                val localPeerId = LocalDeviceIdentity.getOrCreate(context)
                val localLabel = sanitizePeerLabel(Build.MODEL ?: "android")
                val sessionActive = !TransferSecurity.isExpired(sessionExpiresAtMs)
                writeDiscoveryResponsePacket(
                    output = output,
                    peerId = localPeerId,
                    peerLabel = localLabel,
                    sessionActive = sessionActive,
                    trustedByHost = trusted,
                    globalLanJoined = sessionActive && isGlobalLanJoined()
                )
                return
            }

            if (!trusted && !isCredentialsRequest) {
                onTrustRequired(clientId, remoteIp, clientLabel)
                writeResultPacket(output, false, "dispositivo_no_confiable")
                throw SecurityException("dispositivo no confiable")
            }

            if (TransferSecurity.isExpired(sessionExpiresAtMs)) {
                writeResultPacket(output, false, "sesion_expirada")
                throw SecurityException("sesion expirada")
            }

            if (isCredentialsRequest) {
                val allowCredentials = onCredentialsRequested(clientId, remoteIp, clientLabel, trusted)
                if (!allowCredentials) {
                    writeResultPacket(output, false, "confirmacion_host_requerida")
                    return
                }
                val trustedAfterApproval = trusted || isPeerTrusted(clientId)
                if (!trustedAfterApproval) {
                    writeResultPacket(output, false, "dispositivo_no_confiable")
                    return
                }
                writeCredentialsResponsePacket(
                    output = output,
                    token = expectedToken,
                    pin = expectedPin,
                    expiresAtMs = sessionExpiresAtMs
                )
                return
            }

            val purpose = when (packetType) {
                PACKET_HELLO -> "HELLO"
                PACKET_FILE -> "FILE"
                PACKET_MESSAGE -> "MESSAGE"
                else -> {
                    writeResultPacket(output, false, "tipo de paquete no soportado")
                    throw SecurityException("tipo de paquete no soportado")
                }
            }

            val serverNonce = TransferSecurity.randomNonce()
            writeChallengePacket(output, serverNonce, sessionExpiresAtMs)

            val responseDigest = input.readUTF().lowercase()
            val expectedDigest = TransferSecurity.computeDigest(
                purpose = purpose,
                clientNonce = clientNonce,
                serverNonce = serverNonce,
                clientId = clientId,
                tokenOrBlank = expectedToken,
                pin = expectedPin
            )
            if (responseDigest != expectedDigest) {
                writeResultPacket(output, false, "auth_invalida")
                throw SecurityException("autenticacion invalida")
            }

            establishSecureChannel(
                context = context,
                input = input,
                output = output,
                initiator = false,
                token = expectedToken,
                pin = expectedPin,
                usePsk = true,
                purpose = purpose
            ).use { channel ->
                val noiseKey = channel.remoteStaticKeyBase64
                if (!noiseKey.isNullOrBlank()) {
                    if (!isNoiseKeyCompatible(clientId, noiseKey)) {
                        writeSecureResult(channel, false, "noise_key_mismatch")
                        throw SecurityException("clave Noise no coincide para peer confiado")
                    }
                    onNoiseKeyObserved(clientId, noiseKey)
                }

                when (packetType) {
                    PACKET_HELLO -> {
                        val frame = channel.readFrameInput()
                        val frameType = frame.readInt()
                        require(frameType == SECURE_FRAME_HELLO) { "frame HELLO invalido" }
                        writeSecureResult(channel, true, "hello_ok")
                    }

                    PACKET_FILE -> {
                        try {
                            val saved = receiveEncryptedFilePayload(
                                channel = channel,
                                receiveDir = receiveDir,
                                onProgress = onProgress,
                                onFileReceived = onFileReceived,
                                awaitIfPaused = awaitIfPaused,
                                isCancelled = isCancelled
                            )
                            writeSecureResult(channel, true, "saved:${saved.name}")
                        } catch (e: Exception) {
                            writeSecureResult(channel, false, e.message ?: "file_error")
                            throw e
                        }
                    }

                    PACKET_MESSAGE -> {
                        try {
                            val frame = channel.readFrameInput()
                            val frameType = frame.readInt()
                            require(frameType == SECURE_FRAME_MESSAGE) { "frame MESSAGE invalido" }
                            frame.readLong()
                            val message = requireValidMessage(frame.readUTF())
                            onMessageReceived(clientId, remoteIp, clientLabel, message)
                            writeSecureResult(channel, true, "message_ok")
                        } catch (e: Exception) {
                            writeSecureResult(channel, false, e.message ?: "message_error")
                            throw e
                        }
                    }
                }
            }
        }

        output.close()
    }

    private suspend fun receiveEncryptedFilePayload(
        channel: SecureChannel,
        receiveDir: File,
        onProgress: (fileName: String, receivedBytes: Long, totalBytes: Long) -> Unit,
        onFileReceived: (File) -> Unit,
        awaitIfPaused: suspend () -> Unit,
        isCancelled: () -> Boolean
    ): File {
        val metaFrame = channel.readFrameInput()
        val metaType = metaFrame.readInt()
        require(metaType == SECURE_FRAME_FILE_META) { "frame FILE_META invalido" }

        val incomingName = sanitizeFileName(metaFrame.readUTF())
        val total = metaFrame.readLong()
        require(total >= 0L) { "tamano invalido para archivo" }
        val expectedHash = metaFrame.readUTF().lowercase().take(64)
        require(isSha256Hex(expectedHash)) { "hash de archivo invalido" }

        val partialDir = File(receiveDir, ".partial").apply { mkdirs() }
        val partial = partialFileFor(partialDir, incomingName, total, expectedHash)
        if (partial.exists() && partial.length() > total) {
            partial.delete()
        }
        var received = if (partial.exists()) partial.length().coerceIn(0L, total) else 0L
        writeResumeOffset(channel, received)
        onProgress(incomingName, received, total)

        FileOutputStream(partial, received > 0L).use { fos ->
                val out = BufferedOutputStream(fos)

                while (true) {
                    awaitIfPaused()
                    currentCoroutineContext().ensureActive()
                    if (isCancelled()) {
                        throw CancellationException("cancelado por usuario")
                    }

                    val frame = channel.readFrameInput()
                    when (frame.readInt()) {
                        SECURE_FRAME_FILE_CHUNK -> {
                            val chunkLen = frame.readInt()
                            require(chunkLen in 0..MAX_SECURE_FILE_CHUNK_BYTES) {
                                "chunk invalido: $chunkLen"
                            }

                            if (chunkLen > 0) {
                                val chunk = ByteArray(chunkLen)
                                frame.readFully(chunk)
                                out.write(chunk, 0, chunkLen)
                                received += chunkLen
                                onProgress(incomingName, received, total)
                            }

                            if (total > 0 && received > total) {
                                throw EOFException("transferencia excede tamano esperado")
                            }
                        }

                        SECURE_FRAME_FILE_DONE -> break
                        else -> throw EOFException("frame inesperado durante archivo")
                    }
                }

                out.flush()
            }

        if (total >= 0 && received != total) {
            throw EOFException("transferencia incompleta para $incomingName: $received/$total")
        }

        val actualHash = sha256OfFile(partial)
        if (actualHash != expectedHash) {
            partial.delete()
            throw SecurityException("integridad SHA-256 invalida para $incomingName")
        }

        val target = uniqueDestination(receiveDir, incomingName)
        moveFileAtomically(partial, target)
        onFileReceived(target)
        return target
    }

    private fun uniqueDestination(dir: File, name: String): File {
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var candidate = File(dir, name)
        var i = 1
        while (candidate.exists()) {
            val nextName = if (ext.isBlank()) "$base ($i)" else "$base ($i).$ext"
            candidate = File(dir, nextName)
            i++
        }
        return candidate
    }

    private suspend fun computeUriSha256AndLength(context: Context, uri: Uri): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        val input = context.contentResolver.openInputStream(uri)
            ?: error("No se pudo abrir InputStream del archivo")

        input.use { stream ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
                total += read
            }
        }

        return total to digest.digest().toHexLower()
    }

    private fun sha256OfFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHexLower()
    }

    private fun writeResumeOffset(channel: SecureChannel, resumeOffset: Long) {
        val safeOffset = resumeOffset.coerceAtLeast(0L)
        channel.writeFrame { frame ->
            frame.writeInt(SECURE_FRAME_FILE_RESUME)
            frame.writeLong(safeOffset)
        }
    }

    private fun readResumeOffset(channel: SecureChannel, totalBytes: Long): Long {
        val frame = channel.readFrameInput()
        val frameType = frame.readInt()
        require(frameType == SECURE_FRAME_FILE_RESUME) { "frame FILE_RESUME invalido" }
        val requested = frame.readLong()
        return requested.coerceIn(0L, totalBytes.coerceAtLeast(0L))
    }

    private fun skipExactly(input: InputStream, bytesToSkip: Long) {
        if (bytesToSkip <= 0L) return
        var remaining = bytesToSkip
        val buffer = ByteArray(32 * 1024)
        while (remaining > 0L) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) {
                throw EOFException("No se pudo reanudar: faltan bytes por saltar")
            }
            remaining -= read
        }
    }

    private fun partialFileFor(
        partialDir: File,
        incomingName: String,
        totalBytes: Long,
        expectedHash: String
    ): File {
        val nameSeed = "$incomingName|$totalBytes|$expectedHash"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(nameSeed.toByteArray(Charsets.UTF_8))
            .toHexLower()
            .take(24)
        val prefix = sanitizeFileName(incomingName).take(42).ifBlank { "file" }
        return File(partialDir, "${prefix}_${totalBytes}_$digest.part")
    }

    private fun moveFileAtomically(source: File, target: File) {
        if (source.renameTo(target)) return

        try {
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output, 64 * 1024)
                    output.flush()
                    output.fd.sync()
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }

        if (!source.delete()) {
            source.deleteOnExit()
        }
    }

    private fun contentLength(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val size = pfd.statSize
                if (size >= 0) size else -1L
            } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun configureSocket(socket: Socket) {
        socket.tcpNoDelay = true
        socket.keepAlive = true
    }

    private suspend fun <T> withRetry(
        attempts: Int = 3,
        baseDelayMs: Long = 350,
        block: suspend () -> T
    ): T {
        var last: Exception? = null
        repeat(attempts) { index ->
            try {
                return block()
            } catch (e: Exception) {
                last = e
                val isLast = index == attempts - 1
                if (isLast || !isRetryable(e)) throw e
                delay(baseDelayMs * (index + 1L))
            }
        }
        throw last ?: IllegalStateException("fallo de red desconocido")
    }

    private fun isRetryable(error: Throwable): Boolean {
        return error is SocketTimeoutException ||
            error is SocketException ||
            error is EOFException
    }

    private fun writeClientEnvelope(
        output: DataOutputStream,
        packetType: Int,
        clientId: String,
        clientLabel: String,
        clientNonce: String
    ) {
        output.writeInt(PROTOCOL_MAGIC)
        output.writeInt(PROTOCOL_VERSION)
        output.writeInt(packetType)
        output.writeUTF(clientId)
        output.writeUTF(clientLabel)
        output.writeUTF(clientNonce.take(64))
        output.flush()
    }

    private data class Challenge(
        val serverNonce: String,
        val expiresAtMs: Long
    )

    private fun writeChallengePacket(
        output: DataOutputStream,
        serverNonce: String,
        expiresAtMs: Long
    ) {
        output.writeInt(PROTOCOL_MAGIC)
        output.writeInt(PROTOCOL_VERSION)
        output.writeInt(PACKET_CHALLENGE)
        output.writeUTF(serverNonce.take(64))
        output.writeLong(expiresAtMs)
        output.flush()
    }

    private fun readChallengeOrFailure(input: DataInputStream): Challenge {
        val magic = input.readInt()
        require(magic == PROTOCOL_MAGIC) { "respuesta invalida (magic)" }
        val version = input.readInt()
        require(version == PROTOCOL_VERSION) { "respuesta invalida (version)" }

        return when (val packetType = input.readInt()) {
            PACKET_CHALLENGE -> {
                val nonce = input.readUTF()
                val expiresAt = input.readLong()
                if (TransferSecurity.isExpired(expiresAt)) {
                    throw IllegalStateException("sesion expirada en host")
                }
                Challenge(serverNonce = nonce, expiresAtMs = expiresAt)
            }

            PACKET_RESULT -> {
                val ok = input.readBoolean()
                val message = input.readUTF()
                if (!ok) throw IllegalStateException(message)
                throw IllegalStateException("respuesta inesperada")
            }

            else -> throw IllegalStateException("respuesta invalida (packet=$packetType)")
        }
    }

    private fun writeCredentialsResponsePacket(
        output: DataOutputStream,
        token: String,
        pin: String,
        expiresAtMs: Long
    ) {
        output.writeInt(PROTOCOL_MAGIC)
        output.writeInt(PROTOCOL_VERSION)
        output.writeInt(PACKET_CREDENTIALS_RESPONSE)
        output.writeUTF(token)
        output.writeUTF(pin)
        output.writeLong(expiresAtMs)
        output.flush()
    }

    private fun writeDiscoveryResponsePacket(
        output: DataOutputStream,
        peerId: String,
        peerLabel: String,
        sessionActive: Boolean,
        trustedByHost: Boolean,
        globalLanJoined: Boolean
    ) {
        output.writeInt(PROTOCOL_MAGIC)
        output.writeInt(PROTOCOL_VERSION)
        output.writeInt(PACKET_DISCOVERY_RESPONSE)
        output.writeUTF(sanitizeClientId(peerId))
        output.writeUTF(sanitizePeerLabel(peerLabel))
        output.writeBoolean(sessionActive)
        output.writeBoolean(trustedByHost)
        output.writeBoolean(globalLanJoined)
        output.flush()
    }

    private fun readCredentialsResponseOrFailure(input: DataInputStream): SessionCredentialsPayload {
        val magic = input.readInt()
        require(magic == PROTOCOL_MAGIC) { "respuesta invalida (magic)" }
        val version = input.readInt()
        require(version == PROTOCOL_VERSION) { "respuesta invalida (version)" }

        return when (val packetType = input.readInt()) {
            PACKET_CREDENTIALS_RESPONSE -> {
                val token = requireValidToken(input.readUTF())
                val pin = requireValidPin(input.readUTF())
                val expiresAt = input.readLong()
                SessionCredentialsPayload(token = token, pin = pin, expiresAtMs = expiresAt)
            }

            PACKET_TOKEN_RESPONSE -> {
                val token = requireValidToken(input.readUTF())
                val expiresAt = input.readLong()
                SessionCredentialsPayload(token = token, pin = "", expiresAtMs = expiresAt)
            }

            PACKET_RESULT -> {
                val ok = input.readBoolean()
                val message = input.readUTF()
                if (!ok) throw IllegalStateException(message)
                throw IllegalStateException("respuesta inesperada")
            }

            else -> throw IllegalStateException("respuesta invalida (packet=$packetType)")
        }
    }

    private fun readDiscoveryResponseOrFailure(input: DataInputStream): PeerDiscoveryPayload {
        val magic = input.readInt()
        require(magic == PROTOCOL_MAGIC) { "respuesta invalida (magic)" }
        val version = input.readInt()
        require(version == PROTOCOL_VERSION) { "respuesta invalida (version)" }

        return when (val packetType = input.readInt()) {
            PACKET_DISCOVERY_RESPONSE -> {
                val peerId = sanitizeClientId(input.readUTF())
                val peerLabel = sanitizePeerLabel(input.readUTF())
                val sessionActive = input.readBoolean()
                val trustedByHost = input.readBoolean()
                val globalLanJoined = try {
                    input.readBoolean()
                } catch (_: EOFException) {
                    false
                }
                PeerDiscoveryPayload(
                    peerId = peerId,
                    peerLabel = peerLabel,
                    sessionActive = sessionActive,
                    trustedByHost = trustedByHost,
                    globalLanJoined = globalLanJoined
                )
            }

            PACKET_RESULT -> {
                val ok = input.readBoolean()
                val message = input.readUTF()
                if (!ok) throw IllegalStateException(message)
                throw IllegalStateException("respuesta inesperada")
            }

            else -> throw IllegalStateException("respuesta invalida (packet=$packetType)")
        }
    }

    private fun writeResultPacket(
        output: DataOutputStream,
        ok: Boolean,
        messageRaw: String
    ) {
        output.writeInt(PROTOCOL_MAGIC)
        output.writeInt(PROTOCOL_VERSION)
        output.writeInt(PACKET_RESULT)
        output.writeBoolean(ok)
        output.writeUTF(
            messageRaw
                .trim()
                .ifBlank { if (ok) "ok" else "error" }
                .take(200)
        )
        output.flush()
    }

    private fun writeSecureResult(channel: SecureChannel, ok: Boolean, messageRaw: String) {
        channel.writeFrame { frame ->
            frame.writeInt(SECURE_FRAME_RESULT)
            frame.writeBoolean(ok)
            frame.writeUTF(
                messageRaw
                    .trim()
                    .ifBlank { if (ok) "ok" else "error" }
                    .take(200)
            )
        }
    }

    private fun readSecureResult(channel: SecureChannel): Pair<Boolean, String> {
        val frame = channel.readFrameInput()
        val frameType = frame.readInt()
        require(frameType == SECURE_FRAME_RESULT) { "frame RESULT invalido" }
        val ok = frame.readBoolean()
        val message = frame.readUTF().take(200)
        return ok to message
    }

    private fun establishSecureChannel(
        context: Context,
        input: DataInputStream,
        output: DataOutputStream,
        initiator: Boolean,
        token: String,
        pin: String,
        usePsk: Boolean,
        purpose: String
    ): SecureChannel {
        val role = if (initiator) HandshakeState.INITIATOR else HandshakeState.RESPONDER
        val protocol = if (usePsk) NOISE_PROTOCOL_WITH_PSK else NOISE_PROTOCOL_NO_PSK
        val handshake = HandshakeState(protocol, role)
        var success = false

        try {
            handshake.localKeyPair?.let { local ->
                val identity = NoiseIdentityStore.getOrCreate(context)
                local.setPrivateKey(identity.privateKey, 0)
            }

            val prologue = "$NOISE_PROLOGUE_PREFIX|$purpose".toByteArray(Charsets.UTF_8)
            handshake.setPrologue(prologue, 0, prologue.size)

            if (usePsk) {
                val psk = deriveNoisePsk(token, pin)
                handshake.setPreSharedKey(psk, 0, psk.size)
            }

            handshake.start()
            val outMsg = ByteArray(NOISE_HANDSHAKE_MAX_FRAME)
            val emptyPayload = ByteArray(0)

            while (handshake.action != HandshakeState.SPLIT) {
                when (handshake.action) {
                    HandshakeState.WRITE_MESSAGE -> {
                        val len = handshake.writeMessage(outMsg, 0, null, 0, 0)
                        output.writeInt(len)
                        output.write(outMsg, 0, len)
                        output.flush()
                    }

                    HandshakeState.READ_MESSAGE -> {
                        val len = input.readInt()
                        require(len in 1..NOISE_HANDSHAKE_MAX_FRAME) {
                            "frame de handshake invalido"
                        }
                        val inMsg = ByteArray(len)
                        input.readFully(inMsg)
                        handshake.readMessage(inMsg, 0, len, emptyPayload, 0)
                    }

                    else -> {
                        throw SecurityException("estado Noise invalido: ${handshake.action}")
                    }
                }
            }

            val pair = handshake.split()
            val remoteKey = handshake.remotePublicKey?.let { remote ->
                if (!remote.hasPublicKey()) {
                    null
                } else {
                    ByteArray(remote.publicKeyLength).also { key ->
                        remote.getPublicKey(key, 0)
                    }
                }
            }

            success = true
            return SecureChannel(
                input = input,
                output = output,
                sender = pair.sender,
                receiver = pair.receiver,
                remoteStaticKeyBase64 = remoteKey?.let {
                    Base64.encodeToString(it, Base64.NO_WRAP)
                }
            )
        } finally {
            if (!success) {
                handshake.destroy()
            }
        }
    }

    private fun deriveNoisePsk(token: String, pin: String): ByteArray {
        val material = "WIFIDROP_NOISE|$token|$pin".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(material)
    }

    private class SecureChannel(
        private val input: DataInputStream,
        private val output: DataOutputStream,
        private val sender: CipherState,
        private val receiver: CipherState,
        val remoteStaticKeyBase64: String?
    ) : AutoCloseable {

        fun writeFrame(writePayload: (DataOutputStream) -> Unit) {
            val plainOut = ByteArrayOutputStream()
            DataOutputStream(plainOut).use { frame ->
                writePayload(frame)
                frame.flush()
            }

            val plaintext = plainOut.toByteArray()
            require(plaintext.size <= MAX_SECURE_FRAME_BYTES) {
                "frame seguro demasiado grande"
            }

            val ciphertext = ByteArray(plaintext.size + sender.macLength)
            val cipherLen = sender.encryptWithAd(
                null,
                plaintext,
                0,
                ciphertext,
                0,
                plaintext.size
            )

            output.writeInt(cipherLen)
            output.write(ciphertext, 0, cipherLen)
            output.flush()
        }

        fun readFrameInput(): DataInputStream {
            val cipherLen = input.readInt()
            require(cipherLen in 0..(MAX_SECURE_FRAME_BYTES + 128)) {
                "frame seguro invalido: $cipherLen"
            }

            val ciphertext = ByteArray(cipherLen)
            input.readFully(ciphertext)

            val plaintext = ByteArray(cipherLen)
            val plainLen = try {
                receiver.decryptWithAd(null, ciphertext, 0, plaintext, 0, cipherLen)
            } catch (e: BadPaddingException) {
                throw SecurityException("MAC Noise invalida", e)
            } catch (e: ShortBufferException) {
                throw SecurityException("frame Noise invalido", e)
            }

            return DataInputStream(ByteArrayInputStream(plaintext, 0, plainLen))
        }

        override fun close() {
            sender.destroy()
            receiver.destroy()
        }
    }
}
