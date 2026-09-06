package com.example.wifidrop.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.example.wifidrop.FileTransfer
import com.example.wifidrop.IncomingShareBus
import com.example.wifidrop.IncomingSharePayload
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class P2pOutboundSelection(val uri: Uri, val name: String)

data class P2pShareImportState(
    val attachments: P2pAttachmentDrafts<P2pOutboundSelection> = P2pAttachmentDrafts(),
    val incomingShareEventId: Long? = null
) {
    val selectedFiles: List<P2pOutboundSelection> get() = attachments.draft().files
    val shareImportStatus: String get() = attachments.draft().status
    val attachmentContext: P2pAttachmentContext get() = attachments.activeContext
}

class P2pShareImportPresenter(
    context: Context,
    restoredState: P2pSavedAttachments = P2pSavedAttachments()
) {
    private val appContext = context.applicationContext
    private val revisions = P2pAttachmentContext.entries.associateWith { AtomicLong() }
    private val _state = MutableStateFlow(P2pShareImportState(
        attachments = P2pAttachmentDrafts(activeContext = restoredState.activeContext),
        incomingShareEventId = restoredState.incomingShareEventId
    ))
    val state: StateFlow<P2pShareImportState> = _state.asStateFlow()

    fun selectContext(context: P2pAttachmentContext) {
        _state.update { it.copy(attachments = it.attachments.select(context)) }
    }

    fun selectedFiles(context: P2pAttachmentContext): List<P2pOutboundSelection> =
        _state.value.attachments.draft(context).files

    fun savedAttachments(state: P2pShareImportState = _state.value): P2pSavedAttachments =
        P2pSavedAttachments(
            activeContext = state.attachmentContext,
            incomingShareEventId = state.incomingShareEventId,
            files = P2pAttachmentContext.entries.associateWith { context ->
                state.attachments.draft(context).files.map { P2pSavedAttachment(it.uri.toString(), it.name) }
            }
        )

    suspend fun restoreSelections(snapshot: P2pSavedAttachments) {
        val tickets = P2pAttachmentContext.entries.associateWith { context ->
            val ticket = revisions.getValue(context).incrementAndGet()
            if (snapshot.files[context].orEmpty().isNotEmpty()) {
                updateDraft(context) { it.copy(status = "Recuperando selección...") }
            }
            ticket
        }
        val recovered = withContext(Dispatchers.IO) {
            recoverAccessibleAttachments(snapshot) { item ->
                try {
                    appContext.contentResolver.openAssetFileDescriptor(item.uri.toUri(), "r")?.use { true } ?: false
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    false
                }
            }
        }
        P2pAttachmentContext.entries.forEach { context ->
            // Choosing, clearing or sending files while restoration runs must win over the old snapshot.
            if (revisions.getValue(context).get() != tickets.getValue(context)) return@forEach
            val files = recovered.files[context].orEmpty().map { P2pOutboundSelection(it.uri.toUri(), it.name) }
            updateDraft(context) {
                P2pAttachmentDraft(files = files, status = restoredAttachmentStatus(
                    files.size, recovered.unavailable[context] ?: 0
                ))
            }
        }
    }

    suspend fun importPickedUris(uris: List<Uri>, context: P2pAttachmentContext) {
        if (uris.isEmpty()) return
        val ticket = revisions.getValue(context).incrementAndGet()
        updateDraft(context) { it.copy(status = "Preparando selección...") }
        val loaded = loadOutboundSelections(uris)
        if (revisions.getValue(context).get() == ticket && loaded.isNotEmpty()) {
            updateDraft(context) { it.copy(files = loaded, status = "${loaded.size} archivo(s) seleccionado(s).") }
        }
    }

    suspend fun consumeIncomingShare(payload: IncomingSharePayload?) {
        val sharePayload = payload ?: return
        if (!shouldConsumeIncomingShare(sharePayload.eventId, _state.value.incomingShareEventId)) {
            IncomingShareBus.consume(sharePayload.eventId)
            return
        }
        val context = P2pAttachmentContext.FILES
        val ticket = revisions.getValue(context).incrementAndGet()
        val loaded = loadOutboundSelections(sharePayload.uris)
        if (revisions.getValue(context).get() == ticket && loaded.isNotEmpty()) {
            _state.update {
                it.copy(
                    attachments = it.attachments.update(context) { draft ->
                        draft.copy(files = loaded, status = "Recibidos ${loaded.size} archivo(s) desde Compartir.")
                    }.select(context),
                    incomingShareEventId = sharePayload.eventId
                )
            }
        } else {
            // A newer selection or clear action wins; remember consumption so rotation cannot restore this old share.
            _state.update { it.copy(incomingShareEventId = sharePayload.eventId) }
        }
        IncomingShareBus.consume(sharePayload.eventId)
    }

    fun clearSelectedFiles(context: P2pAttachmentContext) {
        revisions.getValue(context).incrementAndGet()
        updateDraft(context) { P2pAttachmentDraft() }
    }

    fun updateStatus(status: String) {
        updateDraft(P2pAttachmentContext.FILES) { it.copy(status = status) }
    }

    fun markFilesQueued(status: String, clearSelectionAfterSend: Boolean) {
        revisions.getValue(P2pAttachmentContext.FILES).incrementAndGet()
        updateDraft(P2pAttachmentContext.FILES) {
            it.copy(files = if (clearSelectionAfterSend) emptyList() else it.files, status = status)
        }
    }

    fun markDirectComposerFilesQueued(fileCount: Int) {
        revisions.getValue(P2pAttachmentContext.DIRECT_CHAT).incrementAndGet()
        updateDraft(P2pAttachmentContext.DIRECT_CHAT) { P2pAttachmentDraft(status = "Enviando $fileCount archivo(s).") }
    }

    fun markChannelComposerFilesQueued(fileCount: Int) {
        revisions.getValue(P2pAttachmentContext.CHANNEL).incrementAndGet()
        updateDraft(P2pAttachmentContext.CHANNEL) {
            P2pAttachmentDraft(status = if (fileCount == 1) "Publicado 1 archivo en el canal Wi‑Fi."
                else "Publicados $fileCount archivos en el canal Wi‑Fi.")
        }
    }

    private fun updateDraft(
        context: P2pAttachmentContext,
        transform: (P2pAttachmentDraft<P2pOutboundSelection>) -> P2pAttachmentDraft<P2pOutboundSelection>
    ) {
        _state.update { it.copy(attachments = it.attachments.update(context, transform)) }
    }

    private suspend fun loadOutboundSelections(uris: List<Uri>): List<P2pOutboundSelection> =
        withContext(Dispatchers.IO) {
            uris.distinctBy { it.toString() }.mapIndexed { index, uri ->
                tryTakePersistableReadPermission(uri)
                val fallback = "file_${System.currentTimeMillis()}_$index"
                val name = try {
                    FileTransfer.queryDisplayName(appContext, uri)?.ifBlank { fallback } ?: fallback
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    fallback
                }
                P2pOutboundSelection(uri = uri, name = name)
            }
        }

    private fun tryTakePersistableReadPermission(uri: Uri) {
        try {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some providers only grant transient access; restoration checks access again.
        } catch (_: UnsupportedOperationException) {
            // Some providers do not support persistable grants.
        }
    }
}
