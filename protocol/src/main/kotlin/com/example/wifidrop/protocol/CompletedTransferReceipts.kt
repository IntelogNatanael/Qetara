package com.example.wifidrop.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Records a destination before publication. A receipt is successful only while that destination
 * contains the expected bytes, so interrupted publication is never accepted from metadata alone.
 */
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

    /** The caller must have verified the complete source hash before reaching this commit boundary. */
    fun publishVerified(
        source: File, peerId: String, attemptId: String, fileName: String, totalBytes: Long, hash: String,
        checkActive: () -> Unit = {}
    ): File {
        require(totalBytes >= 0L && source.isFile && source.length() == totalBytes) {
            "archivo verificado incompleto"
        }
        requireValidFileHash(hash)
        checkActive()
        return publishReceivedFile(source, receiveDirectory, fileName) { target ->
            checkActive()
            // Preserve the existing receipt byte format. After a process restart find() checks
            // size and SHA-256; it cannot mistake a nonempty transfer's reservation for completion.
            remember(peerId, attemptId, fileName, totalBytes, hash, target)
            checkActive()
        }
    }

    fun remember(peerId: String, attemptId: String, fileName: String, totalBytes: Long, hash: String, target: File) {
        require(target.canonicalFile.parentFile == receiveDirectory.canonicalFile) { "destino de recibo invalido" }
        check(receiptsDirectory.isDirectory || receiptsDirectory.mkdirs()) { "carpeta de recibos no disponible" }
        val receipt = receiptFile(peerId, attemptId, fileName, totalBytes, hash)
        val stagedReceipt = File.createTempFile(".qetara-receipt-", ".pending", receiptsDirectory)
        try {
            FileOutputStream(stagedReceipt).use { fileOutput ->
                val output = DataOutputStream(fileOutput)
                output.writeUTF(target.name)
                output.writeLong(totalBytes)
                output.writeUTF(hash)
                output.flush()
                fileOutput.fd.sync()
            }
            // Avoid truncating a prior durable receipt if metadata publication is interrupted.
            replaceOwnedFile(stagedReceipt, receipt)
        } finally {
            stagedReceipt.delete()
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
