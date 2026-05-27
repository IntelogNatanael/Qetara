package com.example.wifidrop

import android.net.Uri

internal data class ThroughputSnapshot(
    val instantBps: Long,
    val averageBps: Long,
    val etaSeconds: Long?
)

internal data class SendTaskSnapshot(
    val id: String,
    val fileName: String,
    val targetIp: String,
    val peerLabel: String?,
    val totalBytes: Long,
    val sentBytes: Long,
    val instantBps: Long,
    val averageBps: Long,
    val etaSeconds: Long?,
    val retriesUsed: Int,
    val maxRetries: Int,
    val status: SendQueueStatus,
    val lastError: String?,
    val pauseRequested: Boolean,
    val cancelRequested: Boolean,
    val done: Boolean,
    val addedAtMs: Long
)

internal data class SendTaskPayload(
    val uri: Uri,
    val fileName: String,
    val targetIp: String,
    val token: String,
    val pin: String,
    val clientId: String,
    val deviceLabel: String,
    val peerLabel: String?
)

internal data class SendAggregateSnapshot(
    val activeCount: Int,
    val pendingCount: Int,
    val batchTotal: Int,
    val completed: Int,
    val failed: Int,
    val canceled: Int,
    val progress: Float,
    val instantBps: Long,
    val averageBps: Long,
    val etaSeconds: Long?,
    val queue: List<SendQueueItemSnapshot>
)

internal class ThroughputTracker {
    private var startedAtMs = 0L
    private var lastAtMs = 0L
    private var lastBytes = 0L

    fun reset() {
        startedAtMs = 0L
        lastAtMs = 0L
        lastBytes = 0L
    }

    fun update(processedBytes: Long, totalBytes: Long): ThroughputSnapshot {
        val now = System.currentTimeMillis()
        if (startedAtMs == 0L) {
            startedAtMs = now
            lastAtMs = now
            lastBytes = 0L
        }

        val deltaTimeMs = (now - lastAtMs).coerceAtLeast(1L)
        val deltaBytes = (processedBytes - lastBytes).coerceAtLeast(0L)

        val instant = (deltaBytes * 1000L) / deltaTimeMs
        val elapsedMs = (now - startedAtMs).coerceAtLeast(1L)
        val average = (processedBytes.coerceAtLeast(0L) * 1000L) / elapsedMs

        val eta = if (totalBytes > 0 && average > 0 && processedBytes <= totalBytes) {
            ((totalBytes - processedBytes) / average).coerceAtLeast(0L)
        } else {
            null
        }

        lastAtMs = now
        lastBytes = processedBytes
        return ThroughputSnapshot(instantBps = instant, averageBps = average, etaSeconds = eta)
    }
}
