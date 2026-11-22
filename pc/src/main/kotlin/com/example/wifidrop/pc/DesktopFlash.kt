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
import java.io.File

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
    var pasteFeedback by remember(controller) { mutableStateOf<DesktopFlashText?>(null) }
    var statusFeedback by remember(controller) { mutableStateOf<DesktopFlashText?>(null) }
    val draftBytes = state.textDraft.toByteArray(Charsets.UTF_8).size
    val canEdit = state.session.active && !state.busy
    val canSendText = canEdit && selected != null && state.textDraft.isNotBlank() && draftBytes <= FLASH_TEXT_MAX_BYTES
    val canSendFiles = canEdit && selected != null && state.selectedFiles.isNotEmpty()
    val acceptingFileDrag = isFileDragActive && state.canAttachFiles
    val selectedBytes = remember(state.selectedFiles) { state.selectedFiles.sumOf { it.length() } }
    val seconds = ((state.session.expiresAtMs - now).coerceAtLeast(0) + 999) / 1000
    val remaining = "%d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
    val sendActions = desktopFlashSendActions(state.textDraft, state.selectedFiles.size)
    val hasText = sendActions == DesktopFlashSendActions.MESSAGE || sendActions == DesktopFlashSendActions.BOTH
    val hasFiles = state.selectedFiles.isNotEmpty()
    val nextStep = when {
        state.starting -> flashCopy("Activando Flash…", "Turning on Flash…")
        !state.session.active -> flashCopy("Activa Flash en ambos equipos conectados a la misma red.", "Turn on Flash on both devices on the same network.")
        selected == null -> flashCopy("Elige un receptor. Tu borrador y adjuntos se conservan.", "Choose a receiver. Your draft and attachments are kept.")
        !hasText && !hasFiles -> flashCopy("Escribe un mensaje o arrastra archivos a esta ventana.", "Write a message or drop files into this window.")
        sendActions == DesktopFlashSendActions.BOTH -> flashCopy("Mensaje y archivos se envían por separado. Verifica cada envío.", "Message and files are sent separately. Verify each transfer.")
        else -> flashCopy("Compara el código en ambos equipos antes de enviar.", "Compare the code on both devices before sending.")
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
    LaunchedEffect(state.statusText, state.statusRevision) {
        statusFeedback = state.statusText
        delay(6_000)
        statusFeedback = null
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // Reserve the side column only when the composer can still keep a comfortable width.
        val wide = maxWidth >= 1040.dp
        val short = maxHeight < 650.dp
        val composer: @Composable () -> Unit = {
            FlashCard(highlighted = acceptingFileDrag) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    WorkspaceIcon(WorkspaceGlyph.DEVICES, color = qetaraMuted)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(selected?.let { flashCopy("Para ${it.label.ifBlank { flashCopy("Equipo Flash", "Flash device") }}", "To ${it.label.ifBlank { flashCopy("Equipo Flash", "Flash device") }}") }
                            ?: flashCopy("Elige un equipo receptor", "Choose a receiver"),
                            style = MaterialTheme.typography.subtitle2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (selected != null) flashCopy("Identidad por verificar", "Identity not yet verified")
                            else flashCopy("En la misma red local", "On the same local network"),
                            style = MaterialTheme.typography.caption, color = qetaraMuted)
                    }
                    TextButton({ selectingReceiver = true; controller.discover() }, enabled = canEdit,
                        modifier = Modifier.heightIn(min = 40.dp)) {
                        Text(if (selected == null) flashCopy("Elegir", "Choose") else flashCopy("Cambiar", "Change"))
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
                    label = { Text(flashCopy("Mensaje", "Message")) },
                    placeholder = { Text(if (state.session.active) flashCopy("Escribe o pega un mensaje…", "Write or paste a message…")
                        else flashCopy("Activa Flash para escribir o pegar texto", "Turn on Flash to write or paste text")) },
                    enabled = canEdit, maxLines = if (short) 4 else 6,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(if (short) 108.dp else 144.dp)
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
                if (hasFiles) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${flashFileCount(state.selectedFiles.size)} · ${formatBytes(selectedBytes)}",
                                Modifier.weight(1f), style = MaterialTheme.typography.subtitle2)
                            TextButton({ reviewingFiles = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text(flashCopy("Revisar", "Review"))
                            }
                        }
                        state.selectedFiles.take(if (short) 1 else 2).forEach { file ->
                            FlashAttachmentRow(file, !state.busy) { controller.removeFile(file) }
                        }
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val wrapActions = maxWidth < 560.dp && sendActions == DesktopFlashSendActions.BOTH
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onChooseFile, enabled = state.canAttachFiles, shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 44.dp)) {
                                Text(flashCopy("Adjuntar", "Attach"))
                            }
                            TextButton({
                                val cursor = controller.pasteText(retainedSelection.start, retainedSelection.end)
                                pasteFeedback = controller.state.statusText.takeUnless { controller.state.error }
                                cursor?.let { position ->
                                    draftValue = TextFieldValue(controller.state.textDraft, TextRange(position))
                                    retainedSelection = draftValue.selection
                                    editorFocusRequest++
                                }
                            }, enabled = canEdit, modifier = Modifier.heightIn(min = 44.dp)) { Text(flashCopy("Pegar", "Paste")) }
                            Spacer(Modifier.weight(1f))
                            if (!wrapActions) FlashSendButtons(sendActions, state.selectedFiles.size, canSendText, canSendFiles,
                                controller::sendText, controller::send)
                        }
                        if (wrapActions) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                            FlashSendButtons(sendActions, state.selectedFiles.size, canSendText, canSendFiles,
                                controller::sendText, controller::send)
                        }
                    }
                }
                // This one reserved line doubles as clipboard/drop feedback; it never pushes the editor.
                Text(when {
                    acceptingFileDrag -> flashCopy("Suelta para adjuntar. Después podrás enviar.", "Drop to attach. You can send afterwards.")
                    pasteFeedback != null -> pasteFeedback?.resolve().orEmpty()
                    draftBytes >= FLASH_TEXT_MAX_BYTES * 3 / 4 -> "$draftBytes / $FLASH_TEXT_MAX_BYTES bytes"
                    hasText -> flashCopy("Ctrl + Enter envía el mensaje · Enter añade una línea", "Ctrl + Enter sends the message · Enter adds a line")
                    else -> flashCopy("Adjunta archivos o arrástralos a esta ventana.", "Attach files or drop them into this window.")
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.caption, color = if (acceptingFileDrag) qetaraTeal else qetaraMuted)
                Divider(color = qetaraLine)
                // Reserve the progress/error area inside the composer instead of moving it on every event.
                Box(Modifier.fillMaxWidth().heightIn(min = 78.dp), contentAlignment = Alignment.CenterStart) {
                    when {
                        transfer != null -> FlashActiveTransfer(transfer) { controller.cancel(transfer.id) }
                        state.sendPending -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(flashCopy("Preparando el envío…", "Preparing transfer…"), style = MaterialTheme.typography.body2)
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = qetaraTeal, backgroundColor = qetaraLine)
                        }
                        else -> Text(if (state.error) state.status else statusFeedback?.resolve() ?: nextStep,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.body2,
                            color = if (state.error) MaterialTheme.colors.error else qetaraMuted)
                    }
                }
            }
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                WorkspaceIcon(WorkspaceGlyph.FLASH, color = qetaraTeal)
                Column(Modifier.weight(1f)) {
                    Text("Flash", style = MaterialTheme.typography.h6, fontWeight = FontWeight.SemiBold)
                    Text(when { state.starting -> flashCopy("Activando…", "Starting…")
                        state.session.active -> flashCopy("Activo · $remaining", "Active · $remaining")
                        else -> flashCopy("Desactivado", "Off") },
                        style = MaterialTheme.typography.caption, color = if (state.session.active) qetaraTeal else qetaraMuted)
                }
                TextButton({ sessionDetails = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(flashCopy("Detalles", "Details")) }
                if (state.session.active || state.starting) {
                    OutlinedButton({ if (state.busy) confirmStop = true else controller.stop() }, shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 40.dp)) {
                        Text(flashCopy("Desactivar", "Turn off"))
                    }
                } else {
                    Button({ controller.start(deviceName) }, shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 44.dp),
                        elevation = ButtonDefaults.elevation(0.dp)) { Text(flashCopy("Activar · 30 min", "Turn on · 30 min")) }
                }
                TextButton(onBack, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.semantics { contentDescription = flashCopy("Volver a Qetara", "Back to Qetara") }) {
                    Text(flashCopy("Volver", "Back"))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize().verticalScroll(pageScroll).padding(end = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (wide) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.weight(1f)) { composer() }
                            Box(Modifier.width(260.dp)) {
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
    if (reviewingFiles) FlashModal(flashCopy("Archivos adjuntos", "Attachments"), { reviewingFiles = false }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${flashFileCount(state.selectedFiles.size)} · ${formatBytes(selectedBytes)}", Modifier.weight(1f))
            TextButton(controller::clearFiles, enabled = !state.busy) { Text(flashCopy("Quitar todos", "Remove all")) }
        }
        state.selectedFiles.forEach { file -> FlashAttachmentRow(file, !state.busy) { controller.removeFile(file) } }
        if (state.selectedFiles.isEmpty()) Text(flashCopy("No hay archivos adjuntos.", "No attachments."), color = qetaraMuted)
    }
    if (showingHistory) {
        FlashModal(flashCopy("Actividad de Flash", "Flash activity"), onDismiss = { showingHistory = false }) {
            if (recent.isEmpty()) Text(flashCopy("Todavía no hay transferencias finalizadas.", "No completed transfers yet."), color = qetaraMuted)
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
        title = { Text(flashCopy("¿Desactivar Flash?", "Turn off Flash?"), fontWeight = FontWeight.SemiBold) },
        text = { Text(flashCopy("Se detendrán la transferencia y las solicitudes pendientes de Flash. Los archivos ya guardados se conservan.", "The current transfer and pending Flash requests will stop. Files already saved are kept."), style = MaterialTheme.typography.body1) },
        buttons = {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton({ confirmStop = false }, modifier = Modifier.heightIn(min = 44.dp)) { Text(flashCopy("Seguir en Flash", "Keep Flash on")) }
                OutlinedButton({ confirmStop = false; controller.stop() }, shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 44.dp)) { Text(flashCopy("Desactivar Flash", "Turn off Flash")) }
            }
        }
    )
}

