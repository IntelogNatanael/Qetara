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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
        !state.session.active -> "Al activarlo, este equipo podrá enviar y recibir solicitudes de Flash durante 30 minutos."
        transfer != null -> transfer.detail
        state.sendPending -> "Preparando el siguiente archivo…"
        state.selectedFiles.isEmpty() -> "Elige uno o varios archivos para enviar, o espera una solicitud del otro equipo."
        selected == null -> "Elige un equipo que tenga Flash activo."
        else -> "El otro equipo verá el nombre y tamaño. Ambos deberán comparar el código antes de transferir."
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Flash", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
                Text(if (state.session.active) "Activo · $minutes min restantes" else "Tus archivos, dos equipos, bajo tu control.", color = qetaraTeal, style = MaterialTheme.typography.body2)
            }
            if (state.session.active || state.starting) {
                OutlinedButton(onClick = { if (state.busy) confirmStop = true else controller.stop() }) { Text("Desactivar") }
            }
            TextButton(onClick = onBack) { Text("Volver") }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(pageScroll).padding(end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!state.session.active) {
                    FlashCard {
                        Text("Activa Flash en ambos equipos", fontWeight = FontWeight.SemiBold)
                        Text("Usa la misma red local. Cada archivo necesita la aprobación de las dos personas tras comparar un código de verificación.", style = MaterialTheme.typography.body2)
                        Text("No cambia tu sesión ni la recepción habitual de Qetara.", style = MaterialTheme.typography.caption, color = qetaraTeal)
                    }
                }
                FlashCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Guardar lo recibido en", Modifier.weight(1f), style = MaterialTheme.typography.subtitle2)
                        TextButton(onChooseDirectory, enabled = !state.session.active && !state.starting) { Text("Cambiar") }
                    }
                    SelectionContainer { Text(state.directory.absolutePath, style = MaterialTheme.typography.caption) }
                    if (state.session.active) Text("La carpeta queda fija mientras Flash esté activo.", style = MaterialTheme.typography.caption, color = qetaraTeal)
                }
                FlashCard {
                    Text("Archivos para compartir", fontWeight = FontWeight.SemiBold)
                    DesktopFileDropZone(state.selectedFile?.absolutePath.orEmpty(), false, onChooseFile, enabled = !state.busy)
                    state.selectedFiles.take(3).forEach { file ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(file.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatBytes(file.length()), style = MaterialTheme.typography.body2)
                        }
                    }
                    if (state.selectedFiles.size > 3) Text("Y ${state.selectedFiles.size - 3} archivo(s) más.", style = MaterialTheme.typography.body2)
                    if (state.selectedFiles.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${state.selectedFiles.size} archivo(s) preparados", style = MaterialTheme.typography.caption, color = qetaraTeal)
                            TextButton(controller::clearFiles, enabled = !state.busy) { Text("Quitar todos") }
                        }
                    }
                }
                if (state.session.active) {
                    FlashCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Equipo receptor", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            TextButton(controller::discover) { Text("Buscar equipos") }
                        }
                        if (peers.isEmpty()) {
                            Text("Aún no hay equipos disponibles. Activa Flash en el otro equipo y pulsa Buscar equipos.", style = MaterialTheme.typography.body2)
                        }
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            peers.forEach { peer ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().selectable(
                                        selected = peer.id == state.selectedPeerId,
                                        enabled = !state.busy,
                                        role = Role.RadioButton,
                                        onClick = { controller.choosePeer(peer) }
                                    ),
                                    color = if (peer.id == state.selectedPeerId) qetaraMist else qetaraCanvasElevated,
                                    shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, qetaraLine)
                                ) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = peer.id == state.selectedPeerId, onClick = null)
                                        Column(Modifier.weight(1f)) {
                                            Text(peer.label.ifBlank { "Equipo Flash" }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text("${peer.address}:${peer.port} · identidad por verificar", style = MaterialTheme.typography.caption)
                                        }
                                    }
                                }
                            }
                        }
                        TextButton({ manual = !manual }) { Text(if (manual) "Ocultar conexión manual" else "No aparece mi equipo") }
                        if (manual) {
                            Text("Dirección de este equipo", fontWeight = FontWeight.SemiBold)
                            if (localAddresses.isEmpty()) {
                                Text("No hay una dirección local disponible. Comprueba tu conexión Wi-Fi o Ethernet.", style = MaterialTheme.typography.body2)
                            } else {
                                localAddresses.distinct().forEach { address ->
                                    SelectionContainer {
                                        Text("$address:${state.session.port}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.body2)
                                    }
                                }
                                Text("La otra persona puede usar esta dirección para buscar tu PC por Flash.", style = MaterialTheme.typography.caption)
                            }
                            Divider(color = qetaraLine)
                            Text("Escribe su dirección local y puerto de Flash. Esto solo busca ese equipo; no envía el archivo.", style = MaterialTheme.typography.body2)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(manualAddress, { manualAddress = it.trim().take(15) }, label = { Text("IP local") }, placeholder = { Text("192.168.1.25") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(manualPort, { manualPort = it.filter(Char::isDigit).take(5) }, label = { Text("Puerto Flash") }, singleLine = true, modifier = Modifier.width(132.dp))
                            }
                            OutlinedButton({ controller.discoverAt(manualAddress, manualPort) }, enabled = manualAddress.isNotBlank()) { Text("Buscar esta dirección") }
                        }
                    }
                }
                state.transfers.asReversed().take(5).forEach { entry ->
                    FlashCard {
                        Text(entry.fileName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text((if (entry.outgoing) "Para " else "De ") + entry.peerLabel + " · " + formatBytes(entry.totalBytes), style = MaterialTheme.typography.caption)
                        if (entry.phase == DesktopFlashPhase.TRANSFERRING) {
                            val progress = if (entry.totalBytes > 0) (entry.transferredBytes.toDouble() / entry.totalBytes).coerceIn(0.0, 1.0).toFloat() else 0f
                            LinearProgressIndicator(progress, Modifier.fillMaxWidth(), color = qetaraTeal)
                            Text("${formatBytes(entry.transferredBytes)} de ${formatBytes(entry.totalBytes)}", style = MaterialTheme.typography.caption)
                        }
                        Text(entry.detail, style = MaterialTheme.typography.body2, color = if (entry.phase == DesktopFlashPhase.FAILED) MaterialTheme.colors.error else qetaraInk)
                        if (entry.phase == DesktopFlashPhase.COMPLETE && entry.file != null) {
                            TextButton({ controller.showFolder(entry.file) }) { Text("Ver en carpeta") }
                        }
                    }
                }
            }
            if (pageScroll.maxValue > 0) VerticalScrollbar(rememberScrollbarAdapter(pageScroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
        Divider(color = qetaraLine)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.status, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = if (state.error) MaterialTheme.colors.error else qetaraInk, style = MaterialTheme.typography.body2)
            if (nextStep != state.status) Text(nextStep, style = MaterialTheme.typography.caption, color = qetaraInk.copy(alpha = .75f))
            when {
                !state.session.active -> Button(
                    { controller.start(deviceName) }, enabled = !state.starting,
                    colors = ButtonDefaults.buttonColors(backgroundColor = qetaraTeal, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(if (state.starting) "Activando Flash…" else "Activar Flash durante 30 minutos") }
                transfer != null -> OutlinedButton(
                    { controller.cancel(transfer.id) }, enabled = !transfer.cancelling,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(if (transfer.cancelling) "Cancelando…" else "Cancelar transferencia") }
                else -> Button(
                    controller::send, enabled = !state.busy && state.selectedFiles.isNotEmpty() && selected != null,
                    colors = ButtonDefaults.buttonColors(backgroundColor = qetaraTeal, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(if (state.sendPending) "Preparando…" else if (state.selectedFiles.size == 1) "Solicitar envío" else "Enviar ${state.selectedFiles.size} archivos") }
            }
        }
    }
    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text("¿Desactivar Flash?") },
        text = { Text("Se detendrán la transferencia y las solicitudes pendientes de Flash. Los archivos ya guardados se conservan.") },
        confirmButton = { TextButton({ confirmStop = false; controller.stop() }) { Text("Desactivar Flash") } },
        dismissButton = { TextButton({ confirmStop = false }) { Text("Seguir en Flash") } }
    )
}

