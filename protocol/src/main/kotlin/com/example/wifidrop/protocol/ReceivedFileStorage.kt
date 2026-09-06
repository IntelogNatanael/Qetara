package com.example.wifidrop.protocol

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/** Publishes verified bytes without replacing an existing user file, including racing receivers. */
fun publishReceivedFile(source: File, directory: File, desiredName: String): File {
    require(source.isFile) { "archivo temporal inexistente" }
    check(directory.isDirectory || directory.mkdirs()) { "carpeta de recepcion no disponible" }
    val safeName = sanitizeFileName(desiredName)
    val extensionAt = safeName.lastIndexOf('.').takeIf { it > 0 } ?: safeName.length
    val base = safeName.substring(0, extensionAt)
    val extension = safeName.substring(extensionAt)
    var collision = 0
    val destination = directory.canonicalFile
    val target: File
    while (true) {
        val nextName = if (collision == 0) safeName else "$base ($collision)$extension"
        val candidate = File(destination, nextName)
        require(candidate.canonicalFile.parentFile == destination) { "ruta de archivo no permitida" }
        // createNewFile is atomic: each concurrent receiver owns a distinct reservation.
        if (candidate.createNewFile()) {
            target = candidate
            break
        }
        collision++
        if (collision == Int.MAX_VALUE) throw IOException("demasiados archivos con el mismo nombre")
    }
    try {
        // On filesystems that allow replacing our own empty reservation, publish in one rename.
        if (!source.renameTo(target)) {
            try {
                // Windows cannot rename over our reservation with File.renameTo. Use an atomic move
                // instead of streaming into the final name, which could expose a partial file on crash.
                // Android 24/25 uses the successful same-volume rename above; fail safely if unavailable.
                Class.forName("java.nio.file.Files")
                java.nio.file.Files.move(
                    source.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (error: Exception) {
                throw IOException("no se pudo publicar el archivo verificado", error)
            }
        }
        return target
    } catch (error: Exception) {
        target.delete()
        throw error
    }
}

/** Free-space rejection happens before accepting bytes; zero means an unavailable filesystem value. */
fun requireReceiveCapacity(totalBytes: Long, resumedBytes: Long, usableBytes: Long) {
    requireValidResumeOffset(resumedBytes, totalBytes)
    if (usableBytes > 0L) {
        val reservedBytes = minOf(usableBytes, 8L * 1024 * 1024)
        check(totalBytes - resumedBytes <= usableBytes - reservedBytes) {
            "espacio insuficiente para recibir el archivo"
        }
    }
}

/** Export a second, complete copy while retaining the app's original verified file. */
fun copyReceivedFile(
    source: File, directory: File, desiredName: String = source.name,
    checkActive: () -> Unit = {}
): File {
    require(source.isFile) { "archivo origen no disponible" }
    check(directory.isDirectory || directory.mkdirs()) { "carpeta de exportacion no disponible" }
    requireReceiveCapacity(source.length(), 0L, directory.usableSpace)
    val staging = File.createTempFile(".qetara-export-", ".part", directory)
    try {
        FileInputStream(source).use { input ->
            FileOutputStream(staging).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) output.write(buffer, 0, count)
                }
                output.flush()
                output.fd.sync()
            }
        }
        checkActive()
        check(staging.length() == source.length()) { "copia local incompleta" }
        return publishReceivedFile(staging, directory, desiredName)
    } finally {
        staging.delete()
    }
}
