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
