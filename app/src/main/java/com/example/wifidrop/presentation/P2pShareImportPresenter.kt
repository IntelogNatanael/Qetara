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
    val selectedFiles: List<P2pOutboundSelection> = emptyList(),
    val shareImportStatus: String = ""
)

class P2pShareImportPresenter(
    context: Context
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(P2pShareImportState())
    val state: StateFlow<P2pShareImportState> = _state.asStateFlow()

    fun importPickedUris(uris: List<Uri>) {
        val loaded = loadOutboundSelections(uris)
        if (loaded.isNotEmpty()) {
            _state.update {
                it.copy(
                    selectedFiles = loaded,
                    shareImportStatus = "${loaded.size} archivo(s) seleccionado(s)."
                )
            }
        }
    }

    fun consumeIncomingShare(payload: IncomingSharePayload?) {
        val sharePayload = payload ?: return
        val loaded = loadOutboundSelections(sharePayload.uris)
        if (loaded.isNotEmpty()) {
            _state.update {
                it.copy(
                    selectedFiles = loaded,
                    shareImportStatus = "Recibidos ${loaded.size} archivo(s) desde Compartir."
                )
            }
        }
        IncomingShareBus.consume(sharePayload.eventId)
    }

    fun clearSelectedFiles() {
        _state.update {
            it.copy(
                selectedFiles = emptyList(),
                shareImportStatus = ""
            )
        }
    }

    fun updateStatus(status: String) {
        _state.update { it.copy(shareImportStatus = status) }
    }

    fun markFilesQueued(status: String, clearSelectionAfterSend: Boolean) {
        _state.update {
            it.copy(
                selectedFiles = if (clearSelectionAfterSend) emptyList() else it.selectedFiles,
                shareImportStatus = status
            )
        }
    }

    fun markDirectComposerFilesQueued(fileCount: Int) {
        _state.update {
            it.copy(
                selectedFiles = emptyList(),
                shareImportStatus = "Enviando $fileCount archivo(s)."
            )
        }
    }

    fun markChannelComposerFilesQueued(fileCount: Int) {
        _state.update {
            it.copy(
                selectedFiles = emptyList(),
                shareImportStatus = "Enviando $fileCount archivo(s) por el canal Wi‑Fi."
            )
        }
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
