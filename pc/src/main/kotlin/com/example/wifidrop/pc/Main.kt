package com.example.wifidrop.pc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Colors
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.wifidrop.protocol.DEFAULT_PORT
import com.example.wifidrop.protocol.MAX_SECURE_FILE_CHUNK_BYTES
import com.example.wifidrop.protocol.MAX_SECURE_FRAME_BYTES
import com.example.wifidrop.protocol.NOISE_HANDSHAKE_MAX_FRAME
import com.example.wifidrop.protocol.NOISE_PROLOGUE_PREFIX
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
import com.example.wifidrop.protocol.PROTOCOL_MAGIC
import com.example.wifidrop.protocol.PROTOCOL_VERSION
import com.example.wifidrop.protocol.SECURE_FRAME_FILE_CHUNK
import com.example.wifidrop.protocol.SECURE_FRAME_FILE_DONE
import com.example.wifidrop.protocol.SECURE_FRAME_FILE_META
import com.example.wifidrop.protocol.SECURE_FRAME_FILE_RESUME
import com.example.wifidrop.protocol.SECURE_FRAME_HELLO
import com.example.wifidrop.protocol.SECURE_FRAME_MESSAGE
import com.example.wifidrop.protocol.SECURE_FRAME_RESULT
import com.example.wifidrop.protocol.computeDigest
import com.example.wifidrop.protocol.isSha256Hex
import com.example.wifidrop.protocol.isValidPin
import com.example.wifidrop.protocol.isValidToken
import com.example.wifidrop.protocol.normalizePin
import com.example.wifidrop.protocol.normalizeToken
import com.example.wifidrop.protocol.randomNonce
import com.example.wifidrop.protocol.requireValidPin
import com.example.wifidrop.protocol.requireValidMessage
import com.example.wifidrop.protocol.requireValidToken
import com.example.wifidrop.protocol.sanitizeClientId
import com.example.wifidrop.protocol.sanitizeFileName
import com.example.wifidrop.protocol.sanitizePeerLabel
import com.example.wifidrop.protocol.toHexLower
import kr.jclab.noise.protocol.CipherState
import kr.jclab.noise.protocol.HandshakeState
import kr.jclab.noise.protocol.Noise
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.awt.datatransfer.DataFlavor
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
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
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URI
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.BadPaddingException
import javax.crypto.ShortBufferException
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.system.exitProcess

private val uiLogTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val qetaraPanelShape = RoundedCornerShape(8.dp)
private val qetaraInk = Color(0xFF102A43)
private val qetaraInkDeep = Color(0xFF071827)
private val qetaraCanvas = Color(0xFFFFF7ED)
private val qetaraCanvasElevated = Color(0xFFFFFBF6)
private val qetaraTeal = Color(0xFF0A6B77)
private val qetaraCoral = Color(0xFFEE6C4D)
private val qetaraMist = Color(0xFFF4F9FB)
private val qetaraLine = Color(0xFFE7D8C9)
private const val qetaraLogoViewportSize = 108f
private const val qetaraHeaderLogoFillRatio = 0.90f
private const val qetaraWindowIconFillRatio = 0.78f
private const val qetaraHeaderLogoCenterOffsetX = -0.13f
private const val qetaraHeaderLogoCenterOffsetY = -0.07f
private const val qetaraWindowLogoCenterOffsetX = -0.18f
private const val qetaraWindowLogoCenterOffsetY = -0.17f
private const val DEVELOPER_GITHUB_URL = "https://github.com/IntelogNatanael"
private const val githubLogoViewportSize = 98f
private const val githubLogoPathData =
    "M41.4395 69.3848C28.8066 67.8535 19.9062 58.7617 19.9062 46.9902C19.9062 42.2051 21.6289 37.0371 24.5 33.5918C23.2559 30.4336 23.4473 23.7344 24.8828 20.959C28.7109 20.4805 33.8789 22.4902 36.9414 25.2656C40.5781 24.1172 44.4062 23.543 49.0957 23.543C53.7852 23.543 57.6133 24.1172 61.0586 25.1699C64.0254 22.4902 69.2891 20.4805 73.1172 20.959C74.457 23.543 74.6484 30.2422 73.4043 33.4961C76.4668 37.1328 78.0937 42.0137 78.0937 46.9902C78.0937 58.7617 69.1934 67.6621 56.3691 69.2891C59.623 71.3945 61.8242 75.9883 61.8242 81.252L61.8242 91.2051C61.8242 94.0762 64.2168 95.7031 67.0879 94.5547C84.4102 87.9512 98 70.6289 98 49.1914C98 22.1074 75.9883 0 48.9043 0C21.8203 0 0 22.1074 0 49.1914C0 70.4375 13.4941 88.0469 31.6777 94.6504C34.2617 95.6074 36.75 93.8848 36.75 91.3008L36.75 83.6445C35.4102 84.2188 33.6875 84.6016 32.1562 84.6016C25.8398 84.6016 22.1074 81.1563 19.4277 74.7441C18.375 72.1602 17.2266 70.6289 15.0254 70.3418C13.877 70.2461 13.4941 69.7676 13.4941 69.1934C13.4941 68.0449 15.4082 67.1836 17.3223 67.1836C20.0977 67.1836 22.4902 68.9063 24.9785 72.4473C26.8926 75.2227 28.9023 76.4668 31.2949 76.4668C33.6875 76.4668 35.2187 75.6055 37.4199 73.4043C39.0469 71.7773 40.291 70.3418 41.4395 69.3848Z"
private const val qetaraLogoPathData =
    "M78.8495,21.6103Q76.4784,21.8948 73.0165,23.2227Q69.5546,24.5505 64.7175,27.5856Q59.8804,30.6206 53.1464,36.0268Q45.6536,42.1918 40.8165,45.4639Q35.9794,48.7361 32.6598,50.0165Q29.3402,51.2969 26.2103,51.2969H24.6928Q25.2619,49.3052 26.0206,45.8907Q26.7794,42.4763 27.5856,38.4454Q28.3918,34.4144 29.1031,30.5258Q29.8144,26.6371 30.1938,23.6969H14.2598Q13.3113,29.8619 11.9361,36.7381Q10.5608,43.6144 9.0433,50.1588Q8.3794,53.3835 8.1897,55.6124Q8,57.8412 8,58.5052Q8,66.0928 12.7423,68.3691Q19.3814,67.4206 24.5505,67.1835Q29.7196,66.9464 31.8062,66.9464Q49.068,66.9464 63.532,71.6887Q77.9959,76.4309 90.0412,86.3897L100,75.3876Q94.5938,69.6021 86.7216,65.0495Q78.8495,60.4969 70.0763,57.699Q61.3031,54.901 52.9567,54.2371V53.8577Q57.0351,52.0557 59.9753,50.301Q62.9155,48.5464 66.4247,46.2701Q69.2701,44.468 71.9258,42.8557Q74.5814,41.2433 76.9526,39.8206Q79.4186,38.3031 81.8845,37.3072Q84.3505,36.3113 86.7216,35.6474Z"
