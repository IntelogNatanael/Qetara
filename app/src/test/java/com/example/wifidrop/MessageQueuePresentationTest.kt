package com.example.wifidrop

import org.junit.Assert.*
import org.junit.Test

class MessageQueuePresentationTest : LocalizedResourcesTest() {
    @Test fun automaticRosterStartRetrySuccessAndFailurePreserveTheVisibleChatStatus() {
        val roster = task(ChatMessageScopeCodec.encodeDirectRoster(listOf(ChatTransportPeer("peer", "Peer", "192.0.2.20"))))
        for (language in listOf("es", "en")) {
            useAppLocale(language)
            val existingFailure = appString(R.string.rt_message_send_error, runtimeFailureText("network_unreachable"))
            for (prior in listOf("", existingFailure)) {
                lifecycleStatuses().forEach { status ->
                    // Both an in-flight/retrying roster and its final removal must leave Chat alone.
                    for (pending in listOf(listOf(roster), emptyList())) {
                        val view = resolveMessageQueuePresentation(prior, pending, 1_000, status, roster)
                        assertEquals(prior, view.status)
                        assertEquals(0, view.pendingCount)
                        assertEquals(appString(R.string.rt_qetara_ready),
                            buildTransferNotificationText(TransferRuntimeState(pendingMessageCount = view.pendingCount)))
                    }
                }
            }
            // Completion callbacks recompute the queue without an explicit status override.
            assertEquals(existingFailure,
                resolveMessageQueuePresentation(existingFailure, listOf(roster), 1_000, sourceTask = roster).status)
        }
    }

    @Test fun userMessagesAndSilentChannelRelayOrFileRequestsKeepTheirFeedback() {
        val tasks = listOf(
            task("A direct message").copy(trackChatStatus = true),
            task("A published channel message").copy(scope = ChatMessageScope.GLOBAL_LAN),
            task(ChatMessageScopeCodec.encodeUserPayload("A direct-channel message", ChatMessageScope.DIRECT_CHANNEL))
                .copy(scope = ChatMessageScope.DIRECT_CHANNEL),
            task(ChatMessageScopeCodec.encodeDirectRelayRequest("Relay this", "sender", "Sender", "peer", "192.0.2.20", "Peer")),
            task(ChatMessageScopeCodec.encodeChannelFileRequest(ChannelFileRequest("offer", "sender", "Sender", null)))
        )
        for (language in listOf("es", "en")) {
            useAppLocale(language)
            for (task in tasks) {
                lifecycleStatuses().forEach { status ->
                    val view = resolveMessageQueuePresentation("", listOf(task), 1_000, status, task)
                    assertEquals(status, view.status)
                    assertEquals(1, view.pendingCount)
                    assertTrue(isUserVisibleMessageTask(task))
                }
            }
        }
    }

    @Test fun rosterDoesNotChangeTheVisibleRetryCountOrDeadlineOfARealMessage() {
        val roster = task(ChatMessageScopeCodec.encodeDirectRoster(emptyList())).copy(nextAttemptAtMs = 2_000)
        val user = task("Queued message").copy(trackChatStatus = true, nextAttemptAtMs = 9_000)
        for (language in listOf("es", "en")) {
            useAppLocale(language)
            val view = resolveMessageQueuePresentation("", listOf(roster, user), 1_000)
            assertEquals(1, view.pendingCount)
            assertEquals(appQuantityString(R.plurals.rt_pending_messages_retry, 1, 1, 8L), view.status)
            val prior = appString(R.string.rt_sending_message_to, "Peer")
            val completion = resolveMessageQueuePresentation(prior, listOf(user), 1_000, sourceTask = roster)
            assertEquals(prior, completion.status)
            assertEquals(1, completion.pendingCount)
        }
    }

    @Test fun systemIdsAloneDoNotSuppressUserFailuresAndTrackedMessagesStayVisible() {
        val encodedRoster = ChatMessageScopeCodec.encodeDirectRoster(emptyList())
        val roster = task(encodedRoster).copy(chatMessageId = "ordinary-id")
        assertFalse(isUserVisibleMessageTask(roster))
        assertTrue(isUserVisibleMessageTask(task("Actual content").copy(chatMessageId = "system_synthetic")))
        assertTrue(isUserVisibleMessageTask(roster.copy(trackChatStatus = true)))
    }

    private fun lifecycleStatuses() = listOf(
        appString(R.string.rt_sending_message_to, "Peer"),
        appString(R.string.rt_connection_retry_in, "Peer", 2L),
        appString(R.string.rt_message_sent_to, "Peer"),
        appString(R.string.rt_message_send_error, runtimeFailureText("frame de handshake invalido"))
    )

    private fun task(message: String) = PendingMessageTask(
        id = "transport-task", chatMessageId = "system_synthetic", targetIp = "192.0.2.20", peerLabel = "Peer",
        scope = ChatMessageScope.DIRECT, message = message, token = "not-used", pin = "not-used",
        clientId = "local", deviceLabel = "Local", trackChatStatus = false
    )
}
