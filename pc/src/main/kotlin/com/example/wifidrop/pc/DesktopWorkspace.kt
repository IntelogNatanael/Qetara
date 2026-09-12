package com.example.wifidrop.pc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

internal data class DesktopWorkspaceState(
    val token: String,
    val pin: String,
    val deviceName: String,
    val outputDirectory: String,
    val host: String,
    val filePaths: List<String>,
    val port: String,
    val retries: String,
    val sessionMinutes: String,
    val localEndpoints: List<LocalNetworkEndpoint>,
    val peers: List<DesktopLanPeer>,
    val discoveryPhase: DesktopTaskPhase,
    val discoveryStatus: String,
    val receiverPhase: DesktopTaskPhase,
    val receiverStatus: String,
    val sendingPhase: DesktopTaskPhase,
    val sendingStatus: String,
    val sendingProgress: Float?,
    val receivingProgress: Float?,
    val receiverIssues: List<String>,
    val sendIssues: List<String>,
    val credentialsReady: Boolean,
    val isFileDragActive: Boolean,
    val transfers: List<DesktopTransferEntry>,
    val notice: String?,
    val sessionRemaining: String,
    val unreadMessages: Int = 0,
    val identityFingerprint: String = "",
    val messageSending: Boolean = false,
    val flashActive: Boolean = false,
    val flashApprovals: Int = 0
) {
    val filePath: String get() = filePaths.firstOrNull().orEmpty()
    val selectedFilesCount: Int get() = filePaths.size
    val receiverBusy: Boolean get() = receiverPhase in listOf(DesktopTaskPhase.STARTING, DesktopTaskPhase.RUNNING, DesktopTaskPhase.STOPPING)
    val sendingBusy: Boolean get() = sendingPhase in listOf(DesktopTaskPhase.STARTING, DesktopTaskPhase.RUNNING, DesktopTaskPhase.STOPPING)
    val settingsLocked: Boolean get() = receiverBusy || sendingBusy || messageSending
}

internal data class DesktopWorkspaceActions(
    val onTokenChange: (String) -> Unit,
    val onPinChange: (String) -> Unit,
    val onDeviceNameChange: (String) -> Unit,
    val onOutputDirectoryChange: (String) -> Unit,
    val onHostChange: (String) -> Unit,
    val onPortChange: (String) -> Unit,
    val onRetriesChange: (String) -> Unit,
    val onSessionMinutesChange: (String) -> Unit,
    val onCreateSession: () -> Unit,
    val onCopySession: () -> Unit,
    val onChooseFile: () -> Unit,
    val onClearFiles: () -> Unit,
    val onChooseDirectory: () -> Unit,
    val onOpenDirectory: () -> Unit,
    val onOpenReceivedFile: (String) -> Unit,
    val onRefreshPeers: () -> Unit,
    val onSelectPeer: (DesktopLanPeer) -> Unit,
    val onStartReceiver: () -> Unit,
    val onStopReceiver: () -> Unit,
    val onSend: () -> Unit,
    val onCancelSend: () -> Unit,
    val onDismissNotice: () -> Unit,
    val onChatVisibilityChange: (Boolean) -> Unit,
    val onSaveSettings: () -> Unit,
    val onOpenSource: () -> Unit,
    val onCopyFingerprint: () -> Unit = {},
    val onOpenFlash: () -> Unit = {}
)

private enum class WorkspaceTab(val label: String, val title: String, val subtitle: String, val glyph: WorkspaceGlyph) {
    SHARE("Compartir", "Compartir archivos", "Elige archivos y un equipo de tu red.", WorkspaceGlyph.SHARE),
    RECEIVE("Recibir", "Recibir archivos", "Activa la recepción y comparte tu sesión.", WorkspaceGlyph.RECEIVE),
    CHAT("Mensajes", "Mensajes", "Conversa con los equipos de tu sesión.", WorkspaceGlyph.CHAT),
    ACTIVITY("Actividad", "Actividad", "El registro de esta sesión se borra al cerrar Qetara.", WorkspaceGlyph.ACTIVITY),
    SETTINGS("Ajustes", "Ajustes", "Preferencias de este equipo y conexión local.", WorkspaceGlyph.SETTINGS)
}

