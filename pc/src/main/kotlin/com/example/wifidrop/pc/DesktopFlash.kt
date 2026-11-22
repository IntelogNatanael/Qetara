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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wifidrop.protocol.flash.FlashApproval
import com.example.wifidrop.protocol.flash.FlashPeer
import com.example.wifidrop.protocol.flash.FlashText
import com.example.wifidrop.protocol.flash.FLASH_TEXT_MAX_BYTES
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
    modifier: Modifier = Modifier,
    isFileDragActive: Boolean = false
) {
    val state = controller.state
    val now = flashClock(state.session.active)
    val peers = state.session.peers.filter { it.expiresAtMs > now }
    val selected = state.selectedPeer?.takeIf { it.expiresAtMs > now }
    val transfer = state.transfers.firstOrNull { it.busy }
    val recent = state.transfers.asReversed().filterNot { it.busy }
    val pageScroll = rememberScrollState()
    val editorFocus = remember { FocusRequester() }
    var editorFocusRequest by remember { mutableIntStateOf(0) }
    var selectingReceiver by remember { mutableStateOf(false) }
    var sessionDetails by remember { mutableStateOf(false) }
    var reviewingFiles by remember { mutableStateOf(false) }
    var showingHistory by remember { mutableStateOf(false) }
    var openedEntryId by remember { mutableStateOf<String?>(null) }
    var confirmStop by remember { mutableStateOf(false) }
    var draftValue by remember(controller) { mutableStateOf(TextFieldValue(state.textDraft)) }
    var editorFocused by remember { mutableStateOf(false) }
    var retainedSelection by remember(controller) { mutableStateOf(draftValue.selection) }
    var enterHeld by remember { mutableStateOf(false) }
    var shortcutArmed by remember { mutableStateOf(false) }
    var pasteFeedback by remember(controller) { mutableStateOf<String?>(null) }
    val draftBytes = state.textDraft.toByteArray(Charsets.UTF_8).size
    val canEdit = state.session.active && !state.busy
    val canSendText = canEdit && selected != null && state.textDraft.isNotBlank() && draftBytes <= FLASH_TEXT_MAX_BYTES
    val canSendFiles = canEdit && selected != null && state.selectedFiles.isNotEmpty()
    val acceptingFileDrag = isFileDragActive && state.canAttachFiles
    val selectedBytes = remember(state.selectedFiles) { state.selectedFiles.sumOf { it.length() } }
    val minutes = ((state.session.expiresAtMs - now).coerceAtLeast(0) + 59_999) / 60_000
    val nextStep = when {
        state.starting -> "Preparando la recepción temporal…"
        !state.session.active -> "Activa Flash en ambos equipos para enviar y recibir en la misma red local."
        transfer != null -> "La transferencia en curso aparece arriba."
        state.sendPending -> "Preparando el envío…"
        selected == null -> "Elige un equipo receptor para enviar${if (state.textDraft.isNotBlank() || state.selectedFiles.isNotEmpty()) " lo que has preparado" else " texto o archivos"}."
        state.textDraft.isBlank() && state.selectedFiles.isEmpty() -> "Escribe un mensaje, pega texto o adjunta archivos."
        else -> "Antes de transferir, ambos equipos deberán comparar el código."
    }
    LaunchedEffect(state.textDraft) {
        if (draftValue.text != state.textDraft) {
            draftValue = TextFieldValue(state.textDraft, TextRange(state.textDraft.length))
            retainedSelection = draftValue.selection
        }
    }
    // Compose collapses selection during blur. Retain the range from focused frames for
    // toolbar actions, including when Tab moves focus to the Paste button.
    val focusedSelection = if (editorFocused) draftValue.selection else null
    SideEffect {
        focusedSelection?.let { retainedSelection = it }
    }
    LaunchedEffect(editorFocusRequest) {
        if (editorFocusRequest > 0 && canEdit) {
            draftValue = draftValue.copy(selection = retainedSelection)
            editorFocus.requestFocus()
        }
    }
    LaunchedEffect(pasteFeedback) {
        if (pasteFeedback != null) {
            delay(4_000)
            pasteFeedback = null
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 800.dp
        val short = maxHeight < 600.dp
        val composer: @Composable () -> Unit = {
            FlashCard(highlighted = acceptingFileDrag) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    WorkspaceIcon(WorkspaceGlyph.DEVICES, color = qetaraMuted)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(selected?.let { "Para ${it.label.ifBlank { "Equipo Flash" }}" } ?: "Elige un equipo receptor",
                            style = MaterialTheme.typography.subtitle2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (selected != null) "Identidad por verificar" else if (state.session.active) "Con Flash activo en tu red local" else "Disponible al activar Flash",
                            style = MaterialTheme.typography.caption, color = qetaraMuted)
                    }
                    TextButton({
                        selectingReceiver = true
                        controller.discover()
                    }, enabled = canEdit, modifier = Modifier.heightIn(min = 40.dp)) {
                        Text(if (selected == null) "Elegir" else "Cambiar")
                    }
                }
                OutlinedTextField(
                    value = draftValue,
                    onValueChange = { value ->
                        val textChanged = value.text != draftValue.text
                        if (textChanged) controller.updateTextDraft(value.text)
                        if (controller.state.textDraft == value.text) {
                            if (textChanged || !value.selection.collapsed) retainedSelection = value.selection
                            draftValue = value
                            pasteFeedback = null
                        }
                    },
                    label = { Text("Mensaje") },
                    placeholder = { Text(if (state.session.active) "Escribe o pega un mensaje…" else "Activa Flash para escribir o pegar texto") },
                    enabled = canEdit,
                    maxLines = if (short) 5 else 7,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(if (short) 112.dp else 152.dp)
                        .focusRequester(editorFocus)
                        .onFocusChanged {
                            editorFocused = it.isFocused
                            if (!it.isFocused) {
                                enterHeld = false
                                shortcutArmed = false
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.key != Key.Enter) false
                            else if (event.type == KeyEventType.KeyDown) {
                                val shortcut = event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && !event.isShiftPressed
                                if (!enterHeld) {
                                    enterHeld = true
                                    shortcutArmed = shortcut && canSendText
                                }
                                shortcut
                            } else if (event.type == KeyEventType.KeyUp) {
                                // Send on release: OS key repeats cannot submit another text.
                                val send = shortcutArmed && canSendText
                                val consumed = shortcutArmed || event.isCtrlPressed
                                enterHeld = false
                                shortcutArmed = false
                                if (send) controller.sendText()
                                consumed
                            } else false
                        }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onChooseFile, enabled = state.canAttachFiles, shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 44.dp)) {
                        WorkspaceIcon(WorkspaceGlyph.FILE, Modifier.size(18.dp), color = if (state.canAttachFiles) qetaraTeal else qetaraMuted)
                        Spacer(Modifier.width(6.dp))
                        Text("Adjuntar")
                    }
                    TextButton({
                        val cursor = controller.pasteText(retainedSelection.start, retainedSelection.end)
                        pasteFeedback = controller.state.status.takeUnless { controller.state.error }
                        cursor?.let { position ->
                            draftValue = TextFieldValue(controller.state.textDraft, TextRange(position))
                            retainedSelection = draftValue.selection
                            editorFocusRequest++
                        }
                    }, enabled = canEdit, modifier = Modifier.heightIn(min = 44.dp)) { Text("Pegar") }
                    Spacer(Modifier.weight(1f))
                    Button(controller::sendText, enabled = canSendText, shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.heightIn(min = 44.dp), elevation = ButtonDefaults.elevation(0.dp)) { Text("Enviar texto") }
                }
                Text("Ctrl + Enter envía · Enter añade una línea",
                    style = MaterialTheme.typography.caption, color = qetaraMuted)
                if (draftBytes >= FLASH_TEXT_MAX_BYTES * 3 / 4) {
                    Text("$draftBytes / $FLASH_TEXT_MAX_BYTES bytes", style = MaterialTheme.typography.caption, color = qetaraMuted)
                }
                pasteFeedback?.let { feedback ->
                    Text(feedback, style = MaterialTheme.typography.caption, color = qetaraMuted,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (acceptingFileDrag) {
                    Text("Suelta para adjuntar. Después podrás enviar.", style = MaterialTheme.typography.body2, color = qetaraTeal,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (state.selectedFiles.isNotEmpty()) {
                    Surface(color = qetaraCanvas, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, qetaraLine)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${state.selectedFiles.size} ${if (state.selectedFiles.size == 1) "archivo" else "archivos"}",
                                        style = MaterialTheme.typography.subtitle2)
                                    Text("${formatBytes(selectedBytes)} en total", style = MaterialTheme.typography.caption, color = qetaraMuted)
                                }
                                Button(controller::send, enabled = canSendFiles, shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.heightIn(min = 44.dp), elevation = ButtonDefaults.elevation(0.dp)) {
                                    Text(if (state.selectedFiles.size == 1) "Enviar archivo" else "Enviar ${state.selectedFiles.size} archivos")
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton({ reviewingFiles = true }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Revisar lista") }
                                Spacer(Modifier.weight(1f))
                                TextButton(controller::clearFiles, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Quitar todos") }
                            }
                            state.selectedFiles.take(3).forEach { file ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    WorkspaceIcon(WorkspaceGlyph.FILE, Modifier.size(18.dp), color = qetaraMuted)
                                    Text(file.name, Modifier.weight(1f), style = MaterialTheme.typography.body2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatBytes(file.length()), style = MaterialTheme.typography.caption, color = qetaraMuted)
                                }
                            }
                            if (state.selectedFiles.size > 3) Text("+${state.selectedFiles.size - 3} más · revisa la lista completa", style = MaterialTheme.typography.caption, color = qetaraMuted)
                        }
                    }
                } else if (!acceptingFileDrag) {
                    Text("Adjunta uno o varios archivos, o arrástralos a esta ventana.", style = MaterialTheme.typography.body2, color = qetaraMuted)
                }
                Text("El texto y los archivos se envían por separado.", style = MaterialTheme.typography.caption, color = qetaraMuted)
                Divider(color = qetaraLine)
                Text(nextStep, style = MaterialTheme.typography.body2, color = qetaraMuted)
            }
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (wide) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        WorkspaceIcon(WorkspaceGlyph.FLASH, color = qetaraTeal)
                        Text("Flash", style = MaterialTheme.typography.h5, fontWeight = FontWeight.SemiBold)
                        FlashLabel(when { state.starting -> "Activando…"; state.session.active -> "Activo · $minutes min"; else -> "Desactivado" }, state.session.active)
                    }
                } else {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            WorkspaceIcon(WorkspaceGlyph.FLASH, color = qetaraTeal)
                            Text("Flash", style = MaterialTheme.typography.h5, fontWeight = FontWeight.SemiBold)
                        }
                        Text(when { state.starting -> "Activando…"; state.session.active -> "Activo · $minutes min restantes"; else -> "Desactivado" },
                            style = MaterialTheme.typography.caption, color = if (state.session.active) qetaraTeal else qetaraMuted)
                    }
                }
                TextButton({ sessionDetails = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(if (wide) "Detalles de sesión" else "Detalles") }
                if (state.session.active || state.starting) {
                    OutlinedButton({ if (state.busy) confirmStop = true else controller.stop() }, shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 40.dp)) { Text("Desactivar") }
                } else {
                    Button({ controller.start(deviceName) }, shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 44.dp),
                        elevation = ButtonDefaults.elevation(0.dp)) { Text("Activar · 30 min") }
                }
                TextButton(onBack, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.semantics { contentDescription = "Volver a Qetara" }) {
                    Text(if (wide) "Volver a Qetara" else "Volver")
                }
            }
            if (state.error) {
                Surface(color = MaterialTheme.colors.error.copy(alpha = .07f), shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colors.error.copy(alpha = .24f))) {
                    Text(state.status, Modifier.fillMaxWidth().padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colors.error, style = MaterialTheme.typography.body2)
                }
            }
            if (transfer != null) {
                FlashActiveTransfer(transfer) { controller.cancel(transfer.id) }
            } else if (state.sendPending) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Preparando el envío…", style = MaterialTheme.typography.body2, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = qetaraTeal, backgroundColor = qetaraLine)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize().verticalScroll(pageScroll).padding(end = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (wide) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.weight(1f)) { composer() }
                            Box(Modifier.width(300.dp)) {
                                FlashRecentActivity(recent, controller, { showingHistory = true }, { openedEntryId = it.id })
                            }
                        }
                    } else {
                        composer()
                        FlashRecentActivity(recent, controller, { showingHistory = true }, { openedEntryId = it.id })
                    }
                }
                if (pageScroll.maxValue > 0) VerticalScrollbar(rememberScrollbarAdapter(pageScroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
    if (selectingReceiver) FlashReceiverDialog(controller, peers) {
        selectingReceiver = false
        editorFocusRequest++
    }
    if (sessionDetails) FlashSessionDialog(state, deviceName, localAddresses, onChooseDirectory) { sessionDetails = false }
    if (reviewingFiles) DesktopSelectedFilesDialog(state.selectedFiles) { reviewingFiles = false }
    if (showingHistory) {
        FlashModal("Actividad de Flash", onDismiss = { showingHistory = false }) {
            if (recent.isEmpty()) Text("Todavía no hay transferencias finalizadas.", color = qetaraMuted)
            recent.forEachIndexed { index, entry ->
                if (index > 0) Divider(color = qetaraLine)
                FlashActivityEntry(entry, controller, { openedEntryId = entry.id })
            }
        }
    }
    state.transfers.firstOrNull { it.id == openedEntryId }?.let { entry ->
        FlashEntryDialog(entry, controller) { openedEntryId = null }
    }
    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        modifier = Modifier.widthIn(max = 520.dp),
        shape = RoundedCornerShape(16.dp), backgroundColor = qetaraCanvasElevated, contentColor = qetaraInk,
        title = { Text("¿Desactivar Flash?", fontWeight = FontWeight.SemiBold) },
        text = { Text("Se detendrán la transferencia y las solicitudes pendientes de Flash. Los archivos ya guardados se conservan.", style = MaterialTheme.typography.body1) },
        buttons = {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton({ confirmStop = false }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Seguir en Flash") }
                OutlinedButton({ confirmStop = false; controller.stop() }, shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 44.dp)) { Text("Desactivar Flash") }
            }
        }
    )
}