private val qetaraDesktopColors: Colors = lightColors(
    primary = qetaraInk,
    primaryVariant = qetaraInkDeep,
    secondary = qetaraCoral,
    background = qetaraCanvas,
    surface = qetaraCanvasElevated,
    error = Color(0xFFB3261E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = qetaraInk,
    onSurface = qetaraInk,
    onError = Color.White
)
private val qetaraPanelBorder = qetaraLine
private val qetaraSubtleSurface = qetaraMist

private data class CliArgs(
    val token: String?,
    val pin: String?,
    val outputDir: File,
    val port: Int,
    val deviceLabel: String,
    val sessionMinutes: Long,
    val allowCredentialsShare: Boolean,
    val interactive: Boolean,
    val disableReceiver: Boolean,
    val sendHost: String?,
    val sendFilePath: String?,
    val retries: Int,
    val selfTest: Boolean,
    val help: Boolean,
    val gui: Boolean
)

private data class ReceiverConfig(
    val token: String,
    val pin: String,
    val outputDir: File,
    val port: Int,
    val deviceLabel: String,
    val sessionDurationMs: Long,
    val allowCredentialsShare: Boolean,
    val localPeerId: String
)

private data class SenderConfig(
    val token: String,
    val pin: String,
    val targetHost: String,
    val port: Int,
    val clientId: String,
    val clientLabel: String,
    val retries: Int,
    val localNoiseIdentity: NoiseStaticIdentity
)

private data class Envelope(
    val packetType: Int,
    val clientId: String,
    val clientLabel: String,
    val clientNonce: String
)

private data class NoiseStaticIdentity(
    val privateKey: ByteArray,
    val publicKey: ByteArray
)

private enum class DesktopTaskPhase {
    IDLE,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

private data class DesktopLogEntry(
    val timestamp: String,
    val level: String,
    val message: String,
    val isError: Boolean
)

private data class LocalNetworkEndpoint(
    val label: String,
    val address: String,
    val priority: Int
)

private data class DesktopLanPeer(
    val id: String,
    val label: String,
    val ip: String,
    val sessionActive: Boolean,
    val trustedByHost: Boolean,
    val globalLanJoined: Boolean,
    val lastSeenAtMs: Long
)

private data class DesktopDiscoveryPayload(
    val peerId: String,
    val peerLabel: String,
    val sessionActive: Boolean,
    val trustedByHost: Boolean,
    val globalLanJoined: Boolean
)

private enum class DesktopChatScope {
    DIRECT,
    GLOBAL_LAN
}

private enum class DesktopChatDirection {
    INCOMING,
    OUTGOING
}

private data class DesktopChatEntry(
    val timestamp: String,
    val scope: DesktopChatScope,
    val direction: DesktopChatDirection,
    val peerLabel: String,
    val peerAddress: String,
    val message: String,
    val isError: Boolean = false
)

private object DesktopIdentityStore {
    private const val DEVICE_ID_FILE = "device_id.txt"
    private const val NOISE_PRIVATE_FILE = "noise_private.b64"
    private const val NOISE_PUBLIC_FILE = "noise_public.b64"

    fun getOrCreateDeviceId(stateDir: File): String {
        val file = File(stateDir, DEVICE_ID_FILE)
        if (file.exists()) {
            val stored = file.readText(Charsets.UTF_8).trim()
            if (stored.isNotBlank()) return stored
        }
        val generated = UUID.randomUUID().toString()
        file.writeText(generated, Charsets.UTF_8)
        return generated
    }

    fun getOrCreateNoiseIdentity(stateDir: File): NoiseStaticIdentity {
        val privateFile = File(stateDir, NOISE_PRIVATE_FILE)
        val publicFile = File(stateDir, NOISE_PUBLIC_FILE)

        if (privateFile.exists() && publicFile.exists()) {
            val decoded = decodeIdentity(
                privateB64 = privateFile.readText(Charsets.UTF_8).trim(),
                publicB64 = publicFile.readText(Charsets.UTF_8).trim()
            )
            if (decoded != null) return decoded
        }

        val dh = Noise.createDH("25519")
        dh.generateKeyPair()
        val privateKey = ByteArray(dh.privateKeyLength)
        val publicKey = ByteArray(dh.publicKeyLength)
        dh.getPrivateKey(privateKey, 0)
        dh.getPublicKey(publicKey, 0)
        dh.destroy()

        privateFile.writeText(Base64.getEncoder().encodeToString(privateKey), Charsets.UTF_8)
        publicFile.writeText(Base64.getEncoder().encodeToString(publicKey), Charsets.UTF_8)

        return NoiseStaticIdentity(privateKey = privateKey, publicKey = publicKey)
    }

    private fun decodeIdentity(privateB64: String, publicB64: String): NoiseStaticIdentity? {
        return try {
            val privateKey = Base64.getDecoder().decode(privateB64)
            val publicKey = Base64.getDecoder().decode(publicB64)
            if (privateKey.size != 32 || publicKey.size != 32) {
                null
            } else {
                NoiseStaticIdentity(privateKey = privateKey, publicKey = publicKey)
            }
        } catch (_: Exception) {
            null
        }
    }
}

private class PcReceiverServer(
    private val config: ReceiverConfig,
    private val localNoiseIdentity: NoiseStaticIdentity,
    private val isGlobalLanJoined: () -> Boolean = { false },
    private val onMessageReceived: (DesktopChatEntry) -> Unit = {}
) {
    private val running = AtomicBoolean(true)
    private var serverSocket: ServerSocket? = null

    fun runBlocking() {
        config.outputDir.mkdirs()

        ServerSocket().use { server ->
            serverSocket = server
            server.reuseAddress = true
            server.bind(InetSocketAddress(config.port))
            server.soTimeout = 1_000

            println("Qetara PC Receiver listo.")
            println("Escuchando en puerto ${config.port}")
            println("Destino: ${config.outputDir.absolutePath}")
            println("Token: ${config.token} | PIN: ${config.pin}")
            println("Peer ID local: ${config.localPeerId.take(8)}...")
            println("Presiona Ctrl+C para detener.")

            while (running.get()) {
                try {
                    val socket = server.accept()
                    socket.use { client ->
                        configureSocket(client)
                        client.soTimeout = 120_000
                        handleClient(client)
                    }
                } catch (_: SocketTimeoutException) {
                    // heartbeat
                } catch (e: SocketException) {
                    if (running.get()) {
                        System.err.println("Error de socket: ${e.message ?: e::class.java.simpleName}")
                    }
                } catch (e: Exception) {
                    System.err.println("Error receptor: ${e.message ?: e::class.java.simpleName}")
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
    }

    private fun handleClient(socket: Socket) {
        val remoteIp = socket.inetAddress?.hostAddress ?: "desconocido"
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

        try {
            val envelope = readEnvelope(input)
            when (envelope.packetType) {
                PACKET_DISCOVERY_REQUEST -> {
                    writeDiscoveryResponsePacket(
                        output = output,
                        peerId = config.localPeerId,
                        peerLabel = sanitizePeerLabel(config.deviceLabel),
                        sessionActive = true,
                        trustedByHost = true,
                        globalLanJoined = isGlobalLanJoined()
                    )
                }

                PACKET_CREDENTIALS_REQUEST -> {
                    if (!config.allowCredentialsShare) {
                        writeResultPacket(output, false, "confirmacion_host_requerida")
                        return
                    }
                    writeCredentialsResponsePacket(
                        output = output,
                        token = config.token,
                        pin = config.pin,
                        expiresAtMs = System.currentTimeMillis() + config.sessionDurationMs
                    )
                }

                PACKET_HELLO,
                PACKET_FILE,
                PACKET_MESSAGE -> {
                    val purpose = when (envelope.packetType) {
                        PACKET_HELLO -> "HELLO"
                        PACKET_FILE -> "FILE"
                        else -> "MESSAGE"
                    }
                    val serverNonce = randomNonce()
                    val expiresAt = System.currentTimeMillis() + config.sessionDurationMs
                    writeChallengePacket(output, serverNonce, expiresAt)

                    val responseDigest = input.readUTF().lowercase()
                    val expectedDigest = computeDigest(
                        purpose = purpose,
                        clientNonce = envelope.clientNonce,
                        serverNonce = serverNonce,
                        clientId = envelope.clientId,
                        tokenOrBlank = config.token,
                        pin = config.pin
                    )
                    if (responseDigest != expectedDigest) {
                        writeResultPacket(output, false, "auth_invalida")
                        throw SecurityException("autenticación inválida de ${envelope.clientLabel} ($remoteIp)")
                    }

                    establishSecureChannel(
                        input = input,
                        output = output,
                        initiator = false,
                        token = config.token,
                        pin = config.pin,
                        purpose = purpose,
                        localIdentity = localNoiseIdentity
                    ).use { channel ->
                        when (envelope.packetType) {
                            PACKET_HELLO -> {
                                val frame = channel.readFrameInput()
                                val type = frame.readInt()
                                require(type == SECURE_FRAME_HELLO) { "frame HELLO inválido" }
                                frame.readLong()
                                writeSecureResult(channel, true, "hello_ok")
                                println("HELLO de ${envelope.clientLabel} @ $remoteIp")
                            }

                            PACKET_FILE -> {
                                try {
                                    val saved = receiveEncryptedFilePayload(
                                        channel = channel,
                                        receiveDir = config.outputDir,
                                        remoteIp = remoteIp,
                                        remoteLabel = envelope.clientLabel
                                    )
                                    writeSecureResult(channel, true, "saved:${saved.name}")
                                    println("Archivo recibido: ${saved.absolutePath}")
                                } catch (e: Exception) {
                                    writeSecureResult(channel, false, e.message ?: "file_error")
                                    throw e
                                }
                            }

                            PACKET_MESSAGE -> {
                                try {
                                    val frame = channel.readFrameInput()
                                    val type = frame.readInt()
                                    require(type == SECURE_FRAME_MESSAGE) { "frame MESSAGE inválido" }
                                    frame.readLong()
                                    val (scope, message) = decodeDesktopChatPayload(frame.readUTF())
                                    onMessageReceived(
                                        DesktopChatEntry(
                                            timestamp = LocalTime.now().format(uiLogTimeFormatter),
                                            scope = scope,
                                            direction = DesktopChatDirection.INCOMING,
                                            peerLabel = envelope.clientLabel,
                                            peerAddress = remoteIp,
                                            message = message
                                        )
                                    )
                                    writeSecureResult(channel, true, "message_ok")
                                    println("Mensaje de ${envelope.clientLabel} @ $remoteIp")
                                } catch (e: Exception) {
                                    writeSecureResult(channel, false, e.message ?: "message_error")
                                    throw e
                                }
                            }
                        }
                    }
                }

                else -> {
                    writeResultPacket(output, false, "tipo de paquete no soportado")
                }
            }
        } catch (e: Exception) {
            System.err.println("Cliente $remoteIp: ${e.message ?: e::class.java.simpleName}")
        } finally {
            try {
                output.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun receiveEncryptedFilePayload(
        channel: SecureChannel,
        receiveDir: File,
        remoteIp: String,
        remoteLabel: String
    ): File {
        val metaFrame = channel.readFrameInput()
        val metaType = metaFrame.readInt()
        require(metaType == SECURE_FRAME_FILE_META) { "frame FILE_META inválido" }

        val incomingName = sanitizeFileName(metaFrame.readUTF())
        val total = metaFrame.readLong()
        require(total >= 0L) { "tamaño inválido para archivo" }
        val expectedHash = metaFrame.readUTF().lowercase().take(64)
        require(isSha256Hex(expectedHash)) { "hash de archivo inválido" }

        val partialDir = File(receiveDir, ".partial").apply { mkdirs() }
        val partial = partialFileFor(partialDir, incomingName, total, expectedHash)
        if (partial.exists() && partial.length() > total) {
            partial.delete()
        }

        var received = if (partial.exists()) partial.length().coerceIn(0L, total) else 0L
        writeResumeOffset(channel, received)

        var lastPercent = -1
        logProgress(incomingName, received, total, remoteLabel, remoteIp, force = true)

        FileOutputStream(partial, received > 0L).use { fos ->
            val out = BufferedOutputStream(fos)
            while (true) {
                val frame = channel.readFrameInput()
                when (frame.readInt()) {
                    SECURE_FRAME_FILE_CHUNK -> {
                        val chunkLen = frame.readInt()
                        require(chunkLen in 0..MAX_SECURE_FILE_CHUNK_BYTES) {
                            "chunk inválido: $chunkLen"
                        }
                        if (chunkLen > 0) {
                            val chunk = ByteArray(chunkLen)
                            frame.readFully(chunk)
                            out.write(chunk, 0, chunkLen)
                            received += chunkLen
                        }
                        if (total > 0 && received > total) {
                            throw EOFException("transferencia excede tamaño esperado")
                        }
                        if (total > 0) {
                            val percent = ((received * 100) / total).toInt()
                            if (percent != lastPercent && percent % 5 == 0) {
                                lastPercent = percent
                                logProgress(incomingName, received, total, remoteLabel, remoteIp, force = false)
                            }
                        }
                    }

                    SECURE_FRAME_FILE_DONE -> break
                    else -> throw EOFException("frame inesperado durante archivo")
                }
            }
            out.flush()
        }

        if (received != total) {
            throw EOFException("transferencia incompleta para $incomingName: $received/$total")
        }

        val actualHash = sha256OfFile(partial)
        if (actualHash != expectedHash) {
            partial.delete()
            throw SecurityException("integridad SHA-256 inválida para $incomingName")
        }

        val target = uniqueDestination(receiveDir, incomingName)
        moveFileAtomically(partial, target)
        logProgress(incomingName, received, total, remoteLabel, remoteIp, force = true)
        return target
    }

    private fun logProgress(
        fileName: String,
        received: Long,
        total: Long,
        remoteLabel: String,
        remoteIp: String,
        force: Boolean
    ) {
        val pct = if (total > 0) ((received * 100) / total).toInt() else 100
        val line = "[$remoteLabel@$remoteIp] $fileName: $pct% (${formatBytes(received)}/${formatBytes(total)})"
        if (force) {
            println(line)
            return
        }
        println(line)
    }
}

private fun sendFileToPeer(
    file: File,
    config: SenderConfig
): String {
    require(file.exists() && file.isFile) { "Archivo no valido: ${file.absolutePath}" }
    val safeName = sanitizeFileName(file.name)
    val total = file.length()
    val fileHash = sha256OfFile(file)
    val clientNonce = randomNonce()
    var lastPercent = -1

    withRetry(attempts = config.retries.coerceAtLeast(1)) {
        Socket().use { socket ->
            configureSocket(socket)
            socket.connect(InetSocketAddress(config.targetHost, config.port), 10_000)
            socket.soTimeout = 120_000

            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

            writeClientEnvelope(
                output = output,
                packetType = PACKET_FILE,
                clientId = config.clientId,
                clientLabel = config.clientLabel,
                clientNonce = clientNonce
            )

            val challenge = readChallengeOrFailure(input)
            val digest = computeDigest(
                purpose = "FILE",
                clientNonce = clientNonce,
                serverNonce = challenge.serverNonce,
                clientId = config.clientId,
                tokenOrBlank = config.token,
                pin = config.pin
            )
            output.writeUTF(digest)
            output.flush()

            establishSecureChannel(
                input = input,
                output = output,
                initiator = true,
                token = config.token,
                pin = config.pin,
                purpose = "FILE",
                localIdentity = config.localNoiseIdentity
            ).use { channel ->
                channel.writeFrame { frame ->
                    frame.writeInt(SECURE_FRAME_FILE_META)
                    frame.writeUTF(safeName)
                    frame.writeLong(total)
                    frame.writeUTF(fileHash)
                }

                val resumeOffset = readResumeOffset(channel, total)
                FileInputStream(file).use { fileInput ->
                    if (resumeOffset > 0L) {
                        skipExactly(fileInput, resumeOffset)
                    }
                    var sent = resumeOffset
                    logSendProgress(
                        fileName = safeName,
                        sent = sent,
                        total = total,
                        host = config.targetHost,
                        force = true
                    )

                    val buffer = ByteArray(MAX_SECURE_FILE_CHUNK_BYTES)
                    while (true) {
                        val read = fileInput.read(buffer)
                        if (read <= 0) break
                        channel.writeFrame { frame ->
                            frame.writeInt(SECURE_FRAME_FILE_CHUNK)
                            frame.writeInt(read)
                            frame.write(buffer, 0, read)
                        }
                        sent += read
                        if (total > 0L) {
                            val percent = ((sent * 100L) / total).toInt()
                            if (percent != lastPercent && percent % 5 == 0) {
                                lastPercent = percent
                                logSendProgress(safeName, sent, total, config.targetHost, force = false)
                            }
                        }
                    }
                }

                channel.writeFrame { frame ->
                    frame.writeInt(SECURE_FRAME_FILE_DONE)
                }
                val (ok, message) = readSecureResult(channel)
                check(ok) { "Receptor rechazo archivo: $message" }
                logSendProgress(safeName, total, total, config.targetHost, force = true)
            }
        }
    }

    return "Archivo enviado a ${config.targetHost}:${config.port}"
}

private fun sendMessageToPeer(
    messageRaw: String,
    scope: DesktopChatScope,
    config: SenderConfig
): String {
    val message = encodeDesktopChatPayload(messageRaw, scope)
    val clientNonce = randomNonce()

    withRetry(attempts = config.retries.coerceAtLeast(1)) {
        Socket().use { socket ->
            configureSocket(socket)
            socket.connect(InetSocketAddress(config.targetHost, config.port), 10_000)
            socket.soTimeout = 60_000

            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

            writeClientEnvelope(
                output = output,
                packetType = PACKET_MESSAGE,
                clientId = config.clientId,
                clientLabel = config.clientLabel,
                clientNonce = clientNonce
            )

            val challenge = readChallengeOrFailure(input)
            val digest = computeDigest(
                purpose = "MESSAGE",
                clientNonce = clientNonce,
                serverNonce = challenge.serverNonce,
                clientId = config.clientId,
                tokenOrBlank = config.token,
                pin = config.pin
            )
            output.writeUTF(digest)
            output.flush()

            establishSecureChannel(
                input = input,
                output = output,
                initiator = true,
                token = config.token,
                pin = config.pin,
                purpose = "MESSAGE",
                localIdentity = config.localNoiseIdentity
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

    return "Mensaje enviado a ${config.targetHost}:${config.port}"
}

private fun logSendProgress(
    fileName: String,
    sent: Long,
    total: Long,
    host: String,
    force: Boolean
) {
    val pct = if (total > 0) ((sent * 100) / total).toInt() else 100
    if (force || pct in setOf(0, 100) || pct % 5 == 0) {
        println("[SEND->$host] $fileName: $pct% (${formatBytes(sent)}/${formatBytes(total)})")
    }
}

private class SecureChannel(
    private val input: DataInputStream,
    private val output: DataOutputStream,
    private val sender: CipherState,
    private val receiver: CipherState
) : AutoCloseable {
    fun writeFrame(writePayload: (DataOutputStream) -> Unit) {
        val plainOut = ByteArrayOutputStream()
        DataOutputStream(plainOut).use { frame ->
            writePayload(frame)
            frame.flush()
        }

        val plaintext = plainOut.toByteArray()
        require(plaintext.size <= MAX_SECURE_FRAME_BYTES) { "frame seguro demasiado grande" }

        val ciphertext = ByteArray(plaintext.size + sender.macLength)
        val cipherLen = sender.encryptWithAd(null, plaintext, 0, ciphertext, 0, plaintext.size)
        output.writeInt(cipherLen)
        output.write(ciphertext, 0, cipherLen)
        output.flush()
    }

    fun readFrameInput(): DataInputStream {
        val cipherLen = input.readInt()
        require(cipherLen in 0..(MAX_SECURE_FRAME_BYTES + 128)) {
            "frame seguro inválido: $cipherLen"
        }

        val ciphertext = ByteArray(cipherLen)
        input.readFully(ciphertext)

        val plaintext = ByteArray(cipherLen)
        val plainLen = try {
            receiver.decryptWithAd(null, ciphertext, 0, plaintext, 0, cipherLen)
        } catch (e: BadPaddingException) {
            throw SecurityException("MAC Noise inválida", e)
        } catch (e: ShortBufferException) {
            throw SecurityException("frame Noise inválido", e)
        }

        return DataInputStream(ByteArrayInputStream(plaintext, 0, plainLen))
    }

    override fun close() {
        sender.destroy()
        receiver.destroy()
    }
}

fun main(args: Array<String>) {
    val cli = parseCliArgs(args)
    if (cli.help) {
        printUsage()
        return
    }
    if (cli.gui) {
        runDesktopGui(cli)
        return
    }

    runCatching {
        val token = requireValidToken(cli.token ?: "")
        val pin = requireValidPin(cli.pin ?: "")
        val stateDir = File(System.getProperty("user.home"), ".qetara-pc").apply { mkdirs() }
        val localPeerId = DesktopIdentityStore.getOrCreateDeviceId(stateDir)
        val noiseIdentity = DesktopIdentityStore.getOrCreateNoiseIdentity(stateDir)

        val config = ReceiverConfig(
            token = token,
            pin = pin,
            outputDir = cli.outputDir,
            port = cli.port,
            deviceLabel = sanitizePeerLabel(cli.deviceLabel),
            sessionDurationMs = (cli.sessionMinutes.coerceAtLeast(1L) * 60_000L),
            allowCredentialsShare = cli.allowCredentialsShare,
            localPeerId = localPeerId
        )

        if (cli.selfTest) {
            runLocalE2eSelfTest(
                token = token,
                pin = pin,
                localPeerId = localPeerId,
                localNoiseIdentity = noiseIdentity,
                baseOutputDir = cli.outputDir,
                defaultPort = cli.port
            )
            return@runCatching
        }

        val receiverServer = if (!cli.disableReceiver) {
            PcReceiverServer(config, noiseIdentity)
        } else {
            null
        }
        val receiverThread = receiverServer?.let { server ->
            Thread {
                server.runBlocking()
            }.apply {
                isDaemon = cli.interactive || !cli.sendHost.isNullOrBlank() || cli.selfTest
                name = "wifidrop-receiver"
                start()
            }
        }

        Runtime.getRuntime().addShutdownHook(
            Thread {
                receiverServer?.stop()
            }
        )

        if (!cli.sendHost.isNullOrBlank() || !cli.sendFilePath.isNullOrBlank()) {
            require(!cli.sendHost.isNullOrBlank() && !cli.sendFilePath.isNullOrBlank()) {
                "Usa --send-host y --send-file juntos."
            }
            val senderConfig = SenderConfig(
                token = token,
                pin = pin,
                targetHost = cli.sendHost,
                port = cli.port,
                clientId = localPeerId,
                clientLabel = sanitizePeerLabel(cli.deviceLabel),
                retries = cli.retries,
                localNoiseIdentity = noiseIdentity
            )
            val result = sendFileToPeer(File(cli.sendFilePath), senderConfig)
            println(result)
        }

        if (cli.interactive) {
            runInteractiveShell(
                initialToken = token,
                initialPin = pin,
                initialHost = cli.sendHost,
                defaultPort = cli.port,
                retries = cli.retries,
                clientId = localPeerId,
                clientLabel = sanitizePeerLabel(cli.deviceLabel),
                localNoiseIdentity = noiseIdentity
            )
            receiverServer?.stop()
            receiverThread?.join(2_000)
        } else if (receiverThread != null) {
            receiverThread.join()
        }
    }.onFailure { error ->
        System.err.println("No pude iniciar el receptor: ${error.message ?: error::class.java.simpleName}")
        printUsage()
        exitProcess(1)
    }
}

private fun parseCliArgs(args: Array<String>): CliArgs {
    var token: String? = System.getenv("WIFIDROP_TOKEN")
    var pin: String? = System.getenv("WIFIDROP_PIN")
    var outputDir = File(System.getProperty("user.home"), "Downloads/WifiDrop")
    var port = DEFAULT_PORT
    var deviceLabel = defaultDeviceLabel()
    var sessionMinutes = 720L
    var allowCredentialsShare = false
    var interactive = true
    var disableReceiver = false
    var sendHost: String? = null
    var sendFilePath: String? = null
    var retries = 3
    var selfTest = false
    var help = false
    var gui = args.isEmpty()

    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--token" -> token = requireArgValue(args, ++i, arg)
            "--pin" -> pin = requireArgValue(args, ++i, arg)
            "--out" -> outputDir = File(requireArgValue(args, ++i, arg))
            "--port" -> {
                val value = requireArgValue(args, ++i, arg).toIntOrNull()
                    ?: throw IllegalArgumentException("Puerto inválido")
                require(value in 1..65535) { "Puerto fuera de rango" }
                port = value
            }

            "--label" -> deviceLabel = requireArgValue(args, ++i, arg)
            "--session-minutes" -> {
                val value = requireArgValue(args, ++i, arg).toLongOrNull()
                    ?: throw IllegalArgumentException("session-minutes inválido")
                require(value > 0) { "session-minutes debe ser > 0" }
                sessionMinutes = value
            }

            "--allow-credentials-share" -> allowCredentialsShare = true
            "--interactive" -> interactive = true
            "--no-interactive" -> interactive = false
            "--no-receiver" -> disableReceiver = true
            "--send-host" -> sendHost = requireArgValue(args, ++i, arg)
            "--send-file" -> sendFilePath = requireArgValue(args, ++i, arg)
            "--retries" -> {
                val value = requireArgValue(args, ++i, arg).toIntOrNull()
                    ?: throw IllegalArgumentException("retries inválido")
                require(value >= 1) { "retries debe ser >= 1" }
                retries = value
            }
            "--self-test" -> selfTest = true
            "--gui" -> gui = true
            "--no-gui" -> gui = false
            "--help", "-h" -> help = true
            else -> throw IllegalArgumentException("Argumento no reconocido: $arg")
        }
        i++
    }

    return CliArgs(
        token = token,
        pin = pin,
        outputDir = outputDir,
        port = port,
        deviceLabel = deviceLabel,
        sessionMinutes = sessionMinutes,
        allowCredentialsShare = allowCredentialsShare,
        interactive = interactive,
        disableReceiver = disableReceiver,
        sendHost = sendHost,
        sendFilePath = sendFilePath,
        retries = retries,
        selfTest = selfTest,
        help = help,
        gui = gui
    )
}

private fun requireArgValue(args: Array<String>, index: Int, flag: String): String {
    if (index >= args.size) throw IllegalArgumentException("Falta valor para $flag")
    return args[index]
}

private fun printUsage() {
    println(
        """
        Uso:
          .\gradlew.bat :pc:run --args="--token ABCD1234 --pin 123456"
          .\gradlew.bat :pc:run --args="--token ABCD1234 --pin 123456 --send-host 192.168.1.20 --send-file C:\ruta\archivo.zip"
        
        Opciones:
          --token <TOKEN>                  Token de sesión (o env WIFIDROP_TOKEN)
          --pin <PIN6>                     PIN de 6 dígitos (o env WIFIDROP_PIN)
          --out <CARPETA>                  Carpeta destino (default ~/Downloads/WifiDrop)
          --port <PUERTO>                  Puerto TCP (default 8988)
          --label <NOMBRE>                 Nombre visible del receptor
          --session-minutes <MIN>          TTL anunciado de sesión (default 720)
          --allow-credentials-share        Permite responder PACKET_CREDENTIALS_REQUEST
          --send-host <IP/HOST>            Envía un archivo al host indicado (PC, Android u otro Qetara)
          --send-file <RUTA_ARCHIVO>       Archivo a enviar en modo one-shot
          --retries <N>                    Reintentos de envío (default 3)
          --gui                            Inicia UI gráfica Compose Desktop (default sin args)
          --no-gui                         Fuerza modo CLI
          --interactive                    CLI interactivo (default activo)
          --no-interactive                 Desactiva CLI interactivo
          --no-receiver                    No levanta servidor receptor local
          --self-test                      Ejecuta pruebas E2E locales (hash + resume)
          --help                           Muestra esta ayuda
        """.trimIndent()
    )
}

private fun defaultDeviceLabel(): String {
    return runCatching {
        InetAddress.getLocalHost().hostName
    }.getOrDefault("PC")
}

private fun detectLocalNetworkEndpoints(): List<LocalNetworkEndpoint> {
    return runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { network ->
                runCatching {
                    network.isUp &&
                        !network.isLoopback
                }.getOrDefault(false)
            }
            .flatMap { network ->
                network.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { address ->
                        address.isSiteLocalAddress && !address.isLoopbackAddress
                    }
                    .map { address ->
                        LocalNetworkEndpoint(
                            label = networkDisplayLabel(network),
                            address = address.hostAddress,
                            priority = networkDisplayPriority(network)
                        )
                    }
            }
            .distinctBy { it.address }
            .sortedWith(compareBy<LocalNetworkEndpoint> { it.priority }.thenBy { it.label }.thenBy { it.address })
    }.getOrDefault(emptyList())
}

private fun networkDisplayPriority(network: NetworkInterface): Int {
    val label = "${network.name} ${network.displayName}".lowercase()
    return when {
        "wi-fi" in label || "wifi" in label || "wlan" in label -> 0
        "vpn" in label || "wireguard" in label || "tailscale" in label || "zerotier" in label -> 1
        isLikelyVirtualNetwork(label, network) -> 3
        "ethernet" in label || Regex("\\beth\\d*\\b").containsMatchIn(label) || "lan" in label -> 2
        runCatching { network.isVirtual }.getOrDefault(false) -> 3
        else -> 4
    }
}

private fun networkDisplayLabel(network: NetworkInterface): String {
    val label = "${network.name} ${network.displayName}".lowercase()
    return when {
        "wi-fi" in label || "wifi" in label || "wlan" in label -> "Wi-Fi"
        "vpn" in label || "wireguard" in label || "tailscale" in label || "zerotier" in label -> "VPN"
        isLikelyVirtualNetwork(label, network) -> "Virtual"
        "ethernet" in label || Regex("\\beth\\d*\\b").containsMatchIn(label) || "lan" in label -> "Ethernet"
        runCatching { network.isVirtual }.getOrDefault(false) -> "Virtual"
        else -> network.displayName?.trim()?.take(28)?.ifBlank { null } ?: network.name.take(28)
    }
}

private fun isLikelyVirtualNetwork(label: String, network: NetworkInterface): Boolean {
    return runCatching { network.isVirtual }.getOrDefault(false) ||
        "vethernet" in label ||
        "hyper-v" in label ||
        "wsl" in label ||
        "virtualbox" in label ||
        "vmware" in label ||
        "docker" in label
}

private fun copyToClipboard(text: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}

private fun endpointWithPort(address: String, port: Int?): String {
    return port?.let { "$address:$it" } ?: address
}

private fun subnetCandidates(localIpv4: String): List<String> {
    val trimmed = localIpv4.trim()
    val prefix = trimmed.substringBeforeLast(".", "")
    val own = trimmed.substringAfterLast(".", "").toIntOrNull()
    if (prefix.isBlank()) return emptyList()

    return (1..254)
        .asSequence()
        .filter { own == null || it != own }
        .map { "$prefix.$it" }
        .toList()
}

private fun discoverDesktopLanPeers(
    localEndpoints: List<LocalNetworkEndpoint>,
    port: Int,
    clientId: String,
    deviceLabel: String
): List<DesktopLanPeer> {
    val ownAddresses = localEndpoints.map { it.address }.toSet()
    val scanEndpoints = localEndpoints
        .filter { it.priority <= 2 }
        .ifEmpty { localEndpoints.take(1) }
    val candidates = scanEndpoints
        .flatMap { subnetCandidates(it.address) }
        .filterNot { it in ownAddresses }
        .distinct()

    if (candidates.isEmpty()) return emptyList()

    val poolSize = candidates.size.coerceAtMost(32).coerceAtLeast(1)
    val executor = Executors.newFixedThreadPool(poolSize)
    return try {
        val futures = candidates.map { ip ->
            executor.submit<DesktopLanPeer?> {
                probeDesktopLanPeer(
                    hostAddress = ip,
                    port = port,
                    clientId = clientId,
                    deviceLabel = deviceLabel
                )
            }
        }
        executor.shutdown()
        executor.awaitTermination(14, TimeUnit.SECONDS)
        futures
            .mapNotNull { future ->
                if (!future.isDone) {
                    null
                } else {
                    runCatching { future.get() }.getOrNull()
                }
            }
            .distinctBy { it.ip }
            .sortedWith(
                compareByDescending<DesktopLanPeer> { it.globalLanJoined }
                    .thenByDescending { it.trustedByHost }
                    .thenByDescending { it.lastSeenAtMs }
                    .thenBy { it.label }
            )
    } finally {
        executor.shutdownNow()
    }
}

private fun probeDesktopLanPeer(
    hostAddress: String,
    port: Int,
    clientId: String,
    deviceLabel: String
): DesktopLanPeer? {
    return runCatching {
        val nonce = randomNonce()
        Socket().use { socket ->
            configureSocket(socket)
            socket.connect(InetSocketAddress(hostAddress, port), 450)
            socket.soTimeout = 900

            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

            writeClientEnvelope(
                output = output,
                packetType = PACKET_DISCOVERY_REQUEST,
                clientId = clientId,
                clientLabel = sanitizePeerLabel(deviceLabel),
                clientNonce = nonce
            )
            output.flush()
            runCatching { socket.shutdownOutput() }

            val payload = readDiscoveryResponseOrFailure(input)
            DesktopLanPeer(
                id = payload.peerId,
                label = payload.peerLabel,
                ip = hostAddress,
                sessionActive = payload.sessionActive,
                trustedByHost = payload.trustedByHost,
                globalLanJoined = payload.globalLanJoined,
                lastSeenAtMs = System.currentTimeMillis()
            )
        }
    }.getOrNull()
}

private fun readDiscoveryResponseOrFailure(input: DataInputStream): DesktopDiscoveryPayload {
    val magic = input.readInt()
    require(magic == PROTOCOL_MAGIC) { "respuesta inválida (magic)" }
    val version = input.readInt()
    require(version == PROTOCOL_VERSION) { "respuesta inválida (version)" }

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
            DesktopDiscoveryPayload(
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

        else -> throw IllegalStateException("respuesta inválida (packet=$packetType)")
    }
}

private const val GLOBAL_LAN_CHAT_MARKER = "\u2063QGL\u2063"

private fun encodeDesktopChatPayload(messageRaw: String, scope: DesktopChatScope): String {
    val message = requireValidMessage(messageRaw)
    return when (scope) {
        DesktopChatScope.DIRECT -> message
        DesktopChatScope.GLOBAL_LAN -> GLOBAL_LAN_CHAT_MARKER + message
    }
}

private fun decodeDesktopChatPayload(messageRaw: String): Pair<DesktopChatScope, String> {
    val message = requireValidMessage(messageRaw)
    return if (message.startsWith(GLOBAL_LAN_CHAT_MARKER)) {
        DesktopChatScope.GLOBAL_LAN to requireValidMessage(message.removePrefix(GLOBAL_LAN_CHAT_MARKER))
    } else {
        DesktopChatScope.DIRECT to message
    }
}

private fun runDesktopGui(cli: CliArgs) {
    val stateDir = File(System.getProperty("user.home"), ".qetara-pc").apply { mkdirs() }
    val localPeerId = DesktopIdentityStore.getOrCreateDeviceId(stateDir)
    val noiseIdentity = DesktopIdentityStore.getOrCreateNoiseIdentity(stateDir)

    application {
        var tokenText by remember { mutableStateOf(normalizeToken(cli.token.orEmpty())) }
        var pinText by remember { mutableStateOf(normalizePin(cli.pin.orEmpty())) }
        var hostText by remember { mutableStateOf(cli.sendHost.orEmpty()) }
        var filePathText by remember { mutableStateOf(cli.sendFilePath.orEmpty()) }
        var outputDirText by remember { mutableStateOf(cli.outputDir.absolutePath) }
        var portText by remember { mutableStateOf(cli.port.toString()) }
        var deviceLabelText by remember { mutableStateOf(sanitizePeerLabel(cli.deviceLabel)) }
        var retriesText by remember { mutableStateOf(cli.retries.toString()) }
        var sessionMinutesText by remember { mutableStateOf(cli.sessionMinutes.toString()) }
        var allowCredentialsShare by remember { mutableStateOf(cli.allowCredentialsShare) }
        var chatDraftText by remember { mutableStateOf("") }
        var chatAttachmentPathText by remember { mutableStateOf("") }
        var openChatScope by remember { mutableStateOf<DesktopChatScope?>(null) }
        var chatPanelOffset by remember { mutableStateOf(IntOffset.Zero) }
        var selectedDirectPeerIp by remember { mutableStateOf<String?>(null) }
        var isGlobalLanJoined by remember { mutableStateOf(false) }

        var receiverPhase by remember { mutableStateOf(DesktopTaskPhase.IDLE) }
        var receiverStatus by remember { mutableStateOf("Listo para recibir desde otro equipo.") }
        var sendingPhase by remember { mutableStateOf(DesktopTaskPhase.IDLE) }
        var sendingStatus by remember { mutableStateOf("Listo para enviar a otro equipo.") }
        var messagePhase by remember { mutableStateOf(DesktopTaskPhase.IDLE) }
        var messageStatus by remember { mutableStateOf("Listo para mensajes cifrados.") }
        var lanDiscoveryPhase by remember { mutableStateOf(DesktopTaskPhase.IDLE) }
        var lanDiscoveryStatus by remember { mutableStateOf("Red sin actualizar.") }
        var isFileDragActive by remember { mutableStateOf(false) }
        var receiverServer by remember { mutableStateOf<PcReceiverServer?>(null) }
        var receiverThread by remember { mutableStateOf<Thread?>(null) }

        val logs = remember { mutableStateListOf<DesktopLogEntry>() }
        val chatMessages = remember { mutableStateListOf<DesktopChatEntry>() }
        val lanPeers = remember { mutableStateListOf<DesktopLanPeer>() }
        val globalLanJoinedFlag = remember { AtomicBoolean(false) }
        val logListState = rememberLazyListState()
        fun appendLog(message: String, isError: Boolean = false) {
            logs.add(
                DesktopLogEntry(
                    timestamp = LocalTime.now().format(uiLogTimeFormatter),
                    level = if (isError) "ERROR" else "INFO",
                    message = message,
                    isError = isError
                )
            )
            while (logs.size > 500) {
                logs.removeAt(0)
            }
        }

        fun appendChat(entry: DesktopChatEntry) {
            chatMessages.add(entry)
            while (chatMessages.size > 200) {
                chatMessages.removeAt(0)
            }
        }

        fun upsertLanPeer(peer: DesktopLanPeer) {
            val existingIndex = lanPeers.indexOfFirst { existing ->
                existing.id == peer.id || existing.ip == peer.ip
            }
            if (existingIndex >= 0) {
                lanPeers[existingIndex] = peer
            } else {
                lanPeers.add(peer)
            }
            val sortedPeers = lanPeers
                .distinctBy { it.ip }
                .sortedWith(
                    compareByDescending<DesktopLanPeer> { it.globalLanJoined }
                        .thenByDescending { it.trustedByHost }
                        .thenByDescending { it.lastSeenAtMs }
                        .thenBy { it.label }
                )
            lanPeers.clear()
            lanPeers.addAll(sortedPeers)
            if (selectedDirectPeerIp.isNullOrBlank() || lanPeers.none { it.ip == selectedDirectPeerIp }) {
                selectedDirectPeerIp = lanPeers.firstOrNull { it.sessionActive }?.ip
            }
        }

        fun mergeLanPeers(peers: List<DesktopLanPeer>) {
            peers.forEach(::upsertLanPeer)
        }

        fun selectSendFile(file: File, source: String) {
            if (!file.exists() || !file.isFile) {
                appendLog("Solo se pueden enviar archivos individuales.", isError = true)
                return
            }
            filePathText = file.absolutePath
            sendingStatus = "Archivo listo: ${file.name}"
            appendLog("$source: ${file.name}")
        }

        fun handleDroppedFiles(files: List<File>) {
            val file = files.firstOrNull { it.isFile }
            if (file == null) {
                appendLog("No se encontró un archivo válido en el drop.", isError = true)
                return
            }
            selectSendFile(file, "Archivo soltado")
            if (files.size > 1) {
                appendLog("Se preparo el primer archivo de ${files.size} elementos soltados.")
            }
        }

        fun selectChatAttachment(file: File, source: String) {
            if (!file.exists() || !file.isFile) {
                messagePhase = DesktopTaskPhase.ERROR
                messageStatus = "Selecciona un archivo válido."
                appendLog("Adjunto inválido para chat/canal.", isError = true)
                return
            }
            chatAttachmentPathText = file.absolutePath
            messagePhase = DesktopTaskPhase.IDLE
            messageStatus = "Adjunto listo: ${file.name}"
            appendLog("$source para chat/canal: ${file.name}")
        }

        fun parsePort(): Int? = portText.trim().toIntOrNull()?.takeIf { it in 1..65535 }
        fun parseRetries(): Int? = retriesText.trim().toIntOrNull()?.takeIf { it >= 1 }
        fun parseSessionMinutes(): Long? = sessionMinutesText.trim().toLongOrNull()?.takeIf { it >= 1L }

        val tokenError = when {
            tokenText.isBlank() -> "Ingresa un token."
            !isValidToken(tokenText) -> "Token inválido. Usa A-Z y 0-9, 4-32 caracteres."
            else -> null
        }
        val pinError = when {
            pinText.isBlank() -> "Ingresa un PIN."
            !isValidPin(pinText) -> "PIN inválido. Usa 6 dígitos."
            else -> null
        }
        val portError = if (parsePort() == null) "Puerto inválido. Usa un valor entre 1 y 65535." else null
        val retriesError = if (parseRetries() == null) "Reintentos inválidos. Usa un entero mayor o igual a 1." else null
        val sessionError = if (parseSessionMinutes() == null) "TTL inválido. Usa minutos mayores o iguales a 1." else null
        val outputDirError = run {
            val dir = File(outputDirText.trim())
            when {
                outputDirText.isBlank() -> "Define una carpeta de destino."
                dir.exists() && !dir.isDirectory -> "La ruta de salida no es una carpeta."
                else -> null
            }
        }
        val hostError = if (hostText.isBlank()) "Ingresa un host destino." else null
        val fileError = run {
            val file = File(filePathText.trim())
            when {
                filePathText.isBlank() -> "Selecciona un archivo."
                !file.exists() -> "El archivo no existe."
                !file.isFile -> "La ruta seleccionada no es un archivo."
                else -> null
            }
        }
        val receiverIssues = listOfNotNull(tokenError, pinError, portError, sessionError, outputDirError)
        val sendIssues = listOfNotNull(tokenError, pinError, fileError, hostError, portError, retriesError)
        val sendStepLabel = sendIssues.firstOrNull()?.let { "Siguiente paso: $it" } ?: "Estado: listo para enviar"

        fun setGlobalLanMembership(joined: Boolean) {
            isGlobalLanJoined = joined
            globalLanJoinedFlag.set(joined)
        }

        fun refreshLanPeers(manual: Boolean = true) {
            if (lanDiscoveryPhase == DesktopTaskPhase.STARTING || lanDiscoveryPhase == DesktopTaskPhase.RUNNING) return
            val port = parsePort() ?: run {
                lanDiscoveryPhase = DesktopTaskPhase.ERROR
                lanDiscoveryStatus = portError ?: "Puerto inválido."
                appendLog(lanDiscoveryStatus, isError = true)
                return
            }

            lanDiscoveryPhase = DesktopTaskPhase.STARTING
            lanDiscoveryStatus = if (manual) "Buscando equipos Qetara en la red..." else "Actualizando equipos de red..."
            appendLog(lanDiscoveryStatus)
            thread(
                start = true,
                isDaemon = true,
                name = "wifidrop-desktop-lan-discovery"
            ) {
                val endpoints = detectLocalNetworkEndpoints()
                val found = if (endpoints.isEmpty()) {
                    emptyList()
                } else {
                    discoverDesktopLanPeers(
                        localEndpoints = endpoints,
                        port = port,
                        clientId = localPeerId,
                        deviceLabel = sanitizePeerLabel(deviceLabelText)
                    )
                }
                SwingUtilities.invokeLater {
                    lanDiscoveryPhase = DesktopTaskPhase.IDLE
                    if (endpoints.isEmpty()) {
                        lanDiscoveryStatus = "No se detectó una IP local."
                        appendLog(lanDiscoveryStatus, isError = manual)
                        return@invokeLater
                    }
                    mergeLanPeers(found)
                    lanDiscoveryStatus = if (found.isEmpty()) {
                        "No hay equipos Qetara visibles en esta red."
                    } else {
                        "Equipos detectados: ${found.size}."
                    }
                    appendLog(lanDiscoveryStatus, isError = false)
                }
            }
            lanDiscoveryPhase = DesktopTaskPhase.RUNNING
        }

        fun moveChatPanel(dragAmount: Offset) {
            chatPanelOffset = IntOffset(
                x = (chatPanelOffset.x + dragAmount.x.roundToInt()).coerceIn(-520, 64),
                y = (chatPanelOffset.y + dragAmount.y.roundToInt()).coerceIn(0, 360)
            )
        }

        fun toggleChatScope(scope: DesktopChatScope) {
            if (scope == DesktopChatScope.GLOBAL_LAN && !isGlobalLanJoined) {
                setGlobalLanMembership(true)
                appendLog("Canal Wi-Fi activo en este equipo.")
            }
            val nextScope = if (openChatScope == scope) null else scope
            if (openChatScope == null && nextScope != null) {
                chatPanelOffset = IntOffset.Zero
            }
            if (nextScope == null) {
                chatPanelOffset = IntOffset.Zero
            }
            openChatScope = nextScope
            val oldestAllowed = System.currentTimeMillis() - 120_000L
            val shouldRefreshLan = lanPeers.isEmpty() || lanPeers.any { it.lastSeenAtMs < oldestAllowed }
            if (openChatScope != null && shouldRefreshLan) {
                refreshLanPeers(manual = false)
            }
        }

        fun startReceiver() {
            if (receiverPhase == DesktopTaskPhase.STARTING || receiverPhase == DesktopTaskPhase.RUNNING) return

            val token = runCatching { requireValidToken(tokenText) }
                .getOrElse {
                    receiverPhase = DesktopTaskPhase.ERROR
                    receiverStatus = it.message ?: "Token inválido."
                    appendLog(receiverStatus, isError = true)
                    return
                }
            val pin = runCatching { requireValidPin(pinText) }
                .getOrElse {
                    receiverPhase = DesktopTaskPhase.ERROR
                    receiverStatus = it.message ?: "PIN inválido."
                    appendLog(receiverStatus, isError = true)
                    return
                }
            val port = parsePort() ?: run {
                receiverPhase = DesktopTaskPhase.ERROR
                receiverStatus = portError ?: "Puerto inválido."
                appendLog(receiverStatus, isError = true)
                return
            }
            val ttlMinutes = parseSessionMinutes() ?: run {
                receiverPhase = DesktopTaskPhase.ERROR
                receiverStatus = sessionError ?: "TTL inválido."
                appendLog(receiverStatus, isError = true)
                return
            }
            val outDir = File(outputDirText.trim())
            if (outputDirError != null) {
                receiverPhase = DesktopTaskPhase.ERROR
                receiverStatus = outputDirError
                appendLog(receiverStatus, isError = true)
                return
            }
            if (!outDir.exists() && !outDir.mkdirs()) {
                receiverPhase = DesktopTaskPhase.ERROR
                receiverStatus = "No pude crear la carpeta de destino."
                appendLog(receiverStatus, isError = true)
                return
            }

            val config = ReceiverConfig(
                token = token,
                pin = pin,
                outputDir = outDir,
                port = port,
                deviceLabel = sanitizePeerLabel(deviceLabelText),
                sessionDurationMs = ttlMinutes * 60_000L,
                allowCredentialsShare = allowCredentialsShare,
                localPeerId = localPeerId
            )

            val server = PcReceiverServer(
                config = config,
                localNoiseIdentity = noiseIdentity,
                isGlobalLanJoined = { globalLanJoinedFlag.get() },
                onMessageReceived = { entry ->
                    SwingUtilities.invokeLater {
                        appendChat(entry)
                        upsertLanPeer(
                            DesktopLanPeer(
                                id = entry.peerAddress,
                                label = entry.peerLabel,
                                ip = entry.peerAddress,
                                sessionActive = true,
                                trustedByHost = true,
                                globalLanJoined = entry.scope == DesktopChatScope.GLOBAL_LAN,
                                lastSeenAtMs = System.currentTimeMillis()
                            )
                        )
                        messageStatus = "Mensaje recibido de ${entry.peerLabel}."
                        appendLog("Mensaje recibido de ${entry.peerLabel} @ ${entry.peerAddress}")
                    }
                }
            )
            receiverServer = server
            receiverPhase = DesktopTaskPhase.STARTING
            receiverStatus = "Iniciando receptor en puerto $port..."
            appendLog("Iniciando receptor en puerto $port. Carpeta: ${outDir.absolutePath}")

            val thread = thread(
                start = true,
                isDaemon = true,
                name = "wifidrop-desktop-receiver"
            ) {
                try {
                    server.runBlocking()
                    SwingUtilities.invokeLater {
                        receiverPhase = DesktopTaskPhase.IDLE
                        receiverStatus = "Receptor detenido."
                        receiverServer = null
                        receiverThread = null
                        appendLog("Receptor detenido.")
                    }
                } catch (error: Exception) {
                    SwingUtilities.invokeLater {
                        receiverPhase = DesktopTaskPhase.ERROR
                        receiverStatus = "Receptor con error: ${error.message ?: error::class.java.simpleName}"
                        receiverServer = null
                        receiverThread = null
                        appendLog(receiverStatus, isError = true)
                    }
                }
            }
            receiverThread = thread
            receiverPhase = DesktopTaskPhase.RUNNING
            receiverStatus = "Receptor activo en puerto $port."
        }

        fun stopReceiver() {
            val server = receiverServer
            if (server == null || receiverPhase == DesktopTaskPhase.IDLE || receiverPhase == DesktopTaskPhase.STOPPING) return
            receiverPhase = DesktopTaskPhase.STOPPING
            receiverStatus = "Deteniendo receptor..."
            appendLog("Deteniendo receptor...")
            thread(
                start = true,
                isDaemon = true,
                name = "wifidrop-desktop-stop"
            ) {
                runCatching { server.stop() }
                runCatching { receiverThread?.join(1_500) }
                SwingUtilities.invokeLater {
                    if (receiverPhase == DesktopTaskPhase.STOPPING) {
                        receiverPhase = DesktopTaskPhase.IDLE
                        receiverStatus = "Receptor detenido."
                        receiverServer = null
                        receiverThread = null
                    }
                }
            }
        }

        fun sendFile() {
            if (sendingPhase == DesktopTaskPhase.STARTING || sendingPhase == DesktopTaskPhase.RUNNING) return

            val token = runCatching { requireValidToken(tokenText) }
                .getOrElse {
                    sendingPhase = DesktopTaskPhase.ERROR
                    sendingStatus = it.message ?: "Token inválido."
                    appendLog(sendingStatus, isError = true)
                    return
                }
            val pin = runCatching { requireValidPin(pinText) }
                .getOrElse {
                    sendingPhase = DesktopTaskPhase.ERROR
                    sendingStatus = it.message ?: "PIN inválido."
                    appendLog(sendingStatus, isError = true)
                    return
                }
            val port = parsePort() ?: run {
                sendingPhase = DesktopTaskPhase.ERROR
                sendingStatus = portError ?: "Puerto inválido."
                appendLog(sendingStatus, isError = true)
                return
            }
            val retries = parseRetries() ?: run {
                sendingPhase = DesktopTaskPhase.ERROR
                sendingStatus = retriesError ?: "Reintentos inválidos."
                appendLog(sendingStatus, isError = true)
                return
            }
            val host = hostText.trim()
            if (hostError != null) {
                sendingPhase = DesktopTaskPhase.ERROR
                sendingStatus = hostError
                appendLog(sendingStatus, isError = true)
                return
            }
            val file = File(filePathText.trim())
            if (fileError != null) {
                sendingPhase = DesktopTaskPhase.ERROR
                sendingStatus = fileError
                appendLog(sendingStatus, isError = true)
                return
            }

            val senderConfig = SenderConfig(
                token = token,
                pin = pin,
                targetHost = host,
                port = port,
                clientId = localPeerId,
                clientLabel = sanitizePeerLabel(deviceLabelText),
                retries = retries,
                localNoiseIdentity = noiseIdentity
            )

            sendingPhase = DesktopTaskPhase.STARTING
            sendingStatus = "Enviando ${file.name} a $host:$port..."
            appendLog(sendingStatus)
            thread(
                start = true,
                isDaemon = true,
                name = "wifidrop-desktop-send"
            ) {
                try {
                    val result = sendFileToPeer(file, senderConfig)
                    SwingUtilities.invokeLater {
                        sendingPhase = DesktopTaskPhase.IDLE
                        sendingStatus = result
                        upsertLanPeer(
                            DesktopLanPeer(
                                id = host,
                                label = host,
                                ip = host,
                                sessionActive = true,
                                trustedByHost = true,
                                globalLanJoined = false,
                                lastSeenAtMs = System.currentTimeMillis()
                            )
                        )
                        appendLog(result)
                    }
                } catch (error: Exception) {
                    SwingUtilities.invokeLater {
                        sendingPhase = DesktopTaskPhase.ERROR
                        sendingStatus = "Falló envío: ${error.message ?: error::class.java.simpleName}"
                        appendLog(sendingStatus, isError = true)
                    }
                }
            }
            sendingPhase = DesktopTaskPhase.RUNNING
        }

        fun sendChatMessage(scope: DesktopChatScope) {
            if (messagePhase == DesktopTaskPhase.STARTING || messagePhase == DesktopTaskPhase.RUNNING) return

            val token = runCatching { requireValidToken(tokenText) }
                .getOrElse {
                    messagePhase = DesktopTaskPhase.ERROR
                    messageStatus = it.message ?: "Token inválido."
                    appendLog(messageStatus, isError = true)
                    return
                }
            val pin = runCatching { requireValidPin(pinText) }
                .getOrElse {
                    messagePhase = DesktopTaskPhase.ERROR
                    messageStatus = it.message ?: "PIN inválido."
                    appendLog(messageStatus, isError = true)
                    return
                }
            val port = parsePort() ?: run {
                messagePhase = DesktopTaskPhase.ERROR
                messageStatus = portError ?: "Puerto inválido."
                appendLog(messageStatus, isError = true)
                return
            }
            val retries = parseRetries() ?: run {
                messagePhase = DesktopTaskPhase.ERROR
                messageStatus = retriesError ?: "Reintentos inválidos."
                appendLog(messageStatus, isError = true)
                return
            }
            val message = chatDraftText.trim().takeIf { it.isNotBlank() }?.let { rawMessage ->
                runCatching { requireValidMessage(rawMessage) }.getOrElse {
                    messagePhase = DesktopTaskPhase.ERROR
                    messageStatus = "Escribe un mensaje."
                    appendLog(it.message ?: messageStatus, isError = true)
                    return
                }
            }
            val attachment = chatAttachmentPathText.trim().takeIf { it.isNotBlank() }?.let(::File)
            if (message == null && attachment == null) {
                messagePhase = DesktopTaskPhase.ERROR
                messageStatus = "Escribe un mensaje o adjunta un archivo."
                appendLog(messageStatus, isError = true)
                return
            }
            if (attachment != null && (!attachment.exists() || !attachment.isFile)) {
                messagePhase = DesktopTaskPhase.ERROR
                messageStatus = "El archivo adjunto no existe."
                appendLog(messageStatus, isError = true)
                return
            }
            if (scope == DesktopChatScope.GLOBAL_LAN && !isGlobalLanJoined) {
                setGlobalLanMembership(true)
            }
            val activeLanPeers = lanPeers
                .filter { it.sessionActive && it.ip.isNotBlank() }
                .distinctBy { it.ip }
            val targets = when (scope) {
                DesktopChatScope.DIRECT -> {
                    val selectedPeer = selectedDirectPeerIp
                        ?.let { selectedIp -> activeLanPeers.firstOrNull { it.ip == selectedIp } }
                        ?: activeLanPeers.firstOrNull()
                    if (selectedDirectPeerIp.isNullOrBlank() && selectedPeer != null) {
                        selectedDirectPeerIp = selectedPeer.ip
                    }
                    listOfNotNull(selectedPeer)
                }
                DesktopChatScope.GLOBAL_LAN -> activeLanPeers
            }
            if (targets.isEmpty()) {
                messagePhase = DesktopTaskPhase.ERROR
                messageStatus = when (scope) {
                    DesktopChatScope.DIRECT -> "No hay un equipo de red seleccionado para chat."
                    DesktopChatScope.GLOBAL_LAN -> "No hay equipos Qetara visibles en Canal Wi-Fi."
                }
                appendLog(messageStatus, isError = true)
                refreshLanPeers(manual = false)
                return
            }

            val targetLabel = if (scope == DesktopChatScope.GLOBAL_LAN) {
                "Canal Wi-Fi"
            } else {
                targets.first().label.ifBlank { targets.first().ip }
            }
            val baseConfig = SenderConfig(
                token = token,
                pin = pin,
                targetHost = targets.first().ip,
                port = port,
                clientId = localPeerId,
                clientLabel = sanitizePeerLabel(deviceLabelText),
                retries = retries,
                localNoiseIdentity = noiseIdentity
            )

            messagePhase = DesktopTaskPhase.STARTING
            val payloadLabel = when {
                message != null && attachment != null -> "mensaje y archivo"
                attachment != null -> "archivo"
                else -> "mensaje"
            }
            messageStatus = "Enviando $payloadLabel a ${if (targets.size == 1) targetLabel else "${targets.size} equipos"}..."
            appendLog(messageStatus)
            thread(
                start = true,
                isDaemon = true,
                name = "wifidrop-desktop-message"
            ) {
                val failures = mutableListOf<String>()
                val messageTargets = mutableListOf<DesktopLanPeer>()
                val fileTargets = mutableListOf<DesktopLanPeer>()
                targets.forEach { peer ->
                    val peerConfig = baseConfig.copy(targetHost = peer.ip)
                    if (message != null) {
                        runCatching {
                            sendMessageToPeer(
                                messageRaw = message,
                                scope = scope,
                                config = peerConfig
                            )
                        }.onSuccess {
                            messageTargets.add(peer)
                        }.onFailure { error ->
                            failures.add("${peer.ip} mensaje: ${error.message ?: error::class.java.simpleName}")
                        }
                    }
                    if (attachment != null) {
                        runCatching {
                            sendFileToPeer(attachment, peerConfig)
                        }.onSuccess {
                            fileTargets.add(peer)
                        }.onFailure { error ->
                            failures.add("${peer.ip} archivo: ${error.message ?: error::class.java.simpleName}")
                        }
                    }
                }
                SwingUtilities.invokeLater {
                    if (message != null && messageTargets.isNotEmpty()) {
                        appendChat(
                            DesktopChatEntry(
                                timestamp = LocalTime.now().format(uiLogTimeFormatter),
                                scope = scope,
                                direction = DesktopChatDirection.OUTGOING,
                                peerLabel = targetLabel,
                                peerAddress = messageTargets.joinToString(", ") { it.ip },
                                message = message
                            )
                        )
                        chatDraftText = ""
                    }
                    if (attachment != null && fileTargets.isNotEmpty()) {
                        appendChat(
                            DesktopChatEntry(
                                timestamp = LocalTime.now().format(uiLogTimeFormatter),
                                scope = scope,
                                direction = DesktopChatDirection.OUTGOING,
                                peerLabel = targetLabel,
                                peerAddress = fileTargets.joinToString(", ") { it.ip },
                                message = "Archivo enviado: ${attachment.name}"
                            )
                        )
                        chatAttachmentPathText = ""
                    }
                    if (failures.isEmpty()) {
                        messagePhase = DesktopTaskPhase.IDLE
                        messageStatus = when {
                            message != null && attachment != null && scope == DesktopChatScope.GLOBAL_LAN ->
                                "Mensaje y archivo publicados en Canal Wi-Fi (${targets.size} equipo(s))."
                            message != null && attachment != null ->
                                "Mensaje y archivo enviados a ${targets.first().label.ifBlank { targets.first().ip }}."
                            attachment != null && scope == DesktopChatScope.GLOBAL_LAN ->
                                "Archivo publicado en Canal Wi-Fi (${targets.size} equipo(s))."
                            attachment != null ->
                                "Archivo enviado a ${targets.first().label.ifBlank { targets.first().ip }}."
                            scope == DesktopChatScope.GLOBAL_LAN ->
                                "Mensaje publicado en Canal Wi-Fi (${targets.size} equipo(s))."
                            else ->
                                "Mensaje enviado a ${targets.first().label.ifBlank { targets.first().ip }}."
                        }
                        appendLog(messageStatus)
                    } else {
                        messagePhase = DesktopTaskPhase.ERROR
                        val sentCount = messageTargets.size + fileTargets.size
                        messageStatus = if (sentCount > 0) {
                            "Envío parcial con ${failures.size} fallo(s)."
                        } else {
                            "No se pudo enviar."
                        }
                        failures.forEach { appendLog(it, isError = true) }
                    }
                }
            }
            messagePhase = DesktopTaskPhase.RUNNING
        }

        DisposableEffect(Unit) {
            onDispose {
                receiverServer?.stop()
            }
        }
        val windowIcon = remember { QetaraWindowIconPainter() }
        val localNetworkEndpoints = remember { detectLocalNetworkEndpoints() }
        val primaryLocalEndpoint = localNetworkEndpoints.firstOrNull()

        Window(
            onCloseRequest = {
                receiverServer?.stop()
                exitApplication()
            },
            title = "Qetara PC",
            state = rememberWindowState(width = 1120.dp, height = 720.dp),
            icon = windowIcon
        ) {
            DisposableEffect(window) {
                val previousDropTarget = window.dropTarget
                val fileDropTarget = DropTarget(
                    window,
                    object : DropTargetAdapter() {
                        override fun dragEnter(event: DropTargetDragEvent) {
                            if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                isFileDragActive = true
                                event.acceptDrag(DnDConstants.ACTION_COPY)
                            } else {
                                event.rejectDrag()
                            }
                        }

                        override fun dragOver(event: DropTargetDragEvent) {
                            if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                isFileDragActive = true
                                event.acceptDrag(DnDConstants.ACTION_COPY)
                            } else {
                                event.rejectDrag()
                            }
                        }

                        override fun dragExit(event: DropTargetEvent) {
                            isFileDragActive = false
                        }

                        override fun drop(event: DropTargetDropEvent) {
                            isFileDragActive = false
                            if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                event.rejectDrop()
                                return
                            }

                            try {
                                event.acceptDrop(DnDConstants.ACTION_COPY)
                                val droppedFiles = (
                                    event.transferable
                                        .getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                                    ).orEmpty().filterIsInstance<File>()
                                handleDroppedFiles(droppedFiles)
                                event.dropComplete(droppedFiles.isNotEmpty())
                            } catch (error: Exception) {
                                appendLog(
                                    "No se pudo leer el archivo soltado: ${error.message ?: error::class.java.simpleName}",
                                    isError = true
                                )
                                event.dropComplete(false)
                            }
                        }
                    }
                )
                onDispose {
                    isFileDragActive = false
                    if (window.dropTarget === fileDropTarget) {
                        window.dropTarget = previousDropTarget
                    }
                }
            }
            MaterialTheme(colors = qetaraDesktopColors) {
                val receiverPhaseLabel = when (receiverPhase) {
                    DesktopTaskPhase.IDLE -> "inactivo"
                    DesktopTaskPhase.STARTING -> "iniciando"
                    DesktopTaskPhase.RUNNING -> "activo"
                    DesktopTaskPhase.STOPPING -> "deteniendo"
                    DesktopTaskPhase.ERROR -> "error"
                }
                val sendingPhaseLabel = when (sendingPhase) {
                    DesktopTaskPhase.IDLE -> "listo"
                    DesktopTaskPhase.STARTING -> "preparando"
                    DesktopTaskPhase.RUNNING -> "enviando"
                    DesktopTaskPhase.STOPPING -> "deteniendo"
                    DesktopTaskPhase.ERROR -> "error"
                }
                val protocolLabel = "WDRP v4 · NoisePSK"
                val credentialsReady = tokenError == null && pinError == null
                val credentialsLabel = if (credentialsReady) "listas" else "revisar"
                val sessionStatusPhase = if (credentialsReady) DesktopTaskPhase.RUNNING else DesktopTaskPhase.STARTING
                val sessionStatusTitle = if (credentialsReady) "Sesión lista" else "Sesión pendiente"
                val sessionStatusDetail = if (credentialsReady) "Token/PIN válidos" else "Falta token/PIN"
                val receiverStatusDetail = when (receiverPhase) {
                    DesktopTaskPhase.IDLE -> if (credentialsReady) "Inicia para recibir" else "Completa sesión"
                    DesktopTaskPhase.STARTING -> "Preparando receptor"
                    DesktopTaskPhase.RUNNING -> "Listo para recibir"
                    DesktopTaskPhase.STOPPING -> "Cerrando receptor"
                    DesktopTaskPhase.ERROR -> "Revisa el error"
                }
                val sendBlocked = !credentialsReady || portError != null || retriesError != null
                val sendStatusTitle = when {
                    sendingPhase == DesktopTaskPhase.IDLE && sendBlocked -> "Envío bloqueado"
                    sendingPhase == DesktopTaskPhase.IDLE -> "Envío listo"
                    else -> "Envío $sendingPhaseLabel"
                }
                val sendStatusDetail = when (sendingPhase) {
                    DesktopTaskPhase.IDLE -> when {
                        !credentialsReady -> "Completa sesión"
                        portError != null || retriesError != null -> "Revisa conexión"
                        fileError != null || hostError != null -> "Archivo y destino"
                        else -> "Todo listo"
                    }
                    DesktopTaskPhase.STARTING -> "Preparando archivo"
                    DesktopTaskPhase.RUNNING -> "Transferencia activa"
                    DesktopTaskPhase.STOPPING -> "Cerrando envío"
                    DesktopTaskPhase.ERROR -> "Revisa el error"
                }
                val sendStatusPhase = when {
                    sendingPhase != DesktopTaskPhase.IDLE -> sendingPhase
                    portError != null || retriesError != null -> DesktopTaskPhase.ERROR
                    sendBlocked -> DesktopTaskPhase.STARTING
                    else -> DesktopTaskPhase.IDLE
                }
                val localIpLabel = primaryLocalEndpoint?.address ?: "IP no detectada"
                val localEndpointLabel = primaryLocalEndpoint?.address?.let { ip ->
                    endpointWithPort(ip, parsePort())
                } ?: "IP no detectada"
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxHeight()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .width(460.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QetaraDesktopHeader(
                                localIpLabel = localIpLabel,
                                localIpToCopy = primaryLocalEndpoint?.address,
                                protocolLabel = protocolLabel,
                                onCopyLocalIp = { ip ->
                                    copyToClipboard(ip)
                                    appendLog("IP local copiada: $ip")
                                }
                            )
                            DesktopStatusBar(
                                items = listOf(
                                    DesktopStatusItemUi(
                                        title = sessionStatusTitle,
                                        detail = sessionStatusDetail,
                                        phase = sessionStatusPhase
                                    ),
                                    DesktopStatusItemUi(
                                        title = "Receptor $receiverPhaseLabel",
                                        detail = receiverStatusDetail,
                                        phase = receiverPhase
                                    ),
                                    DesktopStatusItemUi(
                                        title = sendStatusTitle,
                                        detail = sendStatusDetail,
                                        phase = sendStatusPhase
                                    )
                                )
                            )

                            DesktopSection(
                                title = "Enviar a otro equipo",
                                subtitle = "Pasa archivos a una PC o Android en la misma red.",
                                accentColor = qetaraCoral
                            ) {
                                DesktopFileDropZone(
                                    filePath = filePathText,
                                    isDragActive = isFileDragActive,
                                    onChooseFile = {
                                        chooseFilePath(filePathText)?.let {
                                            selectSendFile(File(it), "Archivo elegido")
                                        }
                                    }
                                )
                                OutlinedTextField(
                                    value = hostText,
                                    onValueChange = { hostText = it.trim() },
                                    label = { Text("Host destino (IP/hostname)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(10.dp),
                                            shape = RoundedCornerShape(50),
                                            color = if (sendIssues.isEmpty()) qetaraTeal else qetaraPanelBorder,
                                            elevation = 0.dp
                                        ) {}
                                        Text(
                                            sendStepLabel,
                                            style = MaterialTheme.typography.body2,
                                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = { sendFile() },
                                        enabled = sendIssues.isEmpty() && (
                                            sendingPhase == DesktopTaskPhase.IDLE ||
                                                sendingPhase == DesktopTaskPhase.ERROR
                                            ),
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = MaterialTheme.colors.secondary,
                                            contentColor = MaterialTheme.colors.onSecondary
                                        ),
                                        modifier = Modifier.width(170.dp)
                                    ) {
                                        Text(
                                            when (sendingPhase) {
                                                DesktopTaskPhase.RUNNING -> "Enviando..."
                                                DesktopTaskPhase.STARTING -> "Preparando..."
                                                DesktopTaskPhase.STOPPING -> "Deteniendo..."
                                                else -> "Enviar archivo"
                                            }
                                        )
                                    }
                                }
                            }

                            DesktopSection(
                                title = "Receptor PC",
                                subtitle = if (primaryLocalEndpoint != null) {
                                    "Este equipo recibe en $localEndpointLabel desde otra PC o Android."
                                } else {
                                    "Deja este equipo listo para recibir archivos desde otra PC o Android."
                                },
                                accentColor = qetaraTeal
                            ) {
                                OutlinedTextField(
                                    value = outputDirText,
                                    onValueChange = { outputDirText = it },
                                    label = { Text("Carpeta destino") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            chooseDirectoryPath(outputDirText)?.let { outputDirText = it }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Elegir carpeta")
                                    }
                                    Button(
                                        onClick = {
                                            if (receiverPhase == DesktopTaskPhase.RUNNING ||
                                                receiverPhase == DesktopTaskPhase.STARTING ||
                                                receiverPhase == DesktopTaskPhase.STOPPING
                                            ) {
                                                stopReceiver()
                                            } else {
                                                startReceiver()
                                            }
                                        },
                                        enabled = when (receiverPhase) {
                                            DesktopTaskPhase.RUNNING,
                                            DesktopTaskPhase.STARTING,
                                            DesktopTaskPhase.STOPPING -> true
                                            else -> receiverIssues.isEmpty()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = if (receiverPhase == DesktopTaskPhase.RUNNING) {
                                                qetaraInk
                                            } else {
                                                qetaraTeal
                                            },
                                            contentColor = MaterialTheme.colors.onPrimary
                                        ),
                                        modifier = Modifier.weight(1.45f)
                                    ) {
                                        Text(
                                            when (receiverPhase) {
                                                DesktopTaskPhase.RUNNING -> "Detener receptor"
                                                DesktopTaskPhase.STARTING -> "Iniciando..."
                                                DesktopTaskPhase.STOPPING -> "Deteniendo..."
                                                else -> "Iniciar receptor"
                                            }
                                        )
                                    }
                                }
                            }

                        }

                        Box(
                            modifier = Modifier
                                .width(500.dp)
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DesktopCompactSessionBlock(
                                    tokenText = tokenText,
                                    onTokenChange = { tokenText = normalizeToken(it) },
                                    tokenHasError = tokenText.isNotBlank() && !isValidToken(tokenText),
                                    pinText = pinText,
                                    onPinChange = { pinText = normalizePin(it) },
                                    pinHasError = pinText.isNotBlank() && !isValidPin(pinText),
                                    deviceLabelText = deviceLabelText,
                                    onDeviceLabelChange = { deviceLabelText = it.take(64) },
                                    credentialsLabel = credentialsLabel,
                                    allowCredentialsShare = allowCredentialsShare,
                                    onAllowCredentialsShareChange = { allowCredentialsShare = it }
                                )
                                DesktopEventsPanel(
                                    logs = logs,
                                    logListState = logListState,
                                    onClear = { logs.clear() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                )
                                DesktopBrandFooter(
                                    onOpenGithub = ::openDeveloperGithub,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            val floatingChatScope = openChatScope
                            if (floatingChatScope != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset { chatPanelOffset }
                                        .fillMaxHeight()
                                        .padding(top = 8.dp, end = 76.dp, bottom = 34.dp)
                                        .zIndex(8f)
                                ) {
                                    DesktopChatFloatingSheet(
                                        scope = floatingChatScope,
                                        lanPeers = lanPeers,
                                        selectedDirectPeerIp = selectedDirectPeerIp,
                                        onSelectDirectPeer = { selectedDirectPeerIp = it },
                                        globalLanJoined = isGlobalLanJoined,
                                        draft = chatDraftText,
                                        onDraftChange = { chatDraftText = it.take(2_000) },
                                        attachmentPath = chatAttachmentPathText,
                                        onChooseAttachment = {
                                            chooseFilePath(chatAttachmentPathText)?.let {
                                                selectChatAttachment(File(it), "Archivo adjunto")
                                            }
                                        },
                                        onClearAttachment = {
                                            chatAttachmentPathText = ""
                                            if (messagePhase == DesktopTaskPhase.ERROR) {
                                                messagePhase = DesktopTaskPhase.IDLE
                                            }
                                            messageStatus = "Adjunto quitado."
                                        },
                                        messages = chatMessages,
                                        status = messageStatus,
                                        messagePhase = messagePhase,
                                        discoveryStatus = lanDiscoveryStatus,
                                        discoveryPhase = lanDiscoveryPhase,
                                        onRefreshLan = { refreshLanPeers(manual = true) },
                                        onSend = { scope -> sendChatMessage(scope) },
                                        onClose = {
                                            openChatScope = null
                                            chatPanelOffset = IntOffset.Zero
                                        },
                                        onDrag = ::moveChatPanel,
                                        modifier = Modifier.fillMaxHeight()
                                    )
                                }
                            }
                            DesktopChatFloatingRail(
                                activeScope = openChatScope,
                                onSelectScope = { scope -> toggleChatScope(scope) },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .offset(y = 34.dp)
                                    .padding(end = 22.dp)
                                    .zIndex(9f)
                            )
                        }
                    }
                    }
                }
            }
        }
    }
}

private fun openDeveloperGithub() {
    runCatching {
        if (Desktop.isDesktopSupported()) {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(URI(DEVELOPER_GITHUB_URL))
            }
        }
    }
}

@Composable
private fun DesktopBrandFooter(
    onOpenGithub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val footerColor = MaterialTheme.colors.onSurface.copy(alpha = 0.58f)
    val githubIcon = remember {
        val resource = Thread.currentThread().contextClassLoader
            .getResourceAsStream("ic_github_invertocat_white.png")
            ?: error("Missing desktop resource: ic_github_invertocat_white.png")
        val image = resource.use { Image.makeFromEncoded(it.readBytes()) }
        BitmapPainter(image.toComposeImageBitmap())
    }
    Row(
        modifier = modifier
            .height(24.dp)
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onOpenGithub)
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = githubIcon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = footerColor
            )
            Text(
                "By Intelog Natanael",
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.SemiBold,
                color = footerColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DesktopChatFloatingSheet(
    scope: DesktopChatScope,
    lanPeers: List<DesktopLanPeer>,
    selectedDirectPeerIp: String?,
    onSelectDirectPeer: (String) -> Unit,
    globalLanJoined: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    attachmentPath: String,
    onChooseAttachment: () -> Unit,
    onClearAttachment: () -> Unit,
    messages: List<DesktopChatEntry>,
    status: String,
    messagePhase: DesktopTaskPhase,
    discoveryStatus: String,
    discoveryPhase: DesktopTaskPhase,
    onRefreshLan: () -> Unit,
    onSend: (DesktopChatScope) -> Unit,
    onClose: () -> Unit,
    onDrag: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(430.dp)
            .fillMaxHeight(),
        shape = qetaraPanelShape,
        color = qetaraCanvasElevated,
        border = BorderStroke(1.dp, qetaraPanelBorder),
        elevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .pointerInput(Unit) {
                            detectDragGestures { _, dragAmount ->
                                onDrag(dragAmount)
                            }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        if (scope == DesktopChatScope.GLOBAL_LAN) "Canal Wi-Fi" else "Chat directo",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.SemiBold,
                        color = qetaraInk
                    )
                }
                TextButton(onClick = onClose) {
                    Text("Cerrar")
                }
            }
            DesktopChatScopeContent(
                scope = scope,
                lanPeers = lanPeers,
                selectedDirectPeerIp = selectedDirectPeerIp,
                onSelectDirectPeer = onSelectDirectPeer,
                globalLanJoined = globalLanJoined,
                draft = draft,
                onDraftChange = onDraftChange,
                attachmentPath = attachmentPath,
                onChooseAttachment = onChooseAttachment,
                onClearAttachment = onClearAttachment,
                messages = messages,
                status = status,
                messagePhase = messagePhase,
                discoveryStatus = discoveryStatus,
                discoveryPhase = discoveryPhase,
                onRefreshLan = onRefreshLan,
                onSend = onSend
            )
        }
    }
}

