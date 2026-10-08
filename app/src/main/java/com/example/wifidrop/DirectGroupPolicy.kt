package com.example.wifidrop

/** Current platform-reported group. Addresses come from its actual network interface. */
data class DirectGroupContext(
    val sessionId: String,
    val ownerIp: String,
    val isOwner: Boolean,
    val interfaceName: String,
    val localAddresses: Set<String>,
    val startedAtElapsedMs: Long
)

/** Captured on an accepted socket, emitted only after its Noise identity is authenticated. */
data class PeerRouteObservation(
    val peerId: String,
    val peerIp: String,
    val localIp: String,
    val acceptedAtElapsedMs: Long,
    val noiseStaticKey: String = ""
)

internal fun isCurrentDirectRoute(group: DirectGroupContext?, route: PeerRouteObservation): Boolean {
    if (group == null || group.sessionId.isBlank() || group.interfaceName.isBlank()) return false
    if (route.peerId.isBlank() || route.peerIp.isBlank() || route.localIp.isBlank()) return false
    // Strict inequality also rejects a socket accepted on the group's transition boundary.
    if (route.acceptedAtElapsedMs <= group.startedAtElapsedMs) return false
    if (route.localIp !in group.localAddresses || route.peerIp in group.localAddresses) return false
    return if (group.isOwner) route.localIp == group.ownerIp else route.peerIp == group.ownerIp
}

fun resolveVerifiedDirectParticipants(
    connection: ConnectionSnapshot?,
    transferState: TransferRuntimeState
): List<KnownPeerSnapshot> {
    if (connection?.groupFormed != true) return emptyList()
    val ownerIp = connection.groupOwnerAddress?.trim().takeUnless { it.isNullOrBlank() } ?: return emptyList()
    if (!connection.isGroupOwner) {
        // The platform supplies this destination before credentials can be approved.
        return listOf(transferState.knownPeers.firstOrNull { it.ip == ownerIp } ?: KnownPeerSnapshot(
            id = "direct_owner_$ownerIp", label = appString(R.string.rt_direct_host), ip = ownerIp,
            trusted = false, globalLanJoined = false, lastSeenAtMs = 0L
        ))
    }
    val group = transferState.directGroup ?: return emptyList()
    if (!group.isOwner || group.ownerIp != ownerIp) return emptyList()
    return transferState.knownPeers.filter { peer ->
        peer.trusted && transferState.authenticatedPeerRoutes.any { route ->
            route.peerId == peer.id && route.peerIp == peer.ip && isCurrentDirectPeerRoute(transferState, route)
        }
    }.distinctBy { it.ip }.sortedByDescending { it.lastSeenAtMs }
}

internal fun verifiedDirectGroupMembers(state: TransferRuntimeState): List<KnownPeerSnapshot> {
    val group = state.directGroup ?: return emptyList()
    return resolveVerifiedDirectParticipants(ConnectionSnapshot(true, group.isOwner, group.ownerIp), state)
}

internal fun requireDirectChannelSender(state: TransferRuntimeState, route: PeerRouteObservation) {
    if (!isCurrentDirectPeerRoute(state, route)) throw SecurityException("grupo_direct_no_acreditado")
    val peer = state.knownPeers.firstOrNull { it.id == route.peerId && it.ip == route.peerIp && it.trusted }
    if (peer == null) throw SecurityException("grupo_direct_no_acreditado")
}

internal fun requireDirectRelayTargets(
    state: TransferRuntimeState,
    sender: PeerRouteObservation,
    targetIp: String? = null,
    targetPeerId: String? = null
): List<KnownPeerSnapshot> {
    if (state.directGroup?.isOwner != true) throw SecurityException("grupo_direct_no_acreditado")
    requireDirectChannelSender(state, sender)
    val members = verifiedDirectGroupMembers(state).filter { it.id != sender.peerId && it.ip != sender.peerIp }
    if (targetIp == null) return members
    return members.filter { it.ip == targetIp && (targetPeerId.isNullOrBlank() || it.id == targetPeerId) }
        .takeIf { it.isNotEmpty() } ?: throw SecurityException("destino_fuera_grupo_direct")
}

