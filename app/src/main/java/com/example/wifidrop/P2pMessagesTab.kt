package com.example.wifidrop
import android.content.Intent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
    val chatCoroutineScope = rememberCoroutineScope()
    var searchVisible by rememberSaveable(activeChannel) { mutableStateOf(false) }
    var messageQuery by rememberSaveable(activeChannel) { mutableStateOf("") }
    var channelOptionsExpanded by rememberSaveable(activeChannel) { mutableStateOf(false) }
    var newMessagesCount by remember(activeChannel) { mutableIntStateOf(0) }
    var previousNewestMessageId by remember(activeChannel) { mutableStateOf<String?>(null) }
    val atLatestMessage by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 80 } }
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
    val recentMessages = remember(filteredMessages, messageQuery) {
        val query = messageQuery.trim()
        if (query.isBlank()) filteredMessages else filteredMessages.filter { message ->
            message.text.contains(query, ignoreCase = true) ||
                message.peerLabel?.contains(query, ignoreCase = true) == true
        }
    }
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
        !state.sessionEnabled -> "Activa la sesión para enviar archivos o mensajes."
        isGlobalChat && !state.lanConnected -> "El canal requiere estar en una red Wi‑Fi."
        isGlobalChat && !globalLanJoined -> "Entra al canal Wi‑Fi para escribir."
        isGlobalChat && state.selectedFilesCount > 0 && state.globalChatPeerCount <= 0 ->
            "No hay otros equipos en el canal para recibir archivos."
        isGlobalChat && state.chatDraft.isBlank() && state.selectedFilesCount <= 0 -> "Escribe un mensaje o adjunta archivos."
        !hasComposerPayload -> "Escribe un mensaje o adjunta archivos."
        state.sessionExpired -> "Sesión expirada. Renueva la sesión."
        !isGlobalChat && directChannelReady && !state.chatSessionReady -> "Confirma la sesión con el receptor en Conectar antes de enviar."
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
        hasComposerPayload && sendDisabledReason != null -> sendDisabledReason
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
    val showSyncButton = !state.chatSessionReady && experience.showSyncAction && (!isGlobalChat || tokenSyncNeedsManualAction)
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

    LaunchedEffect(filteredMessages.firstOrNull()?.id) {
        val newest = filteredMessages.firstOrNull()
        if (newest != null && newest.id != previousNewestMessageId) {
            if (messageQuery.isBlank() && (atLatestMessage || newest.direction == ChatMessageDirection.OUTGOING)) {
                listState.scrollToItem(0)
                newMessagesCount = 0
            } else if (previousNewestMessageId != null && messageQuery.isBlank()) {
                val added = filteredMessages.indexOfFirst { it.id == previousNewestMessageId }.coerceAtLeast(1)
                newMessagesCount += added
            }
            previousNewestMessageId = newest.id
        }
    }
    LaunchedEffect(atLatestMessage) { if (atLatestMessage) newMessagesCount = 0 }
    LaunchedEffect(messageQuery, activeChannel) { listState.scrollToItem(0) }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize(),
        colors = sectionCardColors
    ) {
        val sectionLabel = when {
            showChannelJoinPrompt -> null
            showDirectSetupCard && hasMessages -> "Historial reciente"
            hasMessages -> if (messageQuery.isNotBlank()) "Resultados guardados" else if (isGlobalChat) "Mensajes" else "Historial de chats directos"
            !showDirectSetupCard -> if (isGlobalChat) null else "Empieza aquí"
            else -> null
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val compactImeViewport = keyboardVisible && maxHeight < 220.dp
        val composerMaxHeight = if (compactImeViewport) maxHeight
            else (maxHeight * 0.62f).coerceAtLeast(128.dp).coerceAtMost(maxHeight)
        val headerMaxHeight = maxHeight * 0.34f
        Column(
            modifier = Modifier.fillMaxSize().padding(UiSpaceS),
            verticalArrangement = Arrangement.spacedBy(UiSpaceS)
        ) {
            if (!keyboardVisible) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = headerMaxHeight).verticalScroll(rememberScrollState()),
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
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
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
                    if (filteredMessages.isNotEmpty()) {
                        IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) messageQuery = "" }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Buscar en mensajes guardados")
                        }
                    }
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
                        IconButton(onClick = { confirmClearChat = true }) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Eliminar historial de este canal")
                        }
                    }
                }
            }

            if (searchVisible) {
                OutlinedTextField(
                    value = messageQuery,
                    onValueChange = { messageQuery = it.take(160) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Buscar texto o equipo") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { messageQuery = ""; searchVisible = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cerrar búsqueda")
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }

            if (!isGlobalChat) {
                val availablePeers = if (directChatMode == ConnectionMode.WIFI_DIRECT) directWifiPeers else directLanPeers
                if (availablePeers.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availablePeers.forEach { peer ->
                            val selected = if (directChatMode == ConnectionMode.WIFI_DIRECT) state.chatDirectTargetIps.contains(peer.ip)
                                else state.chatDirectTargetIp == peer.ip
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectChatDirectPeer(peer.ip) },
                                label = { Text(peer.label.ifBlank { peer.ip }, maxLines = 1) }
                            )
                        }
                    }
                }
            }

            if (!isGlobalChat && directChannelReady && !state.chatSessionReady) {
                TextButton(onClick = onOpenConnectTab) { Text("Confirmar sesión en Conectar") }
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
                                    if (!state.chatSessionReady) "Sesión por confirmar" else if (directTargetCount > 1) "$directTargetCount equipos" else "Equipo listo"
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
                        if (state.sessionExpired || (!state.chatSessionReady && (experience.showSyncAction || state.tokenSyncStatus.isNotBlank()))) {
                            StatusChip(
                                label = when {
                                    state.sessionExpired -> "Sesión expirada"
                                    state.tokenSyncStatus.contains("aprob", ignoreCase = true) -> "Pendiente"
                                    state.tokenSyncStatus.contains("reintent", ignoreCase = true) ||
                                        state.tokenSyncStatus.contains("no pude", ignoreCase = true) ||
                                        state.tokenSyncStatus.contains("fall", ignoreCase = true) -> "Reintentar"
                                    state.sessionSyncing -> "Sincronizando"
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
                        TextButton(onClick = { channelOptionsExpanded = !channelOptionsExpanded }) {
                            Text(if (channelOptionsExpanded) "Ocultar opciones del canal" else "Opciones del canal")
                        }
                        if (channelOptionsExpanded) {
                            ChannelDownloadSettingsCard(
                                autoDownload = state.autoDownloadChannelFiles,
                                onAutoDownloadChange = onAutoDownloadChannelFilesChange,
                                panelColor = quietPanelColor
                            )
                        }
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

            }
            } else if (!compactImeViewport) {
                Text(
                    if (isGlobalChat) channelTitle else directTargetLabel ?: chatHeaderTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (hasMessages) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
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
                if (!atLatestMessage && messageQuery.isBlank()) {
                    FilledTonalButton(
                        onClick = {
                            newMessagesCount = 0
                            chatCoroutineScope.launch { listState.animateScrollToItem(0) }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (newMessagesCount > 0) "${newMessagesCount} nuevos" else "Ir al último mensaje")
                    }
                }
                }
            } else if (messageQuery.isNotBlank()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyStateBlock(
                        title = "No encontramos ese mensaje",
                        body = "Prueba otra palabra o busca por el nombre del equipo.",
                        actionLabel = "Borrar búsqueda",
                        onAction = { messageQuery = "" }
                    )
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
                    maxHeight = composerMaxHeight,
                    keyboardVisible = keyboardVisible,
                    selectedFileNames = state.selectedFileNames,
                    selectedFilesCount = state.selectedFilesCount,
                    recoveryIncomplete = com.example.wifidrop.presentation.isIncompleteAttachmentRecovery(state.shareImportStatus),
                    placeholderText = composerPlaceholder,
                    destinationDescription = if (isGlobalChat) channelTitle else directTargetLabel ?: chatHeaderTitle,
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
    }

    if (pendingDeleteMessageId != null) {
        val pendingMessage = state.chatMessages.firstOrNull { it.id == pendingDeleteMessageId }
        AlertDialog(
            onDismissRequest = { pendingDeleteMessageId = null },
            title = { Text("Eliminar mensaje") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(UiSpaceS)) {
                    Text("Se eliminará este mensaje del historial de este equipo.")
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
                    "Se eliminarán todos los mensajes de este canal guardados en este equipo, incluidos los que no aparecen en la búsqueda. Esta acción no se puede deshacer."
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
    maxHeight: Dp,
    keyboardVisible: Boolean,
    selectedFileNames: List<String>,
    selectedFilesCount: Int,
    recoveryIncomplete: Boolean,
    placeholderText: String,
    destinationDescription: String,
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
        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiSpaceS, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if ((selectedFilesCount > 0 || recoveryIncomplete) && !keyboardVisible) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (recoveryIncomplete) {
                            if (selectedFilesCount > 0) "$selectedFilesCount disponibles; faltan otros. Elige de nuevo."
                            else "No se recuperaron los adjuntos. Elige de nuevo."
                        } else attachmentsLabel + selectedFileNames.firstOrNull()?.let { " · $it" }.orEmpty(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (recoveryIncomplete) 2 else 1,
                        color = if (recoveryIncomplete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (recoveryIncomplete) {
                        TextButton(onClick = onPickFile) { Text("Elegir") }
                    } else {
                        TextButton(onClick = onClearSelectedFiles) { Text("Quitar") }
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
                    .weight(1f, fill = false)
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = "Escribir mensaje para $destinationDescription" },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                supportingText = if (draft.length >= 1_800) { { Text("${draft.length}/2000 caracteres") } } else null,
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
                    IconButton(
                        onClick = onPickFile,
                        enabled = attachEnabled && composerEnabled
                    ) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = "Adjuntar archivos")
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
                        if (keyboardVisible && selectedFilesCount > 0) "Enviar ($selectedFilesCount)" else sendButtonLabel,
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
                modifier = Modifier.fillMaxWidth().toggleable(
                    value = autoDownload, role = Role.Checkbox, onValueChange = onAutoDownloadChange
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Checkbox(
                    checked = autoDownload,
                    onCheckedChange = null
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
    val clipboard = LocalClipboardManager.current
    val outgoing = item.direction == ChatMessageDirection.OUTGOING
    val failed = item.status == ChatMessageStatus.FAILED
    val pending = item.status == ChatMessageStatus.QUEUED || item.status == ChatMessageStatus.SENDING
    val channelFileOffer = remember(item.text) {
        (ChatMessageScopeCodec.decodeFromTransport(item.text) as? ChatMessageScopeCodec.DecodedChatPayload.FileOffer)?.offer
    }
    val contentColor = if (failed) MaterialTheme.colorScheme.onErrorContainer
        else if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (outgoing) 18.dp else 5.dp,
                bottomEnd = if (outgoing) 5.dp else 18.dp
            ),
            color = if (failed) MaterialTheme.colorScheme.errorContainer
                else if (outgoing) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            contentColor = contentColor,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier.padding(start = 14.dp, top = 4.dp, end = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (outgoing) "Tú" + (item.peerLabel?.takeIf { it.isNotBlank() }?.let { " → $it" } ?: "")
                        else item.peerLabel ?: "Equipo",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box {
                        IconButton(onClick = { itemMoreExpanded = true }) {
                            Icon(Icons.Rounded.MoreHoriz, contentDescription = "Acciones del mensaje", modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = itemMoreExpanded, onDismissRequest = { itemMoreExpanded = false }) {
                            if (channelFileOffer == null) {
                                DropdownMenuItem(
                                    text = { Text("Copiar texto") },
                                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                    onClick = { itemMoreExpanded = false; clipboard.setText(AnnotatedString(item.text)) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Compartir mensaje") },
                                leadingIcon = { Icon(Icons.Rounded.IosShare, contentDescription = null) },
                                onClick = { itemMoreExpanded = false; onShareMessage() }
                            )
                            if (outgoing && failed) {
                                DropdownMenuItem(
                                    text = { Text("Reintentar envío") },
                                    leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                    onClick = { itemMoreExpanded = false; onRetryMessage(item.id) }
                                )
                            }
                            if (outgoing && pending) {
                                DropdownMenuItem(
                                    text = { Text("Cancelar envío") },
                                    leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                                    onClick = { itemMoreExpanded = false; onCancelQueuedMessage(item.id) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Eliminar de este equipo") },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                onClick = { itemMoreExpanded = false; onRequestDelete() }
                            )
                        }
                    }
                }
                if (channelFileOffer != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(channelFileOffer.fileName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (channelFileOffer.fileSizeBytes >= 0) formatBytes(channelFileOffer.fileSizeBytes) else "Tamaño no disponible",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    if (!outgoing) {
                        FilledTonalButton(onClick = onDownloadChannelFileOffer) { Text("Descargar archivo") }
                    }
                } else {
                    SelectionContainer { Text(item.text, style = MaterialTheme.typography.bodyLarge) }
                }
                Text(
                    formatHistoryTime(item.timestampMs) + " · " + chatStatusLabel(item.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.82f),
                    modifier = Modifier.padding(top = 6.dp)
                )
                val friendlyIssue = friendlyMessageIssue(item.errorCause)
                if (!friendlyIssue.isNullOrBlank()) {
                    Text(friendlyIssue, style = MaterialTheme.typography.bodySmall, color = contentColor)
                }
                if (outgoing && failed) {
                    TextButton(onClick = { onRetryMessage(item.id) }) { Text("Reintentar envío") }
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
    if (cause?.contains("cancelad", ignoreCase = true) == true) return null
    return com.example.wifidrop.presentation.actionableTransferIssue(cause, "No se pudo enviar. Comprueba la conexión y vuelve a intentarlo.")
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
