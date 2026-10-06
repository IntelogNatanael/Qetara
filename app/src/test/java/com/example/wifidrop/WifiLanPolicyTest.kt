package com.example.wifidrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WifiLanPolicyTest {
    private val hotspot = WifiLanInterface("wlan1", listOf("10.144.145.254"))
    private val cellular = WifiLanNetwork("rmnet_data0", listOf("10.20.30.40"), wifi = false, cellular = true, isDefault = true)

    @Test
    fun phoneHostingWifiHotspotIsLanEvenWhenCellularIsDefault() {
        assertEquals(
            WifiLanSnapshot(true, "10.144.145.254"),
            selectWifiLanSnapshot(listOf(cellular), listOf(hotspot), setOf("wlan1"))
        )
    }

    @Test
    fun hotspotDoesNotNeedAnInternetUpstreamOrAParticularNameOrSubnet() {
        val oemHotspot = WifiLanInterface("vendor_ap7", listOf("172.20.8.1"))
        assertEquals(
            WifiLanSnapshot(true, "172.20.8.1"),
            selectWifiLanSnapshot(emptyList(), listOf(oemHotspot), setOf("vendor_ap7"))
        )
    }

    @Test
    fun privateAddressesAndWlanNamesAloneNeverProveASharedWifi() {
        val unreported = listOf("wlan1", "ap0", "rmnet_data0", "tun0", "p2p0", "bnep0", "rndis0").map {
            WifiLanInterface(it, listOf("192.168.2.1"))
        }
        assertEquals(
            WifiLanSnapshot(false, null),
            selectWifiLanSnapshot(listOf(cellular), unreported, emptySet())
        )
    }

    @Test
    fun vpnAndCellularAreExcludedEvenWhenTheyAlsoReportWifiTransport() {
        val candidates = listOf(
            WifiLanNetwork("tun0", listOf("10.8.0.1"), wifi = true, vpn = true),
            WifiLanNetwork("rmnet_data0", listOf("10.20.30.40"), wifi = true, cellular = true)
        )
        assertEquals(WifiLanSnapshot(false, null), selectWifiLanSnapshot(candidates, emptyList(), emptySet()))
    }

    @Test
    fun physicalWifiRemainsAvailableWhenVpnIsDefault() {
        val vpn = WifiLanNetwork("tun0", listOf("10.8.0.1"), wifi = true, vpn = true, isDefault = true)
        val wifi = WifiLanNetwork("wlan0", listOf("192.168.1.20"), wifi = true)
        assertEquals(
            WifiLanSnapshot(true, "192.168.1.20"),
            selectWifiLanSnapshot(listOf(vpn, wifi), emptyList(), emptySet())
        )
    }

    @Test
    fun connectedClientWifiKeepsPriorityOverHotspot() {
        val secondary = WifiLanNetwork("wlan2", listOf("192.168.2.20"), wifi = true)
        val primary = WifiLanNetwork("wlan0", listOf("192.168.1.20"), wifi = true, isDefault = true)
        assertEquals(
            WifiLanSnapshot(true, "192.168.1.20"),
            selectWifiLanSnapshot(listOf(secondary, primary), listOf(hotspot), setOf("wlan1"))
        )
    }

    @Test
    fun directGroupCannotQualifyAsLanIncludingAnOemWlanName() {
        for (name in listOf("p2p0", "p2p-wlan0-0", "wlan1")) {
            val excluded = if (name == "wlan1") setOf(name) else emptySet()
            assertEquals(
                name,
                WifiLanSnapshot(false, null),
                selectWifiLanSnapshot(
                    listOf(WifiLanNetwork(name, listOf("192.168.49.1"), wifi = true)),
                    listOf(WifiLanInterface(name, listOf("192.168.49.1"))),
                    setOf(name),
                    excluded
                )
            )
        }
    }

    @Test
    fun removingHotspotReportRejectsAnInterfaceWhoseOldIpIsStillVisible() {
        assertEquals(
            WifiLanSnapshot(false, null),
            selectWifiLanSnapshot(listOf(cellular), listOf(hotspot), emptySet())
        )
    }

    @Test
    fun staleHotspotReportDoesNotAdmitMissingDownLoopbackOrPointToPointInterface() {
        val observations = listOf(
            emptyList(),
            listOf(hotspot.copy(up = false)),
            listOf(hotspot.copy(loopback = true)),
            listOf(hotspot.copy(pointToPoint = true)),
            listOf(hotspot.copy(ipv4Addresses = emptyList()))
        )
        observations.forEach { interfaces ->
            assertEquals(WifiLanSnapshot(false, null), selectWifiLanSnapshot(emptyList(), interfaces, setOf("wlan1")))
        }
    }

    @Test
    fun currentHotspotAddressReplacesThePreviousSubnet() {
        val changed = hotspot.copy(ipv4Addresses = listOf("192.168.17.1"))
        assertEquals(
            WifiLanSnapshot(true, "192.168.17.1"),
            selectWifiLanSnapshot(emptyList(), listOf(changed), setOf("wlan1"))
        )
    }

    @Test
    fun hotspotNeedsAUsableIpv4AndSkipsNonUnicastAddresses() {
        val invalid = listOf("", "0.0.0.0", "127.0.0.1", "127.2.3.4", "169.254.1.1", "224.0.0.1", "255.255.255.255", "::1", "fe80::1", "10.0.1.256", "qetara.local")
        invalid.forEach { address ->
            assertFalse(address, selectWifiLanSnapshot(emptyList(), listOf(hotspot.copy(ipv4Addresses = listOf(address))), setOf("wlan1")).connected)
        }
        assertEquals(
            WifiLanSnapshot(true, "10.144.145.254"),
            selectWifiLanSnapshot(emptyList(), listOf(hotspot.copy(ipv4Addresses = invalid + "10.144.145.254")), setOf("wlan1"))
        )
    }

    @Test
    fun ipv6OnlyClientKeepsPreviousConnectedStateButDoesNotMaskIpv4Hotspot() {
        val wifi = WifiLanNetwork("wlan0", emptyList(), wifi = true)
        assertEquals(WifiLanSnapshot(true, null), selectWifiLanSnapshot(listOf(wifi), emptyList(), emptySet()))
        assertEquals(
            WifiLanSnapshot(true, "10.144.145.254"),
            selectWifiLanSnapshot(listOf(wifi), listOf(hotspot), setOf("wlan1"))
        )
    }

    @Test
    fun wifiCapabilitiesWithoutCurrentLinkPropertiesDoNotProveALan() {
        assertEquals(
            WifiLanSnapshot(false, null),
            selectWifiLanSnapshot(listOf(WifiLanNetwork(null, emptyList(), wifi = true)), emptyList(), emptySet())
        )
    }
}
