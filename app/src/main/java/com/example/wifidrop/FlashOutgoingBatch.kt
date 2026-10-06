package com.example.wifidrop

import com.example.wifidrop.protocol.flash.FlashOperation
import java.io.File

/** Main-thread bookkeeping for one engine-owned batch; it never starts a second connection. */
internal class FlashOutgoingBatch {
    data class Completion(val file: File, val completed: Int, val total: Int, val remaining: Int)

    private var files = emptyList<File>()
    private var batchId: String? = null
    private val operations = mutableMapOf<String, Int>()
    private val delivered = mutableSetOf<String>()
    private var completed = 0
    private var cancelled = false
    private var observed = false

    val active: Boolean get() = files.isNotEmpty()

    fun start(selection: List<File>): Boolean {
        if (active || selection.isEmpty()) return false
        files = selection.toList()
        return true
    }

    fun started(id: String) {
        if (!active) return // The entire batch may finish before sendBatch() returns.
        check(batchId == null || batchId == id)
        batchId = id
    }

    fun observe(operation: FlashOperation) {
        if (!active || !operation.outgoing || operation.fileCount != files.size ||
            (batchId != null && batchId != operation.batchId)) return
        if (operation.fileIndex !in files.indices || operation.id in delivered) return
        batchId = operation.batchId
        observed = true
        operations[operation.id] = operation.fileIndex
    }

    fun complete(id: String): Completion? {
        val index = operations.remove(id) ?: return null
        val file = files.getOrNull(index) ?: return null
        delivered.add(id)
        completed++
        val result = Completion(file, completed, files.size, if (cancelled) 0 else files.size - completed)
        // Cancellation can lose the transport race to already queued ACKs. Consume every such receipt.
        if (completed == files.size) reset()
        return result
    }

    fun fail(id: String) {
        if (id == batchId || id in operations) reset()
    }

    fun cancelPending(id: String) {
        if (id == batchId || id in operations) cancelled = true
    }

    fun idle() {
        if (observed) reset()
    }

    fun reset() {
        files = emptyList()
        batchId = null
        operations.clear()
        delivered.clear()
        completed = 0
        cancelled = false
        observed = false
    }
}
