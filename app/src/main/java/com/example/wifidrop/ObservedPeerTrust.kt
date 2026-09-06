package com.example.wifidrop

/** Observations can preserve authentication, but only the current trust store can preserve trust. */
internal fun retainCurrentPeerTrust(
    peers: Collection<KnownPeerSnapshot>, currentTrustedPeers: List<TrustedPeer>
): List<KnownPeerSnapshot> {
    val trustedIds = currentTrustedPeers.map { it.id }.toSet()
    return peers.map { peer -> peer.copy(trusted = peer.trusted && peer.id in trustedIds) }
}
