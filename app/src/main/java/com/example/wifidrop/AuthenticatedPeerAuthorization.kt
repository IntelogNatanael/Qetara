package com.example.wifidrop

/** Called only after the PSK check and Noise handshake, before accepting any application payload. */
internal fun authorizeAuthenticatedPeer(
    remoteNoiseKey: String,
    isTrusted: () -> Boolean,
    pinnedNoiseKey: () -> String?,
    requestApproval: () -> Boolean
): Boolean {
    if (remoteNoiseKey.isBlank()) throw SecurityException("identidad Noise ausente")
    val previousKey = pinnedNoiseKey()
    if (!previousKey.isNullOrBlank() && previousKey != remoteNoiseKey) throw SecurityException("noise_key_mismatch")
    if (isTrusted() && previousKey == remoteNoiseKey) return true
    if (!requestApproval()) return false
    // A Boolean callback alone cannot authorize a key that was never pinned by exact-request approval.
    return isTrusted() && pinnedNoiseKey() == remoteNoiseKey
}
