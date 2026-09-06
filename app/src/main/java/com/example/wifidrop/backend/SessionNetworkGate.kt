package com.example.wifidrop.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job

/** Session closure cancels existing requests and excludes new ones until explicit activation. */
internal class SessionNetworkGate(
    private val isEnabled: () -> Boolean,
    private val persistEnabled: (Boolean) -> Unit
) {
    private val lock = Any()
    private val activeJobs = mutableSetOf<Job>()

    fun setEnabled(enabled: Boolean) {
        val cancel = synchronized(lock) {
            persistEnabled(enabled)
            if (enabled) emptyList() else activeJobs.toList()
        }
        cancel.forEach { it.cancel(CancellationException("sesion_cerrada")) }
    }

    suspend fun <T> run(operation: suspend () -> Result<T>): Result<T> = coroutineScope {
        val job = coroutineContext.job
        val admitted = synchronized(lock) {
            if (!isEnabled()) false else { activeJobs += job; true }
        }
        if (!admitted) return@coroutineScope Result.failure(IllegalStateException("sesion_cerrada"))
        try {
            val result = operation()
            coroutineContext.ensureActive()
            result
        } finally {
            synchronized(lock) { activeJobs -= job }
        }
    }
}
