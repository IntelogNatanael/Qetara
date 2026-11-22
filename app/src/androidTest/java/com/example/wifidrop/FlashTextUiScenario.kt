package com.example.wifidrop

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.wifidrop.protocol.flash.FLASH_TEXT_MAX_BYTES
import com.example.wifidrop.protocol.flash.FlashPeer
import com.example.wifidrop.protocol.flash.FlashState
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.json.JSONArray

/** Real FlashActivity/composer/clipboard actions, with no service or network and restored state. */
internal class FlashTextUiScenario(private val instrumentation: Instrumentation) {
    private val ui = MobileUiScenarioSupport(instrumentation)
    private var actionCharacterBounds = emptyMap<String, List<RectF>>()
    private var primaryActionColor = 0

    fun run(fileSendOnly: Boolean = false, composerOnly: Boolean = false, expectedLanguage: String? = null) {
        val result = Bundle()
        val original = FlashAndroidRuntime.state.value
        val checks = mutableListOf<String>()
        var activity: Activity? = null
        var directory: File? = null
        var originalClipboard: ClipData? = null
        var clipboardSaved = false
        val clipboard = instrumentation.targetContext.getSystemService(ClipboardManager::class.java)
        try {
            check(original.phase == FlashAndroidPhase.OFF && original.engine?.active != true && !original.importing) {
                "Close Flash and finish imports before this isolated fixture"
            }
            val context = instrumentation.targetContext
            val scenario = when {
                fileSendOnly -> "flash-file-send-ui"
                composerOnly -> "flash-composer-ui"
                else -> "flash-text-ui"
            }
            directory = File(context.getExternalFilesDir(null) ?: context.cacheDir,
                "qa/$scenario-${System.currentTimeMillis()}").apply { check(mkdirs()) }
            result.putString("output_directory", directory.absolutePath)
            result.putString("scenario", scenario)
            result.putString("effective_language", context.resources.configuration.locales.toLanguageTags())
            check(expectedLanguage == null || context.resources.configuration.locales[0].language == expectedLanguage) {
                "Expected $expectedLanguage; effective ${context.resources.configuration.locales.toLanguageTags()}"
            }
            val now = System.currentTimeMillis()
            val peer = FlashPeer("qa-peer", "Laptop 1", "192.0.2.20", 8989, now + 600_000)
            instrumentation.runOnMainSync {
                FlashAndroidRuntime.update { FlashAndroidState(phase = FlashAndroidPhase.ACTIVE,
                    engine = FlashState(active = true, expiresAtMs = now + 600_000, localId = "qa-local", peers = listOf(peer)),
                    deviceLabel = "Android 01", statusText = flashText(R.string.flash_active)) }
            }
            val host = instrumentation.startActivitySync(Intent(context, FlashActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as FlashActivity
            activity = host
            if (composerOnly) {
                instrumentation.runOnMainSync {
                    originalClipboard = clipboard.primaryClip
                    clipboardSaved = true
                }
                val attachment = File(directory, "archivo-qa.txt").apply { writeText("Adjunto sintético de interfaz.") }
                assertFocusedComposer(host, peer, attachment, directory, checks, clipboard)
                result.putString("result", "PASS")
                return
            }
            if (fileSendOnly) {
                val attachment = File(directory, "archivo-qa.txt").apply { writeText("Adjunto sintético de interfaz.") }
                assertFileSendReadiness(host, peer, attachment, directory, checks)
                result.putString("result", "PASS")
                return
            }
            assertActionVisible(appString(R.string.flash_attach_files), ui.visibleAppFrame(host), host, enabled = true)
            ui.assertInside(appString(R.string.flash_choose_receiver), ui.visibleAppFrame(host))
            checks += "attach_visible_and_enabled_before_choosing_receiver_without_scroll"
            instrumentation.runOnMainSync {
                FlashAndroidRuntime.update { it.copy(selectedPeer = peer) }
                originalClipboard = clipboard.primaryClip
                clipboardSaved = true
                clipboard.setPrimaryClip(ClipData.newPlainText("QA synthetic", SENT_TEXT))
            }
            ui.click(appString(R.string.flash_paste))
            ui.await("pasted Unicode/newlines preserved exactly") { FlashAndroidRuntime.state.value.textDraft == SENT_TEXT }
            ui.assertInside(appString(R.string.flash_send_text), ui.visibleAppFrame(host))
            ui.capture(host, directory, "unicode-draft")
            checks += "production_paste_preserves_unicode_newlines_and_spaces"

            // Short enough in UTF-16 to pass the Android clipboard precheck, too large in UTF-8.
            instrumentation.runOnMainSync {
                clipboard.setPrimaryClip(ClipData.newPlainText("QA oversized UTF-8", "é".repeat(FLASH_TEXT_MAX_BYTES / 2 + 1)))
            }
            ui.click(appString(R.string.flash_paste))
            ui.await("UTF-8 overflow error visible") {
                ui.nodes().any { it.text?.toString() == appString(R.string.flash_text_too_large_unchanged) }
            }
            check(FlashAndroidRuntime.state.value.textDraft == SENT_TEXT) { "Oversize paste changed the draft" }
            checks += "utf8_oversize_paste_rejected_draft_unchanged"

            // Exercise the edit callback too, independently of the Paste action.
            ui.edit("x".repeat(FLASH_TEXT_MAX_BYTES + 1))
            check(FlashAndroidRuntime.state.value.textDraft == SENT_TEXT) { "Oversize editor change changed the draft" }
            ui.capture(host, directory, "oversize-draft-preserved")
            checks += "oversize_editor_change_rejected_draft_unchanged"
            ui.edit(SENT_TEXT)

            // Both cards use the production copy action. Rendering fixture results does not claim delivery.
            for ((kind, body) in listOf(FlashResultKind.DELIVERED to SENT_TEXT, FlashResultKind.RECEIVED to RECEIVED_TEXT)) {
                instrumentation.runOnMainSync {
                    FlashAndroidRuntime.update { it.copy(results = listOf(FlashAndroidResult(
                        id = "qa-${kind.name}", fileName = "synthetic-text.txt", kind = kind,
                        detailText = FlashText(literal = "Resultado sintético para comprobar la interfaz."), textBody = body))) }
                    clipboard.setPrimaryClip(ClipData.newPlainText("QA sentinel", "must change"))
                }
                ui.scrollTo(appString(R.string.flash_copy))
                ui.click(appString(R.string.flash_copy))
                var copied: String? = null
                var sensitive = false
                ui.await("${kind.name} exact copy") {
                    instrumentation.runOnMainSync {
                        val clip = clipboard.primaryClip
                        copied = clip?.getItemAt(0)?.text?.toString()
                        sensitive = clip?.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
                    }
                    copied == body
                }
                check(sensitive) { "Text copy did not request a private clipboard preview" }
                ui.capture(host, directory, "${kind.name.lowercase()}-copy")
                checks += "${kind.name.lowercase()}_production_copy_exact_and_sensitive"
            }

            val attachment = File(directory, "archivo-qa.txt").apply { writeText("Adjunto sintético de interfaz.") }
            assertRecipientTransitions(host, peer, attachment, directory, checks)
            assertFileSendReadiness(host, peer, attachment, directory, checks)
            for (scale in listOf(1f, 1.6f)) {
                instrumentation.runOnMainSync {
                    FlashAndroidRuntime.update { it.copy(selectedFiles = listOf(attachment), textDraft = SENT_TEXT,
                        results = emptyList(), statusText = flashText(R.string.flash_active)) }
                }
                render(host, scale)
                assertComposerVisible(host)
                clickContaining(appString(R.string.flash_text_to_device, peer.label))
                ui.visible(appString(R.string.flash_receiver))
                ui.click(appString(R.string.flash_close))
                assertPreparedContent(attachment)
                clickContaining(appQuantityString(R.plurals.flash_files_attached, 1, 1))
                ui.visible(appString(R.string.flash_attached_files_title, 1))
                ui.visible(attachment.name)
                ui.click(appString(R.string.flash_close))
                ui.await("attached files dialog dismissed before composer capture") {
                    ui.matching(appString(R.string.flash_attached_files_title, 1)) == null &&
                        ui.matching(appString(R.string.flash_send_text)) != null
                }
                assertPreparedContent(attachment)
                ui.capture(host, directory, "composer-attachments-${scale}x")
                checks += "composer_${scale}x_actions_visible_no_scroll_dialog_dismiss_preserves_content"

                ui.click(checkNotNull(ui.nodes().firstOrNull { it.isEditable && it.isVisibleToUser }))
                ui.await("real IME visible", 7_000) { imeVisible(host) }
                assertComposerVisible(host)
                ui.capture(host, directory, "composer-keyboard-${scale}x")
                checks += "composer_${scale}x_real_ime_editor_attach_and_both_send_actions_visible"
                instrumentation.runOnMainSync {
                    WindowCompat.getInsetsController(host.window, host.window.decorView)
                        .hide(WindowInsetsCompat.Type.ime())
                }
                ui.await("IME hidden") { !imeVisible(host) }
            }
            assertDraftSynchronization(host, peer, directory, checks)
            result.putString("result", "PASS")
        } catch (failure: Throwable) {
            result.putString("result", "FAIL")
            result.putString("failure", failure.stackTraceToString())
            if (activity != null && directory != null) runCatching { ui.capture(activity, directory, "failure-app-only") }
        } finally {
            directory?.let { File(it, "manifest.json").writeText(JSONObject()
                .put("source", "Production FlashActivity with process-only synthetic state")
                .put("network", "none; transfer success is tested separately")
                .put("scenario", result.getString("scenario"))
                .put("effective_language", result.getString("effective_language"))
                .put("font_scales", if (fileSendOnly) listOf(1f) else listOf(1f, 1.6f))
                .put("persistent_preferences_changed", false)
                .put("result", result.getString("result")).put("failure", result.getString("failure")).put("checks", checks)
                .put("assertions", ui.assertions).put("captures", ui.captures).toString(2)) }
            instrumentation.runOnMainSync {
                if (clipboardSaved) {
                    if (originalClipboard != null) clipboard.setPrimaryClip(originalClipboard!!)
                    else if (android.os.Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                    else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
                activity?.finish()
                FlashAndroidRuntime.update { original }
            }
            instrumentation.waitForIdleSync()
            result.putBoolean("process_state_restored", FlashAndroidRuntime.state.value == original)
            result.putString("checks", checks.joinToString(","))
            result.putInt("checks_passed", checks.size)
            instrumentation.finish(if (result.getString("result") == "PASS") Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        }
    }

    /** Focused layout/behavior check: two IME scales and one complete long-text view/copy. */
    private fun assertFocusedComposer(
        host: FlashActivity, peer: FlashPeer, attachment: File, directory: File,
        checks: MutableList<String>, clipboard: ClipboardManager
    ) {
        for (scale in listOf(1f, 1.6f)) {
            instrumentation.runOnMainSync {
                FlashAndroidRuntime.update { it.copy(selectedPeer = peer, selectedFiles = listOf(attachment),
                    textDraft = LONG_TEXT, results = emptyList(), statusText = flashText(R.string.flash_active)) }
            }
            render(host, scale)
            assertComposerVisible(host)
            ui.capture(host, directory, "composer-mixed-${scale}x")
            ui.click(checkNotNull(ui.nodes().firstOrNull { it.isEditable && it.isVisibleToUser }))
            ui.await("real IME visible", 7_000) { imeVisible(host) }
            assertComposerVisible(host)
            check(FlashAndroidRuntime.state.value.textDraft == LONG_TEXT &&
                FlashAndroidRuntime.state.value.selectedFiles == listOf(attachment))
            ui.capture(host, directory, "composer-keyboard-${scale}x")
            checks += "mixed_composer_${scale}x_real_ime_editor_attach_paste_and_separate_sends_visible"
            instrumentation.runOnMainSync {
                WindowCompat.getInsetsController(host.window, host.window.decorView).hide(WindowInsetsCompat.Type.ime())
            }
            ui.await("IME hidden") { !imeVisible(host) }
        }
        instrumentation.runOnMainSync {
            FlashAndroidRuntime.update { it.copy(textDraft = "", selectedFiles = emptyList(), results = listOf(
                FlashAndroidResult("qa-long-text", "synthetic-text.txt", FlashResultKind.RECEIVED,
                    flashText(R.string.flash_text_received_verified), textBody = LONG_TEXT))) }
            clipboard.setPrimaryClip(ClipData.newPlainText("QA sentinel", "must change"))
        }
        render(host, 1f)
        val view = appString(R.string.flash_view_text)
        ui.scrollTo(view)
        val preview = ui.visible(LONG_TEXT)
        check(ui.bounds(preview).height() <= (80 * host.resources.displayMetrics.density).toInt()) {
            "History text did not stay within its three-line preview"
        }
        ui.capture(host, directory, "history-long-text-preview")
        ui.click(view)
        ui.visible(appString(R.string.flash_text_message))
        ui.visible(LONG_TEXT)
        var endpoint = false
        repeat(16) {
            if (!endpoint) {
                var scroll: AccessibilityNodeInfo? = ui.visible(LONG_TEXT)
                while (scroll != null && !scroll.isScrollable) scroll = scroll.parent
                checkNotNull(scroll) { "Full text view has no scrollable content" }
                endpoint = !scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(120)
                instrumentation.waitForIdleSync()
            }
        }
        check(endpoint) { "Full text did not reach its scroll endpoint" }
        ui.click(appString(R.string.flash_copy))
        ui.await("full text copy preserves exact Unicode, newlines and trailing spaces") {
            var exact = false
            instrumentation.runOnMainSync {
                val clip = clipboard.primaryClip
                exact = clip?.getItemAt(0)?.text?.toString() == LONG_TEXT &&
                    clip?.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
            }
            exact
        }
        ui.capture(host, directory, "history-full-text-end-copy")
        ui.click(appString(R.string.flash_close))
        checks += "long_text_three_line_preview_full_view_scroll_and_exact_private_copy"

        // Copy acknowledgement must not hide a later asynchronous transfer error.
        instrumentation.runOnMainSync {
            FlashAndroidRuntime.update { it.copy(statusText = flashText(R.string.flash_text_error_rejected), textDraft = SENT_TEXT) }
        }
        val failure = appString(R.string.flash_text_error_rejected)
        // Android's clipboard preview can temporarily own the accessibility root after the
        // dialog closes. Wait for our visible header; never operate that SystemUI overlay.
        ui.await("new transfer error visible after clipboard overlay", timeoutMs = 10_000) {
            ui.matching(failure) != null && ui.matching(appString(R.string.flash_text_copied)) == null
        }
        check(FlashAndroidRuntime.state.value.textDraft == SENT_TEXT)
        ui.capture(host, directory, "error-after-copy-draft-kept")
        checks += "transfer_error_after_copy_remains_visible_and_preserves_draft"

        instrumentation.runOnMainSync { FlashAndroidRuntime.update { it.copy(selectedFiles = listOf(attachment)) } }
        ui.scrollTo(appString(R.string.flash_copy))
        ui.click(appString(R.string.flash_copy))
        ui.await("own-app accessibility restored after clipboard copy", timeoutMs = 10_000) {
            ui.nodes().any { it.isEditable && it.isVisibleToUser }
        }
        ui.scrollTo(appString(R.string.flash_text_copied))
        // This process-only fixture has no service: clicking the production send callback leaves
        // the same error value in place, exercising notice clearing even without a new status echo.
        ui.click(appQuantityString(R.plurals.flash_send_files, 1, 1))
        ui.await("retry cannot leave copy notice covering an identical error") {
            ui.matching(failure) != null && ui.matching(appString(R.string.flash_text_copied)) == null
        }
        check(FlashAndroidRuntime.state.value.textDraft == SENT_TEXT &&
            FlashAndroidRuntime.state.value.selectedFiles == listOf(attachment))
        checks += "file_retry_clears_copy_notice_without_requiring_a_changed_status"
    }

    private fun assertDraftSynchronization(
        host: FlashActivity, peer: FlashPeer, directory: File, checks: MutableList<String>
    ) {
        val saved = FlashAndroidRuntime.state.value
        val observedDraft = mutableStateOf("")
        val renderedEcho = AtomicReference<String?>(null)
        fun editorText(): String? = ui.nodes().firstOrNull { it.isEditable && it.isVisibleToUser }?.let {
            it.text?.toString().orEmpty()
        }
        fun assertDraft(expected: String, echo: String) {
            var stable = 0
            ui.await("current draft preserved after collected echo '$echo'") {
                val matches = renderedEcho.get() == echo && editorText() == expected &&
                    FlashAndroidRuntime.state.value.textDraft == expected
                stable = if (matches) stable + 1 else 0
                stable >= 3
            }
        }
        try {
            instrumentation.runOnMainSync {
                FlashAndroidRuntime.update { it.copy(textDraft = "") }
                host.setContent {
                    QetaraTheme {
                        val echo = observedDraft.value
                        FlashTextComposer(
                            draft = echo,
                            readCurrentDraft = { FlashAndroidRuntime.state.value.textDraft },
                            selectedPeer = peer, busy = false, sendingText = false,
                            importing = false, selectedFileCount = 0, compact = false,
                            onDraftChange = FlashForegroundService::updateTextDraft,
                            onPaste = { null }, onChoosePeer = {}, onChooseFiles = {},
                            onReviewFiles = {}, onCancelImport = {}, onSend = {}, onSendFiles = {}
                        )
                        SideEffect { renderedEcho.set(echo) }
                    }
                }
            }
            assertDraft("", "")
            ui.edit("Ma")
            ui.edit("Mat")
            // The real edit callback has published Mat. Hold back the collected echo deliberately.
            instrumentation.runOnMainSync { observedDraft.value = "Ma" }
            assertDraft("Mat", "Ma")
            checks += "delayed_collected_draft_echo_does_not_erase_newer_production_edit"

            instrumentation.runOnMainSync {
                FlashAndroidRuntime.update { it.copy(textDraft = "") }
                observedDraft.value = ""
            }
            assertDraft("", "")
            ui.click(checkNotNull(ui.nodes().firstOrNull { it.isEditable && it.isVisibleToUser }))
            val burst = "Material listo para revisar en equipo."
            instrumentation.sendStringSync(burst)
            instrumentation.runOnMainSync { observedDraft.value = "Material" }
            assertDraft(burst, "Material")
            check(FlashAndroidRuntime.state.value.textDraft.toByteArray(Charsets.UTF_8).size == 38)
            ui.capture(host, directory, "draft-key-burst-delayed-echo")
            checks += "key_event_burst_preserves_all_38_utf8_bytes_despite_delayed_echo"

            instrumentation.runOnMainSync {
                FlashAndroidRuntime.update { it.completeTextDraft(burst) }
                observedDraft.value = ""
            }
            assertDraft("", "")
            checks += "current_delivery_ack_clears_the_unedited_production_editor"
        } finally {
            instrumentation.runOnMainSync {
                WindowCompat.getInsetsController(host.window, host.window.decorView)
                    .hide(WindowInsetsCompat.Type.ime())
                FlashAndroidRuntime.update { saved }
            }
            render(host, 1f)
        }
    }

    private fun assertPreparedContent(attachment: File) {
        val state = FlashAndroidRuntime.state.value
        check(state.textDraft == SENT_TEXT && state.selectedFiles == listOf(attachment) &&
            attachment.readText() == "Adjunto sintético de interfaz.") {
            "Receiver or dialog transition changed prepared text or attachments"
        }
    }

    private fun assertFileSendReadiness(
        host: FlashActivity, peer: FlashPeer, attachment: File, directory: File, checks: MutableList<String>
    ) {
        val saved = FlashAndroidRuntime.state.value
        val files = listOf(attachment) + (2..5).map { number ->
            File(directory, "adjunto-$number.txt").apply { writeText("Adjunto sintético $number.") }
        }
        val label = appQuantityString(R.plurals.flash_send_files, files.size, files.size)
        fun fixture(draft: String = "", receiver: FlashPeer? = peer, attached: List<File> = files,
                    sending: Boolean = false, importing: Boolean = false) {
            instrumentation.runOnMainSync {
                FlashAndroidRuntime.update { saved.copy(selectedPeer = receiver, selectedFiles = attached,
                    textDraft = draft, sendingText = sending, importing = importing, results = emptyList()) }
            }
            render(host, 1f)
        }
        fun assertState(name: String, enabled: Boolean, textEnabled: Boolean?, sending: Boolean = false) {
            val textLabel = appString(if (sending) R.string.flash_sending_text else R.string.flash_send_text)
            var previous = ""
            var stable = 0
            ui.await("file/text send readiness: $name") {
                val filesAction = actionFor(label)
                val textAction = actionFor(textLabel)
                val textMatches = if (textEnabled == null) textAction == null else textAction?.isEnabled == textEnabled
                val current = if (filesAction != null && filesAction.isEnabled == enabled &&
                    textMatches)
                    ui.bounds(filesAction).flattenToString() + ":" + textAction?.let { ui.bounds(it).flattenToString() } else ""
                stable = if (current.isNotEmpty() && current == previous) stable + 1 else 0
                previous = current
                stable >= 2
            }
            val frame = ui.visibleAppFrame(host)
            assertActionVisible(label, frame, host, enabled)
            if (textEnabled != null) assertActionVisible(textLabel, frame, host, textEnabled)
            else check(ui.matching(textLabel) == null) { "Unexpected empty-draft send action" }
            val bounds = ui.bounds(checkNotNull(actionFor(label)))
            ui.capture(host, directory, name)
            val capture = checkNotNull(BitmapFactory.decodeFile(File(directory, "$name.png").absolutePath))
            try {
                // Sample the solid interior beside the centered label, away from rounded corners.
                val actual = capture.getPixel(bounds.left - frame.left + bounds.width() / 10,
                    bounds.centerY() - frame.top)
                check((actual == primaryActionColor) == enabled) {
                    "File send '$name' must use the theme primary fill only when enabled: " +
                        "actual=${Integer.toHexString(actual)}, primary=${Integer.toHexString(primaryActionColor)}"
                }
                ui.assertions.put(JSONObject().put("check", "file_send_enabled_theme_fill")
                    .put("state", name).put("enabled", enabled).put("text_enabled", textEnabled ?: JSONObject.NULL)
                    .put("file_count", files.size).put("actual_argb", Integer.toHexString(actual))
                    .put("primary_argb", Integer.toHexString(primaryActionColor)))
            } finally { capture.recycle() }
            checks += name
        }
        try {
            fixture(attached = emptyList())
            ui.await("no file send action without attachments") {
                ui.matching(label) == null && ui.matching(appQuantityString(R.plurals.flash_send_files, 0, 0)) == null &&
                    ui.matching(appString(R.string.flash_send_text)) == null
            }
            checks += "empty_composer_has_no_send_actions"

            fixture(draft = SENT_TEXT, attached = emptyList())
            ui.await("text-only primary send") { actionFor(appString(R.string.flash_send_text))?.isEnabled == true }
            assertActionVisible(appString(R.string.flash_send_text), ui.visibleAppFrame(host), host, true)
            check(ui.matching(label) == null)
            ui.capture(host, directory, "text-only-ready")
            checks += "text_only_has_only_message_send"

            fixture()
            assertState("file-send-five-ready-empty-draft", enabled = true, textEnabled = null)
            fixture(draft = SENT_TEXT)
            assertState("file-send-five-ready-with-draft", enabled = true, textEnabled = true)
            clickContaining(appQuantityString(R.plurals.flash_files_attached, files.size, files.size))
            ui.visible(appString(R.string.flash_attached_files_title, files.size))
            val removed = files.first()
            ui.click(appString(R.string.flash_remove_file, removed.name))
            ui.await("only chosen attachment removed") {
                FlashAndroidRuntime.state.value.selectedFiles == files.drop(1) &&
                    ui.matching(appString(R.string.flash_attached_files_title, files.size - 1)) != null
            }
            check(FlashAndroidRuntime.state.value.textDraft == SENT_TEXT && FlashAndroidRuntime.state.value.selectedPeer == peer)
            check(files.all { it.isFile }) { "A process-only review fixture deleted a file" }
            ui.capture(host, directory, "review-removed-one")
            ui.click(appString(R.string.flash_close))
            assertActionVisible(appQuantityString(R.plurals.flash_send_files, files.size - 1, files.size - 1),
                ui.visibleAppFrame(host), host, true)
            checks += "review_remove_one_preserves_other_files_draft_and_receiver"
            fixture(receiver = null)
            assertState("file-send-disabled-without-receiver", enabled = false, textEnabled = null)
            fixture(draft = SENT_TEXT, sending = true)
            assertState("file-send-disabled-while-busy", enabled = false, textEnabled = false, sending = true)
            instrumentation.runOnMainSync { FlashForegroundService.removeFile(files.first()) }
            check(FlashAndroidRuntime.state.value.selectedFiles == files)
            checks += "busy_guard_rejects_individual_removal"
            fixture(importing = true)
            ui.await("file send hidden until import finishes") {
                ui.matching(label) == null && ui.matching(appString(R.string.flash_preparing_files)) != null &&
                    ui.matching(appString(R.string.flash_send_text)) == null
            }
            instrumentation.runOnMainSync { FlashForegroundService.removeFile(files.first()) }
            check(FlashAndroidRuntime.state.value.selectedFiles == files)
            checks += "file_send_hidden_while_importing"
        } finally {
            instrumentation.runOnMainSync { FlashAndroidRuntime.update { saved } }
            render(host, 1f)
        }
    }

    private fun assertRecipientTransitions(
        host: FlashActivity, first: FlashPeer, attachment: File, directory: File, checks: MutableList<String>
    ) {
        val other = first.copy(id = "qa-other-peer", label = "Laptop 2", address = "192.0.2.21")
        fun publishPeers(vararg peers: FlashPeer) = instrumentation.runOnMainSync {
            FlashAndroidRuntime.update { it.copy(engine = checkNotNull(it.engine).copy(peers = peers.toList())) }
        }
        fun currentReceiver() = FlashAndroidRuntime.state.value.let {
            resolveSelectedFlashPeer(it.selectedPeer, it.engine?.peers.orEmpty(), System.currentTimeMillis())
        }
        fun openSelector() {
            val label = currentReceiver()?.let { appString(R.string.flash_text_to_device, it.label) }
                ?: appString(R.string.flash_choose_receiver)
            clickContaining(label)
            ui.visible(appString(R.string.flash_receiver))
        }
        fun choose(receiver: FlashPeer) {
            openSelector()
            if (currentReceiver() == receiver) {
                // Compose deliberately omits accessibility CLICK for an already selected radio.
                // Exercise the dialog's explicit accessible action for keeping that same receiver.
                ui.visible(receiver.label)
                ui.visible(appString(R.string.flash_selected_address, receiver.address))
                ui.click(appString(R.string.flash_keep_device))
            } else {
                ui.click(receiver.label)
            }
            ui.await("selected receiver ${receiver.id}:${receiver.port}") {
                FlashAndroidRuntime.state.value.selectedPeer == receiver &&
                    ui.matching(appString(R.string.flash_receiver)) == null
            }
            assertPreparedContent(attachment)
            check(FlashAndroidRuntime.state.value.engine?.operations.isNullOrEmpty()) {
                "Choosing a receiver submitted prepared content"
            }
        }

        instrumentation.runOnMainSync {
            FlashAndroidRuntime.update { it.copy(selectedPeer = first, selectedFiles = listOf(attachment),
                textDraft = SENT_TEXT, results = emptyList(), statusText = flashText(R.string.flash_active)) }
        }
        publishPeers(first, other)
        render(host, 1f)

        openSelector()
        ui.click(appString(R.string.flash_close))
        check(currentReceiver() == first)
        assertPreparedContent(attachment)
        checks += "receiver_selector_cancel_preserves_selected_receiver_exact_draft_and_attachment"

        choose(first)
        checks += "receiver_keep_same_accessible_action_preserves_exact_draft_and_attachment_without_sending"
        choose(other)
        ui.capture(host, directory, "receiver-changed-content-preserved")
        checks += "receiver_change_to_synthetic_other_preserves_exact_draft_and_attachment_without_sending"

        publishPeers(first)
        check(currentReceiver() == null) { "Missing receiver silently fell back to another device" }
        openSelector()
        ui.visible(appString(R.string.flash_selected_device_unavailable))
        ui.click(appString(R.string.flash_close))
        check(FlashAndroidRuntime.state.value.selectedPeer == other)
        assertPreparedContent(attachment)
        checks += "receiver_disappearance_keeps_preparation_without_fallback"

        val refreshed = other.copy(label = "Laptop 2", expiresAtMs = other.expiresAtMs + 60_000)
        publishPeers(first, refreshed)
        check(currentReceiver() == refreshed) { "Same receiver refresh did not update its visible metadata" }
        openSelector()
        ui.click(appString(R.string.flash_close))
        assertPreparedContent(attachment)
        checks += "receiver_same_endpoint_refresh_preserves_selection_and_preparation"

        val moved = refreshed.copy(address = "192.0.2.22", port = 8990)
        publishPeers(first, moved)
        check(currentReceiver() == null) { "Receiver endpoint changed without explicit selection" }
        assertPreparedContent(attachment)
        choose(moved)
        checks += "receiver_changed_endpoint_requires_explicit_selection_and_preserves_preparation"

        // Restore the original receiver for the existing font-scale and real-IME checks.
        choose(first)
        publishPeers(first)
    }

    private fun clickContaining(text: String) {
        ui.await("visible action containing '$text'") {
            ui.nodes().any { it.isVisibleToUser && it.text?.contains(text) == true }
        }
        ui.click(checkNotNull(ui.nodes().firstOrNull { it.isVisibleToUser && it.text?.contains(text) == true }))
    }

    private fun assertActionVisible(text: String, frame: Rect, activity: Activity, enabled: Boolean? = null) {
        ui.assertInside(text, frame)
        val action = checkNotNull(actionFor(text)) { "Missing action for '$text'" }
        val bounds = ui.bounds(action)
        val minTouch = (48 * activity.resources.displayMetrics.density).toInt()
        check(frame.contains(bounds) && bounds.height() >= minTouch && bounds.width() >= minTouch) {
            "Clipped or undersized action '$text': $bounds in $frame"
        }
        if (enabled != null) check(action.isEnabled == enabled) { "Unexpected enabled state for '$text'" }
    }

    private fun actionFor(text: String): AccessibilityNodeInfo? {
        var node = ui.matching(text)
        while (node != null && node.refresh()) {
            // Disabled Compose buttons retain their Button role but need not expose CLICK.
            if (node.isClickable || node.className?.toString() == "android.widget.Button") return node
            node = node.parent
        }
        return null
    }

    private fun assertComposerVisible(activity: Activity) {
        // A remount and an IME transition both update Compose layout and accessibility separately.
        // Require repeated identical bounds before checking glyphs; a frame being idle is not enough.
        var previous = ""
        var stableSamples = 0
        val actions = listOf(appString(R.string.flash_attach_files), appString(R.string.flash_paste),
            appString(R.string.flash_send_text), appQuantityString(R.plurals.flash_send_files, 1, 1))
        ui.await("composer layout stable", timeoutMs = 8_000) {
            // Read one tree per observation: four independent Binder walks consumed most of
            // the old timeout and could mix geometry from opposite sides of an IME transition.
            val nodes = ui.nodes()
            val bounds = actions.map { text ->
                nodes.firstOrNull { (it.text?.toString() == text || it.contentDescription?.toString() == text) &&
                    it.isVisibleToUser && !ui.bounds(it).isEmpty }?.let { ui.bounds(it).flattenToString() }
            }
            val current = ui.visibleAppFrame(activity).flattenToString() + bounds.joinToString()
            stableSamples = if (bounds.none { it == null } && current == previous) stableSamples + 1 else 0
            previous = current
            stableSamples >= 3
        }
        val frame = ui.visibleAppFrame(activity)
        for (action in actions) {
            assertActionVisible(action, frame, activity, enabled = true)
        }
        for ((text, glyphs) in actionCharacterBounds) {
            val deadline = SystemClock.uptimeMillis() + 2_000
            var lastFailure: Throwable? = null
            var validSamples = 0
            do {
                val checked = runCatching { ui.assertFullyVisibleText(text, frame, glyphs) }
                if (checked.isSuccess) validSamples++ else {
                    validSamples = 0
                    lastFailure = checked.exceptionOrNull()
                }
                if (validSamples >= 2) break
                SystemClock.sleep(100)
                instrumentation.waitForIdleSync()
                // A successful accessibility read may itself consume the retry window on a
                // loaded emulator. Always allow its confirming sample; a failed confirmation
                // still fails after the deadline, with the same per-glyph visibility checks.
            } while (validSamples == 1 || SystemClock.uptimeMillis() < deadline)
            recordCharacterGeometry(text, glyphs)
            if (validSamples < 2) throw checkNotNull(lastFailure) { "Text layout did not settle: '$text'" }
        }
        val editor = checkNotNull(ui.nodes().firstOrNull { it.isEditable && it.isVisibleToUser })
        check(frame.contains(ui.bounds(editor))) { "Editor is clipped or covered by the keyboard" }
    }

    @Suppress("DEPRECATION")
    private fun recordCharacterGeometry(text: String, reference: List<RectF>) {
        val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        val node = ui.nodes().firstOrNull {
            it.text?.toString() == text && it.isVisibleToUser && key in it.availableExtraData
        } ?: return
        val refreshed = node.refreshWithExtraData(key, Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, text.length)
        })
        val actual = node.extras.getParcelableArray(key)
        ui.assertions.put(JSONObject().put("check", "raw_character_geometry").put("text", text)
            .put("refreshed", refreshed).put("node_bounds", ui.bounds(node).flattenToString())
            .put("characters", JSONArray().apply {
                text.indices.forEach { index -> put(JSONObject().put("index", index)
                    .put("character", text[index].toString())
                    .put("reference", reference[index].toString())
                    .put("reported", actual?.getOrNull(index)?.toString() ?: JSONObject.NULL)) }
            }))
    }

    private fun imeVisible(activity: Activity): Boolean {
        var visible = false
        instrumentation.runOnMainSync {
            visible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }

    private fun render(activity: FlashActivity, scale: Float) {
        instrumentation.runOnMainSync {
            actionCharacterBounds = emptyMap()
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    QetaraTheme {
                        val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
                        val measurer = rememberTextMeasurer()
                        val glyphs = listOf(appString(R.string.flash_send_text), appQuantityString(R.plurals.flash_send_files, 1, 1)).associateWith { label ->
                            val layout = measurer.measure(AnnotatedString(label),
                                style = MaterialTheme.typography.labelLarge, softWrap = false)
                            List(label.length) { index ->
                                layout.getBoundingBox(index).let { RectF(it.left, it.top, it.right, it.bottom) }
                            }
                        }
                        SideEffect { actionCharacterBounds = glyphs; primaryActionColor = primaryColor }
                        ProvidePrivateClipboard {
                            key(scale) { FlashScreen(activity, onBack = activity::finish) }
                        }
                    }
                }
            }
        }
        ui.await("production Flash screen at ${scale}x") { actionCharacterBounds.size == 2 }
    }

    private val spanish get() = instrumentation.targetContext.resources.configuration.locales[0].language == "es"
    private val SENT_TEXT get() = if (spanish) "  Hola, Perú 👋\nSegunda línea: ñ, 漢字.\n"
        else "  Hello, Perú 👋\nSecond line: ñ, 漢字.\n"
    private val RECEIVED_TEXT get() = if (spanish) "Recibido: 日本語 🚀\r\nLínea dos\n  final  "
        else "Received: 日本語 🚀\r\nSecond line\n  end  "
    private val LONG_TEXT get() = (1..24).joinToString("\n") {
        if (spanish) "$it. Notas de la reunión — Perú, 日本語, 👋. Espacios conservados.  "
        else "$it. Meeting notes — Perú, 日本語, 👋. Spaces preserved.  "
    } + if (spanish) "\nFIN — exacto.  " else "\nEND — exact.  "
}
