package com.example.wifidrop.pc

import kotlin.test.*

class DesktopConversationsTest {
    @Test
    fun draftsAndAttachmentsRemainWithTheirRecipient() {
        val drafts = DesktopConversationDrafts()
        val alice = desktopConversationKey(DesktopChatScope.DIRECT, "192.168.1.4")
        val bob = desktopConversationKey(DesktopChatScope.DIRECT, "192.168.1.5")
        val channel = desktopConversationKey(DesktopChatScope.GLOBAL_LAN)
        drafts.save(alice, DesktopChatDraft("Solo para Alice", "C:/privado.pdf"))
        drafts.save(bob, DesktopChatDraft("Para Bob"))
        drafts.save(channel, DesktopChatDraft("Para el canal", "C:/publico.pdf"))
        assertEquals(DesktopChatDraft("Solo para Alice", "C:/privado.pdf"), drafts.restore(alice))
        assertEquals("", drafts.restore(bob).attachmentPath)
        assertEquals("C:/publico.pdf", drafts.restore(channel).attachmentPath)
    }

    @Test
    fun acknowledgingOneConversationDoesNotClearAnother() {
        val drafts = DesktopConversationDrafts()
        drafts.save("alice", DesktopChatDraft("Enviado", "C:/first.pdf"))
        drafts.save("bob", DesktopChatDraft("Todavía escribiendo", "C:/second.pdf"))
        drafts.acknowledge("alice", "Enviado", "C:/first.pdf")
        assertEquals(DesktopChatDraft(), drafts.restore("alice"))
        assertEquals(DesktopChatDraft("Todavía escribiendo", "C:/second.pdf"), drafts.restore("bob"))
    }

    @Test
    fun textEditedDuringSendSurvivesItsAcknowledgement() {
        val drafts = DesktopConversationDrafts()
        drafts.save("alice", DesktopChatDraft("Un mensaje nuevo", "C:/nuevo.pdf"))
        drafts.acknowledge("alice", "Un mensaje anterior", "C:/anterior.pdf")
        assertEquals(DesktopChatDraft("Un mensaje nuevo", "C:/nuevo.pdf"), drafts.restore("alice"))
    }

    @Test
    fun explicitOfflineRecipientNeverFallsBackToSomeoneElse() {
        val bob = peer("192.168.1.5", true)
        assertNull(desktopDirectTarget(listOf(bob), "192.168.1.4"))
        assertEquals(bob, desktopDirectTarget(listOf(bob), null))
    }

    @Test
    fun offlineConversationRemainsReadableWithoutBecomingASendTarget() {
        val message = DesktopChatEntry("12:00", DesktopChatScope.DIRECT, DesktopChatDirection.INCOMING, "Alice", "192.168.1.4", "Nos vemos")
        val conversations = desktopConversationPeers(listOf(peer("192.168.1.5", true)), listOf(message))
        val alice = conversations.single { it.ip == "192.168.1.4" }
        assertEquals("Alice", alice.label)
        assertFalse(alice.sessionActive)
        assertNull(desktopDirectTarget(conversations, alice.ip))
    }

    @Test
    fun draftMemoryIsBounded() {
        val drafts = DesktopConversationDrafts(capacity = 2)
        drafts.save("old", DesktopChatDraft("old"))
        drafts.save("recent", DesktopChatDraft("recent"))
        drafts.save("new", DesktopChatDraft("new"))
        assertEquals(DesktopChatDraft(), drafts.restore("old"))
        assertEquals("new", drafts.restore("new").text)
    }

    private fun peer(ip: String, active: Boolean) =
        DesktopLanPeer(ip, ip, ip, active, false, false, 0L)
}
