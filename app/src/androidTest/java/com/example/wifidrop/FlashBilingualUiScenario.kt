package com.example.wifidrop

import android.app.Activity
import android.app.Instrumentation
import android.app.LocaleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.example.wifidrop.protocol.flash.FlashApproval
import com.example.wifidrop.protocol.flash.FlashOfferedFile
import com.example.wifidrop.protocol.flash.FlashOperation
import com.example.wifidrop.protocol.flash.FlashPeer
import com.example.wifidrop.protocol.flash.FlashState
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Production UI fixtures only. App locale is supplied by the caller; no receiver or network starts. */
internal class FlashBilingualUiScenario(
    private val instrumentation: Instrumentation,
    private val expectedLanguage: String?
) {
    private val ui = MobileUiScenarioSupport(instrumentation)
    private var glyphs = emptyMap<String, List<RectF>>()
    private var originalActivityConfiguration: Configuration? = null
    private enum class TextStyle { CODE, ACTION, BODY, STATUS }

    fun run() {
        val result = Bundle()
        val checks = mutableListOf<String>()
        val context = instrumentation.targetContext
        val originalRuntime = FlashAndroidRuntime.state.value
        val originalPreferences = UxPreferencesStore.load(context)
        val originalLanguage = context.resources.configuration.locales.toLanguageTags()
        val localeManager = if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java) else null
        val originalAppLocales = localeManager?.applicationLocales
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        var originalClipboard: ClipData? = null
        var clipboardSaved = false
        var host: FlashActivity? = null
        var output: File? = null
        try {
            check(originalRuntime.phase == FlashAndroidPhase.OFF && originalRuntime.engine?.active != true &&
                !originalRuntime.importing) { "Close Flash before the isolated bilingual UI fixture" }
            check(expectedLanguage == null || expectedLanguage in listOf("en", "es"))
            val language = context.resources.configuration.locales[0].language
            check(language in listOf("en", "es") && (expectedLanguage == null || language == expectedLanguage)) {
                "Expected app language $expectedLanguage, found $originalLanguage; configure appLocale before running"
            }
            result.putString("effective_language", originalLanguage)
            output = File(context.getExternalFilesDir(null) ?: context.cacheDir,
                "qa/flash-bilingual-ui-$language-${System.currentTimeMillis()}").apply { check(mkdirs()) }
            result.putString("output_directory", output.absolutePath)
            instrumentation.runOnMainSync { FlashAndroidRuntime.update { baseState() } }
            val activity = instrumentation.startActivitySync(Intent(context, FlashActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as FlashActivity
            host = activity
            instrumentation.runOnMainSync {
                originalActivityConfiguration = Configuration(activity.resources.configuration)
                originalClipboard = clipboard.primaryClip
                clipboardSaved = true
            }

            for (scale in SCALES) {
                for (batch in listOf(false, true)) for (outgoing in listOf(false, true)) {
                    val name = "${if (batch) "batch5" else "text"}-${if (outgoing) "send" else "receive"}-${scale}x"
                    val approval = approval(name, batch, outgoing)
                    val action = if (batch) appQuantityString(
                        if (outgoing) R.plurals.flash_matches_send_files else R.plurals.flash_matches_receive_files, 5, 5)
                    else appString(if (outgoing) R.string.flash_matches_send else R.string.flash_matches_receive_text)
                    val reject = appString(R.string.flash_reject)
                    val fileLabels = approval.files.map {
                        appString(R.string.flash_file_with_size, it.fileName,
                            appQuantityString(R.plurals.flash_size_bytes, it.totalBytes.toInt(), it.totalBytes))
                    }
                    instrumentation.runOnMainSync {
                        FlashAndroidRuntime.update { baseState(approval) }
                    }
                    render(activity, scale, name, mapOf(CODE to TextStyle.CODE, action to TextStyle.ACTION,
                        reject to TextStyle.ACTION) + if (batch) fileLabels.associateWith { TextStyle.BODY } else emptyMap())
                    ui.visible(appString(if (outgoing) R.string.flash_verify_before_sending else R.string.flash_verify_before_receiving))
                    captureApprovalActions(activity, output, "$name-top", action, reject)
                    if (batch) {
                        scrollInnerToEnd(fileLabels.first(), fileLabels.last(), fileLabels)
                        assertTailVisible(fileLabels.last(), activity)
                        ui.capture(activity, output, "$name-files-end")
                        checks += "${name}_five_exact_file_labels_last_filename_suffix_reachable"
                    } else {
                        ui.scrollTo(appString(R.string.flash_text_message))
                        check(ui.nodes().none { it.isVisibleToUser && it.text?.contains(TEXT_FILE) == true }) {
                            "Reserved transport filename leaked into the text approval"
                        }
                    }
                    ui.scrollUntilTextFullyVisible(CODE, ui.visibleAppFrame(activity), glyphs.getValue(CODE))
                    val verification = ui.visible(CODE)
                    check(verification.contentDescription?.toString() == appString(R.string.flash_verification_description,
                        CODE.toCharArray().joinToString(" "))) { "Verification accessibility description is incomplete" }
                    captureApprovalActions(activity, output, "$name-code-actions", action, reject)
                    checks += "${name}_complete_code_glyphs_and_accessible_enabled_nonoverlapping_actions"
                    // These buttons are inspected, never submitted. Transport approval is tested separately.
                }

                val incoming = FlashOperation("qa-integrity", null, TEXT_FILE, 64, outgoing = false)
                val failures = linkedMapOf(
                    "rejected" to flashTextErrorText("rejected"),
                    "approval-expired" to flashErrorText("approval_expired"),
                    "session-expired" to flashErrorText("expired"),
                    "incoming-integrity" to flashOperationErrorText("integrity_failed", incoming, false, false),
                    "invalid-address" to flashErrorText("invalid_address")
                )
                for ((name, copy) in failures) {
                    instrumentation.runOnMainSync {
                        FlashAndroidRuntime.update { baseState().copy(statusText = copy, textDraft = DRAFT) }
                    }
                    val label = copy.resolve()
                    render(activity, scale, "$name-$scale", mapOf(label to TextStyle.STATUS))
                    ui.scrollUntilTextFullyVisible(label, ui.visibleAppFrame(activity), glyphs.getValue(label))
                    check(FlashAndroidRuntime.state.value.textDraft == DRAFT)
                    ui.capture(activity, output, "error-$name-${scale}x")
                    checks += "${name}_${scale}x_localized_error_all_glyphs_visible_draft_unchanged"
                }

                instrumentation.runOnMainSync {
                    FlashAndroidRuntime.update { baseState().copy(results = listOf(FlashAndroidResult(
                        "qa-long-text", TEXT_FILE, FlashResultKind.RECEIVED,
                        flashText(R.string.flash_text_received_verified), textBody = LONG_TEXT))) }
                }
                val copyAction = appString(R.string.flash_copy)
                render(activity, scale, "long-$scale", mapOf(LONG_TEXT to TextStyle.BODY, copyAction to TextStyle.ACTION))
                ui.scrollTo(LONG_TEXT)
                ui.capture(activity, output, "long-text-${scale}x-start")
                scrollInnerToEnd(LONG_TEXT, LONG_TEXT)
                scrollUntilActionFullyVisible(activity, copyAction)
                ui.click(copyAction)
                ui.await("long text copied without truncation") {
                    var actual: String? = null
                    instrumentation.runOnMainSync { actual = clipboard.primaryClip?.getItemAt(0)?.text?.toString() }
                    actual == LONG_TEXT
                }
                ui.capture(activity, output, "long-text-${scale}x-end-copy")
                checks += "long_text_${scale}x_scroll_endpoint_and_exact_unicode_copy"
            }
            result.putString("result", "PASS")
        } catch (failure: Throwable) {
            result.putString("result", "FAIL")
            result.putString("failure", failure.stackTraceToString())
            if (host != null && output != null) runCatching { ui.capture(host, output, "failure-app-only") }
        } finally {
            runCatching {
                instrumentation.runOnMainSync {
                    if (clipboardSaved) {
                        originalClipboard?.let(clipboard::setPrimaryClip)
                            ?: if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                            else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                    originalActivityConfiguration?.let { original ->
                        host?.let { activity ->
                            disposeComposition(activity)
                            @Suppress("DEPRECATION")
                            activity.resources.updateConfiguration(original, activity.resources.displayMetrics)
                            result.putBoolean("activity_font_scale_restored",
                                activity.resources.configuration.fontScale == original.fontScale)
                        }
                    }
                    host?.finish()
                    FlashAndroidRuntime.update { originalRuntime }
                    if (UxPreferencesStore.load(context) != originalPreferences) {
                        UxPreferencesStore.save(context, originalPreferences)
                        UxPreferencesStore.setSessionEnabled(context, originalPreferences.sessionEnabled)
                    }
                    if (Build.VERSION.SDK_INT >= 33 && localeManager?.applicationLocales != originalAppLocales) {
                        localeManager?.applicationLocales = checkNotNull(originalAppLocales)
                    }
                }
                instrumentation.waitForIdleSync()
                check(FlashAndroidRuntime.state.value == originalRuntime)
                check(UxPreferencesStore.load(context) == originalPreferences)
                check(context.resources.configuration.locales.toLanguageTags() == originalLanguage)
                check(originalActivityConfiguration == null || result.getBoolean("activity_font_scale_restored")) {
                    "Activity font scale was not restored"
                }
                result.putBoolean("runtime_preferences_locale_restored", true)
                result.putBoolean("clipboard_restored", clipboardSaved)
            }.onFailure {
                result.putString("result", "FAIL")
                result.putString("restoration_failure", it.stackTraceToString())
            }
            output?.let { File(it, "manifest.json").writeText(JSONObject()
                .put("source", "Production FlashActivity/FlashScreen, synthetic process-only approvals and results")
                .put("network", "none; no service, sends, or approvals submitted")
                .put("effective_language", result.getString("effective_language"))
                .put("expected_language", expectedLanguage).put("font_scales", JSONArray(SCALES))
                .put("scope", "Glyph geometry for codes/actions/errors; filename scroll endpoint; exact long-text copy. Not transport verification.")
                .put("result", result.getString("result")).put("failure", result.getString("failure"))
                .put("runtime_preferences_locale_restored", result.getBoolean("runtime_preferences_locale_restored"))
                .put("fresh_composition_each_fixture", true)
                .put("activity_font_scale_restored", result.getBoolean("activity_font_scale_restored"))
                .put("clipboard_restored", result.getBoolean("clipboard_restored"))
                .put("checks", JSONArray(checks)).put("assertions", ui.assertions).put("captures", ui.captures).toString(2)) }
            result.putInt("checks_passed", checks.size)
            result.putString("checks", checks.joinToString(","))
            instrumentation.finish(if (result.getString("result") == "PASS") Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        }
    }

    private fun baseState(approval: FlashApproval? = null): FlashAndroidState {
        val expires = System.currentTimeMillis() + 600_000
        return FlashAndroidState(phase = FlashAndroidPhase.ACTIVE,
            engine = FlashState(active = true, expiresAtMs = expires, localId = "qa-bilingual-local",
                approvals = listOfNotNull(approval), peers = listOfNotNull(approval?.peer)),
            deviceLabel = "Android QA", statusText = flashText(R.string.flash_active))
    }

    private fun approval(id: String, batch: Boolean, outgoing: Boolean): FlashApproval {
        val now = System.currentTimeMillis()
        val files = if (batch) List(5) { index -> FlashOfferedFile(
            "${index + 1}-Informe-de-validación-Qetara-con-nombre-largo-y-acentos-revisión-final-2026.pdf", (index + 1L) * 64)
        } else listOf(FlashOfferedFile(TEXT_FILE, 64))
        return FlashApproval("qa-$id", "qa-operation-$id",
            FlashPeer("qa-peer", "Equipo QA — Perú", "192.0.2.20", 8989, now + 600_000),
            files.first().fileName, files.sumOf { it.totalBytes }, outgoing, CODE, now + 120_000, files)
    }

    private fun render(activity: FlashActivity, scale: Float, id: String, labels: Map<String, TextStyle>) {
        instrumentation.runOnMainSync {
            disposeComposition(activity)
            // Dialog creates a separate Android ComposeView. Its context must carry the same
            // process-only font scale as the parent and glyph measurements; restore it in finally.
            val configuration = Configuration(activity.resources.configuration).apply { fontScale = scale }
            @Suppress("DEPRECATION")
            activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
            check(activity.resources.configuration.fontScale == scale)
            glyphs = emptyMap()
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    QetaraTheme {
                        val measurer = rememberTextMeasurer()
                        val measured = labels.mapValues { (text, kind) ->
                            val style = when (kind) {
                                TextStyle.CODE -> MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 34.sp)
                                TextStyle.ACTION -> MaterialTheme.typography.labelLarge
                                TextStyle.BODY -> MaterialTheme.typography.bodyMedium
                                TextStyle.STATUS -> MaterialTheme.typography.bodySmall
                            }
                            val layout = measurer.measure(AnnotatedString(text), style = style, softWrap = false)
                            List(text.length) { index -> layout.getBoundingBox(index).let { RectF(it.left, it.top, it.right, it.bottom) } }
                        }
                        SideEffect { glyphs = measured }
                        ProvidePrivateClipboard { key(id, scale) { FlashScreen(activity, onBack = activity::finish) } }
                    }
                }
            }
        }
        ui.await("fixture $id at ${scale}x") { glyphs.keys == labels.keys }
        SystemClock.sleep(150)
        instrumentation.waitForIdleSync()
    }

    private fun disposeComposition(activity: FlashActivity) {
        activity.window.decorView.clearFocus()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        (content.getChildAt(0) as? AbstractComposeView)?.disposeComposition()
    }

    private fun assertAction(activity: Activity, label: String, settleTimeoutMs: Long = 5_000) {
        var previous = ""
        var stable = 0
        ui.await("stable action '$label'", settleTimeoutMs) {
            val textNode = ui.matching(label)
            val action = refreshedAction(label)
            val current = if (textNode != null && action != null &&
                ui.bounds(action).contains(ui.bounds(textNode))) {
                ui.bounds(textNode).flattenToString() + ":" + ui.bounds(action).flattenToString() +
                    ":" + action.isEnabled + ":" + ui.visibleAppFrame(activity).flattenToString()
            } else null
            stable = if (current != null && current == previous) stable + 1 else 0
            previous = current.orEmpty()
            stable >= 2
        }
        val frame = ui.visibleAppFrame(activity)
        ui.assertFullyVisibleText(label, frame, glyphs.getValue(label))
        val action = checkNotNull(refreshedAction(label)) { "No clickable action for '$label'" }
        val bounds = ui.bounds(action)
        val touch = (48 * activity.resources.displayMetrics.density).toInt()
        check(action.isEnabled && bounds.contains(ui.bounds(ui.visible(label))) && frame.contains(bounds) &&
            bounds.width() >= touch && bounds.height() >= touch) {
            "Action disabled, clipped, or below 48dp: '$label', $bounds"
        }
        ui.assertions.put(JSONObject().put("check", "complete_action_target").put("text", label)
            .put("bounds", bounds.flattenToString()).put("minimum_touch_px", touch))
    }

    /** Parent nodes returned from a fresh label may still hold pre-scroll geometry in Android's cache. */
    private fun refreshedAction(label: String): AccessibilityNodeInfo? {
        var node = ui.matching(label)
        while (node != null) {
            if (!node.refresh()) return null
            if (node.isClickable) return node
            node = node.parent
        }
        return null
    }

    /** Character geometry alone cannot detect another opaque button painting over those glyphs. */
    private fun captureApprovalActions(activity: Activity, output: File, name: String, confirm: String, reject: String) {
        fun geometry(): Pair<Rect, Rect>? {
            val positive = refreshedAction(confirm) ?: return null
            val negative = refreshedAction(reject) ?: return null
            return ui.bounds(positive) to ui.bounds(negative)
        }
        repeat(3) { attempt ->
            var previous: Pair<Rect, Rect>? = null
            var stable = 0
            ui.await("stable approval action geometry") {
                val current = geometry()
                stable = if (current != null && current == previous) stable + 1 else 0
                previous = current
                stable >= 3
            }
            assertAction(activity, confirm)
            assertAction(activity, reject)
            val before = checkNotNull(geometry())
            check(!Rect.intersects(before.first, before.second)) {
                "Approval actions overlap: '$confirm' ${before.first}; '$reject' ${before.second}"
            }
            ui.capture(activity, output, name)
            val after = geometry()
            ui.assertions.put(JSONObject().put("check", "approval_actions_nonoverlapping_and_capture_stable")
                .put("confirm", confirm).put("reject", reject).put("confirm_bounds", before.first.flattenToString())
                .put("reject_bounds", before.second.flattenToString()).put("capture_attempt", attempt + 1)
                .put("capture_geometry_unchanged", after == before))
            if (after == before) return
        }
        error("Approval action geometry changed while capturing '$name'")
    }

    /** A visible label can still leave part of its 48dp target behind the fixed composer. */
    private fun scrollUntilActionFullyVisible(activity: Activity, label: String) {
        ui.scrollTo(label)
        repeat(8) { attempt ->
            val checked = runCatching { assertAction(activity, label) }
            if (checked.isSuccess) return
            val target = checkNotNull(refreshedAction(label)) { "No clickable action for '$label'" }
            val revealRequested = attempt == 0 && target.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            var scrolled = revealRequested
            if (!revealRequested) {
                var ancestor = target.parent
                while (ancestor != null) {
                    if (!ancestor.refresh()) { ancestor = null; break }
                    if (ancestor.isScrollable) break
                    ancestor = ancestor.parent
                }
                scrolled = ancestor?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
            }
            ui.assertions.put(JSONObject().put("check", "reveal_complete_action_target").put("text", label)
                .put("attempt", attempt + 1).put("action", if (revealRequested) "show_on_screen" else "scroll_forward")
                .put("accepted", scrolled).put("previous_failure", checked.exceptionOrNull()?.message))
            SystemClock.sleep(350)
            instrumentation.waitForIdleSync()
            if (!scrolled) {
                // A scroll endpoint can be reported before accessibility has delivered the last
                // position change. Reobserve fresh label/parent geometry before calling it clipped.
                val deadline = SystemClock.uptimeMillis() + 2_000
                var lastFailure = checked.exceptionOrNull()
                do {
                    val settled = runCatching { assertAction(activity, label, settleTimeoutMs = 750) }
                    if (settled.isSuccess) return
                    lastFailure = settled.exceptionOrNull()
                    SystemClock.sleep(100)
                    instrumentation.waitForIdleSync()
                } while (SystemClock.uptimeMillis() < deadline)
                error("Scroll ended before the complete action was visible: ${lastFailure?.message}")
            }
        }
        assertAction(activity, label)
    }

    private fun scrollInnerToEnd(first: String, last: String, expectedLabels: List<String> = listOf(first, last)) {
        ui.scrollTo(first)
        var container: AccessibilityNodeInfo? = ui.visible(first)
        while (container != null && !container.isScrollable) container = container.parent
        val scroll = checkNotNull(container) { "Missing inner scroll for '$first'" }
        val seen = mutableSetOf<String>()
        repeat(80) {
            ui.nodes().filter { it.isVisibleToUser }.mapNotNullTo(seen) { it.text?.toString() }
            val moved = scroll.refresh() && scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(220)
            instrumentation.waitForIdleSync()
            if (!moved) {
                ui.visible(last)
                ui.nodes().filter { it.isVisibleToUser }.mapNotNullTo(seen) { it.text?.toString() }
                check(seen.containsAll(expectedLabels)) { "Some offered labels were not exposed while scrolling" }
                ui.assertions.put(JSONObject().put("check", "inner_scroll_endpoint").put("first", first)
                    .put("last", last).put("scroll_steps", it).put("labels_seen", JSONArray(expectedLabels)))
                return
            }
        }
        error("Inner scroll did not reach its endpoint")
    }

    @Suppress("DEPRECATION")
    private fun assertTailVisible(text: String, activity: Activity) {
        val node = ui.visible(text)
        val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        val start = (text.length - 12).coerceAtLeast(0)
        check(node.refreshWithExtraData(key, Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, start)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, text.length - start)
        }))
        val locations = checkNotNull(node.extras.getParcelableArray(key))
        val frame = RectF(ui.visibleAppFrame(activity)).apply { inset(-1f, -1f) }
        for (index in start until text.length) if (!text[index].isWhitespace()) {
            val rect = checkNotNull(locations[index - start] as? RectF)
            val expected = glyphs.getValue(text)[index]
            check(!rect.isEmpty && frame.contains(rect) && rect.width() + 1 >= expected.width() &&
                rect.height() + 1 >= expected.height()) { "Last filename suffix is clipped: '$text'" }
        }
        ui.assertions.put(JSONObject().put("check", "last_filename_suffix_visible").put("text", text).put("start_index", start))
    }

    companion object {
        private val SCALES = listOf(1f, 1.6f, 2f)
        private const val CODE = "ABCD 2345 EF67 89AB"
        private const val TEXT_FILE = "qetara-flash-text-v1-12345678-1234-1234-1234-123456789abc.txt"
        private const val DRAFT = "  Borrador QA — exacto, ñ, 日本語 👋\nSegunda línea.  "
        private val LONG_TEXT = (1..24).joinToString("\n") { "$it. Texto de prueba largo — Perú, 日本語, 👋. Espacios conservados.  " } + "\nFIN — exacto.  "
    }
}