@Composable
internal fun DesktopWorkspace(
    state: DesktopWorkspaceState,
    actions: DesktopWorkspaceActions,
    chatContent: @Composable () -> Unit,
    activityContent: @Composable (Modifier) -> Unit,
    flashVisible: Boolean = false,
    flashContent: @Composable () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(WorkspaceTab.SHARE) }
    var showLicenses by remember { mutableStateOf(false) }
    if (showLicenses) DesktopLicensesDialog { showLicenses = false }
    val pageScroll = rememberScrollState()
    LaunchedEffect(selectedTab) {
        actions.onChatVisibilityChange(selectedTab == WorkspaceTab.CHAT)
        pageScroll.scrollTo(0)
    }
    Surface(color = qetaraCanvas, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expandedNavigation = maxWidth >= 1040.dp
            Row(Modifier.fillMaxSize()) {
                WorkspaceNavigation(expandedNavigation, selectedTab, state, flashVisible, { selectedTab = it }, actions.onOpenFlash)
                Box(Modifier.fillMaxHeight().width(1.dp).background(qetaraLine))
                Column(Modifier.weight(1f).fillMaxHeight().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (flashVisible) {
                        // Flash keeps its existing exit action; normal navigation cannot close it.
                        Box(Modifier.weight(1f).fillMaxWidth()) { flashContent() }
                        StatusBadge(
                            if (state.receiverPhase == DesktopTaskPhase.RUNNING) "Recepción habitual activa" else "Recepción habitual desactivada",
                            state.receiverPhase == DesktopTaskPhase.RUNNING
                        )
                    } else {
                        WorkspaceHeader(selectedTab, state)
                        state.notice?.let { WorkspaceNotice(it, actions.onDismissNotice) }
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            val wide = maxWidth >= 760.dp
                            val scrolling = Modifier.fillMaxSize().verticalScroll(pageScroll).padding(end = 12.dp)
                            when (selectedTab) {
                                WorkspaceTab.SHARE -> Column(scrolling, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    if (wide) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                            Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                SendCard(state, actions)
                                                RecentTransfers(state.transfers.takeLast(3).reversed(), actions)
                                            }
                                            Column(Modifier.weight(1f)) { SessionCard(state, actions) }
                                        }
                                    } else {
                                        if (!state.credentialsReady) SessionCard(state, actions)
                                        SendCard(state, actions)
                                        if (state.credentialsReady) SessionCard(state, actions)
                                        RecentTransfers(state.transfers.takeLast(3).reversed(), actions)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                                WorkspaceTab.RECEIVE -> Column(scrolling, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    if (wide) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                            Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                ReceiveCard(state, actions)
                                                RecentTransfers(state.transfers.filter { it.incoming }.takeLast(12).reversed(), actions)
                                            }
                                            Column(Modifier.weight(1f)) { SessionCard(state, actions) }
                                        }
                                    } else {
                                        ReceiveCard(state, actions)
                                        SessionCard(state, actions)
                                        RecentTransfers(state.transfers.filter { it.incoming }.takeLast(12).reversed(), actions)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                                WorkspaceTab.CHAT -> Column(scrolling, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    var connectionExpanded by remember { mutableStateOf(false) }
                                    val needsConnection = !state.credentialsReady || state.receiverPhase != DesktopTaskPhase.RUNNING
                                    if (wide && needsConnection) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                            ChatSurface(Modifier.weight(1.5f), chatContent)
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                ChatConnectionCards(state, actions)
                                            }
                                        }
                                    } else {
                                        if (needsConnection) {
                                            Surface(color = qetaraCanvasElevated, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, qetaraLine), modifier = Modifier.fillMaxWidth()) {
                                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    Text("Conexión", Modifier.weight(1f), style = MaterialTheme.typography.subtitle2, color = qetaraInk)
                                                    OutlinedButton({ connectionExpanded = !connectionExpanded }, modifier = Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(8.dp)) {
                                                        Text(if (connectionExpanded) "Ocultar datos" else "Mostrar datos")
                                                    }
                                                }
                                            }
                                            if (connectionExpanded) ChatConnectionCards(state, actions)
                                        }
                                        ChatSurface(content = chatContent)
                                    }
                                }
                                WorkspaceTab.ACTIVITY -> activityContent(Modifier.fillMaxSize())
                                WorkspaceTab.SETTINGS -> Column(scrolling, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    SettingsCards(state, actions, wide) { showLicenses = true }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                            if (selectedTab != WorkspaceTab.ACTIVITY && pageScroll.maxValue > 0) {
                                VerticalScrollbar(rememberScrollbarAdapter(pageScroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceNavigation(expanded: Boolean, selectedTab: WorkspaceTab, state: DesktopWorkspaceState, flashVisible: Boolean, onSelectTab: (WorkspaceTab) -> Unit, onOpenFlash: () -> Unit) {
    Surface(color = qetaraCanvasElevated, modifier = Modifier.width(if (expanded) 176.dp else 76.dp).fillMaxHeight()) {
        Column(Modifier.padding(horizontal = if (expanded) 16.dp else 8.dp, vertical = 24.dp)) {
            if (expanded) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QetaraLogoMark(Modifier.size(48.dp), qetaraInk)
                    Text("Qetara", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = qetaraInk)
                }
            } else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    QetaraLogoMark(Modifier.size(48.dp), qetaraInk)
                    Text("Qetara", style = MaterialTheme.typography.caption, fontWeight = FontWeight.SemiBold, color = qetaraInk)
                }
            }
            Spacer(Modifier.height(24.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkspaceTab.entries.forEach { tab ->
                    WorkspaceNavigationItem(tab.label, tab.glyph, !flashVisible && selectedTab == tab, expanded, !flashVisible, if (tab == WorkspaceTab.CHAT) state.unreadMessages else 0) { onSelectTab(tab) }
                }
                Divider(Modifier.padding(vertical = 8.dp), color = qetaraLine)
                WorkspaceNavigationItem("Flash", WorkspaceGlyph.FLASH, flashVisible, expanded, !flashVisible, state.flashApprovals, state.flashActive, onOpenFlash)
            }
            Spacer(Modifier.height(16.dp))
            Divider(color = qetaraLine)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(qetaraTeal, RoundedCornerShape(50)))
                Text(if (expanded) "En tu red local" else "Local", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.caption, color = qetaraMuted)
            }
        }
    }
}

