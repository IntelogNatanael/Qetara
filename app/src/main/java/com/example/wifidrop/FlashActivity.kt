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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
        setContent { QetaraTheme { FlashScreen(this, onBack = ::finish) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FlashScreen(activity: FlashActivity, onBack: () -> Unit) {
    val state by FlashAndroidRuntime.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var label by rememberSaveable { mutableStateOf(Build.MODEL.take(60)) }
    var manualAddress by rememberSaveable { mutableStateOf("") }
    var manualExpanded by rememberSaveable { mutableStateOf(false) }
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf(false) }
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
    val approval = state.engine?.approvals.orEmpty().firstOrNull { it.expiresAtMs > now }
    if (approval != null && state.active) {
        key(approval.requestId) {
            FlashApprovalDialog(approval, now,
                onAnswer = { accepted -> FlashForegroundService.approve(approval.requestId, accepted) })
        }
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
            if (state.active && operations.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(selectedPeer?.let { "Para: ${it.label} · ${it.address}" } ?: "Elige un equipo receptor",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (state.selectedFiles.isNotEmpty()) {
                            Text(
                                if (state.selectedFiles.size == 1) state.selectedFiles.first().name
                                else "${state.selectedFiles.size} archivos preparados",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(onClick = FlashForegroundService::send,
                            enabled = !busy && selectedPeer != null && state.selectedFiles.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) {
                            Text(if (state.selectedFiles.size <= 1) "Solicitar envío" else "Enviar ${state.selectedFiles.size} archivos")
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("session") {
                FlashCard {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (state.active) "Flash activo" else "Comparte en tu red",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (state.active) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(16.dp)) {
                                Text(flashRemaining(state.engine?.expiresAtMs ?: now, now)+" restantes",
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    if (state.phase == FlashAndroidPhase.OFF) {
                        Text("Activa Flash en ambos equipos de la misma red. Compara el código y acepta cada archivo.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { notice = null; FlashForegroundService.activate(activity, label) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) { Text("Activar Flash · 30 minutos") }
                        Text("Tu sesión habitual de Qetara se mantiene aparte.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (editName) OutlinedTextField(value = label, onValueChange = { label = it.take(60) }, singleLine = true,
                            label = { Text("Nombre visible durante Flash") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                        TextButton(onClick = { editName = !editName }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (editName) "Listo" else "Este equipo: "+label.ifBlank { Build.MODEL }+" · Cambiar")
                        }
                    } else if (state.phase == FlashAndroidPhase.STARTING || state.phase == FlashAndroidPhase.STOPPING) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(if (state.phase == FlashAndroidPhase.STARTING) "Activando…" else "Desactivando y cerrando las conexiones…")
                    } else {
                        Text(state.deviceLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { showSessionDetails = !showSessionDetails }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (showSessionDetails) "Ocultar dirección" else "Mi dirección") }
                            OutlinedButton(onClick = FlashForegroundService::deactivate, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) { Text("Desactivar Flash") }
                        }
                        if (showSessionDetails) {
                            Text("Dirección de este equipo: "+state.localAddresses.ifEmpty { listOf("No disponible") }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            Text("Flash sigue activo al volver a Qetara. Desactivar cancela sus solicitudes y transferencias; los archivos recibidos se conservan.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(state.status, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (notice != null) item("notice") {
                FlashCard {
                    Text(notice.orEmpty(), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(onClick = { notice = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Entendido") }
                }
            }
            if (state.active && !notificationsAllowed) item("notifications") {
                FlashCard {
                    Text("Avisos de solicitudes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Sin notificaciones, vuelve a esta pantalla para revisar las solicitudes.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Permitir avisos")
                    }
                }
            }
            items(operations, key = { "operation:" + it.id }) { operation ->
                FlashCard {
                    Text(if (operation.outgoing) "Enviando a ${operation.peer?.label ?: "otro equipo"}" else "Recibiendo un archivo",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(operation.fileName, style = MaterialTheme.typography.bodyMedium)
                    val progress = state.progress[operation.id]
                    if (progress == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Esperando conexión y confirmación en ambos equipos.")
                    } else {
                        LinearProgressIndicator(progress = { if (progress.totalBytes > 0) (progress.transferredBytes.toFloat() / progress.totalBytes).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                        Text(android.text.format.Formatter.formatFileSize(activity, progress.transferredBytes) + " de " + android.text.format.Formatter.formatFileSize(activity, progress.totalBytes))
                    }
                    OutlinedButton(onClick = { FlashForegroundService.cancel(operation.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) { Text("Cancelar transferencia") }
                }
            }
            if (state.active && operations.isEmpty()) {
                item("file") {
                    FlashCard {
                        FlashSectionHeading("Archivos para compartir", "1")
                        if (state.selectedFiles.isEmpty()) {
                            Text("Elige uno o varios archivos de tu teléfono.", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.selectedFiles.take(3).forEachIndexed { index, file ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            FlashFileSummary(file.name, android.text.format.Formatter.formatFileSize(activity, file.length()))
                        }
                        if (state.selectedFiles.size > 3) {
                            Text("Y ${state.selectedFiles.size - 3} archivo(s) más", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.importing) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            TextButton(onClick = FlashForegroundService::cancelImport, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar selección") }
                        } else {
                            OutlinedButton(onClick = { notice = null; chooseFiles.launch(arrayOf("*/*")) }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) {
                                Text(if (state.selectedFiles.isEmpty()) "Elegir archivos" else "Agregar archivos")
                            }
                            if (state.selectedFiles.isNotEmpty()) TextButton(onClick = FlashForegroundService::clearFiles, enabled = !busy,
                                modifier = Modifier.heightIn(min = 48.dp)) { Text("Quitar todos") }
                        }
                        Text("Para recibir, mantén Flash activo y acepta la solicitud.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item("peers-header") {
                    val searchButton: @Composable () -> Unit = {
                        TextButton(onClick = { FlashForegroundService.discoverPeers() }, enabled = !busy,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text("Buscar") }
                    }
                    val fontScale = LocalDensity.current.fontScale
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth < 320.dp * fontScale) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                FlashSectionHeading("Equipo receptor", "2")
                                searchButton()
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) { FlashSectionHeading("Equipo receptor", "2") }
                                searchButton()
                            }
                        }
                    }
                }
                if (state.engine?.peers.orEmpty().none { it.expiresAtMs > now }) item("empty-peers") {
                    FlashCard {
                        Icon(Icons.Rounded.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Text("Aún no hay equipos disponibles", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Activa Flash en el otro equipo, comprueba que estén en la misma red y vuelve a buscar.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(state.engine?.peers.orEmpty().filter { it.expiresAtMs > now }, key = { it.id + ":" + it.address + ":" + it.port }) { peer ->
                    val selected = selectedPeer?.let { it.id == peer.id && it.address == peer.address && it.port == peer.port } == true
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = !busy,
                            role = Role.RadioButton, onClick = { FlashForegroundService.selectPeer(peer) }),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface),
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected, onClick = null, enabled = !busy)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(peer.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                Text(peer.address + if (selected) " · Seleccionado" else "", style = MaterialTheme.typography.bodySmall,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item("manual") {
                    TextButton(onClick = { manualExpanded = !manualExpanded }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (manualExpanded) "Ocultar dirección manual" else "No aparece mi equipo") }
                    if (manualExpanded) {
                        FlashCard {
                            OutlinedTextField(value = manualAddress, onValueChange = { manualAddress = it.take(64) },
                                label = { Text("Dirección IP del otro equipo") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                                enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                            Text("Usa la dirección que aparece en Flash del otro equipo. También debe tener Flash activo.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = { FlashForegroundService.discoverPeers(manualAddress) }, enabled = !busy && manualAddress.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) { Text("Buscar esta dirección") }
                        }
                    }
                }
                item("send") {
                    FlashCard {
                        FlashSectionHeading("Verifica y comparte", "3")
                        Text(when {
                            selectedPeer != null -> "Destino: ${selectedPeer.label} · ${selectedPeer.address}"
                            state.selectedPeer != null -> "El equipo elegido ya no está disponible. Búscalo de nuevo o elige otro."
                            else -> "Elige un equipo receptor."
                        })
                        Text("Cada archivo tiene su propio código en ambos equipos. Compáralo con la otra persona antes de aceptar.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.results.isNotEmpty()) item("results-title") {
                Text("Actividad reciente", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            items(if (historyExpanded) state.results else state.results.take(3), key = { "result:" + it.id }) { result ->
                FlashCard {
                    Text(when (result.kind) {
                        FlashResultKind.DELIVERED -> "Entrega confirmada"
                        FlashResultKind.RECEIVED -> "Archivo recibido"
                        FlashResultKind.CANCELLED -> "Cancelado"
                        FlashResultKind.FAILED -> "No se completó"
                    }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        color = when (result.kind) {
                            FlashResultKind.DELIVERED, FlashResultKind.RECEIVED -> MaterialTheme.colorScheme.primary
                            FlashResultKind.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                            FlashResultKind.FAILED -> MaterialTheme.colorScheme.error
                        })
                    Text(result.fileName, style = MaterialTheme.typography.bodyMedium)
                    Text(result.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    result.file?.let { file ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            item("footer") {
                Text("Flash es opcional. Las verificaciones no crean confianza permanente. Los archivos viajan cifrados entre los equipos.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FlashCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
private fun FlashSectionHeading(title: String, step: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(12.dp)) {
            Text(step, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FlashFileSummary(name: String, size: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(10.dp).size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(size, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FlashApprovalDialog(approval: FlashApproval, nowMs: Long, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(if (approval.outgoing) "Verifica antes de enviar" else "Verifica antes de recibir", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Equipo: ${approval.peer.label}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(approval.peer.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(approval.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(flashFileSize(approval.totalBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("Si no reconoces el archivo o la verificación no coincide, rechaza la solicitud.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Solicitud válida durante ${flashRemaining(approval.expiresAtMs, nowMs)}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = { onAnswer(true) }, enabled = approval.expiresAtMs > nowMs,
                modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) {
                Text(if (approval.outgoing) "Coincide: enviar" else "Coincide: recibir archivo")
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
