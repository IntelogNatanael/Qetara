package com.example.wifidrop.presentation

import com.example.wifidrop.SessionCredentialShare

data class P2pSessionConfirmation(
    val peerIp: String,
    val peerId: String?,
    val networkKey: String,
    val token: String,
    val pin: String
)

/** A selected address is not evidence that both devices share the current session. */
fun isSessionReadyForTarget(
    target: P2pResolvedTarget?,
    token: String,
    pin: String,
    sessionExpired: Boolean,
    networkKey: String,
    networkChangedAtMs: Long,
    confirmation: P2pSessionConfirmation?,
    sharedCredentials: List<SessionCredentialShare>
): Boolean {
    if (target == null || target.ip.isBlank() || sessionExpired) return false
    val locallyConfirmed = confirmation != null &&
        confirmation.peerIp == target.ip &&
        confirmation.peerId == target.peerId &&
        confirmation.networkKey == networkKey &&
        confirmation.token == token &&
        confirmation.pin == pin
    val sharedWithIdentity = !target.peerId.isNullOrBlank() && sharedCredentials.any {
        it.peerId == target.peerId && it.peerIp == target.ip && it.sharedAtMs >= networkChangedAtMs
    }
    return locallyConfirmed || sharedWithIdentity
}
