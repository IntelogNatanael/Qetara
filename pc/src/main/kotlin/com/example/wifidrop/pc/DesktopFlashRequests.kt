package com.example.wifidrop.pc

import com.example.wifidrop.protocol.flash.FlashApproval

/** Binds a visible decision to the complete request and the activation that displayed it. */
internal class DesktopFlashRequests(private val now: () -> Long = System::currentTimeMillis) {
    private var generation = 0L
    private var active = false
    private val pending = LinkedHashMap<String, FlashApproval>()
    private val decided = HashSet<FlashApproval>()

    @Synchronized
    fun begin(): Long {
        generation++
        active = true
        pending.clear()
        decided.clear()
        return generation
    }

    @Synchronized
    fun invalidate() {
        generation++
        active = false
        pending.clear()
        decided.clear()
    }

    @Synchronized
    fun isCurrent(token: Long): Boolean = active && token == generation

    @Synchronized
    fun update(token: Long, approvals: List<FlashApproval>): List<FlashApproval> {
        if (!isCurrent(token)) return emptyList()
        pending.clear()
        approvals.filter { it.expiresAtMs > now() && it !in decided }.forEach { pending[it.requestId] = it }
        return pending.values.sortedBy { it.expiresAtMs }
    }

    @Synchronized
    fun decide(token: Long, displayed: FlashApproval): Boolean {
        if (!isCurrent(token) || displayed.expiresAtMs <= now() || pending[displayed.requestId] != displayed) return false
        pending.remove(displayed.requestId)
        decided.add(displayed)
        return true
    }
}
