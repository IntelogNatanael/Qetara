package com.example.wifidrop.protocol

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Suppresses retry delivery after a successful callback when its network acknowledgement was lost. */
class ReceivedMessageReceipts(receiveDirectory: File) {
    private val directory = File(receiveDirectory, ".partial")

    @Synchronized
    fun deliverOnce(peerId: String, attemptId: String, message: String, deliver: () -> Unit): Boolean {
        val material = listOf(peerId, attemptId, message).joinToString("") { "${it.length}:$it" }
        val key = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8)).toHexLower()
        check(directory.isDirectory || directory.mkdirs()) { "carpeta de recibos no disponible" }
        val receipt = File(directory, "message_$key.receipt")
        if (receipt.isFile && receipt.length() == 2L && receipt.readText() == "ok") return false
        deliver()
        FileOutputStream(receipt).use { output ->
            output.write(byteArrayOf('o'.code.toByte(), 'k'.code.toByte()))
            output.flush()
            output.fd.sync()
        }
        directory.listFiles { file -> file.isFile && file.name.startsWith("message_") && file.name.endsWith(".receipt") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(512)
            ?.forEach { it.delete() }
        return true
    }
}