@Composable
private fun WorkspaceNavigationItem(label: String, glyph: WorkspaceGlyph, selected: Boolean, expanded: Boolean, enabled: Boolean, count: Int = 0, active: Boolean = false, onClick: () -> Unit) {
    val foreground = if (selected) qetaraTeal else if (!enabled) qetaraMuted.copy(alpha = .6f) else qetaraMuted
    Surface(color = if (selected) qetaraMist else Color.Transparent, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().heightIn(min = if (expanded) 48.dp else 56.dp).selectable(selected, enabled = enabled, role = Role.Tab, onClick = onClick)) {
        if (expanded) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkspaceIcon(glyph, color = foreground)
                Text(label, Modifier.weight(1f), fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (count > 0) NavigationCount(count)
                else if (active) Box(Modifier.size(6.dp).background(qetaraTeal, RoundedCornerShape(50)))
            }
        } else {
            Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                    WorkspaceIcon(glyph, color = foreground)
                    if (count > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-5).dp)) { NavigationCount(count) }
                    else if (active) Box(Modifier.align(Alignment.TopEnd).offset(x = 5.dp).size(6.dp).background(qetaraTeal, RoundedCornerShape(50)))
                }
                Text(label, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = foreground, maxLines = 1)
            }
        }
    }
}

@Composable
private fun NavigationCount(count: Int) {
    Surface(color = qetaraTeal, shape = RoundedCornerShape(50)) {
        Text(if (count > 99) "99+" else count.toString(), Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 10.sp, lineHeight = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WorkspaceHeader(tab: WorkspaceTab, state: DesktopWorkspaceState) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 640.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { PageHeading(tab.title, tab.subtitle) }
                WorkspaceDeviceStatus(state, Modifier.widthIn(max = 224.dp))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageHeading(tab.title, tab.subtitle)
                WorkspaceDeviceStatus(state, Modifier.widthIn(max = 280.dp))
            }
        }
    }
}