@Composable
private fun FlashCard(highlighted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = qetaraCanvasElevated, contentColor = qetaraInk, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, if (highlighted) qetaraTeal else qetaraLine)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun FlashLabel(label: String, active: Boolean = false) {
    Surface(color = if (active) qetaraMist else qetaraCanvas, shape = RoundedCornerShape(8.dp)) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.caption,
            color = if (active) qetaraTeal else qetaraMuted, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FlashActiveTransfer(entry: DesktopFlashTransfer, onCancel: () -> Unit) {
    Surface(color = qetaraMist, contentColor = qetaraInk, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, qetaraLine)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (entry.textBody != null) { if (entry.outgoing) "Enviando texto" else "Recibiendo texto" } else entry.fileName,
                        style = MaterialTheme.typography.subtitle2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text((if (entry.outgoing) "Para " else "De ") + entry.peerLabel + " · " + entry.detail,
                        style = MaterialTheme.typography.caption, color = qetaraMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                OutlinedButton(onCancel, enabled = !entry.cancelling, shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 40.dp)) {
                    Text(if (entry.cancelling) "Cancelando…" else "Cancelar")
                }
            }
            if (entry.phase == DesktopFlashPhase.TRANSFERRING) {
                val progress = if (entry.totalBytes > 0) (entry.transferredBytes.toDouble() / entry.totalBytes).coerceIn(0.0, 1.0).toFloat() else 0f
                LinearProgressIndicator(progress, Modifier.fillMaxWidth(), color = qetaraTeal, backgroundColor = qetaraLine)
                Text("${formatBytes(entry.transferredBytes)} de ${formatBytes(entry.totalBytes)}", style = MaterialTheme.typography.caption, color = qetaraMuted)
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = qetaraTeal, backgroundColor = qetaraLine)
            }
        }
    }
}