@Composable
private fun FlashCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = qetaraCanvasElevated, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, qetaraLine)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
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
private fun DesktopFlashApprovalDialog(approval: FlashApproval, count: Int, now: Long, decide: (Boolean) -> Unit) {
    var compared by remember(approval) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val remaining = ((approval.expiresAtMs - now).coerceAtLeast(0) + 999) / 1000
    AlertDialog(
        onDismissRequest = { decide(false) },
        modifier = Modifier.widthIn(max = 620.dp),
        title = { Text(if (approval.outgoing) "Verifica antes de enviar" else "Solicitud de archivo por Flash") },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                Column(Modifier.verticalScroll(scroll).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text((if (approval.outgoing) "Enviar a " else "Recibir de ") + approval.peer.label, fontWeight = FontWeight.SemiBold)
                    Text("${approval.peer.address}:${approval.peer.port}", style = MaterialTheme.typography.caption)
                    Text(approval.fileName, fontWeight = FontWeight.SemiBold)
                    Text(formatBytes(approval.totalBytes), style = MaterialTheme.typography.body2)
                    Text("Compara este código con el que muestra la otra persona. Debe coincidir completo en ambos equipos.")
                    Text(approval.verificationCode, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold, color = qetaraTeal)
                    Row(
                        Modifier.fillMaxWidth().toggleable(compared, role = Role.Checkbox, onValueChange = { compared = it }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(compared, onCheckedChange = null)
                        Text("Comparé el código completo y coincide.", Modifier.weight(1f))
                    }
                    Text("Esta aprobación corresponde solo a este archivo. Caduca en $remaining s." + if (count > 1) " Hay $count solicitudes pendientes." else "", style = MaterialTheme.typography.caption)
                }
                if (scroll.maxValue > 0) VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        },
        confirmButton = {
            Button({ decide(true) }, enabled = compared && remaining > 0) {
                Text(if (approval.outgoing) "Coincide · enviar archivo" else "Coincide · aceptar archivo")
            }
        },
        dismissButton = { TextButton({ decide(false) }) { Text("Rechazar") } }
    )
}