@Composable
private fun FlashAttachmentRow(file: File, enabled: Boolean, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        WorkspaceIcon(WorkspaceGlyph.FILE, Modifier.size(18.dp), color = qetaraMuted)
        Text(file.name, Modifier.weight(1f), style = MaterialTheme.typography.body2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatBytes(file.length()), style = MaterialTheme.typography.caption, color = qetaraMuted)
        TextButton(onRemove, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = flashCopy("Quitar ${file.name}", "Remove ${file.name}") }) {
            Text(flashCopy("Quitar", "Remove"))
        }
    }
}

@Composable
private fun RowScope.FlashSendButtons(
    actions: DesktopFlashSendActions, fileCount: Int, canSendText: Boolean, canSendFiles: Boolean,
    onSendText: () -> Unit, onSendFiles: () -> Unit
) {
    if (actions == DesktopFlashSendActions.BOTH) {
        OutlinedButton(onSendText, enabled = canSendText, shape = RoundedCornerShape(8.dp),
            modifier = Modifier.heightIn(min = 44.dp)) { Text(flashCopy("Enviar mensaje", "Send message")) }
    } else if (actions == DesktopFlashSendActions.MESSAGE) {
        Button(onSendText, enabled = canSendText, shape = RoundedCornerShape(8.dp),
            modifier = Modifier.heightIn(min = 44.dp), elevation = ButtonDefaults.elevation(0.dp)) {
            Text(flashCopy("Enviar mensaje", "Send message"))
        }
    }
    if (actions == DesktopFlashSendActions.FILES || actions == DesktopFlashSendActions.BOTH) {
        Button(onSendFiles, enabled = canSendFiles, shape = RoundedCornerShape(8.dp),
            modifier = Modifier.heightIn(min = 44.dp), elevation = ButtonDefaults.elevation(0.dp)) {
            Text(if (fileCount == 1) flashCopy("Enviar 1 archivo", "Send 1 file") else flashCopy("Enviar $fileCount archivos", "Send $fileCount files"))
        }
    }
}

