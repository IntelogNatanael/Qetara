package com.example.wifidrop.presentation

import com.example.wifidrop.PendingCredentialShareRequest
import com.example.wifidrop.backend.P2pBackend
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class P2pTrustPresenterTest : com.example.wifidrop.LocalizedResourcesTest() {
    @Test
    fun approvalCarriesTheExactDisplayedRequestProofInsteadOfOnlyItsPeerId() {
        var approved: List<Any?>? = null
        val backend = Proxy.newProxyInstance(
            P2pBackend::class.java.classLoader,
            arrayOf(P2pBackend::class.java)
        ) { _, method, args ->
            if (method.name == "approveCredentialShare") approved = args?.toList()
            null
        } as P2pBackend
        val displayed = PendingCredentialShareRequest(
            id = "same-peer-id", label = "Android A", ip = "192.168.1.8",
            requestedAtMs = 10_000L, noiseStaticKey = "displayed-key-A"
        )

        val feedback = P2pTrustPresenter(backend).approveCredentialShare(displayed)

        assertEquals(listOf("same-peer-id", "Android A", "displayed-key-A", 10_000L), approved)
        // Only actual service acceptance dismisses the request; a stale click must not announce success.
        assertNull(feedback)
    }
}
