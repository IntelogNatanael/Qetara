package com.example.wifidrop

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import org.json.JSONArray
import org.json.JSONObject

/** Production tab, constrained app-sized shell, process-only fixtures and real Android IME. */
internal class MobileMessagesUiScenario(
    private val instrumentation: Instrumentation,
    private val expectedLanguage: String? = null,
    private val requestedFontScale: String? = null
) {
    private val ui = MobileUiScenarioSupport(instrumentation)
    private var viewport = Rect()
    private var historyCharacterBounds = emptyList<RectF>()
    private var recipientCharacterBounds = emptyList<RectF>()
    private var actionCharacterBounds = emptyMap<String, List<RectF>>()
    private var connectActions = 0
    private var sendActions = 0
    private var joinActions = 0
    private var lastSent = ""
    private var lastDeleted: String? = null
    private var renderGeneration = 0
    private var originalActivityConfiguration: Configuration? = null
    @Volatile private var currentFixture = "startup"
    private val scrollEvents = ConcurrentLinkedQueue<JSONObject>()
    private val viewInstances = JSONArray()
    private val listLayouts = JSONArray()
    private val failureDiagnostics = JSONArray()
    private var observedListState: LazyListState? = null
    private val fixture = mutableStateOf(chatFixture(false))

    fun run() {
        val result = Bundle()
        val checks = mutableListOf<String>()
        var runScales = listOf(1f)
        var activity: FlashActivity? = null
        var directory: File? = null
        try {
            val scale = requestedFontScale?.toFloatOrNull() ?: if (requestedFontScale == null) 1f else null
            require(scale != null && scale in FONT_SCALES) { "font-scale must be 1.0, 1.6 or 2.0" }
            runScales = listOf(scale)
            result.putFloat("font_scale", scale)
            check(FlashAndroidRuntime.state.value.phase == FlashAndroidPhase.OFF) { "Close Flash first" }
            val context = instrumentation.targetContext
            instrumentation.uiAutomation.setOnAccessibilityEventListener { event ->
                if (event.packageName?.toString() == context.packageName &&
                    event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                    scrollEvents.add(JSONObject().put("fixture", currentFixture)
                        .put("event_time", event.eventTime).put("from_index", event.fromIndex)
                        .put("to_index", event.toIndex).put("item_count", event.itemCount)
                        .put("scroll_y", event.scrollY).put("max_scroll_y", event.maxScrollY)
                        .put("class", event.className?.toString()))
                }
            }
            expectedLanguage?.let { expected ->
                check(context.resources.configuration.locales[0].language == expected) {
                    "Expected $expected; effective locales ${context.resources.configuration.locales.toLanguageTags()}"
                }
            }
            directory = File(context.getExternalFilesDir(null) ?: context.cacheDir,
                "qa/mobile-messages-${System.currentTimeMillis()}").apply { check(mkdirs()) }
            result.putString("output_directory", directory.absolutePath)
            result.putString("effective_language", context.resources.configuration.locales.toLanguageTags())
            val host = instrumentation.startActivitySync(Intent(context, FlashActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as FlashActivity
            activity = host
            originalActivityConfiguration = Configuration(host.resources.configuration)
            // Each invocation exercises one scale in a fresh instrumentation process. Changing
            // resource scale after a prior IME session is a different transition, not a fresh launch.
            for (scale in runScales) {
                render(host, scale, connected = false)
                ui.await("laid out tab") { !viewport.isEmpty }
                var firstIndex: Int? = null
                var firstOffset: Int? = null
                onMain {
                    firstIndex = observedListState?.firstVisibleItemIndex
                    firstOffset = observedListState?.firstVisibleItemScrollOffset
                }
                // Assertions run on the instrumentation thread, so failure reaches catch/finally.
                check(firstIndex == 0 && firstOffset == 0) {
                    "Fresh disconnected history starts at index=$firstIndex, offset=$firstOffset"
                }
                val headerBounds = ui.bounds(ui.assertInside(appString(R.string.msg_lan_direct), viewport))
                ui.assertInside(appString(R.string.msg_choose_device), viewport)
                ui.assertInside(appString(R.string.msg_go_connect), viewport)
                ui.click(appString(R.string.msg_go_connect))
                ui.await("connect callback") { connectActions == checks.count { it.startsWith("disconnected") } + 1 }
                ui.capture(host, directory, "disconnected-context-${scale}x")
                ui.scrollUntilTextFullyVisible(HISTORY_TEXT, viewport, historyCharacterBounds)
                check(ui.bounds(ui.assertInside(appString(R.string.msg_lan_direct), viewport)) == headerBounds) {
                    "Chat header moved while scrolling the saved history"
                }
                ui.capture(host, directory, "disconnected-history-${scale}x")
                checks += "disconnected_${scale}x_fixed_title_connect_visible_full_history_reachable"

                render(host, scale, connected = true)
                val connectedHeaderLabel = if (scale >= 1.5f) "Equipo QA" else appString(R.string.msg_lan_direct)
                val connectedHeaderBounds = ui.bounds(ui.assertInside(connectedHeaderLabel, viewport))
                if (scale >= 1.5f) ui.assertFullyVisibleText(connectedHeaderLabel, viewport, recipientCharacterBounds)
                ui.capture(host, directory, "connected-context-${scale}x")
                ui.scrollUntilTextFullyVisible(HISTORY_TEXT, viewport, historyCharacterBounds)
                check(ui.bounds(ui.assertInside(connectedHeaderLabel, viewport)) == connectedHeaderBounds) {
                    "Connected chat header moved while scrolling the saved history"
                }
                if (scale >= 1.5f) ui.assertFullyVisibleText(connectedHeaderLabel, viewport, recipientCharacterBounds)
                ui.capture(host, directory, "connected-history-${scale}x")
                val editor = checkNotNull(ui.nodes().firstOrNull { it.isEditable && it.isVisibleToUser })
                ui.click(editor)
                ui.await("real IME visible", 7_000) {
                    var visible = false
                    onMain {
                        visible = ViewCompat.getRootWindowInsets(host.window.decorView)
                            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    }
                    visible
                }
                ui.edit(DRAFT_TEXT)
                ui.await("draft entered intact") { fixture.value.chatDraft == DRAFT_TEXT }
                val send = ui.assertInside(appString(R.string.msg_send), viewport)
                val editableBounds = ui.bounds(checkNotNull(ui.nodes().firstOrNull { it.isEditable && it.isVisibleToUser }))
                check(viewport.contains(editableBounds)) { "Composer clipped by keyboard: $editableBounds in $viewport" }
                check(ui.visibleAppFrame(host).contains(ui.bounds(send))) { "Send action below the IME" }
                ui.capture(host, directory, "connected-keyboard-${scale}x")
                ui.click(send)
                ui.await("send callback") { lastSent == DRAFT_TEXT }
                check(sendActions == checks.count { it.startsWith("connected") } + 1)
                checks += "connected_${scale}x_real_ime_editor_send_visible_exact_callback"
                onMain {
                    androidx.core.view.WindowCompat.getInsetsController(host.window, host.window.decorView)
                        .hide(WindowInsetsCompat.Type.ime())
                }
                ui.await("IME hidden before next viewport") {
                    var hidden = false
                    onMain {
                        hidden = ViewCompat.getRootWindowInsets(host.window.decorView)
                            ?.isVisible(WindowInsetsCompat.Type.ime()) != true
                    }
                    hidden
                }
            }
            render(host, scale, connected = false, global = true)
            ui.scrollTo(appString(R.string.msg_join_wifi_channel))
            ui.assertInside(appString(R.string.msg_join_wifi_channel), viewport)
            ui.click(appString(R.string.msg_join_wifi_channel))
            ui.await("join callback") { joinActions == 1 }
            ui.capture(host, directory, "channel-context-${scale}x")
            ui.scrollUntilTextFullyVisible(HISTORY_TEXT, viewport, historyCharacterBounds)
            ui.capture(host, directory, "channel-history-join-${scale}x")
            checks += "unjoined_channel_${scale}x_join_and_saved_history_reachable"
            for (scale in runScales) {
                assertUnconfirmedRecipient(host, scale, directory, checks)
                assertLatestMessageMenu(host, scale, directory, checks)
                assertLongComposer(host, scale, false, directory, checks)
                assertLongComposer(host, scale, true, directory, checks)
                assertMultilineDeletePreview(host, scale, directory, checks)
            }
            result.putString("result", "PASS")
        } catch (failure: Throwable) {
            result.putString("result", "FAIL")
            result.putString("failure", failure.stackTraceToString())
            if (activity != null && directory != null) {
                recordListLayout("failure-before-diagnostic")
                runCatching { ui.capture(activity, directory, "failure-app-only") }
                runCatching { diagnoseBackwardScroll(activity, directory) }
                    .onFailure { failureDiagnostics.put(JSONObject().put("diagnostic_failure", it.stackTraceToString())) }
            }
        } finally {
            instrumentation.uiAutomation.setOnAccessibilityEventListener(null)
            var fontScaleRestored = false
            val cleanupFailure = runCatching {
                onMain {
                    originalActivityConfiguration?.let { original ->
                        activity?.resources?.let { resources ->
                            @Suppress("DEPRECATION")
                            resources.updateConfiguration(original, resources.displayMetrics)
                            fontScaleRestored = resources.configuration.fontScale == original.fontScale
                        }
                    }
                    activity?.finish()
                }
            }.exceptionOrNull()
            if (cleanupFailure != null) {
                result.putString("result", "FAIL")
                result.putString("cleanup_failure", cleanupFailure.stackTraceToString())
            }
            instrumentation.waitForIdleSync()
            directory?.let { File(it, "manifest.json").writeText(JSONObject()
                .put("source", "Production P2pMessagesTab and bottom navigation; synthetic state; toolbar height formula and 16 dp content padding from P2pScreenRoute/P2pScreen")
                .put("maximum_window_dp", "360 x 740; real IME; toolbar and navigation hide with IME as in production")
                .put("font_scales", JSONArray(runScales)).put("font_scale_scope", "one scale per instrumentation process; no cross-scale transition asserted")
                .put("long_draft_utf16_length", LONG_DRAFT.length)
                .put("network", "none").put("persistent_preferences_changed", false)
                .put("clipboard_changed", false)
                .put("fresh_composition_each_fixture", true)
                .put("fresh_compose_view_each_fixture", true).put("compose_view_instances", viewInstances)
                .put("scroll_events", JSONArray(scrollEvents.toList()))
                .put("list_layouts", listLayouts).put("failure_diagnostics", failureDiagnostics)
                .put("activity_font_scale_restored", fontScaleRestored)
                .put("effective_language", result.getString("effective_language"))
                .put("result", result.getString("result")).put("checks", checks)
                .put("failure", result.getString("failure"))
                .put("cleanup_failure", result.getString("cleanup_failure"))
                .put("assertions", ui.assertions).put("captures", ui.captures).toString(2)) }
            result.putBoolean("activity_font_scale_restored", fontScaleRestored)
            result.putString("checks", checks.joinToString(","))
            result.putInt("checks_passed", checks.size)
            instrumentation.finish(if (result.getString("result") == "PASS") Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        }
    }

    /** Never let a test assertion escape onto Android's main Looper and bypass the manifest/cleanup. */
    private fun onMain(block: () -> Unit) {
        var outcome: Result<Unit>? = null
        instrumentation.runOnMainSync { outcome = runCatching(block) }
        checkNotNull(outcome).getOrThrow()
    }

    private fun recordListLayout(stage: String) {
        onMain {
            observedListState?.let { state ->
                val layout = state.layoutInfo
                listLayouts.put(JSONObject().put("stage", stage).put("fixture", currentFixture)
                    .put("first_visible_index", state.firstVisibleItemIndex)
                    .put("first_visible_offset", state.firstVisibleItemScrollOffset)
                    .put("can_scroll_backward", state.canScrollBackward).put("can_scroll_forward", state.canScrollForward)
                    .put("viewport_start", layout.viewportStartOffset).put("viewport_end", layout.viewportEndOffset)
                    .put("viewport_size", layout.viewportSize.toString()).put("total_items", layout.totalItemsCount)
                    .put("screen_viewport", viewport.flattenToString())
                    .put("items", JSONArray(layout.visibleItemsInfo.map { item ->
                        JSONObject().put("index", item.index).put("key", item.key.toString())
                            .put("offset", item.offset).put("size", item.size)
                    })))
            }
        }
    }

    /** Evidence only after the original failure; revealing text never reclassifies the run as PASS. */
    private fun diagnoseBackwardScroll(activity: Activity, directory: File) {
        val candidates = ui.nodes().filter { it.isScrollable && it.isVisibleToUser }
        failureDiagnostics.put(JSONObject().put("fixture", currentFixture)
            .put("title_visible_before", ui.matching(appString(R.string.msg_choose_device)) != null)
            .put("scroll_containers", JSONArray(candidates.map { node ->
                JSONObject().put("bounds", ui.bounds(node).flattenToString())
                    .put("class", node.className?.toString())
                    .put("actions", JSONArray(node.actionList.map { it.id }))
            })))
        val accepted = candidates.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
        SystemClock.sleep(400)
        instrumentation.waitForIdleSync()
        recordListLayout("failure-after-backward-action")
        failureDiagnostics.put(JSONObject().put("backward_action_accepted", accepted)
            .put("title_visible_after", ui.matching(appString(R.string.msg_choose_device)) != null))
        ui.capture(activity, directory, "failure-after-backward-diagnostic")
    }

    private fun assertUnconfirmedRecipient(
        activity: FlashActivity, scale: Float, directory: File, checks: MutableList<String>
    ) {
        render(activity, scale, connected = true) { state ->
            state.copy(chatMessages = emptyList(), chatDraft = DRAFT_TEXT,
                chatSessionReady = false, sessionReady = false)
        }
        ui.scrollTo(appString(R.string.msg_confirm_receiver_session))
        check(ui.nodes().none { it.text?.toString() == appString(R.string.msg_ready) ||
            it.text?.toString() == appString(R.string.msg_device_ready_hint, "Equipo QA") }) {
            "An unconfirmed recipient was presented as ready to send"
        }
        fun sendActionEnabled(): Boolean? {
            var action: AccessibilityNodeInfo? = ui.matching(appString(R.string.msg_send))
            while (action != null) {
                // parent can return an AccessibilityNodeInfo cached before the recomposition.
                // Refresh every ancestor before reading either its actions or enabled state.
                if (!action.refresh()) return null
                if (action.isClickable || action.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }) {
                    return action.isEnabled
                }
                action = action.parent
            }
            return null
        }
        ui.await("unconfirmed recipient has a disabled Send action") { sendActionEnabled() == false }
        ui.click(checkNotNull(ui.nodes().firstOrNull { it.isEditable && it.isVisibleToUser }))
        ui.await("real IME with unconfirmed recipient", 7_000) { imeVisible(activity) }
        val frame = ui.visibleAppFrame(activity)
        val confirmLabel = appString(R.string.msg_confirm_session_connect)
        assertActionText(confirmLabel, frame)
        ui.assertInside(confirmLabel, viewport)
        ui.assertInside(appString(R.string.msg_send), viewport)
        ui.await("keyboard preserves disabled Send until confirmation") { sendActionEnabled() == false }
        ui.capture(activity, directory, "direct-unconfirmed-ime-${scale}x")
        val beforeConnect = connectActions
        ui.click(confirmLabel)
        ui.await("confirmation action opens Connect") { connectActions == beforeConnect + 1 }
        check(fixture.value.chatDraft == DRAFT_TEXT) { "Opening Connect changed the draft" }

        // Only confirmation changes; keep the route, draft, composition and real keyboard.
        onMain {
            fixture.value = fixture.value.copy(chatSessionReady = true, sessionReady = true)
        }
        ui.await("confirmed recipient removes the confirmation action") { ui.matching(confirmLabel) == null }
        ui.await("confirmed recipient enables the actual Send action") { sendActionEnabled() == true }
        val beforeSend = sendActions
        ui.click(appString(R.string.msg_send))
        ui.await("confirmed recipient sends the unchanged draft") {
            sendActions == beforeSend + 1 && lastSent == DRAFT_TEXT
        }
        ui.capture(activity, directory, "direct-confirmed-ime-${scale}x")
        hideIme(activity)
        render(activity, scale, connected = true) { it.copy(chatMessages = emptyList(), chatDraft = DRAFT_TEXT) }
        ui.scrollTo(appString(R.string.msg_ready))
        ui.visible(appString(R.string.msg_ready))
        val latestLabel = appString(R.string.msg_latest_message)
        check(ui.matching(latestLabel) == null) { "Empty history displays a latest-message overlay" }
        ui.click(appString(R.string.msg_more_actions))
        ui.visible(appString(R.string.msg_clear_text))
        check(ui.matching(latestLabel) == null) { "Empty history offers a nonexistent latest message" }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        ui.await("empty-history menu dismissed") { ui.matching(appString(R.string.msg_clear_text)) == null }
        check(fixture.value.chatDraft == DRAFT_TEXT) { "Opening the empty-history menu changed the draft" }
        checks += "direct_${scale}x_route_without_confirmation_not_ready_ime_guidance_exact_draft_then_confirmed_send"
    }

    private fun assertLatestMessageMenu(
        activity: FlashActivity, scale: Float, directory: File, checks: MutableList<String>
    ) {
        val olderText = "Anterior QA"
        val draft = "Borrador QA: ñ 漢字."
        render(activity, scale, connected = true) { state ->
            val message = state.chatMessages.single()
            state.copy(chatDraft = draft, chatMessages = (0..5).map { index ->
                message.copy(id = "qa-history-$index", text = when (index) {
                    0 -> HISTORY_TEXT
                    5 -> olderText
                    else -> "Mensaje QA $index"
                })
            })
        }
        // Reach older content through the actual scroll action, without assigning list state.
        ui.scrollTo(olderText)
        ui.await("older messages reached") {
            var olderVisible = false
            onMain { olderVisible = checkNotNull(observedListState).firstVisibleItemIndex > 0 }
            olderVisible
        }
        val latestLabel = appString(R.string.msg_latest_message)
        check(ui.matching(latestLabel) == null) { "Latest-message action overlays history outside the menu" }
        val olderBounds = ui.bounds(ui.visible(olderText))
        val moreBounds = ui.bounds(ui.assertInside(appString(R.string.msg_more_actions), viewport))
        check(!Rect.intersects(olderBounds, moreBounds)) { "Composer More action overlaps older message content" }
        ui.capture(activity, directory, "older-history-no-overlay-${scale}x")
        ui.click(appString(R.string.msg_more_actions))
        ui.assertInside(latestLabel, ui.visibleAppFrame(activity))
        ui.capture(activity, directory, "latest-message-menu-${scale}x")
        ui.click(latestLabel)
        ui.await("latest message reached from More") {
            var atStart = false
            onMain {
                val state = checkNotNull(observedListState)
                atStart = state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0
            }
            atStart && ui.matching(appString(R.string.msg_clear_text)) == null
        }
        check(fixture.value.chatDraft == draft) { "Jumping to the latest message changed the draft" }
        ui.scrollUntilTextFullyVisible(HISTORY_TEXT, viewport, historyCharacterBounds)
        check(ui.matching(latestLabel) == null) { "Latest-message action still overlays the returned message" }
        val historyBounds = ui.bounds(ui.visible(HISTORY_TEXT))
        check(!Rect.intersects(historyBounds, ui.bounds(ui.visible(appString(R.string.msg_more_actions))))) {
            "Composer More action overlaps the latest message"
        }
        ui.capture(activity, directory, "latest-history-no-overlay-${scale}x")
        checks += "latest_${scale}x_more_menu_reaches_latest_preserves_draft_no_history_overlay"
    }

    private fun assertLongComposer(
        activity: FlashActivity, scale: Float, global: Boolean, directory: File, checks: MutableList<String>
    ) {
        val attachments = (1..3).map { "adjunto-$it-con-nombre-largo-para-la-comprobación.pdf" }
        render(activity, scale, connected = true, global = global) { state ->
            state.copy(selectedFileNames = attachments, selectedFilesCount = attachments.size,
                globalLanJoined = global, globalChatPeerCount = if (global) 1 else 0)
        }
        ui.click(checkNotNull(ui.nodes().firstOrNull { it.isEditable && it.isVisibleToUser }))
        ui.await("real IME for long composer", 7_000) { imeVisible(activity) }
        ui.edit(LONG_DRAFT)
        ui.await("all 2000 characters retained") { fixture.value.chatDraft == LONG_DRAFT }
        val frame = ui.visibleAppFrame(activity)
        val editor = checkNotNull(ui.nodes().firstOrNull { it.isEditable && it.isVisibleToUser })
        check(viewport.contains(ui.bounds(editor)) && frame.contains(ui.bounds(editor))) {
            "Long-text editor is clipped by the viewport or keyboard"
        }
        val sendLabel = appString(R.string.msg_send_attachment_count, attachments.size)
        assertActionText(sendLabel, frame)
        ui.assertInside(appString(R.string.msg_attach_files), frame)
        ui.assertInside(appString(R.string.msg_more_actions), frame)
        val beforeSend = sendActions
        ui.click(sendLabel)
        ui.await("long draft send callback") { sendActions == beforeSend + 1 && lastSent == LONG_DRAFT }
        check(fixture.value.selectedFileNames == attachments && fixture.value.chatDraft == LONG_DRAFT) {
            "Rendering or invoking the fixture callback changed the payload"
        }
        check(imeVisible(activity)) { "Real IME disappeared before the long-composer capture" }
        ui.capture(activity, directory, "${if (global) "channel" else "direct"}-2000-attachments-ime-${scale}x")
        ui.click(appString(R.string.msg_more_actions))
        val menuFrame = ui.visibleAppFrame(activity)
        ui.assertInside(appString(R.string.msg_clear_text), menuFrame)
        ui.assertInside(appString(R.string.msg_remove_attachments), menuFrame)
        ui.capture(activity, directory, "${if (global) "channel" else "direct"}-2000-attachments-menu-${scale}x")
        // Dismiss only our menu; do not activate a destructive menu item or operate the keyboard app.
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        ui.await("composer menu dismissed") { ui.matching(appString(R.string.msg_clear_text)) == null }
        hideIme(activity)
        checks += "${if (global) "channel" else "direct"}_${scale}x_2000_characters_attachments_real_ime_visible_actions_exact_callback"
    }

    private fun assertMultilineDeletePreview(
        activity: FlashActivity, scale: Float, directory: File, checks: MutableList<String>
    ) {
        render(activity, scale, connected = false) { state ->
            state.copy(chatMessages = state.chatMessages.map { it.copy(text = MULTILINE_MESSAGE) })
        }
        val original = fixture.value.chatMessages.single()
        fun openDeleteDialog() {
            ui.scrollTo(appString(R.string.msg_message_actions))
            ui.click(appString(R.string.msg_message_actions))
            ui.click(appString(R.string.msg_delete_from_device))
            val frame = ui.visibleAppFrame(activity)
            ui.assertInside(appString(R.string.msg_delete_message), frame)
            ui.assertInside(appString(R.string.msg_delete_message_body), frame)
            // The preview intentionally abbreviates content. Require its visible node and both
            // complete decision labels to remain in the dialog rather than asserting a line count.
            ui.assertInside(MULTILINE_MESSAGE.take(160), frame)
            assertActionText(appString(R.string.msg_cancel), frame)
            assertActionText(appString(R.string.msg_delete), frame)
        }
        openDeleteDialog()
        ui.capture(activity, directory, "delete-multiline-preview-${scale}x")
        ui.click(appString(R.string.msg_cancel))
        ui.await("delete cancelled") { ui.matching(appString(R.string.msg_delete_message)) == null }
        check(lastDeleted == null && fixture.value.chatMessages.single() == original) {
            "Cancelling the abbreviated preview changed the original history"
        }
        openDeleteDialog()
        ui.click(appString(R.string.msg_delete))
        ui.await("delete callback targets the full original message") { lastDeleted == original.id }
        check(fixture.value.chatMessages.single().text == MULTILINE_MESSAGE) {
            "The preview modified the original text before the delete callback"
        }
        checks += "delete_${scale}x_multiline_preview_decisions_visible_cancel_preserves_original_correct_id"
    }

    private fun assertActionText(label: String, frame: Rect) {
        ui.assertFullyVisibleText(label, frame, actionCharacterBounds.getValue(label))
    }

    private fun imeVisible(activity: Activity): Boolean {
        var visible = false
        onMain {
            visible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }

    private fun hideIme(activity: Activity) {
        onMain {
            androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        ui.await("real IME hidden") { !imeVisible(activity) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun render(
        activity: FlashActivity, scale: Float, connected: Boolean, global: Boolean = false,
        transform: (P2pScreenState) -> P2pScreenState = { it }
    ) {
        onMain {
            // Activity.setContent reuses its ComposeView, including the view's saveable-state ID.
            // A fresh fixture needs a fresh root view, not just a disposed composition, so previous
            // scroll anchors, keyboard focus and pending bring-into-view work cannot be reused.
            currentFixture = "scale=$scale;connected=$connected;global=$global;generation=${renderGeneration + 1}"
            activity.window.decorView.clearFocus()
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            val previousView = content.getChildAt(0)
            (previousView as? AbstractComposeView)?.disposeComposition()
            content.removeAllViews()
            // Dialogs host their own ComposeView and read their density from the Activity context.
            // LocalDensity alone scales the screen while leaving those windows at the OS default.
            // Change only this Activity's resources, restore them in finally, and never write Settings.
            val configuration = Configuration(activity.resources.configuration).apply { fontScale = scale }
            @Suppress("DEPRECATION")
            activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
            check(activity.resources.configuration.fontScale == scale)
            fixture.value = transform(chatFixture(connected).let { state ->
                if (global) state.copy(chatChannel = ChatChannel.GLOBAL,
                    chatMessages = state.chatMessages.map { it.copy(scope = ChatMessageScope.GLOBAL_LAN) }) else state
            })
            renderGeneration++
            viewport = Rect()
            observedListState = null
            historyCharacterBounds = emptyList()
            recipientCharacterBounds = emptyList()
            lastSent = ""
            lastDeleted = null
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    QetaraTheme {
                        val currentDensity = LocalDensity.current
                        // Use the actual bundled font, text style, resolver and test density.
                        // An unconstrained reference supplies glyph metrics, not a guessed dp floor.
                        val textMeasurer = rememberTextMeasurer()
                        val measuredHistory = textMeasurer.measure(
                            AnnotatedString(HISTORY_TEXT), style = MaterialTheme.typography.bodyLarge,
                            softWrap = false
                        )
                        val measuredRecipient = textMeasurer.measure(AnnotatedString("Equipo QA"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), softWrap = false)
                        SideEffect {
                            historyCharacterBounds = List(HISTORY_TEXT.length) { index ->
                                measuredHistory.getBoundingBox(index).let {
                                    RectF(it.left, it.top, it.right, it.bottom)
                                }
                            }
                            recipientCharacterBounds = List("Equipo QA".length) { index ->
                                measuredRecipient.getBoundingBox(index).let { RectF(it.left, it.top, it.right, it.bottom) }
                            }
                        }
                        val actionMetrics = listOf(
                            appString(R.string.msg_send), appString(R.string.msg_send_attachment_count, 3),
                            appString(R.string.msg_confirm_session_connect),
                            appString(R.string.msg_delete), appString(R.string.msg_cancel)
                        ).associateWith { label ->
                            val measured = textMeasurer.measure(AnnotatedString(label),
                                style = MaterialTheme.typography.labelLarge, softWrap = false)
                            List(label.length) { index -> measured.getBoundingBox(index).let {
                                RectF(it.left, it.top, it.right, it.bottom)
                            } }
                        }
                        SideEffect { actionCharacterBounds = actionMetrics }
                        val keyboardVisible = WindowInsets.ime.getBottom(currentDensity) > 0
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Scaffold(
                            modifier = Modifier.widthIn(max = 360.dp).heightIn(max = 740.dp).fillMaxSize().imePadding(),
                            containerColor = MaterialTheme.colorScheme.background,
                            topBar = {
                                if (!keyboardVisible) {
                                    val brandStyle = if (scale >= 1.5f) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
                                    TopAppBar(
                                        expandedHeight = with(currentDensity) {
                                            (maxOf(40.dp, brandStyle.lineHeight.toDp()) + MaterialTheme.typography.labelSmall.lineHeight.toDp() + 16.dp).coerceAtLeast(64.dp)
                                        },
                                        title = {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    QetaraBrandIcon()
                                                    Text("Qetara", style = brandStyle)
                                                }
                                                Text(appString(R.string.conn_standard_wifi), modifier = Modifier.padding(start = 40.dp),
                                                    style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    )
                                }
                            }
                        ) { padding ->
                            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(UiSpaceM),
                                verticalArrangement = Arrangement.spacedBy(UiSpaceS)) {
                                key(renderGeneration, scale, connected, global) {
                                    val fixtureListState = rememberLazyListState()
                                    SideEffect {
                                        if (observedListState !== fixtureListState) {
                                            listLayouts.put(JSONObject().put("stage", "first-composition-commit")
                                                .put("fixture", currentFixture)
                                                .put("first_visible_index", fixtureListState.firstVisibleItemIndex)
                                                .put("first_visible_offset", fixtureListState.firstVisibleItemScrollOffset))
                                        }
                                        observedListState = fixtureListState
                                    }
                                    P2pMessagesTab(
                                        state = fixture.value, activeChannel = fixture.value.chatChannel,
                                        experience = P2pChatExperienceState(appString(R.string.conn_standard_wifi), "", "", false),
                                        onSetGlobalLanJoined = { if (it) joinActions++ }, onAutoDownloadChannelFilesChange = {},
                                        onOpenConnectTab = { connectActions++ },
                                        onChatDraftChange = { fixture.value = fixture.value.copy(chatDraft = it) },
                                        onSelectChatDirectPeer = {}, onDownloadChannelFileOffer = {},
                                        onPickFile = {}, onClearSelectedFiles = {},
                                        onSendMessage = { sendActions++; lastSent = fixture.value.chatDraft },
                                        onRetryMessage = {}, onCancelQueuedMessage = {}, onDeleteMessage = { lastDeleted = it },
                                        onClearMessages = {}, onSyncToken = {},
                                        listState = fixtureListState,
                                        modifier = Modifier.fillMaxWidth().weight(1f).onGloballyPositioned {
                                            val bounds = it.boundsInWindow()
                                            val offset = IntArray(2)
                                            activity.window.decorView.getLocationOnScreen(offset)
                                            viewport = Rect(bounds.left.toInt() + offset[0], bounds.top.toInt() + offset[1],
                                                bounds.right.toInt() + offset[0], bounds.bottom.toInt() + offset[1])
                                        }
                                    )
                                }
                                if (!keyboardVisible) {
                                    QetaraBottomNavigation(
                                        tabs = P2pMainTab.entries, selectedTabIndex = P2pMainTab.MESSAGES.ordinal,
                                        statusLabel = appString(R.string.conn_standard_wifi), onTabSelected = { _, _ -> }, tabLabel = { it.title },
                                        tabIcon = { when (it) {
                                            P2pMainTab.CONNECTION -> Icons.Rounded.Link
                                            P2pMainTab.SEND -> Icons.Rounded.AttachFile
                                            P2pMainTab.MESSAGES -> Icons.Rounded.ChatBubbleOutline
                                            P2pMainTab.CHANNEL -> Icons.Rounded.Wifi
                                            P2pMainTab.HISTORY -> Icons.Rounded.FolderOpen
                                        } }
                                    )
                                }
                            }
                        }
                        }
                    }
                }
            }
            val currentView = content.getChildAt(0)
            check(currentView != null && currentView !== previousView) { "Fixture reused the prior ComposeView" }
            viewInstances.put(JSONObject().put("fixture", currentFixture)
                .put("previous_view", System.identityHashCode(previousView))
                .put("current_view", System.identityHashCode(currentView)))
        }
        instrumentation.waitForIdleSync()
        ui.await("fixture viewport and real font metrics measured") {
            !viewport.isEmpty && historyCharacterBounds.size == HISTORY_TEXT.length && observedListState != null
        }
        recordListLayout("render-complete")
    }

    companion object {
        private val FONT_SCALES = listOf(1f, 1.6f, 2f)
        private const val HISTORY_TEXT = "Mensaje guardado de prueba"
        private const val DRAFT_TEXT = "Hola, Perú 👋\nSegunda línea: ñ, 漢字."
        private val LONG_DRAFT = "Línea Perú ñ 漢字 — texto sin cambios.\n".repeat(80).take(2_000)
        private val MULTILINE_MESSAGE = (1..80).joinToString("\n") { "L$it" }
        internal fun chatFixture(connected: Boolean): P2pScreenState {
            val now = System.currentTimeMillis()
            val peer = KnownPeerSnapshot("qa-peer", "Equipo QA", "192.0.2.20", true, false, now)
            return P2pScreenState(
                permissionGranted = true, permissionName = "", p2pEnabled = true,
                lanConnected = true, lanLocalIp = "192.0.2.10", lanScanStatus = "", lanScanning = false,
                statusMessage = "", quickHint = "", thisDeviceName = "Android QA", thisDeviceAddress = "",
                peers = emptyList(), connection = null, directDiscovering = false, directConnecting = false,
                directCreatingGroup = false, authToken = "", sessionPin = "", sessionExpiresAtMs = now + 600_000,
                nowMs = now, sessionExpired = false, localDeviceIdShort = "qa", tokenSyncStatus = "",
                targetIp = "", suggestedTargetIp = null, resolvedTargetIp = null, resolvedTargetLabel = null,
                resolvedTargetMode = ConnectionMode.LAN, directTargetIp = null, directTargetLabel = null,
                chatDirectTargetIp = peer.ip.takeIf { connected }, chatDirectTargetIps = if (connected) listOf(peer.ip) else emptyList(),
                chatDirectTargetLabel = peer.label.takeIf { connected }, chatDirectTargetMode = ConnectionMode.LAN,
                chatDirectAvailablePeers = emptyList(), lastPeerLabel = null,
                knownServicePeers = if (connected) listOf(peer) else emptyList(), pendingTrust = null,
                pendingCredentialShare = null, trustedPeers = emptyList(), favoritePeers = emptyList(),
                favoriteSuggestionPeerId = null, favoriteSuggestionLabel = null, lastSendTargetIp = null,
                lastSendTargetLabel = null, receiving = false, receiverStatus = "", receiverProgress = null,
                receiverFileName = null, receiverInstantBps = 0, receiverAverageBps = 0, receiverEtaSeconds = null,
                receiverFailureCause = null, receivedFiles = emptyList(), selectedFileNames = emptyList(),
                selectedFilesCount = 0, sending = false, sendActiveCount = 0, sendBatchTotal = 0,
                sendBatchCompleted = 0, sendBatchFailed = 0, sendBatchCanceled = 0, sendQueue = emptyList(),
                sendProgress = 0f, sendStatus = "", sendInstantBps = 0, sendAverageBps = 0, sendEtaSeconds = null,
                sendFailureCause = null, shareImportStatus = "", paused = false, history = emptyList(),
                chatChannel = ChatChannel.DIRECT, globalLanJoined = false, globalChatPeerCount = 0,
                directChannelPeerCount = 0, chatDraft = "", chatMessages = listOf(ChatMessageEntry("qa-history",
                    ChatMessageDirection.INCOMING, ChatMessageScope.DIRECT, ChatMessageStatus.RECEIVED,
                    HISTORY_TEXT, now, "Equipo QA", "192.0.2.20", null)), messageStatus = "",
                activeConnectionMode = ConnectionMode.LAN, connectionViewMode = ConnectionViewMode.LAN,
                wifiDirectModeEnabled = false, lanModeEnabled = true, fontScale = 1f, compactMode = false,
                vibrateOnConnect = false, vibrateOnError = false, silentSuccessFeedback = true,
                autoDownloadChannelFiles = false, chatSessionReady = connected, sessionReady = connected
            )
        }
    }
}
