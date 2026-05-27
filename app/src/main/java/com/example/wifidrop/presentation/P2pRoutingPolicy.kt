package com.example.wifidrop.presentation

import com.example.wifidrop.ChatChannel
import com.example.wifidrop.ConnectionMode
import com.example.wifidrop.KnownPeerSnapshot
import com.example.wifidrop.TransferRuntimeState
import com.example.wifidrop.WifiDirectState

data class P2pChatSelectionInput(
    val activeConnectionMode: ConnectionMode,
    val lanConnected: Boolean,
    val wifiState: WifiDirectState,
    val knownPeers: List<KnownPeerSnapshot>,
    val chatChannel: ChatChannel,
    val chatDirectLanTargetIp: String?,
    val chatDirectWifiTargetIps: List<String>
)

data class P2pChatSelectionState(
    val chatChannel: ChatChannel,
    val chatDirectLanTargetIp: String?,
    val chatDirectWifiTargetIps: List<String>
)

data class P2pRoutingInput(
    val wifiState: WifiDirectState,
    val transferState: TransferRuntimeState,
    val lanConnected: Boolean,
    val activeConnectionMode: ConnectionMode,
    val manualTargetIp: String,
    val chatDirectLanTargetIp: String?,
    val chatDirectWifiTargetIps: List<String>
)

data class P2pRoutingState(
    val targets: P2pScreenTargetsState,
    val globalChatTargets: List<KnownPeerSnapshot>,
    val directChannelTargets: List<KnownPeerSnapshot>
)

fun normalizeRequestedChatChannel(
    activeConnectionMode: ConnectionMode,
    requested: ChatChannel
): ChatChannel {
    return requested
}

fun normalizeChatSelection(input: P2pChatSelectionInput): P2pChatSelectionState {
    val normalizedChannel = normalizeRequestedChatChannel(input.activeConnectionMode, input.chatChannel)

    val normalizedLanTarget = if (input.activeConnectionMode != ConnectionMode.LAN || !input.lanConnected) {
        null
    } else {
        val directLanPeers = input.knownPeers
            .filter { it.ip.isNotBlank() }
            .distinctBy { it.ip }
        val selectedIp = input.chatDirectLanTargetIp?.trim().takeUnless { it.isNullOrBlank() }
        when {
            !selectedIp.isNullOrBlank() && directLanPeers.none { it.ip == selectedIp } -> null
            selectedIp.isNullOrBlank() && directLanPeers.size == 1 -> directLanPeers.first().ip
            else -> selectedIp
        }
    }

    val normalizedWifiTargets = if (input.activeConnectionMode != ConnectionMode.WIFI_DIRECT) {
        emptyList()
    } else {
        val availableDirectPeers = resolveDirectParticipants(
            wifiState = input.wifiState,
            transferState = input.knownPeers
        )
        val selectedIps = input.chatDirectWifiTargetIps
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val resolved = availableDirectPeers
            .filter { peer -> selectedIps.contains(peer.ip) }
            .map { it.ip }
        when {
            resolved.isNotEmpty() -> resolved
            availableDirectPeers.size == 1 -> listOf(availableDirectPeers.first().ip)
            else -> emptyList()
        }
    }

    return P2pChatSelectionState(
        chatChannel = normalizedChannel,
        chatDirectLanTargetIp = normalizedLanTarget,
        chatDirectWifiTargetIps = normalizedWifiTargets
    )
}

fun resolveP2pRouting(input: P2pRoutingInput): P2pRoutingState {
    val directParticipants = resolveDirectParticipants(
        wifiState = input.wifiState,
        transferState = input.transferState.knownPeers
    )
    val directTarget = resolveDirectTarget(
        wifiState = input.wifiState,
        transferState = input.transferState
    )
    val lanTarget = resolveLanTarget(
        transferState = input.transferState,
        lanConnected = input.lanConnected
    )
    val resolvedTarget = resolvePreferredTarget(
        manualTargetIp = input.manualTargetIp,
        transferState = input.transferState,
        lanConnected = input.lanConnected,
        directTarget = directTarget,
        lanTarget = lanTarget
    )
    val chatDirectTargets = when (input.activeConnectionMode) {
        ConnectionMode.WIFI_DIRECT -> resolveExplicitDirectChatTargets(
            selectedIps = input.chatDirectWifiTargetIps,
            availablePeers = directParticipants
        )
        ConnectionMode.LAN -> resolveExplicitLanChatTarget(
            explicitIp = input.chatDirectLanTargetIp,
            transferState = input.transferState
        )?.let(::listOf).orEmpty()
    }
    val globalChatTargets = resolveGlobalChatTargets(
        lanConnected = input.lanConnected,
        knownPeers = input.transferState.knownPeers
    )

    return P2pRoutingState(
        targets = P2pScreenTargetsState(
            suggestedTargetIp = lanTarget?.ip,
            resolvedTarget = resolvedTarget,
            directTarget = directTarget,
            chatDirectTarget = chatDirectTargets.firstOrNull(),
            chatDirectTargets = chatDirectTargets,
            chatDirectAvailablePeers = directParticipants,
            globalChatPeerCount = globalChatTargets.size,
            directChannelPeerCount = directParticipants.size
        ),
        globalChatTargets = globalChatTargets,
        directChannelTargets = directParticipants
    )
}

