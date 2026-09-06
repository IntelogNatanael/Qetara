package com.example.wifidrop.presentation

import kotlinx.coroutines.sync.Mutex

internal data class P2pSessionSyncEndpoint(
    // The route key includes LAN/Direct and the current network or Direct group.
    val networkKey: String,
    val hostIp: String
)

internal data class P2pSessionSyncAttempt(
    val synced: Boolean,
    val requiresUserAction: Boolean = false
)

/** A manual action and discovery can select the same peer in the same frame. */
internal class P2pSessionSyncCoordinator {
    private val requestMutex = Mutex()
    private var terminalEndpoint: P2pSessionSyncEndpoint? = null

    suspend fun syncEndpointOnce(
        endpoint: P2pSessionSyncEndpoint,
        manual: Boolean,
        request: suspend () -> P2pSessionSyncAttempt
    ): P2pSessionSyncAttempt {
        var attempt = P2pSessionSyncAttempt(synced = false)
        syncOnce {
            if (!manual && terminalEndpoint == endpoint) {
                attempt = P2pSessionSyncAttempt(synced = false, requiresUserAction = true)
                return@syncOnce false
            }
            // Discovery identity and typed credentials do not identify a new endpoint.
            terminalEndpoint = null
            attempt = request()
            if (attempt.requiresUserAction) terminalEndpoint = endpoint
            attempt.synced
        }
        return attempt
    }

    suspend fun syncOnce(request: suspend () -> Boolean): Boolean {
        if (!requestMutex.tryLock()) return false
        return try {
            request()
        } finally {
            requestMutex.unlock()
        }
    }
}
