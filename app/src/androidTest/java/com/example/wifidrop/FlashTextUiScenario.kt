package com.example.wifidrop

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
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
import org.json.JSONObject
import org.json.JSONArray

/** Real FlashActivity/composer/clipboard actions, with no service or network and restored state. */
internal class FlashTextUiScenario(private val instrumentation: Instrumentation) {
    private val ui = MobileUiScenarioSupport(instrumentation)
    private var actionCharacterBounds = emptyMap<String, List<RectF>>()

    fun run() {
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
            directory = File(context.getExternalFilesDir(null) ?: context.cacheDir,
                "qa/flash-text-ui-${System.currentTimeMillis()}").apply { check(mkdirs()) }
            result.putString("output_directory", directory.absolutePath)
            result.putString("effective_language", context.resources.configuration.locales.toLanguageTags())
            val now = System.currentTimeMillis()
            val peer = FlashPeer("qa-peer", "Equipo QA", "192.0.2.20", 8989, now + 600_000)
            instrumentation.runOnMainSync {
                FlashAndroidRuntime.update { FlashAndroidState(phase = FlashAndroidPhase.ACTIVE,
                    engine = FlashState(active = true, expiresAtMs = now + 600_000, localId = "qa-local", peers = listOf(peer)),
                    deviceLabel = "Android QA", statusText = flashText(R.string.flash_active)) }
            }
            val host = instrumentation.startActivitySync(Intent(context, FlashActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as FlashActivity
            activity = host
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
            result.putString("result", "PASS")
        } catch (failure: Throwable) {
            result.putString("result", "FAIL")
            result.putString("failure", failure.stackTraceToString())
            if (activity != null && directory != null) runCatching { ui.capture(activity, directory, "failure-app-only") }
        } finally {
            directory?.let { File(it, "manifest.json").writeText(JSONObject()
                .put("source", "Production FlashActivity with process-only synthetic state")
                .put("network", "none; transfer success is tested separately")
                .put("effective_language", result.getString("effective_language"))
                .put("font_scales", listOf(1f, 1.6f)).put("persistent_preferences_changed", false)
                .put("result", result.getString("result")).put("checks", checks)
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

    private fun assertPreparedContent(attachment: File) {
        val state = FlashAndroidRuntime.state.value
        check(state.textDraft == SENT_TEXT && state.selectedFiles == listOf(attachment) &&
            attachment.readText() == "Adjunto sintético de interfaz.") {
            "Receiver or dialog transition changed prepared text or attachments"
        }
    }

    private fun assertRecipientTransitions(
        host: FlashActivity, first: FlashPeer, attachment: File, directory: File, checks: MutableList<String>
    ) {
        val other = first.copy(id = "qa-other-peer", label = "Otro equipo QA", address = "192.0.2.21")
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

        val refreshed = other.copy(label = "Otro equipo QA actualizado", expiresAtMs = other.expiresAtMs + 60_000)
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
        var node: AccessibilityNodeInfo? = ui.assertInside(text, frame)
        while (node != null && !node.isClickable) node = node.parent
        val action = checkNotNull(node) { "Missing action for '$text'" }
        val bounds = ui.bounds(action)
        val minTouch = (48 * activity.resources.displayMetrics.density).toInt()
        check(frame.contains(bounds) && bounds.height() >= minTouch && bounds.width() >= minTouch) {
            "Clipped or undersized action '$text': $bounds in $frame"
        }
        if (enabled != null) check(action.isEnabled == enabled) { "Unexpected enabled state for '$text'" }
    }

    private fun assertComposerVisible(activity: Activity) {
        // A remount and an IME transition both update Compose layout and accessibility separately.
        // Require repeated identical bounds before checking glyphs; a frame being idle is not enough.
        var previous = ""
        var stableSamples = 0
        ui.await("composer layout stable") {
            val bounds = listOf(appString(R.string.flash_attach_files), appString(R.string.flash_paste), appString(R.string.flash_send_text), appQuantityString(R.plurals.flash_send_files, 1, 1)).map { text ->
                ui.matching(text)?.let { ui.bounds(it).flattenToString() }
            }
            val current = ui.visibleAppFrame(activity).flattenToString() + bounds.joinToString()
            stableSamples = if (bounds.none { it == null } && current == previous) stableSamples + 1 else 0
            previous = current
            stableSamples >= 3
        }
        val frame = ui.visibleAppFrame(activity)
        for (action in listOf(appString(R.string.flash_attach_files), appString(R.string.flash_paste), appString(R.string.flash_send_text), appQuantityString(R.plurals.flash_send_files, 1, 1))) {
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
            } while (SystemClock.uptimeMillis() < deadline)
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
                        val measurer = rememberTextMeasurer()
                        val glyphs = listOf(appString(R.string.flash_send_text), appQuantityString(R.plurals.flash_send_files, 1, 1)).associateWith { label ->
                            val layout = measurer.measure(AnnotatedString(label),
                                style = MaterialTheme.typography.labelLarge, softWrap = false)
                            List(label.length) { index ->
                                layout.getBoundingBox(index).let { RectF(it.left, it.top, it.right, it.bottom) }
                            }
                        }
                        SideEffect { actionCharacterBounds = glyphs }
                        ProvidePrivateClipboard {
                            key(scale) { FlashScreen(activity, onBack = activity::finish) }
                        }
                    }
                }
            }
        }
        ui.await("production Flash screen at ${scale}x") { actionCharacterBounds.size == 2 }
    }

    companion object {
        private const val SENT_TEXT = "  Hola, Perú 👋\nSegunda línea: ñ, 漢字.\n"
        private const val RECEIVED_TEXT = "Recibido: 日本語 🚀\r\nLínea dos\n  final  "
    }
}
