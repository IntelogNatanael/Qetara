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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    val chooseFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) FlashForegroundService.chooseFile(uri)
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        notificationsAllowed = allowed
        if (!allowed) notice = "Puedes seguir usando Flash aquí. Para ver solicitudes al salir, permite notificaciones en Ajustes de Android."
    }
    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
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
        topBar = {
            TopAppBar(
                title = { Text("Flash", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver a Qetara")
                } }
            )
        },
        bottomBar = {
            if (state.active && operations.isEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(selectedPeer?.let { "Para: ${it.label} · ${it.address}" } ?: "Elige un equipo receptor",
                            style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        state.selectedFile?.let { Text(it.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Button(onClick = FlashForegroundService::send,
                            enabled = !busy && selectedPeer != null && state.selectedFile != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Solicitar envío") }
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
                    Text(if (state.active) "Flash está activo" else "Comparte con Flash",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (state.phase == FlashAndroidPhase.OFF) {
                        Text("Actívalo en ambos equipos de la misma red. Cada archivo requiere comparar una verificación y aceptar.")
                        Button(onClick = { notice = null; FlashForegroundService.activate(activity, label) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Activar Flash · 30 minutos") }
                        Text("Tu sesión habitual de Qetara se mantiene aparte.", style = MaterialTheme.typography.bodySmall)
                        if (editName) OutlinedTextField(value = label, onValueChange = { label = it.take(60) }, singleLine = true,
                            label = { Text("Nombre visible durante Flash") }, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { editName = !editName }) {
                            Text(if (editName) "Listo" else "Este equipo: "+label.ifBlank { Build.MODEL }+" · Cambiar")
                        }
                    } else if (state.phase == FlashAndroidPhase.STARTING || state.phase == FlashAndroidPhase.STOPPING) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(if (state.phase == FlashAndroidPhase.STARTING) "Activando…" else "Desactivando y cerrando las conexiones…")
                    } else {
                        Text("Se desactiva en "+flashRemaining(state.engine?.expiresAtMs ?: now, now)+" · "+state.deviceLabel,
                            style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { showSessionDetails = !showSessionDetails }) { Text(if (showSessionDetails) "Menos" else "Mi dirección") }
                            OutlinedButton(onClick = FlashForegroundService::deactivate, modifier = Modifier.heightIn(min = 48.dp)) { Text("Desactivar Flash") }
                        }
                        if (showSessionDetails) {
                            Text("Dirección de este equipo: "+state.localAddresses.ifEmpty { listOf("No disponible") }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            Text("Flash sigue activo al volver a Qetara. Desactivar cancela sus solicitudes y transferencias; los archivos recibidos se conservan.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(state.status, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (notice != null) item("notice") {
                FlashCard {
                    Text(notice.orEmpty(), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(onClick = { notice = null }) { Text("Entendido") }
                }
            }
            if (state.active && !notificationsAllowed) item("notifications") {
                FlashCard {
                    Text("Recibe avisos al salir de Flash", fontWeight = FontWeight.SemiBold)
                    Text("Sin notificaciones, vuelve a esta pantalla para revisar las solicitudes.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("Permitir avisos")
                    }
                }
            }
            items(operations, key = { "operation:" + it.id }) { operation ->
                FlashCard {
                    Text(if (operation.outgoing) "Enviando a ${operation.peer?.label ?: "otro equipo"}" else "Recibiendo un archivo",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(operation.fileName)
                    val progress = state.progress[operation.id]
                    if (progress == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Esperando conexión y confirmación en ambos equipos.")
                    } else {
                        LinearProgressIndicator(progress = { if (progress.totalBytes > 0) (progress.transferredBytes.toFloat() / progress.totalBytes).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                        Text(android.text.format.Formatter.formatFileSize(activity, progress.transferredBytes) + " de " + android.text.format.Formatter.formatFileSize(activity, progress.totalBytes))
                    }
                    OutlinedButton(onClick = { FlashForegroundService.cancel(operation.id) }) { Text("Cancelar transferencia") }
                }
            }
            if (state.active && operations.isEmpty()) {
                item("file") {
                    FlashCard {
                        Text("1. Elige un archivo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        state.selectedFile?.let { file ->
                            Text(file.name)
                            Text(android.text.format.Formatter.formatFileSize(activity, file.length()), style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.importing) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            TextButton(onClick = FlashForegroundService::cancelImport) { Text("Cancelar selección") }
                        } else {
                            Button(onClick = { notice = null; chooseFile.launch(arrayOf("*/*")) }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(if (state.selectedFile == null) "Elegir archivo" else "Elegir otro archivo")
                            }
                            if (state.selectedFile != null) TextButton(onClick = FlashForegroundService::clearFile, enabled = !busy) { Text("Quitar archivo") }
                        }
                        Text("Para recibir, basta con mantener Flash activo y aceptar la solicitud que llegue.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item("peers-header") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("2. Elige el receptor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = { FlashForegroundService.discoverPeers() }, enabled = !busy) { Text("Buscar") }
                    }
                }
                if (state.engine?.peers.orEmpty().none { it.expiresAtMs > now }) item("empty-peers") {
                    Text("Aún no hay equipos disponibles. Activa Flash en el otro equipo y comprueba que estén en la misma red.")
                }
                items(state.engine?.peers.orEmpty().filter { it.expiresAtMs > now }, key = { it.id + ":" + it.address + ":" + it.port }) { peer ->
                    val selected = selectedPeer?.let { it.id == peer.id && it.address == peer.address && it.port == peer.port } == true
                    OutlinedCard(modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = !busy,
                        role = Role.RadioButton, onClick = { FlashForegroundService.selectPeer(peer) })) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(peer.label, fontWeight = FontWeight.SemiBold)
                            Text(peer.address + if (selected) " · Seleccionado" else "", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item("manual") {
                    TextButton(onClick = { manualExpanded = !manualExpanded }, enabled = !busy) { Text(if (manualExpanded) "Ocultar dirección manual" else "No aparece: introducir dirección") }
                    if (manualExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = manualAddress, onValueChange = { manualAddress = it.take(64) },
                                label = { Text("Dirección IP del otro equipo") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                                enabled = !busy, modifier = Modifier.fillMaxWidth())
                            Text("Usa la dirección que aparece en Flash del otro equipo. También debe tener Flash activo.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { FlashForegroundService.discoverPeers(manualAddress) }, enabled = !busy && manualAddress.isNotBlank()) { Text("Buscar esta dirección") }
                        }
                    }
                }
                item("send") {
                    FlashCard {
                        Text("3. Verifica y comparte", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(when {
                            selectedPeer != null -> "Destino: ${selectedPeer.label} · ${selectedPeer.address}"
                            state.selectedPeer != null -> "El equipo elegido ya no está disponible. Búscalo de nuevo o elige otro."
                            else -> "Elige un equipo receptor."
                        })
                        Text("Al solicitar el envío, aparecerá la misma verificación en ambos equipos. Compruébala con la otra persona antes de aceptar.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.results.isNotEmpty()) item("results-title") {
                Text("Actividad de Flash", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            items(if (historyExpanded) state.results else state.results.take(3), key = { "result:" + it.id }) { result ->
                FlashCard {
                    Text(when (result.kind) {
                        FlashResultKind.DELIVERED -> "Entrega confirmada"
                        FlashResultKind.RECEIVED -> "Archivo recibido"
                        FlashResultKind.CANCELLED -> "Cancelado"
                        FlashResultKind.FAILED -> "No se completó"
                    }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(result.fileName)
                    Text(result.detail, style = MaterialTheme.typography.bodySmall)
                    result.file?.let { file ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                runCatching { ExternalOpenUtils.openRoute(activity, result.downloadUri ?: file.absolutePath) }
                                    .onFailure { notice = "No hay una aplicación disponible para abrirlo. Puedes guardar una copia." }
                            }) { Text("Abrir") }
                            TextButton(onClick = { pendingSavePath = file.absolutePath; saveCopy.launch(file.name) }, enabled = !savingCopy) {
                                Text(if (savingCopy) "Guardando…" else "Guardar una copia")
                            }
                        }
                    }
                }
            }
            if (state.results.size > 3) item("history-toggle") {
                TextButton(onClick = { historyExpanded = !historyExpanded }) {
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
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun FlashApprovalDialog(approval: FlashApproval, nowMs: Long, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text(if (approval.outgoing) "Verifica antes de enviar" else "Verifica antes de recibir") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Equipo: ${approval.peer.label}", fontWeight = FontWeight.SemiBold)
                Text(approval.peer.address, style = MaterialTheme.typography.bodySmall)
                Text("Archivo: ${approval.fileName}")
                Text(flashFileSize(approval.totalBytes), style = MaterialTheme.typography.bodySmall)
                Text("Compara estos cuatro grupos en los dos equipos. Deben coincidir exactamente.")
                Text(approval.verificationCode, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { contentDescription = "Verificación: " + approval.verificationCode.toCharArray().joinToString(" ") })
                Text("Si no reconoces el archivo o la verificación no coincide, rechaza la solicitud.", style = MaterialTheme.typography.bodySmall)
                Text("Solicitud válida durante ${flashRemaining(approval.expiresAtMs, nowMs)}", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(true) }, enabled = approval.expiresAtMs > nowMs) {
                Text(if (approval.outgoing) "Coincide: enviar" else "Coincide: recibir archivo")
            }
        },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("Rechazar") } }
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
