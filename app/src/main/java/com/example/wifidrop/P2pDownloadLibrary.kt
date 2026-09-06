package com.example.wifidrop

import java.io.File
import java.text.Normalizer
import java.util.Locale

internal enum class DownloadLibrarySection(val title: String) { FILES("Recibidos"), ACTIVITY("Actividad") }
internal enum class DownloadFileSort(val title: String) { NEWEST("Más recientes"), NAME("Nombre"), LARGEST("Tamaño") }
internal enum class DownloadHistoryFilter(val title: String) {
    ALL("Todo"), RECEIVED("Recibidos"), SENT("Enviados"), ATTENTION("Sin completar")
}

internal data class DownloadFileSnapshot(val file: File, val bytes: Long, val modifiedAtMs: Long)

internal fun filterDownloadFiles(
    files: List<DownloadFileSnapshot>,
    query: String,
    sort: DownloadFileSort
): List<DownloadFileSnapshot> {
    val needle = normalizeLibrarySearch(query.trim())
    val matching = files.filter { normalizeLibrarySearch(it.file.name).contains(needle) }
    return when (sort) {
        DownloadFileSort.NEWEST -> matching.sortedWith(compareByDescending<DownloadFileSnapshot> { it.modifiedAtMs }.thenBy { it.file.name })
        DownloadFileSort.NAME -> matching.sortedBy { normalizeLibrarySearch(it.file.name) }
        DownloadFileSort.LARGEST -> matching.sortedWith(compareByDescending<DownloadFileSnapshot> { it.bytes }.thenBy { it.file.name })
    }
}

internal fun filterDownloadHistory(
    history: List<TransferHistoryEntry>,
    query: String,
    filter: DownloadHistoryFilter
): List<TransferHistoryEntry> {
    val needle = normalizeLibrarySearch(query.trim())
    return history.filter { entry ->
        val matchesFilter = when (filter) {
            DownloadHistoryFilter.ALL -> true
            DownloadHistoryFilter.RECEIVED -> entry.direction == TransferDirection.RECEIVED
            DownloadHistoryFilter.SENT -> entry.direction == TransferDirection.SENT
            DownloadHistoryFilter.ATTENTION -> entry.outcome != TransferOutcome.SUCCESS
        }
        matchesFilter && listOfNotNull(entry.fileName, entry.peerLabel, entry.peerIp)
            .any { normalizeLibrarySearch(it).contains(needle) }
    }.sortedByDescending { it.timestampMs }
}

private fun normalizeLibrarySearch(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "")
    .lowercase(Locale.ROOT)
