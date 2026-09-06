package com.example.wifidrop.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pAttachmentDraftsTest {
    @Test
    fun switchingAudiencesPreservesEachSelectionWithoutCopyingIt() {
        var drafts = P2pAttachmentDrafts<String>()
            .update(P2pAttachmentContext.FILES) { it.copy(files = listOf("report.pdf")) }
        drafts = drafts.select(P2pAttachmentContext.DIRECT_CHAT)
        assertTrue(drafts.draft().files.isEmpty())
        drafts = drafts.update(P2pAttachmentContext.DIRECT_CHAT) { it.copy(files = listOf("private.jpg")) }
            .select(P2pAttachmentContext.CHANNEL)
        assertTrue(drafts.draft().files.isEmpty())
        assertEquals(listOf("report.pdf"), drafts.select(P2pAttachmentContext.FILES).draft().files)
        assertEquals(listOf("private.jpg"), drafts.select(P2pAttachmentContext.DIRECT_CHAT).draft().files)
    }

    @Test
    fun clearingOrSendingOneDraftDoesNotRemoveAnotherDraft() {
        val drafts = P2pAttachmentDrafts<String>()
            .update(P2pAttachmentContext.FILES) { it.copy(files = listOf("report.pdf")) }
            .update(P2pAttachmentContext.DIRECT_CHAT) { it.copy(files = listOf("private.jpg")) }
            .update(P2pAttachmentContext.CHANNEL) { it.copy(files = listOf("public.jpg")) }
            .update(P2pAttachmentContext.CHANNEL) { P2pAttachmentDraft(status = "Publicado") }
        assertTrue(drafts.draft(P2pAttachmentContext.CHANNEL).files.isEmpty())
        assertEquals(listOf("private.jpg"), drafts.draft(P2pAttachmentContext.DIRECT_CHAT).files)
        assertEquals(listOf("report.pdf"), drafts.draft(P2pAttachmentContext.FILES).files)
    }

    @Test
    fun pickerResultBelongsToTheComposerThatOpenedItAfterNavigation() {
        val pickerContext = P2pAttachmentContext.DIRECT_CHAT
        val drafts = P2pAttachmentDrafts<String>(activeContext = pickerContext)
            .select(P2pAttachmentContext.CHANNEL)
            .update(pickerContext) { it.copy(files = listOf("private.jpg")) }
        assertEquals(P2pAttachmentContext.CHANNEL, drafts.activeContext)
        assertTrue(drafts.draft().files.isEmpty())
        assertEquals(listOf("private.jpg"), drafts.draft(pickerContext).files)
    }

    @Test
    fun externalShareCanOpenSendWithoutReplacingChatOrChannelDrafts() {
        val drafts = P2pAttachmentDrafts<String>(activeContext = P2pAttachmentContext.CHANNEL)
            .update(P2pAttachmentContext.CHANNEL) { it.copy(files = listOf("channel.jpg")) }
            .update(P2pAttachmentContext.FILES) { it.copy(files = listOf("shared.pdf")) }
            .select(P2pAttachmentContext.FILES)
        assertEquals(listOf("shared.pdf"), drafts.draft().files)
        assertEquals(listOf("channel.jpg"), drafts.draft(P2pAttachmentContext.CHANNEL).files)
    }
}
