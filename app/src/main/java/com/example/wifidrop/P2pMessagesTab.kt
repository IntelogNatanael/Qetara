package com.example.wifidrop
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.ChatBubbleOutline
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
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
    val closeSearch = {
        focusManager.clearFocus()
        messageQuery = ""
        searchVisible = false
    }
    BackHandler(enabled = searchVisible, onBack = closeSearch)
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
        isGlobalChat -> appString(R.string.msg_channel_wifi)
        else -> appString(R.string.msg_chat)
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
        directTargetCount > 1 -> appQuantityString(R.plurals.msg_devices_count, directTargetCount, directTargetCount)
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
    val sendButtonLabel = appString(R.string.msg_send)
    val directNotReadyTitle = when {
        isDirectWifiMode && !state.permissionGranted ->
            appString(R.string.msg_permission_missing)
        isDirectWifiMode && !state.p2pEnabled ->
            appString(R.string.msg_open_system_wifi)
        isDirectWifiMode && state.directCreatingGroup ->
            appString(R.string.msg_link_created_here)
        isDirectWifiMode && state.directConnecting ->
            appString(R.string.msg_joining_link)
        isDirectWifiMode && p2pLinked && directWifiPeers.isNotEmpty() ->
            appString(R.string.msg_choose_devices)
        isDirectWifiMode && p2pLinked ->
            appString(R.string.msg_waiting_participants)
        isDirectWifiMode && state.directDiscovering && state.peers.isNotEmpty() ->
            appString(R.string.msg_choose_device)
        isDirectWifiMode && state.directDiscovering ->
            appString(R.string.msg_searching_devices)
        isDirectWifiMode ->
            appString(R.string.msg_direct_not_ready)
        else ->
            appString(R.string.msg_choose_device)
    }
    val directNotReadyBody = when {
        isDirectWifiMode && !state.permissionGranted ->
            appString(R.string.msg_permission_connect)
        isDirectWifiMode && !state.p2pEnabled ->
            appString(R.string.msg_system_wifi_help)
        isDirectWifiMode && state.directCreatingGroup ->
            appString(R.string.msg_other_device_find)
        isDirectWifiMode && state.directConnecting ->
            appString(R.string.msg_wait_linking)
        isDirectWifiMode && p2pLinked && directWifiPeers.isNotEmpty() ->
            appString(R.string.msg_choose_group_recipient)
        isDirectWifiMode && p2pLinked ->
            appString(R.string.msg_session_devices_appear)
        isDirectWifiMode && state.directDiscovering && state.peers.isNotEmpty() ->
            appString(R.string.msg_open_connect_choose)
        isDirectWifiMode && state.directDiscovering ->
            appString(R.string.msg_keep_other_link_open)
        isDirectWifiMode ->
            appString(R.string.msg_create_link_first)
        else ->
            appString(R.string.msg_choose_chat_device_first)
    }
    val showDirectSetupCard = !isGlobalChat && !directChannelReady
    val composerPlaceholder = when {
        state.selectedFilesCount > 0 -> appString(R.string.msg_optional_message)
        isGlobalChat -> appString(R.string.msg_channel_message)
        showDirectSetupCard -> appString(R.string.msg_direct_ready_placeholder)
        directTargetCount > 1 -> appQuantityString(R.plurals.msg_message_devices_count, directTargetCount, directTargetCount)
        !directSelectionSummary.isNullOrBlank() -> appString(R.string.msg_message_destination, directSelectionSummary ?: appString(R.string.msg_this_device))
        else -> appString(R.string.msg_write_message)
    }

    val sendDisabledReason = when {
        !state.sessionEnabled -> appString(R.string.msg_enable_session_to_send)
        isGlobalChat && !state.lanConnected -> appString(R.string.msg_channel_needs_wifi)
        isGlobalChat && !globalLanJoined -> appString(R.string.msg_join_to_write)
        isGlobalChat && state.selectedFilesCount > 0 && state.globalChatPeerCount <= 0 ->
            appString(R.string.msg_no_channel_recipients)
        isGlobalChat && state.chatDraft.isBlank() && state.selectedFilesCount <= 0 -> appString(R.string.msg_write_or_attach)
        !hasComposerPayload -> appString(R.string.msg_write_or_attach)
        state.sessionExpired -> appString(R.string.msg_expired_renew)
        !isGlobalChat && directChannelReady && !state.chatSessionReady -> appString(R.string.msg_confirm_receiver_session)
        !isGlobalChat && directChatMode == ConnectionMode.WIFI_DIRECT && state.chatDirectTargetIps.isEmpty() ->
            appString(R.string.msg_choose_group_at_least_one)
        !isGlobalChat && directChatMode == ConnectionMode.WIFI_DIRECT && state.selectedFilesCount > 0 && state.chatDirectTargetIps.size > 1 ->
            appString(R.string.msg_direct_attachments_single)
        !isGlobalChat && directChatMode == ConnectionMode.WIFI_DIRECT &&
            state.selectedFilesCount > 0 &&
            !isP2pHost &&
            state.chatDirectTargetIps.singleOrNull() != state.connection?.groupOwnerAddress ->
            appString(R.string.msg_client_files_host_only)
        !isGlobalChat && !directChannelReady -> when (directChatMode) {
            ConnectionMode.WIFI_DIRECT -> appString(R.string.msg_prepare_direct_first)
            ConnectionMode.LAN -> appString(R.string.msg_choose_lan_direct)
        }
        else -> null
    }
    val composerNotice = when {
        hasComposerPayload && sendDisabledReason != null -> sendDisabledReason
        state.sessionExpired -> appString(R.string.msg_renew_to_send)
        isGlobalChat && state.selectedFilesCount > 0 && state.globalChatPeerCount <= 0 ->
            appString(R.string.msg_wait_channel_peer_files)
        isGlobalChat && state.selectedFilesCount > 0 ->
            appQuantityString(R.plurals.msg_channel_download_recipients, state.globalChatPeerCount, state.globalChatPeerCount)
        else -> null
    }
    val chatStarterTitle = when {
        isGlobalChat -> channelTitle
        isDirectWifiMode -> appString(R.string.msg_direct_chat)
        else -> appString(R.string.msg_lan_direct)
    }
    val chatStarterBody = when {
        isGlobalChat && !state.lanConnected ->
            appString(R.string.msg_connect_device_wifi)
        isGlobalChat && !globalLanJoined ->
            appString(R.string.msg_tap_join)
        isGlobalChat && state.globalChatPeerCount <= 0 ->
            appString(R.string.msg_alone_channel)
        isGlobalChat ->
            globalDeviceCountLabel
        directTargetCount > 1 ->
            appQuantityString(R.plurals.msg_devices_ready_hint, directTargetCount, directTargetCount)
        directChannelReady ->
            appString(R.string.msg_device_ready_hint, directTargetLabel ?: appString(R.string.msg_device))
        else ->
            appString(R.string.msg_no_device_ready)
    }
    val chatHeaderSummary = when {
        isGlobalChat && !state.lanConnected -> appString(R.string.msg_no_local_network)
        isGlobalChat && !globalLanJoined -> appString(R.string.msg_not_joined)
        isGlobalChat && state.globalChatPeerCount <= 0 -> appString(R.string.msg_alone_channel)
        isGlobalChat -> globalDeviceCountLabel
        directTargetCount > 1 -> appQuantityString(R.plurals.msg_direct_group_summary, directTargetCount, directTargetCount)
        directChannelReady -> buildString {
            append(appString(R.string.msg_one_to_one_prefix))
            append(if (directChatMode == ConnectionMode.WIFI_DIRECT) "Wi-Fi Direct" else "Wi-Fi LAN")
            directTargetLabel?.let {
                append(" · ")
                append(it)
            }
        }
        else -> appString(R.string.msg_one_to_one_mode, if (directChatMode == ConnectionMode.WIFI_DIRECT) "Wi-Fi Direct" else "Wi-Fi LAN")
    }
    val chatHeaderTitle = when {
        isGlobalChat -> channelTitle
        isDirectWifiMode -> appString(R.string.msg_direct_chat)
        else -> appString(R.string.msg_lan_direct)
    }
    val channelHelperText = when {
        isGlobalChat && !state.lanConnected -> appString(R.string.msg_connect_wifi_to_view)
        isGlobalChat && !globalLanJoined -> appString(R.string.msg_join_network_channel)
        !isGlobalChat && !directChannelReady -> when (directChatMode) {
            ConnectionMode.WIFI_DIRECT -> when {
                !state.permissionGranted -> appString(R.string.msg_grant_direct_permission)
                !state.p2pEnabled -> appString(R.string.msg_open_wifi_to_continue)
                p2pLinked -> appString(R.string.msg_choose_group_devices)
                else -> appString(R.string.msg_create_find_direct)
            }
            ConnectionMode.LAN -> appString(R.string.msg_choose_one_to_one_lan)
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
            com.example.wifidrop.presentation.isMessageStatusFailure(state.messageStatus)
        )
    val tokenSyncNeedsManualAction =
        com.example.wifidrop.presentation.isSessionSyncFailure(state.tokenSyncStatus)
    val showSyncButton = !state.chatSessionReady && experience.showSyncAction && (!isGlobalChat || tokenSyncNeedsManualAction)
    val queueHeadline = when {
        queueRunningCount > 0 -> appString(R.string.msg_queue_active)
        queueFailedCount > 0 -> appString(R.string.msg_needs_attention)
        queuePendingCount > 0 -> appString(R.string.msg_queue_ready)
        else -> null
    }
    val queueSummary = buildList {
        if (queueRunningCount > 0) add(appQuantityString(R.plurals.msg_queue_running_count, queueRunningCount, queueRunningCount))
        if (queuePendingCount > 0) add(appQuantityString(R.plurals.msg_queue_pending_count, queuePendingCount, queuePendingCount))
        if (queueFailedCount > 0) add(appQuantityString(R.plurals.msg_queue_retry_count, queueFailedCount, queueFailedCount))
    }.joinToString(" · ")
    val featuredQueueItem = state.sendQueue.firstOrNull()
    val sectionCardColors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.background
    )
    val quietPanelColor = MaterialTheme.colorScheme.surfaceContainerLow

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
    LaunchedEffect(searchVisible) {
        if (searchVisible) searchFocusRequester.requestFocus()
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize(),
        colors = sectionCardColors,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        val sectionLabel = when {
            showChannelJoinPrompt -> null
            showDirectSetupCard && hasMessages -> appString(R.string.msg_recent_history)
            hasMessages -> if (messageQuery.isNotBlank()) appString(R.string.msg_saved_results) else if (isGlobalChat) appString(R.string.msg_messages) else appString(R.string.msg_direct_history)
            !showDirectSetupCard -> if (isGlobalChat) null else appString(R.string.msg_start_here)
            else -> null
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val compactImeViewport = keyboardVisible && maxHeight < 220.dp
        val composerMaxHeight = if (compactImeViewport) maxHeight
            else (maxHeight * 0.62f).coerceAtLeast(128.dp).coerceAtMost(maxHeight)
        val headerMaxHeight = maxHeight * 0.34f
        Column(
            modifier = Modifier.fillMaxSize().padding(if (keyboardVisible) 8.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (keyboardVisible) 8.dp else 16.dp)
        ) {
            // Keep this field in the same composition slot when the keyboard opens.
            // The regular header is collapsed for both message writing and search.
            if (searchVisible) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = messageQuery,
                        onValueChange = { messageQuery = it.take(160) },
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                        singleLine = true,
                        label = { Text(appString(R.string.msg_search_text_device)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = closeSearch, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = appString(R.string.msg_close_search))
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Text(
                        appQuantityString(R.plurals.msg_messages_found_count, recentMessages.size, recentMessages.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!keyboardVisible && !searchVisible) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = headerMaxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(
                    modifier = Modifier.widthIn(min = 160.dp).weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        chatHeaderTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        chatHeaderSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (filteredMessages.isNotEmpty()) {
                        IconButton(onClick = { composerMoreExpanded = false; searchVisible = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Search, contentDescription = appString(R.string.msg_search_saved_messages))
                        }
                    }
                    if (isGlobalChat && globalLanJoined) {
                        TextButton(onClick = { onSetGlobalLanJoined(false) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(appString(R.string.msg_leave))
                        }
                    }
                    if (filteredMessages.isNotEmpty()) {
                        IconButton(onClick = { confirmClearChat = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = appString(R.string.msg_delete_channel_history))
                        }
                    }
                }
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
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = qetaraFilterChipColors(),
                                label = { Text(peer.label.ifBlank { peer.ip }, maxLines = 1) }
                            )
                        }
                    }
                }
            }

            if (!isGlobalChat && directChannelReady && !state.chatSessionReady) {
                TextButton(onClick = onOpenConnectTab, modifier = Modifier.heightIn(min = 48.dp)) { Text(appString(R.string.msg_confirm_session_connect)) }
            }

            if (showDirectSetupCard && hasMessages) {
                DirectChatSetupCard(
                    title = directNotReadyTitle,
                    body = directNotReadyBody
                )
                FilledTonalButton(
                    onClick = onOpenConnectTab,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(appString(R.string.msg_go_connect))
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
                                        !state.lanConnected -> appString(R.string.msg_no_wifi)
                                        !globalLanJoined -> appString(R.string.msg_outside_channel)
                                        state.globalChatPeerCount <= 0 -> appString(R.string.msg_only_you)
                                        else -> globalDeviceCountLabel
                                    }
                                } else if (channelReady) {
                                    if (!state.chatSessionReady) appString(R.string.msg_session_unconfirmed) else if (directTargetCount > 1) appQuantityString(R.plurals.msg_devices_count, directTargetCount, directTargetCount) else appString(R.string.msg_device_ready)
                                } else {
                                    appString(R.string.msg_no_device)
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
                                    state.sessionExpired -> appString(R.string.msg_session_expired)
                                    com.example.wifidrop.presentation.isSessionSyncApprovalRequired(state.tokenSyncStatus) -> appString(R.string.msg_pending)
                                    com.example.wifidrop.presentation.isSessionSyncFailure(state.tokenSyncStatus) -> appString(R.string.msg_retry)
                                    state.sessionSyncing -> appString(R.string.msg_syncing)
                                    else -> appString(R.string.msg_session)
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

                    if (channelHelperText != null && !showDirectSetupCard) {
                        Text(
                            channelHelperText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (showSyncButton) {
                        OutlinedButton(onClick = onSyncToken, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(appString(R.string.msg_sync_session))
                        }
                    }

                    if (isGlobalChat) {
                        TextButton(onClick = { channelOptionsExpanded = !channelOptionsExpanded }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (channelOptionsExpanded) appString(R.string.msg_hide_channel_options) else appString(R.string.msg_channel_options))
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
                    shape = RoundedCornerShape(16.dp),
                    color = if (operationalNoticeIsError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        quietPanelColor
                    },
                    contentColor = if (operationalNoticeIsError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
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
                    color = quietPanelColor,
                    contentColor = MaterialTheme.colorScheme.onSurface
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
            } else if (!compactImeViewport && !searchVisible) {
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
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (newMessagesCount > 0) appQuantityString(R.plurals.msg_new_messages_count, newMessagesCount, newMessagesCount) else appString(R.string.msg_latest_message))
                    }
                }
                }
            } else if (messageQuery.isNotBlank()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateBlock(
                        title = appString(R.string.msg_message_not_found),
                        body = appString(R.string.msg_try_other_message_query),
                        actionLabel = appString(R.string.msg_clear_search),
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
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(appString(R.string.msg_go_connect))
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

            if (!searchVisible && !showDirectSetupCard && !showChannelJoinPrompt) {
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
                    text = { Text(appString(R.string.msg_attach_files)) },
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
                    text = { Text(appString(R.string.msg_clear_text)) },
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
                    text = { Text(appString(R.string.msg_remove_attachments)) },
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
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(appString(R.string.msg_delete_message)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(UiSpaceS)) {
                    Text(appString(R.string.msg_delete_message_body))
                    Text(
                        pendingMessage?.text?.take(160).orEmpty().ifBlank { appString(R.string.msg_message_no_visible_content) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val id = pendingDeleteMessageId
                        pendingDeleteMessageId = null
                        if (id != null) onDeleteMessage(id)
                    }
                ) {
                    Text(appString(R.string.msg_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMessageId = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(appString(R.string.msg_cancel))
                }
            }
        )
    }

    if (confirmClearChat) {
        AlertDialog(
            onDismissRequest = { confirmClearChat = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(appString(R.string.msg_clear_chat)) },
            text = {
                Text(
                    appString(R.string.msg_clear_chat_body)
                )
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        confirmClearChat = false
                        onClearMessages(activeChannel)
                    }
                ) {
                    Text(appString(R.string.msg_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearChat = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(appString(R.string.msg_cancel))
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ChatEmptyIcon(Icons.Rounded.ChatBubbleOutline)
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        if (connectedToWifi) appString(R.string.msg_wifi_ready) else appString(R.string.msg_no_wifi),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    appString(R.string.msg_this_wifi_channel),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (connectedToWifi) {
                        if (peerCount > 0) {
                            appString(R.string.msg_join_write_network)
                        } else {
                            appString(R.string.msg_join_peers_appear)
                        }
                    } else {
                        appString(R.string.msg_connect_wifi_channel)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (connectedToWifi) {
                Button(
                    onClick = onJoin,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(appString(R.string.msg_join_wifi_channel), Modifier.padding(start = 8.dp))
                }
            } else {
                OutlinedButton(
                    onClick = onOpenConnectTab,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(appString(R.string.msg_go_connect), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun WifiChannelEmptyCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ChatEmptyIcon(Icons.Rounded.ChatBubbleOutline)
            Text(
                appString(R.string.msg_no_messages_yet),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                appString(R.string.msg_messages_appear_here),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChatEmptyIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(14.dp).size(28.dp)
        )
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
    val attachmentsLabel = appQuantityString(R.plurals.msg_files_ready_count, selectedFilesCount, selectedFilesCount)
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (keyboardVisible) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if ((selectedFilesCount > 0 || recoveryIncomplete) && !keyboardVisible) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (recoveryIncomplete) {
                            if (selectedFilesCount > 0) appQuantityString(R.plurals.msg_attachments_partially_restored, selectedFilesCount, selectedFilesCount)
                            else appString(R.string.msg_attachments_not_restored)
                        } else attachmentsLabel + selectedFileNames.firstOrNull()?.let { " · $it" }.orEmpty(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (recoveryIncomplete) 2 else 1,
                        color = if (recoveryIncomplete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (recoveryIncomplete) {
                        TextButton(onClick = onPickFile, modifier = Modifier.heightIn(min = 48.dp)) { Text(appString(R.string.msg_choose)) }
                    } else {
                        TextButton(onClick = onClearSelectedFiles, modifier = Modifier.heightIn(min = 48.dp)) { Text(appString(R.string.msg_remove)) }
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
                    .semantics { contentDescription = appString(R.string.msg_write_destination, destinationDescription) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                supportingText = if (draft.length >= 1_800) { { Text(appString(R.string.msg_character_count, draft.length)) } } else null,
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    disabledContainerColor = MaterialTheme.colorScheme.background,
                    errorContainerColor = MaterialTheme.colorScheme.background,
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
                        enabled = attachEnabled && composerEnabled,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = appString(R.string.msg_attach_files))
                    }
                }
                Button(
                    onClick = onSend,
                    enabled = sendEnabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        if (keyboardVisible && selectedFilesCount > 0) appString(R.string.msg_send_attachment_count, selectedFilesCount) else sendButtonLabel,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                IconButton(onClick = onOpenMore, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = appString(R.string.msg_more_actions)
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ChatEmptyIcon(Icons.Rounded.ChatBubbleOutline)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                verticalArrangement = Arrangement.spacedBy(UiSpaceS)
            ) {
                StatusChip(
                    label = if (isGlobal) {
                        when {
                            !joined -> appString(R.string.msg_not_joined)
                            peerCount <= 0 -> appString(R.string.msg_only_you)
                            else -> deviceCountLabel(peerCount)
                        }
                    } else if (isReady) {
                        appString(R.string.msg_ready)
                    } else {
                        appString(R.string.msg_no_device)
                    },
                    containerColor = if (isReady) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (isReady) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
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
        color = panelColor,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = UiSpaceM, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                appString(R.string.msg_channel_downloads),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
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
                    appString(R.string.msg_channel_auto_download),
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
    val clipboardContext = LocalContext.current
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
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (outgoing) 16.dp else 4.dp,
                bottomEnd = if (outgoing) 4.dp else 16.dp
            ),
            color = if (failed) MaterialTheme.colorScheme.errorContainer
                else if (outgoing) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            contentColor = contentColor,
            border = if (outgoing || failed) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (outgoing) appString(R.string.msg_you) + (item.peerLabel?.takeIf { it.isNotBlank() }?.let { " → $it" } ?: "")
                        else item.peerLabel ?: appString(R.string.msg_device),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box {
                        IconButton(onClick = { itemMoreExpanded = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.MoreHoriz, contentDescription = appString(R.string.msg_message_actions), modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = itemMoreExpanded, onDismissRequest = { itemMoreExpanded = false }) {
                            if (channelFileOffer == null) {
                                DropdownMenuItem(
                                    text = { Text(appString(R.string.msg_copy_text)) },
                                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        itemMoreExpanded = false
                                        copySensitiveText(clipboardContext, appString(R.string.msg_qetara_message), item.text)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(appString(R.string.msg_share_message)) },
                                leadingIcon = { Icon(Icons.Rounded.IosShare, contentDescription = null) },
                                onClick = { itemMoreExpanded = false; onShareMessage() }
                            )
                            if (outgoing && failed) {
                                DropdownMenuItem(
                                    text = { Text(appString(R.string.msg_retry_send)) },
                                    leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                    onClick = { itemMoreExpanded = false; onRetryMessage(item.id) }
                                )
                            }
                            if (outgoing && pending) {
                                DropdownMenuItem(
                                    text = { Text(appString(R.string.msg_cancel_send)) },
                                    leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                                    onClick = { itemMoreExpanded = false; onCancelQueuedMessage(item.id) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(appString(R.string.msg_delete_from_device)) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                onClick = { itemMoreExpanded = false; onRequestDelete() }
                            )
                        }
                    }
                }
                if (channelFileOffer != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
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
                                    if (channelFileOffer.fileSizeBytes >= 0) formatBytes(channelFileOffer.fileSizeBytes) else appString(R.string.msg_size_unavailable),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    if (!outgoing) {
                        FilledTonalButton(onClick = onDownloadChannelFileOffer, modifier = Modifier.heightIn(min = 48.dp)) { Text(appString(R.string.msg_download_file)) }
                    }
                } else {
                    SelectionContainer { Text(item.text, style = MaterialTheme.typography.bodyLarge) }
                }
                Text(
                    formatHistoryTime(item.timestampMs) + " · " + chatStatusLabel(item.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    modifier = Modifier.padding(top = 6.dp)
                )
                val friendlyIssue = friendlyMessageIssue(item.errorCause)
                if (!friendlyIssue.isNullOrBlank()) {
                    Text(friendlyIssue, style = MaterialTheme.typography.bodySmall, color = contentColor)
                }
                if (outgoing && failed) {
                    TextButton(onClick = { onRetryMessage(item.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(appString(R.string.msg_retry_send)) }
                }
            }
        }
    }
}

private fun deviceCountLabel(count: Int): String {
    return appQuantityString(R.plurals.msg_connected_devices_count, count, count)
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
                ChatMessageScope.DIRECT -> appString(R.string.msg_share_direct_channel)
                ChatMessageScope.GLOBAL_LAN -> appString(R.string.msg_share_wifi_channel)
                ChatMessageScope.DIRECT_CHANNEL -> appString(R.string.msg_share_wifi_direct_channel)
            }
        )
        append('\n')
        append(
            if (item.direction == ChatMessageDirection.OUTGOING) {
                appString(R.string.msg_share_from_you)
            } else {
                appString(R.string.msg_share_sender, item.peerLabel ?: appString(R.string.msg_device))
            }
        )
        append('\n')
        append(appString(R.string.msg_share_time, formatHistoryTime(item.timestampMs)))
        append("\n\n")
        if (fileOffer != null) {
            append(
                if (item.direction == ChatMessageDirection.OUTGOING) {
                    appString(R.string.msg_you_shared)
                } else {
                    appString(R.string.msg_peer_shared, item.peerLabel ?: fileOffer.senderLabel)
                }
            )
            append('\n')
            append(fileOffer.fileName)
            append('\n')
            append(
                if (fileOffer.fileSizeBytes >= 0L) {
                    formatBytes(fileOffer.fileSizeBytes)
                } else {
                    appString(R.string.msg_size_unavailable)
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
    context.startActivity(Intent.createChooser(intent, appString(R.string.msg_share_from_qetara)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

private fun friendlyMessageIssue(cause: String?): String? {
    if (cause?.contains("cancel", ignoreCase = true) == true) return null
    return com.example.wifidrop.presentation.actionableTransferIssue(cause, appString(R.string.msg_send_failed_hint))
        ?.let(::runtimeFailureText)
}

@Composable
private fun queueStatusColor(status: SendQueueStatus): Color {
    return when (status) {
        SendQueueStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer
        SendQueueStatus.PAUSED,
        SendQueueStatus.RETRY_WAIT -> MaterialTheme.colorScheme.tertiaryContainer
        SendQueueStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
        SendQueueStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        SendQueueStatus.QUEUED,
        SendQueueStatus.CANCELED -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
}

@Composable
private fun queueStatusContentColor(status: SendQueueStatus): Color {
    return when (status) {
        SendQueueStatus.RUNNING -> MaterialTheme.colorScheme.onPrimaryContainer
        SendQueueStatus.PAUSED,
        SendQueueStatus.RETRY_WAIT -> MaterialTheme.colorScheme.onTertiaryContainer
        SendQueueStatus.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
        SendQueueStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        SendQueueStatus.QUEUED,
        SendQueueStatus.CANCELED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