private fun resolveDirectParticipants(
    wifiState: WifiDirectState,
    transferState: TransferRuntimeState
): List<KnownPeerSnapshot> {
    return resolveDirectParticipants(
        wifiState = wifiState,
        transferState = transferState.knownPeers
    )
}

private fun resolveDirectParticipants(
    wifiState: WifiDirectState,
    transferState: List<KnownPeerSnapshot>
): List<KnownPeerSnapshot> {
    val connection = wifiState.connection ?: return emptyList()
    if (!connection.groupFormed) return emptyList()

    val ownerIp = connection.groupOwnerAddress?.trim().takeUnless { it.isNullOrBlank() }
    val knownPeers = transferState
        .filter { it.ip.isNotBlank() }
        .distinctBy { it.ip }
        .sortedWith(
            compareByDescending<KnownPeerSnapshot> { it.trusted }
                .thenByDescending { it.lastSeenAtMs }
        )

    val normalized = if (connection.isGroupOwner) {
        knownPeers.filterNot { peer -> !ownerIp.isNullOrBlank() && peer.ip == ownerIp }
    } else {
        val ownerPeer = ownerIp?.let { ip ->
            knownPeers.firstOrNull { it.ip == ip } ?: KnownPeerSnapshot(
                id = "direct_owner_$ip",
                label = knownPeers.firstOrNull { it.ip == ip }?.label ?: "Anfitrión Wi‑Fi Direct",
                ip = ip,
                trusted = false,
                globalLanJoined = false,
                lastSeenAtMs = System.currentTimeMillis()
            )
        }
        buildList {
            ownerPeer?.let(::add)
            knownPeers
                .filterNot { peer -> ownerPeer != null && peer.ip == ownerPeer.ip }
                .forEach(::add)
        }
    }

    return normalized.distinctBy { it.ip }
}

private fun resolveDirectTarget(
    wifiState: WifiDirectState,
    transferState: TransferRuntimeState
): P2pResolvedTarget? {
    val connection = wifiState.connection ?: return null
    if (!connection.groupFormed) return null

    if (!connection.isGroupOwner) {
        val ownerIp = connection.groupOwnerAddress?.trim().takeUnless { it.isNullOrBlank() } ?: return null
        val ownerPeer = transferState.knownPeers.firstOrNull { it.ip == ownerIp }
        return P2pResolvedTarget(
            peerId = ownerPeer?.id,
            ip = ownerIp,
            label = ownerPeer?.label?.ifBlank { null } ?: "Anfitrión Wi‑Fi Direct",
            mode = ConnectionMode.WIFI_DIRECT
        )
    }

    val recentCutoff = System.currentTimeMillis() - 20_000L
    val recentDirectPeer = transferState.lastPeerIp
        ?.trim()
        ?.takeUnless { it.isNullOrBlank() }
        ?.let { lastIp ->
            transferState.knownPeers.firstOrNull { peer ->
                peer.ip == lastIp && peer.lastSeenAtMs >= recentCutoff
            }
        }
        ?: resolveDirectParticipants(wifiState, transferState).firstOrNull()

    return recentDirectPeer?.let { peer ->
        P2pResolvedTarget(
            peerId = peer.id,
            ip = peer.ip,
            label = peer.label.ifBlank { null },
            mode = ConnectionMode.WIFI_DIRECT
        )
    }
}