@Composable
private fun WorkspaceDeviceStatus(state: DesktopWorkspaceState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkspaceIcon(WorkspaceGlyph.DEVICES, Modifier.size(18.dp), qetaraMuted)
            Text(state.deviceName.ifBlank { "Este equipo" }, style = MaterialTheme.typography.body2, color = qetaraInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StatusBadge(
            if (state.flashActive) {
                if (state.receiverPhase == DesktopTaskPhase.RUNNING) "Recepción habitual activa" else "Recepción habitual desactivada"
            } else if (state.receiverPhase == DesktopTaskPhase.RUNNING) "Disponible para recibir" else "Recepción desactivada",
            state.receiverPhase == DesktopTaskPhase.RUNNING
        )
    }
}

@Composable
private fun WorkspaceNotice(notice: String, onDismiss: () -> Unit) {
    Surface(color = qetaraMist, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, qetaraLine), modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            WorkspaceIcon(WorkspaceGlyph.INFO, color = qetaraTeal)
            Text(notice, Modifier.weight(1f), style = MaterialTheme.typography.body2, color = qetaraInk)
            IconButton(onDismiss, modifier = Modifier.size(44.dp).semantics { contentDescription = "Cerrar aviso" }) { WorkspaceIcon(WorkspaceGlyph.CLOSE, Modifier.size(20.dp), qetaraMuted) }
        }
    }
}

@Composable
private fun PageHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, color = qetaraInk)
        Text(description, style = MaterialTheme.typography.body2, color = qetaraMuted)
    }
}

@Composable
private fun WorkspaceCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = qetaraCanvasElevated, border = BorderStroke(1.dp, qetaraLine)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold, color = qetaraInk)
            content()
        }
    }
}

@Composable
private fun SessionCard(state: DesktopWorkspaceState, actions: DesktopWorkspaceActions) {
    var showPin by remember { mutableStateOf(false) }
    WorkspaceCard("Sesión compartida") {
        Text("Usa el mismo código y PIN en los dos equipos.", style = MaterialTheme.typography.body2, color = qetaraMuted)
        OutlinedTextField(
            state.token, actions.onTokenChange,
            label = { Text("Código de sesión") },
            placeholder = { Text("Ej. QETARA24") },
            singleLine = true,
            enabled = !state.settingsLocked,
            isError = state.token.isNotBlank() && !com.example.wifidrop.protocol.isValidToken(state.token),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            state.pin, actions.onPinChange,
            label = { Text("PIN de 6 dígitos") },
            singleLine = true,
            enabled = !state.settingsLocked,
            isError = state.pin.isNotBlank() && !com.example.wifidrop.protocol.isValidPin(state.pin),
            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton({ showPin = !showPin }, modifier = Modifier.heightIn(min = 44.dp)) { Text(if (showPin) "Ocultar" else "Ver") } },
            modifier = Modifier.fillMaxWidth()
        )
        if (!state.settingsLocked) {
            OutlinedButton(actions.onCreateSession, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(8.dp)) {
                Text(if (state.credentialsReady) "Crear otra sesión" else "Crear sesión")
            }
        }
        if (state.credentialsReady) {
            Text(
                if (state.receiverPhase == DesktopTaskPhase.RUNNING) "Recepción activa · " + state.sessionRemaining
                else "Sesión lista. Activa la recepción para recibir aquí.",
                style = MaterialTheme.typography.body2,
                color = qetaraTeal
            )
            if (!state.receiverBusy) {
                Button(actions.onStartReceiver, enabled = state.receiverIssues.isEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(8.dp)) { Text("Activar recepción aquí") }
            }
            TextButton(actions.onCopySession, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                WorkspaceIcon(WorkspaceGlyph.COPY, Modifier.size(18.dp), qetaraTeal)
                Spacer(Modifier.width(8.dp))
                Text("Copiar sesión")
            }
        }
        if (state.settingsLocked) {
            Text("La sesión queda fija mientras hay recepción o envíos activos.", style = MaterialTheme.typography.caption, color = qetaraMuted)
        }
        Divider(color = qetaraLine)
        Text("Comparte estos datos solo con la persona destinataria.", style = MaterialTheme.typography.caption, color = qetaraMuted)
    }
}

