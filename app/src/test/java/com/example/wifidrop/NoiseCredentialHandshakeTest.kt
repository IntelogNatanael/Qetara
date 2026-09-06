package com.example.wifidrop

import com.example.wifidrop.protocol.NOISE_HANDSHAKE_MAX_FRAME
import com.example.wifidrop.protocol.NOISE_PROTOCOL_NO_PSK
import com.example.wifidrop.protocol.NOISE_PROLOGUE_PREFIX
import kr.jclab.noise.protocol.CipherStatePair
import kr.jclab.noise.protocol.HandshakeState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.BadPaddingException

/** Exercises the actual Noise library configuration used by secure credential requests. */
class NoiseCredentialHandshakeTest {
    @Test fun credentialsAreEncryptedAndHandshakeIdentitiesAreProven() {
        val (client, host) = handshake()
        try {
            val plaintext = "session-token=ABCDEFGH;pin=123456".toByteArray()
            val cipher = ByteArray(plaintext.size + client.sender.macLength)
            val size = client.sender.encryptWithAd(null, plaintext, 0, cipher, 0, plaintext.size)
            assertFalse(String(cipher).contains("ABCDEFGH"))
            assertFalse(String(cipher).contains("123456"))
            val decrypted = ByteArray(size)
            val plainSize = host.receiver.decryptWithAd(null, cipher, 0, decrypted, 0, size)
            assertArrayEquals(plaintext, decrypted.copyOf(plainSize))
        } finally { destroy(client); destroy(host) }
    }

    @Test fun alteredCiphertextIsRejectedBeforeCredentialsCanBeRead() {
        val (client, host) = handshake()
        try {
            val plaintext = "secret credentials".toByteArray()
            val cipher = ByteArray(plaintext.size + client.sender.macLength)
            val size = client.sender.encryptWithAd(null, plaintext, 0, cipher, 0, plaintext.size)
            cipher[0] = (cipher[0].toInt() xor 1).toByte()
            assertThrows(BadPaddingException::class.java) {
                host.receiver.decryptWithAd(null, cipher, 0, ByteArray(size), 0, size)
            }
        } finally { destroy(client); destroy(host) }
    }

    private fun handshake(): Pair<CipherStatePair, CipherStatePair> {
        val initiator = HandshakeState(NOISE_PROTOCOL_NO_PSK, HandshakeState.INITIATOR)
        val responder = HandshakeState(NOISE_PROTOCOL_NO_PSK, HandshakeState.RESPONDER)
        try {
            val prologue = "$NOISE_PROLOGUE_PREFIX|CREDENTIALS".toByteArray()
            for (state in listOf(initiator, responder)) {
                requireNotNull(state.localKeyPair).generateKeyPair()
                state.setPrologue(prologue, 0, prologue.size)
                state.start()
            }
            val expectedHost = ByteArray(requireNotNull(responder.localKeyPair).publicKeyLength)
            requireNotNull(responder.localKeyPair).getPublicKey(expectedHost, 0)
            while (initiator.action != HandshakeState.SPLIT || responder.action != HandshakeState.SPLIT) {
                val writer = if (initiator.action == HandshakeState.WRITE_MESSAGE) initiator else responder
                val reader = if (writer === initiator) responder else initiator
                val message = ByteArray(NOISE_HANDSHAKE_MAX_FRAME)
                val size = writer.writeMessage(message, 0, null, 0, 0)
                reader.readMessage(message, 0, size, ByteArray(0), 0)
            }
            val observedHost = ByteArray(requireNotNull(initiator.remotePublicKey).publicKeyLength)
            requireNotNull(initiator.remotePublicKey).getPublicKey(observedHost, 0)
            assertArrayEquals(expectedHost, observedHost)
            return initiator.split() to responder.split()
        } finally {
            // The split CipherStates must remain usable after the handshake secrets are destroyed.
            initiator.destroy()
            responder.destroy()
        }
    }

    private fun destroy(pair: CipherStatePair) {
        pair.sender.destroy()
        pair.receiver.destroy()
    }
}