@Composable
private fun FlashCard(highlighted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = qetaraCanvasElevated, contentColor = qetaraInk, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, if (highlighted) qetaraTeal else qetaraLine)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
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
                    Text(if (entry.textBody != null) { if (entry.outgoing) flashCopy("Enviando texto", "Sending message") else flashCopy("Recibiendo texto", "Receiving message") } else entry.fileName,
                        style = MaterialTheme.typography.subtitle2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text((if (entry.outgoing) flashCopy("Para ", "To ") else flashCopy("De ", "From ")) + entry.peerLabel + " · " + entry.detail,
                        style = MaterialTheme.typography.caption, color = qetaraMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                OutlinedButton(onCancel, enabled = !entry.cancelling, shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 40.dp)) {
                    Text(if (entry.cancelling) flashCopy("Cancelando…", "Cancelling…") else flashCopy("Cancelar", "Cancel"))
                }
            }
            if (entry.phase == DesktopFlashPhase.TRANSFERRING) {
                val progress = if (entry.totalBytes > 0) (entry.transferredBytes.toDouble() / entry.totalBytes).coerceIn(0.0, 1.0).toFloat() else 0f
                LinearProgressIndicator(progress, Modifier.fillMaxWidth(), color = qetaraTeal, backgroundColor = qetaraLine)
                Text(flashCopy("${formatBytes(entry.transferredBytes)} de ${formatBytes(entry.totalBytes)}", "${formatBytes(entry.transferredBytes)} of ${formatBytes(entry.totalBytes)}"), style = MaterialTheme.typography.caption, color = qetaraMuted)
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = qetaraTeal, backgroundColor = qetaraLine)
            }
        }
    }
}

