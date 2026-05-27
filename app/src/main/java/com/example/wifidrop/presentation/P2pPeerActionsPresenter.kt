package com.example.wifidrop.presentation

import com.example.wifidrop.ConnectionSnapshot
import com.example.wifidrop.KnownPeerSnapshot
import com.example.wifidrop.TrustedPeer
import com.example.wifidrop.TrustedPeerStore
import com.example.wifidrop.backend.P2pBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class P2pPeerActionResult(
    val feedback: P2pFeedbackMessage? = null,
    val undoPlan: P2pUndoFeedbackPlan? = null,
    val onUndo: (() -> Unit)? = null
)

class P2pPeerActionsPresenter(
    private val backend: P2pBackend
) {
    private val handledFavoriteSuggestionIds = linkedSetOf<String>()
    private val _favoriteSuggestion = MutableStateFlow(
        P2pFavoriteSuggestionState(
            peerId = null,
            label = null
        )
    )

    val favoriteSuggestion: StateFlow<P2pFavoriteSuggestionState> = _favoriteSuggestion.asStateFlow()

    fun refreshFavoriteSuggestion(
        connection: ConnectionSnapshot?,
        knownPeers: List<KnownPeerSnapshot>,
        trustedPeers: List<TrustedPeer>,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (connection?.groupFormed != true) {
            resetFavoriteSuggestionState()
            return
        }

        val activeSuggestionId = _favoriteSuggestion.value.peerId.orEmpty()
        val activeSuggestionStillValid = trustedPeers.any { peer ->
            peer.id == activeSuggestionId &&
                !peer.favorite &&
                !handledFavoriteSuggestionIds.contains(peer.id)
        }
        if (activeSuggestionStillValid) return

        val connectedIps = buildSet {
            if (connection.isGroupOwner) {
                knownPeers.forEach { peer ->
                    if (peer.ip.isNotBlank()) add(peer.ip)
                }
            } else {
                connection.groupOwnerAddress
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }

        val trustedCandidates = knownPeers
            .asSequence()
            .filter { it.trusted }
            .filter { connectedIps.isEmpty() || connectedIps.contains(it.ip) }
            .sortedByDescending { it.lastSeenAtMs }
            .mapNotNull { known -> trustedPeers.firstOrNull { peer -> peer.id == known.id } }

        val candidate = trustedCandidates.firstOrNull { peer ->
            !peer.favorite && !handledFavoriteSuggestionIds.contains(peer.id)
        } ?: trustedPeers
            .sortedByDescending { it.lastSeenAtMs }
            .firstOrNull { peer ->
                !peer.favorite &&
                    !handledFavoriteSuggestionIds.contains(peer.id) &&
                    peer.lastSeenAtMs > 0L &&
                    nowMs - peer.lastSeenAtMs <= 120_000L
            }

        _favoriteSuggestion.update {
            P2pFavoriteSuggestionState(
                peerId = candidate?.id,
                label = candidate?.let(TrustedPeerStore::displayName)
            )
        }
    }

    fun dismissFavoriteSuggestion(peerId: String) {
        val normalized = peerId.trim()
        if (normalized.isBlank()) return
        handledFavoriteSuggestionIds.add(normalized)
        if (_favoriteSuggestion.value.peerId == normalized) {
            clearFavoriteSuggestion()
        }
    }

    fun saveSuggestedFavorite(peerId: String): P2pFeedbackMessage? {
        val normalized = peerId.trim()
        if (normalized.isBlank()) return null
        backend.setPeerFavorite(normalized, true)
        dismissFavoriteSuggestion(normalized)
        return P2pFeedbackMessage("Equipo guardado como favorito.")
    }

    fun skipSuggestedFavorite(peerId: String): P2pFeedbackMessage? {
        val normalized = peerId.trim()
        if (normalized.isBlank()) return null
        dismissFavoriteSuggestion(normalized)
        return P2pFeedbackMessage("Equipo omitido por ahora.")
    }

    fun updatePeerFavorite(peerId: String, favorite: Boolean): P2pPeerActionResult {
        val normalized = peerId.trim()
        if (normalized.isBlank()) return P2pPeerActionResult()

        backend.setPeerFavorite(normalized, favorite)
        return if (favorite) {
            dismissFavoriteSuggestion(normalized)
            P2pPeerActionResult(
                feedback = P2pFeedbackMessage("Equipo guardado en favoritos.")
            )
        } else {
            dismissFavoriteSuggestion(normalized)
            P2pPeerActionResult(
                undoPlan = buildFavoriteRemovedUndoPlan(),
                onUndo = { backend.setPeerFavorite(normalized, true) }
            )
        }
    }

    fun updatePeerAlias(
        peerId: String,
        aliasRaw: String,
        trustedPeers: List<TrustedPeer>
    ): P2pFeedbackMessage? {
        val normalizedPeerId = peerId.trim()
        if (normalizedPeerId.isBlank()) return null

        val normalizedAlias = normalizePeerAlias(aliasRaw)
        val currentAlias = trustedPeers
            .firstOrNull { it.id == normalizedPeerId }
            ?.alias
            .orEmpty()
            .let(::normalizePeerAlias)

        val feedback = buildAliasUpdateFeedback(
            normalizedAlias = normalizedAlias,
            currentAlias = currentAlias
        )
        if (normalizedAlias == currentAlias) return feedback

        backend.setPeerAlias(normalizedPeerId, normalizedAlias)
        return feedback
    }

    private fun resetFavoriteSuggestionState() {
        handledFavoriteSuggestionIds.clear()
        clearFavoriteSuggestion()
    }

    private fun clearFavoriteSuggestion() {
        _favoriteSuggestion.update {
            P2pFavoriteSuggestionState(
                peerId = null,
                label = null
            )
        }
    }
}
