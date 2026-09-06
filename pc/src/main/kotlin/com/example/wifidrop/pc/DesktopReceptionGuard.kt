package com.example.wifidrop.pc

import java.lang.reflect.InvocationTargetException
import javax.swing.SwingUtilities

/** A callback must still belong to the visible receiving session when it executes. */
internal data class DesktopMessageReception(
    val generation: Int,
    val receiving: Boolean,
    val expiresAtMs: Long?,
    val channelJoined: Boolean
) {
    fun isActiveFor(expectedGeneration: Int, nowMs: Long = System.currentTimeMillis()): Boolean =
        generation == expectedGeneration && receiving && expiresAtMs != null && nowMs < expiresAtMs

    fun requireDelivery(expectedGeneration: Int, scope: DesktopChatScope, nowMs: Long = System.currentTimeMillis()) {
        check(isActiveFor(expectedGeneration, nowMs)) { "sesion_expirada_o_detenida" }
        check(scope != DesktopChatScope.GLOBAL_LAN || channelJoined) { "canal_no_unido" }
    }
}

/** Keep the secure ACK and duplicate receipt after UI acceptance, including file requests. */
internal fun deliverDesktopMessageOnUi(deliver: () -> Unit) {
    if (SwingUtilities.isEventDispatchThread()) {
        deliver()
    } else {
        try {
            SwingUtilities.invokeAndWait(deliver)
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }
}

/** Selection changes never free a running operation; cancellation keeps its slot until the worker finishes. */
internal class DesktopTransferSlot {
    private var active: DesktopTransferCancellation? = null

    @Synchronized
    fun tryAcquire(): DesktopTransferCancellation? {
        if (active != null) return null
        return DesktopTransferCancellation().also { active = it }
    }

    fun cancel() {
        val current = synchronized(this) { active }
        current?.cancel()
    }

    @Synchronized
    fun isCurrent(cancellation: DesktopTransferCancellation): Boolean = active === cancellation

    @Synchronized
    fun release(cancellation: DesktopTransferCancellation) {
        if (active === cancellation) active = null
    }
}

internal fun desktopIdentityFingerprint(publicKey: ByteArray): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(publicKey)
        .take(6).joinToString("") { "%02x".format(it) }.chunked(4).joinToString(" ")
