package com.example.wifidrop

import java.util.Locale

internal fun buildTransferNotificationText(
    state: TransferRuntimeState,
    overrideText: String? = null
): String {
    return overrideText ?: when {
        state.paused -> "Transferencias en pausa."
        state.sending -> {
            if (state.sendActiveCount > 0) {
                val pct = (state.sendProgress * 100).toInt().coerceIn(0, 100)
                "Enviando ${state.sendActiveCount} archivo(s) · $pct% · ${formatTransferRate(state.sendAverageBps)}"
            } else {
                val pending = state.sendQueue.count {
                    it.status == SendQueueStatus.QUEUED || it.status == SendQueueStatus.PAUSED
                }
                "Cola pendiente: $pending archivo(s)"
            }
        }

        state.receiving -> {
            val pct = state.receiverProgress?.let { (it * 100).toInt().coerceIn(0, 100) } ?: 0
            "Recibiendo $pct% · ${formatTransferRate(state.receiverAverageBps)}"
        }

        state.receiverListening -> "Esperando archivos."

        state.pendingMessageCount > 0 -> {
            "Mensajes en cola: ${state.pendingMessageCount}"
        }

        else -> "Qetara lista."
    }
}

internal fun formatTransferRate(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return "0 B/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB/s", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB/s", mb)
    val gb = mb / 1024.0
    return String.format(Locale.getDefault(), "%.2f GB/s", gb)
}
