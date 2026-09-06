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

internal class ThroughputTracker(
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private var startedAtMs: Long? = null
    private var lastAtMs = 0L
    private var firstBytes = 0L
    private var lastBytes = 0L

    fun reset() {
        startedAtMs = null
        lastAtMs = 0L
        firstBytes = 0L
        lastBytes = 0L
    }

    fun update(processedBytes: Long, totalBytes: Long): ThroughputSnapshot {
        val now = clockMs()
        val processed = processedBytes.coerceAtLeast(0L)
        if (startedAtMs == null || processed < lastBytes) {
            startedAtMs = now
            lastAtMs = now
            firstBytes = processed
            lastBytes = processed
            return ThroughputSnapshot(0L, 0L, null)
        }
        val deltaTimeMs = (now - lastAtMs).coerceAtLeast(1L)
        val deltaBytes = processed - lastBytes
        val instant = (deltaBytes.toDouble() * 1000.0 / deltaTimeMs).toLong()
        val elapsedMs = (now - requireNotNull(startedAtMs)).coerceAtLeast(1L)
        val average = ((processed - firstBytes).toDouble() * 1000.0 / elapsedMs).toLong()
        val eta = if (totalBytes >= processed && average > 0L) {
            (totalBytes - processed) / average
        } else null
        lastAtMs = now
        lastBytes = processed
        return ThroughputSnapshot(instant, average, eta)
    }
}
