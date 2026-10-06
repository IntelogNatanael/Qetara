package com.example.wifidrop.pc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wifidrop.protocol.flash.FlashApproval
import kotlinx.coroutines.delay

@Composable
private fun flashClock(active: Boolean): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        now = System.currentTimeMillis()
        while (active) { delay(500); now = System.currentTimeMillis() }
    }
    return now
}

@Composable
internal fun DesktopFlashPanel(
    controller: DesktopFlashController,
    deviceName: String,
    localAddresses: List<String>,
    onBack: () -> Unit,
    onChooseFile: () -> Unit,
    onChooseDirectory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = controller.state
    val now = flashClock(state.session.active)
    val peers = state.session.peers.filter { it.expiresAtMs > now }
    val selected = peers.firstOrNull { it.id == state.selectedPeerId }
    val transfer = state.transfers.firstOrNull { it.busy }
    val pageScroll = rememberScrollState()
    var manual by remember { mutableStateOf(false) }
    var manualAddress by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("8989") }
    var confirmStop by remember { mutableStateOf(false) }
    val minutes = ((state.session.expiresAtMs - now).coerceAtLeast(0) + 59_999) / 60_000
    val nextStep = when {
        state.starting -> "Preparando la recepción temporal…"
        !state.session.active -> ""
        transfer != null -> transfer.detail
        state.sendPending -> "Preparando los archivos del lote…"
        state.selectedFiles.isEmpty() -> "Elige archivos para enviar o espera una solicitud."
        selected == null -> "Elige un equipo que tenga Flash activo."
        else -> "Ambos equipos deberán comparar el código antes de transferir."
    }

    val sessionContent: @Composable () -> Unit = {
        FlashCard {
            Text("Conexión temporal", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            Text(
                if (state.session.active) "Envía y recibe archivos en tu red local."
                else "Activa Flash en ambos equipos, conectados a la misma red local.",
                style = MaterialTheme.typography.body2, color = qetaraMuted
            )
            Text("Compara el código y aprueba una vez en cada equipo para todo el lote.", style = MaterialTheme.typography.body2, color = qetaraMuted)
            Divider(color = qetaraLine)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Guardar lo recibido en", Modifier.weight(1f), style = MaterialTheme.typography.subtitle2)
                    TextButton(onChooseDirectory, enabled = !state.session.active && !state.starting) { Text("Cambiar") }
                }
                SelectionContainer { Text(state.directory.absolutePath, style = MaterialTheme.typography.body2, color = qetaraMuted) }
                if (state.session.active) Text("La carpeta queda fija mientras Flash esté activo.", style = MaterialTheme.typography.caption, color = qetaraMuted)
            }
        }
    }
    val filesContent: @Composable () -> Unit = {
        FlashCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Archivos para compartir", Modifier.weight(1f), style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
                if (state.selectedFiles.isNotEmpty()) FlashLabel(state.selectedFiles.size.toString())
            }
            DesktopFileDropZone(state.selectedFile?.absolutePath.orEmpty(), false, onChooseFile, enabled = !state.busy)
            DesktopSelectedFiles(state.selectedFiles, enabled = !state.busy, onClear = controller::clearFiles)
        }
    }
    val receiverContent: @Composable () -> Unit = {
        if (state.session.active) {
            FlashCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Equipo receptor", Modifier.weight(1f), style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
                    TextButton(controller::discover) { Text("Buscar equipos") }
                }
                if (peers.isEmpty()) {
                    Text("No hay equipos disponibles. Activa Flash en el otro equipo y vuelve a buscar.", style = MaterialTheme.typography.body2, color = qetaraMuted)
                }
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    peers.forEach { peer ->
                        val isSelected = peer.id == state.selectedPeerId
                        Surface(
                            modifier = Modifier.fillMaxWidth().selectable(
                                selected = isSelected,
                                enabled = !state.busy,
                                role = Role.RadioButton,
                                onClick = { controller.choosePeer(peer) }
                            ),
                            color = if (isSelected) qetaraMist else qetaraCanvasElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) qetaraTeal else qetaraLine)
                        ) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = isSelected, onClick = null)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(peer.label.ifBlank { "Equipo Flash" }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${peer.address}:${peer.port}", style = MaterialTheme.typography.caption, color = qetaraMuted)
                                    Text("Identidad por verificar", style = MaterialTheme.typography.caption, color = qetaraMuted)
                                }
                            }
                        }
                    }
                }
                TextButton({ manual = !manual }) { Text(if (manual) "Ocultar conexión manual" else "No aparece mi equipo") }
                if (manual) {
                    Divider(color = qetaraLine)
                    Text("Dirección de este equipo", style = MaterialTheme.typography.subtitle2)
                    if (localAddresses.isEmpty()) {
                        Text("No hay una dirección local disponible. Comprueba tu conexión Wi-Fi o Ethernet.", style = MaterialTheme.typography.body2, color = qetaraMuted)
                    } else {
                        localAddresses.distinct().forEach { address ->
                            SelectionContainer {
                                Text("$address:${state.session.port}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.body2)
                            }
                        }
                        Text("La otra persona puede buscar tu PC con esta dirección.", style = MaterialTheme.typography.caption, color = qetaraMuted)
                    }
                    Text("Buscar por dirección", style = MaterialTheme.typography.subtitle2)
                    Text("Escribe la IP local y el puerto del otro equipo. La búsqueda no envía archivos.", style = MaterialTheme.typography.body2, color = qetaraMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(manualAddress, { manualAddress = it.trim().take(15) }, label = { Text("IP local") }, placeholder = { Text("192.168.1.25") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp))
                        OutlinedTextField(manualPort, { manualPort = it.filter(Char::isDigit).take(5) }, label = { Text("Puerto Flash") }, singleLine = true, modifier = Modifier.width(120.dp), shape = RoundedCornerShape(8.dp))
                    }
                    OutlinedButton({ controller.discoverAt(manualAddress, manualPort) }, enabled = manualAddress.isNotBlank(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Buscar esta dirección") }
                }
            }
        }
    }
    val activityContent: @Composable () -> Unit = {
        if (state.transfers.isNotEmpty()) {
            FlashCard {
                Text("Actividad reciente", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
                state.transfers.asReversed().take(5).forEachIndexed { index, entry ->
                    if (index > 0) Divider(color = qetaraLine)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(entry.fileName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text((if (entry.outgoing) "Para " else "De ") + entry.peerLabel + " · " + formatBytes(entry.totalBytes), style = MaterialTheme.typography.caption, color = qetaraMuted)
                        if (entry.phase == DesktopFlashPhase.TRANSFERRING) {
                            val progress = if (entry.totalBytes > 0) (entry.transferredBytes.toDouble() / entry.totalBytes).coerceIn(0.0, 1.0).toFloat() else 0f
                            LinearProgressIndicator(progress, Modifier.fillMaxWidth(), color = qetaraTeal, backgroundColor = qetaraLine)
                            Text("${formatBytes(entry.transferredBytes)} de ${formatBytes(entry.totalBytes)}", style = MaterialTheme.typography.caption, color = qetaraMuted)
                        }
                        Text(entry.detail, style = MaterialTheme.typography.body2, color = if (entry.phase == DesktopFlashPhase.FAILED) MaterialTheme.colors.error else qetaraInk)
                        if (entry.phase == DesktopFlashPhase.COMPLETE && entry.file != null) {
                            TextButton({ controller.showFolder(entry.file) }) { Text("Ver en carpeta") }
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Flash", style = MaterialTheme.typography.h5, fontWeight = FontWeight.SemiBold)
                    FlashLabel(when { state.starting -> "Activando…"; state.session.active -> "Activo · $minutes min restantes"; else -> "Desactivado" }, state.session.active)
                }
                if (state.session.active || state.starting) {
                    OutlinedButton(onClick = { if (state.busy) confirmStop = true else controller.stop() }, shape = RoundedCornerShape(8.dp)) { Text("Desactivar") }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onBack) { Text("Volver a Qetara") }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(pageScroll).padding(end = 14.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (wide) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                filesContent()
                                activityContent()
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                sessionContent()
                                receiverContent()
                            }
                        }
                    } else {
                        sessionContent()
                        filesContent()
                        receiverContent()
                        activityContent()
                    }
                }
                if (pageScroll.maxValue > 0) VerticalScrollbar(rememberScrollbarAdapter(pageScroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
            val statusContent: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.status, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = if (state.error) MaterialTheme.colors.error else qetaraInk, style = MaterialTheme.typography.body2)
                    if (nextStep.isNotBlank() && nextStep != state.status) Text(nextStep, style = MaterialTheme.typography.caption, color = qetaraMuted)
                }
            }
            val actionContent: @Composable () -> Unit = {
                when {
                    !state.session.active -> Button(
                        { controller.start(deviceName) }, enabled = !state.starting,
                        colors = ButtonDefaults.buttonColors(backgroundColor = qetaraTeal, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp), elevation = ButtonDefaults.elevation(0.dp)
                    ) { Text(if (state.starting) "Activando Flash…" else "Activar Flash · 30 min") }
                    transfer != null -> OutlinedButton(
                        { controller.cancel(transfer.id) }, enabled = !transfer.cancelling,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp)
                    ) { Text(if (transfer.cancelling) "Cancelando…" else "Cancelar transferencia") }
                    else -> Button(
                        controller::send, enabled = !state.busy && state.selectedFiles.isNotEmpty() && selected != null,
                        colors = ButtonDefaults.buttonColors(backgroundColor = qetaraTeal, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp), elevation = ButtonDefaults.elevation(0.dp)
                    ) { Text(if (state.sendPending) "Preparando…" else if (state.selectedFiles.size <= 1) "Solicitar envío" else "Enviar ${state.selectedFiles.size} archivos") }
                }
            }
            Surface(color = qetaraCanvasElevated, contentColor = qetaraInk, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, qetaraLine)) {
                if (wide) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { statusContent() }
                        Box(Modifier.width(240.dp)) { actionContent() }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        statusContent()
                        actionContent()
                    }
                }
            }
        }
    }
    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        shape = RoundedCornerShape(16.dp), backgroundColor = qetaraCanvasElevated, contentColor = qetaraInk,
        title = { Text("¿Desactivar Flash?") },
        text = { Text("Se detendrán la transferencia y las solicitudes pendientes de Flash. Los archivos ya guardados se conservan.") },
        confirmButton = { OutlinedButton({ confirmStop = false; controller.stop() }, shape = RoundedCornerShape(8.dp)) { Text("Desactivar Flash") } },
        dismissButton = { TextButton({ confirmStop = false }) { Text("Seguir en Flash") } }
    )
}

