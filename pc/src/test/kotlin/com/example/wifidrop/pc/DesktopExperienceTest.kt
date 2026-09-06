package com.example.wifidrop.pc

import com.example.wifidrop.protocol.DEFAULT_PORT
import java.io.File
import java.nio.file.Files
import java.util.Properties
import kotlin.test.*

class DesktopExperienceTest {
    @Test
    fun preferencesRoundTripOnlyNonSecretSettings() {
        val root = Files.createTempDirectory("qetara-preferences-test-").toFile()
        try {
            val file = File(root, "preferences.properties")
            val expected = DesktopPreferences("Mi portátil", "C:/Archivos/Recibidos", 9123, 5, 60)
            expected.save(file)
            assertEquals(expected, DesktopPreferences.load(file))
            val saved = Properties().apply { file.inputStream().use(::load) }
            assertEquals(setOf("deviceLabel", "outputDirectory", "port", "retries", "sessionMinutes"), saved.stringPropertyNames())
        } finally { root.deleteRecursively() }
    }

    @Test
    fun invalidSavedSettingsRecoverToUsableDefaults() {
        val root = Files.createTempDirectory("qetara-preferences-test-").toFile()
        try {
            val file = File(root, "preferences.properties").apply {
                writeText("port=-1\nretries=100\nsessionMinutes=9223372036854775807\n")
            }
            val loaded = DesktopPreferences.load(file)
            assertEquals(DEFAULT_PORT, loaded.port)
            assertEquals(3, loaded.retries)
            assertEquals(120L, loaded.sessionMinutes)
        } finally { root.deleteRecursively() }
    }

    @Test
    fun directConversationDoesNotShowOtherPeersMessages() {
        val messages = listOf(
            chat("192.168.1.4", "For A"),
            chat("192.168.1.5", "For B"),
            chat("192.168.1.4", "Channel", DesktopChatScope.GLOBAL_LAN)
        )
        assertEquals(listOf("For A"), desktopMessagesForPeer(messages, DesktopChatScope.DIRECT, "192.168.1.4").map { it.message })
        assertTrue(desktopMessagesForPeer(messages, DesktopChatScope.DIRECT, null).isEmpty())
        assertEquals(listOf("Channel"), desktopMessagesForPeer(messages, DesktopChatScope.GLOBAL_LAN, null).map { it.message })
    }

    @Test
    fun actionableErrorsExplainHowToRecover() {
        assertTrue(actionableDesktopError(java.net.ConnectException()).contains("Activa Recibir"))
        assertTrue(actionableDesktopError(IllegalStateException("auth_invalida")).contains("código o PIN"))
        assertTrue(actionableDesktopError(java.net.BindException()).contains("Ajustes"))
    }

    @Test
    fun openSourceNoticesAreIncludedInDesktopResources() {
        val notices = desktopLicenseNotices()
        assertTrue(notices.contains("Qetara"))
        assertTrue(notices.contains("Apache License"))
        assertTrue(notices.contains("Southern Storm Software"))
        assertTrue(notices.length > 20_000)
    }

    @Test
    fun structuredAndroidUserMessagesShowTextWithoutControlJson() {
        val decoded = decodeDesktopChatPayload("\u2063QCT\u2063{\"kind\":\"user\",\"scope\":\"DIRECT\",\"text\":\"Hola desde Android\"}")
        assertEquals(DesktopChatScope.DIRECT, decoded.first)
        assertEquals("Hola desde Android", decoded.second)
    }

    @Test
    fun unsupportedRelayRequestsFailExplicitly() {
        assertFailsWith<IllegalArgumentException> {
            decodeDesktopChatPayload("\u2063QCT\u2063{\"kind\":\"roster\",\"peers\":[]}")
        }
    }

    private fun chat(address: String, message: String, scope: DesktopChatScope = DesktopChatScope.DIRECT) =
        DesktopChatEntry("12:00", scope, DesktopChatDirection.INCOMING, address, address, message)
}