@Composable
private fun FlashRecentActivity(entries: List<DesktopFlashTransfer>, controller: DesktopFlashController, onShowAll: () -> Unit, onOpen: (DesktopFlashTransfer) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ expanded = !expanded }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(flashCopy("Actividad reciente (${entries.size})", "Recent activity (${entries.size})"),
                    Modifier.weight(1f).semantics { heading() }, textAlign = TextAlign.Start)
                Text(if (expanded) "−" else "+", modifier = Modifier.semantics {
                    contentDescription = if (expanded) flashCopy("Contraer actividad", "Collapse activity") else flashCopy("Expandir actividad", "Expand activity")
                })
            }
            if (entries.isNotEmpty()) TextButton(onShowAll, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(flashCopy("Ver todo", "View all"))
            }
        }
        if (expanded) {
            if (entries.isEmpty()) Text(flashCopy("Tus envíos y lo recibido aparecerán aquí.", "Sent and received items will appear here."), style = MaterialTheme.typography.body2, color = qetaraMuted)
            entries.take(3).forEachIndexed { index, entry ->
                if (index > 0) Divider(color = qetaraLine)
                FlashActivityEntry(entry, controller, { onOpen(entry) })
            }
        }
    }
}

private fun flashEntryTitle(entry: DesktopFlashTransfer): String = if (entry.textBody != null) {
    if (!entry.outgoing) flashCopy("Texto recibido", "Received message") else if (entry.phase == DesktopFlashPhase.COMPLETE) flashCopy("Texto enviado", "Sent message") else flashCopy("Texto para enviar", "Message to send")
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
        Text((if (entry.outgoing) flashCopy("Para ", "To ") else flashCopy("De ", "From ")) + entry.peerLabel + " · " + formatBytes(entry.totalBytes),
            style = MaterialTheme.typography.caption, color = qetaraMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        entry.textBody?.let { Text(it, style = MaterialTheme.typography.body2, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Text(when (entry.phase) {
            DesktopFlashPhase.COMPLETE -> flashCopy("Completado", "Completed")
            DesktopFlashPhase.CANCELLED -> flashCopy("Cancelado", "Cancelled")
            DesktopFlashPhase.FAILED -> flashCopy("No se pudo transferir", "Transfer failed")
            else -> entry.detail
        }, style = MaterialTheme.typography.caption, color = if (entry.phase == DesktopFlashPhase.FAILED) MaterialTheme.colors.error else qetaraMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (entry.textBody != null) TextButton({ controller.copyText(entry.id); copied = !controller.state.error }, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.heightIn(min = 36.dp)) {
                WorkspaceIcon(WorkspaceGlyph.COPY, Modifier.size(16.dp), color = qetaraTeal)
                Spacer(Modifier.width(4.dp))
                Text(if (copied) flashCopy("Copiado", "Copied") else flashCopy("Copiar", "Copy"), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            TextButton(onOpen, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.heightIn(min = 36.dp)) { Text(if (entry.textBody != null) flashCopy("Ver texto", "View text") else flashCopy("Detalles", "Details")) }
            if (entry.textBody == null && entry.phase == DesktopFlashPhase.COMPLETE && entry.file != null) {
                TextButton({ controller.showFolder(entry.file) }, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.heightIn(min = 36.dp)) { Text(flashCopy("Ver en carpeta", "Show in folder")) }
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
        confirmButton = { TextButton(onDismiss, modifier = Modifier.heightIn(min = 44.dp)) { Text(flashCopy("Cerrar", "Close")) } }
    )
}

@Composable
private fun FlashReceiverDialog(controller: DesktopFlashController, peers: List<FlashPeer>, onDismiss: () -> Unit) {
    val state = controller.state
    var manual by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8989") }
    FlashModal(flashCopy("Elegir equipo receptor", "Choose a receiver"), onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(flashCopy("Activa Flash en el otro equipo y conéctalo a la misma red local.", "Turn on Flash on the other device and connect it to the same local network."), Modifier.weight(1f), style = MaterialTheme.typography.body2, color = qetaraMuted)
            OutlinedButton(controller::discover, enabled = state.session.active, shape = RoundedCornerShape(8.dp)) { Text(flashCopy("Buscar", "Search")) }
        }
        if (peers.isEmpty()) {
            Surface(color = qetaraCanvas, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, qetaraLine)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(flashCopy("No hay equipos disponibles", "No devices available"), style = MaterialTheme.typography.subtitle2)
                    Text(flashCopy("Comprueba que ambos tengan Flash activo y vuelve a buscar.", "Check that Flash is active on both devices, then search again."), style = MaterialTheme.typography.body2, color = qetaraMuted)
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
                            Text(peer.label.ifBlank { flashCopy("Equipo Flash", "Flash device") }, style = MaterialTheme.typography.subtitle2)
                            Text("${peer.address}:${peer.port}", style = MaterialTheme.typography.caption, color = qetaraMuted)
                            Text(flashCopy("Identidad por verificar", "Identity not yet verified"), style = MaterialTheme.typography.caption, color = qetaraMuted)
                        }
                    }
                }
            }
        }
        Text(flashCopy("Elegir un equipo no verifica su identidad. Compararás el código antes de cada envío.", "Choosing a device does not verify its identity. Compare the code before each transfer."), style = MaterialTheme.typography.caption, color = qetaraMuted)
        Divider(color = qetaraLine)
        TextButton({ manual = !manual }) { Text(if (manual) flashCopy("Ocultar búsqueda por IP", "Hide IP search") else flashCopy("No aparece mi equipo · buscar por IP", "Device missing? Search by IP")) }
        if (manual) {
            Text(flashCopy("La IP y el puerto están en Detalles de sesión del otro equipo. La búsqueda no envía texto ni archivos.", "Find the IP and port in the other device’s session details. Searching does not send text or files."), style = MaterialTheme.typography.body2, color = qetaraMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(address, { address = it.trim().take(15) }, label = { Text(flashCopy("IP local", "Local IP")) }, placeholder = { Text("192.168.1.25") },
                    enabled = state.session.active, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp))
                OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text(flashCopy("Puerto", "Port")) },
                    enabled = state.session.active, singleLine = true, modifier = Modifier.width(112.dp), shape = RoundedCornerShape(8.dp))
            }
            OutlinedButton({ controller.discoverAt(address, port) }, enabled = state.session.active && address.isNotBlank(), shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 44.dp)) { Text(flashCopy("Buscar esta dirección", "Search this address")) }
        }
        if (state.error) Text(state.status, color = MaterialTheme.colors.error, style = MaterialTheme.typography.body2,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
internal fun FlashSessionDialog(state: DesktopFlashUiState, deviceName: String, localAddresses: List<String>, onChooseDirectory: () -> Unit, onDismiss: () -> Unit) {
    FlashModal(flashCopy("Detalles de sesión", "Session details"), onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(deviceName.ifBlank { "Qetara PC" }, style = MaterialTheme.typography.subtitle1)
            Text(flashCopy("Conexión temporal para enviar y recibir en la misma red local.", "A temporary connection to send and receive on the same local network."), style = MaterialTheme.typography.body2, color = qetaraMuted)
        }
        Divider(color = qetaraLine)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(flashCopy("Guardar lo recibido en", "Save received items in"), Modifier.weight(1f), style = MaterialTheme.typography.subtitle2)
                TextButton(onChooseDirectory, enabled = !state.session.active && !state.starting) { Text(flashCopy("Cambiar", "Change")) }
            }
            SelectionContainer { Text(state.directory.absolutePath, style = MaterialTheme.typography.body2) }
            Text(if (state.session.active) flashCopy("La carpeta queda fija mientras Flash esté activo.", "The folder cannot change while Flash is active.") else flashCopy("Puedes cambiar la carpeta antes de activar Flash.", "You can change the folder before turning on Flash."), style = MaterialTheme.typography.caption, color = qetaraMuted)
            Text(flashCopy("El texto recibido también se guarda como .txt en esta carpeta.", "Received text is also saved as .txt in this folder."), style = MaterialTheme.typography.caption, color = qetaraMuted)
        }
        Divider(color = qetaraLine)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(flashCopy("Dirección de este equipo", "This device’s address"), style = MaterialTheme.typography.subtitle2)
            when {
                !state.session.active -> Text(flashCopy("Activa Flash para consultar la dirección de esta sesión.", "Turn on Flash to see this session’s address."), style = MaterialTheme.typography.body2, color = qetaraMuted)
                localAddresses.isEmpty() -> Text(flashCopy("No hay una dirección local disponible. Comprueba tu conexión Wi-Fi o Ethernet.", "No local address available. Check your Wi-Fi or Ethernet connection."), style = MaterialTheme.typography.body2, color = qetaraMuted)
                else -> {
                    localAddresses.distinct().forEach { address ->
                        SelectionContainer { Text("$address:${state.session.port}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.body2) }
                    }
                    Text(flashCopy("La otra persona puede usarla en Buscar por IP.", "The other person can use it in Search by IP."), style = MaterialTheme.typography.caption, color = qetaraMuted)
                }
            }
        }
        Divider(color = qetaraLine)
        Text(flashCopy("Cada envío se confirma en ambos equipos. Un lote se aprueba completo; los próximos envíos requieren otra confirmación.", "Both devices confirm each transfer. Approval covers the entire batch; later transfers require a new confirmation."), style = MaterialTheme.typography.body2, color = qetaraMuted)
    }
}

