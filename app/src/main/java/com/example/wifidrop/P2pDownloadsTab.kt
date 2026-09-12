package com.example.wifidrop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** The library is independent of the live connection: received files remain useful offline. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun P2pDownloadsTab(
    state: P2pScreenState,
    onRefresh: () -> Unit,
    onOpenDownloads: () -> Unit,
    onShareFile: (File) -> Unit,
    onOpenHistoryItem: (TransferHistoryEntry) -> Unit,
    onOpenConnect: () -> Unit,
    onOpenSend: () -> Unit,
    modifier: Modifier = Modifier,
    initialSection: DownloadLibrarySection = DownloadLibrarySection.FILES
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(DownloadFileSort.NEWEST) }
    var historyFilter by rememberSaveable { mutableStateOf(DownloadHistoryFilter.ALL) }
    var showSortMenu by rememberSaveable { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    val compactSearch = searchFocused && WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val listState = rememberLazyListState()
    val files by produceState<List<DownloadFileSnapshot>>(emptyList(), state.receivedFiles) {
        value = withContext(Dispatchers.IO) {
            state.receivedFiles.filter { it.isFile }.map { DownloadFileSnapshot(it, it.length(), it.lastModified()) }
        }
    }
    val visibleFiles = remember(files, query, sort) { filterDownloadFiles(files, query, sort) }
    val visibleHistory = remember(state.history, query, historyFilter) {
        filterDownloadHistory(state.history, query, historyFilter)
    }
    val hasQuery = query.isNotBlank()
    val resultCount = if (section == DownloadLibrarySection.FILES) visibleFiles.size else visibleHistory.size

    LaunchedEffect(section, query, sort, historyFilter, compactSearch) {
        if (!compactSearch) listState.scrollToItem(0)
    }
    LaunchedEffect(compactSearch) {
        if (compactSearch) listState.scrollToItem(0)
    }
    LaunchedEffect(Unit) { onRefresh() }

    fun openFile(file: File) {
        runCatching { ExternalOpenUtils.openRoute(context, file.absolutePath) }
            .onFailure {
                scope.launch { snackbar.showSnackbar("No se pudo abrir. Prueba Compartir y elige una aplicación.") }
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (compactSearch) 8.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactSearch) 8.dp else 16.dp)
            ) {
                if (!compactSearch) item("library-header") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Tus archivos",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                if (files.isEmpty()) "Los archivos recibidos se guardan aquí."
                                else "${files.size} ${if (files.size == 1) "archivo recibido" else "archivos recibidos"} · ${formatBytes(files.sumOf { it.bytes })}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(onClick = onOpenDownloads, modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Abrir carpeta", Modifier.padding(start = 8.dp))
                            }
                            TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Actualizar", Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
                if (state.receiving) {
                    item("receiving") { ReceivingFilePanel(state) }
                } else if (!state.receiverFailureCause.isNullOrBlank()) {
                    item("receive-error") {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("La recepción necesita atención", fontWeight = FontWeight.SemiBold)
                                Text(
                                    friendlyTransferIssue(state.receiverFailureCause, "No se completó el archivo. Pide al otro equipo que vuelva a enviarlo.")
                                        ?: "Pide al otro equipo que vuelva a enviar el archivo.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(onClick = onOpenConnect, modifier = Modifier.heightIn(min = 48.dp)) { Text("Revisar conexión") }
                            }
                        }
                    }
                }
                if (!compactSearch) item("library-sections") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DownloadLibrarySection.entries.forEach { option ->
                            FilterChip(
                                selected = section == option,
                                onClick = { section = option },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = qetaraFilterChipColors(),
                                label = { Text(option.title) }
                            )
                        }
                    }
                }
                if (files.isNotEmpty() || state.history.isNotEmpty() || hasQuery) {
                    item("library-search") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it.take(160) },
                            modifier = Modifier.fillMaxWidth().onFocusChanged { searchFocused = it.isFocused },
                            singleLine = true,
                            label = { Text(if (section == DownloadLibrarySection.FILES) "Buscar archivo" else "Buscar archivo o equipo") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Borrar búsqueda")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
                item("library-filters") {
                    if (compactSearch) {
                        Text(
                            "$resultCount ${if (resultCount == 1) "resultado" else "resultados"}" +
                                if (section == DownloadLibrarySection.ACTIVITY && historyFilter != DownloadHistoryFilter.ALL)
                                    " · ${historyFilter.title}" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (section == DownloadLibrarySection.FILES && files.isNotEmpty()) {
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                if (hasQuery) "$resultCount ${if (resultCount == 1) "resultado" else "resultados"}" else "Disponibles en este equipo",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            Box {
                                TextButton(onClick = { showSortMenu = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(sort.title, Modifier.padding(start = 6.dp))
                                }
                                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                    DownloadFileSort.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.title) },
                                            onClick = { sort = option; showSortMenu = false },
                                            trailingIcon = {
                                                if (sort == option) Icon(Icons.Rounded.CheckCircleOutline, contentDescription = "Seleccionado")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else if (section == DownloadLibrarySection.ACTIVITY && state.history.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DownloadHistoryFilter.entries.forEach { option ->
                                FilterChip(
                                    selected = historyFilter == option,
                                    onClick = { historyFilter = option },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    colors = qetaraFilterChipColors(),
                                    label = { Text(option.title) }
                                )
                            }
                        }
                    }
                }
                if (section == DownloadLibrarySection.FILES) {
                    if (visibleFiles.isEmpty()) {
                        item("files-empty") {
                            DownloadEmptyState(
                                icon = if (hasQuery) Icons.Rounded.Search else Icons.Rounded.FolderOpen,
                                title = if (hasQuery) "No encontramos ese archivo" else "Aún no has recibido archivos",
                                body = if (hasQuery) "Prueba con otra parte del nombre o borra la búsqueda."
                                    else "Conecta Qetara con otro equipo y recibe tu primer archivo. Después podrás abrirlo y compartirlo desde aquí, incluso sin conexión.",
                                actionLabel = if (hasQuery) "Borrar búsqueda" else "Conectar un equipo",
                                onAction = if (hasQuery) ({ query = "" }) else onOpenConnect
                            )
                        }
                    }
                    items(visibleFiles, key = { "file:${it.file.absolutePath}" }) { item ->
                        ReceivedFileRow(item = item, onOpen = { openFile(item.file) }, onShare = { onShareFile(item.file) })
                    }
                } else {
                    if (visibleHistory.isEmpty()) {
                        item("activity-empty") {
                            val filtered = hasQuery || historyFilter != DownloadHistoryFilter.ALL
                            DownloadEmptyState(
                                icon = if (filtered) Icons.Rounded.Search else Icons.Rounded.CheckCircleOutline,
                                title = if (filtered) "No hay actividad con estos filtros" else "Aún no hay transferencias",
                                body = if (filtered) "Prueba con otro nombre o muestra toda la actividad."
                                    else "Aquí verás qué enviaste, qué recibiste y si cada transferencia se completó.",
                                actionLabel = if (filtered) "Ver toda la actividad" else "Enviar archivos",
                                onAction = if (filtered) ({ query = ""; historyFilter = DownloadHistoryFilter.ALL }) else onOpenSend
                            )
                        }
                    }
                    items(visibleHistory, key = { "history:${it.id}" }) { item ->
                        TransferActivityRow(item = item, onOpen = { onOpenHistoryItem(item) })
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
}

@Composable
private fun DownloadEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(14.dp).size(28.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceivedFileRow(item: DownloadFileSnapshot, onOpen: () -> Unit, onShare: () -> Unit) {
    var moreExpanded by rememberSaveable(item.file.absolutePath) { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Icon(
                        downloadFileIcon(item.file.extension),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(4.dp).size(24.dp)
                    )
                }
                Text(item.file.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(formatBytes(item.bytes), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatHistoryTime(item.modifiedAtMs), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { moreExpanded = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Acciones para ${item.file.name}")
                    }
                    DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Abrir archivo") },
                            leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                            onClick = { moreExpanded = false; onOpen() }
                        )
                        DropdownMenuItem(
                            text = { Text("Compartir") },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            onClick = { moreExpanded = false; onShare() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferActivityRow(item: TransferHistoryEntry, onOpen: () -> Unit) {
    val successful = item.outcome == TransferOutcome.SUCCESS
    val contentColor = if (item.outcome == TransferOutcome.FAILED) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onSurface
    val detailColor = if (item.outcome == TransferOutcome.FAILED) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (item.outcome == TransferOutcome.FAILED) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surface,
        contentColor = contentColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (item.outcome == TransferOutcome.FAILED) Icons.Rounded.ErrorOutline
                    else if (item.outcome == TransferOutcome.CANCELED) Icons.Rounded.Close
                    else if (item.direction == TransferDirection.RECEIVED) Icons.Rounded.ArrowDownward
                    else Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    tint = when (item.outcome) {
                        TransferOutcome.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                        TransferOutcome.CANCELED -> MaterialTheme.colorScheme.onSurfaceVariant
                        TransferOutcome.SUCCESS -> MaterialTheme.colorScheme.primary
                    }
                )
                Text(
                    "${historyDirectionLabel(item.direction)} · ${historyOutcomeLabel(item.outcome)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(item.fileName, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    item.peerLabel?.takeIf { it.isNotBlank() } ?: item.peerIp?.takeIf { it.isNotBlank() },
                    item.bytes.takeIf { it >= 0 }?.let(::formatBytes),
                    formatHistoryTime(item.timestampMs)
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = detailColor
            )
            if (!successful) {
                Text(
                    if (item.outcome == TransferOutcome.CANCELED) "La transferencia se canceló antes de completarse."
                    else friendlyTransferIssue(item.errorCause, "No se completó. Comprueba la conexión y vuelve a enviar el archivo.")
                        ?: "No se completó. Comprueba la conexión y vuelve a enviar el archivo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = detailColor
                )
            } else if (!item.route.isNullOrBlank()) {
                TextButton(onClick = onOpen, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) { Text("Abrir archivo") }
            }
        }
    }
}

@Composable
private fun ReceivingFilePanel(state: P2pScreenState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Text("Recibiendo archivo", fontWeight = FontWeight.SemiBold)
            }
            Text(state.receiverFileName ?: "Preparando la recepción…", maxLines = 2, overflow = TextOverflow.Ellipsis)
            val progress = state.receiverProgress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                buildList {
                    progress?.let { add("${(it * 100).roundToInt().coerceIn(0, 100)} %") }
                    if (state.receiverAverageBps > 0) add(formatRate(state.receiverAverageBps))
                    state.receiverEtaSeconds?.let { add("Faltan ${formatEta(it)}") }
                }.joinToString(" · ").ifBlank { "Puedes seguir usando Qetara mientras llega." },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun downloadFileIcon(extension: String): ImageVector = when (extension.lowercase()) {
    "jpg", "jpeg", "png", "webp", "gif", "heic", "svg", "avif" -> Icons.Rounded.Image
    "mp4", "mkv", "mov", "webm", "avi" -> Icons.Rounded.VideoFile
    "mp3", "m4a", "ogg", "wav", "flac", "aac" -> Icons.Rounded.AudioFile
    else -> Icons.Rounded.Description
}
