package com.example.wifidrop.pc

import java.io.File
import com.example.wifidrop.protocol.DEFAULT_PORT
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/** Only convenience settings are persisted. Session credentials and message content stay in memory. */
internal data class DesktopPreferences(
    val deviceLabel: String = "",
    val outputDirectory: String = "",
    val port: Int = DEFAULT_PORT,
    val retries: Int = 3,
    val sessionMinutes: Long = 120L
) {
    companion object {
        fun load(file: File): DesktopPreferences = runCatching {
            if (!file.isFile) return DesktopPreferences()
            val values = Properties().apply { file.inputStream().use(::load) }
            DesktopPreferences(
                deviceLabel = values.getProperty("deviceLabel", "").take(64),
                outputDirectory = values.getProperty("outputDirectory", ""),
                port = values.getProperty("port")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: DEFAULT_PORT,
                retries = values.getProperty("retries")?.toIntOrNull()?.takeIf { it in 1..10 } ?: 3,
                sessionMinutes = values.getProperty("sessionMinutes")?.toLongOrNull()
                    ?.takeIf { it in 1L..1440L } ?: 120L
            )
        }.getOrDefault(DesktopPreferences())
    }

    fun save(file: File) {
        file.parentFile?.mkdirs()
        val values = Properties().apply {
            setProperty("deviceLabel", deviceLabel)
            setProperty("outputDirectory", outputDirectory)
            setProperty("port", port.toString())
            setProperty("retries", retries.toString())
            setProperty("sessionMinutes", sessionMinutes.toString())
        }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.outputStream().use { values.store(it, "Qetara desktop preferences — no credentials") }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
