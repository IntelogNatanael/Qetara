package com.example.wifidrop.presentation

import com.example.wifidrop.ConnectionSnapshot
import com.example.wifidrop.FileTransfer
import com.example.wifidrop.TransferSecurity

data class P2pOutboundContext(
    val permissionGranted: Boolean,
    val lanConnected: Boolean,
    val sessionToken: String,
    val sessionPin: String,
    val sessionExpired: Boolean,
    val connection: ConnectionSnapshot?
)

sealed interface P2pOutboundDecision<out T> {
    data class Ready<T>(val plan: T) : P2pOutboundDecision<T>
    data class Blocked(val message: String) : P2pOutboundDecision<Nothing>
}

data class P2pFileBatchPlan(
    val targetIp: String,
    val shareImportStatus: String,
    val feedbackMessage: String
)

data class P2pDirectMessagePlan(
    val targetIp: String,
    val message: String,
    val feedbackMessage: String
)

data class P2pGlobalMessagePlan(
    val message: String?,
    val includeFiles: Boolean,
    val feedbackMessage: String
)

data class P2pDirectChatComposerPlan(
    val targetIps: List<String>,
    val message: String?,
    val includeFiles: Boolean,
    val feedbackMessage: String
)

fun buildFileBatchSendPlan(
    context: P2pOutboundContext,
    targetIp: String?,
    fileCount: Int
): P2pOutboundDecision<P2pFileBatchPlan> {
    if (fileCount <= 0) {
        return P2pOutboundDecision.Blocked("Selecciona al menos un archivo.")
    }

    return when (val targetDecision = validateOutboundTarget("enviar archivos", context, targetIp)) {
        is P2pOutboundDecision.Blocked -> targetDecision
        is P2pOutboundDecision.Ready -> P2pOutboundDecision.Ready(
            P2pFileBatchPlan(
                targetIp = targetDecision.plan,
                shareImportStatus = "Enviando $fileCount archivo(s).",
                feedbackMessage = if (fileCount == 1) {
                    "Archivo en cola para envío."
                } else {
                    "$fileCount archivos en cola para envío."
                }
            )
        )
    }
}

fun buildDirectMessageSendPlan(
    context: P2pOutboundContext,
    draft: String,
    targetIp: String?
): P2pOutboundDecision<P2pDirectMessagePlan> {
    val message = sanitizeOutgoingMessage(draft)
    if (message.isBlank()) {
        return P2pOutboundDecision.Blocked("Escribe un mensaje antes de enviar.")
    }

    return when (val targetDecision = validateOutboundTarget("enviar mensajes", context, targetIp)) {
        is P2pOutboundDecision.Blocked -> targetDecision
        is P2pOutboundDecision.Ready -> P2pOutboundDecision.Ready(
            P2pDirectMessagePlan(
                targetIp = targetDecision.plan,
                message = message,
                feedbackMessage = "Mensaje en cola para envío."
            )
        )
    }
}

fun buildGlobalMessageSendPlan(
    context: P2pOutboundContext,
    draft: String,
    selectedFilesCount: Int,
    globalLanJoined: Boolean,
    targetCount: Int
): P2pOutboundDecision<P2pGlobalMessagePlan> {
    val message = sanitizeOutgoingMessage(draft)
    val hasMessage = message.isNotBlank()
    val hasFiles = selectedFilesCount > 0
    if (!hasMessage && !hasFiles) {
        return P2pOutboundDecision.Blocked("Escribe un mensaje o adjunta al menos un archivo.")
    }
    if (!context.lanConnected) {
        return P2pOutboundDecision.Blocked("El canal requiere estar en una red Wi‑Fi.")
    }
    if (!globalLanJoined) {
        return P2pOutboundDecision.Blocked("Entra al canal Wi‑Fi para escribir.")
    }
    if (hasFiles && targetCount <= 0) {
        return P2pOutboundDecision.Blocked("No hay otros equipos en el canal para recibir archivos.")
    }

    return when (val sessionDecision = validateAuthenticatedSession("usar el canal Wi‑Fi", context)) {
        is P2pOutboundDecision.Blocked -> sessionDecision
        is P2pOutboundDecision.Ready -> P2pOutboundDecision.Ready(
            P2pGlobalMessagePlan(
                message = message.takeIf { hasMessage },
                includeFiles = hasFiles,
                feedbackMessage = when {
                    hasMessage && hasFiles -> {
                        val fileLabel = if (selectedFilesCount == 1) "1 archivo" else "$selectedFilesCount archivos"
                        "Mensaje publicado y $fileLabel en cola para el canal Wi‑Fi."
                    }
                    hasFiles -> {
                        val fileLabel = if (selectedFilesCount == 1) "1 archivo" else "$selectedFilesCount archivos"
                        "$fileLabel en cola para el canal Wi‑Fi."
                    }
                    targetCount == 0 -> "Mensaje publicado en el canal Wi‑Fi."
                    else -> "Mensaje publicado en el canal Wi‑Fi para ${if (targetCount == 1) "1 equipo" else "$targetCount equipos"}."
                }
            )
        )
    }
}

