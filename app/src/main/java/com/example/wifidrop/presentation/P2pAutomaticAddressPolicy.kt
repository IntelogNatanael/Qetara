package com.example.wifidrop.presentation

/** LAN discovery is IPv4. Loopback, wildcard, multicast and this device are never auto-selected. */
fun isAutomaticConnectionAddress(address: String?, localAddress: String? = null): Boolean {
    val ip = address?.trim().orEmpty()
    if (ip.isEmpty() || ip == localAddress?.trim()) return false
    val parts = ip.split('.')
    if (parts.size != 4 || parts.any { it.isEmpty() || it.any { char -> char !in '0'..'9' } }) return false
    val octets = parts.map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    return octets.first() in 1..223 && octets.first() != 127
}