@Composable
private fun DesktopChatFloatingRail(
    activeScope: DesktopChatScope?,
    onSelectScope: (DesktopChatScope) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DesktopChatIconButton(
            scope = DesktopChatScope.DIRECT,
            selected = activeScope == DesktopChatScope.DIRECT,
            onClick = { onSelectScope(DesktopChatScope.DIRECT) }
        )
        DesktopChatIconButton(
            scope = DesktopChatScope.GLOBAL_LAN,
            selected = activeScope == DesktopChatScope.GLOBAL_LAN,
            onClick = { onSelectScope(DesktopChatScope.GLOBAL_LAN) }
        )
    }
}

@Composable
private fun DesktopChatScopeContent(
    scope: DesktopChatScope,
    lanPeers: List<DesktopLanPeer>,
    selectedDirectPeerIp: String?,
    onSelectDirectPeer: (String) -> Unit,
    globalLanJoined: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    attachmentPath: String,
    onChooseAttachment: () -> Unit,
    onClearAttachment: () -> Unit,
    messages: List<DesktopChatEntry>,
    status: String,
    messagePhase: DesktopTaskPhase,
    discoveryStatus: String,
    discoveryPhase: DesktopTaskPhase,
    onRefreshLan: () -> Unit,
    onSend: (DesktopChatScope) -> Unit
) {
    val activePeers = lanPeers.filter { it.sessionActive && it.ip.isNotBlank() }.distinctBy { it.ip }
    val selectedPeer = selectedDirectPeerIp
        ?.let { selectedIp -> activePeers.firstOrNull { it.ip == selectedIp } }
        ?: activePeers.firstOrNull()
    val attachmentFile = attachmentPath.trim().takeIf { it.isNotBlank() }?.let(::File)
    val validAttachment = attachmentFile?.takeIf { it.exists() && it.isFile }
    val hasDraft = draft.trim().isNotBlank()
    val scopedMessages = messages.filter { it.scope == scope }.takeLast(5)
    val sendLabel = when (messagePhase) {
        DesktopTaskPhase.STARTING -> "Preparando..."
        DesktopTaskPhase.RUNNING -> "Enviando..."
        else -> "Enviar"
    }
    val discoveryLabel = when (discoveryPhase) {
        DesktopTaskPhase.STARTING,
        DesktopTaskPhase.RUNNING -> "Buscando..."
        else -> "Buscar equipos"
    }
    val hasTarget = when (scope) {
        DesktopChatScope.DIRECT -> selectedPeer != null
        DesktopChatScope.GLOBAL_LAN -> activePeers.isNotEmpty()
    }
    val canSend = hasTarget &&
        (hasDraft || validAttachment != null) &&
        messagePhase != DesktopTaskPhase.STARTING &&
        messagePhase != DesktopTaskPhase.RUNNING

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when (scope) {
                    DesktopChatScope.DIRECT -> selectedPeer?.let { peer ->
                        "Destino: ${peer.label.ifBlank { peer.ip }}"
                    } ?: "Elige un equipo detectado."
                    DesktopChatScope.GLOBAL_LAN -> {
                        val joinedLabel = if (globalLanJoined) "Canal activo" else "Canal inactivo"
                        "$joinedLabel · ${activePeers.size} equipo(s)"
                    }
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.64f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                onClick = onRefreshLan,
                enabled = discoveryPhase != DesktopTaskPhase.STARTING && discoveryPhase != DesktopTaskPhase.RUNNING
            ) {
                Text(discoveryLabel)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 74.dp, max = 136.dp),
            shape = qetaraPanelShape,
            color = qetaraMist,
            elevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (scope == DesktopChatScope.DIRECT) {
                    if (activePeers.isEmpty()) {
                        DesktopChatEmptyTarget(
                            title = "Sin equipos conectados.",
                            detail = discoveryStatus
                        )
                    } else {
                        activePeers.take(6).forEach { peer ->
                            DesktopLanPeerRow(
                                peer = peer,
                                selected = peer.ip == selectedPeer?.ip,
                                onClick = { onSelectDirectPeer(peer.ip) }
                            )
                        }
                    }
                } else {
                    DesktopChatEmptyTarget(
                        title = if (activePeers.isEmpty()) {
                            "Canal Wi-Fi sin destinatarios."
                        } else {
                            "Canal Wi-Fi preparado."
                        },
                        detail = if (activePeers.isEmpty()) {
                            discoveryStatus
                        } else {
                            "Se publicará a ${activePeers.size} equipo(s) Qetara visibles en esta red."
                        }
                    )
                    activePeers.take(4).forEach { peer ->
                        DesktopLanPeerRow(
                            peer = peer,
                            selected = peer.globalLanJoined,
                            onClick = { onSelectDirectPeer(peer.ip) },
                            compact = true
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp),
            shape = qetaraPanelShape,
            color = qetaraMist,
            elevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (scopedMessages.isEmpty()) {
                    Text(
                        "Sin mensajes todavía.",
                        style = MaterialTheme.typography.body2,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.68f)
                    )
                    Text(
                        if (scope == DesktopChatScope.GLOBAL_LAN) {
                            "Publica a los equipos Qetara detectados en la red."
                        } else {
                            "Envía y recibe texto cifrado con un equipo de red."
                        },
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.58f)
                    )
                } else {
                    scopedMessages.forEach { entry ->
                        DesktopChatMessageRow(entry)
                    }
                }
            }
        }

        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = {
                Text(
                    if (scope == DesktopChatScope.GLOBAL_LAN) {
                        "Mensaje para el canal Wi-Fi"
                    } else {
                        "Mensaje de chat"
                    }
                )
            },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onChooseAttachment,
                modifier = Modifier.width(142.dp)
            ) {
                Text(if (validAttachment != null) "Cambiar archivo" else "Adjuntar archivo")
            }
            Text(
                when {
                    validAttachment != null -> validAttachment.name
                    attachmentFile != null -> "Adjunto no válido"
                    else -> "Texto, archivo o ambos"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.caption,
                color = if (attachmentFile != null && validAttachment == null) {
                    MaterialTheme.colors.error
                } else {
                    MaterialTheme.colors.onSurface.copy(alpha = 0.58f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (attachmentFile != null) {
                TextButton(onClick = onClearAttachment) {
                    Text("Quitar")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                status,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.64f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Button(
                onClick = { onSend(scope) },
                enabled = canSend,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = qetaraTeal,
                    contentColor = MaterialTheme.colors.onPrimary
                ),
                modifier = Modifier.width(120.dp)
            ) {
                Text(sendLabel)
            }
        }
    }
}

@Composable
private fun DesktopChatEmptyTarget(
    title: String,
    detail: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            detail,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.58f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DesktopLanPeerRow(
    peer: DesktopLanPeer,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false
) {
    val foreground = if (selected) qetaraTeal else qetaraInk
    val background = if (selected) Color(0xFFE8F7F8) else qetaraCanvasElevated
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 36.dp else 46.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        shape = qetaraPanelShape,
        color = background,
        border = BorderStroke(1.dp, if (selected) Color(0xFFB9E3E6) else Color(0xFFE8EEF2)),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(7.dp),
                shape = RoundedCornerShape(50),
                color = if (peer.sessionActive) qetaraTeal else MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                elevation = 0.dp
            ) {}
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    peer.label.ifBlank { peer.ip },
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!compact) {
                    Text(
                        peer.ip,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.54f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (peer.globalLanJoined) {
                Text(
                    "Canal",
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = qetaraTeal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DesktopChatIconButton(
    scope: DesktopChatScope,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (selected) Color.White else qetaraInk
    Surface(
        modifier = modifier
            .size(44.dp)
            .pointerHoverIcon(PointerIcon.Hand),
        shape = qetaraPanelShape,
        color = if (selected) qetaraTeal else qetaraMist,
        contentColor = accent,
        border = BorderStroke(1.dp, if (selected) qetaraTeal else qetaraPanelBorder),
        elevation = 0.dp
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize()
        ) {
            if (scope == DesktopChatScope.GLOBAL_LAN) {
                DesktopWifiGlyph(
                    modifier = Modifier.size(21.dp),
                    color = accent
                )
            } else {
                DesktopChatGlyph(
                    modifier = Modifier.size(21.dp),
                    color = accent
                )
            }
        }
    }
}

@Composable
private fun DesktopChatGlyph(
    modifier: Modifier = Modifier,
    color: Color = qetaraInk
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.16f, size.height * 0.18f),
            size = Size(size.width * 0.68f, size.height * 0.52f),
            cornerRadius = CornerRadius(size.width * 0.12f, size.width * 0.12f),
            style = stroke
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.36f, size.height * 0.70f),
            end = Offset(size.width * 0.25f, size.height * 0.84f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.36f, size.height * 0.70f),
            end = Offset(size.width * 0.50f, size.height * 0.70f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun DesktopWifiGlyph(
    modifier: Modifier = Modifier,
    color: Color = qetaraInk
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        drawArc(
            color = color,
            startAngle = 218f,
            sweepAngle = 104f,
            useCenter = false,
            topLeft = Offset(size.width * 0.10f, size.height * 0.26f),
            size = Size(size.width * 0.80f, size.height * 0.80f),
            style = stroke
        )
        drawArc(
            color = color,
            startAngle = 220f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(size.width * 0.28f, size.height * 0.44f),
            size = Size(size.width * 0.44f, size.height * 0.44f),
            style = stroke
        )
        drawCircle(
            color = color,
            radius = strokeWidth * 0.72f,
            center = Offset(size.width * 0.50f, size.height * 0.80f)
        )
    }
}

@Composable
private fun DesktopChatMessageRow(entry: DesktopChatEntry) {
    val directionLabel = if (entry.direction == DesktopChatDirection.OUTGOING) "Tu" else entry.peerLabel
    val color = when {
        entry.isError -> MaterialTheme.colors.error
        entry.direction == DesktopChatDirection.OUTGOING -> qetaraTeal
        else -> qetaraInk
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "$directionLabel · ${entry.timestamp}",
            style = MaterialTheme.typography.caption,
            fontWeight = FontWeight.SemiBold,
            color = color.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            entry.message,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.78f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QetaraDesktopHeader(
    localIpLabel: String,
    localIpToCopy: String?,
    protocolLabel: String,
    onCopyLocalIp: (String) -> Unit
) {
    val ipLineModifier = if (localIpToCopy != null) {
        Modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onCopyLocalIp(localIpToCopy) }
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(width = 88.dp, height = 84.dp),
            contentAlignment = Alignment.Center
        ) {
            QetaraLogoMark(
                modifier = Modifier.size(88.dp),
                color = qetaraInk,
                fillRatio = 1.10f,
                centerOffsetX = -0.05f,
                centerOffsetY = 0.08f
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                "Qetara PC",
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold,
                color = qetaraInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Transferencia segura entre PCs y Android",
                style = MaterialTheme.typography.body2,
                color = qetaraInk.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "IP local $localIpLabel",
                modifier = ipLineModifier,
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.SemiBold,
                color = qetaraTeal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                protocolLabel,
                style = MaterialTheme.typography.caption,
                fontFamily = FontFamily.Monospace,
                color = qetaraInk.copy(alpha = 0.56f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DesktopCompactSessionBlock(
    tokenText: String,
    onTokenChange: (String) -> Unit,
    tokenHasError: Boolean,
    pinText: String,
    onPinChange: (String) -> Unit,
    pinHasError: Boolean,
    deviceLabelText: String,
    onDeviceLabelChange: (String) -> Unit,
    credentialsLabel: String,
    allowCredentialsShare: Boolean,
    onAllowCredentialsShareChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = qetaraPanelShape,
        color = qetaraCanvasElevated,
        contentColor = qetaraInk,
        border = BorderStroke(1.dp, qetaraPanelBorder.copy(alpha = 0.84f)),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                "Sesión segura",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.SemiBold,
                color = qetaraInk
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = tokenText,
                    onValueChange = onTokenChange,
                    label = { Text("Token") },
                    singleLine = true,
                    isError = tokenHasError,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = pinText,
                    onValueChange = onPinChange,
                    label = { Text("PIN") },
                    singleLine = true,
                    isError = pinHasError,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = deviceLabelText,
                    onValueChange = onDeviceLabelChange,
                    label = { Text("Nombre del dispositivo") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                DesktopInfoPill("Estado", credentialsLabel, qetaraMist)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = allowCredentialsShare,
                    onCheckedChange = onAllowCredentialsShareChange
                )
                Text(
                    "Compartir token/PIN",
                    style = MaterialTheme.typography.body2,
                    color = qetaraInk.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Composable
private fun DesktopFileDropZone(
    filePath: String,
    isDragActive: Boolean,
    onChooseFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedFile = filePath.trim().takeIf { it.isNotBlank() }?.let(::File)
    val hasFile = selectedFile != null
    val borderColor = when {
        isDragActive -> qetaraTeal
        hasFile -> qetaraTeal.copy(alpha = 0.72f)
        else -> qetaraPanelBorder.copy(alpha = 0.9f)
    }
    val backgroundColor = when {
        isDragActive -> Color(0xFFFFEFE7)
        hasFile -> qetaraMist
        else -> qetaraCanvasElevated
    }
    val headline = when {
        isDragActive -> "Suelta el archivo aquí"
        hasFile -> selectedFile?.name.orEmpty()
        else -> "Arrastra y suelta un archivo aquí"
    }
    val detail = when {
        hasFile -> selectedFile?.absolutePath.orEmpty()
        else -> "O haz click en esta área para elegirlo"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clickable(onClick = onChooseFile)
            .pointerHoverIcon(PointerIcon.Hand)
            .qetaraDashedBorder(borderColor),
        shape = qetaraPanelShape,
        color = backgroundColor,
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FileUploadGlyph(
                modifier = Modifier.size(30.dp),
                color = if (isDragActive) qetaraTeal else qetaraInk
            )
            Text(
                headline,
                modifier = Modifier.padding(top = 5.dp),
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                detail,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun Modifier.qetaraDashedBorder(color: Color): Modifier = drawWithContent {
    drawContent()
    val strokeWidth = 1.4.dp.toPx()
    val dash = 7.dp.toPx()
    val gap = 5.dp.toPx()
    val canvasSize = size
    drawRoundRect(
        color = color,
        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
        size = Size(canvasSize.width - strokeWidth, canvasSize.height - strokeWidth),
        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), 0f)
        )
    )
}

@Composable
private fun GithubMark(
    modifier: Modifier = Modifier,
    color: Color = qetaraInk
) {
    val githubPath = remember { PathParser().parsePathString(githubLogoPathData).toPath() }
    Canvas(modifier = modifier) {
        val scale = size.minDimension / githubLogoViewportSize
        val left = (size.width - githubLogoViewportSize * scale) / 2f
        val top = (size.height - githubLogoViewportSize * scale) / 2f
        withTransform({
            translate(left, top)
            scale(scale, scale)
        }) {
            drawPath(githubPath, color)
        }
    }
}

@Composable
private fun FileUploadGlyph(
    modifier: Modifier = Modifier,
    color: Color = qetaraInk
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val left = width * 0.25f
        val top = height * 0.08f
        val right = width * 0.75f
        val bottom = height * 0.88f
        val fold = width * 0.16f
        val documentPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(left, top)
            lineTo(right - fold, top)
            lineTo(right, top + fold)
            lineTo(right, bottom)
            lineTo(left, bottom)
            close()
        }
        val foldPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(right - fold, top)
            lineTo(right, top + fold)
            lineTo(right - fold, top + fold)
            close()
        }
        drawPath(documentPath, color)
        drawPath(foldPath, qetaraCanvasElevated.copy(alpha = 0.9f))
        val strokeWidth = 3.2.dp.toPx()
        drawLine(
            color = Color.White,
            start = Offset(width * 0.5f, height * 0.35f),
            end = Offset(width * 0.5f, height * 0.66f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(width * 0.5f, height * 0.35f),
            end = Offset(width * 0.39f, height * 0.48f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(width * 0.5f, height * 0.35f),
            end = Offset(width * 0.61f, height * 0.48f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun QetaraLogoMark(
    modifier: Modifier = Modifier,
    color: Color = qetaraInk,
    fillRatio: Float = qetaraHeaderLogoFillRatio,
    centerOffsetX: Float = qetaraHeaderLogoCenterOffsetX,
    centerOffsetY: Float = qetaraHeaderLogoCenterOffsetY
) {
    val logoPath = remember { PathParser().parsePathString(qetaraLogoPathData).toPath() }
    Canvas(modifier = modifier) {
        drawCenteredQetaraLogo(
            logoPath = logoPath,
            color = color,
            fillRatio = fillRatio,
            centerOffsetX = centerOffsetX,
            centerOffsetY = centerOffsetY
        )
    }
}

private class QetaraWindowIconPainter : Painter() {
    private val logoPath = PathParser().parsePathString(qetaraLogoPathData).toPath()

    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawRect(qetaraCanvas)
        drawRect(
            color = qetaraInk,
            style = Stroke(width = size.minDimension * 0.08f)
        )
        drawCenteredQetaraLogo(
            logoPath = logoPath,
            color = qetaraInk,
            fillRatio = qetaraWindowIconFillRatio,
            centerOffsetX = qetaraWindowLogoCenterOffsetX,
            centerOffsetY = qetaraWindowLogoCenterOffsetY
        )
    }
}

private fun DrawScope.drawCenteredQetaraLogo(
    logoPath: androidx.compose.ui.graphics.Path,
    color: Color,
    fillRatio: Float,
    centerOffsetX: Float,
    centerOffsetY: Float
) {
    val scale = size.minDimension * fillRatio / qetaraLogoViewportSize
    val left = (size.width - qetaraLogoViewportSize * scale) / 2f + size.width * centerOffsetX
    val top = (size.height - qetaraLogoViewportSize * scale) / 2f + size.height * centerOffsetY
    withTransform({
        translate(left, top)
        scale(scale, scale)
    }) {
        drawPath(logoPath, color)
    }
}

@Composable
private fun DesktopInfoPill(
    label: String,
    value: String,
    surfaceColor: Color
) {
    Surface(
        modifier = Modifier.widthIn(min = 88.dp),
        shape = qetaraPanelShape,
        color = surfaceColor,
        contentColor = qetaraInk,
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.overline,
                color = qetaraInk.copy(alpha = 0.58f),
                maxLines = 1
            )
            Text(
                value,
                style = MaterialTheme.typography.caption,
                fontWeight = FontWeight.Bold,
                color = qetaraInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DesktopEventsPanel(
    logs: List<DesktopLogEntry>,
    logListState: LazyListState,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    floatingContent: (@Composable () -> Unit)? = null,
    floatingActions: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        shape = qetaraPanelShape,
        color = MaterialTheme.colors.surface,
        border = BorderStroke(1.dp, qetaraPanelBorder),
        elevation = 1.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Eventos",
                            style = MaterialTheme.typography.h6,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${logs.size} registros",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.64f)
                        )
                    }
                    TextButton(
                        onClick = onClear,
                        enabled = logs.isNotEmpty()
                    ) {
                        Text("Limpiar")
                    }
                }
                Divider(color = qetaraPanelBorder)
                if (logs.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clipToBounds(),
                        shape = qetaraPanelShape,
                        color = qetaraSubtleSurface,
                        border = BorderStroke(1.dp, qetaraPanelBorder),
                        elevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            QetaraLogoMark(
                                modifier = Modifier.size(38.dp),
                                color = qetaraInk.copy(alpha = 0.18f)
                            )
                            Text(
                                "Sin eventos aún.",
                                style = MaterialTheme.typography.body2,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f)
                            )
                            Text(
                                "Inicia el receptor o envía un archivo para ver la actividad.",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.56f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clipToBounds(),
                        state = logListState,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs) { entry ->
                            val entrySurface = if (entry.isError) Color(0xFFFFF5F5) else qetaraSubtleSurface
                            val entryBorder = if (entry.isError) Color(0xFFF0B8BE) else qetaraPanelBorder
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = qetaraPanelShape,
                                color = entrySurface,
                                border = BorderStroke(1.dp, entryBorder),
                                elevation = 0.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "${entry.timestamp}  ${entry.level}",
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.caption,
                                        color = if (entry.isError) {
                                            MaterialTheme.colors.error
                                        } else {
                                            MaterialTheme.colors.onSurface.copy(alpha = 0.68f)
                                        }
                                    )
                                    Text(
                                        entry.message,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.body2
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (floatingContent != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 68.dp, bottom = 28.dp)
                ) {
                    floatingContent()
                }
            }
            if (floatingActions != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 28.dp)
                ) {
                    floatingActions()
                }
            }
        }
    }
}

@Composable
private fun DesktopSection(
    title: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    headerActions: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = qetaraPanelShape,
        color = MaterialTheme.colors.surface,
        border = BorderStroke(1.dp, qetaraPanelBorder),
        elevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .width(4.dp)
                        .height(30.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = accentColor,
                    elevation = 0.dp
                ) {}
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (headerActions != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        headerActions()
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun DesktopStatusBar(
    items: List<DesktopStatusItemUi>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = qetaraPanelShape,
        color = qetaraMist.copy(alpha = 0.72f),
        contentColor = qetaraInk,
        border = BorderStroke(1.dp, qetaraPanelBorder.copy(alpha = 0.64f)),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                DesktopStatusInlineItem(
                    item = item,
                    modifier = Modifier.weight(1f)
                )
                if (index < items.lastIndex) {
                    Divider(
                        color = qetaraPanelBorder.copy(alpha = 0.72f),
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopStatusInlineItem(
    item: DesktopStatusItemUi,
    modifier: Modifier = Modifier
) {
    val palette = desktopPhasePalette(item.phase)
    Row(
        modifier = modifier.padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(8.dp),
            shape = RoundedCornerShape(50),
            color = palette.foreground,
            elevation = 0.dp
        ) {}
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.caption,
                fontWeight = FontWeight.Bold,
                color = qetaraInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.detail,
                style = MaterialTheme.typography.caption,
                color = qetaraInk.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class DesktopPhasePalette(
    val background: Color,
    val foreground: Color,
    val border: Color
)

private data class DesktopStatusItemUi(
    val title: String,
    val detail: String,
    val phase: DesktopTaskPhase
)

private fun desktopPhasePalette(phase: DesktopTaskPhase): DesktopPhasePalette = when (phase) {
    DesktopTaskPhase.RUNNING -> DesktopPhasePalette(
        background = Color(0xFFE0F2EF),
        foreground = Color(0xFF0F6B4D),
        border = Color(0xFF8DD5C7)
    )
    DesktopTaskPhase.STARTING,
    DesktopTaskPhase.STOPPING -> DesktopPhasePalette(
        background = Color(0xFFFFF4E5),
        foreground = Color(0xFF8A4B00),
        border = Color(0xFFFFC978)
    )
    DesktopTaskPhase.ERROR -> DesktopPhasePalette(
        background = Color(0xFFFFEBEE),
        foreground = Color(0xFFB3261E),
        border = Color(0xFFF1A2A8)
    )
    DesktopTaskPhase.IDLE -> DesktopPhasePalette(
        background = Color(0xFFEAF1F6),
        foreground = Color(0xFF3E5668),
        border = Color(0xFFC9D8E3)
    )
}

private fun chooseFilePath(current: String): String? {
    val chooser = JFileChooser()
    val base = File(current)
    chooser.currentDirectory = when {
        base.exists() && base.isFile -> base.parentFile
        base.exists() && base.isDirectory -> base
        else -> File(System.getProperty("user.home"))
    }
    chooser.fileSelectionMode = JFileChooser.FILES_ONLY
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
}

private fun chooseDirectoryPath(current: String): String? {
    val chooser = JFileChooser()
    val base = File(current)
    chooser.currentDirectory = when {
        base.exists() && base.isDirectory -> base
        base.exists() && base.isFile -> base.parentFile
        else -> File(System.getProperty("user.home"))
    }
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.isAcceptAllFileFilterUsed = false
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
}

private fun runInteractiveShell(
    initialToken: String,
    initialPin: String,
    initialHost: String?,
    defaultPort: Int,
    retries: Int,
    clientId: String,
    clientLabel: String,
    localNoiseIdentity: NoiseStaticIdentity
) {
    var currentToken = initialToken
    var currentPin = initialPin
    var currentHost = initialHost

    println("Modo interactivo activo. Comandos: help, status, host <ip>, token <v>, pin <v>, send <ip> <archivo>, send <archivo>, exit")
    while (true) {
        print("wifidrop> ")
        val line = readLine() ?: break
        val parts = tokenizeCommandLine(line)
        if (parts.isEmpty()) continue

        when (parts.first().lowercase()) {
            "help" -> {
                println("help                         Muestra comandos")
                println("status                       Muestra configuración activa")
                println("host <ip>                    Fija host por defecto para send")
                println("token <TOKEN>                Actualiza token para envíos")
                println("pin <PIN6>                   Actualiza PIN para envíos")
                println("send <ip> <archivo>          Envía archivo al host indicado")
                println("send <archivo>               Envía al host por defecto")
                println("exit                         Cierra la app")
            }

            "status" -> {
                println("host=${currentHost ?: "(sin host)"} token=$currentToken pin=$currentPin port=$defaultPort retries=$retries")
            }

            "host" -> {
                if (parts.size < 2) {
                    println("Uso: host <ip>")
                } else {
                    currentHost = parts[1]
                    println("Host por defecto: $currentHost")
                }
            }

            "token" -> {
                if (parts.size < 2) {
                    println("Uso: token <TOKEN>")
                } else {
                    runCatching { requireValidToken(parts[1]) }
                        .onSuccess {
                            currentToken = it
                            println("Token actualizado.")
                        }
                        .onFailure { println("Token inválido: ${it.message}") }
                }
            }

            "pin" -> {
                if (parts.size < 2) {
                    println("Uso: pin <PIN6>")
                } else {
                    runCatching { requireValidPin(parts[1]) }
                        .onSuccess {
                            currentPin = it
                            println("PIN actualizado.")
                        }
                        .onFailure { println("PIN inválido: ${it.message}") }
                }
            }

            "send" -> {
                val resolved = resolveSendCommand(parts, currentHost)
                if (resolved == null) {
                    println("Uso: send <ip> <archivo>  o  send <archivo> (si ya definiste host)")
                    continue
                }

                val senderConfig = SenderConfig(
                    token = currentToken,
                    pin = currentPin,
                    targetHost = resolved.first,
                    port = defaultPort,
                    clientId = clientId,
                    clientLabel = clientLabel,
                    retries = retries,
                    localNoiseIdentity = localNoiseIdentity
                )

                runCatching {
                    sendFileToPeer(File(resolved.second), senderConfig)
                }.onSuccess {
                    println(it)
                    currentHost = resolved.first
                }.onFailure {
                    println("Falló envío: ${it.message ?: it::class.java.simpleName}")
                }
            }

            "exit", "quit" -> return
            else -> println("Comando no reconocido. Usa 'help'.")
        }
    }
}

private fun resolveSendCommand(parts: List<String>, defaultHost: String?): Pair<String, String>? {
    return when {
        parts.size >= 3 -> {
            val host = parts[1]
            val path = parts.subList(2, parts.size).joinToString(" ")
            host to path
        }

        parts.size == 2 && !defaultHost.isNullOrBlank() -> {
            defaultHost to parts[1]
        }

        else -> null
    }
}

private fun tokenizeCommandLine(line: String): List<String> {
    val tokenRegex = Regex("\"([^\"]*)\"|'([^']*)'|(\\S+)")
    return tokenRegex.findAll(line)
        .map { match ->
            match.groups[1]?.value
                ?: match.groups[2]?.value
                ?: match.groups[3]?.value
                ?: ""
        }
        .filter { it.isNotBlank() }
        .toList()
}

private fun runLocalE2eSelfTest(
    token: String,
    pin: String,
    localPeerId: String,
    localNoiseIdentity: NoiseStaticIdentity,
    baseOutputDir: File,
    defaultPort: Int
) {
    println("[SelfTest] Iniciando pruebas E2E locales...")
    val testRoot = File(baseOutputDir, "selftest_${System.currentTimeMillis()}").apply { mkdirs() }
    val receiveDir = File(testRoot, "receive").apply { mkdirs() }
    val payload = File(testRoot, "payload.bin")
    generateRandomFile(payload, 2 * 1024 * 1024 + 257)

    val port = findFreePort(preferred = defaultPort + 1)
    val receiverConfig = ReceiverConfig(
        token = token,
        pin = pin,
        outputDir = receiveDir,
        port = port,
        deviceLabel = "SelfTestPC",
        sessionDurationMs = 30 * 60_000L,
        allowCredentialsShare = false,
        localPeerId = localPeerId
    )
    val receiver = PcReceiverServer(receiverConfig, localNoiseIdentity)
    val receiverThread = Thread {
        receiver.runBlocking()
    }.apply {
        isDaemon = true
        name = "wifidrop-selftest-receiver"
        start()
    }

    Thread.sleep(400)

    val senderConfig = SenderConfig(
        token = token,
        pin = pin,
        targetHost = "127.0.0.1",
        port = port,
        clientId = localPeerId,
        clientLabel = "SelfTestClient",
        retries = 3,
        localNoiseIdentity = localNoiseIdentity
    )

    try {
        println("[SelfTest] Caso 1: envío completo + validación hash")
        sendFileToPeer(payload, senderConfig)
        val expectedHash = sha256OfFile(payload)
        val received1 = latestReceivedFile(receiveDir)
            ?: error("Caso 1 fallido: no se encontró archivo recibido")
        check(sha256OfFile(received1) == expectedHash) {
            "Caso 1 fallido: hash no coincide"
        }
        println("[SelfTest] Caso 1 OK")

        println("[SelfTest] Caso 2: resume desde parcial + hash")
        received1.delete()
        val partialDir = File(receiveDir, ".partial").apply { mkdirs() }
        val partial = partialFileFor(
            partialDir = partialDir,
            incomingName = sanitizeFileName(payload.name),
            totalBytes = payload.length(),
            expectedHash = expectedHash
        )
        writeFirstBytes(source = payload, target = partial, bytes = (payload.length() * 35L) / 100L)

        sendFileToPeer(payload, senderConfig)
        val received2 = latestReceivedFile(receiveDir)
            ?: error("Caso 2 fallido: no se encontró archivo recibido")
        check(sha256OfFile(received2) == expectedHash) {
            "Caso 2 fallido: hash no coincide tras resume"
        }
        println("[SelfTest] Caso 2 OK")
        println("[SelfTest] Completado en: ${testRoot.absolutePath}")
    } finally {
        receiver.stop()
        receiverThread.join(2_000)
    }
}

private fun writeFirstBytes(source: File, target: File, bytes: Long) {
    val targetBytes = bytes.coerceAtLeast(1L)
    FileInputStream(source).use { input ->
        FileOutputStream(target).use { output ->
            val buffer = ByteArray(64 * 1024)
            var remaining = targetBytes
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = input.read(buffer, 0, toRead)
                if (read <= 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
            output.flush()
            output.fd.sync()
        }
    }
}

private fun generateRandomFile(file: File, sizeBytes: Int) {
    FileOutputStream(file).use { output ->
        val buffer = ByteArray(64 * 1024)
        var written = 0
        while (written < sizeBytes) {
            Random.nextBytes(buffer)
            val chunk = minOf(buffer.size, sizeBytes - written)
            output.write(buffer, 0, chunk)
            written += chunk
        }
        output.flush()
        output.fd.sync()
    }
}

private fun latestReceivedFile(dir: File): File? {
    return dir.listFiles()
        ?.filter { it.isFile }
        ?.maxByOrNull { it.lastModified() }
}

private fun findFreePort(preferred: Int? = null): Int {
    if (preferred != null) {
        runCatching {
            ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(preferred))
                return preferred
            }
        }
    }
    ServerSocket(0).use { socket ->
        return socket.localPort
    }
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

private fun configureSocket(socket: Socket) {
    socket.tcpNoDelay = true
    socket.keepAlive = true
}

private fun readEnvelope(input: DataInputStream): Envelope {
    val magic = input.readInt()
    require(magic == PROTOCOL_MAGIC) { "protocolo no valido" }
    val version = input.readInt()
    require(version == PROTOCOL_VERSION) { "version no compatible: $version" }

    val packetType = input.readInt()
    val clientId = sanitizeClientId(input.readUTF())
    val clientLabel = sanitizePeerLabel(input.readUTF())
    val clientNonce = input.readUTF().take(64)

    return Envelope(
        packetType = packetType,
        clientId = clientId,
        clientLabel = clientLabel,
        clientNonce = clientNonce
    )
}

private data class Challenge(
    val serverNonce: String,
    val expiresAtMs: Long
)

private fun <T> withRetry(
    attempts: Int = 3,
    baseDelayMs: Long = 350,
    block: () -> T
): T {
    var last: Exception? = null
    repeat(attempts) { index ->
        try {
            return block()
        } catch (e: Exception) {
            last = e
            val isLast = index == attempts - 1
            if (isLast || !isRetryable(e)) throw e
            Thread.sleep(baseDelayMs * (index + 1L))
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

private fun readChallengeOrFailure(input: DataInputStream): Challenge {
    val magic = input.readInt()
    require(magic == PROTOCOL_MAGIC) { "respuesta inválida (magic)" }
    val version = input.readInt()
    require(version == PROTOCOL_VERSION) { "respuesta inválida (version)" }

    return when (val packetType = input.readInt()) {
        PACKET_CHALLENGE -> {
            val nonce = input.readUTF()
            val expiresAt = input.readLong()
            if (System.currentTimeMillis() >= expiresAt) {
                throw IllegalStateException("sesión expirada en host")
            }
            Challenge(serverNonce = nonce, expiresAtMs = expiresAt)
        }

        PACKET_RESULT -> {
            val ok = input.readBoolean()
            val message = input.readUTF()
            if (!ok) throw IllegalStateException(message)
            throw IllegalStateException("respuesta inesperada")
        }

        else -> throw IllegalStateException("respuesta inválida (packet=$packetType)")
    }
}

private fun readSecureResult(channel: SecureChannel): Pair<Boolean, String> {
    val frame = channel.readFrameInput()
    val frameType = frame.readInt()
    require(frameType == SECURE_FRAME_RESULT) { "frame RESULT inválido" }
    val ok = frame.readBoolean()
    val message = frame.readUTF().take(200)
    return ok to message
}

private fun readResumeOffset(channel: SecureChannel, totalBytes: Long): Long {
    val frame = channel.readFrameInput()
    val frameType = frame.readInt()
    require(frameType == SECURE_FRAME_FILE_RESUME) { "frame FILE_RESUME inválido" }
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

private fun writeSecureResult(
    channel: SecureChannel,
    ok: Boolean,
    messageRaw: String
) {
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

private fun establishSecureChannel(
    input: DataInputStream,
    output: DataOutputStream,
    initiator: Boolean,
    token: String,
    pin: String,
    purpose: String,
    localIdentity: NoiseStaticIdentity
): SecureChannel {
    val role = if (initiator) HandshakeState.INITIATOR else HandshakeState.RESPONDER
    val handshake = HandshakeState(NOISE_PROTOCOL_WITH_PSK, role)
    var success = false

    try {
        handshake.localKeyPair?.let { local ->
            local.setPrivateKey(localIdentity.privateKey, 0)
        }

        val prologue = "$NOISE_PROLOGUE_PREFIX|$purpose".toByteArray(Charsets.UTF_8)
        handshake.setPrologue(prologue, 0, prologue.size)
        val psk = deriveNoisePsk(token, pin)
        handshake.setPreSharedKey(psk, 0, psk.size)

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
                        "frame handshake inválido: $len"
                    }
                    val inMsg = ByteArray(len)
                    input.readFully(inMsg)
                    handshake.readMessage(inMsg, 0, len, emptyPayload, 0)
                }

                else -> throw SecurityException("estado Noise inválido: ${handshake.action}")
            }
        }

        val pair = handshake.split()
        success = true
        return SecureChannel(
            input = input,
            output = output,
            sender = pair.sender,
            receiver = pair.receiver
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

private fun writeResumeOffset(channel: SecureChannel, resumeOffset: Long) {
    val safeOffset = resumeOffset.coerceAtLeast(0L)
    channel.writeFrame { frame ->
        frame.writeInt(SECURE_FRAME_FILE_RESUME)
        frame.writeLong(safeOffset)
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

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format("%.1f %s", value, units[unitIndex])
}
