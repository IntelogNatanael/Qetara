package com.example.wifidrop

import com.example.wifidrop.protocol.flash.FlashApproval
import com.example.wifidrop.protocol.flash.FlashPeer
import com.example.wifidrop.protocol.flash.FlashProgress
import com.example.wifidrop.protocol.flash.FlashState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

internal enum class FlashAndroidPhase { OFF, STARTING, ACTIVE, STOPPING }
internal enum class FlashResultKind { DELIVERED, RECEIVED, FAILED, CANCELLED }

internal data class FlashAndroidResult(
    val id: String,
    val fileName: String,
    val kind: FlashResultKind,
    val detail: String,
    val file: File? = null,
    val downloadUri: String? = null,
    val confirmationIssue: Boolean = false
)

internal data class FlashAndroidState(
    val phase: FlashAndroidPhase = FlashAndroidPhase.OFF,
    val engine: FlashState? = null,
    val status: String = "Flash está desactivado.",
    val importing: Boolean = false,
    val selectedFiles: List<File> = emptyList(),
    val selectedPeer: FlashPeer? = null,
    val progress: Map<String, FlashProgress> = emptyMap(),
    val results: List<FlashAndroidResult> = emptyList(),
    val localAddresses: List<String> = emptyList(),
    val deviceLabel: String = "Android"
) {
    val active: Boolean get() = phase == FlashAndroidPhase.ACTIVE && engine?.active == true
    val selectedFile: File? get() = selectedFiles.firstOrNull()
}

/** Process-only state. Opening Flash or recreating its Activity never starts a receiver. */
internal object FlashAndroidRuntime {
    private val mutable = MutableStateFlow(FlashAndroidState())
    val state = mutable.asStateFlow()
    fun update(transform: (FlashAndroidState) -> FlashAndroidState) = mutable.update(transform)
}

/** A late worker may not publish UI state or an approval into a later activation. */
internal class FlashAndroidSessionFence {
    private var generation = 0L
    private var active = false

    @Synchronized fun activate(): Long {
        generation++
        active = true
        return generation
    }

    @Synchronized fun close() {
        active = false
        generation++
    }

    @Synchronized fun accepts(ticket: Long): Boolean = active && ticket == generation
}

internal fun resolveSelectedFlashPeer(
    selection: FlashPeer?, peers: List<FlashPeer>, nowMs: Long
): FlashPeer? = selection?.let { selected ->
    peers.firstOrNull {
        it.id == selected.id && it.address == selected.address && it.port == selected.port &&
            it.expiresAtMs > nowMs
    }
}

internal fun canAnswerFlashApproval(
    active: Boolean, requestId: String, approvals: List<FlashApproval>, nowMs: Long
): Boolean = active && approvals.any { it.requestId == requestId && it.expiresAtMs > nowMs }

/** An ACK failure after publication cannot turn a verified received file into a failed receipt. */
internal fun recordFlashFailure(
    results: List<FlashAndroidResult>, operationId: String, fileName: String, message: String, cancelled: Boolean
): List<FlashAndroidResult> {
    val received = results.firstOrNull { it.id == operationId && it.kind == FlashResultKind.RECEIVED }
    if (received != null) return results.map {
        if (it.id == operationId) it.copy(confirmationIssue = true,
            detail = "Archivo recibido y verificado. No se pudo confirmar la entrega al otro equipo.") else it
    }
    val result = FlashAndroidResult(operationId, fileName,
        if (cancelled) FlashResultKind.CANCELLED else FlashResultKind.FAILED, message)
    return (listOf(result) + results.filterNot { it.id == operationId }).take(20)
}
