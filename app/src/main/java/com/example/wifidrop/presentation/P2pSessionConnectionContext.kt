package com.example.wifidrop.presentation

import com.example.wifidrop.ConnectionMode
import com.example.wifidrop.TransferRuntimeState
import com.example.wifidrop.sessionCredentialBinding

/** Discovery may name a manual endpoint before its authenticated socket observation arrives. */
internal fun observeSessionConnectionContext(
    current: P2pSessionState,
    networkKey: String,
    target: P2pResolvedTarget?,
    transferState: TransferRuntimeState?,
    nowMs: Long
): P2pSessionState {
    val networkChanged = current.connectionNetworkKey != networkKey
    val addressChanged = current.connectionTargetIp != target?.ip
    val identityChanged = current.connectionTargetPeerId != target?.peerId
    val confirmation = current.confirmation
    val learningManualIdentity = !networkChanged && !addressChanged &&
        target?.mode == ConnectionMode.LAN && !target.peerId.isNullOrBlank() &&
        (current.connectionTargetPeerId == null || current.connectionTargetPeerId == target.peerId) &&
        confirmation != null && confirmation.peerId == null && confirmation.confirmedAtElapsedMs != null &&
        confirmation.peerIp == target.ip && confirmation.networkKey == networkKey &&
        confirmation.token == current.token && confirmation.pin == current.pin && nowMs < current.expiresAtMs

    if (learningManualIdentity) {
        val sessionBinding = sessionCredentialBinding(current.token, current.pin, current.expiresAtMs)
        val authenticated = transferState != null && transferState.serviceRunning &&
            transferState.activeToken == current.token && transferState.sessionExpiresAtMs == current.expiresAtMs &&
            transferState.authenticatedPeerRoutes.any { route ->
                route.peerId == target.peerId && route.peerIp == target.ip &&
                    route.sessionBinding == sessionBinding &&
                    networkKey == "lan:true:${route.localIp}" &&
                    route.acceptedAtElapsedMs > confirmation.confirmedAtElapsedMs!! &&
                    route.acceptedAtElapsedMs > (transferState.peerRouteRevocations[route.peerId] ?: -1L) &&
                    route.noiseStaticKey.isNotBlank() && transferState.trustedPeers.any {
                        it.id == route.peerId && it.noiseStaticKey == route.noiseStaticKey
                    }
            }
        // Keep the unbound confirmation pending, never ready for the discovered identity.
        // Only a fresh authenticated route can bind it; a subsequent identity change clears it.
        return current.copy(
            connectionTargetPeerId = target.peerId,
            confirmation = if (authenticated) confirmation.copy(peerId = target.peerId, awaitingAuthenticatedIdentity = false)
                else confirmation.copy(awaitingAuthenticatedIdentity = true)
        )
    }
    if (!networkChanged && !addressChanged && !identityChanged) return current
    return current.copy(
        connectionNetworkKey = networkKey,
        connectionTargetIp = target?.ip,
        connectionTargetPeerId = target?.peerId,
        confirmation = null,
        lastAutoSyncedPeerIp = null,
        readinessChangedAtMs = if (networkChanged) nowMs else current.readinessChangedAtMs
    )
}
