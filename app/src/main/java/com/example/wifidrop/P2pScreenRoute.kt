package com.example.wifidrop

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.example.wifidrop.presentation.FeedbackDeduplicator
import com.example.wifidrop.presentation.P2pFeedbackMessage
import com.example.wifidrop.presentation.P2pOutboundOrchestrationResult
import com.example.wifidrop.presentation.P2pUndoFeedbackPlan
import com.example.wifidrop.presentation.normalizeRequestedChatChannel
import com.example.wifidrop.presentation.resolveWifiPermissionPlan
import com.example.wifidrop.presentation.resolveWifiPermissionRequestAction
import com.example.wifidrop.presentation.WifiPermissionRequestAction
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun P2pScreenRoute() {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val localDeviceId = remember { LocalDeviceIdentity.getOrCreate(appContext) }
    val presenters = rememberP2pRoutePresenters(
        appContext = appContext,
        localDeviceId = localDeviceId
    )
    val backend = presenters.backend
    val sessionPresenter = presenters.sessionPresenter
    val connectionHintsPresenter = presenters.connectionHintsPresenter
    val shareImportPresenter = presenters.shareImportPresenter

    val wifiState by backend.wifiState.collectAsState()
    val transferState by backend.transferState.collectAsState()
    val incomingShare by IncomingShareBus.payload.collectAsState()
    val lanDiscoveryState by presenters.lanDiscoveryPresenter.state.collectAsState()
    val sessionState by sessionPresenter.state.collectAsState()
    val favoriteSuggestion by presenters.peerActionsPresenter.favoriteSuggestion.collectAsState()
    val shareImportState by shareImportPresenter.state.collectAsState()

    val receiveDir = remember {
        File(appContext.getExternalFilesDir(null), "WifiDropReceived").apply { mkdirs() }
    }
    val directWifiTargetsSaver = remember {
        listSaver<List<String>, String>(
            save = { ArrayList(it) },
            restore = { it.toList() }
        )
    }

    var receivedFiles by remember { mutableStateOf(loadReceivedFiles(receiveDir)) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var targetIpInput by rememberSaveable { mutableStateOf("") }
    var directChatDraft by rememberSaveable { mutableStateOf("") }
    var channelChatDraft by rememberSaveable { mutableStateOf("") }
    var chatChannel by rememberSaveable { mutableStateOf(ChatChannel.DIRECT) }
    val chatDraft = if (chatChannel == ChatChannel.GLOBAL) channelChatDraft else directChatDraft
    fun updateChatDraft(value: String) {
        if (chatChannel == ChatChannel.GLOBAL) channelChatDraft = value else directChatDraft = value
    }
    var chatDirectLanTargetIp by rememberSaveable { mutableStateOf<String?>(null) }
    var chatDirectWifiTargetIps by rememberSaveable(stateSaver = directWifiTargetsSaver) {
        mutableStateOf(emptyList<String>())
    }
    var uxPreferences by remember { mutableStateOf(UxPreferencesStore.load(appContext)) }

    val sessionExpired = sessionPresenter.isSessionExpired(nowMs)
    val requiredPermissions = remember { requiredWifiDirectPermissions() }
    var hasPermission by remember { mutableStateOf(hasRequiredPermissions(appContext)) }
    DisposableEffect(activity, appContext) {
        val lifecycleOwner = activity as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = hasRequiredPermissions(appContext)
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    var wifiPermissionAsked by rememberSaveable { mutableStateOf(false) }
    var notificationPermissionAsked by rememberSaveable { mutableStateOf(false) }

    val routeState = deriveP2pScreenRouteState(
        P2pScreenRouteDerivationInput(
            wifiState = wifiState,
            transferState = transferState,
            lanDiscoveryState = lanDiscoveryState,
            sessionState = sessionState,
            shareImportState = shareImportState,
            favoriteSuggestion = favoriteSuggestion,
            hasPermission = hasPermission,
            sessionExpired = sessionExpired,
            nowMs = nowMs,
            targetIpInput = targetIpInput,
            chatDraft = chatDraft,
            chatChannel = chatChannel,
            chatDirectLanTargetIp = chatDirectLanTargetIp,
            chatDirectWifiTargetIps = chatDirectWifiTargetIps,
            uxPreferences = uxPreferences,
            receivedFiles = receivedFiles,
            localDeviceId = localDeviceId
        )
    )

    val feedbackDeduplicator = remember { FeedbackDeduplicator() }
    val latestLanConnected by rememberUpdatedState(routeState.lanConnected)
    val latestLanDeviceLabel by rememberUpdatedState(routeState.latestLanDeviceLabel)
    val latestTargetIpInput by rememberUpdatedState(targetIpInput)
    val latestSessionDeviceLabel by rememberUpdatedState(routeState.latestSessionDeviceLabel)

    fun persistUxPreferences(next: UxPreferences) {
        val saved = UxPreferencesStore.save(appContext, next)
        if (saved.activeConnectionMode != uxPreferences.activeConnectionMode) {
            targetIpInput = ""
            chatDirectLanTargetIp = null
            chatDirectWifiTargetIps = emptyList()
            sessionPresenter.clearSessionConfirmation()
        }
        uxPreferences = saved
    }

    fun applyConnectionViewMode(next: ConnectionViewMode) {
        val adjusted = adjustedPreferencesForConnectionViewMode(uxPreferences, next)
        persistUxPreferences(adjusted)
        chatChannel = normalizeRequestedChatChannel(adjusted.activeConnectionMode, chatChannel)
    }

    fun setGlobalLanJoined(enabled: Boolean) {
        normalizedPreferencesForGlobalLan(uxPreferences, enabled)?.let(::persistUxPreferences)
    }

    fun hapticFeedback(success: Boolean) {
        val enabled = if (success) uxPreferences.vibrateOnConnect else uxPreferences.vibrateOnError
        if (!enabled) return
        appContext.vibrateBrief(success)
    }

    suspend fun showFeedback(message: String, isError: Boolean = false) {
        val normalized = message.trim()
        if (normalized.isBlank()) return
        if (!isError && uxPreferences.silentSuccessFeedback) return
        if (!feedbackDeduplicator.shouldShow(normalized, isError, System.currentTimeMillis())) return
        val text = if (isError && !normalized.startsWith("Error:", ignoreCase = true)) {
            "Error: $normalized"
        } else {
            normalized
        }
        if (isError) {
            hapticFeedback(success = false)
        }
        snackbarHostState.showSnackbar(text)
    }

    suspend fun showUndoSnackbar(
        message: String,
        actionLabel: String = "Deshacer"
    ): Boolean {
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            withDismissAction = true
        )
        return result == SnackbarResult.ActionPerformed
    }

    fun pushFeedback(message: String, isError: Boolean = false) {
        scope.launch {
            showFeedback(message, isError)
        }
    }

    fun pushFeedback(feedback: P2pFeedbackMessage?) {
        feedback ?: return
        pushFeedback(feedback.message, feedback.isError)
    }

    suspend fun runUndoPlan(
        plan: P2pUndoFeedbackPlan,
        onCommit: suspend () -> Unit = {},
        onUndo: suspend () -> Unit = {}
    ) {
        val undone = showUndoSnackbar(
            message = plan.promptMessage,
            actionLabel = plan.actionLabel
        )
        if (undone) {
            onUndo()
            plan.undoFeedback?.let { showFeedback(it.message, it.isError) }
            return
        }
        onCommit()
        plan.committedFeedback?.let { showFeedback(it.message, it.isError) }
    }

    fun openWifiSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launchedPanel = runCatching {
                appContext.startActivity(panelIntent)
                true
            }.getOrElse { false }
            if (launchedPanel) return
        }

        val primaryIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val launchedPrimary = runCatching {
            appContext.startActivity(primaryIntent)
            true
        }.getOrElse { false }
        if (launchedPrimary) return

        val fallbackIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val launchedFallback = runCatching {
            appContext.startActivity(fallbackIntent)
            true
        }.getOrElse { false }

        if (!launchedFallback) {
            pushFeedback("No pude abrir ajustes de Wi-Fi.", isError = true)
        }
    }

    fun refreshReceivedFiles() {
        receivedFiles = loadReceivedFiles(receiveDir)
    }

    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasRequiredPermissions(appContext)
        val plan = resolveWifiPermissionPlan(
            permissionGranted = hasPermission,
            lanConnected = latestLanConnected
        )
        if (plan.refreshWifiDirectState) {
            backend.refreshWifiDirectState()
        }
        if (plan.markPermissionMissing) {
            backend.markWifiDirectPermissionMissing()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Notification permission is optional for functionality.
    }

    var pendingPickerContext by rememberSaveable { mutableStateOf(com.example.wifidrop.presentation.P2pAttachmentContext.FILES) }
    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        presenters.importPickedFiles(uris, pendingPickerContext)
    }

    fun handleOutboundResult(result: P2pOutboundOrchestrationResult) {
        applyOutboundResult(
            result = result,
            clearChatDraft = { updateChatDraft("") },
            pushFeedback = ::pushFeedback
        )
    }

    fun handleLanSuggestedTarget(suggestedIp: String?) {
        if (!canUseLanTargetSuggestion(uxPreferences.connectionViewMode)) return
        applyLanSuggestedTarget(
            presenter = connectionHintsPresenter,
            currentTargetIp = latestTargetIpInput,
            suggestedIp = suggestedIp,
            onResolved = { targetIpInput = it }
        )
    }

    fun handleTransientUiEffect(effect: com.example.wifidrop.presentation.P2pTransientUiEffect?) {
        applyTransientUiEffect(
            effect = effect,
            hapticFeedback = ::hapticFeedback,
            pushFeedback = ::pushFeedback
        )
    }

    fun applyWifiPermissionPlan() {
        val plan = resolveWifiPermissionPlan(
            permissionGranted = hasPermission,
            lanConnected = latestLanConnected
        )
        if (plan.refreshWifiDirectState) {
            backend.refreshWifiDirectState()
        }
        if (plan.markPermissionMissing) {
            backend.markWifiDirectPermissionMissing()
        }
    }

    LaunchedEffect(
        routeState.effectiveChatChannel,
        routeState.effectiveChatDirectLanTargetIp,
        routeState.effectiveChatDirectWifiTargetIps
    ) {
        if (chatChannel != routeState.effectiveChatChannel) {
            chatChannel = routeState.effectiveChatChannel
        }
        if (chatDirectLanTargetIp != routeState.effectiveChatDirectLanTargetIp) {
            chatDirectLanTargetIp = routeState.effectiveChatDirectLanTargetIp
        }
        if (chatDirectWifiTargetIps != routeState.effectiveChatDirectWifiTargetIps) {
            chatDirectWifiTargetIps = routeState.effectiveChatDirectWifiTargetIps
        }
    }


    LaunchedEffect(
        uxPreferences.connectionViewMode,
        wifiState.connection?.groupFormed,
        routeState.routing.targets.directTarget?.ip
    ) {
        if (uxPreferences.connectionViewMode == ConnectionViewMode.WIFI_DIRECT &&
            wifiState.connection?.groupFormed == true && targetIpInput.isBlank()) {
            routeState.routing.targets.directTarget?.ip?.let { targetIpInput = it }
        }
    }

    BindP2pScreenRouteEffects(
        input = P2pScreenRouteEffectsInput(
            activity = activity,
            appContext = appContext,
            presenters = presenters,
            wifiState = wifiState,
            transferState = transferState,
            incomingShare = incomingShare,
            hasPermission = hasPermission,
            lanConnected = routeState.lanConnected,
            sessionState = sessionState,
            sessionExpired = sessionExpired,
            latestLanDeviceLabel = latestLanDeviceLabel,
            latestTargetIpInput = latestTargetIpInput,
            latestSessionDeviceLabel = latestSessionDeviceLabel,
            receiveDirPath = receiveDir.absolutePath,
            nowMs = nowMs,
            uxPreferences = uxPreferences,
            shareImportStatus = routeState.shareImportStatus,
            directChatAvailablePeers = routeState.uiState.chatDirectAvailablePeers,
            resolvedTarget = routeState.routing.targets.resolvedTarget,
            sessionNetworkKey = routeState.sessionNetworkKey,
            targetIpInput = targetIpInput,
            wifiPermissionAsked = wifiPermissionAsked,
            notificationPermissionAsked = notificationPermissionAsked
        ),
        onNowTick = { nowMs = it },
        onSuggestedTarget = ::handleLanSuggestedTarget,
        onApplyTransientUiEffect = ::handleTransientUiEffect,
        onApplyWifiPermissionPlan = ::applyWifiPermissionPlan,
        onRequestNotificationPermission = {
            notificationPermissionAsked = true
            postNotificationsPermission()?.let(notificationPermissionLauncher::launch)
        },
        onReceivedFilesChanged = ::refreshReceivedFiles,
        onShowFeedback = { feedback -> showFeedback(feedback.message, feedback.isError) },
        onConnectionHintTargetResolved = {
            if (uxPreferences.connectionViewMode != ConnectionViewMode.LAN) targetIpInput = it
        }
    )

    val screenWiring = buildP2pScreenEventWiring(
        P2pScreenEventWiringInput(
            context = context,
            appContext = appContext,
            scope = scope,
            presenters = presenters,
            wifiState = wifiState,
            transferState = transferState,
            uxPreferences = uxPreferences,
            routeState = routeState,
            hasPermission = hasPermission,
            targetIpInput = targetIpInput,
            rawChatChannel = chatChannel,
            persistUxPreferences = ::persistUxPreferences,
            applyConnectionViewMode = ::applyConnectionViewMode,
            setGlobalLanJoined = ::setGlobalLanJoined,
            setTargetIpInput = { targetIpInput = it },
            setChatDraft = ::updateChatDraft,
            setChatChannel = { chatChannel = it },
            setChatDirectLanTargetIp = { chatDirectLanTargetIp = it },
            setChatDirectWifiTargetIps = { chatDirectWifiTargetIps = it },
            pushFeedback = ::pushFeedback,
            pushFeedbackMessage = ::pushFeedback,
            runUndoPlan = ::runUndoPlan,
            openWifiSettings = ::openWifiSettings,
            refreshReceivedFiles = ::refreshReceivedFiles,
            handleOutboundResult = ::handleOutboundResult,
            handleLanSuggestedTarget = ::handleLanSuggestedTarget,
            requestWifiPermissions = {
                val permissionGrantedNow = hasRequiredPermissions(appContext)
                hasPermission = permissionGrantedNow
                when (resolveWifiPermissionRequestAction(
                    permissionGranted = permissionGrantedNow,
                    permissionAlreadyRequested = wifiPermissionAsked,
                    shouldShowRationale = requiredPermissions.any { activity?.shouldShowRequestPermissionRationale(it) == true }
                )) {
                    WifiPermissionRequestAction.REFRESH_STATE -> backend.refreshWifiDirectState()
                    WifiPermissionRequestAction.REQUEST_PERMISSION -> {
                        wifiPermissionAsked = true
                        wifiPermissionLauncher.launch(requiredPermissions)
                    }
                    WifiPermissionRequestAction.OPEN_APP_SETTINGS -> {
                        runCatching {
                            context.startActivity(Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", appContext.packageName, null)
                            ))
                        }.onFailure {
                            pushFeedback("Abre Ajustes de Android, Qetara y Permisos para permitir dispositivos cercanos.", true)
                        }
                    }
                }
            },
            pickFiles = { attachmentContext ->
                pendingPickerContext = attachmentContext
                pickFileLauncher.launch(arrayOf("*/*"))
            }
        )
    )

    var showPreferences by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    if (showPreferences) {
        QetaraPreferencesDialog(
            state = routeState.uiState,
            onFontScaleChange = screenWiring.onFontScaleChange,
            onCompactModeChange = screenWiring.onCompactModeChange,
            onVibrateOnConnectChange = screenWiring.onVibrateOnConnectChange,
            onVibrateOnErrorChange = screenWiring.onVibrateOnErrorChange,
            onSilentSuccessFeedbackChange = screenWiring.onSilentSuccessFeedbackChange,
            onDismiss = { showPreferences = false }
        )
    }
    if (showLicenses) {
        QetaraOpenSourceLicensesDialog(onDismiss = { showLicenses = false })
    }
    if (showAbout && !showLicenses) {
        val identityFingerprint = remember(appContext) {
            runCatching { NoiseIdentityStore.fingerprintShort(NoiseIdentityStore.getOrCreate(appContext).publicKey).chunked(4).joinToString(" ") }.getOrNull()
        }
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("Qetara") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Comparte cerca. Conserva el control.", fontWeight = FontWeight.SemiBold)
                    Text("Archivos y mensajes entre tus equipos, usando tu red local o Wi-Fi Direct. Las transferencias no necesitan una cuenta ni un servidor en la nube.")
                    Text("Un proyecto de código abierto de Experience Lab, creado por Intelog Natanael.")
                    Text("Código abierto · licencia MIT", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { showLicenses = true }) { Text("Licencias") }
                    Text("Versión "+BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelMedium)
                    identityFingerprint?.let { fingerprint ->
                        Text("Huella de este equipo: $fingerprint", style = MaterialTheme.typography.labelMedium)
                    }
                    Text("Los archivos recibidos están en Descargas. La actividad y los mensajes se conservan en este equipo.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Listo") } }
        )
    }

    val flashState by FlashAndroidRuntime.state.collectAsState()
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (!keyboardVisible) {
            var topModeMenuExpanded by rememberSaveable { mutableStateOf(false) }
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Box {
                        TextButton(
                            onClick = { topModeMenuExpanded = true },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Text("Qetara", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (routeState.uiState.connectionViewMode == ConnectionViewMode.ADVANCED) "Conexión avanzada"
                                        else routeState.uiState.activeConnectionMode.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(Icons.Rounded.ExpandMore, contentDescription = "Cambiar conexión", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = topModeMenuExpanded,
                            onDismissRequest = { topModeMenuExpanded = false }
                        ) {
                            ConnectionViewMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.title) },
                                    onClick = {
                                        topModeMenuExpanded = false
                                        applyConnectionViewMode(mode)
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { context.startActivity(Intent(context, FlashActivity::class.java)) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Flash")
                            if (flashState.active) Text(
                                if (flashState.engine?.approvals?.isNotEmpty() == true) "solicitud" else "activo",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    IconButton(onClick = { showPreferences = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Ajustes de lectura y avisos")
                    }
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Rounded.Info, contentDescription = "Acerca de Qetara")
                    }
                }
            )
            }
        }
    ) { padding ->
        RenderP2pScreen(
            state = routeState.uiState,
            wiring = screenWiring,
            snackbarHost = {
                if (snackbarHostState.currentSnackbarData != null) {
                    SnackbarHost(hostState = snackbarHostState)
                }
            },
            modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        )
    }
}
