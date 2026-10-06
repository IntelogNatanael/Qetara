package com.example.wifidrop

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    fun currentWifiLanSnapshot(
        context: Context,
        wifiHotspotInterfaceNames: Set<String> = emptySet(),
        excludedInterfaceNames: Set<String> = emptySet()
    ): WifiLanSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val networks = runCatching {
            val active = cm?.activeNetwork
            // This presenter already refreshes snapshots. Include non-default Wi-Fi when
            // Android selects cellular/VPN as its Internet network; do not require Internet.
            @Suppress("DEPRECATION")
            val available = cm?.allNetworks?.toList().orEmpty()
            (listOfNotNull(active) + available).distinct().mapNotNull { network ->
                val capabilities = cm?.getNetworkCapabilities(network) ?: return@mapNotNull null
                val link = cm.getLinkProperties(network)
                WifiLanNetwork(
                    interfaceName = link?.interfaceName,
                    ipv4Addresses = link?.linkAddresses.orEmpty()
                        .mapNotNull { (it.address as? Inet4Address)?.hostAddress },
                    wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                    vpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                    cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                    isDefault = network == active
                )
            }
        }.getOrDefault(emptyList())
        val hotspotInterfaces = wifiHotspotInterfaceNames.mapNotNull { name ->
            runCatching {
                NetworkInterface.getByName(name)?.let { network ->
                    WifiLanInterface(
                        name = network.name,
                        ipv4Addresses = network.inetAddresses.toList()
                            .filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress },
                        up = network.isUp,
                        loopback = network.isLoopback,
                        pointToPoint = network.isPointToPoint
                    )
                }
            }.getOrNull()
        }
        return selectWifiLanSnapshot(networks, hotspotInterfaces, wifiHotspotInterfaceNames, excludedInterfaceNames)
    }

    fun subnetCandidates(localIpv4: String): List<String> {
        val trimmed = localIpv4.trim()
        val prefix = trimmed.substringBeforeLast(".", "")
        val own = trimmed.substringAfterLast(".", "").toIntOrNull()
        if (prefix.isBlank()) return emptyList()

        return (1..254)
            .asSequence()
            .filter { own == null || it != own }
            .map { "$prefix.$it" }
            .toList()
    }
}
