package com.example.wifidrop.protocol.flash

/**
 * Commit and its receipt notification precede any subsequent stopped-state notification.
 * Always acquire events before state, matching snapshot callbacks. Never call UI under the state lock.
 */
internal fun <T> flashPublishAndNotify(
    events: Any, state: Any, publish: () -> T, received: (T) -> Unit
): T = synchronized(events) {
    val result = synchronized(state) { publish() }
    received(result)
    result
}
