package com.example.wifidrop

import java.io.File

/** Process-only ownership, matching the lifetime of FlashAndroidRuntime.selectedFiles. */
internal class FlashImportSources {
    private val preparedSources = mutableMapOf<String, File>()

    fun pending(uris: List<String>, selectedFiles: List<File>): List<String> {
        val selected = selectedFiles.toSet()
        preparedSources.entries.removeAll { it.value !in selected }
        return uris.distinct().filter { uri ->
            val prepared = preparedSources[uri]
            prepared == null || !isReadableFlashFile(prepared)
        }
    }

    /** Called only when the entire import succeeds; failed/cancelled imports claim no URI. */
    fun commit(prepared: List<Pair<String, File>>, selectedFiles: List<File>): List<File> {
        val replaced = prepared.mapNotNull { preparedSources[it.first] }.toSet()
        preparedSources.putAll(prepared)
        return selectedFiles.filterNot { it in replaced } + prepared.map { it.second }
    }

    fun clear() = preparedSources.clear()
}

internal fun isReadableFlashFile(file: File): Boolean = file.isFile && file.canRead()

internal fun canSendFlashFiles(files: List<File>): Boolean =
    files.isNotEmpty() && files.all(::isReadableFlashFile)
