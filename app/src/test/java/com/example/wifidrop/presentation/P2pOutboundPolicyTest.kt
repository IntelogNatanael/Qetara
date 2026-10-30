package com.example.wifidrop.presentation

import com.example.wifidrop.ConnectionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pOutboundPolicyTest : com.example.wifidrop.LocalizedResourcesTest() {

    @Test
    fun directMessageRequiresText() {
        val decision = buildDirectMessageSendPlan(
            context = connectedContext(),
            draft = " \t\r\n  ",
            targetIp = "192.168.1.20"
        )

        assertBlocked(decision, "Escribe un mensaje antes de enviar.")
    }

    @Test
    fun fileBatchRequiresFiles() {
        val decision = buildFileBatchSendPlan(
            context = connectedContext(),
            targetIp = "192.168.1.20",
            fileCount = 0
        )

        assertBlocked(decision, "Selecciona al menos un archivo.")
    }

    @Test
    fun outboundBlocksExpiredSessionWithActionableMessage() {
        val decision = buildDirectMessageSendPlan(
            context = connectedContext(sessionExpired = true),
            draft = "hola",
            targetIp = "192.168.1.20"
        )

        assertBlocked(decision, "La sesión expiró. Renuévala antes de enviar mensajes.")
    }

    @Test
    fun directComposerDeduplicatesTargets() {
        val decision = buildDirectChatComposerPlan(
            context = connectedContext(),
            draft = " hola   mundo ",
            selectedFilesCount = 0,
            targetIps = listOf("192.168.1.20", " 192.168.1.20 ", "192.168.1.21")
        )

        assertTrue(decision is P2pOutboundDecision.Ready)
        val plan = (decision as P2pOutboundDecision.Ready).plan
        assertEquals(listOf("192.168.1.20", "192.168.1.21"), plan.targetIps)
        assertEquals("hola   mundo", plan.message)
    }

    @Test
    fun outgoingChatPathsPreserveUnicodeParagraphsAndInteriorSpacing() {
        val expected = "Hola, Perú 👋\r\n\r\nLínea  dos:\tñ, 漢字.\nÚltima línea 🚀"
        val draft = " \t$expected  \n"
        val direct = buildDirectMessageSendPlan(connectedContext(), draft, "192.168.1.20")
        val composer = buildDirectChatComposerPlan(connectedContext(), draft, 0, listOf("192.168.1.20"))
        val channel = buildGlobalMessageSendPlan(connectedContext(), draft, 0, true, 1)

        assertTrue(direct is P2pOutboundDecision.Ready)
        assertTrue(composer is P2pOutboundDecision.Ready)
        assertTrue(channel is P2pOutboundDecision.Ready)
        assertEquals(expected, (direct as P2pOutboundDecision.Ready).plan.message)
        assertEquals(expected, (composer as P2pOutboundDecision.Ready).plan.message)
        assertEquals(expected, (channel as P2pOutboundDecision.Ready).plan.message)
    }

    @Test
    fun preservingParagraphsStillCapsOutgoingTextAtTwoThousandCharacters() {
        val expected = "é".repeat(1_990) + "\n\nñ  漢字🚀Z"
        assertEquals(2_000, expected.length)
        val decision = buildDirectMessageSendPlan(
            connectedContext(), "\t$expected overflow\n ", "192.168.1.20"
        )

        assertTrue(decision is P2pOutboundDecision.Ready)
        assertEquals(expected, (decision as P2pOutboundDecision.Ready).plan.message)
    }

    @Test
    fun whitespaceOnlyComposersWithoutAttachmentsRemainBlocked() {
        val draft = " \t\r\n  "
        val message = "Escribe un mensaje o adjunta al menos un archivo."
        assertBlocked(
            buildDirectChatComposerPlan(connectedContext(), draft, 0, listOf("192.168.1.20")), message
        )
        assertBlocked(buildGlobalMessageSendPlan(connectedContext(), draft, 0, true, 1), message)
    }

    @Test
    fun globalLanAcceptsAttachmentsWhenPeersAreAvailable() {
        val decision = buildGlobalMessageSendPlan(
            context = connectedContext(lanConnected = true),
            draft = "hola",
            selectedFilesCount = 1,
            globalLanJoined = true,
            targetCount = 2
        )

        assertTrue(decision is P2pOutboundDecision.Ready)
        val plan = (decision as P2pOutboundDecision.Ready).plan
        assertEquals("hola", plan.message)
        assertEquals(true, plan.includeFiles)
        assertEquals("Mensaje publicado y 1 archivo en cola para el canal Wi‑Fi.", plan.feedbackMessage)
    }

    @Test
    fun globalLanAttachmentsRequirePeers() {
        val decision = buildGlobalMessageSendPlan(
            context = connectedContext(lanConnected = true),
            draft = "",
            selectedFilesCount = 1,
            globalLanJoined = true,
            targetCount = 0
        )

        assertBlocked(decision, "No hay otros equipos en el canal para recibir archivos.")
    }

    private fun connectedContext(
        lanConnected: Boolean = true,
        sessionExpired: Boolean = false
    ) = P2pOutboundContext(
        permissionGranted = true,
        lanConnected = lanConnected,
        sessionToken = "ABCD1234",
        sessionPin = "123456",
        sessionExpired = sessionExpired,
        connection = ConnectionSnapshot(
            groupFormed = true,
            isGroupOwner = false,
            groupOwnerAddress = "192.168.49.1"
        )
    )

    private fun <T> assertBlocked(
        decision: P2pOutboundDecision<T>,
        message: String
    ) {
        assertTrue(decision is P2pOutboundDecision.Blocked)
        assertEquals(message, (decision as P2pOutboundDecision.Blocked).message)
    }
}
