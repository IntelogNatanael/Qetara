package com.example.wifidrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class DiscoveryResponsePrivacyTest {
    @Test fun publicDiscoveryKeepsWdrp4LayoutWithoutClaimingTrust() {
        for (sessionActive in listOf(false, true)) {
            for (channelJoined in listOf(false, true)) {
                val bytes = ByteArrayOutputStream()
                FileTransfer.writeDiscoveryResponsePacket(
                    DataOutputStream(bytes),
                    peerId = "test-host",
                    peerLabel = "Test host",
                    sessionActive = sessionActive,
                    globalLanJoined = channelJoined
                )
                // Read the existing wire format directly, independently of the app decoder.
                DataInputStream(ByteArrayInputStream(bytes.toByteArray())).use { input ->
                    assertEquals(0x57445250, input.readInt())
                    assertEquals(4, input.readInt())
                    assertEquals(com.example.wifidrop.protocol.PACKET_DISCOVERY_RESPONSE, input.readInt())
                    assertEquals("test-host", input.readUTF())
                    assertEquals("Test host", input.readUTF())
                    assertEquals(sessionActive, input.readBoolean())
                    // An unauthenticated request must never learn a host's trust relationship.
                    assertFalse(input.readBoolean())
                    assertEquals(channelJoined, input.readBoolean())
                    assertEquals(-1, input.read())
                }
            }
        }
    }
}
