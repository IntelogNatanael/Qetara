package com.example.wifidrop.presentation

import com.example.wifidrop.ConnectionSnapshot
import com.example.wifidrop.KnownPeerSnapshot
import com.example.wifidrop.TrustedPeer

class P2pConnectionHintsPresenter(
    private val peerActionsPresenter: P2pPeerActionsPresenter
) {
    fun onLanTargetSuggested(
        currentTargetIp: String,
        suggestedIp: String?
    ): String? {
        if (currentTargetIp.isNotBlank()) return null
        return suggestedIp?.trim().takeUnless { it.isNullOrBlank() }
    }

    fun useSuggestedTarget(suggestedIp: String?): String? {
        return suggestedIp?.trim().takeUnless { it.isNullOrBlank() }
    }

    fun onConnectionStateChanged(
        connection: ConnectionSnapshot?,
        knownPeers: List<KnownPeerSnapshot>,
        trustedPeers: List<TrustedPeer>,
        currentTargetIp: String
    ): String? {
        peerActionsPresenter.refreshFavoriteSuggestion(
            connection = connection,
            knownPeers = knownPeers,
            trustedPeers = trustedPeers
        )

        if (currentTargetIp.isNotBlank()) return null
        if (connection?.groupFormed != true || connection.isGroupOwner) return null
        return connection.groupOwnerAddress?.trim().takeUnless { it.isNullOrBlank() }
    }

    fun onPeerTargetSelected(peerIp: String): String? {
        return peerIp.trim().takeUnless { it.isBlank() }
    }
}
