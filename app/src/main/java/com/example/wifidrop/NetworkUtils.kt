package com.example.wifidrop

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

data class WifiLanSnapshot(
    val connected: Boolean,
    val ipv4: String?
)

object NetworkUtils {

    fun currentWifiLanSnapshot(context: Context): WifiLanSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return WifiLanSnapshot(connected = false, ipv4 = null)

        val active = cm.activeNetwork ?: return WifiLanSnapshot(connected = false, ipv4 = null)
        val capabilities = cm.getNetworkCapabilities(active)
            ?: return WifiLanSnapshot(connected = false, ipv4 = null)

        val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        if (!hasWifi) return WifiLanSnapshot(connected = false, ipv4 = null)

        val linkProperties = cm.getLinkProperties(active)
        val ipv4 = linkProperties
            ?.linkAddresses
            ?.asSequence()
            ?.mapNotNull { it.address as? Inet4Address }
            ?.map { it.hostAddress.orEmpty() }
            ?.firstOrNull { it.isNotBlank() && it != "127.0.0.1" }

        return WifiLanSnapshot(connected = true, ipv4 = ipv4)
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
