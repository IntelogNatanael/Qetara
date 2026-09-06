package com.example.wifidrop

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class SessionLifecycleTicket(val generation: Long, val startId: Int)

/** Orders accepted service commands without letting a previous STOP terminate a later START. */
internal class SessionLifecycleFence {
    private var generation = 0L
    private var latestStartId = 0
    private var stopped = true

    @Synchronized fun recordCommand(startId: Int) { latestStartId = maxOf(latestStartId, startId) }
    @Synchronized fun beginStart(): SessionLifecycleTicket {
        stopped = false
        return SessionLifecycleTicket(++generation, latestStartId)
    }
    @Synchronized fun beginStop(): SessionLifecycleTicket {
        stopped = true
        return SessionLifecycleTicket(++generation, latestStartId)
    }
    @Synchronized fun canActivate(ticket: SessionLifecycleTicket): Boolean =
        !stopped && generation == ticket.generation
    @Synchronized fun stopStartId(ticket: SessionLifecycleTicket): Int? =
        ticket.startId.takeIf { stopped && generation == ticket.generation && it > 0 && latestStartId == it }
}

/** Joining cancelled workers includes their finally blocks and synchronous receive callbacks. */
internal suspend fun awaitSessionWorkers(
    workers: Collection<Job>, fence: SessionLifecycleFence, ticket: SessionLifecycleTicket
): Boolean {
    workers.distinct().forEach { it.join() }
    currentCoroutineContext().ensureActive()
    return fence.canActivate(ticket)
}

/**
 * Recheck closure while holding the queue lock, then create the child and register its callbacks.
 * STOP snapshots children under these same locks, so it either drains the child or rejects it.
 */
internal inline fun <T> withSessionWorkerAdmission(
    lock: Any, isClosing: () -> Boolean, create: () -> T
): T? = synchronized(lock) {
    if (isClosing()) null else create()
}
