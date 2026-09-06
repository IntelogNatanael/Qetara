package com.example.wifidrop

internal enum class P2pExperienceCommand {
    REQUEST_PERMISSION,
    OPEN_WIFI_SETTINGS,
    START_CLIENT,
    CANCEL_DIRECT,
    SCAN_LAN,
    CANCEL_LAN_SCAN,
    USE_SUGGESTED_TARGET,
    REFRESH_STATE,
    REVIEW_TRUST,
    RENEW_SESSION,
    OPEN_DOWNLOADS,
    SYNC_TOKEN,
    OPEN_QUEUE,
    OPEN_MESSAGES,
    SEND_NOW,
    CONTINUE_FLOW
}

internal data class P2pExperienceAction(
    val label: String,
    val command: P2pExperienceCommand,
    val enabled: Boolean = true
)

internal data class P2pSupportBannerState(
    val title: String,
    val body: String,
    val action: P2pExperienceAction,
    val isError: Boolean = false
) {
    val key: String = "$title|$body|${action.label}|$isError"
}

internal data class P2pExperienceState(
    val activeConnectionMode: ConnectionMode,
    val alternateConnectionMode: ConnectionMode,
    val alternateModeEnabled: Boolean,
    val connectionLabel: String,
    val headerSummary: String,
    val headerHint: String,
    val connectionNarrative: String,
    val alternateModeHint: String?,
    val primaryAction: P2pExperienceAction,
    val supportBanner: P2pSupportBannerState?,
    val connectionReadyForFlow: Boolean,
    val lanReadyForExchange: Boolean,
    val nextStageAfterConnect: FocusStage,
    val showWifiDirectSection: Boolean,
    val showLanSection: Boolean
)

internal data class P2pChatExperienceState(
    val transportLabel: String,
    val transportStatus: String,
    val transportHint: String,
    val showSyncAction: Boolean
)

