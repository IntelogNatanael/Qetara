package com.example.wifidrop

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.wifidrop.protocol.flash.FlashApproval
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

class FlashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        setContent {
            val baseDensity = LocalDensity.current
            val readingScale = remember { UxPreferencesStore.load(this).fontScale.coerceIn(0.85f, 1.25f) }
            QetaraTheme {
                CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * readingScale)) {
                    FlashScreen(this, onBack = ::finish)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FlashScreen(activity: FlashActivity, onBack: () -> Unit) {
    val state by FlashAndroidRuntime.state.collectAsState()
    val listState = rememberLazyListState()
    var previousPhase by remember { mutableStateOf(state.phase) }
    LaunchedEffect(state.phase) {
        if (previousPhase != state.phase) {
            previousPhase = state.phase
            listState.scrollToItem(0)
        }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var label by rememberSaveable { mutableStateOf(Build.MODEL.take(60)) }
    var manualAddress by rememberSaveable { mutableStateOf("") }
    var manualExpanded by rememberSaveable { mutableStateOf(false) }
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    var filesExpanded by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf(false) }
    var choosingPeer by rememberSaveable { mutableStateOf(false) }
    var showStopConfirmation by rememberSaveable { mutableStateOf(false) }
    var showSessionDetails by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSavePath by rememberSaveable { mutableStateOf<String?>(null) }
    var savingCopy by remember { mutableStateOf(false) }
    var notificationsAllowed by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { while (isActive) { delay(1_000); now = System.currentTimeMillis() } }
    val chooseFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) FlashForegroundService.chooseFiles(uris)
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        notificationsAllowed = allowed
        if (!allowed) notice = "Puedes seguir usando Flash aquí. Para ver solicitudes al salir, permite notificaciones en Ajustes de Android."
    }
    val saveCopy = rememberLauncherForActivityResult(CreateReceivedDocument()) { uri ->
        val path = pendingSavePath
        pendingSavePath = null
        if (uri != null && path != null) {
            savingCopy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        File(path).inputStream().use { input ->
                            activity.contentResolver.openOutputStream(uri, "w")?.use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                            } ?: error("No disponible")
                        }
                    }
                    notice = "Copia guardada en la ubicación que elegiste."
                } catch (_: Exception) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { DocumentsContract.deleteDocument(activity.contentResolver, uri) }
                    }
                    notice = "No se pudo guardar la copia. El original sigue en Qetara."
                } finally { savingCopy = false }
            }
        }
    }
    val selectedPeer = resolveSelectedFlashPeer(state.selectedPeer, state.engine?.peers.orEmpty(), now)
    val operations = state.engine?.operations.orEmpty()
    val busy = state.importing || operations.isNotEmpty()
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val showStatus = state.status.isNotBlank() &&
        !(state.phase == FlashAndroidPhase.OFF && state.status == "Flash está desactivado.")
    val approval = state.engine?.approvals.orEmpty().firstOrNull { it.expiresAtMs > now }
    if (approval != null && state.active) {
        key(approval.requestId) {
            FlashApprovalDialog(approval, now,
                onAnswer = { accepted -> FlashForegroundService.approve(approval.requestId, accepted) })
        }
    }

    // The service also owns pending outgoing work. Prepared files protect the interval
    // between two operations without inventing another transfer state in the UI.
    val confirmBeforeStopping = busy || approval != null || state.selectedFiles.isNotEmpty()
    LaunchedEffect(state.active, approval?.requestId) {
        if (!state.active || approval != null) showStopConfirmation = false
        if (!state.active) choosingPeer = false
    }
    if (showStopConfirmation && state.active && approval == null) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("¿Apagar Flash?") },
            text = {
                Text("Se detendrán la preparación de archivos, las solicitudes y las transferencias de Flash. Los archivos recibidos se conservan.",
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
            },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirmation = false
                    FlashForegroundService.deactivate()
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Apagar Flash") }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmation = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Seguir en Flash")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Flash", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver a Qetara")
                } }
            )
        },
        bottomBar = {
            if (state.active && operations.isEmpty() && !imeVisible) {
                FlashSendBar(
                    importing = state.importing,
                    selectedPeer = selectedPeer,
                    selectedFileCount = state.selectedFiles.size,
                    enabled = !busy && approval == null,
                    onChoosePeer = {
                        choosingPeer = true
                        // The active, idle prefix contains session, optional status and optional notice.
                        val receiverIndex = 1 + (if (showStatus) 1 else 0) + (if (notice != null) 1 else 0)
                        scope.launch { listState.animateScrollToItem(receiverIndex) }
                    },
                    onChooseFiles = { notice = null; chooseFiles.launch(arrayOf("*/*")) },
                    onSend = {
                        if (!busy && approval == null && selectedPeer != null && state.selectedFiles.isNotEmpty()) {
                            FlashForegroundService.send()
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.active) item("session") {
                FlashSessionSummary(
                    deviceLabel = state.deviceLabel,
                    remaining = flashRemaining(state.engine?.expiresAtMs ?: now, now),
                    transferring = operations.isNotEmpty(),
                    awaitingApproval = approval != null,
                    showAddress = showSessionDetails,
                    addresses = state.localAddresses,
                    onToggleAddress = { showSessionDetails = !showSessionDetails },
                    onStop = {
                        if (confirmBeforeStopping) showStopConfirmation = true
                        else FlashForegroundService.deactivate()
                    }
                )
            }
            // Active work takes precedence over choosing another destination or file.
            items(operations, key = { "operation:" + it.id }) { operation ->
                FlashCard {
                    Text(if (operation.outgoing) "Enviando a ${operation.peer?.label ?: "otro equipo"}" else "Recibiendo un archivo",
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(operation.fileName, style = MaterialTheme.typography.bodyMedium)
                    val progress = state.progress[operation.id]
                    if (progress == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Esperando conexión y confirmación en ambos equipos.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LinearProgressIndicator(progress = {
                            if (progress.totalBytes > 0) (progress.transferredBytes.toFloat() / progress.totalBytes).coerceIn(0f, 1f) else 0f
                        }, modifier = Modifier.fillMaxWidth())
                        Text(android.text.format.Formatter.formatFileSize(activity, progress.transferredBytes) + " de " +
                            android.text.format.Formatter.formatFileSize(activity, progress.totalBytes),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { FlashForegroundService.cancel(operation.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("Cancelar transferencia", textAlign = TextAlign.Center)
                    }
                }
            }
            // Omit only the initial, redundant OFF label. Errors, expiry and service
            // notices keep their original wording and remain visible in every phase.
            if (showStatus) {
                item("status") {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(12.dp)) {
                        Text(state.status, modifier = Modifier.fillMaxWidth().padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (notice != null) item("notice") {
                FlashCard {
                    Text(notice.orEmpty(), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { notice = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Entendido") }
                }
            }
            when (state.phase) {
                FlashAndroidPhase.OFF -> item("introduction") {
                    FlashIntroduction(
                        label = if (editName) label else label.ifBlank { Build.MODEL },
                        editingName = editName,
                        onLabelChange = { label = it.take(60) },
                        onToggleName = { editName = !editName },
                        onActivate = { notice = null; FlashForegroundService.activate(activity, label) }
                    )
                }
                FlashAndroidPhase.STARTING, FlashAndroidPhase.STOPPING -> item("transition") {
                    FlashCard {
                        Text(if (state.phase == FlashAndroidPhase.STARTING) "Activando Flash" else "Cerrando Flash",
                            style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(if (state.phase == FlashAndroidPhase.STARTING) "Preparando una sesión de 30 minutos." else "Cerrando las conexiones de esta sesión.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FlashAndroidPhase.ACTIVE -> Unit
            }
            if (state.active && operations.isEmpty()) {
                item("receiver") {
                    FlashCard {
                        FlashSectionHeading("Equipo receptor", "1")
                        if (selectedPeer != null && !choosingPeer) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(12.dp)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Rounded.Devices, contentDescription = null, modifier = Modifier.size(24.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(selectedPeer.label, style = MaterialTheme.typography.titleSmall)
                                        Text(selectedPeer.address, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            TextButton(onClick = { choosingPeer = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Cambiar equipo")
                            }
                        } else {
                            Text("Los equipos con Flash activo en tu Wi-Fi aparecen aquí al activar la sesión.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { FlashForegroundService.discoverPeers() }, enabled = !busy,
                                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Volver a buscar") }
                                if (selectedPeer != null) {
                                    TextButton(onClick = { choosingPeer = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text("Conservar equipo")
                                    }
                                }
                            }
                            if (state.selectedPeer != null && selectedPeer == null) {
                                Surface(color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = RoundedCornerShape(12.dp)) {
                                    Text("El equipo elegido ya no está disponible. Vuelve a buscarlo o elige otro.",
                                        modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            val peers = state.engine?.peers.orEmpty().filter { it.expiresAtMs > now }
                            if (peers.isEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp))
                                    Text("Activa Flash en el otro equipo", style = MaterialTheme.typography.titleSmall)
                                    Text("Comprueba que ambos estén en la misma Wi-Fi y pulsa «Volver a buscar».",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    peers.forEach { peer ->
                                        key(peer.id, peer.address, peer.port) {
                                            val selected = selectedPeer?.let {
                                                it.id == peer.id && it.address == peer.address && it.port == peer.port
                                            } == true
                                            FlashPeerChoice(peer, selected, enabled = !busy, onSelect = {
                                                FlashForegroundService.selectPeer(peer)
                                                choosingPeer = false
                                                manualExpanded = false
                                            })
                                        }
                                    }
                                }
                            }
                            TextButton(onClick = { manualExpanded = !manualExpanded }, enabled = !busy,
                                modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(if (manualExpanded) "Ocultar dirección manual" else "Buscar por dirección IP")
                            }
                            if (manualExpanded) {
                                OutlinedTextField(value = manualAddress, onValueChange = { manualAddress = it.take(64) },
                                    label = { Text("IP del otro equipo") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                                    enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                                Text("En Flash del otro equipo, abre «Mi dirección». Debe seguir activo.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = { FlashForegroundService.discoverPeers(manualAddress) },
                                    enabled = !busy && manualAddress.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) {
                                    Text("Buscar esta dirección", textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
                item("files") {
                    FlashCard {
                        FlashSectionHeading("Archivos para compartir", "2")
                        if (state.selectedFiles.isEmpty()) {
                            Text("Elige uno o varios archivos de tu teléfono.", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        (if (filesExpanded) state.selectedFiles else state.selectedFiles.take(3)).forEachIndexed { index, file ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            FlashFileSummary(file.name, android.text.format.Formatter.formatFileSize(activity, file.length()),
                                showFullName = filesExpanded || state.selectedFiles.size <= 3)
                        }
                        if (state.selectedFiles.size > 3) {
                            TextButton(onClick = { filesExpanded = !filesExpanded }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(if (filesExpanded) "Mostrar menos" else "Ver los ${state.selectedFiles.size} archivos")
                            }
                        }
                        if (state.importing) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("Preparando tu selección…", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = FlashForegroundService::cancelImport,
                                modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar selección") }
                        } else {
                            OutlinedButton(onClick = { notice = null; chooseFiles.launch(arrayOf("*/*")) }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) {
                                Text(if (state.selectedFiles.isEmpty()) "Elegir archivos" else "Agregar archivos", textAlign = TextAlign.Center)
                            }
                            if (state.selectedFiles.isNotEmpty()) {
                                TextButton(onClick = FlashForegroundService::clearFiles, enabled = !busy,
                                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Quitar todos") }
                            }
                        }
                    }
                }
            }
            if (state.results.isNotEmpty()) item("results-title") {
                Text("Actividad reciente", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
            }
            items(if (historyExpanded) state.results else state.results.take(3), key = { "result:" + it.id }) { result ->
                FlashCard {
                    Text(when (result.kind) {
                        FlashResultKind.DELIVERED -> "Entrega confirmada"
                        FlashResultKind.RECEIVED -> "Archivo recibido"
                        FlashResultKind.CANCELLED -> "Cancelado"
                        FlashResultKind.FAILED -> "No se completó"
                    }, style = MaterialTheme.typography.labelLarge,
                        color = when (result.kind) {
                            FlashResultKind.DELIVERED, FlashResultKind.RECEIVED -> MaterialTheme.colorScheme.primary
                            FlashResultKind.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                            FlashResultKind.FAILED -> MaterialTheme.colorScheme.error
                        })
                    Text(result.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(result.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    result.file?.let { file ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = {
                                runCatching { ExternalOpenUtils.openRoute(activity, result.downloadUri ?: file.absolutePath) }
                                    .onFailure { notice = "No hay una aplicación disponible para abrirlo. Puedes guardar una copia." }
                            }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) { Text("Abrir") }
                            TextButton(onClick = { pendingSavePath = file.absolutePath; saveCopy.launch(file.name) }, enabled = !savingCopy,
                                modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(if (savingCopy) "Guardando…" else "Guardar una copia")
                            }
                        }
                    }
                }
            }
            if (state.results.size > 3) item("history-toggle") {
                TextButton(onClick = { historyExpanded = !historyExpanded }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (historyExpanded) "Mostrar menos" else "Ver historial ("+state.results.size+")")
                }
            }
            if (state.active && !notificationsAllowed) item("notifications") {
                FlashCard {
                    Text("Avisos al salir de Flash", style = MaterialTheme.typography.titleSmall)
                    Text("Sin notificaciones, vuelve a esta pantalla para revisar las solicitudes.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Permitir avisos") }
                }
            }
        }
    }
}

@Composable
private fun FlashApprovalDialog(approval: FlashApproval, nowMs: Long, onAnswer: (Boolean) -> Unit) {
    val scroll = key(approval.requestId) { rememberScrollState() }
    val filesScroll = key(approval.requestId) { rememberScrollState() }
    val fileCount = approval.files.size
    val isBatch = fileCount > 1
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(if (approval.outgoing) "Verifica antes de enviar" else "Verifica antes de recibir", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Equipo: ${approval.peer.label}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(approval.peer.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("$fileCount ${if (isBatch) "archivos" else "archivo"} · ${flashFileSize(approval.totalBytes)} en total",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() })
                        SelectionContainer {
                            Column(Modifier.fillMaxWidth().heightIn(max = 144.dp).verticalScroll(filesScroll),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                approval.files.forEach { file ->
                                    Text("${file.fileName} · ${flashFileSize(file.totalBytes)}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                Text("Compara estos cuatro grupos en los dos equipos. Deben coincidir exactamente.", style = MaterialTheme.typography.bodyMedium)
                Surface(color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(16.dp)) {
                    Text(approval.verificationCode, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 34.sp, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).semantics { contentDescription = "Verificación: " + approval.verificationCode.toCharArray().joinToString(" ") })
                }
                if (isBatch) Text("Se aprueban los $fileCount archivos de este lote. Los próximos envíos requieren otra confirmación.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(if (isBatch) "Si no reconoces algún archivo o la verificación no coincide, rechaza la solicitud."
                    else "Si no reconoces el archivo o la verificación no coincide, rechaza la solicitud.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Solicitud válida durante ${flashRemaining(approval.expiresAtMs, nowMs)}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = { onAnswer(true) }, enabled = approval.expiresAtMs > nowMs,
                modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) {
                Text(if (isBatch) {
                    if (approval.outgoing) "Coincide: enviar $fileCount archivos" else "Coincide: recibir $fileCount archivos"
                } else if (approval.outgoing) "Coincide: enviar" else "Coincide: recibir archivo")
            }
        },
        dismissButton = { TextButton(onClick = { onAnswer(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Rechazar") } }
    )
}

private fun flashRemaining(expiresAtMs: Long, nowMs: Long): String {
    val seconds = ((expiresAtMs - nowMs).coerceAtLeast(0L) + 999) / 1000
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

private fun flashFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> "$bytes bytes"
}
