package com.example.wifidrop

import com.example.wifidrop.protocol.flash.FlashPeer
import java.io.File
import java.util.ArrayDeque

/** Main-thread queue: only consuming a terminal callback releases the current file. */
internal class FlashOutgoingBatch {
    data class Request(val file: File, val peer: FlashPeer, val position: Int, val total: Int)
    data class Completion(val file: File, val completed: Int, val total: Int, val remaining: Int)

    private val pending = ArrayDeque<File>()
    private var peer: FlashPeer? = null
    private var current: Request? = null
    private var operationId: String? = null
    private var total = 0
    private var completed = 0

    val active: Boolean get() = current != null || pending.isNotEmpty()

    fun start(files: List<File>, receiver: FlashPeer): Boolean {
        if (active || files.isEmpty()) return false
        pending.addAll(files)
        peer = receiver
        total = files.size
        completed = 0
        return true
    }

    fun next(): Request? {
        if (current != null) return null
        val receiver = peer ?: return null
        val file = pending.pollFirst() ?: return null
        return Request(file, receiver, completed + 1, total).also { current = it }
    }

    fun started(id: String) {
        check(current != null && operationId == null)
        operationId = id
    }

    fun complete(id: String): Completion? {
        if (id != operationId) return null
        val file = current?.file ?: return null
        completed++
        val result = Completion(file, completed, total, pending.size)
        current = null
        operationId = null
        if (pending.isEmpty()) reset()
        return result
    }

    fun fail(id: String) {
        if (id == operationId) reset()
    }

    fun cancelPending(id: String) {
        if (id == operationId) pending.clear()
    }

    fun reset() {
        pending.clear()
        peer = null
        current = null
        operationId = null
        total = 0
        completed = 0
    }
}
