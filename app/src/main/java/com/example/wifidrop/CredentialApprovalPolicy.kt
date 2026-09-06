package com.example.wifidrop

import kotlinx.coroutines.flow.MutableStateFlow

/** Consume exactly the encrypted request the user reviewed, preserving any replacement request. */
internal fun claimCredentialShareRequest(
    state: MutableStateFlow<TransferRuntimeState>,
    peerId: String,
    expectedNoiseStaticKey: String,
    requestedAtMs: Long
): PendingCredentialShareRequest? {
    if (peerId.isBlank() || expectedNoiseStaticKey.isBlank() || requestedAtMs <= 0L) return null
    while (true) {
        val before = state.value
        val pending = before.pendingCredentialShare ?: return null
        if (pending.id != peerId || pending.noiseStaticKey != expectedNoiseStaticKey || pending.requestedAtMs != requestedAtMs) return null
        if (state.compareAndSet(before, before.copy(pendingCredentialShare = null))) return pending
    }
}