@Composable
private fun FlashRecentActivity(entries: List<DesktopFlashTransfer>, controller: DesktopFlashController, onShowAll: () -> Unit, onOpen: (DesktopFlashTransfer) -> Unit) {
    FlashCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Actividad reciente", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            if (entries.size > 3) TextButton(onShowAll, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Ver ${entries.size}") }
        }
        if (entries.isEmpty()) {
            Text("Tus envíos y lo recibido aparecerán aquí.", style = MaterialTheme.typography.body2, color = qetaraMuted)
            Text("Podrás copiar los textos y abrir la carpeta de los archivos.", style = MaterialTheme.typography.caption, color = qetaraMuted)
        }
        entries.take(3).forEachIndexed { index, entry ->
            if (index > 0) Divider(color = qetaraLine)
            FlashActivityEntry(entry, controller, { onOpen(entry) })
        }
    }
}

private fun flashEntryTitle(entry: DesktopFlashTransfer): String = if (entry.textBody != null) {
    if (!entry.outgoing) "Texto recibido" else if (entry.phase == DesktopFlashPhase.COMPLETE) "Texto enviado" else "Texto para enviar"
} else entry.fileName

@Composable
private fun FlashActivityEntry(entry: DesktopFlashTransfer, controller: DesktopFlashController, onOpen: () -> Unit) {
    var copied by remember(entry.id) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(4_000)
            copied = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            WorkspaceIcon(if (entry.textBody == null) WorkspaceGlyph.FILE else WorkspaceGlyph.CHAT, Modifier.size(18.dp), color = qetaraMuted)
            Text(flashEntryTitle(entry), style = MaterialTheme.typography.subtitle2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text((if (entry.outgoing) "Para " else "De ") + entry.peerLabel + " · " + formatBytes(entry.totalBytes),
            style = MaterialTheme.typography.caption, color = qetaraMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        entry.textBody?.let { Text(it, style = MaterialTheme.typography.body2, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Text(when (entry.phase) {
            DesktopFlashPhase.COMPLETE -> "Completado"
            DesktopFlashPhase.CANCELLED -> "Cancelado"
            DesktopFlashPhase.FAILED -> "No se pudo transferir"
            else -> entry.detail
        }, style = MaterialTheme.typography.caption, color = if (entry.phase == DesktopFlashPhase.FAILED) MaterialTheme.colors.error else qetaraMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (entry.textBody != null) TextButton({ controller.copyText(entry.id); copied = !controller.state.error }, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.heightIn(min = 36.dp)) {
                WorkspaceIcon(WorkspaceGlyph.COPY, Modifier.size(16.dp), color = qetaraTeal)
                Spacer(Modifier.width(4.dp))
                Text(if (copied) "Copiado" else "Copiar", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            TextButton(onOpen, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.heightIn(min = 36.dp)) { Text(if (entry.textBody != null) "Ver texto" else "Detalles") }
            if (entry.textBody == null && entry.phase == DesktopFlashPhase.COMPLETE && entry.file != null) {
                TextButton({ controller.showFolder(entry.file) }, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.heightIn(min = 36.dp)) { Text("Ver en carpeta") }
            }
        }
    }
}

@Composable
private fun FlashModal(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss, modifier = Modifier.widthIn(max = 620.dp),
        shape = RoundedCornerShape(16.dp), backgroundColor = qetaraCanvasElevated, contentColor = qetaraInk,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
                if (scroll.maxValue > 0) VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        },
        confirmButton = { TextButton(onDismiss, modifier = Modifier.heightIn(min = 44.dp)) { Text("Cerrar") } }
    )
}

@Composable
private fun FlashReceiverDialog(controller: DesktopFlashController, peers: List<FlashPeer>, onDismiss: () -> Unit) {
    val state = controller.state
    var manual by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8989") }
    FlashModal("Elegir equipo receptor", onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Activa Flash en el otro equipo y conéctalo a la misma red local.", Modifier.weight(1f), style = MaterialTheme.typography.body2, color = qetaraMuted)
            OutlinedButton(controller::discover, enabled = state.session.active, shape = RoundedCornerShape(8.dp)) { Text("Buscar") }
        }
        if (peers.isEmpty()) {
            Surface(color = qetaraCanvas, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, qetaraLine)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No hay equipos disponibles", style = MaterialTheme.typography.subtitle2)
                    Text("Comprueba que ambos tengan Flash activo y vuelve a buscar.", style = MaterialTheme.typography.body2, color = qetaraMuted)
                }
            }
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            peers.forEach { peer ->
                val selected = peer == state.selectedPeer
                Surface(
                    modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = state.session.active && !state.busy, role = Role.RadioButton,
                        onClick = { controller.choosePeer(peer); onDismiss() }),
                    color = if (selected) qetaraMist else qetaraCanvasElevated, shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (selected) qetaraTeal else qetaraLine)
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, onClick = null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(peer.label.ifBlank { "Equipo Flash" }, style = MaterialTheme.typography.subtitle2)
                            Text("${peer.address}:${peer.port}", style = MaterialTheme.typography.caption, color = qetaraMuted)
                            Text("Identidad por verificar", style = MaterialTheme.typography.caption, color = qetaraMuted)
                        }
                    }
                }
            }
        }
        Text("Elegir un equipo no verifica su identidad. Compararás el código antes de cada envío.", style = MaterialTheme.typography.caption, color = qetaraMuted)
        Divider(color = qetaraLine)
        TextButton({ manual = !manual }) { Text(if (manual) "Ocultar búsqueda por IP" else "No aparece mi equipo · buscar por IP") }
        if (manual) {
            Text("La IP y el puerto están en Detalles de sesión del otro equipo. La búsqueda no envía texto ni archivos.", style = MaterialTheme.typography.body2, color = qetaraMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(address, { address = it.trim().take(15) }, label = { Text("IP local") }, placeholder = { Text("192.168.1.25") },
                    enabled = state.session.active, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp))
                OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Puerto") },
                    enabled = state.session.active, singleLine = true, modifier = Modifier.width(112.dp), shape = RoundedCornerShape(8.dp))
            }
            OutlinedButton({ controller.discoverAt(address, port) }, enabled = state.session.active && address.isNotBlank(), shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 44.dp)) { Text("Buscar esta dirección") }
        }
        if (state.error) Text(state.status, color = MaterialTheme.colors.error, style = MaterialTheme.typography.body2,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun FlashSessionDialog(state: DesktopFlashUiState, deviceName: String, localAddresses: List<String>, onChooseDirectory: () -> Unit, onDismiss: () -> Unit) {
    FlashModal("Detalles de sesión", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(deviceName.ifBlank { "Qetara PC" }, style = MaterialTheme.typography.subtitle1)
            Text("Conexión temporal para enviar y recibir en la misma red local.", style = MaterialTheme.typography.body2, color = qetaraMuted)
        }
        Divider(color = qetaraLine)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Guardar lo recibido en", Modifier.weight(1f), style = MaterialTheme.typography.subtitle2)
                TextButton(onChooseDirectory, enabled = !state.session.active && !state.starting) { Text("Cambiar") }
            }
            SelectionContainer { Text(state.directory.absolutePath, style = MaterialTheme.typography.body2) }
            Text(if (state.session.active) "La carpeta queda fija mientras Flash esté activo." else "Puedes cambiar la carpeta antes de activar Flash.", style = MaterialTheme.typography.caption, color = qetaraMuted)
            Text("El texto recibido también se guarda como .txt en esta carpeta.", style = MaterialTheme.typography.caption, color = qetaraMuted)
        }
        Divider(color = qetaraLine)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Dirección de este equipo", style = MaterialTheme.typography.subtitle2)
            when {
                !state.session.active -> Text("Activa Flash para consultar la dirección de esta sesión.", style = MaterialTheme.typography.body2, color = qetaraMuted)
                localAddresses.isEmpty() -> Text("No hay una dirección local disponible. Comprueba tu conexión Wi-Fi o Ethernet.", style = MaterialTheme.typography.body2, color = qetaraMuted)
                else -> {
                    localAddresses.distinct().forEach { address ->
                        SelectionContainer { Text("$address:${state.session.port}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.body2) }
                    }
                    Text("La otra persona puede usarla en Buscar por IP.", style = MaterialTheme.typography.caption, color = qetaraMuted)
                }
            }
        }
        Divider(color = qetaraLine)
        Text("Cada envío se confirma en ambos equipos. Un lote se aprueba completo; los próximos envíos requieren otra confirmación.", style = MaterialTheme.typography.body2, color = qetaraMuted)
    }
}

