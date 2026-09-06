package com.example.wifidrop.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Remembers a completed attempt so a lost acknowledgement never creates a second copy. */
class CompletedTransferReceipts(private val receiveDirectory: File) {
    private val receiptsDirectory = File(receiveDirectory, ".partial")

    fun find(
        peerId: String, attemptId: String, fileName: String, totalBytes: Long, hash: String,
        checkActive: () -> Unit = {}
    ): File? {
        val receipt = receiptFile(peerId, attemptId, fileName, totalBytes, hash)
        if (!receipt.isFile) return null
        return runCatching {
            DataInputStream(receipt.inputStream().buffered()).use { input ->
                val storedName = input.readUTF()
                val storedTotal = input.readLong()
                val storedHash = input.readUTF()
                val target = File(receiveDirectory, storedName)
                if (target.canonicalFile.parentFile != receiveDirectory.canonicalFile ||
                    storedTotal != totalBytes || storedHash != hash ||
                    !target.isFile || target.length() != totalBytes || sha256File(target, checkActive) != hash
                ) null else target
            }
        }.getOrElse { error ->
            if (error is java.util.concurrent.CancellationException) throw error
            null
        }
    }

    fun remember(peerId: String, attemptId: String, fileName: String, totalBytes: Long, hash: String, target: File) {
        require(target.canonicalFile.parentFile == receiveDirectory.canonicalFile) { "destino de recibo invalido" }
        check(receiptsDirectory.isDirectory || receiptsDirectory.mkdirs()) { "carpeta de recibos no disponible" }
        val receipt = receiptFile(peerId, attemptId, fileName, totalBytes, hash)
        FileOutputStream(receipt).use { fileOutput ->
            val output = DataOutputStream(fileOutput)
            output.writeUTF(target.name)
            output.writeLong(totalBytes)
            output.writeUTF(hash)
            output.flush()
            fileOutput.fd.sync()
        }
        // Only our small receipts are pruned. Received files and resumable bytes are never deleted here.
        receiptsDirectory.listFiles { file -> file.isFile && file.name.startsWith("receipt_") && file.name.endsWith(".receipt") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(512)
            ?.forEach { it.delete() }
    }

    private fun receiptFile(peerId: String, attemptId: String, fileName: String, totalBytes: Long, hash: String): File {
        // Length-prefixed components avoid ambiguous identities containing separators.
        val material = listOf(peerId, attemptId, fileName, totalBytes.toString(), hash)
            .joinToString("") { "${it.length}:$it" }
        val key = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8)).toHexLower()
        return File(receiptsDirectory, "receipt_$key.receipt")
    }
}

fun sha256File(file: File, checkActive: () -> Unit = {}): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val bytes = ByteArray(64 * 1024)
        while (true) {
            checkActive()
            val count = input.read(bytes)
            if (count < 0) break
            if (count > 0) digest.update(bytes, 0, count)
        }
    }
    return digest.digest().toHexLower()
}