@Composable
private fun FlashEntryDialog(entry: DesktopFlashTransfer, controller: DesktopFlashController, onDismiss: () -> Unit) {
    var copyAttempted by remember(entry.id) { mutableStateOf(false) }
    FlashModal(if (entry.textBody != null) flashEntryTitle(entry) else flashCopy("Detalle de transferencia", "Transfer details"), onDismiss) {
        if (entry.textBody == null) SelectionContainer { Text(entry.fileName, style = MaterialTheme.typography.subtitle1) }
        Text((if (entry.outgoing) flashCopy("Para ", "To ") else flashCopy("De ", "From ")) + entry.peerLabel + " · " + formatBytes(entry.totalBytes), style = MaterialTheme.typography.body2, color = qetaraMuted)
        Text(entry.detail, style = MaterialTheme.typography.body2, color = if (entry.phase == DesktopFlashPhase.FAILED) MaterialTheme.colors.error else qetaraMuted)
        entry.textBody?.let { body ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ controller.copyText(entry.id); copyAttempted = true }, shape = RoundedCornerShape(8.dp)) { Text(flashCopy("Copiar texto", "Copy text")) }
                if (copyAttempted) Text(controller.state.status, style = MaterialTheme.typography.caption,
                    color = if (controller.state.error) MaterialTheme.colors.error else qetaraMuted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Divider(color = qetaraLine)
            SelectionContainer { Text(body, style = MaterialTheme.typography.body1) }
        }
        if (entry.phase == DesktopFlashPhase.COMPLETE && entry.file != null && (entry.textBody == null || !entry.outgoing)) {
            TextButton({ controller.showFolder(entry.file) }) { Text(flashCopy("Ver en carpeta", "Show in folder")) }
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
        title = { Text(if (approval.outgoing) flashCopy("Verifica antes de enviar", "Verify before sending") else if (isText) flashCopy("Solicitud de texto por Flash", "Flash message request") else if (isBatch) flashCopy("Verifica antes de recibir", "Verify before receiving") else flashCopy("Solicitud de archivo por Flash", "Flash file request"), fontWeight = FontWeight.SemiBold) },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                Column(Modifier.verticalScroll(scroll).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text((if (approval.outgoing) flashCopy("Enviar a ", "Send to ") else flashCopy("Recibir de ", "Receive from ")) + approval.peer.label, fontWeight = FontWeight.SemiBold)
                        Text("${approval.peer.address}:${approval.peer.port}", style = MaterialTheme.typography.caption, color = qetaraMuted)
                    }
                    Surface(color = qetaraCanvas, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, qetaraLine)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text((if (isText) flashCopy("Texto", "Text") else flashFileCount(fileCount)) + flashCopy(" · ${formatBytes(approval.totalBytes)} en total", " · ${formatBytes(approval.totalBytes)} total"),
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
                        Text(flashCopy("Compara este código con la otra persona. Debe coincidir completo en ambos equipos.", "Compare this code with the other person. The complete code must match on both devices."), style = MaterialTheme.typography.body2)
                        Surface(color = qetaraMist, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, qetaraLine)) {
                            Text(
                                approval.verificationCode, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp)
                                    .semantics { contentDescription = flashCopy("Verificación: ", "Verification: ") + approval.verificationCode.toCharArray().joinToString(" ") },
                                fontFamily = FontFamily.Monospace, fontSize = 28.sp, lineHeight = 36.sp,
                                fontWeight = FontWeight.Bold, color = qetaraTeal, textAlign = TextAlign.Center
                            )
                        }
                    }
                    if (isBatch) {
                        Text(flashCopy("Se aprueban los $fileCount archivos de este lote. Los próximos envíos requieren otra confirmación.", "Approval covers all $fileCount files in this batch. Later transfers require a new confirmation."), style = MaterialTheme.typography.body2)
                        Text(flashCopy("Si no reconoces algún archivo o la verificación no coincide, rechaza la solicitud.", "If you do not recognize a file or the code does not match, reject the request."), style = MaterialTheme.typography.caption, color = qetaraMuted)
                    }
                    Text((if (isBatch) flashCopy("Solicitud válida durante $remaining s.", "Request valid for $remaining s.") else if (isText) flashCopy("Solo se aprueba este texto. Caduca en $remaining s.", "Approval covers this text only. Expires in $remaining s.") else flashCopy("Solo se aprueba este archivo. Caduca en $remaining s.", "Approval covers this file only. Expires in $remaining s.")) + if (count > 1) flashCopy(" Hay $count solicitudes pendientes.", " There are $count pending requests.") else "", style = MaterialTheme.typography.caption, color = qetaraMuted)
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
                        Text(flashCopy("Comparé el código completo y coincide.", "I compared the complete code and it matches."), Modifier.weight(1f), style = MaterialTheme.typography.body2)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ decide(false) }) { Text(flashCopy("Rechazar", "Reject")) }
                    Button(
                        { decide(true) }, enabled = compared && remaining > 0,
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 44.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = qetaraTeal, contentColor = Color.White), elevation = ButtonDefaults.elevation(0.dp)
                    ) {
                        Text(if (isBatch) {
                            if (approval.outgoing) flashCopy("Coincide: enviar $fileCount archivos", "Codes match: send $fileCount files") else flashCopy("Coincide: recibir $fileCount archivos", "Codes match: receive $fileCount files")
                        } else if (isText) { if (approval.outgoing) flashCopy("Coincide · enviar texto", "Codes match · send text") else flashCopy("Coincide · aceptar texto", "Codes match · accept text") }
                        else if (approval.outgoing) flashCopy("Coincide · enviar archivo", "Codes match · send file") else flashCopy("Coincide · aceptar archivo", "Codes match · accept file"))
                    }
                }
            }
        }
    )
}