@Composable
private fun FlashCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = qetaraCanvasElevated, contentColor = qetaraInk, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, qetaraLine)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun FlashLabel(label: String, active: Boolean = false) {
    Surface(color = if (active) qetaraMist else qetaraCanvas, shape = RoundedCornerShape(8.dp)) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.caption, color = if (active) qetaraTeal else qetaraMuted, fontWeight = FontWeight.Medium)
    }
}

/** Visible even while the main workspace is open; no identity decision is inferred from navigation. */
@Composable
internal fun DesktopFlashApprovalHost(controller: DesktopFlashController) {
    val state = controller.state
    val now = flashClock(state.session.active)
    val pending = state.session.approvals.filter { it.expiresAtMs > now }
    val approval = pending.firstOrNull() ?: return
    DesktopFlashApprovalDialog(approval, pending.size, now) { accepted -> controller.decide(approval, accepted) }
}

@Composable
internal fun DesktopFlashApprovalDialog(approval: FlashApproval, count: Int, now: Long, decide: (Boolean) -> Unit) {
    var compared by remember(approval) { mutableStateOf(false) }
    val scroll = key(approval.requestId) { rememberScrollState() }
    val filesScroll = key(approval.requestId) { rememberScrollState() }
    val fileCount = approval.files.size
    val isBatch = fileCount > 1
    val remaining = ((approval.expiresAtMs - now).coerceAtLeast(0) + 999) / 1000
    AlertDialog(
        onDismissRequest = { decide(false) },
        modifier = Modifier.widthIn(max = 620.dp),
        shape = RoundedCornerShape(16.dp), backgroundColor = qetaraCanvasElevated, contentColor = qetaraInk,
        title = { Text(if (approval.outgoing) "Verifica antes de enviar" else if (isBatch) "Verifica antes de recibir" else "Solicitud de archivo por Flash", fontWeight = FontWeight.SemiBold) },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                Column(Modifier.verticalScroll(scroll).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text((if (approval.outgoing) "Enviar a " else "Recibir de ") + approval.peer.label, fontWeight = FontWeight.SemiBold)
                        Text("${approval.peer.address}:${approval.peer.port}", style = MaterialTheme.typography.caption, color = qetaraMuted)
                    }
                    Surface(color = qetaraCanvas, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, qetaraLine)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("$fileCount ${if (isBatch) "archivos" else "archivo"} · ${formatBytes(approval.totalBytes)} en total",
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                            Box(Modifier.fillMaxWidth().heightIn(max = 144.dp)) {
                                SelectionContainer {
                                    Column(Modifier.fillMaxWidth().verticalScroll(filesScroll).padding(end = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        approval.files.forEach { file ->
                                            Text("${file.fileName} · ${formatBytes(file.totalBytes)}", style = MaterialTheme.typography.body2)
                                        }
                                    }
                                }
                                if (filesScroll.maxValue > 0) VerticalScrollbar(rememberScrollbarAdapter(filesScroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Compara este código con la otra persona. Debe coincidir completo en ambos equipos.", style = MaterialTheme.typography.body2)
                        Surface(color = qetaraMist, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, qetaraLine)) {
                            Text(
                                approval.verificationCode, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp)
                                    .semantics { contentDescription = "Verificación: " + approval.verificationCode.toCharArray().joinToString(" ") },
                                fontFamily = FontFamily.Monospace, fontSize = 28.sp, lineHeight = 36.sp,
                                fontWeight = FontWeight.Bold, color = qetaraTeal, textAlign = TextAlign.Center
                            )
                        }
                    }
                    if (isBatch) {
                        Text("Se aprueban los $fileCount archivos de este lote. Los próximos envíos requieren otra confirmación.", style = MaterialTheme.typography.body2)
                        Text("Si no reconoces algún archivo o la verificación no coincide, rechaza la solicitud.", style = MaterialTheme.typography.caption, color = qetaraMuted)
                    }
                    Text((if (isBatch) "Solicitud válida durante $remaining s." else "Solo se aprueba este archivo. Caduca en $remaining s.") + if (count > 1) " Hay $count solicitudes pendientes." else "", style = MaterialTheme.typography.caption, color = qetaraMuted)
                }
                if (scroll.maxValue > 0) VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        },
        buttons = {
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = if (compared) qetaraMist else qetaraCanvasElevated, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, if (compared) qetaraTeal else qetaraLine)) {
                    Row(
                        Modifier.fillMaxWidth().toggleable(compared, role = Role.Checkbox, onValueChange = { compared = it }).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(compared, onCheckedChange = null)
                        Text("Comparé el código completo y coincide.", Modifier.weight(1f), style = MaterialTheme.typography.body2)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ decide(false) }) { Text("Rechazar") }
                    Button(
                        { decide(true) }, enabled = compared && remaining > 0,
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 44.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = qetaraTeal, contentColor = Color.White), elevation = ButtonDefaults.elevation(0.dp)
                    ) {
                        Text(if (isBatch) {
                            if (approval.outgoing) "Coincide: enviar $fileCount archivos" else "Coincide: recibir $fileCount archivos"
                        } else if (approval.outgoing) "Coincide · enviar archivo" else "Coincide · aceptar archivo")
                    }
                }
            }
        }
    )
}
