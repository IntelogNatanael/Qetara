package com.example.wifidrop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal suspend fun prepareFlashImportFile(
    staged: MutableList<File>,
    prepare: CoroutineScope.() -> File
) {
    withContext(Dispatchers.IO) {
        // Register cleanup ownership before dispatching back: cancellation can discard the result
        // of withContext even after the provider has finished writing the entire file.
        staged += prepare()
    }
}

/** Never remove a provider original or anything outside one private outgoing import directory. */
internal fun deletePreparedFlashFile(file: File, cacheDir: File): Boolean = runCatching {
    val root = File(cacheDir, "flash-outgoing").canonicalFile
    val prepared = file.canonicalFile
    val directory = prepared.parentFile ?: return@runCatching false
    if (directory.parentFile != root || !prepared.isFile) return@runCatching false
    prepared.delete().also { deleted -> if (deleted) directory.delete() }
}.getOrDefault(false)