private fun resolveLanTarget(
    transferState: TransferRuntimeState,
    lanConnected: Boolean
): P2pResolvedTarget? {
    if (!lanConnected) return null

    val preferredPeer = transferState.knownPeers
        .asSequence()
        .filter { it.ip.isNotBlank() }
        .sortedWith(
            compareByDescending<KnownPeerSnapshot> { it.trusted }
                .thenByDescending { it.lastSeenAtMs }
        )
        .firstOrNull()

    val fallbackIp = transferState.lastPeerIp?.trim().takeUnless { it.isNullOrBlank() }
    val fallbackLabel = transferState.lastPeerLabel?.trim().takeUnless { it.isNullOrBlank() }

    return when {
        preferredPeer != null -> P2pResolvedTarget(
            peerId = preferredPeer.id,
            ip = preferredPeer.ip,
            label = preferredPeer.label.ifBlank { null },
            mode = ConnectionMode.LAN
        )

        fallbackIp != null -> P2pResolvedTarget(
            ip = fallbackIp,
            label = fallbackLabel,
            mode = ConnectionMode.LAN
        )

        else -> null
    }
}

private fun resolvePreferredTarget(
    manualTargetIp: String,
    transferState: TransferRuntimeState,
    lanConnected: Boolean,
    directTarget: P2pResolvedTarget?,
    lanTarget: P2pResolvedTarget?
): P2pResolvedTarget? {
    val manual = manualTargetIp.trim()
    if (manual.isNotBlank()) {
        val mode = when {
            directTarget?.ip == manual -> ConnectionMode.WIFI_DIRECT
            lanConnected -> ConnectionMode.LAN
            directTarget != null -> ConnectionMode.WIFI_DIRECT
            else -> ConnectionMode.LAN
        }
        return P2pResolvedTarget(
            peerId = transferState.knownPeers.firstOrNull { it.ip == manual }?.id,
            ip = manual,
            label = knownPeerLabel(manual, transferState) ?: directTarget?.label,
            mode = mode
        )
    }

    return directTarget ?: lanTarget
}

private fun resolveExplicitDirectChatTargets(
    selectedIps: List<String>,
    availablePeers: List<KnownPeerSnapshot>
): List<P2pResolvedTarget> {
    val selected = selectedIps
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    val resolved = when {
        selected.isNotEmpty() -> availablePeers.filter { selected.contains(it.ip) }
        availablePeers.size == 1 -> listOf(availablePeers.first())
        else -> emptyList()
    }

    return resolved.map { peer ->
        P2pResolvedTarget(
            peerId = peer.id,
            ip = peer.ip,
            label = peer.label.ifBlank { null },
            mode = ConnectionMode.WIFI_DIRECT
        )
    }
}

private fun resolveExplicitLanChatTarget(
    explicitIp: String?,
    transferState: TransferRuntimeState
): P2pResolvedTarget? {
    val normalizedIp = explicitIp?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    val peer = transferState.knownPeers.firstOrNull { it.ip == normalizedIp }
    return when {
        peer != null -> P2pResolvedTarget(
            peerId = peer.id,
            ip = peer.ip,
            label = peer.label.ifBlank { null },
            mode = ConnectionMode.LAN
        )

        knownPeerLabel(normalizedIp, transferState) != null -> P2pResolvedTarget(
            ip = normalizedIp,
            label = knownPeerLabel(normalizedIp, transferState),
            mode = ConnectionMode.LAN
        )

        else -> null
    }
}

private fun resolveGlobalChatTargets(
    lanConnected: Boolean,
    knownPeers: List<KnownPeerSnapshot>
): List<KnownPeerSnapshot> {
    if (!lanConnected) return emptyList()
    return knownPeers
        .filter { it.ip.isNotBlank() && it.globalLanJoined }
        .distinctBy { it.ip }
        .sortedWith(
            compareByDescending<KnownPeerSnapshot> { it.trusted }
                .thenByDescending { it.lastSeenAtMs }
        )
}

private fun knownPeerLabel(
    ip: String?,
    transferState: TransferRuntimeState
): String? {
    val normalized = ip?.trim().orEmpty()
    if (normalized.isBlank()) return null
    return transferState.knownPeers.firstOrNull { it.ip == normalized }?.label
        ?: transferState.lastPeerLabel?.takeIf { transferState.lastPeerIp == normalized }
        ?: transferState.lastSendTargetLabel?.takeIf { transferState.lastSendTargetIp == normalized }
}
