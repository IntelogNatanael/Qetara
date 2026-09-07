package com.example.wifidrop.pc

import com.example.wifidrop.protocol.flash.FlashProgress

/** The network may report every chunk; the EDT drains at most one update per operation per tick. */
internal class DesktopFlashProgressBuffer {
    private val latest = LinkedHashMap<Pair<Long, String>, FlashProgress>()

    @Synchronized
    fun offer(generation: Long, progress: FlashProgress) {
        latest[generation to progress.operationId] = progress
    }

    @Synchronized
    fun drain(): List<Pair<Long, FlashProgress>> {
        val result = latest.map { (key, progress) -> key.first to progress }
        latest.clear()
        return result
    }

    @Synchronized
    fun clear() = latest.clear()
}
