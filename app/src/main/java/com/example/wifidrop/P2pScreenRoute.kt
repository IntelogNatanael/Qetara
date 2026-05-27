package com.example.wifidrop

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
    var chatDraft by rememberSaveable { mutableStateOf("") }
    var chatChannel by rememberSaveable { mutableStateOf(ChatChannel.DIRECT) }
    var chatDirectLanTargetIp by rememberSaveable { mutableStateOf<String?>(null) }
    var chatDirectWifiTargetIps by rememberSaveable(stateSaver = directWifiTargetsSaver) {
        mutableStateOf(emptyList<String>())
    }
    var uxPreferences by remember { mutableStateOf(UxPreferencesStore.load(appContext)) }

    val sessionExpired = sessionPresenter.isSessionExpired(nowMs)
    val requiredPermissions = remember { requiredWifiDirectPermissions() }
    var hasPermission by remember { mutableStateOf(hasRequiredPermissions(appContext)) }
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
            receivedFiles = receivedFiles
        )
    )

    val feedbackDeduplicator = remember { FeedbackDeduplicator() }
    val latestLanConnected by rememberUpdatedState(routeState.lanConnected)
    val latestLanDeviceLabel by rememberUpdatedState(routeState.latestLanDeviceLabel)
    val latestTargetIpInput by rememberUpdatedState(targetIpInput)
    val latestSessionDeviceLabel by rememberUpdatedState(routeState.latestSessionDeviceLabel)

    fun persistUxPreferences(next: UxPreferences) {
        uxPreferences = UxPreferencesStore.save(appContext, next)
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
            lanConnected = latestLanConnected,
            permissionAlreadyRequested = true
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

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        shareImportPresenter.importPickedUris(uris)
    }

    fun handleOutboundResult(result: P2pOutboundOrchestrationResult) {
        applyOutboundResult(
            result = result,
            clearChatDraft = { chatDraft = "" },
            pushFeedback = ::pushFeedback
        )
    }

    fun handleLanSuggestedTarget(suggestedIp: String?) {
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

    fun applyWifiPermissionPlan(permissionAlreadyRequested: Boolean) {
        val plan = resolveWifiPermissionPlan(
            permissionGranted = hasPermission,
            lanConnected = latestLanConnected,
            permissionAlreadyRequested = permissionAlreadyRequested
        )
        if (plan.refreshWifiDirectState) {
            backend.refreshWifiDirectState()
        }
        if (plan.markPermissionMissing) {
            backend.markWifiDirectPermissionMissing()
        }
        if (plan.requestPermission) {
            wifiPermissionAsked = true
            wifiPermissionLauncher.launch(requiredPermissions)
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
        onConnectionHintTargetResolved = { targetIpInput = it }
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
            setChatDraft = { chatDraft = it },
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
            requestWifiPermissions = { wifiPermissionLauncher.launch(requiredPermissions) },
            pickFiles = { pickFileLauncher.launch(arrayOf("*/*")) }
        )
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            var topModeMenuExpanded by rememberSaveable { mutableStateOf(false) }
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Box {
                        Column(
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                topModeMenuExpanded = true
                            }
                        ) {
                            Text(
                                "Qetara",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when (routeState.uiState.connectionViewMode) {
                                    ConnectionViewMode.ADVANCED -> "Completo · local-first"
                                    else -> "${routeState.uiState.activeConnectionMode.title} · local-first"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { padding ->
        RenderP2pScreen(
            state = routeState.uiState,
            wiring = screenWiring,
            modifier = Modifier.padding(padding)
        )
    }
}
