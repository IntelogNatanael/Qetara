package com.example.wifidrop.presentation

import com.example.wifidrop.TransferRuntimeState

/** Identity is authoritative: one device can be reachable through several local interfaces. */
fun withoutLocalDiscovery(
    transferState: TransferRuntimeState,
    localDeviceId: String?,
    localIpv4Addresses: Set<String>
): TransferRuntimeState {
    val excludedAddresses = localDiscoveryAddresses(transferState, localDeviceId, localIpv4Addresses)
    val filteredPeers = transferState.knownPeers.filter {
        (localDeviceId.isNullOrBlank() || it.id != localDeviceId) && it.ip !in excludedAddresses
    }
    val lastPeerIsLocal = transferState.lastPeerIp in excludedAddresses
    return transferState.copy(
        knownPeers = filteredPeers,
        lastPeerIp = transferState.lastPeerIp.takeUnless { lastPeerIsLocal },
        lastPeerLabel = transferState.lastPeerLabel.takeUnless { lastPeerIsLocal }
    )
}

fun localDiscoveryAddresses(
    transferState: TransferRuntimeState,
    localDeviceId: String?,
    localIpv4Addresses: Set<String>
): Set<String> = localIpv4Addresses + transferState.knownPeers
    .filter { !localDeviceId.isNullOrBlank() && it.id == localDeviceId }
    .map { it.ip }
