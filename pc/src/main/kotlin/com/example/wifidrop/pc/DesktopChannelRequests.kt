package com.example.wifidrop.pc

internal data class DesktopChannelOfferKey(val peerAddress: String, val offerId: String)

internal enum class DesktopChannelRequestPhase { QUEUED, REQUESTING, REQUESTED, MANUAL }

internal data class DesktopChannelRequestStatus(val phase: DesktopChannelRequestPhase, val detail: String)

internal data class DesktopChannelRequestWork<T>(
    val key: DesktopChannelOfferKey,
    val value: T,
    val automatic: Boolean,
    val generation: Long,
    val cancellation: DesktopTransferCancellation = DesktopTransferCancellation()
)

internal enum class DesktopChannelEnqueueResult { QUEUED, DUPLICATE, FULL }

/**
 * Serializes request handshakes, not file transfers: a request ACK does not prove delivery.
 * Owned by the UI thread. Revocation retains the occupied slot until its worker exits.
 */
internal class DesktopChannelRequestQueue<T>(private val capacity: Int = 32, private val historyCapacity: Int = 200) {
    private val pending = LinkedHashMap<DesktopChannelOfferKey, DesktopChannelRequestWork<T>>()
    private val statuses = LinkedHashMap<DesktopChannelOfferKey, DesktopChannelRequestStatus>()
    private var active: DesktopChannelRequestWork<T>? = null
    private var generation = 0L

    init {
        require(capacity > 0 && historyCapacity >= capacity + 1)
    }

    val hasActivity: Boolean get() = active != null || pending.isNotEmpty()
    val pendingCount: Int get() = pending.size
    fun snapshot(): Map<DesktopChannelOfferKey, DesktopChannelRequestStatus> = statuses.toMap()

    fun enqueue(key: DesktopChannelOfferKey, value: T, automatic: Boolean): DesktopChannelEnqueueResult {
        if (active?.key == key || key in pending || (automatic && key in statuses)) {
            return DesktopChannelEnqueueResult.DUPLICATE
        }
        if (pending.size >= capacity) {
            record(key, DesktopChannelRequestPhase.MANUAL, "Cola llena. Usa Descargar cuando termine una solicitud.")
            return DesktopChannelEnqueueResult.FULL
        }
        pending[key] = DesktopChannelRequestWork(key, value, automatic, generation)
        record(key, DesktopChannelRequestPhase.QUEUED, "Solicitud en cola.")
        return DesktopChannelEnqueueResult.QUEUED
    }

    fun next(): DesktopChannelRequestWork<T>? {
        if (active != null) return null
        val key = pending.keys.firstOrNull() ?: return null
        return pending.remove(key)!!.also {
            active = it
            record(key, DesktopChannelRequestPhase.REQUESTING, "Solicitando el archivo…")
        }
    }

    /** Returns false for a late/cancelled completion; callers may still drain new eligible work. */
    fun complete(work: DesktopChannelRequestWork<T>, error: String? = null): Boolean {
        if (active !== work) return false
        active = null
        if (work.generation != generation || work.cancellation.isCancelled) return false
        if (error == null) {
            record(work.key, DesktopChannelRequestPhase.REQUESTED, "Solicitud aceptada. La recepción se confirma en Recibir.")
        } else {
            record(work.key, DesktopChannelRequestPhase.MANUAL, "$error Usa Descargar para volver a intentarlo.")
        }
        return true
    }

    fun cancelAutomatic() {
        val reason = "Descarga automática desactivada. Puedes solicitar el archivo con Descargar."
        val keys = pending.filterValues { it.automatic }.keys.toList()
        keys.forEach { key ->
            pending.remove(key)
            record(key, DesktopChannelRequestPhase.MANUAL, reason)
        }
        active?.takeIf { it.automatic }?.let {
            it.cancellation.cancel()
            record(it.key, DesktopChannelRequestPhase.MANUAL, reason)
        }
    }

    fun revoke(reason: String) {
        generation++
        val keys = pending.keys.toList()
        pending.clear()
        keys.forEach { record(it, DesktopChannelRequestPhase.MANUAL, reason) }
        active?.let {
            it.cancellation.cancel()
            record(it.key, DesktopChannelRequestPhase.MANUAL, reason)
        }
    }

    private fun record(key: DesktopChannelOfferKey, phase: DesktopChannelRequestPhase, detail: String) {
        statuses.remove(key)
        statuses[key] = DesktopChannelRequestStatus(phase, detail)
        while (statuses.size > historyCapacity) {
            val expired = statuses.keys.firstOrNull { it !in pending && it != active?.key } ?: break
            statuses.remove(expired)
        }
    }
}

internal fun desktopChannelPublicationIssue(
    scope: DesktopChatScope, hasAttachment: Boolean, receiverActive: Boolean
): String? = if (scope == DesktopChatScope.GLOBAL_LAN && hasAttachment && !receiverActive) {
    "Para publicar un archivo, activa Recibir con una sesión vigente. Puedes quitar el adjunto y enviar sólo el texto."
} else null

internal fun desktopExitNeedsConfirmation(
    otherOperationActive: Boolean,
    channelUploads: DesktopTransferSlot,
    channelRequests: DesktopChannelRequestQueue<*>
): Boolean = otherOperationActive || channelUploads.hasActive || channelRequests.hasActivity
