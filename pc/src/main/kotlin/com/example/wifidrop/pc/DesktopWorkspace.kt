package com.example.wifidrop.pc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

private enum class WorkspaceTab(val label: String) {
    SHARE("Compartir"), RECEIVE("Recibir"), CHAT("Mensajes"), ACTIVITY("Actividad"), SETTINGS("Ajustes")
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
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                QetaraLogoMark(Modifier.size(46.dp), qetaraInk)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Qetara", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
                    Text("Cerca de ti. Bajo tu control.", style = MaterialTheme.typography.caption, color = qetaraTeal)
                }
                OutlinedButton(actions.onOpenFlash, enabled = !flashVisible, modifier = Modifier.padding(end = 12.dp)) {
                    Text((if (state.flashActive) "Flash activo" else "Flash") + if (state.flashApprovals > 0) " (${state.flashApprovals})" else "")
                }
                Column(horizontalAlignment = Alignment.End) {
                    StatusBadge(
                        if (state.flashActive) {
                            if (state.receiverPhase == DesktopTaskPhase.RUNNING) "Recepción habitual activa" else "Recepción habitual desactivada"
                        } else if (state.receiverPhase == DesktopTaskPhase.RUNNING) "Disponible para recibir" else "Recepción desactivada",
                        state.receiverPhase == DesktopTaskPhase.RUNNING
                    )
                    Text(
                        state.deviceName.ifBlank { "Este equipo" },
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(top = 5.dp),
                        color = qetaraInk.copy(alpha = .7f)
                    )
                }
            }
            if (flashVisible) {
                Box(Modifier.weight(1f).fillMaxWidth()) { flashContent() }
                return@Column
            }
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                backgroundColor = qetaraCanvas,
                contentColor = qetaraInk,
                edgePadding = 0.dp,
                divider = { Divider(color = qetaraLine) }
            ) {
                WorkspaceTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label + if (tab == WorkspaceTab.CHAT && state.unreadMessages > 0) " (" + state.unreadMessages + ")" else "", fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
            state.notice?.let { notice ->
                Surface(
                    color = qetaraMist,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(notice, Modifier.weight(1f), style = MaterialTheme.typography.body2)
                        TextButton(actions.onDismissNotice) { Text("Cerrar") }
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 20.dp)) {
                val wide = maxWidth >= 930.dp
                when (selectedTab) {
                    WorkspaceTab.SHARE -> Column(
                        Modifier.fillMaxSize().verticalScroll(pageScroll).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        PageHeading("Tus archivos, de un equipo al otro.", "Comparte con Android o PC en tu red local. Sin subir tus archivos a una nube.")
                        if (wide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                Column(Modifier.weight(1.45f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    WorkspaceTab.RECEIVE -> Column(
                        Modifier.fillMaxSize().verticalScroll(pageScroll).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        PageHeading("Un lugar para lo que te envían.", "Activa la recepción y usa la misma sesión en el otro equipo.")
                        if (wide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                Column(Modifier.weight(1.45f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    WorkspaceTab.CHAT -> Column(
                        Modifier.fillMaxSize().verticalScroll(pageScroll).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PageHeading("La conversación también se queda cerca.", "Envía texto y archivos a equipos que usan tu mismo código y PIN.")
                        if (state.receiverPhase != DesktopTaskPhase.RUNNING) {
                            WorkspaceCard("Activa la recepción para poder responderte") {
                                Text("Puedes enviar con una sesión válida. Para recibir respuestas en este equipo, activa Recibir.", style = MaterialTheme.typography.body2)
                                Button(actions.onStartReceiver, enabled = state.receiverIssues.isEmpty() && !state.receiverBusy) {
                                    Text("Activar recepción")
                                }
                            }
                        }
                        if (!state.credentialsReady) SessionCard(state, actions)
                        chatContent()
                    }
                    WorkspaceTab.ACTIVITY -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        PageHeading("Lo que está pasando.", "La actividad de esta sesión permanece en este equipo y se borra al cerrar Qetara.")
                        activityContent(Modifier.weight(1f).fillMaxWidth())
                    }
                    WorkspaceTab.SETTINGS -> Column(
                        Modifier.fillMaxSize().verticalScroll(pageScroll).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        PageHeading("A tu manera.", "Qetara recuerda tus preferencias. El código, PIN y mensajes no se guardan en las preferencias.")
                        WorkspaceCard("Este equipo") {
                            OutlinedTextField(state.deviceName, actions.onDeviceNameChange, label = { Text("Nombre visible") }, singleLine = true, enabled = !state.settingsLocked, modifier = Modifier.fillMaxWidth())
                            Text("Usa un nombre que reconozcas en el otro equipo.", style = MaterialTheme.typography.caption)
                            Text("Huella de este equipo", style = MaterialTheme.typography.subtitle2)
                            SelectionContainer { Text(state.identityFingerprint, fontFamily = FontFamily.Monospace) }
                            Text("Compara esta huella con la solicitud que aparece en Android antes de aprobar la conexión.", style = MaterialTheme.typography.body2)
                            TextButton(actions.onCopyFingerprint) { Text("Copiar huella") }
                            if (state.settingsLocked) Text("Detén la recepción y termina el envío para cambiar los ajustes.", color = qetaraTeal, style = MaterialTheme.typography.body2)
                        }
                        WorkspaceCard("Conexión") {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(state.port, actions.onPortChange, label = { Text("Puerto") }, singleLine = true, enabled = !state.settingsLocked, modifier = Modifier.weight(1f))
                                OutlinedTextField(state.retries, actions.onRetriesChange, label = { Text("Reintentos (1–10)") }, singleLine = true, enabled = !state.settingsLocked, modifier = Modifier.weight(1f))
                                OutlinedTextField(state.sessionMinutes, actions.onSessionMinutesChange, label = { Text("Sesión (minutos)") }, singleLine = true, enabled = !state.settingsLocked, modifier = Modifier.weight(1f))
                            }
                            Text("El puerto debe coincidir en ambos equipos. La recepción caduca entre 1 y 1440 minutos, según el valor elegido.", style = MaterialTheme.typography.body2, color = qetaraInk.copy(alpha = .75f))
                            Button(actions.onSaveSettings, enabled = !state.settingsLocked) { Text("Guardar preferencias") }
                        }
                        WorkspaceCard("Tu red local") {
                            if (state.localEndpoints.isEmpty()) {
                                Text("No hay una dirección local disponible. Conéctate a Wi-Fi o Ethernet y pulsa Actualizar red.")
                            }
                            state.localEndpoints.forEach { endpoint ->
                                SelectionContainer { Text(endpoint.label + " · " + endpoint.address, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.body2) }
                            }
                            TextButton(actions.onRefreshPeers, enabled = state.discoveryPhase != DesktopTaskPhase.RUNNING) { Text("Actualizar red") }
                            Text("Si no aparece otro equipo, verifica que Qetara esté abierto, la recepción activa y ambos estén en la misma red. Una red de invitados puede impedir que se vean.", style = MaterialTheme.typography.body2)
                        }
                        WorkspaceCard("Hecho para compartir") {
                            Text("Qetara es software de código abierto. Puedes estudiar cómo funciona, adaptarlo y colaborar.", style = MaterialTheme.typography.body2)
                            Text("Los archivos y mensajes viajan directamente entre los equipos. La transferencia verifica la integridad del archivo antes de confirmar su recepción.", style = MaterialTheme.typography.body2)
                            OutlinedButton(onClick = { showLicenses = true }) { Text("Licencias de código abierto") }
                            TextButton(actions.onOpenSource) { Text("Conocer al desarrollador en GitHub") }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (selectedTab != WorkspaceTab.ACTIVITY && pageScroll.maxValue > 0) {
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(pageScroll),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )
                }
            }
            Text(
                "RED LOCAL  ·  CÓDIGO ABIERTO  ·  EXPERIENCE LAB",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.overline,
                color = qetaraInk.copy(alpha = .62f)
            )
        }
    }
}

@Composable
private fun PageHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.h5, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.body2, color = qetaraInk.copy(alpha = .75f))
    }
}

@Composable
private fun WorkspaceCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = qetaraCanvasElevated, border = BorderStroke(1.dp, qetaraLine)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SessionCard(state: DesktopWorkspaceState, actions: DesktopWorkspaceActions) {
    var showPin by remember { mutableStateOf(false) }
    WorkspaceCard("Conecta los dos equipos") {
        Text("Crea una sesión aquí o escribe el código y PIN que muestra el equipo receptor.", style = MaterialTheme.typography.body2, color = qetaraInk.copy(alpha = .75f))
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
            trailingIcon = { TextButton({ showPin = !showPin }) { Text(if (showPin) "Ocultar" else "Ver") } },
            modifier = Modifier.fillMaxWidth()
        )
        if (!state.settingsLocked) {
            OutlinedButton(actions.onCreateSession, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.credentialsReady) "Crear otra sesión" else "Crear una sesión")
            }
        }
        if (state.credentialsReady) {
            Text(
                if (state.receiverPhase == DesktopTaskPhase.RUNNING) "Recepción activa · " + state.sessionRemaining
                else "Código y PIN listos. Activa Recibir si este equipo será el receptor.",
                style = MaterialTheme.typography.body2,
                color = qetaraTeal
            )
            if (!state.receiverBusy) {
                Button(actions.onStartReceiver, enabled = state.receiverIssues.isEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Activar recepción aquí") }
            }
            TextButton(actions.onCopySession, modifier = Modifier.fillMaxWidth()) { Text("Copiar datos para conectar") }
        } else {
            Text("Usa exactamente el mismo código y PIN en ambos equipos.", style = MaterialTheme.typography.caption, color = qetaraInk.copy(alpha = .75f))
        }
        if (state.settingsLocked) {
            Text("La sesión queda fija mientras la recepción o un envío están activos.", style = MaterialTheme.typography.caption, color = qetaraInk.copy(alpha = .75f))
        }
        Divider(color = qetaraLine)
        Text("Comparte estos datos solo con la persona que recibirá tus archivos.", style = MaterialTheme.typography.caption, color = qetaraInk.copy(alpha = .75f))
    }
}

@Composable
private fun SendCard(state: DesktopWorkspaceState, actions: DesktopWorkspaceActions) {
    WorkspaceCard("1. Elige qué compartir") {
        DesktopFileDropZone(state.filePath, state.isFileDragActive, actions.onChooseFile, enabled = !state.sendingBusy)
        if (state.filePaths.isNotEmpty()) {
            state.filePaths.take(3).forEach { path ->
                val selectedFile = File(path)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedFile.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatBytes(selectedFile.length()), style = MaterialTheme.typography.body2, color = qetaraTeal)
                }
            }
            if (state.selectedFilesCount > 3) Text("Y ${state.selectedFilesCount - 3} archivo(s) más.", style = MaterialTheme.typography.body2)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${state.selectedFilesCount} archivo(s) listos", Modifier.weight(1f), style = MaterialTheme.typography.caption, color = qetaraTeal)
                TextButton(actions.onClearFiles, enabled = !state.sendingBusy) { Text("Quitar todos") }
            }
        }
        Divider(color = qetaraLine)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("2. Elige el equipo receptor", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            TextButton(actions.onRefreshPeers, enabled = state.discoveryPhase !in listOf(DesktopTaskPhase.STARTING, DesktopTaskPhase.RUNNING)) {
                Text(if (state.discoveryPhase == DesktopTaskPhase.RUNNING) "Buscando…" else "Buscar equipos")
            }
        }
        if (state.discoveryPhase in listOf(DesktopTaskPhase.STARTING, DesktopTaskPhase.RUNNING)) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = qetaraTeal)
        }
        if (state.peers.isEmpty()) {
            Text(state.discoveryStatus, style = MaterialTheme.typography.body2, color = qetaraInk.copy(alpha = .75f))
            Text("Abre Qetara y activa Recibir en el otro equipo. También puedes escribir su IP.", style = MaterialTheme.typography.caption, color = qetaraInk.copy(alpha = .75f))
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
            color = if (state.sendingPhase == DesktopTaskPhase.ERROR) MaterialTheme.colors.error else qetaraInk.copy(alpha = .8f)
        )
        if (state.sendingBusy) {
            OutlinedButton(actions.onCancelSend, enabled = state.sendingPhase != DesktopTaskPhase.STOPPING, modifier = Modifier.fillMaxWidth()) { Text("Cancelar envío") }
        } else {
            Button(
                actions.onSend,
                enabled = state.sendIssues.isEmpty(),
                colors = ButtonDefaults.buttonColors(backgroundColor = qetaraTeal, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(if (state.sendingPhase == DesktopTaskPhase.ERROR) "Volver a intentar" else if (state.selectedFilesCount == 1) "Enviar archivo" else "Enviar ${state.selectedFilesCount} archivos", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ReceiveCard(state: DesktopWorkspaceState, actions: DesktopWorkspaceActions) {
    WorkspaceCard("Recibir en este equipo") {
        Text(
            state.receiverStatus,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.body1,
            color = if (state.receiverPhase == DesktopTaskPhase.ERROR) MaterialTheme.colors.error else qetaraInk
        )
        state.receivingProgress?.let { LinearProgressIndicator(it, Modifier.fillMaxWidth(), color = qetaraTeal) }
        if (state.receiverPhase == DesktopTaskPhase.RUNNING) {
            StatusBadge("Sesión activa · " + state.sessionRemaining, true)
            state.localEndpoints.firstOrNull()?.let {
                SelectionContainer { Text("Dirección: " + it.address + ":" + state.port, fontFamily = FontFamily.Monospace) }
            }
        }
        OutlinedTextField(state.outputDirectory, actions.onOutputDirectoryChange, label = { Text("Guardar archivos en") }, singleLine = true, enabled = !state.receiverBusy, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(actions.onChooseDirectory, enabled = !state.receiverBusy) { Text("Elegir carpeta") }
            TextButton(actions.onOpenDirectory) { Text("Abrir carpeta") }
        }
        if (!state.receiverBusy && state.receiverIssues.isNotEmpty()) {
            Text(state.receiverIssues.first(), style = MaterialTheme.typography.body2, color = qetaraInk.copy(alpha = .75f))
        }
        Button(
            if (state.receiverBusy) actions.onStopReceiver else actions.onStartReceiver,
            enabled = if (state.receiverBusy) state.receiverPhase == DesktopTaskPhase.RUNNING else state.receiverIssues.isEmpty(),
            colors = ButtonDefaults.buttonColors(backgroundColor = if (state.receiverBusy) qetaraInk else qetaraTeal, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(when (state.receiverPhase) {
                DesktopTaskPhase.STARTING -> "Activando recepción…"
                DesktopTaskPhase.STOPPING -> "Deteniendo…"
                DesktopTaskPhase.RUNNING -> "Detener recepción"
                else -> "Activar recepción"
            }, fontWeight = FontWeight.Bold)
        }
        Text("Mientras esté activa, los equipos con tu código y PIN podrán enviarte archivos y mensajes.", style = MaterialTheme.typography.caption, color = qetaraInk.copy(alpha = .75f))
    }
}

@Composable
private fun RecentTransfers(entries: List<DesktopTransferEntry>, actions: DesktopWorkspaceActions) {
    WorkspaceCard(if (entries.isEmpty()) "Todo listo para empezar" else "Archivos recientes") {
        if (entries.isEmpty()) {
            Text("Aquí aparecerán los archivos de esta sesión. Las copias recibidas se conservan en tu carpeta de destino.", style = MaterialTheme.typography.body2, color = qetaraInk.copy(alpha = .75f))
        }
        entries.forEachIndexed { index, entry ->
            if (index > 0) Divider(color = qetaraLine)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (entry.incoming) "Recibido de " else "Enviado a ") + entry.peer + " · " + formatBytes(entry.bytes),
                        style = MaterialTheme.typography.caption,
                        color = qetaraInk.copy(alpha = .75f)
                    )
                    Text(entry.timestamp + " · " + entry.outcome, style = MaterialTheme.typography.caption, color = qetaraTeal)
                }
                entry.path?.let { path ->
                    TextButton({ actions.onOpenReceivedFile(path) }) { Text("Ver en carpeta") }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, active: Boolean) {
    Surface(shape = RoundedCornerShape(50), color = if (active) Color(0xFFE3F2ED) else Color(0xFFF0EAE3)) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.caption, fontWeight = FontWeight.SemiBold, color = if (active) qetaraTeal else qetaraInk.copy(alpha = .8f))
    }
}
