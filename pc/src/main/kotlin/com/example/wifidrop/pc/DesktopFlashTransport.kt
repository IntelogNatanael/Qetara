package com.example.wifidrop.pc

import com.example.wifidrop.protocol.flash.FlashConfig
import com.example.wifidrop.protocol.flash.FlashEngine
import com.example.wifidrop.protocol.flash.FlashListener
import com.example.wifidrop.protocol.flash.FlashPeer
import java.io.File

/** Transport boundary allows callback ordering to be exercised without opening network ports. */
internal interface DesktopFlashTransport {
    fun start()
    fun stop()
    fun send(file: File, peer: FlashPeer): String
    fun approve(requestId: String, accepted: Boolean): Boolean
    fun cancel(operationId: String): Boolean
    fun discover()
    fun discoverAt(address: String, port: Int)
}

internal fun createDesktopFlashTransport(label: String, directory: File, listener: FlashListener): DesktopFlashTransport {
    val engine = FlashEngine(label, directory, listener, FlashConfig(maxOperations = 1))
    return object : DesktopFlashTransport {
        override fun start() { engine.start() }
        override fun stop() = engine.stop()
        override fun send(file: File, peer: FlashPeer) = engine.send(file, peer)
        override fun approve(requestId: String, accepted: Boolean) = engine.approve(requestId, accepted)
        override fun cancel(operationId: String) = engine.cancel(operationId)
        override fun discover() = engine.discover()
        override fun discoverAt(address: String, port: Int) = engine.discoverAt(address, port)
    }
}
