package com.example.wifidrop.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.wifidrop.FileTransfer
import com.example.wifidrop.IncomingShareBus
import com.example.wifidrop.IncomingSharePayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class P2pOutboundSelection(
    val uri: Uri,
    val name: String
)

data class P2pShareImportState(
    val attachments: P2pAttachmentDrafts<P2pOutboundSelection> = P2pAttachmentDrafts(),
    val incomingShareEventId: Long? = null
) {
    val selectedFiles: List<P2pOutboundSelection> get() = attachments.draft().files
    val shareImportStatus: String get() = attachments.draft().status
    val attachmentContext: P2pAttachmentContext get() = attachments.activeContext
}

class P2pShareImportPresenter(
    context: Context
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(P2pShareImportState())
    val state: StateFlow<P2pShareImportState> = _state.asStateFlow()

    fun selectContext(context: P2pAttachmentContext) {
        _state.update { it.copy(attachments = it.attachments.select(context)) }
    }

    fun selectedFiles(context: P2pAttachmentContext): List<P2pOutboundSelection> =
        _state.value.attachments.draft(context).files

    fun importPickedUris(uris: List<Uri>, context: P2pAttachmentContext) {
        val loaded = loadOutboundSelections(uris)
        if (loaded.isNotEmpty()) {
            updateDraft(context) { it.copy(files = loaded, status = "${loaded.size} archivo(s) seleccionado(s).") }
        }
    }

    fun consumeIncomingShare(payload: IncomingSharePayload?) {
        val sharePayload = payload ?: return
        val loaded = loadOutboundSelections(sharePayload.uris)
        if (loaded.isNotEmpty()) {
            _state.update {
                it.copy(
                    attachments = it.attachments.update(P2pAttachmentContext.FILES) { draft ->
                        draft.copy(files = loaded, status = "Recibidos ${loaded.size} archivo(s) desde Compartir.")
                    }.select(P2pAttachmentContext.FILES),
                    incomingShareEventId = sharePayload.eventId
                )
            }
        }
        IncomingShareBus.consume(sharePayload.eventId)
    }

    fun clearSelectedFiles(context: P2pAttachmentContext) {
        updateDraft(context) { P2pAttachmentDraft() }
    }

    fun updateStatus(status: String) {
        updateDraft(P2pAttachmentContext.FILES) { it.copy(status = status) }
    }

    fun markFilesQueued(status: String, clearSelectionAfterSend: Boolean) {
        updateDraft(P2pAttachmentContext.FILES) {
            it.copy(files = if (clearSelectionAfterSend) emptyList() else it.files, status = status)
        }
    }

    fun markDirectComposerFilesQueued(fileCount: Int) {
        updateDraft(P2pAttachmentContext.DIRECT_CHAT) {
            P2pAttachmentDraft(status = "Enviando $fileCount archivo(s).")
        }
    }

    fun markChannelComposerFilesQueued(fileCount: Int) {
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

    private fun loadOutboundSelections(uris: List<Uri>): List<P2pOutboundSelection> {
        return uris
            .distinctBy { it.toString() }
            .mapIndexed { index, uri ->
                tryTakePersistableReadPermission(uri)
                val fallback = "file_${System.currentTimeMillis()}_$index"
                val name = FileTransfer.queryDisplayName(appContext, uri)?.ifBlank { fallback } ?: fallback
                P2pOutboundSelection(uri = uri, name = name)
            }
    }

    private fun tryTakePersistableReadPermission(uri: Uri) {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers only grant transient access.
        } catch (_: UnsupportedOperationException) {
            // Some providers do not support persistable grants.
        }
    }
}
