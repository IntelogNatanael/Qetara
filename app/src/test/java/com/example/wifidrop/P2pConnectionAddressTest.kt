package com.example.wifidrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pConnectionAddressTest {
    @Test
    fun acceptsIpv4ShownByOtherQetaraDevices() {
        listOf("192.168.1.8", "10.0.2.2", "172.20.10.4", " 192.168.0.23 ").forEach { assertTrue(it, isValidManualConnectionAddress(it)) }
    }

    @Test
    fun rejectsMissingOctetsInvalidRangesAndNonAddresses() {
        listOf("", "192.168.1", "192.168.1.256", "192.168.-1.2", "qetara.local", "https://192.168.1.2", "0.0.0.0", "255.255.255.255", "192.168.1.2:8787").forEach {
            assertFalse(it, isValidManualConnectionAddress(it))
        }
    }
}
