package com.example.wifidrop

data class KnownPeerSnapshot(
    val id: String,
    val label: String,
    val ip: String,
    val trusted: Boolean,
    val globalLanJoined: Boolean,
    val lastSeenAtMs: Long
)

data class PendingTrustRequest(
    val id: String,
    val label: String,
    val ip: String,
    val requestedAtMs: Long
)

data class PendingCredentialShareRequest(
    val id: String,
    val label: String,
    val ip: String,
    val requestedAtMs: Long
)

enum class SendQueueStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    RETRY_WAIT,
    SUCCESS,
    FAILED,
    CANCELED
}

data class SendQueueItemSnapshot(
    val id: String,
    val fileName: String,
    val targetIp: String,
    val peerLabel: String?,
    val status: SendQueueStatus,
    val sentBytes: Long,
    val totalBytes: Long,
    val retriesUsed: Int,
    val maxRetries: Int,
    val lastError: String?,
    val addedAtMs: Long
)

data class TransferRuntimeState(
    val serviceRunning: Boolean = false,
    val paused: Boolean = false,
    val receiving: Boolean = false,
    val receiverStatus: String = "Receptor inactivo.",
    val receiverProgress: Float? = null,
    val receiverFileName: String? = null,
    val receiverInstantBps: Long = 0L,
    val receiverAverageBps: Long = 0L,
    val receiverEtaSeconds: Long? = null,
    val receiverFailureCause: String? = null,
    val sending: Boolean = false,
    val sendProgress: Float = 0f,
    val sendStatus: String = "",
    val sendInstantBps: Long = 0L,
    val sendAverageBps: Long = 0L,
    val sendEtaSeconds: Long? = null,
    val sendFailureCause: String? = null,
    val sendActiveCount: Int = 0,
    val sendBatchTotal: Int = 0,
    val sendBatchCompleted: Int = 0,
    val sendBatchFailed: Int = 0,
    val sendBatchCanceled: Int = 0,
    val sendQueue: List<SendQueueItemSnapshot> = emptyList(),
    val knownPeers: List<KnownPeerSnapshot> = emptyList(),
    val pendingTrust: PendingTrustRequest? = null,
    val pendingCredentialShare: PendingCredentialShareRequest? = null,
    val trustedPeers: List<TrustedPeer> = emptyList(),
    val favoritePeers: List<TrustedPeer> = emptyList(),
    val history: List<TransferHistoryEntry> = emptyList(),
    val lastPeerIp: String? = null,
    val lastPeerLabel: String? = null,
    val lastSendTargetIp: String? = null,
    val lastSendTargetLabel: String? = null,
    val lastSendTargetAtMs: Long? = null,
    val lastReceivedPath: String? = null,
    val activeToken: String? = null,
    val sessionExpiresAtMs: Long? = null,
    val chatMessages: List<ChatMessageEntry> = emptyList(),
    val messageStatus: String = "",
    val pendingMessageCount: Int = 0
)
