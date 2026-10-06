package com.example.wifidrop

data class WifiLanSnapshot(
    val connected: Boolean,
    val ipv4: String?
)

/** A ConnectivityManager network; a VPN can report Wi-Fi as an underlying transport. */
internal data class WifiLanNetwork(
    val interfaceName: String?,
    val ipv4Addresses: List<String>,
    val wifi: Boolean,
    val vpn: Boolean = false,
    val cellular: Boolean = false,
    val isDefault: Boolean = false
)

internal data class WifiLanInterface(
    val name: String,
    val ipv4Addresses: List<String>,
    val up: Boolean = true,
    val loopback: Boolean = false,
    val pointToPoint: Boolean = false
)

/**
 * A private IP or an interface named wlan is not proof of a shared Wi-Fi network.
 * Only platform Wi-Fi networks or platform-reported Wi-Fi tethering interfaces qualify.
 */
internal fun selectWifiLanSnapshot(
    networks: List<WifiLanNetwork>,
    interfaces: List<WifiLanInterface>,
    wifiHotspotInterfaceNames: Set<String>,
    excludedInterfaceNames: Set<String> = emptySet()
): WifiLanSnapshot {
    fun allowedInterface(name: String?): Boolean =
        !name.isNullOrBlank() && name !in excludedInterfaceNames && !name.contains("p2p", ignoreCase = true)

    val wifiNetworks = networks.filter {
        it.wifi && !it.vpn && !it.cellular && allowedInterface(it.interfaceName)
    }.sortedByDescending { it.isDefault }

    // Preserve the existing preference for a connected Wi-Fi client when both roles exist.
    wifiNetworks.firstNotNullOfOrNull { network ->
        network.ipv4Addresses.firstOrNull(::isUsableLanIpv4)
    }?.let { return WifiLanSnapshot(connected = true, ipv4 = it) }

    interfaces.asSequence()
        .filter {
            it.name in wifiHotspotInterfaceNames && allowedInterface(it.name) &&
                it.up && !it.loopback && !it.pointToPoint
        }
        .sortedBy { it.name }
        .mapNotNull { it.ipv4Addresses.firstOrNull(::isUsableLanIpv4) }
        .firstOrNull()
        ?.let { return WifiLanSnapshot(connected = true, ipv4 = it) }

    // IPv6-only client Wi-Fi retains its previous connected state; IPv4 scanning waits.
    return WifiLanSnapshot(connected = wifiNetworks.isNotEmpty(), ipv4 = null)
}

private fun isUsableLanIpv4(value: String): Boolean {
    val parts = value.split('.')
    if (parts.size != 4) return false
    val octets = parts.map { part ->
        if (part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' }) return false
        part.toIntOrNull()?.takeIf { it in 0..255 } ?: return false
    }
    return octets[0] in 1..223 && octets[0] != 127 &&
        !(octets[0] == 169 && octets[1] == 254)
}
