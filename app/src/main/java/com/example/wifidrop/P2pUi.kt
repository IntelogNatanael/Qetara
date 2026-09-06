package com.example.wifidrop

import android.content.Intent
import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.io.File
import kotlin.math.roundToInt

internal enum class P2pMainTab(val title: String) {
    CONNECTION("Conectar"),
    SEND("Enviar"),
    MESSAGES("Chat"),
    CHANNEL("Canal"),
    HISTORY("Descargas")
}

internal data class DirectFlowStepUi(
    val label: String,
    val done: Boolean,
    val active: Boolean = false
)

internal const val DEVELOPER_GITHUB_URL = "https://github.com/IntelogNatanael"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun P2pScreen(
    screenState: P2pScreenState,
    onRequestPermission: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onCompactModeChange: (Boolean) -> Unit,
    onVibrateOnConnectChange: (Boolean) -> Unit,
    onVibrateOnErrorChange: (Boolean) -> Unit,
    onSilentSuccessFeedbackChange: (Boolean) -> Unit,
    initialFocusStage: FocusStage,
    onFocusStageChange: (FocusStage) -> Unit,
    onConnectionViewModeChange: (ConnectionViewMode) -> Unit,
    onConnectionModeChange: (ConnectionMode) -> Unit,
    onWifiDirectModeEnabledChange: (Boolean) -> Unit,
    onLanModeEnabledChange: (Boolean) -> Unit,
    onStartHost: () -> Unit,
    onStartClient: () -> Unit,
    onScanLanPeers: () -> Unit,
    onCancelLanScan: () -> Unit,
    onDisconnect: () -> Unit,
    onRefreshState: () -> Unit,
    onCancelConnect: () -> Unit,
    onConnectToPeer: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onGenerateToken: () -> Unit,
    onGeneratePin: () -> Unit,
    onRenewSession: () -> Unit,
    onSetSessionEnabled: (Boolean) -> Unit,
    onCopyToken: () -> Unit,
    onPasteToken: () -> Unit,
    onSyncToken: () -> Unit,
    onConfirmManualSession: () -> Unit,
    onSyncFromPeerIp: (String) -> Unit,
    onTargetIpChange: (String) -> Unit,
    onUseSuggestedTarget: () -> Unit,
    onPickFile: (com.example.wifidrop.presentation.P2pAttachmentContext) -> Unit,
    onClearSelectedFiles: (com.example.wifidrop.presentation.P2pAttachmentContext) -> Unit,
    onAttachmentContextChange: (com.example.wifidrop.presentation.P2pAttachmentContext) -> Unit,
    onSendFile: () -> Unit,
    onRefreshReceived: () -> Unit,
    onShareReceivedFile: (File) -> Unit,
    onPauseTransfers: () -> Unit,
    onResumeTransfers: () -> Unit,
    onCancelActiveTransfer: () -> Unit,
    onSendToLastTarget: () -> Unit,
    onSetPeerFavorite: (String, Boolean) -> Unit,
    onSetPeerAlias: (String, String) -> Unit,
    onForgetPeer: (String) -> Unit,
    onPauseQueueItem: (String) -> Unit,
    onResumeQueueItem: (String) -> Unit,
    onCancelQueueItem: (String) -> Unit,
    onMoveQueueItemUp: (String) -> Unit,
    onMoveQueueItemDown: (String) -> Unit,
    onChatChannelChange: (ChatChannel) -> Unit,
    onSetGlobalLanJoined: (Boolean) -> Unit,
    onAutoDownloadChannelFilesChange: (Boolean) -> Unit,
    onChatDraftChange: (String) -> Unit,
    onSelectChatDirectPeer: (String) -> Unit,
    onDownloadChannelFileOffer: (ChatMessageEntry) -> Unit,
    onSendMessage: () -> Unit,
    onRetryMessage: (String) -> Unit,
    onCancelQueuedMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onClearMessages: (ChatChannel) -> Unit,
    onSaveSuggestedFavorite: (String) -> Unit,
    onSkipSuggestedFavorite: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onTrustPeer: (PendingTrustRequest) -> Unit,
    onApproveCredentialShare: (PendingCredentialShareRequest) -> Unit,
    onRejectCredentialShare: (PendingCredentialShareRequest) -> Unit,
    onOpenHistoryItem: (TransferHistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val openDeveloperProfile: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    DEVELOPER_GITHUB_URL.toUri()
                )
            )
        }
    }
    val connected = screenState.connection?.groupFormed == true
    val isHost = screenState.connection?.isGroupOwner == true
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val isCompactScreen = with(density) {
        windowSize.width.toDp() < 420.dp || windowSize.height.toDp() < 760.dp
    }
    val allTabs = listOf(
        P2pMainTab.CONNECTION,
        P2pMainTab.SEND,
        P2pMainTab.MESSAGES,
        P2pMainTab.CHANNEL,
        P2pMainTab.HISTORY
    )
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    if (selectedTabIndex !in allTabs.indices) {
        selectedTabIndex = 0
    }
    var focusStage by rememberSaveable(initialFocusStage.name) { mutableStateOf(initialFocusStage) }
    var headerMinimized by rememberSaveable(isCompactScreen) { mutableStateOf(isCompactScreen) }
    var headerExpanded by rememberSaveable { mutableStateOf(false) }
    var downloadsInitialSection by rememberSaveable { mutableStateOf(DownloadLibrarySection.FILES) }
    var dismissedSupportCardKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastHandledShareEventId by rememberSaveable { mutableStateOf<Long?>(null) }
    val focusEnabled = focusStage != FocusStage.OFF
    val selectedTab = when (focusStage) {
        FocusStage.CONNECT -> P2pMainTab.CONNECTION
        FocusStage.SEND -> P2pMainTab.SEND
        FocusStage.CHAT -> P2pMainTab.MESSAGES
        FocusStage.OFF -> allTabs[selectedTabIndex]
    }
    val selectionContext = when (selectedTab) {
        P2pMainTab.SEND -> com.example.wifidrop.presentation.P2pAttachmentContext.FILES
        P2pMainTab.MESSAGES -> com.example.wifidrop.presentation.P2pAttachmentContext.DIRECT_CHAT
        P2pMainTab.CHANNEL -> com.example.wifidrop.presentation.P2pAttachmentContext.CHANNEL
        else -> screenState.attachmentContext
    }
    // The new screen never renders the previous audience's attachments while its state updates.
    val state = if (screenState.attachmentContext == selectionContext) screenState else screenState.copy(
        selectedFileNames = emptyList(), selectedFilesCount = 0, shareImportStatus = ""
    )
    val pickFilesForContext: () -> Unit = { onPickFile(selectionContext) }
    val clearFilesForContext: () -> Unit = { onClearSelectedFiles(selectionContext) }
    LaunchedEffect(selectionContext) { onAttachmentContextChange(selectionContext) }
    val contentScroll = rememberScrollState()
    var showAdvancedTargetOptions by rememberSaveable { mutableStateOf(false) }
    var showQueueDetails by rememberSaveable { mutableStateOf(false) }
    var aliasEditorPeerId by rememberSaveable { mutableStateOf<String?>(null) }
    var aliasEditorValue by rememberSaveable { mutableStateOf("") }
    var connectionModesExpanded by rememberSaveable { mutableStateOf(false) }
    var previousConnectionReady by rememberSaveable { mutableStateOf(false) }
    var securityExpanded by rememberSaveable { mutableStateOf(false) }
    var showSendActivityDetails by rememberSaveable { mutableStateOf(false) }
    var showSelectedFilesReview by rememberSaveable { mutableStateOf(false) }
    var sessionDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    var trustExpanded by rememberSaveable { mutableStateOf(false) }
    var showUxPreferences by rememberSaveable { mutableStateOf(false) }
    var dismissedCredentialRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingForgetPeerId by rememberSaveable { mutableStateOf<String?>(null) }
    val connectionViewMode = state.connectionViewMode
    fun applyFocusStage(next: FocusStage, persist: Boolean = true) {
        val changed = focusStage != next
        focusStage = next
        if (isCompactScreen) {
            headerMinimized = true
        }
        headerExpanded = false
        when (next) {
            FocusStage.CONNECT -> selectedTabIndex = allTabs.indexOf(P2pMainTab.CONNECTION)
            FocusStage.SEND -> selectedTabIndex = allTabs.indexOf(P2pMainTab.SEND)
            FocusStage.CHAT -> selectedTabIndex = allTabs.indexOf(P2pMainTab.MESSAGES)
            FocusStage.OFF -> Unit
        }
        if (persist && changed) {
            onFocusStageChange(next)
        }
    }
    LaunchedEffect(initialFocusStage) {
        applyFocusStage(initialFocusStage, persist = false)
    }
    LaunchedEffect(screenState.incomingShareEventId) {
        if (com.example.wifidrop.presentation.shouldNavigateToIncomingShare(screenState.incomingShareEventId, lastHandledShareEventId)) {
            lastHandledShareEventId = screenState.incomingShareEventId
            selectedTabIndex = allTabs.indexOf(P2pMainTab.SEND)
            applyFocusStage(FocusStage.OFF)
        }
    }
    LaunchedEffect(selectedTab, state.chatChannel) {
        val targetChannel = when (selectedTab) {
            P2pMainTab.MESSAGES -> ChatChannel.DIRECT
            P2pMainTab.CHANNEL -> ChatChannel.GLOBAL
            else -> null
        }
        if (targetChannel != null && state.chatChannel != targetChannel) {
            onChatChannelChange(targetChannel)
        }
    }

    fun applyConnectionMode(next: ConnectionMode) {
        if (connectionViewMode == ConnectionViewMode.ADVANCED) {
            onConnectionModeChange(next)
        } else {
            onConnectionViewModeChange(ConnectionViewMode.fromConnectionMode(next))
        }
        headerExpanded = false
        if (isCompactScreen) headerMinimized = true
    }

    @Composable
    fun ConnectionModeSwitchRow(modifier: Modifier = Modifier) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
            verticalArrangement = Arrangement.spacedBy(UiSpaceS)
        ) {
            if (state.activeConnectionMode == ConnectionMode.WIFI_DIRECT) {
                Button(onClick = { applyConnectionMode(ConnectionMode.WIFI_DIRECT) }) {
                    Text(ConnectionMode.WIFI_DIRECT.title)
                }
            } else {
                OutlinedButton(onClick = { applyConnectionMode(ConnectionMode.WIFI_DIRECT) }) {
                    Text(ConnectionMode.WIFI_DIRECT.title)
                }
            }
            if (state.activeConnectionMode == ConnectionMode.LAN) {
                Button(onClick = { applyConnectionMode(ConnectionMode.LAN) }) {
                    Text(ConnectionMode.LAN.title)
                }
            } else {
                OutlinedButton(onClick = { applyConnectionMode(ConnectionMode.LAN) }) {
                    Text(ConnectionMode.LAN.title)
                }
            }
        }
    }

    @Composable
    fun ConnectionViewModeRow(modifier: Modifier = Modifier) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
            verticalArrangement = Arrangement.spacedBy(UiSpaceS)
        ) {
            ConnectionViewMode.entries.forEach { mode ->
                val selected = connectionViewMode == mode
                val onClick = {
                    onConnectionViewModeChange(mode)
                }
                if (selected) {
                    Button(onClick = onClick) { Text(mode.title) }
                } else {
                    OutlinedButton(onClick = onClick) { Text(mode.title) }
                }
            }
        }
    }

    val experience = deriveP2pExperienceState(state)
    val chatExperience = deriveP2pChatExperienceState(state)
    val sessionLabel = when {
        state.sessionExpired -> "Sesión expirada"
        else -> formatSessionExpiry(state.sessionExpiresAtMs, state.nowMs)
    }
    val activeConnectionMode = experience.activeConnectionMode
    val alternateConnectionMode = experience.alternateConnectionMode
    val alternateModeEnabled = experience.alternateModeEnabled
    val connectionLabel = experience.connectionLabel
    val alternateModeHint = experience.alternateModeHint
    val lanReadyForExchange = experience.lanReadyForExchange
    val connectionReadyForFlow = experience.connectionReadyForFlow
    val nextStageAfterConnect = experience.nextStageAfterConnect
    val openNextStage: () -> Unit = {
        applyFocusStage(nextStageAfterConnect)
        selectedTabIndex = when (nextStageAfterConnect) {
            FocusStage.CONNECT -> allTabs.indexOf(P2pMainTab.CONNECTION)
            FocusStage.SEND -> allTabs.indexOf(P2pMainTab.SEND)
            FocusStage.CHAT -> allTabs.indexOf(P2pMainTab.MESSAGES)
            FocusStage.OFF -> selectedTabIndex
        }
    }
    val queueRunningCount = state.sendQueue.count { it.status == SendQueueStatus.RUNNING }
    val queuePendingCount = state.sendQueue.count {
        it.status == SendQueueStatus.QUEUED ||
            it.status == SendQueueStatus.PAUSED ||
            it.status == SendQueueStatus.RETRY_WAIT
    }
    val queueFailedCount = state.sendQueue.count { it.status == SendQueueStatus.FAILED }
    val messageFailedCount = state.chatMessages.count {
        it.direction == ChatMessageDirection.OUTGOING && it.status == ChatMessageStatus.FAILED
    }
    val hasConnectionAlerts = state.pendingCredentialShare != null || state.pendingTrust != null
    val isSyncingNow = state.sessionSyncing
    val isConnectingNow = state.directDiscovering || state.directConnecting || state.directCreatingGroup
    val activeTransferCancelLabel = when {
        state.receiving && (state.sending || state.sendActiveCount > 0) -> "Cancelar transferencias"
        state.receiving -> "Cancelar recepción"
        else -> "Cancelar envíos"
    }
    fun runExperienceAction(action: P2pExperienceAction) {
        when (action.command) {
            P2pExperienceCommand.REQUEST_PERMISSION -> onRequestPermission()
            P2pExperienceCommand.OPEN_WIFI_SETTINGS -> onOpenWifiSettings()
            P2pExperienceCommand.START_CLIENT -> onStartClient()
            P2pExperienceCommand.CANCEL_DIRECT -> onCancelConnect()
            P2pExperienceCommand.SCAN_LAN -> onScanLanPeers()
            P2pExperienceCommand.CANCEL_LAN_SCAN -> onCancelLanScan()
            P2pExperienceCommand.USE_SUGGESTED_TARGET -> onUseSuggestedTarget()
            P2pExperienceCommand.REFRESH_STATE -> onRefreshState()
            P2pExperienceCommand.REVIEW_TRUST -> { dismissedCredentialRequestId = null }
            P2pExperienceCommand.RENEW_SESSION -> onRenewSession()
            P2pExperienceCommand.OPEN_DOWNLOADS -> onOpenDownloads()
            P2pExperienceCommand.SYNC_TOKEN -> onSyncToken()
            P2pExperienceCommand.OPEN_QUEUE -> {
                selectedTabIndex = allTabs.indexOf(P2pMainTab.SEND)
                showQueueDetails = true
                applyFocusStage(FocusStage.OFF)
            }

            P2pExperienceCommand.OPEN_MESSAGES -> {
                selectedTabIndex = allTabs.indexOf(P2pMainTab.MESSAGES)
                applyFocusStage(FocusStage.CHAT)
            }

            P2pExperienceCommand.SEND_NOW -> {
                selectedTabIndex = allTabs.indexOf(P2pMainTab.SEND)
                onSendFile()
            }

            P2pExperienceCommand.CONTINUE_FLOW -> openNextStage()
        }
    }
    val quickActionLabel = experience.primaryAction.label
    val quickActionEnabled = experience.primaryAction.enabled
    val quickAction = { runExperienceAction(experience.primaryAction) }
    val supportCard = experience.supportBanner
    val supportCardKey = supportCard?.key
    val suppressDirectInactiveBanner =
        activeConnectionMode == ConnectionMode.WIFI_DIRECT &&
            !state.p2pEnabled &&
            !connected &&
            !(state.directDiscovering || state.directConnecting || state.directCreatingGroup) &&
            connectionViewMode != ConnectionViewMode.ADVANCED
    val suppressDirectConnectionBanner =
        selectedTab == P2pMainTab.CONNECTION &&
            connectionViewMode != ConnectionViewMode.ADVANCED
    val showSupportBanner = !focusEnabled &&
        supportCard != null &&
        supportCardKey != dismissedSupportCardKey &&
        supportCard.action.label != quickActionLabel &&
        !suppressDirectInactiveBanner &&
        !suppressDirectConnectionBanner
    val activeSupportCard = supportCard.takeIf { showSupportBanner }
    fun tabLabel(tab: P2pMainTab): String {
        return when (tab) {
            P2pMainTab.CONNECTION -> "Conectar"
            P2pMainTab.SEND -> "Enviar"
            P2pMainTab.MESSAGES -> "Chat"
            P2pMainTab.CHANNEL -> "Canal"
            P2pMainTab.HISTORY -> "Descargas"
        }
    }

    fun tabIcon(tab: P2pMainTab) = when (tab) {
        P2pMainTab.CONNECTION -> Icons.Rounded.Link
        P2pMainTab.SEND -> Icons.AutoMirrored.Rounded.Send
        P2pMainTab.MESSAGES -> Icons.Rounded.ChatBubbleOutline
        P2pMainTab.CHANNEL -> Icons.Rounded.Wifi
        P2pMainTab.HISTORY -> Icons.Rounded.Download
    }
    var securityMoreExpanded by rememberSaveable { mutableStateOf(false) }
    var sendMoreExpanded by rememberSaveable { mutableStateOf(false) }
    var headerQuietMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val baseDensity = LocalDensity.current
    val appliedFontScale = state.fontScale.coerceIn(0.85f, 1.25f)
    val compactSpacing = if (state.compactMode) 6.dp else UiSpaceS
    val compactPadding = if (state.compactMode) 12.dp else UiSpaceM
    val headerSummary = experience.headerSummary
    val headerHint = experience.headerHint
    val connectionNarrative = experience.connectionNarrative
    val directReadyForExchange = !state.directTargetIp.isNullOrBlank()
    val resolvedTargetLabel = state.resolvedTargetLabel ?: state.resolvedTargetIp
    val simpleWifiDirectMode =
        activeConnectionMode == ConnectionMode.WIFI_DIRECT &&
            connectionViewMode != ConnectionViewMode.ADVANCED
    val showLanSecondaryHelp = activeConnectionMode == ConnectionMode.WIFI_DIRECT ||
        connectionViewMode == ConnectionViewMode.ADVANCED ||
        !state.suggestedTargetIp.isNullOrBlank() ||
        !state.resolvedTargetIp.isNullOrBlank() ||
        state.knownServicePeers.any { it.ip.isNotBlank() }
    val directBusy = state.directDiscovering || state.directConnecting || state.directCreatingGroup
    val directFlowHeadline = when {
        directReadyForExchange -> "Directo listo"
        !state.permissionGranted -> "Falta permiso"
        !state.p2pEnabled -> "Abre Wi-Fi del sistema"
        state.directCreatingGroup -> "Enlace creado en este equipo"
        state.directConnecting -> "Uniéndote al enlace"
        state.directDiscovering && state.peers.isNotEmpty() -> "Elige el equipo correcto"
        state.directDiscovering -> "Buscando equipos"
        else -> "Elige cómo iniciar"
    }
    val directFlowBody = when {
        directReadyForExchange ->
            state.directTargetLabel?.let { "Todo quedó listo con $it. Ya puedes enviar y chatear." }
                ?: "El equipo ya quedó listo para enviar y chatear."
        !state.permissionGranted ->
            "Concede el permiso de red cercana. Sin eso, Android no deja usar Wi-Fi Direct."
        !state.p2pEnabled ->
            "La app no puede encender Wi-Fi Direct. Abre Wi-Fi del sistema y luego vuelve aquí."
        state.directCreatingGroup ->
            "Este equipo ya abrió el enlace. En el otro, toca Buscar enlace y luego Conectar."
        state.directConnecting ->
            "Mantén ambos equipos abiertos mientras Directo termina de prepararse."
        state.directDiscovering && state.peers.isNotEmpty() ->
            "Toca el equipo correcto para completar el enlace 1 a 1."
        state.directDiscovering ->
            "Mantén el otro equipo con el enlace abierto. La lista se actualizará sola."
        else ->
            "Si este equipo empieza, toca Crear enlace. Si el otro ya empezó, toca Buscar enlace."
    }
    val directFlowSteps = listOf(
        DirectFlowStepUi(
            label = "1 Wi-Fi",
            done = state.permissionGranted && state.p2pEnabled,
            active = !state.permissionGranted || !state.p2pEnabled
        ),
        DirectFlowStepUi(
            label = "2 Crear o buscar",
            done = directBusy || connected || directReadyForExchange,
            active = state.permissionGranted && state.p2pEnabled && !directBusy && !connected && !directReadyForExchange
        ),
        DirectFlowStepUi(
            label = "3 Elegir equipo",
            done = directReadyForExchange,
            active = state.directConnecting || (state.directDiscovering && state.peers.isNotEmpty()) || (connected && !directReadyForExchange)
        ),
        DirectFlowStepUi(
            label = "4 Listo",
            done = directReadyForExchange,
            active = connected && !directReadyForExchange
        )
    )
    val wifiDirectRoleHint = when {
        activeConnectionMode != ConnectionMode.WIFI_DIRECT -> null
        directReadyForExchange -> "Este equipo ya quedó listo para usar Directo."
        connected && isHost -> "Este equipo ya creó el enlace. En el otro, toca Buscar enlace."
        connected && !isHost -> "Este equipo ya se unió al enlace. Espera un momento mientras Directo termina de prepararse."
        state.directCreatingGroup -> "Este equipo está creando el enlace."
        state.directConnecting -> "Este equipo se está uniendo al enlace."
        state.directDiscovering && state.peers.isNotEmpty() -> "Elige el equipo correcto y toca Conectar."
        state.directDiscovering -> "Mantén abierto el enlace en el otro equipo."
        !state.permissionGranted -> "Primero concede el permiso para usar Wi‑Fi Direct."
        !state.p2pEnabled -> "Primero abre Wi‑Fi del sistema. Luego vuelve aquí para crear o buscar un enlace."
        else -> "Si este equipo inicia, toca Crear enlace. Si el otro ya inició, toca Buscar enlace."
    }
    val connectionStepBody = when {
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && directReadyForExchange ->
            "Directo listo con ${state.directTargetLabel ?: "tu equipo"}."
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && connected && isHost ->
            "Enlace creado en este equipo. Falta que el otro equipo se una."
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && connected ->
            "Enlace detectado. Terminando de preparar Directo."
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directCreatingGroup ->
            "Creando el enlace en este equipo."
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directConnecting ->
            "Uniéndote al enlace del otro equipo."
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directDiscovering && state.peers.isNotEmpty() ->
            "Elige el equipo que inició el enlace y toca Conectar."
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directDiscovering ->
            "Buscando equipos con Wi‑Fi Direct."
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && !state.p2pEnabled ->
            "Abre Wi‑Fi del sistema para habilitar Wi‑Fi Direct."
        activeConnectionMode == ConnectionMode.WIFI_DIRECT ->
            "Crea un enlace o únete al del otro equipo."
        activeConnectionMode == ConnectionMode.LAN && !state.lanConnected ->
            "Conecta ambos equipos a la misma red Wi-Fi."
        activeConnectionMode == ConnectionMode.LAN && directReadyForExchange ->
            "Ya hay un enlace directo listo con ${state.directTargetLabel ?: "tu equipo"}."
        activeConnectionMode == ConnectionMode.LAN && state.resolvedTargetIp.isNullOrBlank() ->
            "Busca dispositivos o usa una IP sugerida."
        else ->
            "Equipo listo con ${resolvedTargetLabel ?: state.resolvedTargetIp}."
    }
    val hideDirectConnectCard =
        activeConnectionMode == ConnectionMode.WIFI_DIRECT &&
            connectionViewMode != ConnectionViewMode.ADVANCED &&
            (!state.permissionGranted || !state.p2pEnabled) &&
            !connected &&
            !directReadyForExchange &&
            !directBusy
    val requiresManualPairing = state.tokenSyncStatus.contains("manual", ignoreCase = true) ||
        state.tokenSyncStatus.contains("secure_credentials_required", ignoreCase = true)
    LaunchedEffect(requiresManualPairing) {
        if (requiresManualPairing) {
            securityExpanded = true
            sessionDetailsExpanded = true
        }
    }
    val compactSyncLabel = when {
        state.sessionExpired -> "Sesión expirada"
        state.sessionSyncing -> "Sincronizando"
        state.tokenSyncStatus.contains("reintent", ignoreCase = true) -> "Reintentando"
        state.tokenSyncStatus.contains("aprob", ignoreCase = true) -> "Pendiente"
        state.tokenSyncStatus.contains("no pude", ignoreCase = true) ||
            state.tokenSyncStatus.contains("no se pudo", ignoreCase = true) -> "Reintentar"
        else -> null
    }
    val sessionNeedsAttention = requiresManualPairing || state.sessionExpired ||
        state.pendingCredentialShare != null ||
        state.tokenSyncStatus.contains("no pude", ignoreCase = true) ||
        state.tokenSyncStatus.contains("no se pudo", ignoreCase = true) ||
        state.tokenSyncStatus.contains("aprob", ignoreCase = true)
    val showSessionActionButton = requiresManualPairing || state.sessionExpired ||
        state.tokenSyncStatus.contains("no pude", ignoreCase = true) ||
        state.tokenSyncStatus.contains("no se pudo", ignoreCase = true) ||
        state.tokenSyncStatus.contains("aprob", ignoreCase = true)
    val showMinimalSessionCard = !securityExpanded &&
        !sessionNeedsAttention &&
        connectionViewMode != ConnectionViewMode.ADVANCED
    val sessionActionLabel = when {
        requiresManualPairing -> "Introducir credenciales"
        state.sessionExpired -> "Renovar sesión"
        state.tokenSyncStatus.contains("aprob", ignoreCase = true) -> "Reintentar sincronización"
        else -> "Sincronizar ahora"
    }
    val sessionStatusText = when {
        requiresManualPairing -> "Abre Qetara en el otro equipo. Copia aquí su token y su PIN para compartir la misma sesión."
        state.sessionExpired -> "La sesión expiró. Renueva para continuar."
        state.sessionSyncing -> "Sincronizando sesión..."
        state.tokenSyncStatus.contains("reintent", ignoreCase = true) -> state.tokenSyncStatus
        state.tokenSyncStatus.contains("aprob", ignoreCase = true) -> "El otro equipo debe aprobar la sesión."
        state.tokenSyncStatus.contains("no pude", ignoreCase = true) ||
            state.tokenSyncStatus.contains("no se pudo", ignoreCase = true) -> "No se pudo sincronizar la sesión."
        else -> null
    }
    val showTrustedPeersPanel =
        state.pendingCredentialShare != null ||
            state.pendingTrust != null ||
            (connectionViewMode == ConnectionViewMode.ADVANCED && state.trustedPeers.isNotEmpty())
    val headerActivityLabel = when {
        isSyncingNow -> "Sincronizando"
        isConnectingNow -> "Conectando"
        state.receiving -> "Recibiendo"
        state.sending || queueRunningCount > 0 -> {
            if (state.sendBatchTotal > 0) {
                "Envío ${state.sendBatchCompleted}/${state.sendBatchTotal}"
            } else {
                "Envío activo"
            }
        }
        queuePendingCount > 0 -> "Cola $queuePendingCount"
        else -> null
    }
    val headerIsQuiet = !sessionNeedsAttention &&
        headerActivityLabel.isNullOrBlank() &&
        activeSupportCard == null &&
        connectionViewMode != ConnectionViewMode.ADVANCED
    val headerChipsVisible = sessionNeedsAttention || !headerActivityLabel.isNullOrBlank()
    val showHeaderQuickAction =
        selectedTab != P2pMainTab.CONNECTION || connectionViewMode == ConnectionViewMode.ADVANCED
    val headerDetailHint = when {
        connectionViewMode != ConnectionViewMode.ADVANCED &&
            !sessionNeedsAttention &&
            !isConnectingNow &&
            !isSyncingNow &&
            (state.receiving || state.sending || queueRunningCount > 0 || queuePendingCount > 0) -> null
        else -> headerHint
    }
    val liveStateColor by animateColorAsState(
        targetValue = when {
            headerIsQuiet -> lerp(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceVariant,
                0.06f
            )
            state.sessionExpired -> lerp(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.errorContainer,
                0.16f
            )
            activeConnectionMode == ConnectionMode.WIFI_DIRECT && connected -> lerp(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.primaryContainer,
                0.14f
            )
            activeConnectionMode == ConnectionMode.LAN && state.lanConnected -> lerp(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.secondaryContainer,
                0.14f
            )
            else -> lerp(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceVariant,
                0.12f
            )
        },
        animationSpec = tween(durationMillis = 250),
        label = "state-chip"
    )
    val headerCardPadding = if (headerIsQuiet && headerMinimized) 12.dp else compactPadding
    val headerCardSpacing = if (headerIsQuiet && headerMinimized) 6.dp else compactSpacing
    val topActionMenuItems = buildList {
        add(ActionMenuItem("Refrescar") { onRefreshState() })
        if (connected) {
            add(ActionMenuItem("Desconectar") { onDisconnect() })
        }
        add(
            ActionMenuItem(
                label = if (state.paused) "Reanudar transferencias" else "Pausar transferencias"
            ) {
                if (state.paused) onResumeTransfers() else onPauseTransfers()
            }
        )
        add(
            ActionMenuItem(
                label = activeTransferCancelLabel,
                enabled = state.sending || state.sendActiveCount > 0 || state.receiving
            ) {
                onCancelActiveTransfer()
            }
        )
        add(ActionMenuItem("Abrir Descargas") { onOpenDownloads() })
        add(ActionMenuItem(if (showUxPreferences) "Ocultar ajustes UX" else "Ajustes UX") {
            showUxPreferences = !showUxPreferences
        })
    }
    val sectionCardColors = CardDefaults.elevatedCardColors(
        containerColor = lerp(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant,
            0.14f
        )
    )
    val shellSurfaceColor = lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant,
        0.1f
    )
    val quietPanelColor = lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant,
        0.26f
    )

    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface, 0.18f),
            lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceVariant, 0.42f),
            lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primaryContainer, 0.22f),
            lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.secondaryContainer, 0.08f)
        )
    )
    val headerAuraBrush = Brush.linearGradient(
        colors = listOf(
            liveStateColor,
            if (activeConnectionMode == ConnectionMode.WIFI_DIRECT) {
                lerp(liveStateColor, MaterialTheme.colorScheme.primaryContainer, 0.3f)
            } else {
                lerp(liveStateColor, MaterialTheme.colorScheme.secondaryContainer, 0.24f)
            },
            shellSurfaceColor
        )
    )

    LaunchedEffect(state.pendingCredentialShare?.id) {
        if (
            state.pendingCredentialShare != null &&
            selectedTab != P2pMainTab.CONNECTION &&
            selectedTab != P2pMainTab.MESSAGES &&
            selectedTab != P2pMainTab.CHANNEL
        ) {
            selectedTabIndex = allTabs.indexOf(P2pMainTab.CONNECTION)
        }
    }
    LaunchedEffect(supportCardKey) {
        if (supportCardKey == null) {
            dismissedSupportCardKey = null
        }
    }
    LaunchedEffect(sessionNeedsAttention) {
        if (sessionNeedsAttention) {
            securityExpanded = true
        }
    }
    LaunchedEffect(headerIsQuiet) {
        if (headerIsQuiet) {
            headerExpanded = false
            headerMinimized = true
        }
    }
    LaunchedEffect(connectionReadyForFlow, focusEnabled, focusStage, state.selectedFilesCount, state.chatDraft, state.chatMessages.size) {
        if (focusEnabled &&
            focusStage == FocusStage.CONNECT &&
            connectionReadyForFlow &&
            !previousConnectionReady
        ) {
            openNextStage()
        }
        previousConnectionReady = connectionReadyForFlow
    }
    LaunchedEffect(state.sending, queueRunningCount, queuePendingCount, queueFailedCount, state.sendFailureCause) {
        if (state.sending || queueRunningCount > 0 || queueFailedCount > 0 || !state.sendFailureCause.isNullOrBlank()) {
            showSendActivityDetails = true
        }
    }
    val showStateHeader =
        connectionViewMode == ConnectionViewMode.ADVANCED ||
            headerExpanded ||
            (!headerIsQuiet && selectedTab == P2pMainTab.CONNECTION && connectionViewMode == ConnectionViewMode.ADVANCED)
    val useDedicatedTabViewport =
        (selectedTab == P2pMainTab.CONNECTION && connectionViewMode != ConnectionViewMode.ADVANCED) ||
        selectedTab == P2pMainTab.MESSAGES ||
            selectedTab == P2pMainTab.CHANNEL ||
            selectedTab == P2pMainTab.HISTORY ||
            (selectedTab == P2pMainTab.SEND && showQueueDetails)

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * appliedFontScale
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(bgBrush)
        ) {
            QetaraBackdrop(
                modifier = Modifier.fillMaxSize()
            )
            state.pendingCredentialShare?.takeIf { it.id != dismissedCredentialRequestId }?.let { request ->
                QetaraCredentialRequestDialog(
                    request = request,
                    onApprove = { onApproveCredentialShare(request) },
                    onReject = { onRejectCredentialShare(request) },
                    onDismiss = { dismissedCredentialRequestId = request.id }
                )
            }
            pendingForgetPeerId?.let { peerId ->
                val peer = state.trustedPeers.firstOrNull { it.id == peerId }
                AlertDialog(
                    onDismissRequest = { pendingForgetPeerId = null },
                    title = { Text("¿Olvidar este equipo?") },
                    text = {
                        Text((peer?.let(TrustedPeerStore::displayName) ?: "Este equipo") +
                            " dejará de estar recordado. Se cancelarán sus envíos activos y pendientes y tendrás que aprobar una nueva conexión. Los archivos ya recibidos se conservan.")
                    },
                    confirmButton = {
                        TextButton(onClick = { pendingForgetPeerId = null; onForgetPeer(peerId) }) {
                            Text("Olvidar equipo", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { TextButton(onClick = { pendingForgetPeerId = null }) { Text("Cancelar") } }
                )
            }
            if (showSelectedFilesReview) {
                AlertDialog(
                    onDismissRequest = { showSelectedFilesReview = false },
                    title = { Text("Archivos preparados") },
                    text = {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.selectedFileNames) { name ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Text(name, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showSelectedFilesReview = false }) { Text("Listo") } }
                )
            }
            if (showUxPreferences) {
                QetaraPreferencesDialog(
                    state = state,
                    onFontScaleChange = onFontScaleChange,
                    onCompactModeChange = onCompactModeChange,
                    onVibrateOnConnectChange = onVibrateOnConnectChange,
                    onVibrateOnErrorChange = onVibrateOnErrorChange,
                    onSilentSuccessFeedbackChange = onSilentSuccessFeedbackChange,
                    onDismiss = { showUxPreferences = false }
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = compactPadding, vertical = compactPadding),
                verticalArrangement = Arrangement.spacedBy(compactSpacing)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(compactSpacing)
            ) {
            if (showStateHeader && !keyboardVisible && selectedTab != P2pMainTab.MESSAGES && selectedTab != P2pMainTab.CHANNEL) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = liveStateColor
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerAuraBrush)
                ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(headerCardPadding),
                    verticalArrangement = Arrangement.spacedBy(headerCardSpacing)
                ) {
                    if (!headerMinimized) {
                        Text(
                            text = if (activeConnectionMode == ConnectionMode.WIFI_DIRECT) {
                                "Entre tus equipos · sin internet"
                            } else {
                                "Tu red local · sin cuentas"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (headerMinimized) {
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
                                    connectionLabel,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    headerSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (headerIsQuiet) 1 else 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = { headerMinimized = false }) {
                                Text("Ver")
                            }
                        }

                        if (headerChipsVisible) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                                verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                            ) {
                                if (sessionNeedsAttention) {
                                    StatusChip(
                                        label = sessionLabel,
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
                                if (!headerActivityLabel.isNullOrBlank()) {
                                    StatusChip(
                                        label = headerActivityLabel,
                                        containerColor = if (state.receiving || isSyncingNow || isConnectingNow) {
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        } else if (state.sending || queueRunningCount > 0) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        contentColor = if (state.receiving || isSyncingNow || isConnectingNow) {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else if (state.sending || queueRunningCount > 0) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }

                        if (headerIsQuiet) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (showHeaderQuickAction) {
                                    FilledTonalButton(
                                        onClick = quickAction,
                                        enabled = quickActionEnabled,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(quickActionLabel, maxLines = 1)
                                    }
                                }
                                if (showHeaderQuickAction || connectionViewMode == ConnectionViewMode.ADVANCED) {
                                    Box {
                                        TextButton(onClick = { headerQuietMenuExpanded = true }) {
                                            Text("Más")
                                        }
                                        DropdownMenu(
                                            expanded = headerQuietMenuExpanded,
                                            onDismissRequest = { headerQuietMenuExpanded = false }
                                        ) {
                                            ConnectionViewMode.entries.forEach { mode ->
                                                DropdownMenuItem(
                                                    text = { Text(mode.title) },
                                                    onClick = {
                                                        headerQuietMenuExpanded = false
                                                        onConnectionViewModeChange(mode)
                                                    }
                                                )
                                            }
                                            HorizontalDivider()
                                            topActionMenuItems.forEach { item ->
                                                DropdownMenuItem(
                                                    text = { Text(item.label) },
                                                    enabled = item.enabled,
                                                    onClick = {
                                                        headerQuietMenuExpanded = false
                                                        item.onClick()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (showHeaderQuickAction) {
                            PrimaryActionBar(
                                primaryLabel = quickActionLabel,
                                primaryEnabled = quickActionEnabled,
                                onPrimaryClick = quickAction,
                                menuItems = topActionMenuItems
                            )
                        }
                    } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                connectionLabel,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                headerSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (headerExpanded) 3 else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row {
                            if (connectionViewMode == ConnectionViewMode.ADVANCED) {
                                TextButton(onClick = { headerExpanded = !headerExpanded }) {
                                    Text(if (headerExpanded) "Menos" else "Ver estado")
                                }
                            }
                            TextButton(
                                onClick = {
                                    headerExpanded = false
                                    headerMinimized = true
                                }
                            ) {
                                Text("Ocultar")
                            }
                        }
                    }

                    if (headerChipsVisible) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(compactSpacing),
                            verticalArrangement = Arrangement.spacedBy(compactSpacing)
                        ) {
                            if (sessionNeedsAttention) {
                                StatusChip(
                                    label = sessionLabel,
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
                            if (!headerActivityLabel.isNullOrBlank()) {
                                StatusChip(
                                    label = headerActivityLabel,
                                    containerColor = if (state.receiving || isSyncingNow || isConnectingNow) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else if (state.sending || queueRunningCount > 0) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    contentColor = if (state.receiving || isSyncingNow || isConnectingNow) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else if (state.sending || queueRunningCount > 0) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }

                    if (state.sending) {
                        LinearProgressIndicator(
                            progress = { state.sendProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (state.receiving && state.receiverProgress != null) {
                        LinearProgressIndicator(
                            progress = { state.receiverProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (showHeaderQuickAction) {
                        PrimaryActionBar(
                            primaryLabel = quickActionLabel,
                            primaryEnabled = quickActionEnabled,
                            onPrimaryClick = {
                                headerExpanded = false
                                quickAction()
                            },
                            menuItems = topActionMenuItems,
                            primaryHeight = 48.dp
                        )
                    }

                    if (connectionViewMode == ConnectionViewMode.ADVANCED && focusEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Modo foco: ${focusStage.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { headerExpanded = true }) {
                                Text("Cambiar etapa")
                            }
                        }
                    }

                    AnimatedVisibility(visible = connectionViewMode == ConnectionViewMode.ADVANCED && headerExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(compactSpacing)) {
                            if (!headerDetailHint.isNullOrBlank()) {
                                Text(
                                    headerDetailHint,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (connectionViewMode == ConnectionViewMode.ADVANCED) {
                                Text(
                                    "Disponibilidad de red",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Completo permite tener Wi-Fi Direct y Wi-Fi LAN disponibles al mismo tiempo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box {
                                    OutlinedButton(onClick = { connectionModesExpanded = true }) {
                                        Text("Gestionar disponibilidad")
                                    }
                                    DropdownMenu(
                                        expanded = connectionModesExpanded,
                                        onDismissRequest = { connectionModesExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (state.wifiDirectModeEnabled) {
                                                        "Ocultar Wi-Fi Direct"
                                                    } else {
                                                        "Mostrar Wi-Fi Direct"
                                                    }
                                                )
                                            },
                                            enabled = state.wifiDirectModeEnabled.not() || state.lanModeEnabled,
                                            onClick = {
                                                connectionModesExpanded = false
                                                onWifiDirectModeEnabledChange(!state.wifiDirectModeEnabled)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (state.lanModeEnabled) {
                                                        "Ocultar Wi-Fi LAN"
                                                    } else {
                                                        "Mostrar Wi-Fi LAN"
                                                    }
                                                )
                                            },
                                            enabled = state.lanModeEnabled.not() || state.wifiDirectModeEnabled,
                                            onClick = {
                                                connectionModesExpanded = false
                                                onLanModeEnabledChange(!state.lanModeEnabled)
                                            }
                                        )
                                    }
                                }
                                if (!alternateModeHint.isNullOrBlank()) {
                                    Text(
                                        alternateModeHint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (connectionViewMode == ConnectionViewMode.ADVANCED) {
                                Text(
                                    connectionNarrative,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                "Modo foco",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(UiSpaceS)
                            ) {
                                FocusStage.entries.forEach { stage ->
                                    val selected = focusStage == stage
                                    if (selected) {
                                        Button(onClick = { applyFocusStage(stage) }) {
                                            Text(stage.title)
                                        }
                                    } else {
                                        OutlinedButton(onClick = { applyFocusStage(stage) }) {
                                            Text(stage.title)
                                        }
                                    }
                                }
                            }
                            if (focusEnabled) {
                                Text(
                                    "Vista simplificada: solo etapa ${focusStage.title.lowercase()}.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            AnimatedVisibility(
                                visible = isConnectingNow || isSyncingNow,
                                enter = fadeIn(tween(150)) + slideInVertically(tween(150)),
                                exit = fadeOut(tween(180)) + slideOutVertically(tween(180))
                            ) {
                                Text(
                                    text = if (isSyncingNow) {
                                        "Sincronizando credenciales..."
                                    } else {
                                        "Conectando dispositivos..."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                }
            }
            }
            }


            if ((selectedTab == P2pMainTab.CONNECTION || !state.sessionEnabled) &&
                !(keyboardVisible && (selectedTab == P2pMainTab.MESSAGES || selectedTab == P2pMainTab.CHANNEL))) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (state.sessionEnabled) "Compartir en este equipo" else "Sesión cerrada",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { onSetSessionEnabled(!state.sessionEnabled) }) {
                                Text(if (state.sessionEnabled) "Cerrar sesión" else "Activar sesión")
                            }
                        }
                        Text(
                            if (state.sessionEnabled) "Cerrar detiene la recepción y las operaciones en curso."
                            else "Actívala para conectar o enviar. Tus archivos y borradores se conservan.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (selectedTab == P2pMainTab.SEND) {
                buildP2pSendSummary(
                    total = state.sendBatchTotal,
                    completed = state.sendBatchCompleted,
                    failed = state.sendBatchFailed,
                    canceled = state.sendBatchCanceled,
                    sending = state.sending,
                    paused = state.paused
                )?.let { summary ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                        shape = RoundedCornerShape(16.dp),
                        color = when (summary.kind) {
                            P2pSendSummaryKind.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                            P2pSendSummaryKind.ATTENTION -> MaterialTheme.colorScheme.errorContainer
                            P2pSendSummaryKind.ACTIVE -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = when (summary.kind) {
                            P2pSendSummaryKind.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
                            P2pSendSummaryKind.ATTENTION -> MaterialTheme.colorScheme.onErrorContainer
                            P2pSendSummaryKind.ACTIVE -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(summary.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                    Text(summary.detail, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = {
                                    downloadsInitialSection = DownloadLibrarySection.ACTIVITY
                                    selectedTabIndex = allTabs.indexOf(P2pMainTab.HISTORY)
                                    applyFocusStage(FocusStage.OFF)
                                }) { Text("Actividad") }
                            }
                            if (summary.kind == P2pSendSummaryKind.ACTIVE) {
                                LinearProgressIndicator(progress = { state.sendProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }

            if (!keyboardVisible && activeSupportCard != null &&
                selectedTab != P2pMainTab.MESSAGES && selectedTab != P2pMainTab.CHANNEL &&
                (selectedTab != P2pMainTab.SEND || activeSupportCard.isError)) {
                val currentSupportCardKey = supportCardKey.orEmpty()
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (activeSupportCard.isError) {
                        lerp(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.errorContainer,
                            0.42f
                        )
                    } else {
                        quietPanelColor
                    }
                ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiSpaceM, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                activeSupportCard.title,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                activeSupportCard.body,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = { runExperienceAction(activeSupportCard.action) }) {
                            Text(activeSupportCard.action.label, maxLines = 1)
                        }
                        TextButton(onClick = { dismissedSupportCardKey = currentSupportCardKey }) {
                            Text("X")
                        }
                    }
                }
            }

            Column(
                modifier = if (
                    selectedTab == P2pMainTab.MESSAGES ||
                    selectedTab == P2pMainTab.CHANNEL
                ) {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .then(if (useDedicatedTabViewport) Modifier else Modifier.verticalScroll(contentScroll))
                },
                verticalArrangement = Arrangement.spacedBy(UiSpaceS)
            ) {
            if (selectedTab == P2pMainTab.CONNECTION && connectionViewMode == ConnectionViewMode.ADVANCED && !focusEnabled && supportCard == null && !state.permissionGranted && !state.lanConnected) {
                if (!hideDirectConnectCard) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = sectionCardColors
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(UiSpaceM),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Permiso requerido", fontWeight = FontWeight.Bold)
                        Text("Concede ${state.permissionName} para usar Wi-Fi Direct.")
                        Button(onClick = onRequestPermission) {
                            Text("Conceder permiso")
                        }
                    }
                }
                }
            }

            if (
                selectedTab == P2pMainTab.MESSAGES ||
                selectedTab == P2pMainTab.CHANNEL
            ) {
                P2pMessagesTab(
                    state = state,
                    activeChannel = when (selectedTab) {
                        P2pMainTab.CHANNEL -> ChatChannel.GLOBAL
                        else -> ChatChannel.DIRECT
                    },
                    experience = chatExperience,
                    onSetGlobalLanJoined = onSetGlobalLanJoined,
                    onAutoDownloadChannelFilesChange = onAutoDownloadChannelFilesChange,
                    onOpenConnectTab = {
                        selectedTabIndex = allTabs.indexOf(P2pMainTab.CONNECTION)
                        applyFocusStage(FocusStage.CONNECT)
                    },
                    onChatDraftChange = onChatDraftChange,
                    onSelectChatDirectPeer = onSelectChatDirectPeer,
                    onDownloadChannelFileOffer = onDownloadChannelFileOffer,
                    onPickFile = pickFilesForContext,
                    onClearSelectedFiles = clearFilesForContext,
                    onSendMessage = onSendMessage,
                    onRetryMessage = onRetryMessage,
                    onCancelQueuedMessage = onCancelQueuedMessage,
                    onDeleteMessage = onDeleteMessage,
                    onClearMessages = onClearMessages,
                    onSyncToken = onSyncToken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            if (selectedTab == P2pMainTab.SEND && showQueueDetails) {
                val queueListState = rememberLazyListState()
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = sectionCardColors
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(UiSpaceM),
                        verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                    ) {
                        Text("Cola de envío", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Aquí verás lo que está en curso, en espera o con error.", style = MaterialTheme.typography.bodySmall)

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                            verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                        ) {
                            StatusChip(
                                label = "Pendientes $queuePendingCount",
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            StatusChip(
                                label = "Activos $queueRunningCount",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (queueFailedCount > 0) {
                                StatusChip(
                                    label = "Fallidos $queueFailedCount",
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                            verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                        ) {
                            Button(
                                onClick = if (state.paused) onResumeTransfers else onPauseTransfers
                            ) {
                                Text(if (state.paused) "Reanudar todo" else "Pausar todo")
                            }
                            OutlinedButton(
                                onClick = onCancelActiveTransfer,
                                enabled = state.sending || state.sendActiveCount > 0
                            ) {
                                Text(activeTransferCancelLabel)
                            }
                            OutlinedButton(
                                onClick = { showQueueDetails = false }
                            ) {
                                Text("Ocultar cola")
                            }
                        }

                        if (state.sendStatus.isNotBlank()) {
                            Text(state.sendStatus, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!state.sendFailureCause.isNullOrBlank()) {
                            Text(
                                friendlyTransferIssue(state.sendFailureCause, "No se pudo continuar con el envío.") ?: "No se pudo continuar con el envío.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            state = queueListState,
                            verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                        ) {
                            if (state.sendQueue.isEmpty()) {
                                item("empty-queue") {
                                    EmptyStateBlock(
                                        title = "No hay tareas en cola",
                                        body = "Agrega archivos desde la pestaña Enviar para gestionarlos aqui.",
                                        actionLabel = "Ir a Enviar",
                                        onAction = {
                                            selectedTabIndex = allTabs.indexOf(P2pMainTab.SEND)
                                        }
                                    )
                                }
                            } else {
                                items(state.sendQueue, key = { it.id }) { item ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = quietPanelColor,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(UiSpaceM),
                                            verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                                        ) {
                                            var itemActionsExpanded by rememberSaveable(item.id) { mutableStateOf(false) }
                                            Text(item.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                "${queueStatusLabel(item.status)} · ${formatBytes(item.sentBytes)}/${formatBytes(item.totalBytes)}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            if (item.totalBytes > 0L && !isTerminalQueueStatus(item.status)) {
                                                LinearProgressIndicator(
                                                    progress = {
                                                        (item.sentBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            if (!item.lastError.isNullOrBlank()) {
                                                Text(
                                                    item.lastError,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            val canPause = item.status == SendQueueStatus.RUNNING ||
                                                item.status == SendQueueStatus.QUEUED ||
                                                item.status == SendQueueStatus.RETRY_WAIT
                                            val canResume = item.status == SendQueueStatus.PAUSED
                                            val canCancel = !isTerminalQueueStatus(item.status)
                                            val canPrioritize = item.status == SendQueueStatus.QUEUED ||
                                                item.status == SendQueueStatus.PAUSED ||
                                                item.status == SendQueueStatus.RETRY_WAIT
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(UiSpaceS)
                                            ) {
                                                if (canPause) {
                                                    OutlinedButton(onClick = { onPauseQueueItem(item.id) }) {
                                                        Text("Pausar")
                                                    }
                                                }
                                                if (canResume) {
                                                    OutlinedButton(onClick = { onResumeQueueItem(item.id) }) {
                                                        Text("Reanudar")
                                                    }
                                                }
                                                if (canCancel || canPrioritize) {
                                                    Box {
                                                    OutlinedButton(
                                                        onClick = { itemActionsExpanded = true },
                                                        enabled = true
                                                    ) {
                                                        Text("Más")
                                                    }
                                                    DropdownMenu(
                                                        expanded = itemActionsExpanded,
                                                        onDismissRequest = { itemActionsExpanded = false }
                                                    ) {
                                                        if (canPrioritize) {
                                                            DropdownMenuItem(
                                                                text = { Text("Subir prioridad") },
                                                                onClick = {
                                                                    itemActionsExpanded = false
                                                                    onMoveQueueItemUp(item.id)
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text("Bajar prioridad") },
                                                                onClick = {
                                                                    itemActionsExpanded = false
                                                                    onMoveQueueItemDown(item.id)
                                                                }
                                                            )
                                                        }
                                                        if (canCancel) {
                                                            DropdownMenuItem(
                                                                text = { Text("Cancelar tarea") },
                                                                onClick = {
                                                                    itemActionsExpanded = false
                                                                    onCancelQueueItem(item.id)
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                DeveloperFooter(onOpenGithub = openDeveloperProfile)
            }

            if (selectedTab == P2pMainTab.CONNECTION && connectionViewMode != ConnectionViewMode.ADVANCED) {
                P2pConnectTab(
                    state = state,
                    onModeChange = { mode ->
                        onConnectionViewModeChange(ConnectionViewMode.fromConnectionMode(mode))
                    },
                    onRequestPermission = onRequestPermission,
                    onOpenWifiSettings = onOpenWifiSettings,
                    onStartHost = onStartHost,
                    onStartClient = onStartClient,
                    onCancelConnect = onCancelConnect,
                    onDisconnect = onDisconnect,
                    onConnectToPeer = onConnectToPeer,
                    onScan = onScanLanPeers,
                    onCancelScan = onCancelLanScan,
                    onUsePeer = onSyncFromPeerIp,
                    onSyncSession = onSyncToken,
                    onConfirmManualSession = onConfirmManualSession,
                    onRenewSession = onRenewSession,
                    onTokenChange = onTokenChange,
                    onPinChange = onPinChange,
                    onCopyToken = onCopyToken,
                    onPasteToken = onPasteToken,
                    onOpenSend = {
                        selectedTabIndex = allTabs.indexOf(P2pMainTab.SEND)
                        applyFocusStage(FocusStage.OFF)
                    },
                    onOpenChat = {
                        selectedTabIndex = allTabs.indexOf(P2pMainTab.MESSAGES)
                        applyFocusStage(FocusStage.OFF)
                    },
                    onSaveFavorite = onSaveSuggestedFavorite,
                    onSkipFavorite = onSkipSuggestedFavorite,
                    onTrustPeer = onTrustPeer,
                    onRequestForgetPeer = { pendingForgetPeerId = it },
                    modifier = Modifier.weight(1f)
                )
            }

            if (selectedTab == P2pMainTab.CONNECTION && connectionViewMode == ConnectionViewMode.ADVANCED) {
                if (!state.favoriteSuggestionPeerId.isNullOrBlank() && !state.favoriteSuggestionLabel.isNullOrBlank()) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        )
                    ) {
                        val peerId = state.favoriteSuggestionPeerId.orEmpty()
                        val peerLabel = state.favoriteSuggestionLabel.orEmpty()
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(UiSpaceM),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Conexión nueva detectada", fontWeight = FontWeight.Bold)
                            Text("¿Guardar $peerLabel como favorito?")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onSaveSuggestedFavorite(peerId) }) {
                                    Text("Guardar favorito")
                                }
                                OutlinedButton(onClick = { onSkipSuggestedFavorite(peerId) }) {
                                    Text("No ahora")
                                }
                            }
                        }
                    }
                }

                if (!hideDirectConnectCard) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = sectionCardColors
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(UiSpaceM),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (simpleWifiDirectMode) "Wi-Fi Direct" else "Conectar",
                                fontWeight = FontWeight.Bold
                            )
                            if (simpleWifiDirectMode) {
                                Text(
                                    "Comparte cerca, aunque no tengas internet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                DirectFlowPanel(
                                    title = directFlowHeadline,
                                    body = directFlowBody,
                                    steps = directFlowSteps,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else if (connectionViewMode == ConnectionViewMode.ADVANCED) {
                                ConnectionViewModeRow()
                                Text(
                                    when (connectionViewMode) {
                                        ConnectionViewMode.WIFI_DIRECT -> "Enlace 1 a 1 entre dos equipos."
                                        ConnectionViewMode.LAN -> "Usa la misma Wi-Fi para detectar equipos."
                                        ConnectionViewMode.ADVANCED -> "Vista completa para ajustar rutas y respaldo."
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                                    verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                                ) {
                                    StatusChip(
                                        label = if (state.wifiDirectModeEnabled) {
                                            when {
                                                directReadyForExchange -> "Direct · ${state.directTargetLabel ?: "enlazado"}"
                                                state.p2pEnabled -> "Direct listo"
                                                else -> "Direct apagado"
                                            }
                                        } else {
                                            "Direct desactivado"
                                        },
                                        containerColor = if (state.wifiDirectModeEnabled && state.p2pEnabled) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        contentColor = if (state.wifiDirectModeEnabled && state.p2pEnabled) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    StatusChip(
                                        label = if (state.lanModeEnabled) {
                                            when {
                                                state.resolvedTargetMode == ConnectionMode.LAN && !state.resolvedTargetIp.isNullOrBlank() -> {
                                                    "LAN · ${resolvedTargetLabel ?: state.resolvedTargetIp}"
                                                }
                                                state.lanConnected -> "LAN ${state.lanLocalIp ?: "lista"}"
                                                else -> "LAN sin red"
                                            }
                                        } else {
                                            "LAN desactivada"
                                        },
                                        containerColor = if (state.lanModeEnabled && state.lanConnected) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        contentColor = if (state.lanModeEnabled && state.lanConnected) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    if (!isCompactScreen) {
                                        StatusChip(
                                            label = connectionLabel,
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                            if (!simpleWifiDirectMode && showLanSecondaryHelp) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = quietPanelColor,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        connectionStepBody,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = UiSpaceM, vertical = 12.dp)
                                    )
                                }
                            }
                            if (!state.resolvedTargetIp.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = when (state.resolvedTargetMode) {
                                        ConnectionMode.WIFI_DIRECT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
                                        ConnectionMode.LAN -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
                                        else -> quietPanelColor
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = UiSpaceM, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            when (state.resolvedTargetMode) {
                                                ConnectionMode.WIFI_DIRECT -> "Ruta preferida: Directo"
                                                ConnectionMode.LAN -> "Ruta lista: misma Wi-Fi"
                                                else -> "Ruta lista"
                                            },
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            when (state.resolvedTargetMode) {
                                                ConnectionMode.WIFI_DIRECT ->
                                                    state.resolvedTargetLabel?.let { "Con $it." } ?: "Lista para enviar y chatear."
                                                ConnectionMode.LAN ->
                                                    state.resolvedTargetLabel?.let { "Con $it en esta Wi-Fi." } ?: "Lista para enviar y chatear."
                                                else ->
                                                    "Lista para continuar."
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                            if (connectionViewMode == ConnectionViewMode.ADVANCED && !alternateModeHint.isNullOrBlank() && !isCompactScreen) {
                                Text(
                                    alternateModeHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                                verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                            ) {
                                if (activeConnectionMode == ConnectionMode.WIFI_DIRECT) {
                                    if (directReadyForExchange) {
                                        OutlinedButton(onClick = onDisconnect) {
                                            Text("Cerrar enlace")
                                        }
                                    } else if (connected) {
                                        OutlinedButton(onClick = onCancelConnect) {
                                            Text(if (isHost) "Cancelar enlace" else "Salir del enlace")
                                        }
                                    } else if (!state.p2pEnabled) {
                                        OutlinedButton(onClick = onOpenWifiSettings) {
                                            Text("Abrir Wi-Fi")
                                        }
                                    } else {
                                        Button(
                                            onClick = onStartHost,
                                            enabled = state.wifiDirectModeEnabled && state.permissionGranted && !directBusy
                                        ) {
                                            Text("Crear enlace")
                                        }
                                        OutlinedButton(
                                            onClick = onStartClient,
                                            enabled = state.wifiDirectModeEnabled && state.permissionGranted && !directBusy
                                        ) {
                                            Text(if (isCompactScreen) "Buscar" else "Buscar enlace")
                                        }
                                    }
                                    if (directBusy && !connected && !directReadyForExchange) {
                                        OutlinedButton(onClick = onCancelConnect) {
                                            Text(
                                                if (state.directCreatingGroup) "Cancelar enlace"
                                                else if (isCompactScreen) "Cancelar"
                                                else "Cancelar búsqueda"
                                            )
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = if (state.lanScanning) onCancelLanScan else onScanLanPeers,
                                        enabled = state.lanModeEnabled
                                    ) {
                                        Text(
                                            if (state.lanScanning) {
                                                "Cancelar búsqueda"
                                            } else {
                                                "Buscar dispositivos"
                                            }
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onUseSuggestedTarget,
                                        enabled = state.lanModeEnabled && !state.suggestedTargetIp.isNullOrBlank()
                                    ) {
                                        Text(if (isCompactScreen) "Usar IP" else "Usar IP sugerida")
                                    }
                                    if (connectionViewMode == ConnectionViewMode.ADVANCED && !state.lanConnected) {
                                        OutlinedButton(onClick = onOpenWifiSettings) {
                                            Text(if (isCompactScreen) "Ajustes" else "Ajustes Wi-Fi")
                                        }
                                    }
                                }
                            }
                            if (!wifiDirectRoleHint.isNullOrBlank() && state.p2pEnabled && !simpleWifiDirectMode) {
                                Text(
                                    wifiDirectRoleHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (connectionViewMode == ConnectionViewMode.ADVANCED && !isCompactScreen) {
                                Text(
                                    connectionNarrative,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (showMinimalSessionCard) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = quietPanelColor
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = UiSpaceM, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    "Sesión activa",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    sessionLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (compactSyncLabel != null) {
                                StatusChip(
                                    label = compactSyncLabel,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = sectionCardColors
                    ) {
                        StageHeader(
                            title = "Sesión",
                            summary = when {
                                state.sessionExpired -> "Sesión expirada. Renueva para continuar."
                                compactSyncLabel != null -> compactSyncLabel
                                else -> "Activa y lista."
                            },
                            minimized = !securityExpanded,
                            expanded = securityExpanded,
                            onOpen = { securityExpanded = true },
                            onToggleExpanded = {
                                securityExpanded = !securityExpanded
                                if (!securityExpanded) sessionDetailsExpanded = false
                            },
                            onMinimize = {
                                securityExpanded = false
                                sessionDetailsExpanded = false
                            },
                            modifier = Modifier.padding(UiSpaceM),
                            minimizedContent = {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                                    verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                                ) {
                                    StatusChip(
                                        label = sessionLabel,
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
                                    if (compactSyncLabel != null) {
                                        StatusChip(
                                            label = compactSyncLabel,
                                            containerColor = if (state.sessionExpired) {
                                                MaterialTheme.colorScheme.errorContainer
                                            } else {
                                                MaterialTheme.colorScheme.primaryContainer
                                            },
                                            contentColor = if (state.sessionExpired) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            }
                                        )
                                    }
                                }
                            },
                            expandedContent = {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                                    verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                                ) {
                                    StatusChip(
                                        label = sessionLabel,
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
                                    if (compactSyncLabel != null) {
                                        StatusChip(
                                            label = compactSyncLabel,
                                            containerColor = if (state.sessionExpired) {
                                                MaterialTheme.colorScheme.errorContainer
                                            } else {
                                                MaterialTheme.colorScheme.primaryContainer
                                            },
                                            contentColor = if (state.sessionExpired) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            }
                                        )
                                    }
                                }

                                sessionStatusText?.let { statusText ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (state.sessionExpired) {
                                            lerp(
                                                MaterialTheme.colorScheme.surface,
                                                MaterialTheme.colorScheme.errorContainer,
                                                0.42f
                                            )
                                        } else {
                                            quietPanelColor
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            statusText,
                                            color = if (state.sessionExpired) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(horizontal = UiSpaceM, vertical = 10.dp)
                                        )
                                    }
                                }

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                                    verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                                ) {
                                    if (showSessionActionButton) {
                                        Button(
                                            onClick = {
                                                if (requiresManualPairing) {
                                                    sessionDetailsExpanded = true
                                                } else if (state.sessionExpired) {
                                                    onRenewSession()
                                                } else {
                                                    onSyncToken()
                                                }
                                            }
                                        ) {
                                            Text(sessionActionLabel)
                                        }
                                    }
                                    OutlinedButton(onClick = { sessionDetailsExpanded = !sessionDetailsExpanded }) {
                                        Text(if (sessionDetailsExpanded) "Ocultar credenciales" else "Ver credenciales")
                                    }
                                    Box {
                                        OutlinedButton(onClick = { securityMoreExpanded = true }) {
                                            Text("Más")
                                        }
                                        DropdownMenu(
                                            expanded = securityMoreExpanded,
                                            onDismissRequest = { securityMoreExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Renovar 30m") },
                                                onClick = {
                                                    securityMoreExpanded = false
                                                    onRenewSession()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Copiar token") },
                                                onClick = {
                                                    securityMoreExpanded = false
                                                    onCopyToken()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Pegar token") },
                                                onClick = {
                                                    securityMoreExpanded = false
                                                    onPasteToken()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Nuevo token") },
                                                onClick = {
                                                    securityMoreExpanded = false
                                                    onGenerateToken()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Nuevo PIN") },
                                                onClick = {
                                                    securityMoreExpanded = false
                                                    onGeneratePin()
                                                }
                                            )
                                        }
                                    }
                                }

                                if (sessionDetailsExpanded) {
                                    Text(
                                        "Para conectar manualmente, introduce el código de sesión y el PIN que muestra el equipo receptor. Después confirma los datos.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = state.authToken,
                                        onValueChange = onTokenChange,
                                        singleLine = true,
                                        label = { Text("Código de sesión") },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = state.sessionPin,
                                        onValueChange = onPinChange,
                                        singleLine = true,
                                        label = { Text("PIN") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = onConfirmManualSession,
                                        enabled = !state.sessionExpired &&
                                            !state.sessionSyncing &&
                                            FileTransfer.isValidToken(state.authToken) && TransferSecurity.isValidPin(state.sessionPin) &&
                                            (if (state.activeConnectionMode == ConnectionMode.WIFI_DIRECT) !state.directTargetIp.isNullOrBlank() else !state.resolvedTargetIp.isNullOrBlank())
                                    ) { Text("Usar esta sesión") }

                                }
                            }
                        )
                    }
                }

                if (showTrustedPeersPanel) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = sectionCardColors
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(UiSpaceM),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Confianza", fontWeight = FontWeight.Bold)

                            if (state.pendingCredentialShare != null) {
                                val req = state.pendingCredentialShare
                                Text("Solicitud de sesión · ${req.label}")
                                Text(req.ip, style = MaterialTheme.typography.bodySmall)
                                credentialRequestFingerprint(req)?.let { fingerprint ->
                                    Text("Huella: $fingerprint", style = MaterialTheme.typography.labelMedium)
                                }
                                Text(
                                    "Confirma que reconoces este equipo antes de compartir la sesión.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(onClick = { onApproveCredentialShare(req) }, enabled = credentialRequestFingerprint(req) != null) {
                                        Text("Aprobar")
                                    }
                                    OutlinedButton(onClick = { onRejectCredentialShare(req) }) {
                                        Text("Rechazar")
                                    }
                                }
                                HorizontalDivider()
                            }

                            if (state.pendingTrust != null) {
                                val req = state.pendingTrust
                                Text("Pendiente de confianza · ${req.label}")
                                Text(req.ip, style = MaterialTheme.typography.bodySmall)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(onClick = { onTrustPeer(req) }) {
                                        Text("Confiar este dispositivo")
                                    }
                                }
                                HorizontalDivider()
                            }

                            if (state.trustedPeers.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${state.trustedPeers.size} equipos recordados",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(onClick = { trustExpanded = !trustExpanded }) {
                                        Text(if (trustExpanded) "Ocultar" else "Ver")
                                    }
                                }
                            }

                            if (trustExpanded && state.trustedPeers.isNotEmpty()) {
                                state.trustedPeers.take(8).forEachIndexed { index, peer ->
                                    val displayName = TrustedPeerStore.displayName(peer)
                                    Text(displayName, fontWeight = FontWeight.SemiBold)
                                    if (!peer.lastKnownIp.isNullOrBlank()) {
                                        Text("IP conocida: ${peer.lastKnownIp}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onSetPeerFavorite(peer.id, !peer.favorite) }
                                        ) {
                                            Text(if (peer.favorite) "Quitar favorito" else "Favorito")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                aliasEditorPeerId = peer.id
                                                aliasEditorValue = peer.alias
                                            }
                                        ) {
                                            Text("Editar apodo")
                                        }
                                        TextButton(onClick = { pendingForgetPeerId = peer.id }) {
                                            Text("Olvidar equipo", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    Text(
                                        "Apodo: ${peer.alias.ifBlank { "sin definir" }}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (index < state.trustedPeers.take(8).lastIndex) {
                                        HorizontalDivider(Modifier.padding(vertical = UiSpaceS))
                                    }
                                }
                            }
                        }
                    }
                }

                if (
                    when (connectionViewMode) {
                        ConnectionViewMode.WIFI_DIRECT ->
                            state.wifiDirectModeEnabled &&
                                state.p2pEnabled &&
                                !connected &&
                                state.peers.isNotEmpty()
                        ConnectionViewMode.LAN -> false
                        ConnectionViewMode.ADVANCED -> state.wifiDirectModeEnabled && state.p2pEnabled && !connected
                    }
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = sectionCardColors
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(UiSpaceM),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                when {
                                    state.peers.isNotEmpty() -> "Equipos encontrados"
                                    state.directDiscovering -> "Buscando equipos"
                                    else -> "Equipos cercanos"
                                },
                                fontWeight = FontWeight.Bold
                            )

                            if (state.peers.isNotEmpty()) {
                                Text(
                                    "Elige el equipo que creó el enlace y toca Conectar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (state.peers.isEmpty()) {
                                EmptyStateBlock(
                                    title = if (state.directDiscovering) "Buscando equipos" else "Aún no hay equipos",
                                    body = if (state.directDiscovering) {
                                        "Mantén el otro equipo con el enlace abierto. La lista se actualizará sola."
                                    } else {
                                        "Activa Wi-Fi Direct en ambos equipos y vuelve a buscar."
                                    },
                                    actionLabel = null,
                                    onAction = null
                                )
                            } else {
                                state.peers.forEachIndexed { index, peer ->
                                    val knownPeer = state.knownServicePeers.firstOrNull {
                                        it.label.equals(peer.name, ignoreCase = true)
                                    }
                                    val trustedFromKnown = knownPeer?.id?.let { knownId ->
                                        state.trustedPeers.firstOrNull { tp -> tp.id == knownId }
                                    }
                                    val trustedPeer = trustedFromKnown ?: state.trustedPeers.firstOrNull { tp ->
                                        TrustedPeerStore.displayName(tp).equals(peer.name, ignoreCase = true) ||
                                            tp.label.equals(peer.name, ignoreCase = true)
                                    }
                                    val trusted = knownPeer?.trusted == true || trustedPeer != null
                                    val favorite = trustedPeer?.favorite == true
                                    val peerIp = knownPeer?.ip ?: trustedPeer?.lastKnownIp
                                    val lastSeenAtMs = maxOf(
                                        knownPeer?.lastSeenAtMs ?: 0L,
                                        trustedPeer?.lastSeenAtMs ?: 0L
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = quietPanelColor,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(UiSpaceM),
                                            verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                                        ) {
                                            Text(peer.name, fontWeight = FontWeight.SemiBold)
                                            if (connectionViewMode == ConnectionViewMode.ADVANCED) {
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                                                    verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                                                ) {
                                                    StatusChip(
                                                        label = peerStatusLabel(peer.status),
                                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                    StatusChip(
                                                        label = if (trusted) "Confiable" else "No confiable",
                                                        containerColor = if (trusted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                        contentColor = if (trusted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (favorite) {
                                                        StatusChip(
                                                            label = "Favorito",
                                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                                        )
                                                    }
                                                }

                                                if (!peerIp.isNullOrBlank()) {
                                                    Text("IP conocida: $peerIp", style = MaterialTheme.typography.bodySmall)
                                                }
                                                Text(
                                                    "Última vez: ${formatRelativeSeen(lastSeenAtMs, state.nowMs)}",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            } else {
                                                Text(
                                                    peerStatusLabel(peer.status),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Button(
                                                onClick = { onConnectToPeer(peer.address) },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Conectar")
                                            }
                                        }
                                    }
                                    if (index < state.peers.lastIndex) {
                                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                val lanPeers = state.knownServicePeers
                    .filter { it.ip.isNotBlank() }
                    .distinctBy { it.ip }
                    .sortedWith(compareByDescending<KnownPeerSnapshot> { it.trusted }.thenByDescending { it.lastSeenAtMs })
                    .take(12)

                if (
                    when (connectionViewMode) {
                        ConnectionViewMode.WIFI_DIRECT -> false
                        ConnectionViewMode.LAN -> state.lanModeEnabled
                        ConnectionViewMode.ADVANCED -> state.lanModeEnabled
                    }
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = sectionCardColors
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(UiSpaceM),
                            verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                        ) {
                            Text("Equipos en esta Wi-Fi", fontWeight = FontWeight.Bold)
                            Text(
                                "Equipos detectados por IP en la misma red.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (lanPeers.isEmpty()) {
                                EmptyStateBlock(
                                    title = "Aún no hay equipos en esta Wi-Fi",
                                    body = "Busca esta red o usa una IP sugerida para dejar un destino listo.",
                                    actionLabel = when {
                                        state.lanScanning -> "Cancelar búsqueda"
                                        state.suggestedTargetIp.isNullOrBlank() -> "Buscar dispositivos"
                                        else -> "Usar IP sugerida"
                                    },
                                    onAction = when {
                                        state.lanScanning -> onCancelLanScan
                                        state.suggestedTargetIp.isNullOrBlank() -> onScanLanPeers
                                        else -> onUseSuggestedTarget
                                    }
                                )
                            } else {
                                lanPeers.forEachIndexed { index, peer ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = quietPanelColor,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(UiSpaceS),
                                            verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                                        ) {
                                            Text(peer.label, fontWeight = FontWeight.SemiBold)
                                            Text(peer.ip, style = MaterialTheme.typography.bodySmall)
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(UiSpaceS)
                                            ) {
                                                StatusChip(
                                                    label = if (peer.trusted) "Confiable" else "Por aprobar",
                                                    containerColor = if (peer.trusted) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.secondaryContainer
                                                    },
                                                    contentColor = if (peer.trusted) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSecondaryContainer
                                                    }
                                                )
                                                StatusChip(
                                                    label = "Visto ${formatRelativeSeen(peer.lastSeenAtMs, state.nowMs)}",
                                                    containerColor = MaterialTheme.colorScheme.surface,
                                                    contentColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            FlowRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                                                verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                                            ) {
                                                Button(
                                                    onClick = { onSyncFromPeerIp(peer.ip) }
                                                ) {
                                                    Text("Sincronizar sesión")
                                                }
                                                OutlinedButton(
                                                    onClick = { onTargetIpChange(peer.ip) }
                                                ) {
                                                    Text("Usar como destino")
                                                }
                                            }
                                        }
                                    }
                                    if (index < lanPeers.lastIndex) {
                                        HorizontalDivider(Modifier.padding(vertical = UiSpaceS))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (selectedTab == P2pMainTab.SEND && !showQueueDetails) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = sectionCardColors
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(UiSpaceM),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Elige qué compartir", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Fotos, documentos y más. Puedes elegir varios archivos a la vez.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (com.example.wifidrop.presentation.isIncompleteAttachmentRecovery(state.shareImportStatus)) {
                            Text(
                                state.shareImportStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (state.selectedFilesCount == 0) {
                            Button(onClick = pickFilesForContext, modifier = Modifier.fillMaxWidth()) {
                                Icon(imageVector = Icons.Rounded.AttachFile, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Elegir archivos")
                            }
                        }

                        val resolved = state.resolvedTargetIp
                        val resolvedLabel = state.resolvedTargetLabel ?: resolved
                        val hasResolvedRoute = !resolved.isNullOrBlank()
                        val routeTitle = when {
                            hasResolvedRoute && !state.sessionReady -> "Sesión por confirmar"
                            simpleWifiDirectMode && hasResolvedRoute -> {
                                "Directo listo"
                            }
                            simpleWifiDirectMode && !state.permissionGranted -> {
                                "Falta permiso"
                            }
                            simpleWifiDirectMode && !state.p2pEnabled -> {
                                "Abre Wi-Fi del sistema"
                            }
                            simpleWifiDirectMode && state.directCreatingGroup -> {
                                "Enlace creado"
                            }
                            simpleWifiDirectMode && state.directConnecting -> {
                                "Uniéndote al enlace"
                            }
                            simpleWifiDirectMode && state.directDiscovering && state.peers.isNotEmpty() -> {
                                "Elige un equipo"
                            }
                            simpleWifiDirectMode && state.directDiscovering -> {
                                "Buscando equipos"
                            }
                            simpleWifiDirectMode -> {
                                "Falta equipo"
                            }
                            state.resolvedTargetMode == ConnectionMode.WIFI_DIRECT && !resolved.isNullOrBlank() -> {
                                "Ruta preferida: Directo"
                            }
                            state.resolvedTargetMode == ConnectionMode.LAN && !resolved.isNullOrBlank() -> {
                                "Ruta lista: misma Wi-Fi"
                            }
                            else -> "Elige un equipo cuando estés listo"
                        }
                        val routeBody = when {
                            hasResolvedRoute && !state.sessionReady -> "El equipo está seleccionado. Confirma su sesión en Conectar antes de enviar."
                            simpleWifiDirectMode && hasResolvedRoute -> {
                                resolvedLabel?.let { "Enviarás directo a $it." } ?: "El equipo ya quedó listo para recibir."
                            }
                            simpleWifiDirectMode && !state.permissionGranted -> {
                                "Concede el permiso y luego vuelve a Conectar para dejar el enlace listo."
                            }
                            simpleWifiDirectMode && !state.p2pEnabled -> {
                                "La app no puede activar Directo. Abre Wi-Fi del sistema y después crea o busca un enlace."
                            }
                            simpleWifiDirectMode && state.directCreatingGroup -> {
                                "En el otro equipo toca Buscar enlace para terminar la conexión."
                            }
                            simpleWifiDirectMode && state.directConnecting -> {
                                "Espera un momento mientras ambos equipos terminan de enlazarse."
                            }
                            simpleWifiDirectMode && state.directDiscovering && state.peers.isNotEmpty() -> {
                                "Termina la selección en Conectar y luego vuelve aquí."
                            }
                            simpleWifiDirectMode && state.directDiscovering -> {
                                "Mantén el otro equipo con el enlace abierto para que aparezca."
                            }
                            simpleWifiDirectMode -> {
                                "Puedes preparar los archivos ahora y conectar un equipo después."
                            }
                            state.resolvedTargetMode == ConnectionMode.WIFI_DIRECT && !resolved.isNullOrBlank() -> {
                                resolvedLabel?.let { "Directo listo con $it." } ?: "Directo listo para enviar."
                            }
                            state.resolvedTargetMode == ConnectionMode.LAN && !resolved.isNullOrBlank() -> {
                                resolvedLabel?.let { "Equipo listo: $it." } ?: "Equipo listo en esta Wi-Fi."
                            }
                            !state.suggestedTargetIp.isNullOrBlank() -> {
                                "Usa la IP sugerida o elige otro destino."
                            }
                            else -> "Puedes preparar los archivos ahora y conectar un equipo después."
                        }
                        val showDestinationChooser = when {
                            connectionViewMode != ConnectionViewMode.ADVANCED -> false
                            else -> showAdvancedTargetOptions || !hasResolvedRoute
                        }
                        val canUseLastDestination = state.sessionReady && state.lastSendTargetIp == resolved && state.lastSendTargetIp != null &&
                            state.selectedFilesCount > 0 &&
                            !state.sessionExpired
                        val showSendMore = state.selectedFilesCount > 0
                        val sendSelectionLabel = when {
                            state.selectedFilesCount <= 0 -> "Aún no hay archivos listos"
                            state.selectedFilesCount == 1 -> "1 archivo listo"
                            else -> "${state.selectedFilesCount} archivos listos"
                        }
                        val sendDisabledReason = when {
                            state.selectedFilesCount <= 0 -> null
                            state.sessionExpired -> "Sesión expirada. Renueva la sesión antes de enviar."
                            !hasResolvedRoute -> "Conecta un equipo para enviarlos. Tu selección se queda aquí."
                            !state.sessionReady -> "Confirma la sesión con el receptor en Conectar antes de enviar."
                            else -> null
                        }
                        val hasSendActivity = state.shareImportStatus.isNotBlank() ||
                            state.sendBatchTotal > 0 ||
                            state.sending ||
                            state.sendQueue.isNotEmpty() ||
                            !state.sendFailureCause.isNullOrBlank() ||
                            state.sendStatus.isNotBlank()
                        val sendActivitySummary = buildList {
                            if (state.sending) add("En curso")
                            if (queuePendingCount > 0) add("$queuePendingCount pendientes")
                            if (queueRunningCount > 0) add("$queueRunningCount activos")
                            if (queueFailedCount > 0) add("$queueFailedCount para reintentar")
                        }.joinToString(" · ")

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = quietPanelColor,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = UiSpaceM, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                            ) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                                    verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                                ) {
                                    StatusChip(
                                        label = routeTitle,
                                        containerColor = when (state.resolvedTargetMode) {
                                            ConnectionMode.WIFI_DIRECT -> MaterialTheme.colorScheme.primaryContainer
                                            ConnectionMode.LAN -> MaterialTheme.colorScheme.secondaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        contentColor = when (state.resolvedTargetMode) {
                                            ConnectionMode.WIFI_DIRECT -> MaterialTheme.colorScheme.onPrimaryContainer
                                            ConnectionMode.LAN -> MaterialTheme.colorScheme.onSecondaryContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    if (!resolved.isNullOrBlank()) {
                                        StatusChip(
                                            label = resolvedLabel ?: resolved,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(routeBody, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                            verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    selectedTabIndex = allTabs.indexOf(P2pMainTab.CONNECTION)
                                    applyFocusStage(FocusStage.OFF)
                                }
                            ) { Text(if (hasResolvedRoute) "Cambiar equipo" else "Conectar un equipo") }
                            if (connectionViewMode == ConnectionViewMode.ADVANCED) {
                                TextButton(onClick = { showAdvancedTargetOptions = !showAdvancedTargetOptions }) {
                                    Text(if (showDestinationChooser) "Ocultar direcciones" else "Elegir por IP")
                                }
                            }
                        }

                        if (showDestinationChooser) {
                            val favoriteDestinations = state.favoritePeers
                                .mapNotNull { peer ->
                                    val ip = peer.lastKnownIp?.trim().orEmpty()
                                    if (ip.isBlank()) null else TrustedPeerStore.displayName(peer) to ip
                                }
                                .distinctBy { it.second }
                                .take(4)
                            if (favoriteDestinations.isNotEmpty()) {
                                Text("Favoritos", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    favoriteDestinations.forEach { (label, ip) ->
                                        OutlinedButton(onClick = { onTargetIpChange(ip) }) {
                                            Text("$label · $ip")
                                        }
                                    }
                                }
                            }

                            val quickPeers = state.knownServicePeers
                                .filter { it.ip.isNotBlank() }
                                .distinctBy { it.ip }
                                .take(5)
                            if (quickPeers.isNotEmpty()) {
                                Text("Equipos detectados", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    quickPeers.forEach { kp ->
                                        OutlinedButton(onClick = { onTargetIpChange(kp.ip) }) {
                                            Text("${kp.label} · ${kp.ip}")
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = state.targetIp,
                                onValueChange = onTargetIpChange,
                                singleLine = true,
                                label = { Text("IP destino") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (!state.lastPeerLabel.isNullOrBlank() && showDestinationChooser) {
                            Text("Último equipo: ${state.lastPeerLabel}")
                        }

                        if (state.lastSendTargetIp != null && showDestinationChooser) {
                            Text(
                                "Último destino: ${state.lastSendTargetLabel ?: state.lastSendTargetIp} (${state.lastSendTargetIp})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (state.selectedFilesCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = quietPanelColor,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = UiSpaceM, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(sendSelectionLabel, fontWeight = FontWeight.SemiBold)
                                    }
                                    state.selectedFileNames.take(3).forEach { name ->
                                        Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (state.selectedFilesCount > 3) {
                                        TextButton(onClick = { showSelectedFilesReview = true }) {
                                            Text("Revisar ${state.selectedFilesCount} archivos")
                                        }
                                    }
                                }
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                            verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                        ) {
                            if (state.selectedFilesCount > 0) {
                                Button(
                                    onClick = onSendFile,
                                    enabled = sendDisabledReason == null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = null
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (state.selectedFilesCount == 1) "Enviar archivo" else "Enviar ${state.selectedFilesCount} archivos")
                                }
                                OutlinedButton(onClick = pickFilesForContext) {
                                    Icon(
                                        imageVector = Icons.Rounded.AttachFile,
                                        contentDescription = null
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (isCompactScreen) "Cambiar" else "Cambiar archivos")
                                }
                            }
                            if (showSendMore) {
                                Box {
                                    OutlinedButton(onClick = { sendMoreExpanded = true }) {
                                        Text("Más")
                                    }
                                    DropdownMenu(
                                        expanded = sendMoreExpanded,
                                        onDismissRequest = { sendMoreExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Limpiar selección") },
                                            enabled = state.selectedFilesCount > 0,
                                            onClick = {
                                                sendMoreExpanded = false
                                                clearFilesForContext()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Enviar al último destino") },
                                            enabled = canUseLastDestination,
                                            onClick = {
                                                sendMoreExpanded = false
                                                onSendToLastTarget()
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (sendDisabledReason != null) {
                            Text(
                                sendDisabledReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (hasSendActivity) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = quietPanelColor,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = UiSpaceM, vertical = 12.dp),
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
                                                "Actividad de envío",
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (sendActivitySummary.isNotBlank()) {
                                                Text(
                                                    sendActivitySummary,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        TextButton(onClick = { showSendActivityDetails = !showSendActivityDetails }) {
                                            Text(if (showSendActivityDetails) "Ocultar" else "Ver")
                                        }
                                    }

                                    AnimatedVisibility(visible = showSendActivityDetails) {
                                        Column(verticalArrangement = Arrangement.spacedBy(UiSpaceS)) {
                                            if (state.shareImportStatus.isNotBlank()) {
                                                Text(
                                                    state.shareImportStatus,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            if (state.sendBatchTotal > 0) {
                                                Text(
                                                    "Lote: ${state.sendBatchCompleted}/${state.sendBatchTotal} OK · " +
                                                        "${state.sendBatchFailed} fallidos · ${state.sendBatchCanceled} cancelados",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            if (state.sending) {
                                                LinearProgressIndicator(
                                                    progress = { state.sendProgress.coerceIn(0f, 1f) },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                val pct = (state.sendProgress * 100).roundToInt().coerceIn(0, 100)
                                                Text("Progreso de envío: $pct%", style = MaterialTheme.typography.bodySmall)
                                            }

                                            if (state.sending || state.sendQueue.isNotEmpty() || state.sendBatchTotal > 0) {
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                                                    verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                                                ) {
                                                    StatusChip(
                                                        label = "Inst ${formatRate(state.sendInstantBps)}",
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    StatusChip(
                                                        label = "Media ${formatRate(state.sendAverageBps)}",
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    StatusChip(
                                                        label = "ETA ${formatEta(state.sendEtaSeconds)}",
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            if (!state.sendFailureCause.isNullOrBlank()) {
                                                Text(
                                                    friendlyTransferIssue(state.sendFailureCause, "No se pudo continuar con el envío.")
                                                        ?: "No se pudo continuar con el envío.",
                                                    color = MaterialTheme.colorScheme.error,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            if (state.sendStatus.isNotBlank()) {
                                                Text(
                                                    state.sendStatus,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            if (state.sendQueue.isNotEmpty()) {
                                                HorizontalDivider(Modifier.padding(vertical = UiSpaceS))
                                                Text("Cola", fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    "Pendientes: $queuePendingCount · Activos: $queueRunningCount · Fallidos: $queueFailedCount",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                if (!focusEnabled) {
                                                    OutlinedButton(
                                                        onClick = { showQueueDetails = !showQueueDetails }
                                                    ) {
                                                        Text(if (showQueueDetails) "Ocultar cola" else "Ver cola")
                                                    }
                                                } else {
                                                    Text(
                                                        "Desactiva modo foco para abrir la cola.",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }

            if (selectedTab == P2pMainTab.HISTORY) {
                P2pDownloadsTab(
                    state = state,
                    initialSection = downloadsInitialSection,
                    onRefresh = onRefreshReceived,
                    onOpenDownloads = onOpenDownloads,
                    onShareFile = onShareReceivedFile,
                    onOpenHistoryItem = onOpenHistoryItem,
                    onOpenConnect = {
                        selectedTabIndex = allTabs.indexOf(P2pMainTab.CONNECTION)
                        applyFocusStage(FocusStage.OFF)
                    },
                    onOpenSend = {
                        selectedTabIndex = allTabs.indexOf(P2pMainTab.SEND)
                        applyFocusStage(FocusStage.OFF)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

                if (!useDedicatedTabViewport) {
                    DeveloperFooter(onOpenGithub = openDeveloperProfile)
                }
            }

            snackbarHost()

            if (!keyboardVisible || (selectedTab != P2pMainTab.MESSAGES && selectedTab != P2pMainTab.CHANNEL)) {
            QetaraBottomNavigation(
                tabs = allTabs,
                selectedTabIndex = selectedTabIndex,
                statusLabel = if (focusEnabled) {
                    "Foco ${focusStage.title}"
                } else {
                    connectionLabel
                },
                onTabSelected = { index, tab ->
                    selectedTabIndex = index
                    if (tab != P2pMainTab.SEND) {
                        showQueueDetails = false
                    }
                    when (tab) {
                        P2pMainTab.MESSAGES -> {
                            if (state.chatChannel != ChatChannel.DIRECT) {
                                onChatChannelChange(ChatChannel.DIRECT)
                            }
                        }
                        P2pMainTab.CHANNEL -> {
                            if (state.chatChannel != ChatChannel.GLOBAL) {
                                onChatChannelChange(ChatChannel.GLOBAL)
                            }
                        }
                        else -> Unit
                    }
                    applyFocusStage(FocusStage.OFF)
                },
                tabLabel = ::tabLabel,
                tabIcon = ::tabIcon,
                modifier = Modifier.fillMaxWidth()
            )

            }

            if (!aliasEditorPeerId.isNullOrBlank()) {
                val editingPeerId = aliasEditorPeerId.orEmpty()
                val editingPeer = state.trustedPeers.firstOrNull { it.id == editingPeerId }
                val savedAlias = editingPeer?.alias.orEmpty()
                val normalizedSavedAlias = savedAlias.trim().replace(Regex("\\s+"), " ").take(48)
                val normalizedDraftAlias = aliasEditorValue.trim().replace(Regex("\\s+"), " ").take(48)
                val hasAliasChanges = normalizedDraftAlias != normalizedSavedAlias
                AlertDialog(
                    onDismissRequest = {
                        aliasEditorPeerId = null
                        aliasEditorValue = ""
                    },
                    title = { Text("Editar apodo") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(UiSpaceS)) {
                            Text(
                                "Equipo: ${editingPeer?.label ?: editingPeerId.take(8)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedTextField(
                                value = aliasEditorValue,
                                onValueChange = { aliasEditorValue = it.take(48) },
                                singleLine = true,
                                label = { Text("Apodo") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Déjalo vacío para quitar el apodo.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (hasAliasChanges) {
                                    onSetPeerAlias(editingPeerId, normalizedDraftAlias)
                                }
                                aliasEditorPeerId = null
                                aliasEditorValue = ""
                            },
                            enabled = hasAliasChanges
                        ) {
                            Text("Guardar")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                aliasEditorPeerId = null
                                aliasEditorValue = ""
                            }
                        ) {
                            Text("Cancelar")
                        }
                    }
                )
            }
    }
}
}
}
}