internal fun deriveP2pExperienceState(state: P2pScreenState): P2pExperienceState {
    val activeConnectionMode = state.activeConnectionMode
    val connected = state.connection?.groupFormed == true
    val isHost = state.connection?.isGroupOwner == true
    val directBusy = state.directDiscovering || state.directConnecting || state.directCreatingGroup
    val directReadyForExchange = state.sessionReady && !state.directTargetIp.isNullOrBlank()
    val directTargetLabel = state.directTargetLabel ?: state.directTargetIp
    val queueRunningCount = state.sendQueue.count { it.status == SendQueueStatus.RUNNING }
    val queueFailedCount = state.sendQueue.count { it.status == SendQueueStatus.FAILED }
    val messageFailedCount = state.chatMessages.count {
        it.direction == ChatMessageDirection.OUTGOING && it.status == ChatMessageStatus.FAILED
    }
    val hasAnyExchange = state.chatMessages.isNotEmpty() ||
        state.history.isNotEmpty() ||
        state.sendBatchTotal > 0
    val alternateConnectionMode = if (activeConnectionMode == ConnectionMode.WIFI_DIRECT) {
        ConnectionMode.LAN
    } else {
        ConnectionMode.WIFI_DIRECT
    }
    val alternateModeEnabled = when (alternateConnectionMode) {
        ConnectionMode.WIFI_DIRECT -> state.wifiDirectModeEnabled
        ConnectionMode.LAN -> state.lanModeEnabled
    }
    val lanReadyForExchange = state.sessionReady && state.lanConnected && (
        !state.resolvedTargetIp.isNullOrBlank() ||
            !state.suggestedTargetIp.isNullOrBlank() ||
            state.knownServicePeers.any { it.ip.isNotBlank() }
        )
    val connectionReadyForFlow = when (activeConnectionMode) {
        ConnectionMode.WIFI_DIRECT -> directReadyForExchange
        ConnectionMode.LAN -> lanReadyForExchange || directReadyForExchange
    }
    val nextStageAfterConnect = when {
        state.selectedFilesCount > 0 || state.sendQueue.isNotEmpty() -> FocusStage.SEND
        state.chatDraft.isNotBlank() || state.chatMessages.isNotEmpty() -> FocusStage.CHAT
        else -> FocusStage.SEND
    }
    val connectionLabel = activeConnectionMode.title
    val headerSummary = when (activeConnectionMode) {
        ConnectionMode.WIFI_DIRECT -> when {
            connected && directReadyForExchange && isHost -> "Wi-Fi Direct listo con ${directTargetLabel ?: "tu equipo"}."
            connected && directReadyForExchange -> "Directo listo con ${directTargetLabel ?: "tu equipo"}."
            connected && isHost -> "Enlace creado en este equipo."
            connected -> "Preparando el enlace directo."
            state.directCreatingGroup -> "Creando un enlace en este equipo."
            state.directConnecting -> "Uniéndote al enlace directo."
            state.directDiscovering && state.peers.isNotEmpty() -> "Elige un equipo para unirte."
            state.directDiscovering -> "Buscando equipos con Wi-Fi Direct."
            !state.permissionGranted -> "Falta permiso para usar Wi-Fi Direct."
            !state.p2pEnabled -> "Abre Wi-Fi del sistema para usar Wi-Fi Direct."
            else -> "Wi-Fi Direct listo para crear o buscar un enlace."
        }

        ConnectionMode.LAN -> when {
            state.lanScanning -> "Buscando equipos en esta misma Wi-Fi."
            state.lanConnected -> "Wi-Fi normal lista en ${state.lanLocalIp ?: "esta red"}."
            else -> "Conecta ambos equipos a la misma red Wi-Fi."
        }
    }
    val alternateModeHint = when {
        !alternateModeEnabled -> null
        alternateConnectionMode == ConnectionMode.WIFI_DIRECT && connected -> "Tambien hay enlace directo disponible."
        alternateConnectionMode == ConnectionMode.WIFI_DIRECT && state.p2pEnabled -> "Wi-Fi Direct sigue disponible como alternativa."
        alternateConnectionMode == ConnectionMode.LAN && state.lanConnected -> "Wi-Fi normal disponible como alternativa."
        alternateConnectionMode == ConnectionMode.LAN -> "Tambien puedes usar la misma red Wi-Fi."
        else -> null
    }
    val primaryAction = when {
        state.sessionExpired -> P2pExperienceAction("Renovar sesión", P2pExperienceCommand.RENEW_SESSION)

        connectionReadyForFlow && state.tokenSyncStatus.contains("no se pudo", ignoreCase = true) -> {
            P2pExperienceAction("Sincronizar sesión", P2pExperienceCommand.SYNC_TOKEN)
        }

        connectionReadyForFlow && state.selectedFilesCount > 0 -> {
            P2pExperienceAction("Continuar con envío", P2pExperienceCommand.CONTINUE_FLOW)
        }

        connectionReadyForFlow && (state.chatDraft.isNotBlank() || state.chatMessages.isNotEmpty()) -> {
            P2pExperienceAction("Abrir chat", P2pExperienceCommand.CONTINUE_FLOW)
        }

        connectionReadyForFlow -> {
            P2pExperienceAction("Continuar", P2pExperienceCommand.CONTINUE_FLOW)
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && !state.permissionGranted -> {
            P2pExperienceAction("Conceder permiso", P2pExperienceCommand.REQUEST_PERMISSION)
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && !state.p2pEnabled -> {
            P2pExperienceAction("Abrir Wi-Fi", P2pExperienceCommand.OPEN_WIFI_SETTINGS)
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && connected && isHost && directReadyForExchange -> {
            P2pExperienceAction("Continuar", P2pExperienceCommand.CONTINUE_FLOW)
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && connected && isHost -> {
            P2pExperienceAction("Cancelar enlace", P2pExperienceCommand.CANCEL_DIRECT)
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && connected && !isHost -> {
            P2pExperienceAction("Preparando Directo", P2pExperienceCommand.REFRESH_STATE, enabled = false)
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && directBusy -> {
            P2pExperienceAction("Cancelar", P2pExperienceCommand.CANCEL_DIRECT)
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT -> {
            P2pExperienceAction("Buscar enlace", P2pExperienceCommand.START_CLIENT)
        }

        activeConnectionMode == ConnectionMode.LAN && state.lanScanning -> {
            P2pExperienceAction("Cancelar búsqueda", P2pExperienceCommand.CANCEL_LAN_SCAN)
        }

        activeConnectionMode == ConnectionMode.LAN && !state.lanConnected -> {
            P2pExperienceAction("Conectar a Wi-Fi", P2pExperienceCommand.OPEN_WIFI_SETTINGS)
        }

        activeConnectionMode == ConnectionMode.LAN &&
            !state.suggestedTargetIp.isNullOrBlank() &&
            state.resolvedTargetIp.isNullOrBlank() -> {
            P2pExperienceAction("Usar IP sugerida", P2pExperienceCommand.USE_SUGGESTED_TARGET)
        }

        activeConnectionMode == ConnectionMode.LAN &&
            state.selectedFilesCount > 0 &&
            !state.resolvedTargetIp.isNullOrBlank() -> {
            P2pExperienceAction("Enviar ahora", P2pExperienceCommand.SEND_NOW)
        }

        else -> P2pExperienceAction("Buscar dispositivos", P2pExperienceCommand.SCAN_LAN)
    }
    val onboardingStep1Done = when (activeConnectionMode) {
        ConnectionMode.WIFI_DIRECT -> state.permissionGranted
        ConnectionMode.LAN -> state.lanConnected || directReadyForExchange
    }
    val onboardingStep2Done = when (activeConnectionMode) {
        ConnectionMode.WIFI_DIRECT -> directReadyForExchange
        ConnectionMode.LAN -> lanReadyForExchange || directReadyForExchange
    }
    val onboardingStep3Done = hasAnyExchange
    val onboardingAllDone = onboardingStep1Done && onboardingStep2Done && onboardingStep3Done
    val onboardingActiveStep = when {
        !onboardingStep1Done -> 1
        !onboardingStep2Done -> 2
        !onboardingStep3Done -> 3
        else -> 0
    }
    val supportBanner = when {
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && !state.permissionGranted -> P2pSupportBannerState(
            title = "Permisos pendientes",
            body = "Sin permiso de red cercana no puedes usar Wi-Fi Direct.",
            action = P2pExperienceAction("Conceder permiso", P2pExperienceCommand.REQUEST_PERMISSION),
            isError = true
        )

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && !state.p2pEnabled -> P2pSupportBannerState(
            title = "Wi-Fi apagado",
            body = "Android gestiona Wi-Fi Direct desde el sistema. Primero abre Wi-Fi y luego vuelve aquí.",
            action = P2pExperienceAction("Abrir Wi-Fi", P2pExperienceCommand.OPEN_WIFI_SETTINGS),
            isError = true
        )

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directCreatingGroup -> P2pSupportBannerState(
            title = "Enlace creado en este equipo",
            body = "En el otro equipo, toca Buscar enlace y luego Conectar.",
            action = P2pExperienceAction("Cancelar enlace", P2pExperienceCommand.CANCEL_DIRECT)
        )

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directConnecting -> P2pSupportBannerState(
            title = "Uniéndote al enlace",
            body = "Espera mientras ambos equipos terminan de enlazarse.",
            action = P2pExperienceAction("Cancelar", P2pExperienceCommand.CANCEL_DIRECT)
        )

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directDiscovering && state.peers.isEmpty() -> P2pSupportBannerState(
            title = "Buscando equipos",
            body = "Mantén el otro equipo con el enlace abierto. Cuando aparezca, toca Conectar.",
            action = P2pExperienceAction("Cancelar", P2pExperienceCommand.CANCEL_DIRECT)
        )

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && !connected -> P2pSupportBannerState(
            title = "Elige el rol de este equipo",
            body = "Si este equipo inicia, toca Crear enlace. Si el otro ya inició, toca Buscar enlace.",
            action = P2pExperienceAction("Buscar enlace", P2pExperienceCommand.START_CLIENT)
        )

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && connected && isHost && !directReadyForExchange -> P2pSupportBannerState(
            title = "Enlace creado en este equipo",
            body = "En el otro equipo, toca Buscar enlace para terminar la conexión.",
            action = P2pExperienceAction("Refrescar estado", P2pExperienceCommand.REFRESH_STATE)
        )

        activeConnectionMode == ConnectionMode.LAN && !state.lanConnected && !directReadyForExchange -> P2pSupportBannerState(
            title = "Falta red Wi-Fi compartida",
            body = "Conecta ambos equipos a la misma red Wi-Fi para descubrir equipos por IP.",
            action = P2pExperienceAction("Abrir ajustes Wi-Fi", P2pExperienceCommand.OPEN_WIFI_SETTINGS),
            isError = true
        )

        activeConnectionMode == ConnectionMode.LAN && !lanReadyForExchange && !directReadyForExchange -> P2pSupportBannerState(
            title = "Prepara esta misma Wi-Fi",
            body = "Busca dispositivos o usa una IP sugerida.",
            action = if (state.suggestedTargetIp.isNullOrBlank()) {
                P2pExperienceAction("Buscar dispositivos", P2pExperienceCommand.SCAN_LAN)
            } else {
                P2pExperienceAction("Usar IP sugerida", P2pExperienceCommand.USE_SUGGESTED_TARGET)
            }
        )

        state.pendingCredentialShare != null -> P2pSupportBannerState(
            title = "Solicitud de conexión",
            body = "${state.pendingCredentialShare.label} quiere compartir la sesión.",
            action = P2pExperienceAction("Revisar solicitud", P2pExperienceCommand.REVIEW_TRUST)
        )

        state.sessionExpired -> P2pSupportBannerState(
            title = "Sesión expirada",
            body = "Renueva la sesión para seguir usando chat y transferencias.",
            action = P2pExperienceAction("Renovar sesión", P2pExperienceCommand.RENEW_SESSION),
            isError = true
        )

        !state.sendFailureCause.isNullOrBlank() || queueFailedCount > 0 -> P2pSupportBannerState(
            title = "Hay fallos en la cola",
            body = "Revisa los elementos fallidos y reintenta desde la vista dedicada.",
            action = P2pExperienceAction("Abrir cola", P2pExperienceCommand.OPEN_QUEUE),
            isError = true
        )

        state.selectedFilesCount > 0 && state.resolvedTargetIp.isNullOrBlank() -> P2pSupportBannerState(
            title = "Destino no resuelto",
            body = "Seleccionaste archivos, pero aún no hay una IP de destino válida.",
            action = if (state.suggestedTargetIp.isNullOrBlank()) {
                P2pExperienceAction("Refrescar estado", P2pExperienceCommand.REFRESH_STATE)
            } else {
                P2pExperienceAction("Usar destino sugerido", P2pExperienceCommand.USE_SUGGESTED_TARGET)
            },
            isError = true
        )

        state.selectedFilesCount > 0 && !state.sessionExpired -> P2pSupportBannerState(
            title = "Listo para enviar",
            body = "Ya puedes iniciar el envío al equipo conectado.",
            action = P2pExperienceAction("Enviar ahora", P2pExperienceCommand.SEND_NOW)
        )

        messageFailedCount > 0 -> P2pSupportBannerState(
            title = "Mensajes por reintentar",
            body = "Hay mensajes fallidos en el chat.",
            action = P2pExperienceAction("Abrir mensajes", P2pExperienceCommand.OPEN_MESSAGES),
            isError = true
        )

        !onboardingAllDone -> {
            val action = when (onboardingActiveStep) {
                1 -> if (activeConnectionMode == ConnectionMode.WIFI_DIRECT) {
                    P2pExperienceAction("Conceder permiso", P2pExperienceCommand.REQUEST_PERMISSION)
                } else {
                    P2pExperienceAction("Abrir Wi-Fi", P2pExperienceCommand.OPEN_WIFI_SETTINGS)
                }

                2 -> when (activeConnectionMode) {
                    ConnectionMode.WIFI_DIRECT -> if (state.p2pEnabled) {
                        P2pExperienceAction("Buscar enlace", P2pExperienceCommand.START_CLIENT)
                    } else {
                        P2pExperienceAction("Abrir Wi-Fi", P2pExperienceCommand.OPEN_WIFI_SETTINGS)
                    }

                    ConnectionMode.LAN -> if (state.suggestedTargetIp.isNullOrBlank()) {
                        P2pExperienceAction(
                            if (state.lanScanning) "Cancelar búsqueda" else "Buscar dispositivos",
                            if (state.lanScanning) {
                                P2pExperienceCommand.CANCEL_LAN_SCAN
                            } else {
                                P2pExperienceCommand.SCAN_LAN
                            }
                        )
                    } else {
                        P2pExperienceAction("Usar IP sugerida", P2pExperienceCommand.USE_SUGGESTED_TARGET)
                    }
                }

                3 -> when {
                    state.sessionExpired -> P2pExperienceAction("Renovar sesión", P2pExperienceCommand.RENEW_SESSION)
                    !isHost && state.tokenSyncStatus.contains("no se pudo", ignoreCase = true) -> {
                        P2pExperienceAction("Sincronizar sesión", P2pExperienceCommand.SYNC_TOKEN)
                    }

                    else -> P2pExperienceAction("Ir a Mensajes", P2pExperienceCommand.OPEN_MESSAGES)
                }

                else -> P2pExperienceAction("Refrescar estado", P2pExperienceCommand.REFRESH_STATE)
            }
            P2pSupportBannerState(
                title = when (onboardingActiveStep) {
                    1 -> if (activeConnectionMode == ConnectionMode.WIFI_DIRECT) "Permisos pendientes" else "Conecta a la misma red"
                    2 -> if (activeConnectionMode == ConnectionMode.WIFI_DIRECT) "Conecta por Wi-Fi Direct" else "Prepara esta misma Wi-Fi"
                    3 -> "Listo para probar"
                    else -> "Siguiente paso"
                },
                body = when (onboardingActiveStep) {
                    1 -> if (activeConnectionMode == ConnectionMode.WIFI_DIRECT) {
                        "Sin permiso de red cercana no se detectan ni conectan dispositivos por Wi-Fi Direct."
                    } else {
                        "Conecta ambos equipos a la misma red Wi-Fi para usar IP y detección LAN."
                    }

                    2 -> if (activeConnectionMode == ConnectionMode.WIFI_DIRECT) {
                        "Primero abre Wi-Fi del sistema. Luego crea o busca un enlace."
                    } else {
                        "Busca dispositivos en esta misma Wi‑Fi. El canal Wi‑Fi sale por aquí."
                    }

                    3 -> "Ya puedes probar chat, envío o historial sin salir de esta pantalla."
                    else -> ""
                },
                action = action,
                isError = onboardingActiveStep == 1
            )
        }

        else -> null
    }
    val headerHint = when {
        state.sessionExpired -> "Renueva la sesión para continuar con chat y transferencias."
        state.sending -> "Enviando archivos en segundo plano. Puedes revisar la cola."
        state.receiving -> "Recibiendo archivos en segundo plano."
        state.pendingCredentialShare != null -> "Hay una solicitud de credenciales pendiente."
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directCreatingGroup ->
            "Este equipo ya creó el enlace. En el otro equipo, toca Buscar enlace."
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directConnecting ->
            "Espera mientras este equipo se une al enlace."
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directDiscovering && state.peers.isNotEmpty() ->
            "Elige el equipo que creó el enlace y toca Conectar."
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directDiscovering ->
            "Busca el otro equipo para unirte a su enlace."
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && !state.p2pEnabled ->
            "Android gestiona Wi-Fi Direct desde el sistema. Abre Wi-Fi y vuelve aquí."
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && !connected -> "Si este equipo inicia, toca Crear enlace. Si el otro ya inició, toca Buscar enlace."
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && connected && isHost && !directReadyForExchange ->
            "Este equipo ya creó el enlace. Falta que el otro equipo se una."
        activeConnectionMode == ConnectionMode.LAN && !state.lanConnected && !directReadyForExchange ->
            "Conecta ambos equipos a la misma red Wi-Fi."
        activeConnectionMode == ConnectionMode.LAN && state.resolvedTargetIp.isNullOrBlank() -> {
            "Busca equipos en esta misma Wi-Fi o usa una IP conocida."
        }

        else -> "Usa la pestaña activa o la acción principal del encabezado."
    }
    val connectionNarrative = when (activeConnectionMode) {
        ConnectionMode.WIFI_DIRECT -> if (!state.p2pEnabled) {
            "Primero abre Wi-Fi del sistema. Cuando Wi-Fi Direct esté disponible, podrás crear o buscar un enlace."
        } else if (connected) {
            if (directReadyForExchange) {
                "Cuando Directo está enlazado, Qetara lo usa primero para mensajes y archivos."
            } else {
                "Este equipo ya creó el enlace, pero aún falta que el otro termine de unirse."
            }
        } else if (state.directDiscovering && state.peers.isNotEmpty()) {
            "Elige el equipo que creó el enlace y toca Conectar para terminar la unión."
        } else if (state.directDiscovering) {
            "Busca el otro equipo para unirte a su enlace directo."
        } else {
            "Usa Crear enlace si este equipo inicia. Usa Buscar enlace si el otro ya inició."
        }

        ConnectionMode.LAN -> if (directReadyForExchange) {
            "Aunque estés viendo Wi-Fi normal, Directo ya quedó listo y se usará primero para enviar y chatear."
        } else if (state.lanConnected) {
            "Usa la misma red Wi-Fi para descubrir equipos por IP y compartir con PC u otros equipos."
        } else {
            "Usa Wi-Fi normal cuando ambos equipos comparten la misma red local."
        }
    }

    return P2pExperienceState(
        activeConnectionMode = activeConnectionMode,
        alternateConnectionMode = alternateConnectionMode,
        alternateModeEnabled = alternateModeEnabled,
        connectionLabel = connectionLabel,
        headerSummary = headerSummary,
        headerHint = headerHint,
        connectionNarrative = connectionNarrative,
        alternateModeHint = alternateModeHint,
        primaryAction = primaryAction,
        supportBanner = supportBanner,
        connectionReadyForFlow = connectionReadyForFlow,
        lanReadyForExchange = lanReadyForExchange,
        nextStageAfterConnect = nextStageAfterConnect,
        showWifiDirectSection = activeConnectionMode == ConnectionMode.WIFI_DIRECT,
        showLanSection = activeConnectionMode == ConnectionMode.LAN
    )
}

internal fun deriveP2pChatExperienceState(state: P2pScreenState): P2pChatExperienceState {
    val p2pLinked = state.connection?.groupFormed == true
    val isP2pHost = state.connection?.isGroupOwner == true
    val activeConnectionMode = state.activeConnectionMode
    val effectiveChatChannel = state.chatChannel
    val globalReady = state.lanConnected && state.globalLanJoined
    val directTargetCount = state.chatDirectTargetIps.size
    val directReady = directTargetCount > 0
    val directTargetLabel = state.chatDirectTargetLabel ?: state.chatDirectTargetIp
    val directChatMode = state.chatDirectTargetMode ?: state.activeConnectionMode
    val transportLabel = when (effectiveChatChannel) {
        ChatChannel.DIRECT -> "Chat directo"
        ChatChannel.GLOBAL -> "Canal Wi‑Fi"
    }
    val transportStatus = when {
        effectiveChatChannel == ChatChannel.GLOBAL && globalReady -> {
            if (state.globalChatPeerCount > 0) {
                "Canal actual: Wi‑Fi · ${if (state.globalChatPeerCount == 1) "1 equipo" else "${state.globalChatPeerCount} equipos"}"
            } else {
                "Canal actual: Wi‑Fi · solo tú"
            }
        }
        effectiveChatChannel == ChatChannel.GLOBAL && !state.lanConnected -> "Canal actual: sin Wi‑Fi"
        effectiveChatChannel == ChatChannel.GLOBAL && !state.globalLanJoined -> "Canal actual: fuera del canal Wi‑Fi"
        effectiveChatChannel == ChatChannel.GLOBAL -> "Canal actual: Wi‑Fi · solo tú"
        directReady && directChatMode == ConnectionMode.WIFI_DIRECT -> {
            when {
                directTargetCount > 1 -> "Canal actual: Directo · Wi‑Fi Direct · $directTargetCount equipos"
                directTargetLabel != null -> "Canal actual: Directo · Wi‑Fi Direct · $directTargetLabel"
                else -> "Canal actual: Directo · Wi‑Fi Direct"
            }
        }
        directReady && directChatMode == ConnectionMode.LAN -> {
            "Canal actual: Directo · Wi-Fi LAN${directTargetLabel?.let { " · $it" } ?: ""}"
        }
        else -> "Canal actual: sin destino"
    }
    val transportHint = when {
        state.sessionExpired -> "Sesión expirada: renueva la sesión para reactivar el chat."
        effectiveChatChannel == ChatChannel.GLOBAL && !state.lanConnected -> {
            "Conecta este equipo a una red Wi‑Fi para usar el canal."
        }

        effectiveChatChannel == ChatChannel.GLOBAL && !state.globalLanJoined -> {
            "Entra al canal Wi‑Fi para participar."
        }

        effectiveChatChannel == ChatChannel.GLOBAL && state.globalChatPeerCount <= 0 -> {
            "Puedes escribir aunque todavía estés solo."
        }

        effectiveChatChannel == ChatChannel.GLOBAL -> {
            "Envía texto a los equipos que ya entraron al canal Wi‑Fi."
        }

        directReady && directChatMode == ConnectionMode.WIFI_DIRECT -> {
            when {
                directTargetCount > 1 -> "Tu chat directo por Wi‑Fi Direct está listo para $directTargetCount equipos."
                directTargetLabel != null -> "Tu chat directo por Wi-Fi Direct ya está listo con $directTargetLabel."
                else -> "Tu chat directo por Wi‑Fi Direct ya está listo."
            }
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && p2pLinked && isP2pHost -> {
            "Tu enlace directo ya está creado. Falta que el otro equipo se una para abrir el chat directo."
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && p2pLinked -> {
            "El modo actual es Wi-Fi Direct. El canal ya esta listo para chatear."
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT -> {
            "El modo actual es Wi-Fi Direct. Primero conecta el otro equipo para habilitar el chat."
        }

        activeConnectionMode == ConnectionMode.LAN && !state.lanConnected -> {
            "El modo actual es Wi-Fi normal. Conecta ambos equipos a la misma red."
        }

        activeConnectionMode == ConnectionMode.LAN && !directReady -> {
            "La red ya está lista, pero falta un equipo para chatear 1 a 1."
        }

        else -> "El canal directo sobre Wi-Fi LAN ya está listo."
    }
    val showSyncAction = !state.sessionExpired && (
        when (effectiveChatChannel) {
            ChatChannel.DIRECT -> {
                (activeConnectionMode == ConnectionMode.WIFI_DIRECT && directReady && !isP2pHost) ||
                    (activeConnectionMode == ConnectionMode.LAN && state.lanConnected && !state.chatDirectTargetIp.isNullOrBlank())
            }

            ChatChannel.GLOBAL -> state.lanConnected && state.globalLanJoined && state.globalChatPeerCount > 0
        }
    )
    return P2pChatExperienceState(
        transportLabel = transportLabel,
        transportStatus = transportStatus,
        transportHint = transportHint,
        showSyncAction = showSyncAction
    )
}