/** A clients change keeps the same epoch; a disconnected/recreated group gets a new one. */
internal class DirectGroupEpoch {
    private var identity: List<Any>? = null
    private var current: DirectGroupContext? = null

    fun clear(): DirectGroupContext? {
        identity = null
        current = null
        return null
    }

    fun update(
        ownerIp: String, isOwner: Boolean, interfaceName: String,
        localAddresses: Set<String>, networkName: String, ownerDeviceAddress: String,
        nowElapsedMs: Long, nextSessionId: () -> String
    ): DirectGroupContext? {
        if (ownerIp.isBlank() || interfaceName.isBlank() || localAddresses.isEmpty() ||
            (isOwner && ownerIp !in localAddresses)) return clear()
        val nextIdentity = listOf(ownerIp, isOwner, interfaceName, localAddresses, networkName, ownerDeviceAddress)
        if (identity != nextIdentity || current == null) {
            identity = nextIdentity
            current = DirectGroupContext(nextSessionId(), ownerIp, isOwner, interfaceName, localAddresses.toSet(), nowElapsedMs)
        }
        return current
    }
}

internal fun requiresDirectGroupDelivery(message: String, scope: ChatMessageScope, localPeerId: String): Boolean {
    if (scope == ChatMessageScope.DIRECT_CHANNEL) return true
    return when (val payload = ChatMessageScopeCodec.decodeFromTransport(message)) {
        is ChatMessageScopeCodec.DecodedChatPayload.DirectRoster,
        is ChatMessageScopeCodec.DecodedChatPayload.DirectRelayRequest,
        is ChatMessageScopeCodec.DecodedChatPayload.ChannelRelayRequest -> true
        is ChatMessageScopeCodec.DecodedChatPayload.User -> payload.scope == ChatMessageScope.DIRECT_CHANNEL ||
            (payload.scope == ChatMessageScope.DIRECT && !payload.senderId.isNullOrBlank() && payload.senderId != localPeerId)
        else -> false
    }
}

internal fun validateDirectGroupDelivery(task: PendingMessageTask, state: TransferRuntimeState) {
    if (!requiresDirectGroupDelivery(task.message, task.scope, task.clientId)) return
    val group = state.directGroup ?: throw SecurityException("grupo_direct_no_acreditado")
    if (task.directGroupSessionId != group.sessionId || task.expectedPeerId.isNullOrBlank()) {
        throw SecurityException("grupo_direct_renovado")
    }
    val members = verifiedDirectGroupMembers(state)
    if (members.none { it.ip == task.targetIp && it.id == task.expectedPeerId }) {
        throw SecurityException("destino_fuera_grupo_direct")
    }
    val payload = ChatMessageScopeCodec.decodeFromTransport(task.message)
    if (payload is ChatMessageScopeCodec.DecodedChatPayload.DirectRoster) {
        if (!group.isOwner || payload.peers.any { listed -> members.none { it.id == listed.id && it.ip == listed.ip } }) {
            throw SecurityException("destino_fuera_grupo_direct")
        }
    }
}

internal fun isCurrentDirectPeerRoute(state: TransferRuntimeState, route: PeerRouteObservation): Boolean {
    if (!isCurrentDirectRoute(state.directGroup, route)) return false
    if (route.acceptedAtElapsedMs <= (state.peerRouteRevocations[route.peerId] ?: -1L)) return false
    if (route.noiseStaticKey.isBlank()) return false
    return state.trustedPeers.any { it.id == route.peerId && it.noiseStaticKey == route.noiseStaticKey }
}

internal fun revokePeerRouteEvidence(state: TransferRuntimeState, peerId: String, nowElapsedMs: Long): TransferRuntimeState = state.copy(
    authenticatedPeerRoutes = state.authenticatedPeerRoutes.filterNot { it.peerId == peerId },
    peerRouteRevocations = state.peerRouteRevocations + (peerId to nowElapsedMs)
)

internal fun removeReassignedPeerRoutes(routes: List<PeerRouteObservation>, peerId: String, peerIp: String): List<PeerRouteObservation> =
    routes.filterNot { (it.peerId == peerId && it.peerIp != peerIp) || (it.peerIp == peerIp && it.peerId != peerId) }
