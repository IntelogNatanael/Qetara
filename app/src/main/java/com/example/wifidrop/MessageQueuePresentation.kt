package com.example.wifidrop

/** Roster synchronization is control traffic, not a message sent by the person using Chat. */
internal fun isUserVisibleMessageTask(task: PendingMessageTask): Boolean =
    task.trackChatStatus ||
        ChatMessageScopeCodec.decodeFromTransport(task.message) !is ChatMessageScopeCodec.DecodedChatPayload.DirectRoster

internal data class MessageQueuePresentation(val status: String, val pendingCount: Int)

/** Project the transport queue without removing control tasks or changing their retry policy. */
internal fun resolveMessageQueuePresentation(
    currentStatus: String,
    pending: Collection<PendingMessageTask>,
    nowMs: Long,
    statusOverride: String? = null,
    sourceTask: PendingMessageTask? = null
): MessageQueuePresentation {
    val visiblePending = pending.filter(::isUserVisibleMessageTask)
    val count = visiblePending.size
    val nextDelaySec = visiblePending.minOfOrNull { ((it.nextAttemptAtMs - nowMs).coerceAtLeast(0L) / 1000L) }
    val status = when {
        sourceTask != null && !isUserVisibleMessageTask(sourceTask) -> currentStatus
        statusOverride != null -> statusOverride
        count > 0 && nextDelaySec != null && nextDelaySec > 0L ->
            appQuantityString(R.plurals.rt_pending_messages_retry, count, count, nextDelaySec)
        count > 0 -> appQuantityString(R.plurals.rt_pending_messages, count, count)
        else -> currentStatus
    }
    return MessageQueuePresentation(status, count)
}
