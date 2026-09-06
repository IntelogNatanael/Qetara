package com.example.wifidrop.presentation

import kotlinx.coroutines.sync.Mutex

/** A manual action and discovery can select the same peer in the same frame. */
internal class P2pSessionSyncCoordinator {
    private val requestMutex = Mutex()

    suspend fun syncOnce(request: suspend () -> Boolean): Boolean {
        if (!requestMutex.tryLock()) return false
        return try {
            request()
        } finally {
            requestMutex.unlock()
        }
    }
}
