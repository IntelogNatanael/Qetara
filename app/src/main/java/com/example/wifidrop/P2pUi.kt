package com.example.wifidrop

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription

import android.content.Intent
import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
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
import com.example.wifidrop.presentation.isSessionSyncManualPairingRequired
import com.example.wifidrop.presentation.isSessionSyncRetrying
import com.example.wifidrop.presentation.isSessionSyncApprovalRequired
import com.example.wifidrop.presentation.isSessionSyncFailure
import java.io.File
import kotlin.math.roundToInt

internal enum class P2pMainTab(val titleRes: Int) {
    CONNECTION(R.string.shell_connect),
    SEND(R.string.shell_send),
    MESSAGES(R.string.shell_chat),
    CHANNEL(R.string.shell_channel),
    HISTORY(R.string.shell_downloads);

    val title: String get() = appString(titleRes)
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
        !state.sessionEnabled -> stringResource(R.string.shell_session_closed)
        state.sessionExpired -> stringResource(R.string.shell_session_expired)
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
    val isDirectBusyNow = state.directDiscovering || state.directConnecting || state.directCreatingGroup
    val directActivityLabel = when {
        state.directCreatingGroup -> stringResource(R.string.shell_creating_link)
        state.directConnecting -> stringResource(R.string.shell_connecting)
        state.directDiscovering -> stringResource(R.string.shell_finding_devices)
        else -> null
    }
    val activeTransferCancelLabel = when {
        state.receiving && (state.sending || state.sendActiveCount > 0) -> stringResource(R.string.shell_cancel_transfers)
        state.receiving -> stringResource(R.string.shell_cancel_receiving)
        else -> stringResource(R.string.shell_cancel_sending)
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
        supportCard.action.command != experience.primaryAction.command &&
        !suppressDirectInactiveBanner &&
        !suppressDirectConnectionBanner
    val activeSupportCard = supportCard.takeIf { showSupportBanner }
    fun tabLabel(tab: P2pMainTab): String = tab.title

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
        directReadyForExchange -> stringResource(R.string.shell_direct_ready)
        !state.permissionGranted -> stringResource(R.string.shell_permission_missing)
        !state.p2pEnabled -> stringResource(R.string.shell_open_system_wifi)
        state.directCreatingGroup -> stringResource(R.string.shell_creating_link)
        state.directConnecting -> stringResource(R.string.shell_joining_link)
        state.directDiscovering && state.peers.isNotEmpty() -> stringResource(R.string.shell_choose_correct_device)
        state.directDiscovering -> stringResource(R.string.shell_finding_devices)
        else -> stringResource(R.string.shell_choose_how_to_start)
    }
    val directFlowBody = when {
        directReadyForExchange ->
            state.directTargetLabel?.let { stringResource(R.string.shell_ready_to_send_chat_with, (it).toString()) }
                ?: stringResource(R.string.shell_device_ready_to_send_chat)
        !state.permissionGranted ->
            stringResource(R.string.shell_grant_nearby_permission)
        !state.p2pEnabled ->
            stringResource(R.string.shell_cannot_enable_direct_wifi)
        state.directCreatingGroup ->
            stringResource(R.string.shell_link_open_find_on_other)
        state.directConnecting ->
            stringResource(R.string.shell_keep_both_open_direct)
        state.directDiscovering && state.peers.isNotEmpty() ->
            stringResource(R.string.shell_tap_correct_device)
        state.directDiscovering ->
            stringResource(R.string.shell_keep_other_link_open)
        else ->
            stringResource(R.string.shell_create_or_find_link_help)
    }
    val directFlowSteps = listOf(
        DirectFlowStepUi(
            label = stringResource(R.string.shell_step_wifi),
            done = state.permissionGranted && state.p2pEnabled,
            active = !state.permissionGranted || !state.p2pEnabled
        ),
        DirectFlowStepUi(
            label = stringResource(R.string.shell_step_create_or_find),
            done = directBusy || connected || directReadyForExchange,
            active = state.permissionGranted && state.p2pEnabled && !directBusy && !connected && !directReadyForExchange
        ),
        DirectFlowStepUi(
            label = stringResource(R.string.shell_step_choose_device),
            done = directReadyForExchange,
            active = state.directConnecting || (state.directDiscovering && state.peers.isNotEmpty()) || (connected && !directReadyForExchange)
        ),
        DirectFlowStepUi(
            label = stringResource(R.string.shell_step_ready),
            done = directReadyForExchange,
            active = connected && !directReadyForExchange
        )
    )
    val wifiDirectRoleHint = when {
        activeConnectionMode != ConnectionMode.WIFI_DIRECT -> null
        directReadyForExchange -> stringResource(R.string.shell_device_direct_ready)
        connected && isHost -> stringResource(R.string.shell_created_find_on_other)
        connected && !isHost -> stringResource(R.string.shell_joined_preparing_direct)
        state.directCreatingGroup -> stringResource(R.string.shell_device_creating_link)
        state.directConnecting -> stringResource(R.string.shell_device_joining_link)
        state.directDiscovering && state.peers.isNotEmpty() -> stringResource(R.string.shell_choose_correct_connect)
        state.directDiscovering -> stringResource(R.string.shell_keep_link_open_on_other)
        !state.permissionGranted -> stringResource(R.string.shell_grant_direct_permission_first)
        !state.p2pEnabled -> stringResource(R.string.shell_open_wifi_first)
        else -> stringResource(R.string.shell_initiator_create_or_find_help)
    }
    val connectionStepBody = when {
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && directReadyForExchange ->
            stringResource(R.string.shell_direct_ready_with, (state.directTargetLabel ?: stringResource(R.string.shell_your_device)).toString())
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && connected && isHost ->
            stringResource(R.string.shell_link_created_awaiting_other)
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && connected ->
            stringResource(R.string.shell_link_detected_preparing)
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directCreatingGroup ->
            stringResource(R.string.shell_creating_link_on_device)
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directConnecting ->
            stringResource(R.string.shell_joining_other_link)
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directDiscovering && state.peers.isNotEmpty() ->
            stringResource(R.string.shell_choose_initiating_device)
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directDiscovering ->
            stringResource(R.string.shell_finding_wifi_direct_devices)
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && !state.p2pEnabled ->
            stringResource(R.string.shell_open_wifi_enable_direct)
        activeConnectionMode == ConnectionMode.WIFI_DIRECT ->
            stringResource(R.string.shell_create_or_join_link)
        activeConnectionMode == ConnectionMode.LAN && !state.lanConnected ->
            stringResource(R.string.shell_connect_same_wifi)
        activeConnectionMode == ConnectionMode.LAN && directReadyForExchange ->
            stringResource(R.string.shell_existing_direct_link, (state.directTargetLabel ?: stringResource(R.string.shell_your_device)).toString())
        activeConnectionMode == ConnectionMode.LAN && state.resolvedTargetIp.isNullOrBlank() ->
            stringResource(R.string.shell_find_devices_or_suggested_ip)
        else ->
            stringResource(R.string.shell_device_ready_with, (resolvedTargetLabel ?: state.resolvedTargetIp).toString())
    }
    val hideDirectConnectCard =
        activeConnectionMode == ConnectionMode.WIFI_DIRECT &&
            connectionViewMode != ConnectionViewMode.ADVANCED &&
            (!state.permissionGranted || !state.p2pEnabled) &&
            !connected &&
            !directReadyForExchange &&
            !directBusy
    val requiresManualPairing = isSessionSyncManualPairingRequired(state.tokenSyncStatus)
    LaunchedEffect(requiresManualPairing) {
        if (requiresManualPairing) {
            securityExpanded = true
            sessionDetailsExpanded = true
        }
    }
    val compactSyncLabel = when {
        !state.sessionEnabled -> null
        state.sessionExpired -> stringResource(R.string.shell_session_expired)
        state.sessionSyncing -> stringResource(R.string.shell_syncing)
        isSessionSyncRetrying(state.tokenSyncStatus) -> stringResource(R.string.shell_retrying)
        isSessionSyncApprovalRequired(state.tokenSyncStatus) -> stringResource(R.string.shell_pending)
        isSessionSyncFailure(state.tokenSyncStatus) -> stringResource(R.string.shell_retry)
        else -> null
    }
    val sessionNeedsAttention = requiresManualPairing || state.sessionExpired ||
        state.pendingCredentialShare != null ||
        isSessionSyncFailure(state.tokenSyncStatus) ||
        isSessionSyncApprovalRequired(state.tokenSyncStatus)
    val showSessionActionButton = requiresManualPairing || state.sessionExpired ||
        isSessionSyncFailure(state.tokenSyncStatus) ||
        isSessionSyncApprovalRequired(state.tokenSyncStatus)
    val showMinimalSessionCard = !securityExpanded &&
        !sessionNeedsAttention &&
        connectionViewMode != ConnectionViewMode.ADVANCED
    val sessionActionLabel = when {
        requiresManualPairing -> stringResource(R.string.shell_enter_credentials)
        state.sessionExpired -> stringResource(R.string.shell_renew_session)
        isSessionSyncApprovalRequired(state.tokenSyncStatus) -> stringResource(R.string.shell_retry_sync)
        else -> stringResource(R.string.shell_sync_now)
    }
    val sessionStatusText = when {
        !state.sessionEnabled -> null
        requiresManualPairing -> stringResource(R.string.shell_manual_credentials_help)
        state.sessionExpired -> stringResource(R.string.shell_expired_renew_to_continue)
        state.sessionSyncing -> stringResource(R.string.shell_syncing_session)
        isSessionSyncRetrying(state.tokenSyncStatus) -> state.tokenSyncStatus
        isSessionSyncApprovalRequired(state.tokenSyncStatus) -> stringResource(R.string.shell_other_device_must_approve)
        isSessionSyncFailure(state.tokenSyncStatus) -> stringResource(R.string.shell_session_sync_failed)
        else -> null
    }
    val showTrustedPeersPanel =
        state.pendingCredentialShare != null ||
            state.pendingTrust != null ||
            (connectionViewMode == ConnectionViewMode.ADVANCED && state.trustedPeers.isNotEmpty())
    val headerActivityLabel = when {
        isSyncingNow -> stringResource(R.string.shell_syncing)
        isDirectBusyNow -> directActivityLabel
        state.receiving -> stringResource(R.string.shell_receiving)
        state.sending || queueRunningCount > 0 -> {
            if (state.sendBatchTotal > 0) {
                stringResource(R.string.shell_sending_batch_progress, state.sendBatchCompleted, state.sendBatchTotal)
            } else {
                stringResource(R.string.shell_sending_active)
            }
        }
        queuePendingCount > 0 -> stringResource(R.string.shell_queue_count, queuePendingCount)
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
            !isDirectBusyNow &&
            !isSyncingNow &&
            (state.receiving || state.sending || queueRunningCount > 0 || queuePendingCount > 0) -> null
        else -> headerHint
    }
    val headerSurfaceColor = MaterialTheme.colorScheme.surface
    val headerCardPadding = if (headerIsQuiet && headerMinimized) 12.dp else compactPadding
    val headerCardSpacing = if (headerIsQuiet && headerMinimized) 6.dp else compactSpacing
    val topActionMenuItems = buildList {
        add(ActionMenuItem(stringResource(R.string.shell_refresh)) { onRefreshState() })
        if (connected) {
            add(ActionMenuItem(stringResource(R.string.shell_disconnect)) { onDisconnect() })
        }
        add(
            ActionMenuItem(
                label = if (state.paused) stringResource(R.string.shell_resume_transfers) else stringResource(R.string.shell_pause_transfers)
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
        add(ActionMenuItem(stringResource(R.string.shell_open_downloads)) { onOpenDownloads() })
        add(ActionMenuItem(if (showUxPreferences) stringResource(R.string.shell_hide_ux_settings) else stringResource(R.string.shell_ux_settings)) {
            showUxPreferences = !showUxPreferences
        })
    }
    val sectionCardColors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
    val quietPanelColor = MaterialTheme.colorScheme.surfaceContainerLow
    val sessionStatusIsError = state.sessionExpired ||
        isSessionSyncFailure(state.tokenSyncStatus)
    val sessionStatusNeedsAttention = requiresManualPairing || state.pendingCredentialShare != null ||
        isSessionSyncApprovalRequired(state.tokenSyncStatus)
    val sessionStatusContainerColor = when {
        sessionStatusIsError -> MaterialTheme.colorScheme.errorContainer
        sessionStatusNeedsAttention -> MaterialTheme.colorScheme.tertiaryContainer
        state.sessionEnabled -> MaterialTheme.colorScheme.primaryContainer
        else -> quietPanelColor
    }
    val sessionStatusContentColor = when {
        sessionStatusIsError -> MaterialTheme.colorScheme.onErrorContainer
        sessionStatusNeedsAttention -> MaterialTheme.colorScheme.onTertiaryContainer
        state.sessionEnabled -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

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
                .background(MaterialTheme.colorScheme.background)
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
                    title = { Text(stringResource(R.string.shell_forget_device_question)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.shell_forget_device_body,
                                peer?.let(TrustedPeerStore::displayName) ?: stringResource(R.string.shell_this_device)
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { pendingForgetPeerId = null; onForgetPeer(peerId) }) {
                            Text(stringResource(R.string.shell_forget_device), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { TextButton(onClick = { pendingForgetPeerId = null }) { Text(stringResource(R.string.shell_cancel)) } }
                )
            }
            if (showSelectedFilesReview) {
                AlertDialog(
                    onDismissRequest = { showSelectedFilesReview = false },
                    title = { Text(stringResource(R.string.shell_prepared_files)) },
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
                    confirmButton = { TextButton(onClick = { showSelectedFilesReview = false }) { Text(stringResource(R.string.shell_done)) } }
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
                    containerColor = headerSurfaceColor,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerSurfaceColor)
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
                                stringResource(R.string.shell_between_devices_offline)
                            } else {
                                stringResource(R.string.shell_local_network_no_accounts)
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
                                Text(stringResource(R.string.shell_view))
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
                                        containerColor = sessionStatusContainerColor,
                                        contentColor = sessionStatusContentColor
                                    )
                                }
                                if (!headerActivityLabel.isNullOrBlank()) {
                                    StatusChip(
                                        label = headerActivityLabel,
                                        containerColor = if (state.paused) {
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        } else if (state.receiving || isSyncingNow || isDirectBusyNow) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else if (state.sending || queueRunningCount > 0) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        contentColor = if (state.paused) {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else if (state.receiving || isSyncingNow || isDirectBusyNow) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
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
                                            Text(stringResource(R.string.shell_more))
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
                                    Text(if (headerExpanded) stringResource(R.string.shell_less) else stringResource(R.string.shell_view_status))
                                }
                            }
                            TextButton(
                                onClick = {
                                    headerExpanded = false
                                    headerMinimized = true
                                }
                            ) {
                                Text(stringResource(R.string.shell_hide))
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
                                    containerColor = sessionStatusContainerColor,
                                    contentColor = sessionStatusContentColor
                                )
                            }
                            if (!headerActivityLabel.isNullOrBlank()) {
                                StatusChip(
                                    label = headerActivityLabel,
                                    containerColor = if (state.paused) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else if (state.receiving || isSyncingNow || isDirectBusyNow) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else if (state.sending || queueRunningCount > 0) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    contentColor = if (state.paused) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else if (state.receiving || isSyncingNow || isDirectBusyNow) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
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
                                stringResource(R.string.shell_focus_mode_stage, (focusStage.title).toString()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { headerExpanded = true }) {
                                Text(stringResource(R.string.shell_change_stage))
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
                                    stringResource(R.string.shell_network_availability),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    stringResource(R.string.shell_full_mode_network_help),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box {
                                    OutlinedButton(onClick = { connectionModesExpanded = true }) {
                                        Text(stringResource(R.string.shell_manage_availability))
                                    }
                                    DropdownMenu(
                                        expanded = connectionModesExpanded,
                                        onDismissRequest = { connectionModesExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (state.wifiDirectModeEnabled) {
                                                        stringResource(R.string.shell_hide_wifi_direct)
                                                    } else {
                                                        stringResource(R.string.shell_show_wifi_direct)
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
                                                        stringResource(R.string.shell_hide_wifi_lan)
                                                    } else {
                                                        stringResource(R.string.shell_show_wifi_lan)
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
                                stringResource(R.string.shell_focus_mode),
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
                                    stringResource(R.string.shell_simplified_stage, (focusStage.title.lowercase()).toString()),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            AnimatedVisibility(
                                visible = isDirectBusyNow || isSyncingNow,
                                enter = fadeIn(tween(150)) + slideInVertically(tween(150)),
                                exit = fadeOut(tween(180)) + slideOutVertically(tween(180))
                            ) {
                                Text(
                                    text = if (isSyncingNow) {
                                        stringResource(R.string.shell_syncing_credentials)
                                    } else {
                                        directActivityLabel.orEmpty()
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
                    color = quietPanelColor,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (state.sessionEnabled) stringResource(R.string.shell_session_active) else stringResource(R.string.shell_session_closed),
                                modifier = Modifier.align(Alignment.CenterVertically),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { onSetSessionEnabled(!state.sessionEnabled) }) {
                                Text(if (state.sessionEnabled) stringResource(R.string.shell_close_session) else stringResource(R.string.shell_activate_session))
                            }
                        }
                        Text(
                            if (state.sessionEnabled) stringResource(R.string.shell_close_stops_transfers)
                            else stringResource(R.string.shell_activate_session_help),
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
                            P2pSendSummaryKind.ATTENTION -> if (state.sendBatchFailed > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
                            P2pSendSummaryKind.ACTIVE -> if (state.paused) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
                        },
                        contentColor = when (summary.kind) {
                            P2pSendSummaryKind.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
                            P2pSendSummaryKind.ATTENTION -> if (state.sendBatchFailed > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                            P2pSendSummaryKind.ACTIVE -> if (state.paused) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
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
                                }, colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) { Text(stringResource(R.string.shell_activity)) }
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
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        quietPanelColor
                    },
                    contentColor = if (activeSupportCard.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
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
                        TextButton(onClick = { runExperienceAction(activeSupportCard.action) }, colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) {
                            Text(activeSupportCard.action.label, maxLines = 1)
                        }
                        TextButton(onClick = { dismissedSupportCardKey = currentSupportCardKey }, colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) {
                            Text(
                                stringResource(R.string.shell_close_symbol),
                                modifier = Modifier.clearAndSetSemantics {
                                    contentDescription = appString(R.string.shell_close)
                                }
                            )
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
                        Text(stringResource(R.string.shell_permission_required), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.shell_grant_named_permission, (state.permissionName).toString()))
                        Button(onClick = onRequestPermission) {
                            Text(stringResource(R.string.shell_grant_permission))
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
                        Text(stringResource(R.string.shell_send_queue), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.shell_queue_help), style = MaterialTheme.typography.bodySmall)

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                            verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                        ) {
                            StatusChip(
                                label = pluralStringResource(R.plurals.shell_pending_count, queuePendingCount, queuePendingCount),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            StatusChip(
                                label = pluralStringResource(R.plurals.shell_active_count, queueRunningCount, queueRunningCount),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (queueFailedCount > 0) {
                                StatusChip(
                                    label = pluralStringResource(R.plurals.shell_failed_count, queueFailedCount, queueFailedCount),
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
                                Text(if (state.paused) stringResource(R.string.shell_resume_all) else stringResource(R.string.shell_pause_all))
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
                                Text(stringResource(R.string.shell_hide_queue))
                            }
                        }

                        if (state.sendStatus.isNotBlank()) {
                            Text(state.sendStatus, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!state.sendFailureCause.isNullOrBlank()) {
                            Text(
                                friendlyTransferIssue(state.sendFailureCause, stringResource(R.string.shell_could_not_continue_sending)) ?: stringResource(R.string.shell_could_not_continue_sending),
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
                                        title = stringResource(R.string.shell_no_queued_tasks),
                                        body = stringResource(R.string.shell_add_files_send_tab),
                                        actionLabel = stringResource(R.string.shell_go_to_send),
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
                                        contentColor = MaterialTheme.colorScheme.onSurface,
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
                                                stringResource(R.string.shell_queue_item_progress, (queueStatusLabel(item.status)).toString(), (formatBytes(item.sentBytes)).toString(), (formatBytes(item.totalBytes)).toString()),
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
                                                    runtimeFailureText(item.lastError),
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
                                                        Text(stringResource(R.string.shell_pause))
                                                    }
                                                }
                                                if (canResume) {
                                                    OutlinedButton(onClick = { onResumeQueueItem(item.id) }) {
                                                        Text(stringResource(R.string.shell_resume))
                                                    }
                                                }
                                                if (canCancel || canPrioritize) {
                                                    Box {
                                                    OutlinedButton(
                                                        onClick = { itemActionsExpanded = true },
                                                        enabled = true
                                                    ) {
                                                        Text(stringResource(R.string.shell_more))
                                                    }
                                                    DropdownMenu(
                                                        expanded = itemActionsExpanded,
                                                        onDismissRequest = { itemActionsExpanded = false }
                                                    ) {
                                                        if (canPrioritize) {
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(R.string.shell_raise_priority)) },
                                                                onClick = {
                                                                    itemActionsExpanded = false
                                                                    onMoveQueueItemUp(item.id)
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(R.string.shell_lower_priority)) },
                                                                onClick = {
                                                                    itemActionsExpanded = false
                                                                    onMoveQueueItemDown(item.id)
                                                                }
                                                            )
                                                        }
                                                        if (canCancel) {
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(R.string.shell_cancel_task)) },
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
                            containerColor = quietPanelColor,
                            contentColor = MaterialTheme.colorScheme.onSurface
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
                            Text(stringResource(R.string.shell_new_connection_detected), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.shell_save_favorite_question, (peerLabel).toString()))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onSaveSuggestedFavorite(peerId) }) {
                                    Text(stringResource(R.string.shell_save_favorite))
                                }
                                OutlinedButton(onClick = { onSkipSuggestedFavorite(peerId) }) {
                                    Text(stringResource(R.string.shell_not_now))
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
                                if (simpleWifiDirectMode) stringResource(R.string.shell_wifi_direct) else stringResource(R.string.shell_connect),
                                fontWeight = FontWeight.Bold
                            )
                            if (simpleWifiDirectMode) {
                                Text(
                                    stringResource(R.string.shell_share_nearby_offline),
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
                                        ConnectionViewMode.WIFI_DIRECT -> stringResource(R.string.shell_one_to_one_link)
                                        ConnectionViewMode.LAN -> stringResource(R.string.shell_same_wifi_find_devices)
                                        ConnectionViewMode.ADVANCED -> stringResource(R.string.shell_full_view_routes_fallback)
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
                                                directReadyForExchange -> stringResource(R.string.shell_direct_target, (state.directTargetLabel ?: stringResource(R.string.shell_linked)).toString())
                                                state.p2pEnabled -> stringResource(R.string.shell_direct_available)
                                                else -> stringResource(R.string.shell_direct_off)
                                            }
                                        } else {
                                            stringResource(R.string.shell_direct_disabled)
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
                                                    stringResource(R.string.shell_lan_target, (resolvedTargetLabel ?: state.resolvedTargetIp).toString())
                                                }
                                                state.lanConnected -> stringResource(R.string.shell_lan_local, (state.lanLocalIp ?: stringResource(R.string.shell_ready_lowercase)).toString())
                                                else -> stringResource(R.string.shell_lan_no_network)
                                            }
                                        } else {
                                            stringResource(R.string.shell_lan_disabled)
                                        },
                                        containerColor = if (state.lanModeEnabled && state.lanConnected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        contentColor = if (state.lanModeEnabled && state.lanConnected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    if (!isCompactScreen) {
                                        StatusChip(
                                            label = connectionLabel,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (!simpleWifiDirectMode && showLanSecondaryHelp) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = quietPanelColor,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
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
                                    color = quietPanelColor,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = UiSpaceM, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            when (state.resolvedTargetMode) {
                                                ConnectionMode.WIFI_DIRECT -> stringResource(R.string.shell_preferred_route_direct)
                                                ConnectionMode.LAN -> stringResource(R.string.shell_route_ready_same_wifi)
                                                else -> stringResource(R.string.shell_route_ready)
                                            },
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            when (state.resolvedTargetMode) {
                                                ConnectionMode.WIFI_DIRECT ->
                                                    state.resolvedTargetLabel?.let { stringResource(R.string.shell_with_device, (it).toString()) } ?: stringResource(R.string.shell_ready_send_chat)
                                                ConnectionMode.LAN ->
                                                    state.resolvedTargetLabel?.let { stringResource(R.string.shell_with_device_same_wifi, (it).toString()) } ?: stringResource(R.string.shell_ready_send_chat)
                                                else ->
                                                    stringResource(R.string.shell_ready_continue)
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
                                            Text(stringResource(R.string.shell_close_link))
                                        }
                                    } else if (connected) {
                                        OutlinedButton(onClick = onCancelConnect) {
                                            Text(if (isHost) stringResource(R.string.shell_cancel_link) else stringResource(R.string.shell_leave_link))
                                        }
                                    } else if (!state.p2pEnabled) {
                                        OutlinedButton(onClick = onOpenWifiSettings) {
                                            Text(stringResource(R.string.shell_open_wifi))
                                        }
                                    } else {
                                        Button(
                                            onClick = onStartHost,
                                            enabled = state.wifiDirectModeEnabled && state.permissionGranted && !directBusy
                                        ) {
                                            Text(stringResource(R.string.shell_create_link))
                                        }
                                        OutlinedButton(
                                            onClick = onStartClient,
                                            enabled = state.wifiDirectModeEnabled && state.permissionGranted && !directBusy
                                        ) {
                                            Text(if (isCompactScreen) stringResource(R.string.shell_search) else stringResource(R.string.shell_find_link))
                                        }
                                    }
                                    if (directBusy && !connected && !directReadyForExchange) {
                                        OutlinedButton(onClick = onCancelConnect) {
                                            Text(
                                                if (state.directCreatingGroup) stringResource(R.string.shell_cancel_link)
                                                else if (isCompactScreen) stringResource(R.string.shell_cancel)
                                                else stringResource(R.string.shell_cancel_search)
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
                                                stringResource(R.string.shell_cancel_search)
                                            } else {
                                                stringResource(R.string.shell_find_devices)
                                            }
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onUseSuggestedTarget,
                                        enabled = state.lanModeEnabled && !state.suggestedTargetIp.isNullOrBlank()
                                    ) {
                                        Text(if (isCompactScreen) stringResource(R.string.shell_use_ip) else stringResource(R.string.shell_use_suggested_ip))
                                    }
                                    if (connectionViewMode == ConnectionViewMode.ADVANCED && !state.lanConnected) {
                                        OutlinedButton(onClick = onOpenWifiSettings) {
                                            Text(if (isCompactScreen) stringResource(R.string.shell_settings) else stringResource(R.string.shell_wifi_settings))
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
                        color = quietPanelColor,
                    contentColor = MaterialTheme.colorScheme.onSurface
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
                                    if (state.sessionEnabled) stringResource(R.string.shell_session_active) else stringResource(R.string.shell_session_closed),
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
                                    containerColor = sessionStatusContainerColor,
                                    contentColor = sessionStatusContentColor
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
                            title = stringResource(R.string.shell_session),
                            summary = when {
                                !state.sessionEnabled -> stringResource(R.string.shell_closed_prepare_credentials)
                                state.sessionExpired -> stringResource(R.string.shell_session_expired_renew)
                                compactSyncLabel != null -> compactSyncLabel
                                else -> stringResource(R.string.shell_active_and_ready)
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
                                        containerColor = sessionStatusContainerColor,
                                        contentColor = sessionStatusContentColor
                                    )
                                    if (compactSyncLabel != null) {
                                        StatusChip(
                                            label = compactSyncLabel,
                                            containerColor = sessionStatusContainerColor,
                                            contentColor = sessionStatusContentColor
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
                                        containerColor = sessionStatusContainerColor,
                                        contentColor = sessionStatusContentColor
                                    )
                                    if (compactSyncLabel != null) {
                                        StatusChip(
                                            label = compactSyncLabel,
                                            containerColor = sessionStatusContainerColor,
                                            contentColor = sessionStatusContentColor
                                        )
                                    }
                                }

                                sessionStatusText?.let { statusText ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = sessionStatusContainerColor,
                                        contentColor = sessionStatusContentColor,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            statusText,
                                            color = sessionStatusContentColor,
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
                                        Text(if (sessionDetailsExpanded) stringResource(R.string.shell_hide_credentials) else stringResource(R.string.shell_view_credentials))
                                    }
                                    Box {
                                        OutlinedButton(onClick = { securityMoreExpanded = true }) {
                                            Text(stringResource(R.string.shell_more))
                                        }
                                        DropdownMenu(
                                            expanded = securityMoreExpanded,
                                            onDismissRequest = { securityMoreExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.shell_renew_thirty_minutes)) },
                                                onClick = {
                                                    securityMoreExpanded = false
                                                    onRenewSession()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.shell_copy_token)) },
                                                onClick = {
                                                    securityMoreExpanded = false
                                                    onCopyToken()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.shell_paste_token)) },
                                                onClick = {
                                                    securityMoreExpanded = false
                                                    onPasteToken()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.shell_new_token)) },
                                                onClick = {
                                                    securityMoreExpanded = false
                                                    onGenerateToken()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.shell_new_pin)) },
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
                                        stringResource(R.string.shell_manual_session_help),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = state.authToken,
                                        onValueChange = onTokenChange,
                                        singleLine = true,
                                        label = { Text(stringResource(R.string.shell_session_code)) },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    P2pSessionPinField(state = state, onValueChange = onPinChange, modifier = Modifier.fillMaxWidth())
                                    Button(
                                        onClick = onConfirmManualSession,
                                        enabled = !state.sessionExpired &&
                                            !state.sessionSyncing &&
                                            FileTransfer.isValidToken(state.authToken) && TransferSecurity.isValidPin(state.sessionPin) &&
                                            (if (state.activeConnectionMode == ConnectionMode.WIFI_DIRECT) !state.directTargetIp.isNullOrBlank() else !state.resolvedTargetIp.isNullOrBlank())
                                    ) { Text(stringResource(R.string.shell_use_this_session)) }

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
                            Text(stringResource(R.string.shell_trust), fontWeight = FontWeight.Bold)

                            if (state.pendingCredentialShare != null) {
                                val req = state.pendingCredentialShare
                                Text(stringResource(R.string.shell_session_request, (req.label).toString()))
                                Text(req.ip, style = MaterialTheme.typography.bodySmall)
                                credentialRequestFingerprint(req)?.let { fingerprint ->
                                    Text(stringResource(R.string.shell_fingerprint, (fingerprint).toString()), style = MaterialTheme.typography.labelMedium)
                                }
                                Text(
                                    stringResource(R.string.shell_recognize_device_before_sharing),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(onClick = { onApproveCredentialShare(req) }, enabled = credentialRequestFingerprint(req) != null) {
                                        Text(stringResource(R.string.shell_approve))
                                    }
                                    OutlinedButton(onClick = { onRejectCredentialShare(req) }) {
                                        Text(stringResource(R.string.shell_reject))
                                    }
                                }
                                HorizontalDivider()
                            }

                            if (state.pendingTrust != null) {
                                val req = state.pendingTrust
                                Text(stringResource(R.string.shell_trust_pending_device, (req.label).toString()))
                                Text(req.ip, style = MaterialTheme.typography.bodySmall)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(onClick = { onTrustPeer(req) }) {
                                        Text(stringResource(R.string.shell_trust_device))
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
                                        pluralStringResource(R.plurals.shell_remembered_devices, state.trustedPeers.size, state.trustedPeers.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(onClick = { trustExpanded = !trustExpanded }) {
                                        Text(if (trustExpanded) stringResource(R.string.shell_hide) else stringResource(R.string.shell_view))
                                    }
                                }
                            }

                            if (trustExpanded && state.trustedPeers.isNotEmpty()) {
                                state.trustedPeers.take(8).forEachIndexed { index, peer ->
                                    val displayName = TrustedPeerStore.displayName(peer)
                                    Text(displayName, fontWeight = FontWeight.SemiBold)
                                    if (!peer.lastKnownIp.isNullOrBlank()) {
                                        Text(stringResource(R.string.shell_known_ip, (peer.lastKnownIp).toString()), style = MaterialTheme.typography.bodySmall)
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onSetPeerFavorite(peer.id, !peer.favorite) }
                                        ) {
                                            Text(if (peer.favorite) stringResource(R.string.shell_remove_favorite) else stringResource(R.string.shell_favorite))
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                aliasEditorPeerId = peer.id
                                                aliasEditorValue = peer.alias
                                            }
                                        ) {
                                            Text(stringResource(R.string.shell_edit_nickname))
                                        }
                                        TextButton(onClick = { pendingForgetPeerId = peer.id }) {
                                            Text(stringResource(R.string.shell_forget_device), color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    Text(
                                        stringResource(R.string.shell_nickname_value, (peer.alias.ifBlank { stringResource(R.string.shell_undefined) }).toString()),
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
                                    state.peers.isNotEmpty() -> stringResource(R.string.shell_devices_found)
                                    state.directDiscovering -> stringResource(R.string.shell_finding_devices)
                                    else -> stringResource(R.string.shell_nearby_devices)
                                },
                                fontWeight = FontWeight.Bold
                            )

                            if (state.peers.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.shell_choose_creator_connect),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (state.peers.isEmpty()) {
                                EmptyStateBlock(
                                    title = if (state.directDiscovering) stringResource(R.string.shell_finding_devices) else stringResource(R.string.shell_no_devices_yet),
                                    body = if (state.directDiscovering) {
                                        stringResource(R.string.shell_keep_other_link_open)
                                    } else {
                                        stringResource(R.string.shell_enable_direct_both_search)
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
                                        contentColor = MaterialTheme.colorScheme.onSurface,
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
                                                        containerColor = when (peer.status) {
                                                            WifiP2pDevice.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                                                            WifiP2pDevice.INVITED -> MaterialTheme.colorScheme.tertiaryContainer
                                                            WifiP2pDevice.FAILED -> MaterialTheme.colorScheme.errorContainer
                                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                                        },
                                                        contentColor = when (peer.status) {
                                                            WifiP2pDevice.CONNECTED -> MaterialTheme.colorScheme.onPrimaryContainer
                                                            WifiP2pDevice.INVITED -> MaterialTheme.colorScheme.onTertiaryContainer
                                                            WifiP2pDevice.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                        }
                                                    )
                                                    StatusChip(
                                                        label = if (trusted) stringResource(R.string.shell_trusted) else stringResource(R.string.shell_not_trusted),
                                                        containerColor = if (trusted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                        contentColor = if (trusted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (favorite) {
                                                        StatusChip(
                                                            label = stringResource(R.string.shell_favorite),
                                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }

                                                if (!peerIp.isNullOrBlank()) {
                                                    Text(stringResource(R.string.shell_known_peer_ip, (peerIp).toString()), style = MaterialTheme.typography.bodySmall)
                                                }
                                                Text(
                                                    stringResource(R.string.shell_last_seen, (formatRelativeSeen(lastSeenAtMs, state.nowMs)).toString()),
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
                                                Text(stringResource(R.string.shell_connect))
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
                            Text(stringResource(R.string.shell_devices_on_wifi), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.shell_devices_detected_ip),
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (lanPeers.isEmpty()) {
                                EmptyStateBlock(
                                    title = stringResource(R.string.shell_no_devices_on_wifi_yet),
                                    body = stringResource(R.string.shell_search_network_or_ip),
                                    actionLabel = when {
                                        state.lanScanning -> stringResource(R.string.shell_cancel_search)
                                        state.suggestedTargetIp.isNullOrBlank() -> stringResource(R.string.shell_find_devices)
                                        else -> stringResource(R.string.shell_use_suggested_ip)
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
                                        contentColor = MaterialTheme.colorScheme.onSurface,
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
                                                    label = if (peer.trusted) stringResource(R.string.shell_trusted) else stringResource(R.string.shell_awaiting_approval),
                                                    containerColor = if (peer.trusted) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.tertiaryContainer
                                                    },
                                                    contentColor = if (peer.trusted) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onTertiaryContainer
                                                    }
                                                )
                                                StatusChip(
                                                    label = stringResource(R.string.shell_seen_relative, (formatRelativeSeen(peer.lastSeenAtMs, state.nowMs)).toString()),
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
                                                    Text(stringResource(R.string.shell_sync_session))
                                                }
                                                OutlinedButton(
                                                    onClick = { onTargetIpChange(peer.ip) }
                                                ) {
                                                    Text(stringResource(R.string.shell_use_as_destination))
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
                        Text(stringResource(R.string.shell_share_files), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            stringResource(R.string.shell_files_help),
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
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    Icon(Icons.Rounded.Description, contentDescription = null,
                                        modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                    Button(onClick = pickFilesForContext,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                        Icon(imageVector = Icons.Rounded.AttachFile, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.shell_choose_files))
                                    }
                                }
                            }
                        }

                        val resolved = state.resolvedTargetIp
                        val resolvedLabel = state.resolvedTargetLabel ?: resolved
                        val hasResolvedRoute = !resolved.isNullOrBlank()
                        val routeTitle = when {
                            hasResolvedRoute && !state.sessionReady -> stringResource(R.string.shell_session_needs_confirmation)
                            simpleWifiDirectMode && hasResolvedRoute -> {
                                stringResource(R.string.shell_direct_ready)
                            }
                            simpleWifiDirectMode && !state.permissionGranted -> {
                                stringResource(R.string.shell_permission_missing)
                            }
                            simpleWifiDirectMode && !state.p2pEnabled -> {
                                stringResource(R.string.shell_open_system_wifi)
                            }
                            simpleWifiDirectMode && state.directCreatingGroup -> {
                                stringResource(R.string.shell_creating_link)
                            }
                            simpleWifiDirectMode && state.directConnecting -> {
                                stringResource(R.string.shell_joining_link)
                            }
                            simpleWifiDirectMode && state.directDiscovering && state.peers.isNotEmpty() -> {
                                stringResource(R.string.shell_choose_device)
                            }
                            simpleWifiDirectMode && state.directDiscovering -> {
                                stringResource(R.string.shell_finding_devices)
                            }
                            simpleWifiDirectMode -> {
                                stringResource(R.string.shell_device_missing)
                            }
                            state.resolvedTargetMode == ConnectionMode.WIFI_DIRECT && !resolved.isNullOrBlank() -> {
                                stringResource(R.string.shell_preferred_route_direct)
                            }
                            state.resolvedTargetMode == ConnectionMode.LAN && !resolved.isNullOrBlank() -> {
                                stringResource(R.string.shell_route_ready_same_wifi)
                            }
                            else -> stringResource(R.string.shell_choose_device_when_ready)
                        }
                        val routeBody = when {
                            hasResolvedRoute && !state.sessionReady -> stringResource(R.string.shell_confirm_selected_device_session)
                            simpleWifiDirectMode && hasResolvedRoute -> {
                                resolvedLabel?.let { stringResource(R.string.shell_send_directly_to, (it).toString()) } ?: stringResource(R.string.shell_device_ready_to_receive)
                            }
                            simpleWifiDirectMode && !state.permissionGranted -> {
                                stringResource(R.string.shell_grant_return_connect)
                            }
                            simpleWifiDirectMode && !state.p2pEnabled -> {
                                stringResource(R.string.shell_cannot_activate_direct)
                            }
                            simpleWifiDirectMode && state.directCreatingGroup -> {
                                stringResource(R.string.shell_other_find_finish_connection)
                            }
                            simpleWifiDirectMode && state.directConnecting -> {
                                stringResource(R.string.shell_wait_devices_link)
                            }
                            simpleWifiDirectMode && state.directDiscovering && state.peers.isNotEmpty() -> {
                                stringResource(R.string.shell_finish_selection_connect)
                            }
                            simpleWifiDirectMode && state.directDiscovering -> {
                                stringResource(R.string.shell_keep_other_link_to_appear)
                            }
                            simpleWifiDirectMode -> {
                                stringResource(R.string.shell_prepare_files_connect_later)
                            }
                            state.resolvedTargetMode == ConnectionMode.WIFI_DIRECT && !resolved.isNullOrBlank() -> {
                                resolvedLabel?.let { stringResource(R.string.shell_direct_ready_named, (it).toString()) } ?: stringResource(R.string.shell_direct_ready_send)
                            }
                            state.resolvedTargetMode == ConnectionMode.LAN && !resolved.isNullOrBlank() -> {
                                resolvedLabel?.let { stringResource(R.string.shell_ready_device_named, (it).toString()) } ?: stringResource(R.string.shell_device_ready_wifi)
                            }
                            !state.suggestedTargetIp.isNullOrBlank() -> {
                                stringResource(R.string.shell_suggested_ip_or_destination)
                            }
                            else -> stringResource(R.string.shell_prepare_files_connect_later)
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
                            state.selectedFilesCount <= 0 -> stringResource(R.string.shell_no_files_ready_yet)
                            else -> pluralStringResource(R.plurals.shell_files_ready, state.selectedFilesCount, state.selectedFilesCount)
                        }
                        val sendDisabledReason = when {
                            state.selectedFilesCount <= 0 -> null
                            state.sessionExpired -> stringResource(R.string.shell_expired_renew_before_send)
                            !hasResolvedRoute -> stringResource(R.string.shell_connect_to_send_selection_kept)
                            !state.sessionReady -> stringResource(R.string.shell_confirm_receiver_session)
                            else -> null
                        }
                        val hasSendActivity = state.shareImportStatus.isNotBlank() ||
                            state.sendBatchTotal > 0 ||
                            state.sending ||
                            state.sendQueue.isNotEmpty() ||
                            !state.sendFailureCause.isNullOrBlank() ||
                            state.sendStatus.isNotBlank()
                        val sendActivitySummary = buildList {
                            if (state.sending) add(stringResource(R.string.shell_in_progress))
                            if (queuePendingCount > 0) add(pluralStringResource(R.plurals.shell_pending_summary, queuePendingCount, queuePendingCount))
                            if (queueRunningCount > 0) add(pluralStringResource(R.plurals.shell_active_summary, queueRunningCount, queueRunningCount))
                            if (queueFailedCount > 0) add(pluralStringResource(R.plurals.shell_retry_summary, queueFailedCount, queueFailedCount))
                        }.joinToString(" · ")

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = quietPanelColor,
                            contentColor = MaterialTheme.colorScheme.onSurface,
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
                                            ConnectionMode.LAN -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        contentColor = when (state.resolvedTargetMode) {
                                            ConnectionMode.WIFI_DIRECT -> MaterialTheme.colorScheme.onPrimaryContainer
                                            ConnectionMode.LAN -> MaterialTheme.colorScheme.onPrimaryContainer
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
                            ) { Text(if (hasResolvedRoute) stringResource(R.string.shell_change_device) else stringResource(R.string.shell_connect_device)) }
                            if (connectionViewMode == ConnectionViewMode.ADVANCED) {
                                TextButton(onClick = { showAdvancedTargetOptions = !showAdvancedTargetOptions }) {
                                    Text(if (showDestinationChooser) stringResource(R.string.shell_hide_addresses) else stringResource(R.string.shell_choose_by_ip))
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
                                Text(stringResource(R.string.shell_favorites), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    favoriteDestinations.forEach { (label, ip) ->
                                        OutlinedButton(onClick = { onTargetIpChange(ip) }) {
                                            Text(stringResource(R.string.shell_device_with_ip, (label).toString(), (ip).toString()))
                                        }
                                    }
                                }
                            }

                            val quickPeers = state.knownServicePeers
                                .filter { it.ip.isNotBlank() }
                                .distinctBy { it.ip }
                                .take(5)
                            if (quickPeers.isNotEmpty()) {
                                Text(stringResource(R.string.shell_detected_devices), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    quickPeers.forEach { kp ->
                                        OutlinedButton(onClick = { onTargetIpChange(kp.ip) }) {
                                            Text(stringResource(R.string.shell_known_device_with_ip, (kp.label).toString(), (kp.ip).toString()))
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = state.targetIp,
                                onValueChange = onTargetIpChange,
                                singleLine = true,
                                label = { Text(stringResource(R.string.shell_destination_ip)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (!state.lastPeerLabel.isNullOrBlank() && showDestinationChooser) {
                            Text(stringResource(R.string.shell_last_device, (state.lastPeerLabel).toString()))
                        }

                        if (state.lastSendTargetIp != null && showDestinationChooser) {
                            Text(
                                stringResource(R.string.shell_last_destination, (state.lastSendTargetLabel ?: state.lastSendTargetIp).toString(), (state.lastSendTargetIp).toString()),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (state.selectedFilesCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = quietPanelColor,
                                contentColor = MaterialTheme.colorScheme.onSurface,
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
                                            Text(pluralStringResource(R.plurals.shell_review_files, state.selectedFilesCount, state.selectedFilesCount))
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
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = null
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(pluralStringResource(R.plurals.shell_send_files, state.selectedFilesCount, state.selectedFilesCount))
                                }
                                OutlinedButton(onClick = pickFilesForContext) {
                                    Icon(
                                        imageVector = Icons.Rounded.AttachFile,
                                        contentDescription = null
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (isCompactScreen) stringResource(R.string.shell_add) else stringResource(R.string.shell_add_files))
                                }
                            }
                            if (showSendMore) {
                                Box {
                                    OutlinedButton(onClick = { sendMoreExpanded = true }) {
                                        Text(stringResource(R.string.shell_more))
                                    }
                                    DropdownMenu(
                                        expanded = sendMoreExpanded,
                                        onDismissRequest = { sendMoreExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.shell_clear_selection)) },
                                            enabled = state.selectedFilesCount > 0,
                                            onClick = {
                                                sendMoreExpanded = false
                                                clearFilesForContext()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.shell_send_last_destination)) },
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
                                contentColor = MaterialTheme.colorScheme.onSurface,
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
                                                stringResource(R.string.shell_send_activity),
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
                                            Text(if (showSendActivityDetails) stringResource(R.string.shell_hide) else stringResource(R.string.shell_view))
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
                                                    stringResource(
                                                        R.string.shell_batch_summary,
                                                        state.sendBatchCompleted,
                                                        state.sendBatchTotal,
                                                        pluralStringResource(R.plurals.shell_failed_summary, state.sendBatchFailed, state.sendBatchFailed),
                                                        pluralStringResource(R.plurals.shell_canceled_summary, state.sendBatchCanceled, state.sendBatchCanceled)
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            if (state.sending) {
                                                LinearProgressIndicator(
                                                    progress = { state.sendProgress.coerceIn(0f, 1f) },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                val pct = (state.sendProgress * 100).roundToInt().coerceIn(0, 100)
                                                Text(stringResource(R.string.shell_send_progress, pct), style = MaterialTheme.typography.bodySmall)
                                            }

                                            if (state.sending || state.sendQueue.isNotEmpty() || state.sendBatchTotal > 0) {
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                                                    verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                                                ) {
                                                    StatusChip(
                                                        label = stringResource(R.string.shell_instant_rate, (formatRate(state.sendInstantBps)).toString()),
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    StatusChip(
                                                        label = stringResource(R.string.shell_average_rate, (formatRate(state.sendAverageBps)).toString()),
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    StatusChip(
                                                        label = stringResource(R.string.shell_eta, (formatEta(state.sendEtaSeconds)).toString()),
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            if (!state.sendFailureCause.isNullOrBlank()) {
                                                Text(
                                                    friendlyTransferIssue(state.sendFailureCause, stringResource(R.string.shell_could_not_continue_sending))
                                                        ?: stringResource(R.string.shell_could_not_continue_sending),
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
                                                Text(stringResource(R.string.shell_queue), fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    stringResource(R.string.shell_queue_totals, queuePendingCount, queueRunningCount, queueFailedCount),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                if (!focusEnabled) {
                                                    OutlinedButton(
                                                        onClick = { showQueueDetails = !showQueueDetails }
                                                    ) {
                                                        Text(if (showQueueDetails) stringResource(R.string.shell_hide_queue) else stringResource(R.string.shell_view_queue))
                                                    }
                                                } else {
                                                    Text(
                                                        stringResource(R.string.shell_disable_focus_open_queue),
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
                    stringResource(R.string.shell_focus_stage, (focusStage.title).toString())
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
                    title = { Text(stringResource(R.string.shell_edit_nickname)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(UiSpaceS)) {
                            Text(
                                stringResource(R.string.shell_device_label, (editingPeer?.label ?: editingPeerId.take(8)).toString()),
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedTextField(
                                value = aliasEditorValue,
                                onValueChange = { aliasEditorValue = it.take(48) },
                                singleLine = true,
                                label = { Text(stringResource(R.string.shell_nickname)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                stringResource(R.string.shell_empty_removes_nickname),
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
                            Text(stringResource(R.string.shell_save))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                aliasEditorPeerId = null
                                aliasEditorValue = ""
                            }
                        ) {
                            Text(stringResource(R.string.shell_cancel))
                        }
                    }
                )
            }
    }
}
}
}
}