@Composable
private fun SendCard(state: DesktopWorkspaceState, actions: DesktopWorkspaceActions) {
    WorkspaceCard("Archivos para compartir") {
        DesktopFileDropZone(state.filePath, state.isFileDragActive, actions.onChooseFile, enabled = !state.sendingBusy)
        if (state.filePaths.isNotEmpty()) {
            Surface(color = qetaraCanvas, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, qetaraLine)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    state.filePaths.take(3).forEachIndexed { index, path ->
                        if (index > 0) Divider(color = qetaraLine)
                        val selectedFile = File(path)
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            WorkspaceIcon(WorkspaceGlyph.FILE, color = qetaraTeal)
                            Text(selectedFile.name, Modifier.weight(1f), style = MaterialTheme.typography.body2, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatBytes(selectedFile.length()), style = MaterialTheme.typography.caption, color = qetaraMuted)
                        }
                    }
                    if (state.selectedFilesCount > 3) Text("+${state.selectedFilesCount - 3} más", Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.caption, color = qetaraMuted)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.selectedFilesCount == 1) "1 archivo seleccionado" else "${state.selectedFilesCount} archivos seleccionados", Modifier.weight(1f), style = MaterialTheme.typography.caption, color = qetaraMuted)
                TextButton(actions.onClearFiles, enabled = !state.sendingBusy, modifier = Modifier.heightIn(min = 44.dp)) { Text("Quitar todos") }
            }
        }
        Divider(color = qetaraLine)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 340.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Equipo receptor", Modifier.weight(1f), style = MaterialTheme.typography.subtitle2, color = qetaraInk)
                    SearchPeersButton(state, actions)
                }
            } else {
                Column {
                    Text("Equipo receptor", style = MaterialTheme.typography.subtitle2, color = qetaraInk)
                    SearchPeersButton(state, actions)
                }
            }
        }
        if (state.discoveryPhase in listOf(DesktopTaskPhase.STARTING, DesktopTaskPhase.RUNNING)) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = qetaraTeal)
        }
        if (state.peers.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                WorkspaceIcon(WorkspaceGlyph.DEVICES, color = qetaraMuted)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.discoveryStatus, style = MaterialTheme.typography.body2, color = qetaraMuted)
                    Text("Activa Recibir en el otro equipo o escribe su dirección.", style = MaterialTheme.typography.caption, color = qetaraMuted)
                }
            }
        } else {
            state.peers.take(8).forEach { peer ->
                DesktopLanPeerRow(peer, state.host == peer.ip, { actions.onSelectPeer(peer) })
            }
        }
        OutlinedTextField(
            state.host, actions.onHostChange,
            label = { Text("IP o nombre del equipo") },
            placeholder = { Text("Ej. 192.168.1.25") },
            singleLine = true, enabled = !state.sendingBusy,
            modifier = Modifier.fillMaxWidth()
        )
        if (state.sendingBusy) {
            state.sendingProgress?.let { LinearProgressIndicator(it, Modifier.fillMaxWidth(), color = qetaraTeal) }
                ?: LinearProgressIndicator(Modifier.fillMaxWidth(), color = qetaraTeal)
        }
        Text(
            if (state.sendingBusy || state.sendingPhase == DesktopTaskPhase.ERROR || state.sendIssues.isEmpty()) state.sendingStatus
            else state.sendIssues.first(),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.body2,
            color = if (state.sendingPhase == DesktopTaskPhase.ERROR) MaterialTheme.colors.error else qetaraMuted
        )
        if (state.sendingBusy) {
            OutlinedButton(actions.onCancelSend, enabled = state.sendingPhase != DesktopTaskPhase.STOPPING, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp)) { Text("Cancelar envío") }
        } else {
            Button(
                actions.onSend,
                enabled = state.sendIssues.isEmpty(),
                colors = ButtonDefaults.buttonColors(backgroundColor = qetaraTeal, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(if (state.sendingPhase == DesktopTaskPhase.ERROR) "Volver a intentar" else if (state.selectedFilesCount <= 1) "Enviar archivo" else "Enviar ${state.selectedFilesCount} archivos", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun ReceiveCard(state: DesktopWorkspaceState, actions: DesktopWorkspaceActions) {
    WorkspaceCard("Recepción en este equipo") {
        Text(
            if (state.receiverPhase == DesktopTaskPhase.IDLE) "Elige una carpeta y activa la recepción." else state.receiverStatus,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.body2,
            color = if (state.receiverPhase == DesktopTaskPhase.ERROR) MaterialTheme.colors.error else qetaraMuted
        )
        state.receivingProgress?.let { LinearProgressIndicator(it, Modifier.fillMaxWidth(), color = qetaraTeal) }
        if (state.receiverPhase == DesktopTaskPhase.RUNNING) {
            StatusBadge("Sesión activa · " + state.sessionRemaining, true)
            state.localEndpoints.firstOrNull()?.let {
                SelectionContainer { Text(it.address + ":" + state.port, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.body2) }
            }
        }
        OutlinedTextField(state.outputDirectory, actions.onOutputDirectoryChange, label = { Text("Guardar archivos en") }, singleLine = true, enabled = !state.receiverBusy, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(actions.onChooseDirectory, enabled = !state.receiverBusy, modifier = Modifier.heightIn(min = 44.dp)) { Text("Elegir carpeta") }
            TextButton(actions.onOpenDirectory, modifier = Modifier.heightIn(min = 44.dp)) { Text("Abrir carpeta") }
        }
        if (!state.receiverBusy && state.receiverIssues.isNotEmpty()) {
            Text(state.receiverIssues.first(), style = MaterialTheme.typography.body2, color = qetaraMuted)
        }
        Button(
            if (state.receiverBusy) actions.onStopReceiver else actions.onStartReceiver,
            enabled = if (state.receiverBusy) state.receiverPhase == DesktopTaskPhase.RUNNING else state.receiverIssues.isEmpty(),
            colors = ButtonDefaults.buttonColors(backgroundColor = if (state.receiverBusy) qetaraInk else qetaraTeal, contentColor = Color.White),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(when (state.receiverPhase) {
                DesktopTaskPhase.STARTING -> "Activando recepción…"
                DesktopTaskPhase.STOPPING -> "Deteniendo…"
                DesktopTaskPhase.RUNNING -> "Detener recepción"
                else -> "Activar recepción"
            }, fontWeight = FontWeight.SemiBold)
        }
        Text("Los equipos con tu código y PIN podrán enviarte archivos y mensajes mientras esté activa.", style = MaterialTheme.typography.caption, color = qetaraMuted)
    }
}

@Composable
private fun ChatSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(color = qetaraCanvasElevated, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, qetaraLine), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
    }
}

@Composable
private fun ChatConnectionCards(state: DesktopWorkspaceState, actions: DesktopWorkspaceActions) {
    if (!state.credentialsReady) SessionCard(state, actions)
    if (state.receiverPhase != DesktopTaskPhase.RUNNING) {
        WorkspaceCard("Recibir respuestas") {
            Text("Activa la recepción para que puedan responderte en este equipo.", style = MaterialTheme.typography.body2, color = qetaraMuted)
            Button(actions.onStartReceiver, enabled = state.receiverIssues.isEmpty() && !state.receiverBusy, modifier = Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(8.dp)) { Text("Activar recepción") }
        }
    }
}

@Composable
private fun RecentTransfers(entries: List<DesktopTransferEntry>, actions: DesktopWorkspaceActions) {
    WorkspaceCard("Archivos recientes") {
        if (entries.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = qetaraCanvas, shape = RoundedCornerShape(12.dp)) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { WorkspaceIcon(WorkspaceGlyph.FOLDER, color = qetaraMuted) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Aún no hay transferencias", style = MaterialTheme.typography.body2, fontWeight = FontWeight.Medium, color = qetaraInk)
                    Text("Los archivos de esta sesión aparecerán aquí.", style = MaterialTheme.typography.caption, color = qetaraMuted)
                }
            }
        }
        entries.forEachIndexed { index, entry ->
            if (index > 0) Divider(color = qetaraLine)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = qetaraCanvas, shape = RoundedCornerShape(8.dp)) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { WorkspaceIcon(if (entry.incoming) WorkspaceGlyph.RECEIVE else WorkspaceGlyph.SHARE, Modifier.size(20.dp), qetaraTeal) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(entry.fileName, style = MaterialTheme.typography.body2, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = qetaraInk)
                    Text(
                        (if (entry.incoming) "De " else "A ") + entry.peer + " · " + formatBytes(entry.bytes),
                        style = MaterialTheme.typography.caption,
                        color = qetaraMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(entry.timestamp + " · " + entry.outcome, style = MaterialTheme.typography.caption, color = qetaraMuted)
                    entry.path?.let { path ->
                        TextButton({ actions.onOpenReceivedFile(path) }, modifier = Modifier.heightIn(min = 44.dp), contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Ver en carpeta") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, active: Boolean) {
    Surface(shape = RoundedCornerShape(50), color = if (active) qetaraMist else qetaraCanvas) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(if (active) qetaraTeal else qetaraMuted, RoundedCornerShape(50)))
            Text(label, style = MaterialTheme.typography.caption, fontWeight = FontWeight.Medium, color = if (active) qetaraTeal else qetaraMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SearchPeersButton(state: DesktopWorkspaceState, actions: DesktopWorkspaceActions) {
    TextButton(actions.onRefreshPeers, enabled = state.discoveryPhase !in listOf(DesktopTaskPhase.STARTING, DesktopTaskPhase.RUNNING), modifier = Modifier.heightIn(min = 44.dp)) {
        WorkspaceIcon(WorkspaceGlyph.SEARCH, Modifier.size(18.dp), qetaraTeal)
        Spacer(Modifier.width(6.dp))
        Text(if (state.discoveryPhase == DesktopTaskPhase.RUNNING) "Buscando…" else "Buscar equipos")
    }
}

@Composable
private fun SettingsCards(state: DesktopWorkspaceState, actions: DesktopWorkspaceActions, wide: Boolean, onShowLicenses: () -> Unit) {
    if (state.settingsLocked) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            WorkspaceIcon(WorkspaceGlyph.LOCK, Modifier.size(18.dp), qetaraTeal)
            Text("Detén la recepción y termina los envíos para editar los ajustes.", style = MaterialTheme.typography.body2, color = qetaraMuted)
        }
    }
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DeviceSettingsCard(state, actions)
                LocalNetworkCard(state, actions)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ConnectionSettingsCard(state, actions)
                AboutCard(actions, onShowLicenses)
            }
        }
    } else {
        DeviceSettingsCard(state, actions)
        ConnectionSettingsCard(state, actions)
        LocalNetworkCard(state, actions)
        AboutCard(actions, onShowLicenses)
    }
}

@Composable
private fun DeviceSettingsCard(state: DesktopWorkspaceState, actions: DesktopWorkspaceActions) {
    WorkspaceCard("Este equipo") {
        OutlinedTextField(state.deviceName, actions.onDeviceNameChange, label = { Text("Nombre visible") }, singleLine = true, enabled = !state.settingsLocked, modifier = Modifier.fillMaxWidth())
        Text("Huella de identidad", style = MaterialTheme.typography.subtitle2, color = qetaraInk)
        Surface(color = qetaraCanvas, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            SelectionContainer { Text(state.identityFingerprint, Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.caption, color = qetaraInk) }
        }
        Text("Compárala con la solicitud de Android antes de aprobar la conexión.", style = MaterialTheme.typography.body2, color = qetaraMuted)
        TextButton(actions.onCopyFingerprint, modifier = Modifier.heightIn(min = 44.dp)) {
            WorkspaceIcon(WorkspaceGlyph.COPY, Modifier.size(18.dp), qetaraTeal)
            Spacer(Modifier.width(8.dp))
            Text("Copiar huella")
        }
    }
}

@Composable
private fun ConnectionSettingsCard(state: DesktopWorkspaceState, actions: DesktopWorkspaceActions) {
    WorkspaceCard("Conexión") {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 440.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(state.port, actions.onPortChange, label = { Text("Puerto") }, singleLine = true, enabled = !state.settingsLocked, modifier = Modifier.weight(1f))
                    OutlinedTextField(state.retries, actions.onRetriesChange, label = { Text("Reintentos (1–10)") }, singleLine = true, enabled = !state.settingsLocked, modifier = Modifier.weight(1f))
                    OutlinedTextField(state.sessionMinutes, actions.onSessionMinutesChange, label = { Text("Sesión (min)") }, singleLine = true, enabled = !state.settingsLocked, modifier = Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(state.port, actions.onPortChange, label = { Text("Puerto") }, singleLine = true, enabled = !state.settingsLocked, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(state.retries, actions.onRetriesChange, label = { Text("Reintentos (1–10)") }, singleLine = true, enabled = !state.settingsLocked, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(state.sessionMinutes, actions.onSessionMinutesChange, label = { Text("Sesión (minutos)") }, singleLine = true, enabled = !state.settingsLocked, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Text("Usa el mismo puerto en ambos equipos. La recepción caduca en 1–1440 minutos.", style = MaterialTheme.typography.caption, color = qetaraMuted)
        Button(actions.onSaveSettings, enabled = !state.settingsLocked, modifier = Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(8.dp)) { Text("Guardar preferencias") }
        Text("El código, PIN y mensajes no se guardan en las preferencias.", style = MaterialTheme.typography.caption, color = qetaraMuted)
    }
}

@Composable
private fun LocalNetworkCard(state: DesktopWorkspaceState, actions: DesktopWorkspaceActions) {
    WorkspaceCard("Red local") {
        if (state.localEndpoints.isEmpty()) Text("Sin dirección local. Conéctate a Wi-Fi o Ethernet y actualiza la red.", style = MaterialTheme.typography.body2, color = qetaraMuted)
        state.localEndpoints.forEach { endpoint ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(endpoint.label, style = MaterialTheme.typography.caption, color = qetaraMuted)
                SelectionContainer { Text(endpoint.address, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.body2, color = qetaraInk) }
            }
        }
        TextButton(actions.onRefreshPeers, enabled = state.discoveryPhase != DesktopTaskPhase.RUNNING, modifier = Modifier.heightIn(min = 44.dp)) { Text("Actualizar red") }
        Text("El otro equipo debe tener Qetara abierto y la recepción activa. Las redes de invitados pueden impedir la conexión.", style = MaterialTheme.typography.caption, color = qetaraMuted)
    }
}

@Composable
private fun AboutCard(actions: DesktopWorkspaceActions, onShowLicenses: () -> Unit) {
    WorkspaceCard("Acerca de Qetara") {
        Text("Código abierto. Archivos y mensajes directos entre tus equipos.", style = MaterialTheme.typography.body2, color = qetaraMuted)
        Text("Cada archivo verifica su integridad antes de confirmar la recepción.", style = MaterialTheme.typography.caption, color = qetaraMuted)
        OutlinedButton(onShowLicenses, modifier = Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(8.dp)) { Text("Licencias de código abierto") }
        TextButton(actions.onOpenSource, modifier = Modifier.heightIn(min = 44.dp)) { Text("Desarrollador en GitHub") }
    }
}
