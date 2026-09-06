package com.example.wifidrop

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** The everyday connection flow. Detailed transport controls remain in the advanced view. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun P2pConnectTab(
    state: P2pScreenState,
    onModeChange: (ConnectionMode) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onStartHost: () -> Unit,
    onStartClient: () -> Unit,
    onCancelConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onConnectToPeer: (String) -> Unit,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onUsePeer: (String) -> Unit,
    onSyncSession: () -> Unit,
    onConfirmManualSession: () -> Unit,
    onRenewSession: () -> Unit,
    onTokenChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onCopyToken: () -> Unit,
    onPasteToken: () -> Unit,
    onOpenSend: () -> Unit,
    onOpenChat: () -> Unit,
    onSaveFavorite: (String) -> Unit,
    onSkipFavorite: (String) -> Unit,
    onTrustPeer: (PendingTrustRequest) -> Unit,
    onRequestForgetPeer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val selectPeer: (String) -> Unit = { ip ->
        focusManager.clearFocus()
        onUsePeer(ip)
        scope.launch { listState.animateScrollToItem(2) }
    }
    val direct = state.activeConnectionMode == ConnectionMode.WIFI_DIRECT
    val linked = state.connection?.groupFormed == true
    val host = state.connection?.isGroupOwner == true
    val ready = state.sessionEnabled && (if (direct) !state.directTargetIp.isNullOrBlank() else state.lanConnected && !state.resolvedTargetIp.isNullOrBlank())
    val exchangeReady = ready && state.sessionReady
    val selectedLabel = if (direct) state.directTargetLabel ?: state.directTargetIp else state.resolvedTargetLabel ?: state.resolvedTargetIp
    var manualAddressDraft by rememberSaveable(state.targetIp) { mutableStateOf(state.targetIp) }
    var showManualAddress by rememberSaveable { mutableStateOf(false) }
    var showCredentials by rememberSaveable { mutableStateOf(false) }
    var showConnectionHelp by rememberSaveable { mutableStateOf(false) }
    var showRememberedPeers by rememberSaveable { mutableStateOf(false) }
    val manualPairing = state.tokenSyncStatus.contains("manual", ignoreCase = true) ||
        state.tokenSyncStatus.contains("secure_credentials_required", ignoreCase = true)
    val sessionProblem = state.sessionExpired || manualPairing ||
        state.tokenSyncStatus.contains("no se pudo", ignoreCase = true) ||
        state.tokenSyncStatus.contains("no pude", ignoreCase = true)
    val sessionSyncing = state.sessionSyncing
    val validCredentials = FileTransfer.isValidToken(state.authToken) && TransferSecurity.isValidPin(state.sessionPin)
    val invalidAddress = manualAddressDraft.isNotBlank() && !isValidManualConnectionAddress(manualAddressDraft)
    val peers = state.knownServicePeers.filter { com.example.wifidrop.presentation.isAutomaticConnectionAddress(it.ip, state.lanLocalIp) }
        .distinctBy { it.ip }.sortedWith(compareByDescending<KnownPeerSnapshot> { it.trusted }.thenBy { it.label })

    val showSessionSection = state.sessionEnabled && (ready || linked || (state.lanConnected && !direct) || sessionProblem)
    val lanPeersSectionIndex = 3 + if (showSessionSection) 1 else 0
    val manualAddressIndex = lanPeersSectionIndex + 1 + peers.size + if (peers.isEmpty() && !state.lanScanning) 1 else 0

    LaunchedEffect(manualPairing) {
        if (manualPairing) {
            showCredentials = true
            listState.animateScrollToItem(3)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item("connect-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (manualPairing) "Un paso para conectar." else if (exchangeReady && !sessionProblem && !sessionSyncing) "Ya están cerca." else "Comparte cerca.",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    if (manualPairing) "Usa el código de sesión y el PIN que muestra el receptor."
                    else if (exchangeReady && !sessionProblem && !sessionSyncing) "Elige un archivo o empieza una conversación."
                    else "Sin cuentas. Sin subir tus archivos a la nube.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !direct,
                        onClick = { onModeChange(ConnectionMode.LAN) },
                        leadingIcon = { Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text("Misma Wi-Fi") }
                    )
                    FilterChip(
                        selected = direct,
                        onClick = { onModeChange(ConnectionMode.WIFI_DIRECT) },
                        leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text("Wi-Fi Direct") }
                    )
                }
                Text(
                    if (direct) "Entre equipos Android cercanos, sin necesitar un router."
                    else "Para Android y PC conectados a la misma red Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item("this-device") {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Devices, contentDescription = null)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Este equipo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.thisDeviceName.ifBlank { "Mi Android" }, fontWeight = FontWeight.SemiBold)
                        if (!direct && state.lanConnected) {
                            Text(state.lanLocalIp ?: "Conectado a la red", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item("connection-action") {
            ConnectionStepSurface {
                when {
                    !state.sessionEnabled -> {
                        Text("Vuelve cuando quieras compartir", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("La sesión permanece cerrada hasta que toques Activar sesión. Puedes preparar archivos, leer mensajes y consultar Descargas.")
                    }
                    direct && !state.permissionGranted -> {
                        Text("Primero, permite encontrar equipos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Android necesita tu permiso para detectar dispositivos cercanos y crear un enlace Wi-Fi Direct.")
                        Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) { Text("Permitir conexión cercana") }
                        Text("También puedes elegir Misma Wi-Fi para compartir con un PC.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    direct && !state.p2pEnabled -> {
                        Text("Activa Wi-Fi para empezar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Abre los ajustes de Android, activa Wi-Fi y vuelve a Qetara. No hace falta tener internet.")
                        Button(onClick = onOpenWifiSettings, modifier = Modifier.fillMaxWidth()) { Text("Abrir ajustes Wi-Fi") }
                    }
                    ready -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null)
                            Text("Equipo seleccionado", style = MaterialTheme.typography.labelLarge)
                        }
                        Text(selectedLabel ?: "Tu otro equipo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        when {
                            sessionSyncing -> {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text("Preparando la sesión. Si el otro equipo es Android, acepta allí la solicitud de conexión.")
                            }
                            manualPairing -> {
                                Text("Introduce debajo el código de sesión y el PIN que muestra Qetara en el equipo receptor.")
                                FilledTonalButton(onClick = { showCredentials = true; scope.launch { listState.animateScrollToItem(3) } }) { Text("Introducir código y PIN") }
                            }
                            !state.sessionReady || sessionProblem || !validCredentials -> {
                                Text(if (state.sessionExpired) "Renueva la sesión para continuar." else "La sesión aún no está confirmada. Acepta la solicitud en el otro Android o introduce aquí los datos del receptor.", style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = onSyncSession, enabled = !state.sessionExpired) { Text("Reintentar conexión") }
                                TextButton(onClick = { showCredentials = true; scope.launch { listState.animateScrollToItem(3) } }) { Text("Introducir código y PIN") }
                            }
                            else -> {
                                Text("Los archivos viajan entre tus equipos por la red local.", style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = onOpenSend, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Enviar archivos", Modifier.padding(start = 8.dp))
                                }
                                FilledTonalButton(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Abrir chat", Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                        if (direct && linked) {
                            TextButton(onClick = onDisconnect) { Text("Desconectar equipo") }
                        }
                    }
                    direct && (state.directCreatingGroup || (linked && host)) -> {
                        Text(if (linked) "Tu enlace está abierto" else "Creando tu enlace…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (!linked) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        if (linked && state.chatDirectAvailablePeers.isNotEmpty()) {
                            Text("Elige el equipo que recibirá tus archivos y mensajes. La selección anterior no cambia sola.")
                            FilledTonalButton(onClick = onOpenChat) { Text("Elegir equipo") }
                        } else {
                            Text("En el otro Android, abre Qetara, elige Wi-Fi Direct y toca Buscar un equipo. Después elige este dispositivo.")
                        }
                        Text(state.thisDeviceName.ifBlank { "Este equipo" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = if (linked) onDisconnect else onCancelConnect) { Text("Cerrar enlace") }
                    }
                    direct && state.directConnecting -> {
                        Text("Conectando los equipos…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Acepta la solicitud de Android si aparece. Mantén Qetara abierto en ambos equipos.")
                        TextButton(onClick = onCancelConnect) { Text("Cancelar conexión") }
                    }
                    direct && linked -> {
                        Text("Preparando la sesión…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("El enlace ya existe. Aprueba la conexión en el equipo que abrió el enlace para empezar a compartir.")
                        OutlinedButton(onClick = onSyncSession) { Text("Sincronizar sesión") }
                        TextButton(onClick = onDisconnect) { Text("Desconectar") }
                    }
                    direct && state.directDiscovering -> {
                        Text("Buscando un equipo cercano…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(if (state.peers.isEmpty()) "En el otro Android, toca Crear un enlace. Los equipos aparecerán aquí."
                            else "Elige abajo el equipo que creó el enlace.")
                        TextButton(onClick = onCancelConnect) { Text("Detener búsqueda") }
                    }
                    direct -> {
                        Text("¿Quién empieza?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Crea un enlace en uno de los equipos y búscalo desde el otro.")
                        Button(onClick = onStartHost, modifier = Modifier.fillMaxWidth()) { Text("Crear un enlace") }
                        OutlinedButton(onClick = onStartClient, modifier = Modifier.fillMaxWidth()) { Text("Buscar un equipo") }
                    }
                    !state.lanConnected -> {
                        Text("Conecta ambos a la misma Wi-Fi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Abre Qetara también en el otro equipo. Puede ser un Android o un PC; la red no necesita internet.")
                        Button(onClick = onOpenWifiSettings, modifier = Modifier.fillMaxWidth()) { Text("Abrir ajustes Wi-Fi") }
                    }
                    else -> {
                        Text(if (state.lanScanning) "Buscando en tu red…" else "Encuentra tu otro equipo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Mantén Qetara abierto en el otro equipo para que aparezca aquí.")
                        if (state.lanScanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Button(onClick = if (state.lanScanning) onCancelScan else onScan, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.lanScanning) "Detener búsqueda" else "Buscar equipos")
                        }
                    }
                }
                if (!direct && state.lanConnected) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (ready) {
                            TextButton(onClick = { scope.launch { listState.animateScrollToItem(lanPeersSectionIndex) } }) { Text("Cambiar equipo") }
                        }
                        TextButton(onClick = {
                            showManualAddress = true
                            scope.launch { listState.animateScrollToItem(manualAddressIndex) }
                        }) { Text("Conectar por dirección IP") }
                    }
                }
            }
        }
        if (showSessionSection) {
            item("session") {
                ConnectionStepSurface {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (manualPairing) "Sesión del otro equipo" else "Tu sesión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (state.sessionExpired) {
                        Text("La sesión terminó. Renuévala para seguir compartiendo.", color = MaterialTheme.colorScheme.error)
                        Button(onClick = onRenewSession) { Text("Renovar sesión") }
                    } else if (state.tokenSyncStatus.isNotBlank()) {
                        Text(state.tokenSyncStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    } else {
                        Text("Con un PC, escribe su código de sesión y PIN. Entre Android puedes aceptar la solicitud de conexión para compartir la sesión.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (ready && !state.sessionExpired && !manualPairing && !sessionSyncing) {
                        OutlinedButton(onClick = onSyncSession) { Text("Sincronizar sesión") }
                    }
                    TextButton(onClick = { showCredentials = !showCredentials }) { Text(if (showCredentials) "Ocultar credenciales" else "Ver o introducir credenciales") }
                    if (showCredentials) {
                        Text("Con un PC o una versión anterior, copia aquí el código de sesión y el PIN que muestra el otro equipo. Ambos deben usar los mismos valores.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = state.authToken, onValueChange = onTokenChange, enabled = !sessionSyncing, label = { Text("Código de sesión") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        P2pSessionPinField(state = state, onValueChange = onPinChange, modifier = Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onCopyToken) { Text("Copiar código") }
                            TextButton(onClick = onPasteToken) { Text("Pegar código") }
                        }
                        if (ready && !state.sessionExpired) {
                            Button(onClick = { onConfirmManualSession(); onOpenSend() }, enabled = validCredentials && !sessionSyncing, modifier = Modifier.fillMaxWidth()) { Text("Usar esta sesión") }
                        }
                    }
                }
            }
        }
        if (state.sessionEnabled && direct && state.permissionGranted && state.p2pEnabled && !linked && state.peers.isNotEmpty()) {
            item("direct-peers-title") { ConnectionSectionTitle("Equipos cercanos") }
            items(state.peers, key = { "direct:" + it.address }) { peer ->
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(peer.name.ifBlank { "Android cercano" }, fontWeight = FontWeight.SemiBold)
                        Text(peerStatusLabel(peer.status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = { onConnectToPeer(peer.address) },
                            enabled = peer.status == WifiP2pDevice.AVAILABLE || peer.status == WifiP2pDevice.FAILED,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Conectar con este equipo") }
                    }
                }
            }
        }
        if (!direct && state.lanConnected) {
            item("lan-peers-title") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    ConnectionSectionTitle("Equipos en esta red")
                    TextButton(onClick = if (state.lanScanning) onCancelScan else onScan) { Text(if (state.lanScanning) "Detener" else "Actualizar") }
                }
            }
            if (peers.isEmpty() && !state.lanScanning) {
                item("no-lan-peers") {
                    EmptyStateBlock(
                        title = "Todavía no vemos otro equipo",
                        body = "Abre Qetara en ambos y comprueba que usan la misma Wi-Fi. Si conoces su dirección IP, puedes escribirla abajo."
                    )
                }
            }
            items(peers, key = { "lan:" + it.ip }) { peer ->
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Devices, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(peer.label.ifBlank { peer.ip }, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(peer.ip + if (peer.trusted) " · Equipo recordado" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton(onClick = { selectPeer(peer.ip) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.resolvedTargetIp == peer.ip) "Sincronizar con este equipo" else "Conectar con este equipo")
                        }
                    }
                }
            }
            item("manual-address") {
                ConnectionStepSurface {
                    TextButton(onClick = { showManualAddress = !showManualAddress }) {
                        Text(if (showManualAddress) "Ocultar dirección manual" else "Conectar con una dirección IP")
                    }
                    if (showManualAddress) {
                        OutlinedTextField(
                            value = manualAddressDraft,
                            onValueChange = { manualAddressDraft = it.trim() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("IP del otro equipo") },
                            placeholder = { Text("192.168.1.8") },
                            supportingText = { Text(if (invalidAddress) "Escribe una dirección IPv4, por ejemplo 192.168.1.8."
                                else "Aparece en Qetara del otro equipo.") },
                            isError = invalidAddress,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                        )
                        Button(onClick = { selectPeer(manualAddressDraft.trim()) }, enabled = isValidManualConnectionAddress(manualAddressDraft) && !sessionSyncing) { Text("Conectar por IP") }
                    }
                }
            }
        }
        state.pendingTrust?.let { request ->
            item("pending-trust") {
                ConnectionStepSurface {
                    Text("¿Reconoces este equipo?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(request.label + " · " + request.ip)
                    Text("Comprueba que el envío lo has iniciado tú desde ese equipo antes de recordarlo.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onTrustPeer(request) }) { Text("Confiar en este equipo") }
                }
            }
        }
        if (!direct && state.lanConnected && state.favoritePeers.any { !it.lastKnownIp.isNullOrBlank() }) {
            item("favorites-title") { ConnectionSectionTitle("Tus favoritos") }
            items(state.favoritePeers.filter { !it.lastKnownIp.isNullOrBlank() }, key = { "favorite:" + it.id }) { peer ->
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(TrustedPeerStore.displayName(peer), fontWeight = FontWeight.SemiBold)
                        Text("Última IP: " + peer.lastKnownIp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { selectPeer(peer.lastKnownIp.orEmpty()) }) { Text("Conectar con favorito") }
                    }
                }
            }
        }
        val suggestionId = state.favoriteSuggestionPeerId
        if (ready && !suggestionId.isNullOrBlank()) {
            item("favorite-suggestion") {
                ConnectionStepSurface {
                    Text("Encuéntralo más rápido la próxima vez", fontWeight = FontWeight.SemiBold)
                    Text("Guarda a " + (state.favoriteSuggestionLabel ?: "este equipo") + " como favorito.", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onSaveFavorite(suggestionId) }) { Text("Guardar favorito") }
                        TextButton(onClick = { onSkipFavorite(suggestionId) }) { Text("Ahora no") }
                    }
                }
            }
        }
        if (state.trustedPeers.isNotEmpty()) {
            item("remembered-peers-toggle") {
                TextButton(onClick = { showRememberedPeers = !showRememberedPeers }) {
                    Text(if (showRememberedPeers) "Ocultar equipos recordados" else "Gestionar equipos recordados")
                }
            }
            if (showRememberedPeers) {
                items(state.trustedPeers, key = { "remembered:" + it.id }) { peer ->
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(TrustedPeerStore.displayName(peer), fontWeight = FontWeight.SemiBold)
                            peer.lastKnownIp?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            TextButton(onClick = { onRequestForgetPeer(peer.id) }) {
                                Text("Olvidar equipo", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        item("connection-help") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showConnectionHelp = !showConnectionHelp }) { Text(if (showConnectionHelp) "Cerrar ayuda" else "¿No aparece el otro equipo?") }
                if (showConnectionHelp) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (direct) """1. Mantén ambos Android cerca y con Wi-Fi activo.
2. Crea un enlace en uno y búscalo desde el otro.
3. Acepta la solicitud de conexión de Android.
4. Si el dispositivo no admite Wi-Fi Direct, prueba Misma Wi-Fi."""
                                else """1. Abre Qetara en ambos equipos.
2. Comprueba que comparten la misma red. Las redes de invitados pueden impedir que se vean.
3. En PC, permite a Qetara acceder a la red local.
4. Si no aparece, escribe la IP que muestra el otro equipo.""", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStepSurface(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun ConnectionSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
}

internal fun isValidManualConnectionAddress(value: String): Boolean {
    val address = value.trim()
    val octets = address.split('.')
    return octets.size == 4 && octets.all { it.isNotEmpty() && it.length <= 3 && it.all { character -> character in '0'..'9' } && (it.toIntOrNull() ?: -1) in 0..255 } &&
        address != "0.0.0.0" && address != "255.255.255.255"
}
