package com.example.wifidrop.presentation

import com.example.wifidrop.FileTransfer
import com.example.wifidrop.TransferSecurity

data class P2pRestoredSession(val token: String, val pin: String, val expiresAtMs: Long)

fun saveSessionForRecreation(state: P2pSessionState): ArrayList<String> =
    arrayListOf("1", state.token, state.pin, state.expiresAtMs.toString())

fun restoreSessionAfterProcessDeath(values: List<String>?, nowMs: Long): P2pRestoredSession? {
    if (values == null || values.size != 4 || values[0] != "1") return null
    val expiresAtMs = values[3].toLongOrNull() ?: return null
    if (!FileTransfer.isValidToken(values[1]) || !TransferSecurity.isValidPin(values[2]) ||
        expiresAtMs <= 0L || expiresAtMs - nowMs > 24 * 60 * 60 * 1000L) return null
    // Keep an expired session expired; recreating the Activity must not renew it.
    // Connection proof is deliberately absent: the restored UI must verify the current destination.
    return P2pRestoredSession(values[1], values[2], expiresAtMs)
}

data class P2pSavedAttachment(val uri: String, val name: String)

data class P2pSavedAttachments(
    val activeContext: P2pAttachmentContext = P2pAttachmentContext.FILES,
    val incomingShareEventId: Long? = null,
    val files: Map<P2pAttachmentContext, List<P2pSavedAttachment>> = emptyMap(),
    val omittedCount: Int = 0
)

private const val MAX_SAVED_ATTACHMENT_CHARACTERS = 64_000
private const val MAX_SAVED_ATTACHMENTS = 300

/** Keep the Activity saved-state payload bounded; do not put file bytes in the Bundle. */
fun saveAttachmentsForRecreation(snapshot: P2pSavedAttachments): ArrayList<String> {
    val values = arrayListOf("1", snapshot.activeContext.name, snapshot.incomingShareEventId?.toString().orEmpty(), "0")
    var characters = values.sumOf { it.length }
    var count = 0
    var omitted = snapshot.omittedCount.coerceAtLeast(0)
    P2pAttachmentContext.entries.forEach { context ->
        snapshot.files[context].orEmpty().distinctBy { it.uri }.forEach { item ->
            val recordSize = context.name.length + item.uri.length + item.name.length
            if (!isRestorableContentUri(item.uri) || item.name.length > 2_048 ||
                count >= MAX_SAVED_ATTACHMENTS || characters + recordSize > MAX_SAVED_ATTACHMENT_CHARACTERS) {
                omitted++
            } else {
                values += context.name
                values += item.uri
                values += item.name
                characters += recordSize
                count++
            }
        }
    }
    values[3] = omitted.toString()
    return values
}

fun restoreSavedAttachments(values: List<String>?): P2pSavedAttachments {
    if (values == null || values.size < 4 || values[0] != "1" || (values.size - 4) % 3 != 0 ||
        values.size > 4 + MAX_SAVED_ATTACHMENTS * 3 ||
        values.sumOf { it.length.toLong() } > MAX_SAVED_ATTACHMENT_CHARACTERS + 64L) return P2pSavedAttachments()
    val context = P2pAttachmentContext.entries.firstOrNull { it.name == values[1] } ?: P2pAttachmentContext.FILES
    val incomingEventId = values[2].toLongOrNull()?.takeIf { it > 0L }
    val files = mutableMapOf<P2pAttachmentContext, MutableList<P2pSavedAttachment>>()
    var omitted = values[3].toIntOrNull()?.coerceIn(0, 100_000) ?: 0
    values.drop(4).chunked(3).forEach { record ->
        val bank = P2pAttachmentContext.entries.firstOrNull { it.name == record[0] }
        if (bank == null || !isRestorableContentUri(record[1]) || record[2].length > 2_048) {
            omitted++
        } else {
            val bucket = files.getOrPut(bank) { mutableListOf() }
            if (bucket.none { it.uri == record[1] }) bucket += P2pSavedAttachment(record[1], record[2])
        }
    }
    return P2pSavedAttachments(context, incomingEventId, files, omitted)
}

fun isRestorableContentUri(value: String): Boolean {
    if (!value.startsWith("content://") || value.length > 8_192) return false
    val authority = value.removePrefix("content://").substringBefore('/').substringBefore('?').substringBefore('#')
    return authority.isNotBlank() && authority.none { it.isWhitespace() }
}

fun shouldConsumeIncomingShare(eventId: Long, lastConsumedEventId: Long?): Boolean =
    eventId > 0L && eventId != lastConsumedEventId

fun shouldNavigateToIncomingShare(eventId: Long?, lastHandledEventId: Long?): Boolean =
    eventId != null && eventId > 0L && eventId != lastHandledEventId

fun restoredAttachmentStatus(available: Int, unavailable: Int): String = when {
    unavailable > 0 && available > 0 ->
        "Recuperamos $available archivo(s). $unavailable ya no están disponibles; vuelve a seleccionarlos."
    unavailable > 0 ->
        "No pudimos recuperar $unavailable archivo(s). Vuelve a seleccionarlos; tu texto se conserva."
    available > 0 -> "$available archivo(s) recuperado(s). Revisa el equipo antes de enviar."
    else -> ""
}

fun isIncompleteAttachmentRecovery(status: String): Boolean =
    status.startsWith("No pudimos recuperar ") || status.startsWith("Recuperamos ")

data class P2pRecoveredAttachments(
    val files: Map<P2pAttachmentContext, List<P2pSavedAttachment>>,
    val unavailable: Map<P2pAttachmentContext, Int>
)

/** Access is checked by the caller in IO; an unavailable URI never becomes an outgoing selection. */
fun recoverAccessibleAttachments(
    snapshot: P2pSavedAttachments,
    isAccessible: (P2pSavedAttachment) -> Boolean
): P2pRecoveredAttachments {
    val available = mutableMapOf<P2pAttachmentContext, List<P2pSavedAttachment>>()
    val unavailable = mutableMapOf<P2pAttachmentContext, Int>()
    P2pAttachmentContext.entries.forEach { context ->
        val source = snapshot.files[context].orEmpty()
        val readable = source.filter { isRestorableContentUri(it.uri) && isAccessible(it) }
        available[context] = readable
        unavailable[context] = source.size - readable.size +
            if (context == snapshot.activeContext) snapshot.omittedCount else 0
    }
    return P2pRecoveredAttachments(available, unavailable)
}
