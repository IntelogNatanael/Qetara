package com.example.wifidrop

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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

/** Keep copy unresolved while the service survives an Activity language change. */
internal data class FlashText(
    val resourceId: Int = 0,
    val arguments: List<Any> = emptyList(),
    val quantity: Int? = null,
    val literal: String? = null
) {
    fun resolve(): String = literal ?: quantity?.let {
        appQuantityString(resourceId, it, *arguments.toTypedArray())
    } ?: appString(resourceId, *arguments.toTypedArray())

    @Composable
    fun localized(): String = literal ?: quantity?.let {
        pluralStringResource(resourceId, it, *arguments.toTypedArray())
    } ?: stringResource(resourceId, *arguments.toTypedArray())
}

internal fun flashText(@StringRes id: Int, vararg arguments: Any) =
    FlashText(resourceId = id, arguments = arguments.toList())

internal fun flashPlural(@PluralsRes id: Int, quantity: Int, vararg arguments: Any) =
    FlashText(resourceId = id, arguments = arguments.toList(), quantity = quantity)

internal data class FlashAndroidResult(
    val id: String,
    val fileName: String,
    val kind: FlashResultKind,
    val detailText: FlashText,
    val file: File? = null,
    val downloadUri: String? = null,
    val confirmationIssue: Boolean = false
) {
    val detail: String get() = detailText.resolve()

    constructor(id: String, fileName: String, kind: FlashResultKind, detail: String,
                file: File? = null, downloadUri: String? = null, confirmationIssue: Boolean = false) :
        this(id, fileName, kind, FlashText(literal = detail), file, downloadUri, confirmationIssue)
}

internal data class FlashAndroidState(
    val phase: FlashAndroidPhase = FlashAndroidPhase.OFF,
    val engine: FlashState? = null,
    val statusText: FlashText = flashText(R.string.flash_off),
    val importing: Boolean = false,
    val selectedFiles: List<File> = emptyList(),
    val selectedPeer: FlashPeer? = null,
    val progress: Map<String, FlashProgress> = emptyMap(),
    val results: List<FlashAndroidResult> = emptyList(),
    val localAddresses: List<String> = emptyList(),
    val deviceLabel: String = "Android"
) {
    val status: String get() = statusText.resolve()
    val active: Boolean get() = phase == FlashAndroidPhase.ACTIVE && engine?.active == true
    val selectedFile: File? get() = selectedFiles.firstOrNull()

    constructor(phase: FlashAndroidPhase = FlashAndroidPhase.OFF, engine: FlashState? = null,
                status: String, importing: Boolean = false, selectedFiles: List<File> = emptyList(),
                selectedPeer: FlashPeer? = null, progress: Map<String, FlashProgress> = emptyMap(),
                results: List<FlashAndroidResult> = emptyList(), localAddresses: List<String> = emptyList(),
                deviceLabel: String = "Android") :
        this(phase, engine, FlashText(literal = status), importing, selectedFiles, selectedPeer,
            progress, results, localAddresses, deviceLabel)
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

/** A later batch failure cannot undo a verified local receipt or a confirmed remote delivery. */
internal fun recordFlashFailure(
    results: List<FlashAndroidResult>, operationId: String, fileName: String, message: FlashText, cancelled: Boolean
): List<FlashAndroidResult> {
    if (results.any { it.id == operationId && it.kind == FlashResultKind.DELIVERED }) return results
    val received = results.firstOrNull { it.id == operationId && it.kind == FlashResultKind.RECEIVED }
    if (received != null) return results.map {
        if (it.id == operationId) it.copy(confirmationIssue = true,
            detailText = flashText(R.string.flash_received_unconfirmed)) else it
    }
    val result = FlashAndroidResult(operationId, fileName,
        if (cancelled) FlashResultKind.CANCELLED else FlashResultKind.FAILED, message)
    return (listOf(result) + results.filterNot { it.id == operationId }).take(20)
}

internal fun recordFlashFailure(
    results: List<FlashAndroidResult>, operationId: String, fileName: String, message: String, cancelled: Boolean
): List<FlashAndroidResult> = recordFlashFailure(results, operationId, fileName, FlashText(literal = message), cancelled)
