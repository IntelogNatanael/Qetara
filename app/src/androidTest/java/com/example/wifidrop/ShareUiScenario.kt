package com.example.wifidrop

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.wifidrop.presentation.P2pAttachmentContext
import java.io.File
import org.json.JSONObject

/** Production Compartir screen; synthetic names only, no URIs, transfers or persisted settings. */
internal class ShareUiScenario(
    private val instrumentation: Instrumentation,
    private val expectedLanguage: String? = null
) {
    private val ui = MobileUiScenarioSupport(instrumentation)
    private var viewport = Rect()
    private var nameCharacterBounds = emptyMap<String, List<RectF>>()
    private var actionCharacterBounds = emptyMap<String, List<RectF>>()
    private var sendActions = 0
    private var originalActivityConfiguration: Configuration? = null

    fun run() {
        val result = Bundle()
        val checks = mutableListOf<String>()
        val originalFlash = FlashAndroidRuntime.state.value
        var activity: FlashActivity? = null
        var directory: File? = null
        try {
            check(originalFlash.phase == FlashAndroidPhase.OFF && !originalFlash.importing) {
                "Close Flash and finish imports before this isolated fixture"
            }
            val context = instrumentation.targetContext
            val language = context.resources.configuration.locales[0].language
            expectedLanguage?.let { check(language == it) { "Expected $it; effective language $language" } }
            directory = File(context.getExternalFilesDir(null) ?: context.cacheDir,
                "qa/share-ui-${System.currentTimeMillis()}").apply { check(mkdirs()) }
            result.putString("output_directory", directory.absolutePath)
            result.putString("effective_language", context.resources.configuration.locales.toLanguageTags())
            val host = instrumentation.startActivitySync(Intent(context, FlashActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as FlashActivity
            activity = host
            originalActivityConfiguration = Configuration(host.resources.configuration)
            for (scale in FONT_SCALES) {
                for (count in listOf(1, 3, 5)) {
                    val names = (1..count).map {
                        "$it-informe-extenso-del-proyecto-Qetara-con-nombre-único-2026-10-30.pdf"
                    }
                    render(host, scale, names)
                    val reviewLabel = appQuantityString(R.plurals.shell_review_files, count, count)
                    // Before the regression fix this action did not exist for selections of 1–3 files.
                    ui.scrollUntilTextFullyVisible(reviewLabel, viewport, actionCharacterBounds.getValue(reviewLabel))
                    ui.click(reviewLabel)
                    val frame = ui.visibleAppFrame(host)
                    ui.assertInside(appString(R.string.shell_prepared_files), frame)
                    val doneLabel = appString(R.string.shell_done)
                    ui.assertFullyVisibleText(doneLabel, frame, actionCharacterBounds.getValue(doneLabel))
                    names.forEachIndexed { index, name ->
                        ui.scrollUntilTextFullyVisible(name, frame, nameCharacterBounds.getValue(name))
                        ui.capture(host, directory, "review-${count}-files-${scale}x-item-${index + 1}")
                    }
                    ui.click(doneLabel)
                    ui.await("review dialog dismissed") { ui.matching(appString(R.string.shell_prepared_files)) == null }
                    val sendLabel = appQuantityString(R.plurals.shell_send_files, count, count)
                    ui.scrollUntilTextFullyVisible(sendLabel, viewport, actionCharacterBounds.getValue(sendLabel))
                    val beforeSend = sendActions
                    ui.click(sendLabel)
                    ui.await("Compartir send callback") { sendActions == beforeSend + 1 }
                    ui.capture(host, directory, "share-${count}-files-${scale}x-send-reachable")
                    checks += "share_${count}_files_${scale}x_review_full_names_glyphs_done_and_send_reachable"
                }
            }
            result.putString("result", "PASS")
        } catch (failure: Throwable) {
            result.putString("result", "FAIL")
            result.putString("failure", failure.stackTraceToString())
            if (activity != null && directory != null) runCatching { ui.capture(activity, directory, "failure-app-only") }
        } finally {
            var fontScaleRestored = false
            instrumentation.runOnMainSync {
                originalActivityConfiguration?.let { original ->
                    activity?.resources?.let { resources ->
                        @Suppress("DEPRECATION")
                        resources.updateConfiguration(original, resources.displayMetrics)
                        fontScaleRestored = resources.configuration.fontScale == original.fontScale
                    }
                }
                activity?.finish()
            }
            instrumentation.waitForIdleSync()
            val stateUnchanged = FlashAndroidRuntime.state.value == originalFlash
            result.putBoolean("process_state_unchanged", stateUnchanged)
            result.putBoolean("activity_font_scale_restored", fontScaleRestored)
            if (!stateUnchanged && result.getString("result") == "PASS") {
                result.putString("result", "FAIL")
                result.putString("failure", "Fixture unexpectedly changed Flash process state")
            }
            directory?.let { File(it, "manifest.json").writeText(JSONObject()
                .put("source", "Production P2pScreen Compartir; process-only synthetic file names and callback recorder")
                .put("maximum_window_dp", "360 x 740; safe drawing insets")
                .put("font_scales", FONT_SCALES).put("file_counts", listOf(1, 3, 5))
                .put("effective_language", result.getString("effective_language"))
                .put("network", "none; no transfer success claimed")
                .put("persistent_preferences_changed", false).put("clipboard_changed", false)
                .put("fresh_composition_each_fixture", true)
                .put("activity_font_scale_restored", fontScaleRestored)
                .put("process_state_unchanged", stateUnchanged)
                .put("result", result.getString("result")).put("failure", result.getString("failure"))
                .put("checks", checks).put("assertions", ui.assertions).put("captures", ui.captures).toString(2)) }
            result.putString("checks", checks.joinToString(","))
            result.putInt("checks_passed", checks.size)
            instrumentation.finish(if (result.getString("result") == "PASS") Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        }
    }

    private fun render(activity: FlashActivity, scale: Float, names: List<String>) {
        val state = MobileMessagesUiScenario.chatFixture(connected = true).copy(
            authToken = "QETARAQA", sessionPin = "123456", targetIp = "192.0.2.20",
            resolvedTargetIp = "192.0.2.20", resolvedTargetLabel = "Equipo QA",
            selectedFileNames = names, selectedFilesCount = names.size,
            attachmentContext = P2pAttachmentContext.FILES, chatMessages = emptyList(),
            fontScale = 1f, sessionReady = true
        )
        instrumentation.runOnMainSync {
            activity.window.decorView.clearFocus()
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            (content.getChildAt(0) as? AbstractComposeView)?.disposeComposition()
            // Android dialogs create their own ComposeView from this context. A LocalDensity
            // override alone leaves the dialog at the system scale while enlarging its parent.
            // This process-only resource override is restored in finally; system Settings stay intact.
            val configuration = Configuration(activity.resources.configuration).apply { fontScale = scale }
            @Suppress("DEPRECATION")
            activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
            check(activity.resources.configuration.fontScale == scale)
            viewport = Rect()
            nameCharacterBounds = emptyMap()
            actionCharacterBounds = emptyMap()
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    QetaraTheme {
                        val textMeasurer = rememberTextMeasurer()
                        val nameMetrics = names.associateWith { text ->
                            val measured = textMeasurer.measure(AnnotatedString(text),
                                style = MaterialTheme.typography.bodyMedium, softWrap = false)
                            List(text.length) { index -> measured.getBoundingBox(index).let {
                                RectF(it.left, it.top, it.right, it.bottom)
                            } }
                        }
                        val actionMetrics = listOf(
                            appQuantityString(R.plurals.shell_review_files, names.size, names.size),
                            appQuantityString(R.plurals.shell_send_files, names.size, names.size),
                            appString(R.string.shell_done)
                        ).associateWith { text ->
                            val measured = textMeasurer.measure(AnnotatedString(text),
                                style = MaterialTheme.typography.labelLarge, softWrap = false)
                            List(text.length) { index -> measured.getBoundingBox(index).let {
                                RectF(it.left, it.top, it.right, it.bottom)
                            } }
                        }
                        SideEffect { nameCharacterBounds = nameMetrics; actionCharacterBounds = actionMetrics }
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            key(scale, names) {
                                P2pScreen(
                                    screenState = state,
                                    onRequestPermission = {}, onOpenWifiSettings = {}, onFontScaleChange = {},
                                    onCompactModeChange = {}, onVibrateOnConnectChange = {}, onVibrateOnErrorChange = {},
                                    onSilentSuccessFeedbackChange = {}, initialFocusStage = FocusStage.SEND,
                                    onFocusStageChange = {}, onConnectionViewModeChange = {}, onConnectionModeChange = {},
                                    onWifiDirectModeEnabledChange = {}, onLanModeEnabledChange = {},
                                    onStartHost = {}, onStartClient = {}, onScanLanPeers = {}, onCancelLanScan = {},
                                    onDisconnect = {}, onRefreshState = {}, onCancelConnect = {}, onConnectToPeer = {},
                                    onTokenChange = {}, onPinChange = {}, onGenerateToken = {}, onGeneratePin = {},
                                    onRenewSession = {}, onSetSessionEnabled = {}, onCopyToken = {}, onPasteToken = {},
                                    onSyncToken = {}, onConfirmManualSession = {}, onSyncFromPeerIp = {},
                                    onTargetIpChange = {}, onUseSuggestedTarget = {}, onPickFile = {},
                                    onClearSelectedFiles = {}, onAttachmentContextChange = {}, onSendFile = { sendActions++ },
                                    onRefreshReceived = {}, onShareReceivedFile = {}, onPauseTransfers = {},
                                    onResumeTransfers = {}, onCancelActiveTransfer = {}, onSendToLastTarget = {},
                                    onSetPeerFavorite = { _, _ -> }, onSetPeerAlias = { _, _ -> }, onForgetPeer = {},
                                    onPauseQueueItem = {}, onResumeQueueItem = {}, onCancelQueueItem = {},
                                    onMoveQueueItemUp = {}, onMoveQueueItemDown = {}, onChatChannelChange = {},
                                    onSetGlobalLanJoined = {}, onAutoDownloadChannelFilesChange = {}, onChatDraftChange = {},
                                    onSelectChatDirectPeer = {}, onDownloadChannelFileOffer = {}, onSendMessage = {},
                                    onRetryMessage = {}, onCancelQueuedMessage = {}, onDeleteMessage = {}, onClearMessages = {},
                                    onSaveSuggestedFavorite = {}, onSkipSuggestedFavorite = {}, onOpenDownloads = {},
                                    onTrustPeer = {}, onApproveCredentialShare = {}, onRejectCredentialShare = {},
                                    onOpenHistoryItem = {},
                                    modifier = Modifier.widthIn(max = 360.dp).heightIn(max = 740.dp).fillMaxSize()
                                        .safeDrawingPadding().onGloballyPositioned { coordinates ->
                                            val bounds = coordinates.boundsInWindow()
                                            val offset = IntArray(2)
                                            activity.window.decorView.getLocationOnScreen(offset)
                                            viewport = Rect(bounds.left.toInt() + offset[0], bounds.top.toInt() + offset[1],
                                                bounds.right.toInt() + offset[0], bounds.bottom.toInt() + offset[1])
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
        instrumentation.waitForIdleSync()
        ui.await("Compartir viewport and real font metrics") {
            !viewport.isEmpty && nameCharacterBounds.size == names.size && actionCharacterBounds.size == 3
        }
    }

    companion object {
        private val FONT_SCALES = listOf(1f, 1.6f, 2f)
    }
}