fun buildDirectChatComposerPlan(
    context: P2pOutboundContext,
    draft: String,
    selectedFilesCount: Int,
    targetIps: List<String>
): P2pOutboundDecision<P2pDirectChatComposerPlan> {
    val message = sanitizeOutgoingMessage(draft)
    val hasDraft = message.isNotBlank()
    val hasFiles = selectedFilesCount > 0
    if (!hasDraft && !hasFiles) {
        return P2pOutboundDecision.Blocked("Escribe un mensaje o adjunta al menos un archivo.")
    }

    val actionLabel = when {
        hasDraft && hasFiles -> "mensajes y archivos"
        hasFiles -> "enviar archivos"
        else -> "enviar mensajes"
    }

    val normalizedTargets = targetIps
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    if (normalizedTargets.isEmpty()) {
        return P2pOutboundDecision.Blocked("No se pudo resolver IP destino para $actionLabel.")
    }

    normalizedTargets.forEach { targetIp ->
        when (val targetDecision = validateOutboundTarget(actionLabel, context, targetIp)) {
            is P2pOutboundDecision.Blocked -> return targetDecision
            is P2pOutboundDecision.Ready -> Unit
        }
    }

    return P2pOutboundDecision.Ready(
        P2pDirectChatComposerPlan(
            targetIps = normalizedTargets,
            message = message.takeIf { hasDraft },
            includeFiles = hasFiles,
            feedbackMessage = when {
                hasDraft && hasFiles && normalizedTargets.size > 1 ->
                    "Mensaje y archivos en cola para ${normalizedTargets.size} equipos."
                hasDraft && hasFiles -> "Mensaje y archivos en cola para envío."
                hasDraft && normalizedTargets.size > 1 -> "Mensaje en cola para ${normalizedTargets.size} equipos."
                hasDraft -> "Mensaje en cola para envío."
                normalizedTargets.size > 1 -> "Archivos en cola para ${normalizedTargets.size} equipos."
                else -> "Archivos en cola para envío."
            }
        )
    )
}

private fun validateOutboundTarget(
    actionLabel: String,
    context: P2pOutboundContext,
    targetIp: String?
): P2pOutboundDecision<String> {
    if (!context.permissionGranted && !context.lanConnected) {
        return P2pOutboundDecision.Blocked(
            "Faltan permisos de Wi-Fi Direct o conexión Wi-Fi para $actionLabel. Concede el permiso o usa el canal Wi‑Fi en la misma red."
        )
    }

    when (val sessionDecision = validateAuthenticatedSession(actionLabel, context)) {
        is P2pOutboundDecision.Blocked -> return sessionDecision
        is P2pOutboundDecision.Ready -> Unit
    }

    val p2pLinked = context.connection?.groupFormed == true
    if (!p2pLinked && !context.lanConnected) {
        return P2pOutboundDecision.Blocked(
            "Conecta por Wi-Fi Direct o a la misma red Wi-Fi antes de $actionLabel. Inicia anfitrión/cliente o selecciona un equipo LAN."
        )
    }

    val normalizedTarget = targetIp?.trim().takeUnless { it.isNullOrBlank() }
        ?: return P2pOutboundDecision.Blocked(
            "No se pudo resolver IP destino para $actionLabel. Elige un equipo detectado o escribe la IP manualmente."
        )

    if (
        p2pLinked &&
        context.connection?.isGroupOwner == true &&
        normalizedTarget == context.connection.groupOwnerAddress
    ) {
        return P2pOutboundDecision.Blocked(
            "Destino inválido: no puedes enviarte a este mismo equipo. Elige otro peer o borra esa IP."
        )
    }

    return P2pOutboundDecision.Ready(normalizedTarget)
}

internal fun validateAuthenticatedSession(
    actionLabel: String,
    context: P2pOutboundContext
): P2pOutboundDecision<Unit> {
    if (!FileTransfer.isValidToken(context.sessionToken)) {
        return P2pOutboundDecision.Blocked(
            "La sesión actual no es válida. Sincroniza token/PIN con el otro equipo o crea una nueva."
        )
    }
    if (!TransferSecurity.isValidPin(context.sessionPin)) {
        return P2pOutboundDecision.Blocked("El PIN debe tener 6 dígitos.")
    }
    if (context.sessionExpired) {
        return P2pOutboundDecision.Blocked(
            "La sesión expiró. Renuévala antes de $actionLabel."
        )
    }

    return P2pOutboundDecision.Ready(Unit)
}

private fun sanitizeOutgoingMessage(raw: String): String {
    return raw.trim().replace(Regex("\\s+"), " ").take(2_000)
}
