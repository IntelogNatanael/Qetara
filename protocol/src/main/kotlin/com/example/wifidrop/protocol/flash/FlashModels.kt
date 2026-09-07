package com.example.wifidrop.protocol.flash

import java.io.File

const val FLASH_PORT = 8989

/** IDs and labels discovered over UDP are unverified until BOTH users compare the Noise code. */
data class FlashPeer(val id: String, val label: String, val address: String, val port: Int, val expiresAtMs: Long)
data class FlashOperation(val id: String, val peer: FlashPeer?, val fileName: String, val totalBytes: Long, val outgoing: Boolean)
data class FlashApproval(
    val requestId: String, val operationId: String, val peer: FlashPeer, val fileName: String,
    val totalBytes: Long, val outgoing: Boolean, val verificationCode: String, val expiresAtMs: Long
)
data class FlashState(
    val active: Boolean = false, val expiresAtMs: Long = 0, val localId: String = "", val port: Int = FLASH_PORT,
    val peers: List<FlashPeer> = emptyList(), val approvals: List<FlashApproval> = emptyList(),
    val operations: List<FlashOperation> = emptyList()
)
data class FlashProgress(val operationId: String, val transferredBytes: Long, val totalBytes: Long, val outgoing: Boolean)
data class FlashReceived(val operationId: String, val peer: FlashPeer, val file: File)
data class FlashCompleted(val operationId: String, val peer: FlashPeer, val fileName: String, val outgoing: Boolean)
data class FlashError(val operationId: String?, val code: String, val message: String)

/** Callbacks run on engine threads. UI adapters must dispatch to their UI thread. No secrets are emitted. */
interface FlashListener {
    fun onState(state: FlashState) {}
    fun onApproval(approval: FlashApproval) {}
    fun onProgress(progress: FlashProgress) {}
    fun onReceived(received: FlashReceived) {}
    fun onCompleted(completed: FlashCompleted) {}
    fun onError(error: FlashError) {}
}

/** Overrides primarily permit isolated loopback tests; production activation is capped at 30 minutes. */
data class FlashConfig(
    val port: Int = FLASH_PORT,
    val discoveryPort: Int = FLASH_PORT,
    val lifetimeMs: Long = 30 * 60 * 1000L,
    val approvalTimeoutMs: Long = 90_000L,
    val handshakeTimeoutMs: Int = 10_000,
    val transferTimeoutMs: Int = 30_000,
    val maxFileBytes: Long = 16L * 1024 * 1024 * 1024,
    val maxOperations: Int = 1,
    val discoveryEnabled: Boolean = true,
    val bindAddress: String? = null
) {
    init {
        require(port in 0..65535 && discoveryPort in 1..65535)
        require(lifetimeMs in 1..1_800_000 && approvalTimeoutMs in 1..120_000)
        require(handshakeTimeoutMs in 1..30_000 && transferTimeoutMs in 1..120_000)
        require(maxFileBytes >= 0 && maxOperations in 1..4)
    }
}
