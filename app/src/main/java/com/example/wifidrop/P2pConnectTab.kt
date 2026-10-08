package com.example.wifidrop

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton as MaterialFilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.wifidrop.presentation.isSessionSyncFailure
import com.example.wifidrop.presentation.isSessionSyncManualPairingRequired
import kotlinx.coroutines.launch

/** The everyday connection flow. Detailed transport controls remain in the advanced view. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun P2pConnectTab(
    state: P2pScreenState,
    onModeChange: (ConnectionMode) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onStartHost: () -> Unit,
    onStartClient: () -> Unit,
    onCancelConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onConnectToPeer: (String) -> Unit,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onUsePeer: (String) -> Unit,
    onSyncSession: () -> Unit,
    onConfirmManualSession: () -> Unit,
    onRenewSession: () -> Unit,
    onTokenChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onCopyToken: () -> Unit,
    onPasteToken: () -> Unit,
    onOpenSend: () -> Unit,
    onOpenChat: () -> Unit,
    onSaveFavorite: (String) -> Unit,
    onSkipFavorite: (String) -> Unit,
    onTrustPeer: (PendingTrustRequest) -> Unit,
    onRequestForgetPeer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val selectPeer: (String) -> Unit = { ip ->
        focusManager.clearFocus()
        onUsePeer(ip)
        scope.launch { listState.animateScrollToItem(2) }
    }
    val direct = state.activeConnectionMode == ConnectionMode.WIFI_DIRECT
    val linked = state.connection?.groupFormed == true
    val host = state.connection?.isGroupOwner == true
    val ready = state.sessionEnabled && (if (direct) !state.directTargetIp.isNullOrBlank() else state.lanConnected && !state.resolvedTargetIp.isNullOrBlank())
    val exchangeReady = ready && state.sessionReady
    val selectedLabel = if (direct) state.directTargetLabel ?: state.directTargetIp else state.resolvedTargetLabel ?: state.resolvedTargetIp
    var manualAddressDraft by rememberSaveable(state.targetIp) { mutableStateOf(state.targetIp) }
    var showManualAddress by rememberSaveable { mutableStateOf(false) }
    var showCredentials by rememberSaveable { mutableStateOf(false) }
    var showConnectionHelp by rememberSaveable { mutableStateOf(false) }
    var showRememberedPeers by rememberSaveable { mutableStateOf(false) }
    val manualPairing = isSessionSyncManualPairingRequired(state.tokenSyncStatus)
    val sessionProblem = state.sessionExpired || manualPairing ||
        isSessionSyncFailure(state.tokenSyncStatus)
    val sessionSyncing = state.sessionSyncing
    val validCredentials = FileTransfer.isValidToken(state.authToken) && TransferSecurity.isValidPin(state.sessionPin)
    val invalidAddress = manualAddressDraft.isNotBlank() && !isValidManualConnectionAddress(manualAddressDraft)
    val peers = state.knownServicePeers.filter { com.example.wifidrop.presentation.isAutomaticConnectionAddress(it.ip, state.lanLocalIp) }
        .distinctBy { it.ip }.sortedWith(compareByDescending<KnownPeerSnapshot> { it.trusted }.thenBy { it.label })

    val showSessionSection = state.sessionEnabled && (ready || linked || (state.lanConnected && !direct) || sessionProblem)
    val lanPeersSectionIndex = 3 + if (showSessionSection) 1 else 0
    val manualAddressIndex = lanPeersSectionIndex + 1 + peers.size + if (peers.isEmpty() && !state.lanScanning) 1 else 0

    LaunchedEffect(manualPairing) {
        if (manualPairing) {
            showCredentials = true
            listState.animateScrollToItem(3)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item("connect-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.conn_connect_devices),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    if (manualPairing) stringResource(R.string.conn_enter_receiver_credentials)
                    else if (exchangeReady && !sessionProblem && !sessionSyncing) stringResource(R.string.conn_ready_files_messages)
                    else if (direct) stringResource(R.string.conn_nearby_android_no_router)
                    else stringResource(R.string.conn_android_pc_same_wifi),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = !direct,
                        onClick = { onModeChange(ConnectionMode.LAN) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = qetaraFilterChipColors(),
                        leadingIcon = { Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text(stringResource(R.string.conn_same_wifi)) }
                    )
                    FilterChip(
                        selected = direct,
                        onClick = { onModeChange(ConnectionMode.WIFI_DIRECT) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = qetaraFilterChipColors(),
                        leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text(stringResource(R.string.conn_wifi_direct)) }
                    )
                }
            }
        }
        item("this-device") {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Devices, contentDescription = null, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.conn_this_device), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.thisDeviceName.ifBlank { stringResource(R.string.conn_my_android) }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (!direct && state.lanConnected) {
                            Text(state.lanLocalIp ?: stringResource(R.string.conn_connected_network), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item("connection-action") {
            ConnectionStepSurface(active = state.sessionEnabled) {
                when {
                    !state.sessionEnabled -> {
                        Text(stringResource(R.string.conn_session_disabled), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.conn_activate_session_hint))
                    }
                    direct && !state.permissionGranted -> {
                        Text(stringResource(R.string.conn_allow_discovery), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.conn_nearby_permission_hint))
                        Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.conn_allow_nearby_connection)) }
                        Text(stringResource(R.string.conn_same_wifi_pc_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    direct && !state.p2pEnabled -> {
                        Text(stringResource(R.string.conn_enable_wifi), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.conn_enable_wifi_hint))
                        Button(onClick = onOpenWifiSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.conn_open_wifi_settings)) }
                    }
                    ready -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.conn_selected_device), style = MaterialTheme.typography.labelLarge)
                        }
                        Text(selectedLabel ?: stringResource(R.string.conn_your_other_device), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                        when {
                            sessionSyncing -> {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(stringResource(R.string.conn_preparing_approval_hint))
                            }
                            manualPairing -> {
                                Text(stringResource(R.string.conn_enter_credentials_below))
                                FilledTonalButton(onClick = { showCredentials = true; scope.launch { listState.animateScrollToItem(3) } }) { Text(stringResource(R.string.conn_enter_code_pin)) }
                            }
                            !state.sessionReady || sessionProblem || !validCredentials -> {
                                Text(if (state.sessionExpired) stringResource(R.string.conn_renew_continue) else stringResource(R.string.conn_session_unconfirmed_hint), style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = onSyncSession, enabled = !state.sessionExpired) { Text(stringResource(R.string.conn_retry_connection)) }
                                TextButton(onClick = { showCredentials = true; scope.launch { listState.animateScrollToItem(3) } }) { Text(stringResource(R.string.conn_enter_code_pin)) }
                            }
                            else -> {
                                Text(stringResource(R.string.conn_session_confirmed_hint), style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = onOpenSend, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(stringResource(R.string.conn_send_files), Modifier.padding(start = 8.dp))
                                }
                                FilledTonalButton(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(stringResource(R.string.conn_open_chat), Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                        if (direct && linked) {
                            TextButton(onClick = onDisconnect) { Text(stringResource(R.string.conn_disconnect_device)) }
                        }
                    }
                    direct && (state.directCreatingGroup || (linked && host)) -> {
                        Text(if (linked) stringResource(R.string.conn_link_available) else stringResource(R.string.conn_creating_link), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                        if (!linked) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        if (linked && state.chatDirectAvailablePeers.isNotEmpty()) {
                            Text(stringResource(R.string.conn_choose_recipient_hint))
                            FilledTonalButton(onClick = onOpenChat) { Text(stringResource(R.string.conn_choose_device)) }
                        } else {
                            Text(stringResource(R.string.conn_other_android_find_hint))
                        }
                        Text(state.thisDeviceName.ifBlank { stringResource(R.string.conn_this_device) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onClick = if (linked) onDisconnect else onCancelConnect) { Text(stringResource(R.string.conn_close_link)) }
                    }
                    direct && state.directConnecting -> {
                        Text(stringResource(R.string.conn_connecting_devices), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.conn_accept_android_hint))
                        TextButton(onClick = onCancelConnect) { Text(stringResource(R.string.conn_cancel_connection)) }
                    }
                    direct && linked -> {
                        Text(stringResource(R.string.conn_preparing_session), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.conn_approve_host_hint))
                        OutlinedButton(onClick = onSyncSession) { Text(stringResource(R.string.conn_sync_session)) }
                        TextButton(onClick = onDisconnect) { Text(stringResource(R.string.conn_disconnect)) }
                    }
                    direct && state.directDiscovering -> {
                        Text(stringResource(R.string.conn_searching_nearby), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(if (state.peers.isEmpty()) stringResource(R.string.conn_other_android_create_hint)
                            else stringResource(R.string.conn_choose_link_device_below))
                        TextButton(onClick = onCancelConnect) { Text(stringResource(R.string.conn_stop_search)) }
                    }
                    direct -> {
                        Text(stringResource(R.string.conn_connect_wifi_direct), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.conn_create_search_hint))
                        Button(onClick = onStartHost, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.conn_create_a_link)) }
                        OutlinedButton(onClick = onStartClient, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.conn_find_a_device)) }
                    }
                    !state.lanConnected -> {
                        Text(stringResource(R.string.conn_connect_same_wifi), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.conn_other_android_pc_hint))
                        Button(onClick = onOpenWifiSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.conn_open_wifi_settings)) }
                    }
                    else -> {
                        Text(if (state.lanScanning) stringResource(R.string.conn_searching_network) else stringResource(R.string.conn_find_other_device), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.conn_pc_receive_hint))
                        if (state.lanScanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Button(onClick = if (state.lanScanning) onCancelScan else onScan, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.lanScanning) stringResource(R.string.conn_stop_search) else stringResource(R.string.conn_find_devices))
                        }
                    }
                }
                if (!direct && state.lanConnected) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (ready) {
                            TextButton(onClick = { scope.launch { listState.animateScrollToItem(lanPeersSectionIndex) } }) { Text(stringResource(R.string.conn_change_device)) }
                        }
                        TextButton(onClick = {
                            showManualAddress = true
                            scope.launch { listState.animateScrollToItem(manualAddressIndex) }
                        }) { Text(stringResource(R.string.conn_connect_ip_address)) }
                    }
                }
            }
        }
        if (showSessionSection) {
            item("session") {
                ConnectionStepSurface {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (manualPairing) stringResource(R.string.conn_other_device_session) else stringResource(R.string.conn_your_session), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (state.sessionExpired) {
                        Text(stringResource(R.string.conn_session_ended_hint), color = MaterialTheme.colorScheme.error)
                        Button(onClick = onRenewSession) { Text(stringResource(R.string.conn_renew_session)) }
                    } else if (state.tokenSyncStatus.isNotBlank()) {
                        Text(state.tokenSyncStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    } else {
                        Text(stringResource(R.string.conn_credentials_or_approval_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (ready && !state.sessionExpired && !manualPairing && !sessionSyncing) {
                        OutlinedButton(onClick = onSyncSession) { Text(stringResource(R.string.conn_sync_session)) }
                    }
                    TextButton(onClick = { showCredentials = !showCredentials }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showCredentials) stringResource(R.string.conn_hide_code_pin) else stringResource(R.string.conn_show_code_pin), Modifier.weight(1f))
                        Icon(if (showCredentials) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    if (showCredentials) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(stringResource(R.string.conn_matching_credentials_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(value = state.authToken, onValueChange = onTokenChange, enabled = !sessionSyncing, label = { Text(stringResource(R.string.conn_session_code)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        P2pSessionPinField(state = state, onValueChange = onPinChange, modifier = Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onCopyToken) { Text(stringResource(R.string.conn_copy_code)) }
                            TextButton(onClick = onPasteToken) { Text(stringResource(R.string.conn_paste_code)) }
                        }
                        if (ready && !state.sessionExpired) {
                            Button(onClick = { onConfirmManualSession(); onOpenSend() }, enabled = validCredentials && !sessionSyncing, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.conn_use_session)) }
                        }
                    }
                }
            }
        }
        if (state.sessionEnabled && direct && state.permissionGranted && state.p2pEnabled && !linked && state.peers.isNotEmpty()) {
            item("direct-peers-title") { ConnectionSectionTitle(stringResource(R.string.conn_nearby_devices)) }
            items(state.peers, key = { "direct:" + it.address }) { peer ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(peer.name.ifBlank { stringResource(R.string.conn_nearby_android) }, fontWeight = FontWeight.SemiBold)
                        Text(peerStatusLabel(peer.status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = { onConnectToPeer(peer.address) },
                            enabled = peer.status == WifiP2pDevice.AVAILABLE || peer.status == WifiP2pDevice.FAILED,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.conn_connect_this_device)) }
                    }
                }
            }
        }
        if (!direct && state.lanConnected) {
            item("lan-peers-title") {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ConnectionSectionTitle(stringResource(R.string.conn_network_devices))
                    TextButton(onClick = if (state.lanScanning) onCancelScan else onScan) {
                        Icon(if (state.lanScanning) Icons.Rounded.Close else Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (state.lanScanning) stringResource(R.string.conn_stop) else stringResource(R.string.conn_refresh), Modifier.padding(start = 6.dp))
                    }
                }
            }
            if (peers.isEmpty() && !state.lanScanning) {
                item("no-lan-peers") {
                    EmptyStateBlock(
                        title = stringResource(R.string.conn_no_devices_yet),
                        body = stringResource(R.string.conn_no_devices_hint)
                    )
                }
            }
            items(peers, key = { "lan:" + it.ip }) { peer ->
                val selected = state.resolvedTargetIp == peer.ip
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Devices, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(peer.label.ifBlank { peer.ip }, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(if (peer.trusted) stringResource(R.string.conn_remembered_ip, peer.ip) else peer.ip, style = MaterialTheme.typography.bodySmall, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton(onClick = { selectPeer(peer.ip) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.resolvedTargetIp == peer.ip) stringResource(R.string.conn_sync_this_device) else stringResource(R.string.conn_connect_this_device))
                        }
                    }
                }
            }
            item("manual-address") {
                ConnectionStepSurface {
                    TextButton(onClick = { showManualAddress = !showManualAddress }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showManualAddress) stringResource(R.string.conn_hide_ip_address) else stringResource(R.string.conn_connect_ip_address), Modifier.weight(1f))
                        Icon(if (showManualAddress) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    if (showManualAddress) {
                        OutlinedTextField(
                            value = manualAddressDraft,
                            onValueChange = { manualAddressDraft = it.trim() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.conn_other_device_ip)) },
                            placeholder = { Text(stringResource(R.string.conn_ip_example)) },
                            supportingText = { Text(if (invalidAddress) stringResource(R.string.conn_invalid_ip_hint)
                                else stringResource(R.string.conn_ip_location_hint)) },
                            isError = invalidAddress,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                        )
                        Button(onClick = { selectPeer(manualAddressDraft.trim()) }, enabled = isValidManualConnectionAddress(manualAddressDraft) && !sessionSyncing) { Text(stringResource(R.string.conn_connect_ip)) }
                    }
                }
            }
        }
        state.pendingTrust?.let { request ->
            item("pending-trust") {
                ConnectionStepSurface {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.conn_recognize_device), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(stringResource(R.string.conn_peer_and_ip, request.label, request.ip))
                    Text(stringResource(R.string.conn_verify_sender_hint), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onTrustPeer(request) }) { Text(stringResource(R.string.conn_trust_device)) }
                }
            }
        }
        if (!direct && state.lanConnected && state.favoritePeers.any { !it.lastKnownIp.isNullOrBlank() }) {
            item("favorites-title") { ConnectionSectionTitle(stringResource(R.string.conn_favorites)) }
            items(state.favoritePeers.filter { !it.lastKnownIp.isNullOrBlank() }, key = { "favorite:" + it.id }) { peer ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(TrustedPeerStore.displayName(peer), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.conn_last_ip, peer.lastKnownIp.orEmpty()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { selectPeer(peer.lastKnownIp.orEmpty()) }) { Text(stringResource(R.string.conn_connect_favorite)) }
                    }
                }
            }
        }
        val suggestionId = state.favoriteSuggestionPeerId
        if (ready && !suggestionId.isNullOrBlank()) {
            item("favorite-suggestion") {
                ConnectionStepSurface {
                    Text(stringResource(R.string.conn_find_faster), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.conn_save_favorite_hint, state.favoriteSuggestionLabel ?: stringResource(R.string.conn_this_device_lowercase)), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(onClick = { onSaveFavorite(suggestionId) }) { Text(stringResource(R.string.conn_save_favorite)) }
                        TextButton(onClick = { onSkipFavorite(suggestionId) }) { Text(stringResource(R.string.conn_not_now)) }
                    }
                }
            }
        }
        if (state.trustedPeers.isNotEmpty()) {
            item("remembered-peers-toggle") {
                TextButton(onClick = { showRememberedPeers = !showRememberedPeers }) {
                    Text(if (showRememberedPeers) stringResource(R.string.conn_hide_remembered) else stringResource(R.string.conn_manage_remembered))
                }
            }
            if (showRememberedPeers) {
                items(state.trustedPeers, key = { "remembered:" + it.id }) { peer ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(TrustedPeerStore.displayName(peer), fontWeight = FontWeight.SemiBold)
                            peer.lastKnownIp?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            TextButton(onClick = { onRequestForgetPeer(peer.id) }) {
                                Text(stringResource(R.string.conn_forget_device), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        item("connection-help") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showConnectionHelp = !showConnectionHelp }) { Text(if (showConnectionHelp) stringResource(R.string.conn_close_help) else stringResource(R.string.conn_missing_device_help)) }
                if (showConnectionHelp) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (direct) stringResource(R.string.conn_help_direct)
                                else stringResource(R.string.conn_help_lan), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStepSurface(active: Boolean = false, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) { content() }
        }
    }
}

@Composable
private fun ConnectionSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 12.dp).semantics { heading() })
}

@Composable
private fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    MaterialButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(12.dp), content = content)
}

@Composable
private fun FilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    MaterialFilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        content = content
    )
}

@Composable
private fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    MaterialOutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(12.dp), content = content)
}

@Composable
private fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    MaterialTextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, content = content)
}

internal fun isValidManualConnectionAddress(value: String): Boolean {
    val address = value.trim()
    val octets = address.split('.')
    return octets.size == 4 && octets.all { it.isNotEmpty() && it.length <= 3 && it.all { character -> character in '0'..'9' } && (it.toIntOrNull() ?: -1) in 0..255 } &&
        address != "0.0.0.0" && address != "255.255.255.255"
}