@Composable
private fun FlashEntryDialog(entry: DesktopFlashTransfer, controller: DesktopFlashController, onDismiss: () -> Unit) {
    var copyAttempted by remember(entry.id) { mutableStateOf(false) }
    FlashModal(if (entry.textBody != null) flashEntryTitle(entry) else "Detalle de transferencia", onDismiss) {
        if (entry.textBody == null) SelectionContainer { Text(entry.fileName, style = MaterialTheme.typography.subtitle1) }
        Text((if (entry.outgoing) "Para " else "De ") + entry.peerLabel + " · " + formatBytes(entry.totalBytes), style = MaterialTheme.typography.body2, color = qetaraMuted)
        Text(entry.detail, style = MaterialTheme.typography.body2, color = if (entry.phase == DesktopFlashPhase.FAILED) MaterialTheme.colors.error else qetaraMuted)
        entry.textBody?.let { body ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ controller.copyText(entry.id); copyAttempted = true }, shape = RoundedCornerShape(8.dp)) { Text("Copiar texto") }
                if (copyAttempted) Text(controller.state.status, style = MaterialTheme.typography.caption,
                    color = if (controller.state.error) MaterialTheme.colors.error else qetaraMuted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Divider(color = qetaraLine)
            SelectionContainer { Text(body, style = MaterialTheme.typography.body1) }
        }
        if (entry.phase == DesktopFlashPhase.COMPLETE && entry.file != null && (entry.textBody == null || !entry.outgoing)) {
            TextButton({ controller.showFolder(entry.file) }) { Text("Ver en carpeta") }
        }
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
    val isText = !isBatch && approval.files.singleOrNull()?.let { FlashText.isTextFileName(it.fileName) && it.totalBytes <= FLASH_TEXT_MAX_BYTES } == true
    val remaining = ((approval.expiresAtMs - now).coerceAtLeast(0) + 999) / 1000
    AlertDialog(
        onDismissRequest = { decide(false) },
        modifier = Modifier.widthIn(max = 620.dp),
        shape = RoundedCornerShape(16.dp), backgroundColor = qetaraCanvasElevated, contentColor = qetaraInk,
        title = { Text(if (approval.outgoing) "Verifica antes de enviar" else if (isText) "Solicitud de texto por Flash" else if (isBatch) "Verifica antes de recibir" else "Solicitud de archivo por Flash", fontWeight = FontWeight.SemiBold) },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                Column(Modifier.verticalScroll(scroll).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text((if (approval.outgoing) "Enviar a " else "Recibir de ") + approval.peer.label, fontWeight = FontWeight.SemiBold)
                        Text("${approval.peer.address}:${approval.peer.port}", style = MaterialTheme.typography.caption, color = qetaraMuted)
                    }
                    Surface(color = qetaraCanvas, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, qetaraLine)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text((if (isText) "Texto" else "$fileCount ${if (isBatch) "archivos" else "archivo"}") + " · ${formatBytes(approval.totalBytes)} en total",
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                            if (!isText) Box(Modifier.fillMaxWidth().heightIn(max = 144.dp)) {
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
                    Text((if (isBatch) "Solicitud válida durante $remaining s." else if (isText) "Solo se aprueba este texto. Caduca en $remaining s." else "Solo se aprueba este archivo. Caduca en $remaining s.") + if (count > 1) " Hay $count solicitudes pendientes." else "", style = MaterialTheme.typography.caption, color = qetaraMuted)
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
                        } else if (isText) { if (approval.outgoing) "Coincide · enviar texto" else "Coincide · aceptar texto" }
                        else if (approval.outgoing) "Coincide · enviar archivo" else "Coincide · aceptar archivo")
                    }
                }
            }
        }
    )
}
