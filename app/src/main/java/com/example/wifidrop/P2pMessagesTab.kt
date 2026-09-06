package com.example.wifidrop
import android.content.Intent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun P2pMessagesTab(
    state: P2pScreenState,
    activeChannel: ChatChannel,
    experience: P2pChatExperienceState,
    onSetGlobalLanJoined: (Boolean) -> Unit,
    onAutoDownloadChannelFilesChange: (Boolean) -> Unit,
    onOpenConnectTab: () -> Unit,
    onChatDraftChange: (String) -> Unit,
    onSelectChatDirectPeer: (String) -> Unit,
    onDownloadChannelFileOffer: (ChatMessageEntry) -> Unit,
    onPickFile: () -> Unit,
    onClearSelectedFiles: () -> Unit,
    onSendMessage: () -> Unit,
    onRetryMessage: (String) -> Unit,
    onCancelQueuedMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onClearMessages: (ChatChannel) -> Unit,
    onSyncToken: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var composerMoreExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmClearChat by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    val emptyScrollState = rememberScrollState()
    val queuePendingCount = state.sendQueue.count {
        it.status == SendQueueStatus.QUEUED ||
            it.status == SendQueueStatus.PAUSED ||
            it.status == SendQueueStatus.RETRY_WAIT
    }
    val queueRunningCount = state.sendQueue.count { it.status == SendQueueStatus.RUNNING }
    val queueFailedCount = state.sendQueue.count { it.status == SendQueueStatus.FAILED }
    val p2pLinked = state.connection?.groupFormed == true
    val isP2pHost = state.connection?.isGroupOwner == true
    val isGlobalChat = activeChannel == ChatChannel.GLOBAL
    val channelTitle = when {
        isGlobalChat -> "Canal Wi‑Fi"
        else -> "Chat"
    }
    val globalLanJoined = state.globalLanJoined
    val globalDeviceCountLabel = deviceCountLabel(state.globalChatPeerCount)
    val directLanPeers = state.knownServicePeers
        .filter { it.ip.isNotBlank() }
        .distinctBy { it.ip }
        .sortedWith(compareByDescending<KnownPeerSnapshot> { it.trusted }.thenByDescending { it.lastSeenAtMs })
        .take(8)
    val directWifiPeers = state.chatDirectAvailablePeers
        .filter { it.ip.isNotBlank() }
        .distinctBy { it.ip }
        .sortedWith(compareByDescending<KnownPeerSnapshot> { it.trusted }.thenByDescending { it.lastSeenAtMs })
        .take(12)
    val activeScope = when {
        isGlobalChat -> ChatMessageScope.GLOBAL_LAN
        else -> ChatMessageScope.DIRECT
    }
    val filteredMessages = state.chatMessages.filter { entry ->
        entry.scope == activeScope
    }
    val recentMessages = filteredMessages.take(120)
    val directChatMode = state.chatDirectTargetMode ?: state.activeConnectionMode
    val isDirectWifiMode = !isGlobalChat && directChatMode == ConnectionMode.WIFI_DIRECT
    val directTargetCount = state.chatDirectTargetIps.size
    val directTargetLabel = state.chatDirectTargetLabel ?: state.chatDirectTargetIp
    val directSelectionSummary = when {
        directTargetCount > 1 -> "$directTargetCount equipos"
        directTargetLabel != null -> directTargetLabel
        else -> state.chatDirectTargetIps.firstOrNull()
    }
    val directChannelReady = when {
        directChatMode == ConnectionMode.WIFI_DIRECT -> state.chatDirectTargetIps.isNotEmpty()
        else -> !state.chatDirectTargetIp.isNullOrBlank()
    }
    val globalChannelReady = state.lanConnected && globalLanJoined
    val channelReady = when {
        isGlobalChat -> globalChannelReady
        else -> directChannelReady
    }
    val showChannelJoinPrompt = isGlobalChat && !channelReady
    val hasMessages = recentMessages.isNotEmpty()
    val hasComposerPayload = state.chatDraft.isNotBlank() || state.selectedFilesCount > 0
    val sendButtonLabel = "Enviar"
    val directNotReadyTitle = when {
        isDirectWifiMode && !state.permissionGranted ->
            "Falta permiso"
        isDirectWifiMode && !state.p2pEnabled ->
            "Abre Wi-Fi del sistema"
        isDirectWifiMode && state.directCreatingGroup ->
            "Enlace creado en este equipo"
        isDirectWifiMode && state.directConnecting ->
            "Uniéndote al enlace"
        isDirectWifiMode && p2pLinked && directWifiPeers.isNotEmpty() ->
            "Elige uno o varios equipos"
        isDirectWifiMode && p2pLinked ->
            "Esperando participantes"
        isDirectWifiMode && state.directDiscovering && state.peers.isNotEmpty() ->
            "Elige un equipo"
        isDirectWifiMode && state.directDiscovering ->
            "Buscando equipos"
        isDirectWifiMode ->
            "Directo todavía no está listo"
        else ->
            "Elige un equipo"
    }
    val directNotReadyBody = when {
        isDirectWifiMode && !state.permissionGranted ->
            "Concede el permiso y luego vuelve a Conectar para completar el enlace."
        isDirectWifiMode && !state.p2pEnabled ->
            "La app no puede encender Wi-Fi Direct. Abre Wi-Fi del sistema y luego crea o busca un enlace."
        isDirectWifiMode && state.directCreatingGroup ->
            "En el otro equipo, toca Buscar enlace y luego Conectar."
        isDirectWifiMode && state.directConnecting ->
            "Espera mientras ambos equipos terminan de enlazarse."
        isDirectWifiMode && p2pLinked && directWifiPeers.isNotEmpty() ->
            "Elige a quién enviar dentro del grupo Wi‑Fi Direct."
        isDirectWifiMode && p2pLinked ->
            "Cuando los equipos compartan sesión aparecerán aquí para elegirlos."
        isDirectWifiMode && state.directDiscovering && state.peers.isNotEmpty() ->
            "Abre Conectar, elige el equipo correcto y toca Conectar."
        isDirectWifiMode && state.directDiscovering ->
            "Mantén abierto el enlace en el otro equipo. La lista se actualizará sola."
        isDirectWifiMode ->
            "Primero crea o busca un enlace para habilitar el chat directo."
        else ->
            "Primero elige el equipo con el que vas a chatear."
    }
    val showDirectSetupCard = !isGlobalChat && !directChannelReady
    val composerPlaceholder = when {
        state.selectedFilesCount > 0 -> "Añade un mensaje opcional..."
        isGlobalChat -> "Mensaje para el canal Wi‑Fi"
        showDirectSetupCard -> "Directo se habilita cuando el enlace esté listo"
        directTargetCount > 1 -> "Mensaje para $directTargetCount equipos"
        !directSelectionSummary.isNullOrBlank() -> "Mensaje para ${directSelectionSummary ?: "este equipo"}"
        else -> "Escribe un mensaje"
    }

    val sendDisabledReason = when {
        isGlobalChat && !state.lanConnected -> "El canal requiere estar en una red Wi‑Fi."
        isGlobalChat && !globalLanJoined -> "Entra al canal Wi‑Fi para escribir."
        isGlobalChat && state.selectedFilesCount > 0 && state.globalChatPeerCount <= 0 ->
            "No hay otros equipos en el canal para recibir archivos."
        isGlobalChat && state.chatDraft.isBlank() && state.selectedFilesCount <= 0 -> "Escribe un mensaje o adjunta archivos."
        !hasComposerPayload -> "Escribe un mensaje o adjunta archivos."
        state.sessionExpired -> "Sesión expirada. Renueva la sesión."
        !isGlobalChat && directChatMode == ConnectionMode.WIFI_DIRECT && state.chatDirectTargetIps.isEmpty() ->
            "Elige al menos un equipo del grupo Wi‑Fi Direct."
        !isGlobalChat && directChatMode == ConnectionMode.WIFI_DIRECT && state.selectedFilesCount > 0 && state.chatDirectTargetIps.size > 1 ->
            "Por ahora los adjuntos por Wi‑Fi Direct salen a un solo equipo."
        !isGlobalChat && directChatMode == ConnectionMode.WIFI_DIRECT &&
            state.selectedFilesCount > 0 &&
            !isP2pHost &&
            state.chatDirectTargetIps.singleOrNull() != state.connection?.groupOwnerAddress ->
            "Desde clientes, los archivos por Wi‑Fi Direct solo salen directo al anfitrión."
        !isGlobalChat && !directChannelReady -> when (directChatMode) {
            ConnectionMode.WIFI_DIRECT -> "Primero deja listo un equipo por Wi-Fi Direct."
            ConnectionMode.LAN -> "Elige un equipo para usar Directo por Wi-Fi LAN."
        }
        else -> null
    }
    val composerNotice = when {
        state.sessionExpired -> "Renueva la sesión para volver a enviar."
        isGlobalChat && state.selectedFilesCount > 0 && state.globalChatPeerCount <= 0 ->
            "Cuando haya otro equipo en el canal podrás enviarle archivos."
        isGlobalChat && state.selectedFilesCount > 0 ->
            "Se publicará una descarga para ${if (state.globalChatPeerCount == 1) "1 equipo" else "${state.globalChatPeerCount} equipos"} del canal."
        !isGlobalChat && directChatMode == ConnectionMode.WIFI_DIRECT && state.chatDirectTargetIps.isEmpty() -> "Elige a quién enviar."
        !isGlobalChat && !directChannelReady -> if (directChatMode == ConnectionMode.LAN) "Elige un equipo." else "Deja un equipo listo."
        else -> null
    }
    val chatStarterTitle = when {
        isGlobalChat -> channelTitle
        isDirectWifiMode -> "Chat directo"
        else -> "Directo por LAN"
    }
    val chatStarterBody = when {
        isGlobalChat && !state.lanConnected ->
            "Conecta este equipo a una red Wi‑Fi"
        isGlobalChat && !globalLanJoined ->
            "Toca Entrar para participar"
        isGlobalChat && state.globalChatPeerCount <= 0 ->
            "Solo tú en este canal"
        isGlobalChat ->
            globalDeviceCountLabel
        directTargetCount > 1 ->
            "$directTargetCount equipos quedaron listos. Puedes escribir o adjuntar archivos."
        directChannelReady ->
            "${directTargetLabel ?: "Equipo"} quedó listo. Puedes escribir o adjuntar archivos."
        else ->
            "Ningún equipo listo"
    }
    val chatHeaderSummary = when {
        isGlobalChat && !state.lanConnected -> "Sin red local"
        isGlobalChat && !globalLanJoined -> "No estás dentro"
        isGlobalChat && state.globalChatPeerCount <= 0 -> "Solo tú en este canal"
        isGlobalChat -> globalDeviceCountLabel
        directTargetCount > 1 -> "Directo · Wi‑Fi Direct · $directTargetCount equipos"
        directChannelReady -> buildString {
            append("1 a 1 · ")
            append(if (directChatMode == ConnectionMode.WIFI_DIRECT) "Wi-Fi Direct" else "Wi-Fi LAN")
            directTargetLabel?.let {
                append(" · ")
                append(it)
            }
        }
        else -> "1 a 1 · ${if (directChatMode == ConnectionMode.WIFI_DIRECT) "Wi-Fi Direct" else "Wi-Fi LAN"}"
    }
    val chatHeaderTitle = when {
        isGlobalChat -> channelTitle
        isDirectWifiMode -> "Chat directo"
        else -> "Directo por LAN"
    }
    val channelHelperText = when {
        isGlobalChat && !state.lanConnected -> "Conéctate a una red Wi‑Fi para ver el canal."
        isGlobalChat && !globalLanJoined -> "Entra para escribir en el canal de esta red."
        !isGlobalChat && !directChannelReady -> when (directChatMode) {
            ConnectionMode.WIFI_DIRECT -> when {
                !state.permissionGranted -> "Concede el permiso para usar Wi-Fi Direct."
                !state.p2pEnabled -> "Abre Wi-Fi del sistema para continuar."
                p2pLinked -> "Elige a uno o varios equipos del grupo."
                else -> "Crea o busca un enlace para usar Directo."
            }
            ConnectionMode.LAN -> "Elige un equipo para chatear 1 a 1 en esta Wi-Fi."
        }
        else -> null
    }
    val baseOperationalNotice = when {
        composerNotice != null -> composerNotice
        state.messageStatus.isNotBlank() -> state.messageStatus.take(120)
        else -> null
    }
    val operationalNotice = baseOperationalNotice?.takeIf {
        it != chatStarterBody && it != channelHelperText
    }
    val operationalNoticeIsError = operationalNotice != null && (
        state.sessionExpired ||
            (isGlobalChat && !state.lanConnected) ||
            state.messageStatus.contains("error", ignoreCase = true) ||
            state.messageStatus.contains("fall", ignoreCase = true) ||
            state.messageStatus.contains("no ", ignoreCase = true)
        )
    val tokenSyncNeedsManualAction =
        state.tokenSyncStatus.contains("reintent", ignoreCase = true) ||
            state.tokenSyncStatus.contains("no pude", ignoreCase = true) ||
            state.tokenSyncStatus.contains("fall", ignoreCase = true)
    val showSyncButton = experience.showSyncAction && (!isGlobalChat || tokenSyncNeedsManualAction)
    val queueHeadline = when {
        queueRunningCount > 0 -> "Cola activa"
        queueFailedCount > 0 -> "Requiere atención"
        queuePendingCount > 0 -> "Cola lista"
        else -> null
    }
    val queueSummary = buildList {
        if (queueRunningCount > 0) add("$queueRunningCount activo${if (queueRunningCount == 1) "" else "s"}")
        if (queuePendingCount > 0) add("$queuePendingCount pendiente${if (queuePendingCount == 1) "" else "s"}")
        if (queueFailedCount > 0) add("$queueFailedCount para reintentar")
    }.joinToString(" · ")
    val featuredQueueItem = state.sendQueue.firstOrNull()
    val sectionCardColors = CardDefaults.elevatedCardColors(
        containerColor = lerp(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant,
            0.12f
        )
    )
    val quietPanelColor = lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant,
        0.22f
    )

    LaunchedEffect(recentMessages.firstOrNull()?.id, recentMessages.size) {
        if (recentMessages.isNotEmpty() &&
            !listState.isScrollInProgress &&
            listState.firstVisibleItemIndex <= 1
        ) {
            listState.scrollToItem(0)
        }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize(),
        colors = sectionCardColors
    ) {
        val sectionLabel = when {
            showChannelJoinPrompt -> null
            showDirectSetupCard && hasMessages -> "Historial reciente"
            hasMessages -> "Mensajes"
            !showDirectSetupCard -> if (isGlobalChat) null else "Empieza aquí"
            else -> null
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(UiSpaceM),
            verticalArrangement = Arrangement.spacedBy(UiSpaceS)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        chatHeaderTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        chatHeaderSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isGlobalChat && globalLanJoined) {
                        TextButton(onClick = { onSetGlobalLanJoined(false) }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Salir")
                        }
                    }
                    if (filteredMessages.isNotEmpty()) {
                        TextButton(onClick = { confirmClearChat = true }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Limpiar")
                        }
                    }
                }
            }

            if (!isGlobalChat && directChatMode == ConnectionMode.LAN) {
                if (directLanPeers.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                        verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                    ) {
                        directLanPeers.forEach { peer ->
                            val selected = state.chatDirectTargetIp == peer.ip
                            val onClick = { onSelectChatDirectPeer(peer.ip) }
                            if (selected) {
                                Button(onClick = onClick) {
                                    Text(peer.label.ifBlank { peer.ip })
                                }
                            } else {
                                OutlinedButton(onClick = onClick) {
                                    Text(peer.label.ifBlank { peer.ip })
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "Busca dispositivos para elegir un equipo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isGlobalChat && directChatMode == ConnectionMode.WIFI_DIRECT) {
                if (directWifiPeers.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                        verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                    ) {
                        directWifiPeers.forEach { peer ->
                            val selected = state.chatDirectTargetIps.contains(peer.ip)
                            val onClick = { onSelectChatDirectPeer(peer.ip) }
                            if (selected) {
                                Button(onClick = onClick) {
                                    Text(peer.label.ifBlank { peer.ip })
                                }
                            } else {
                                OutlinedButton(onClick = onClick) {
                                    Text(peer.label.ifBlank { peer.ip })
                                }
                            }
                        }
                    }
                } else if (p2pLinked) {
                    Text(
                        "Cuando el grupo detecte más equipos aparecerán aquí.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showDirectSetupCard && hasMessages) {
                DirectChatSetupCard(
                    title = directNotReadyTitle,
                    body = directNotReadyBody
                )
                FilledTonalButton(
                    onClick = onOpenConnectTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ir a Conectar")
                }
            } else {
                if (!showChannelJoinPrompt) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                        verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                    ) {
                        if (!(isGlobalChat && channelReady)) {
                            StatusChip(
                                label = if (isGlobalChat) {
                                    when {
                                        !state.lanConnected -> "Sin Wi‑Fi"
                                        !globalLanJoined -> "Fuera del canal"
                                        state.globalChatPeerCount <= 0 -> "Solo tú"
                                        else -> globalDeviceCountLabel
                                    }
                                } else if (channelReady) {
                                    if (directTargetCount > 1) "$directTargetCount equipos" else "Equipo listo"
                                } else {
                                    "Sin equipo"
                                },
                                containerColor = if (channelReady) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = if (channelReady) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (state.sessionExpired || experience.showSyncAction || state.tokenSyncStatus.isNotBlank()) {
                            StatusChip(
                                label = when {
                                    state.sessionExpired -> "Sesión expirada"
                                    state.tokenSyncStatus.contains("aprob", ignoreCase = true) -> "Pendiente"
                                    state.tokenSyncStatus.contains("reintent", ignoreCase = true) ||
                                        state.tokenSyncStatus.contains("no pude", ignoreCase = true) ||
                                        state.tokenSyncStatus.contains("fall", ignoreCase = true) -> "Reintentar"
                                    state.tokenSyncStatus.contains("sincron", ignoreCase = true) -> "Sincronizando"
                                    else -> "Sesión"
                                },
                                containerColor = if (state.sessionExpired) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                },
                                contentColor = if (state.sessionExpired) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                }
                            )
                        }
                    }

                    if (channelHelperText != null) {
                        Text(
                            channelHelperText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (showSyncButton) {
                        OutlinedButton(onClick = onSyncToken) {
                            Text("Sincronizar sesión")
                        }
                    }

                    if (isGlobalChat) {
                        ChannelDownloadSettingsCard(
                            autoDownload = state.autoDownloadChannelFiles,
                            onAutoDownloadChange = onAutoDownloadChannelFilesChange,
                            panelColor = quietPanelColor
                        )
                    }
                }
            }

            if (operationalNotice != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (operationalNoticeIsError) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.58f)
                    } else {
                        quietPanelColor
                    }
                ) {
                    Text(
                        operationalNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (operationalNoticeIsError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = UiSpaceM, vertical = 10.dp)
                    )
                }
            }

            if (state.sendQueue.isNotEmpty() && queueHeadline != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = quietPanelColor
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiSpaceM, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    queueHeadline,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (queueSummary.isNotBlank()) {
                                    Text(
                                        queueSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            StatusChip(
                                label = "${state.sendQueue.size}",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (featuredQueueItem != null) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        featuredQueueItem.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        (featuredQueueItem.peerLabel ?: featuredQueueItem.targetIp)
                                            .takeIf { it.isNotBlank() }
                                            ?: featuredQueueItem.targetIp,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusChip(
                                    label = queueStatusLabel(featuredQueueItem.status),
                                    containerColor = queueStatusColor(featuredQueueItem.status),
                                    contentColor = queueStatusContentColor(featuredQueueItem.status)
                                )
                            }
                        }
                    }
                }
            }

            if (sectionLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        sectionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (hasMessages) {
                        Text(
                            "${recentMessages.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (hasMessages) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                ) {
                    items(recentMessages, key = { it.id }) { item ->
                        ChatMessageCard(
                            item = item,
                            onRetryMessage = onRetryMessage,
                            onCancelQueuedMessage = onCancelQueuedMessage,
                            onDownloadChannelFileOffer = { onDownloadChannelFileOffer(item) },
                            onRequestDelete = { pendingDeleteMessageId = item.id },
                            onShareMessage = { shareChatMessage(context, item) }
                        )
                    }
                }
            } else if (showDirectSetupCard) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(emptyScrollState),
                    verticalArrangement = Arrangement.spacedBy(UiSpaceM)
                ) {
                    DirectChatSetupCard(
                        title = directNotReadyTitle,
                        body = directNotReadyBody
                    )
                    FilledTonalButton(
                        onClick = onOpenConnectTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ir a Conectar")
                    }
                }
            } else if (showChannelJoinPrompt) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(emptyScrollState),
                    verticalArrangement = Arrangement.spacedBy(UiSpaceM)
                ) {
                    WifiChannelJoinCard(
                        connectedToWifi = state.lanConnected,
                        peerCount = state.globalChatPeerCount,
                        onJoin = { onSetGlobalLanJoined(true) },
                        onOpenConnectTab = onOpenConnectTab
                    )
                    ChannelDownloadSettingsCard(
                        autoDownload = state.autoDownloadChannelFiles,
                        onAutoDownloadChange = onAutoDownloadChannelFilesChange,
                        panelColor = quietPanelColor
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(emptyScrollState),
                    verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                ) {
                    if (isGlobalChat) {
                        WifiChannelEmptyCard()
                    } else {
                        ChatStarterCard(
                            title = chatStarterTitle,
                            body = chatStarterBody,
                            isReady = channelReady,
                            isGlobal = false,
                            joined = false,
                            peerCount = 0
                        )
                    }
                }
            }

            if (!showDirectSetupCard && !showChannelJoinPrompt) {
                ChatComposerPanel(
                    draft = state.chatDraft,
                    selectedFileNames = state.selectedFileNames,
                    selectedFilesCount = state.selectedFilesCount,
                    placeholderText = composerPlaceholder,
                    sendButtonLabel = sendButtonLabel,
                    composerEnabled = if (isGlobalChat) {
                        state.lanConnected && globalLanJoined && !state.sessionExpired
                    } else {
                        directChannelReady && !state.sessionExpired
                    },
                    sendEnabled = sendDisabledReason == null,
                    attachEnabled = if (isGlobalChat) {
                        state.lanConnected && globalLanJoined && !state.sessionExpired
                    } else {
                        directChannelReady &&
                            !state.sessionExpired &&
                            when (directChatMode) {
                                ConnectionMode.LAN -> true
                                ConnectionMode.WIFI_DIRECT -> {
                                    if (isP2pHost) {
                                        true
                                    } else {
                                        state.chatDirectTargetIps.size == 1 &&
                                            state.chatDirectTargetIps.firstOrNull() == state.connection?.groupOwnerAddress
                                    }
                                }
                            }
                    },
                    onDraftChange = { onChatDraftChange(it.take(2_000)) },
                    onPickFile = onPickFile,
                    onClearSelectedFiles = onClearSelectedFiles,
                    onSend = onSendMessage,
                    onOpenMore = { composerMoreExpanded = true }
                )
            }

            DropdownMenu(
                expanded = composerMoreExpanded,
                onDismissRequest = { composerMoreExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Adjuntar archivos") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.AttachFile,
                            contentDescription = null
                        )
                    },
                    enabled = if (isGlobalChat) {
                        state.lanConnected && globalLanJoined && !state.sessionExpired
                    } else {
                        directChannelReady && !state.sessionExpired
                    },
                    onClick = {
                        composerMoreExpanded = false
                        onPickFile()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Limpiar texto") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = null
                        )
                    },
                    enabled = state.chatDraft.isNotBlank(),
                    onClick = {
                        composerMoreExpanded = false
                        onChatDraftChange("")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Quitar adjuntos") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null
                        )
                    },
                    enabled = state.selectedFilesCount > 0,
                    onClick = {
                        composerMoreExpanded = false
                        onClearSelectedFiles()
                    }
                )
            }
        }
    }

    if (pendingDeleteMessageId != null) {
        val pendingMessage = state.chatMessages.firstOrNull { it.id == pendingDeleteMessageId }
        AlertDialog(
            onDismissRequest = { pendingDeleteMessageId = null },
            title = { Text("Eliminar mensaje") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(UiSpaceS)) {
                    Text("Esta accion eliminara solo este mensaje.")
                    Text(
                        pendingMessage?.text?.take(160).orEmpty().ifBlank { "Mensaje sin contenido visible." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = pendingDeleteMessageId
                        pendingDeleteMessageId = null
                        if (id != null) onDeleteMessage(id)
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMessageId = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (confirmClearChat) {
        AlertDialog(
            onDismissRequest = { confirmClearChat = false },
            title = { Text("Limpiar chat") },
            text = {
                Text(
                    "Se eliminaran ${filteredMessages.size} mensajes de este canal. Esta accion no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearChat = false
                        onClearMessages(activeChannel)
                    }
                ) {
                    Text("Limpiar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearChat = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun DirectChatSetupCard(
    title: String,
    body: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpaceM),
            verticalArrangement = Arrangement.spacedBy(UiSpaceS)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WifiChannelJoinCard(
    connectedToWifi: Boolean,
    peerCount: Int,
    onJoin: () -> Unit,
    onOpenConnectTab: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpaceM),
            verticalArrangement = Arrangement.spacedBy(UiSpaceM)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (connectedToWifi) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (connectedToWifi) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        if (connectedToWifi) "Wi‑Fi listo" else "Sin Wi‑Fi",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Canal de esta Wi‑Fi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (connectedToWifi) {
                        if (peerCount > 0) {
                            "Entra para escribir a los equipos de esta red."
                        } else {
                            "Entra ahora. Los equipos aparecerán cuando también se unan."
                        }
                    } else {
                        "Conecta este equipo a una red Wi‑Fi para usar el canal."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (connectedToWifi) {
                Button(
                    onClick = onJoin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Entrar al canal Wi‑Fi")
                }
            } else {
                OutlinedButton(
                    onClick = onOpenConnectTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Ir a Conectar")
                }
            }
        }
    }
}

@Composable
private fun WifiChannelEmptyCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpaceM),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Sin mensajes todavía",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Lo que escribas aparecerá aquí para los equipos de esta Wi‑Fi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChatComposerPanel(
    draft: String,
    selectedFileNames: List<String>,
    selectedFilesCount: Int,
    placeholderText: String,
    sendButtonLabel: String,
    composerEnabled: Boolean,
    sendEnabled: Boolean,
    attachEnabled: Boolean,
    onDraftChange: (String) -> Unit,
    onPickFile: () -> Unit,
    onClearSelectedFiles: () -> Unit,
    onSend: () -> Unit,
    onOpenMore: () -> Unit
) {
    val attachmentsLabel = if (selectedFilesCount == 1) {
        "1 archivo listo"
    } else {
        "$selectedFilesCount archivos listos"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiSpaceS, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectedFilesCount > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AttachFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        attachmentsLabel,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Se enviarán con este mensaje.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(onClick = onClearSelectedFiles) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Quitar")
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            selectedFileNames.take(4).forEach { fileName ->
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Description,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            fileName,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (selectedFilesCount > 4) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Text(
                                        "+${selectedFilesCount - 4} más",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            TextField(
                value = draft,
                onValueChange = onDraftChange,
                enabled = composerEnabled,
                placeholder = {
                    Text(placeholderText)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    errorContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (attachEnabled || selectedFilesCount > 0) {
                    FilledTonalButton(
                        onClick = onPickFile,
                        enabled = attachEnabled && composerEnabled
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            if (selectedFilesCount == 0) "Adjuntar" else "Adjuntar más",
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Button(
                    onClick = onSend,
                    enabled = sendEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        sendButtonLabel,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                IconButton(onClick = onOpenMore) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = "Más acciones"
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatStarterCard(
    title: String,
    body: String,
    isReady: Boolean,
    isGlobal: Boolean,
    joined: Boolean,
    peerCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpaceM),
            verticalArrangement = Arrangement.spacedBy(UiSpaceS)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                verticalArrangement = Arrangement.spacedBy(UiSpaceS)
            ) {
                StatusChip(
                    label = if (isGlobal) {
                        when {
                            !joined -> "No estás dentro"
                            peerCount <= 0 -> "Solo tú"
                            else -> deviceCountLabel(peerCount)
                        }
                    } else if (isReady) {
                        "Listo"
                    } else {
                        "Sin equipo"
                    },
                    containerColor = if (isReady) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (isReady) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChannelDownloadSettingsCard(
    autoDownload: Boolean,
    onAutoDownloadChange: (Boolean) -> Unit,
    panelColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = panelColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = UiSpaceM, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Descargas del Canal Wi‑Fi",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Checkbox(
                    checked = autoDownload,
                    onCheckedChange = onAutoDownloadChange
                )
                Text(
                    "Descargar automáticamente archivos del canal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ChatMessageCard(
    item: ChatMessageEntry,
    onRetryMessage: (String) -> Unit,
    onCancelQueuedMessage: (String) -> Unit,
    onDownloadChannelFileOffer: () -> Unit,
    onRequestDelete: () -> Unit,
    onShareMessage: () -> Unit
) {
    var itemMoreExpanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val outgoing = item.direction == ChatMessageDirection.OUTGOING
    val channelFileOffer = (
        ChatMessageScopeCodec.decodeFromTransport(item.text)
            as? ChatMessageScopeCodec.DecodedChatPayload.FileOffer
        )?.offer
    val bubbleColor = when {
        outgoing && item.status == ChatMessageStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        outgoing && item.status == ChatMessageStatus.CANCELED -> MaterialTheme.colorScheme.surfaceVariant
        outgoing -> MaterialTheme.colorScheme.primaryContainer
        item.status == ChatMessageStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val bubbleContentColor = when {
        outgoing && item.status == ChatMessageStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        outgoing && item.status == ChatMessageStatus.CANCELED -> MaterialTheme.colorScheme.onSurfaceVariant
        outgoing -> MaterialTheme.colorScheme.onPrimaryContainer
        item.status == ChatMessageStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val statusChipLabel = chatStatusLabel(item.status)
    val statusChipColor = when (item.status) {
        ChatMessageStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        ChatMessageStatus.QUEUED,
        ChatMessageStatus.SENDING -> MaterialTheme.colorScheme.secondaryContainer
        ChatMessageStatus.PUBLISHED -> MaterialTheme.colorScheme.tertiaryContainer
        ChatMessageStatus.CANCELED -> MaterialTheme.colorScheme.surfaceVariant
        ChatMessageStatus.SENT,
        ChatMessageStatus.RECEIVED -> MaterialTheme.colorScheme.primaryContainer
    }
    val statusChipContentColor = when (item.status) {
        ChatMessageStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        ChatMessageStatus.QUEUED,
        ChatMessageStatus.SENDING -> MaterialTheme.colorScheme.onSecondaryContainer
        ChatMessageStatus.PUBLISHED -> MaterialTheme.colorScheme.onTertiaryContainer
        ChatMessageStatus.CANCELED -> MaterialTheme.colorScheme.onSurfaceVariant
        ChatMessageStatus.SENT,
        ChatMessageStatus.RECEIVED -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = bubbleColor,
            contentColor = bubbleContentColor,
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(
                modifier = Modifier.padding(UiSpaceS),
                verticalArrangement = Arrangement.spacedBy(UiSpaceS)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (outgoing) {
                                buildString {
                                    append("Tu")
                                    if (!item.peerLabel.isNullOrBlank()) {
                                        append(" → ")
                                        append(item.peerLabel)
                                    }
                                    append(" · ")
                                    append(formatHistoryTime(item.timestampMs))
                                }
                            } else {
                                "${item.peerLabel ?: "Equipo"} · ${formatHistoryTime(item.timestampMs)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (
                            item.status == ChatMessageStatus.FAILED ||
                            item.status == ChatMessageStatus.CANCELED ||
                            item.status == ChatMessageStatus.QUEUED ||
                            item.status == ChatMessageStatus.SENDING
                        ) {
                            StatusChip(
                                label = statusChipLabel,
                                containerColor = statusChipColor,
                                contentColor = statusChipContentColor
                            )
                        }
                    }
                }

                if (channelFileOffer != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (outgoing) {
                                "Tú compartiste:"
                            } else {
                                "${item.peerLabel ?: channelFileOffer.senderLabel} compartió:"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        channelFileOffer.fileName,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (channelFileOffer.fileSizeBytes >= 0L) {
                                            formatBytes(channelFileOffer.fileSizeBytes)
                                        } else {
                                            "Tamaño no disponible"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (!outgoing) {
                            Button(onClick = onDownloadChannelFileOffer) {
                                Text("Descargar")
                            }
                        }
                    }
                } else {
                    Text(item.text, style = MaterialTheme.typography.bodyMedium)
                }

                if (
                    item.status == ChatMessageStatus.SENT ||
                    item.status == ChatMessageStatus.RECEIVED ||
                    item.status == ChatMessageStatus.PUBLISHED
                ) {
                    Text(
                        statusChipLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = bubbleContentColor.copy(alpha = 0.82f)
                    )
                }

                val friendlyIssue = friendlyMessageIssue(item.errorCause)
                if (!friendlyIssue.isNullOrBlank()) {
                    Text(
                        friendlyIssue,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            outgoing && item.status == ChatMessageStatus.FAILED -> {
                OutlinedButton(onClick = { onRetryMessage(item.id) }) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("Reintentar envío")
                }
            }

            outgoing && (item.status == ChatMessageStatus.QUEUED || item.status == ChatMessageStatus.SENDING) -> {
                OutlinedButton(
                    onClick = { onCancelQueuedMessage(item.id) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        if (item.status == ChatMessageStatus.SENDING) {
                            "Cancelar envío"
                        } else {
                            "Quitar de cola"
                        }
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShareMessage) {
                Icon(
                    imageVector = Icons.Rounded.IosShare,
                    contentDescription = "Compartir mensaje"
                )
            }
            IconButton(onClick = onRequestDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Eliminar mensaje"
                )
            }
            if (outgoing && (item.status == ChatMessageStatus.FAILED || item.status == ChatMessageStatus.QUEUED || item.status == ChatMessageStatus.SENDING)) {
                Box {
                    IconButton(onClick = { itemMoreExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = "Más acciones"
                        )
                    }
                    DropdownMenu(
                        expanded = itemMoreExpanded,
                        onDismissRequest = { itemMoreExpanded = false }
                    ) {
                        if (outgoing && item.status == ChatMessageStatus.FAILED) {
                            DropdownMenuItem(
                                text = { Text("Reintentar envío") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    itemMoreExpanded = false
                                    onRetryMessage(item.id)
                                }
                            )
                        }
                        if (outgoing && (item.status == ChatMessageStatus.QUEUED || item.status == ChatMessageStatus.SENDING)) {
                            DropdownMenuItem(
                                text = { Text("Cancelar envío") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    itemMoreExpanded = false
                                    onCancelQueuedMessage(item.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun deviceCountLabel(count: Int): String {
    return if (count == 1) "1 equipo conectado" else "$count equipos conectados"
}

private fun shareChatMessage(context: android.content.Context, item: ChatMessageEntry) {
    val fileOffer = (
        ChatMessageScopeCodec.decodeFromTransport(item.text)
            as? ChatMessageScopeCodec.DecodedChatPayload.FileOffer
        )?.offer
    val shareBody = buildString {
        append("Qetara")
        append('\n')
        append(
            when (item.scope) {
                ChatMessageScope.DIRECT -> "Canal: Directo"
                ChatMessageScope.GLOBAL_LAN -> "Canal: Wi‑Fi"
                ChatMessageScope.DIRECT_CHANNEL -> "Canal: Wi‑Fi Direct"
            }
        )
        append('\n')
        append(
            if (item.direction == ChatMessageDirection.OUTGOING) {
                "Desde: Tú"
            } else {
                "Desde: ${item.peerLabel ?: "Equipo"}"
            }
        )
        append('\n')
        append("Hora: ${formatHistoryTime(item.timestampMs)}")
        append("\n\n")
        if (fileOffer != null) {
            append(
                if (item.direction == ChatMessageDirection.OUTGOING) {
                    "Tú compartiste:"
                } else {
                    "${item.peerLabel ?: fileOffer.senderLabel} compartió:"
                }
            )
            append('\n')
            append(fileOffer.fileName)
            append('\n')
            append(
                if (fileOffer.fileSizeBytes >= 0L) {
                    formatBytes(fileOffer.fileSizeBytes)
                } else {
                    "Tamaño no disponible"
                }
            )
        } else {
            append(item.text)
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareBody)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir desde Qetara").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

private fun friendlyMessageIssue(cause: String?): String? {
    val normalized = cause?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) return null
    return when {
        normalized.contains("cancelad") -> null
        normalized.contains("token") || normalized.contains("pin") || normalized.contains("sesion") || normalized.contains("sesión") -> {
            "La sesión ya no es válida. Renuévala y vuelve a intentar."
        }
        normalized.contains("timeout") || normalized.contains("timed out") || normalized.contains("tiempo") -> {
            "El otro equipo no respondió a tiempo."
        }
        normalized.contains("rechaz") || normalized.contains("refus") -> {
            "El otro equipo rechazó la conexión."
        }
        normalized.contains("wifi") || normalized.contains("network") || normalized.contains("socket") || normalized.contains("host") || normalized.contains("ip") -> {
            "No se pudo llegar al otro equipo."
        }
        else -> "No se pudo enviar este mensaje."
    }
}

@Composable
private fun queueStatusColor(status: SendQueueStatus): Color {
    return when (status) {
        SendQueueStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer
        SendQueueStatus.QUEUED,
        SendQueueStatus.PAUSED,
        SendQueueStatus.RETRY_WAIT -> MaterialTheme.colorScheme.secondaryContainer
        SendQueueStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
        SendQueueStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        SendQueueStatus.CANCELED -> MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
private fun queueStatusContentColor(status: SendQueueStatus): Color {
    return when (status) {
        SendQueueStatus.RUNNING -> MaterialTheme.colorScheme.onPrimaryContainer
        SendQueueStatus.QUEUED,
        SendQueueStatus.PAUSED,
        SendQueueStatus.RETRY_WAIT -> MaterialTheme.colorScheme.onSecondaryContainer
        SendQueueStatus.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
        SendQueueStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        SendQueueStatus.CANCELED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
