package com.example.wifidrop.pc

import java.net.Socket
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal data class DesktopTransferEntry(
    val id: String,
    val timestamp: String,
    val fileName: String,
    val bytes: Long,
    val peer: String,
    val incoming: Boolean,
    val path: String? = null,
    val outcome: String = "Completado"
)

internal class DesktopTransferCancellation {
    private val cancelled = AtomicBoolean(false)
    private val socket = AtomicReference<Socket?>(null)
    val isCancelled: Boolean get() = cancelled.get()

    fun attach(value: Socket) {
        socket.set(value)
        if (cancelled.get()) {
            value.close()
            throwIfCancelled()
        }
    }

    fun cancel() {
        cancelled.set(true)
        runCatching { socket.getAndSet(null)?.close() }
    }

    fun throwIfCancelled() {
        if (cancelled.get()) throw CancellationException("Transferencia cancelada")
    }
}

internal fun actionableDesktopError(error: Throwable): String {
    val raw = error.message.orEmpty()
    return when {
        raw.contains("confirmacion_host_requerida") ->
            "Aprueba este equipo en el teléfono y vuelve a enviar. Compara antes la huella con Ajustes → Este equipo."
        error is java.net.BindException -> "El puerto está ocupado. Cierra otra instancia de Qetara o cambia el puerto en Ajustes."
        error is java.net.UnknownHostException -> "No se encuentra ese equipo. Revisa la dirección o usa Buscar equipos."
        error is java.net.ConnectException -> "El equipo no responde. Activa Recibir en el otro equipo y comprueba la misma red y puerto."
        error is java.net.SocketTimeoutException -> "Se agotó la espera. Acerca los equipos al Wi-Fi y vuelve a intentar; el archivo puede reanudarse."
        raw.contains("auth", ignoreCase = true) || raw.contains("autentic", ignoreCase = true) ->
            "El código o PIN no coincide. Copia la sesión del equipo receptor y vuelve a intentar."
        raw.contains("espacio insuficiente", ignoreCase = true) ->
            "No hay espacio suficiente en el equipo receptor. Libera espacio allí o elige otra carpeta antes de intentar de nuevo."
        error is java.nio.file.AccessDeniedException || raw.contains("denegado", ignoreCase = true) || raw.contains("Permission denied", ignoreCase = true) ->
            "No se puede acceder a esa carpeta. Elige una carpeta donde tengas permiso para guardar archivos."
        raw.contains("canal_no_unido") -> "El equipo no se ha unido al Canal Wi-Fi. Actívalo allí y vuelve a intentar."
        raw.contains("integridad", ignoreCase = true) || raw.contains("SHA-256", ignoreCase = true) ->
            "No se pudo verificar el archivo. Vuelve a enviarlo; no se guardó una copia dañada."
        raw.contains("expir", ignoreCase = true) -> "La sesión terminó. Crea una nueva sesión en el equipo receptor."
        error is java.net.SocketException || error is java.io.EOFException ->
            "Se interrumpió la conexión. Comprueba que el receptor siga activo y vuelve a intentar."
        raw.isNotBlank() -> raw
        else -> "No se pudo completar la operación. Revisa la conexión y vuelve a intentar."
    }
}

internal fun desktopMessagesForPeer(
    messages: List<DesktopChatEntry>,
    scope: DesktopChatScope,
    peerIp: String?
): List<DesktopChatEntry> = messages.filter { entry ->
    entry.scope == scope && (scope == DesktopChatScope.GLOBAL_LAN ||
        (!peerIp.isNullOrBlank() && entry.peerAddress.split(',').any { it.trim() == peerIp }))
}
