package com.example.wifidrop

internal fun buildTransferNotificationText(
    state: TransferRuntimeState,
    overrideText: String? = null
): String {
    return overrideText ?: when {
        state.paused -> appString(R.string.rt_transfers_paused)
        state.sending -> {
            if (state.sendActiveCount > 0) {
                val pct = (state.sendProgress * 100).toInt().coerceIn(0, 100)
                appQuantityString(
                    R.plurals.rt_notification_sending_files, state.sendActiveCount,
                    state.sendActiveCount, pct, formatTransferRate(state.sendAverageBps)
                )
            } else {
                val pending = state.sendQueue.count {
                    it.status == SendQueueStatus.QUEUED || it.status == SendQueueStatus.PAUSED
                }
                appQuantityString(R.plurals.rt_pending_files, pending, pending)
            }
        }

        state.receiving -> {
            val pct = state.receiverProgress?.let { (it * 100).toInt().coerceIn(0, 100) } ?: 0
            appString(R.string.rt_notification_receiving, pct, formatTransferRate(state.receiverAverageBps))
        }

        state.receiverListening -> appString(R.string.rt_waiting_files)

        state.pendingMessageCount > 0 -> {
            appQuantityString(R.plurals.rt_queued_messages, state.pendingMessageCount, state.pendingMessageCount)
        }

        else -> appString(R.string.rt_qetara_ready)
    }
}

internal fun formatTransferRate(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return appString(R.string.rt_rate_bytes, 0)
    val kb = bytesPerSec / 1024.0
    if (kb < 1024) return appString(R.string.rt_rate_kilobytes, kb)
    val mb = kb / 1024.0
    if (mb < 1024) return appString(R.string.rt_rate_megabytes, mb)
    val gb = mb / 1024.0
    return appString(R.string.rt_rate_gigabytes, gb)
}
