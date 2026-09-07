package com.example.wifidrop.protocol.flash

import java.io.*
import java.net.*
import java.util.UUID

/** Small, nonce-correlated UDP discovery. Runs only for the lifetime of an explicit Flash activation. */
internal class FlashDiscovery(
    private val port: Int, private val ownPeer: () -> FlashPeer,
    private val onPeer: (FlashPeer) -> Unit, private val isActive: () -> Boolean
) : Closeable {
    private val socket = DatagramSocket(null).apply {
        try {
            reuseAddress = false
            broadcast = true
            bind(InetSocketAddress(this@FlashDiscovery.port))
        } catch (error: Exception) {
            close()
            throw error
        }
    }
    @Volatile private var nonce: String = ""
    @Volatile private var queryUntil = 0L
    private var lastResponse = 0L
    @Volatile private var closed = false
    fun start() { Thread(::receive, "qetara-flash-discovery").apply { isDaemon = true; start() } }
    fun discover() {
        if (closed || !isActive()) return
        val now = System.currentTimeMillis()
        if (now < queryUntil - 9_000) return // At most one user-triggered broadcast per second.
        nonce = UUID.randomUUID().toString(); queryUntil = now + 10_000
        val bytes = packet { it.flashHeader(FLASH_DISCOVER); it.writeUTF(nonce) }
        Thread({
            val targets = linkedSetOf("255.255.255.255")
            runCatching {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val network = interfaces.nextElement()
                    if (network.isUp && !network.isLoopback) network.interfaceAddresses.mapNotNullTo(targets) { it.broadcast?.hostAddress }
                }
            }
            targets.forEach { target ->
                if (!closed && isActive()) runCatching { socket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(target), port)) }
            }
        }, "qetara-flash-broadcast").apply { isDaemon = true; start() }
    }
    private fun receive() {
        val buffer = ByteArray(1024)
        while (!closed && isActive()) {
            val packet = DatagramPacket(buffer, buffer.size)
            try { socket.receive(packet) } catch (_: IOException) { break }
            if (packet.length >= buffer.size || !isActive()) continue
            runCatching {
                val address = requireFlashAddress(packet.address.hostAddress ?: "")
                val input = DataInputStream(ByteArrayInputStream(packet.data, packet.offset, packet.length))
                when (input.flashKind()) {
                    FLASH_DISCOVER -> {
                        val requestNonce = input.readUTF(); input.requireEnd()
                        require(UUID.fromString(requestNonce).toString() == requestNonce)
                        val now = System.currentTimeMillis()
                        if (now - lastResponse < 200) return@runCatching // Bound reflection/flood responses globally.
                        lastResponse = now
                        val response = packet { it.flashHeader(FLASH_ANNOUNCE); it.writeUTF(requestNonce); writeFlashPeer(it, ownPeer()) }
                        if (isActive()) socket.send(DatagramPacket(response, response.size, packet.address, packet.port))
                    }
                    FLASH_ANNOUNCE -> {
                        val responseNonce = input.readUTF()
                        if (responseNonce != nonce || System.currentTimeMillis() > queryUntil) return@runCatching
                        val peer = readFlashPeer(input, address); input.requireEnd()
                        if (isActive()) onPeer(peer)
                    }
                }
            }
        }
    }
    override fun close() { closed = true; socket.close() }
    private fun packet(write: (DataOutputStream) -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream(); DataOutputStream(bytes).use(write); return bytes.toByteArray()
    }
}
