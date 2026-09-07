package com.example.wifidrop.protocol.flash

import com.example.wifidrop.protocol.NOISE_PROTOCOL_NO_PSK
import com.example.wifidrop.protocol.toHexLower
import kr.jclab.noise.protocol.CipherState
import kr.jclab.noise.protocol.HandshakeState
import java.io.*

internal const val FLASH_MAGIC = 0x51464c53 // QFLS; deliberately separate from WDRP v4.
internal const val FLASH_VERSION = 1
internal const val FLASH_PROBE = 1
internal const val FLASH_TRANSFER = 2
internal const val FLASH_DISCOVER = 3
internal const val FLASH_ANNOUNCE = 4
internal const val FLASH_HELLO = 10
internal const val FLASH_OFFER = 11
internal const val FLASH_DECISION = 12
internal const val FLASH_CHUNK = 13
internal const val FLASH_DONE = 14
internal const val FLASH_ACK = 15
internal const val FLASH_CHUNK_BYTES = 48 * 1024
internal const val FLASH_FRAME_BYTES = 50 * 1024

internal fun requireFlashAddress(raw: String): String {
    val parts = raw.split('.')
    require(parts.size == 4 && parts.all { it.isNotEmpty() && it.length <= 3 && it.all { c -> c in '0'..'9' } }) { "invalid_address" }
    val octets = parts.map { it.toInt() }
    require(octets.all { it in 0..255 }) { "invalid_address" }
    require(octets[0] == 10 || octets[0] == 127 ||
        (octets[0] == 172 && octets[1] in 16..31) ||
        (octets[0] == 192 && octets[1] == 168) ||
        (octets[0] == 169 && octets[1] == 254)) { "invalid_address" }
    return octets.joinToString(".")
}

internal fun DataOutputStream.flashHeader(kind: Int) { writeInt(FLASH_MAGIC); writeInt(FLASH_VERSION); writeInt(kind) }
internal fun DataInputStream.flashKind(): Int {
    require(readInt() == FLASH_MAGIC && readInt() == FLASH_VERSION) { "incompatible" }
    return readInt()
}

internal fun DataInputStream.requireEnd() { require(available() == 0) { "invalid_frame" } }

/** Only Noise performs encryption and authentication. The displayed code is its final transcript hash. */
internal class FlashChannel(
    private val input: DataInputStream, private val output: DataOutputStream,
    private val sender: CipherState, private val receiver: CipherState, val verificationCode: String
) : Closeable {
    fun write(body: (DataOutputStream) -> Unit) {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use(body)
        val plain = bytes.toByteArray()
        require(plain.size <= FLASH_FRAME_BYTES) { "invalid_frame" }
        val cipher = ByteArray(plain.size + sender.macLength)
        val size = sender.encryptWithAd(null, plain, 0, cipher, 0, plain.size)
        output.writeInt(size); output.write(cipher, 0, size); output.flush()
    }
    fun read(): DataInputStream {
        val size = input.readInt()
        require(size in receiver.macLength..(FLASH_FRAME_BYTES + receiver.macLength)) { "invalid_frame" }
        val cipher = ByteArray(size); input.readFully(cipher)
        val plain = ByteArray(size)
        val length = receiver.decryptWithAd(null, cipher, 0, plain, 0, size)
        return DataInputStream(ByteArrayInputStream(plain, 0, length))
    }
    override fun close() { sender.destroy(); receiver.destroy() }
}

internal fun flashHandshake(input: DataInputStream, output: DataOutputStream, initiator: Boolean, privateKey: ByteArray): FlashChannel {
    val handshake = HandshakeState(NOISE_PROTOCOL_NO_PSK, if (initiator) HandshakeState.INITIATOR else HandshakeState.RESPONDER)
    try {
        handshake.localKeyPair.setPrivateKey(privateKey, 0)
        val prologue = "Qetara/Flash/v1/BOTH_CONFIRM_EVERY_FILE".toByteArray(Charsets.US_ASCII)
        handshake.setPrologue(prologue, 0, prologue.size)
        handshake.start()
        val buffer = ByteArray(4096)
        while (handshake.action != HandshakeState.SPLIT) {
            when (handshake.action) {
                HandshakeState.WRITE_MESSAGE -> {
                    val size = handshake.writeMessage(buffer, 0, null, 0, 0)
                    output.writeInt(size); output.write(buffer, 0, size); output.flush()
                }
                HandshakeState.READ_MESSAGE -> {
                    val size = input.readInt(); require(size in 1..4096) { "invalid_frame" }
                    input.readFully(buffer, 0, size)
                    val payload = ByteArray(4096)
                    require(handshake.readMessage(buffer, 0, size, payload, 0) == 0) { "invalid_frame" }
                }
                else -> error("invalid_handshake")
            }
        }
        val code = handshake.handshakeHash.copyOfRange(0, 8).toHexLower().uppercase(java.util.Locale.ROOT).chunked(4).joinToString(" ")
        val pair = handshake.split()
        return FlashChannel(input, output, pair.sender, pair.receiver, code)
    } finally { handshake.destroy() }
}
