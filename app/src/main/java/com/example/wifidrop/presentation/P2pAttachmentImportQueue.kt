package com.example.wifidrop.presentation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Main-thread coordinator: ordered additions, with clear/send/replace discarding outstanding work. */
internal class P2pAttachmentImportQueue {
    private class Generation(val mutex: Mutex = Mutex())
    private var generation = Generation()
    private var revision = 0L

    @Synchronized
    fun replace(): Long {
        // New work must not wait for a discarded provider read to finish.
        generation = Generation()
        return ++revision
    }

    @Synchronized
    fun isCurrentReplacement(ticket: Long): Boolean = revision == ticket

    @Synchronized
    private fun beginAddition(): Generation {
        // A new picker action supersedes an older restore/share, but not another addition.
        revision += 1
        return generation
    }

    @Synchronized
    private fun accepts(ticket: Generation): Boolean = generation === ticket

    suspend fun <T> append(load: suspend () -> T, commit: (T) -> Unit) {
        val ticket = beginAddition()
        ticket.mutex.withLock {
            if (!accepts(ticket)) return@withLock
            val loaded = load()
            if (accepts(ticket)) commit(loaded)
        }
    }
}
