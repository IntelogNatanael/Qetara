package com.example.wifidrop

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeReceivedStatusTextTest : LocalizedResourcesTest() {
    @Test fun receivedFileNamesWithSentencePunctuationKeepTheirCompleteNames() {
        val fileName = "notes. 2026.pdf"
        for ((source, target) in listOf("es" to "en", "en" to "es")) {
            useAppLocale(source)
            val received = appString(R.string.rt_file_received, fileName, appString(R.string.rt_saved_downloads))
            val exportFailed = appString(
                R.string.rt_file_received, fileName,
                appString(R.string.rt_copy_downloads_failed, appString(R.string.rt_error_insufficient_space))
            )
            useAppLocale(target)

            assertEquals(
                appString(R.string.rt_file_received, fileName, appString(R.string.rt_saved_downloads)),
                localizeRuntimeStatus(received)
            )
            assertEquals(
                appString(
                    R.string.rt_file_received, fileName,
                    appString(R.string.rt_copy_downloads_failed, appString(R.string.rt_error_insufficient_space))
                ),
                localizeRuntimeStatus(exportFailed)
            )
        }
    }

    @Test fun ambiguousLegacyExportCopyIsPreservedInsteadOfGuessingTheFileName() {
        for ((source, target) in listOf("es" to "en", "en" to "es")) {
            useAppLocale(source)
            val fileName = "notes. " + appString(R.string.rt_copy_downloads_failed, "marker") + " 2026.pdf"
            val received = appString(
                R.string.rt_file_received, fileName,
                appString(R.string.rt_copy_downloads_failed, appString(R.string.rt_error_insufficient_space))
            )
            useAppLocale(target)
            assertEquals(received, localizeRuntimeStatus(received))
        }
    }
}
