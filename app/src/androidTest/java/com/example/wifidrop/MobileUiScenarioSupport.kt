package com.example.wifidrop

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Own-app accessibility assertions; no permission dialogs or other apps are operated or captured. */
internal class MobileUiScenarioSupport(private val instrumentation: Instrumentation) {
    val assertions = JSONArray()
    val captures = JSONArray()

    fun nodes(): List<AccessibilityNodeInfo> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptyList()
        if (root.packageName?.toString() != instrumentation.targetContext.packageName) return emptyList()
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        val result = mutableListOf<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty() && result.size < 4096) {
            val node = pending.removeFirst()
            if (!node.refresh()) continue
            result += node
            repeat(node.childCount) { node.getChild(it)?.let(pending::addLast) }
        }
        return result
    }

    fun matching(text: String): AccessibilityNodeInfo? = nodes().firstOrNull {
        (it.text?.toString() == text || it.contentDescription?.toString() == text) &&
            it.isVisibleToUser && !bounds(it).isEmpty
    }

    fun await(name: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        do {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(60)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Timed out: $name. Visible nodes: " + nodes().filter { it.isVisibleToUser }
            .mapNotNull { it.text ?: it.contentDescription }.joinToString(" | ").take(2500))
    }

    fun visible(text: String): AccessibilityNodeInfo {
        await("visible '$text'") { matching(text) != null }
        return checkNotNull(matching(text))
    }

    fun bounds(node: AccessibilityNodeInfo): Rect = Rect().also(node::getBoundsInScreen)

    fun assertInside(text: String, viewport: Rect, minHeightPx: Int = 1): AccessibilityNodeInfo {
        val node = visible(text)
        val rect = bounds(node)
        check(viewport.contains(rect) && rect.height() >= minHeightPx) {
            "Clipped or undersized '$text': $rect in $viewport (minimum height $minHeightPx)"
        }
        assertions.put(JSONObject().put("text", text).put("bounds", rect.flattenToString())
            .put("viewport", viewport.flattenToString()).put("minimum_height_px", minHeightPx))
        return node
    }

    /** Check the actual visible glyphs; a node may expose full text even when its pixels are clipped. */
    @Suppress("DEPRECATION")
    fun assertFullyVisibleText(text: String, viewport: Rect, referenceCharacterBounds: List<RectF>) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            "Character visibility assertions require Android API 26 or later"
        }
        check(referenceCharacterBounds.size == text.length) { "Missing reference text metrics" }
        assertInside(text, viewport)
        val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        val node = checkNotNull(nodes().firstOrNull {
            it.text?.toString() == text && it.isVisibleToUser && key in it.availableExtraData
        }) { "Production text node does not expose character locations: '$text'" }
        check(node.refreshWithExtraData(key, Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, text.length)
        })) { "Could not refresh text character locations" }
        check(node.text?.toString() == text && viewport.contains(bounds(node))) {
            "Text or viewport changed while checking character visibility"
        }
        val locations = checkNotNull(node.extras.getParcelableArray(key)) { "Missing character locations" }
        check(locations.size == text.length) { "Incomplete character location response" }
        // Compare against TextMeasurer from the same composition/font, not a nominal line height.
        // Compose intersects these character rectangles with the visible text node. A missing or
        // shrunken letter therefore fails even if accessibility still reports the complete string.
        // One physical pixel accommodates float-to-integer screen-coordinate conversion only.
        val roundingPx = 1f
        val screen = RectF(viewport).apply { inset(-roundingPx, -roundingPx) }
        val characterEvidence = JSONArray()
        text.forEachIndexed { index, character ->
            // A wrapping space can be discarded by the text layout without hiding any glyph.
            if (!character.isWhitespace()) {
                val actual = checkNotNull(locations[index] as? RectF) { "Character $index ('$character') is off-screen" }
                val expected = referenceCharacterBounds[index]
                check(!actual.isEmpty && screen.contains(actual) &&
                    actual.width() + roundingPx >= expected.width() &&
                    actual.height() + roundingPx >= expected.height()) {
                    "Clipped character $index ('$character'): $actual; measured glyph " +
                        "${expected.width()} x ${expected.height()} px; viewport $viewport"
                }
                characterEvidence.put(JSONObject().put("index", index).put("bounds", actual.toString())
                    .put("reference_width_px", expected.width()).put("reference_height_px", expected.height()))
            }
        }
        assertions.put(JSONObject().put("text", text).put("check", "all_non_whitespace_glyphs_visible")
            .put("rounding_tolerance_px", roundingPx).put("characters", characterEvidence))
    }

    /** A partially exposed LazyColumn row is reachable only after every glyph passes the same check. */
    fun scrollUntilTextFullyVisible(text: String, viewport: Rect, referenceCharacterBounds: List<RectF>) {
        var lastFailure: Throwable? = null
        repeat(8) { attempt ->
            val visibility = runCatching { assertFullyVisibleText(text, viewport, referenceCharacterBounds) }
            if (visibility.isSuccess) return
            lastFailure = visibility.exceptionOrNull()
            val target = matching(text)
            val revealRequested = attempt == 0 && target?.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id
            ) == true
            var scrolled = revealRequested
            var scrollAction = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            if (!revealRequested) {
                var ancestor = target
                while (ancestor != null) {
                    if (!ancestor.refresh()) { ancestor = null; break }
                    if (ancestor.isScrollable) break
                    ancestor = ancestor.parent
                }
                val container = ancestor ?: nodes().firstOrNull { it.isScrollable && it.isVisibleToUser }
                // A reverse-layout chat exposes BACKWARD at its latest-message edge.
                val available = container?.actionList?.map { it.id }.orEmpty()
                scrollAction = if (AccessibilityNodeInfo.ACTION_SCROLL_FORWARD in available)
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                scrolled = scrollAction in available && container?.performAction(scrollAction) == true
            }
            assertions.put(JSONObject().put("text", text).put("check", "reveal_full_text")
                .put("attempt", attempt + 1).put("action", if (revealRequested) "show_on_screen"
                    else if (scrollAction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) "scroll_forward" else "scroll_backward")
                .put("accepted", scrolled).put("previous_failure", lastFailure?.message))
            check(scrolled) { "Scroll ended before the complete text was visible: ${lastFailure?.message}" }
            // Allow the real Compose scroll animation to settle before obtaining fresh node/character bounds.
            SystemClock.sleep(350)
            instrumentation.waitForIdleSync()
        }
        assertFullyVisibleText(text, viewport, referenceCharacterBounds)
    }

    fun click(node: AccessibilityNodeInfo) {
        var clickable: AccessibilityNodeInfo? = null
        // Parents may still contain a disabled snapshot after the label has refreshed.
        // Refresh the entire ancestry, then dispatch exactly one click after it is ready.
        await("enabled click action for ${node.text ?: node.contentDescription}", 2_000) {
            var current: AccessibilityNodeInfo? = node
            clickable = null
            while (current != null && current.refresh()) {
                if (current.isClickable) {
                    clickable = current
                    break
                }
                current = current.parent
            }
            clickable?.let { it.isEnabled && it.actionList.any { action ->
                action.id == AccessibilityNodeInfo.ACTION_CLICK
            } } == true
        }
        check(clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
            "No enabled click action for ${node.text ?: node.contentDescription}"
        }
        instrumentation.waitForIdleSync()
    }

    fun click(text: String) = click(visible(text))

    fun edit(text: String) {
        val editable = checkNotNull(nodes().firstOrNull { it.isEditable && it.isVisibleToUser && it.isEnabled }) {
            "No enabled visible editor"
        }
        check(editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        })) { "Editor did not accept ACTION_SET_TEXT" }
        instrumentation.waitForIdleSync()
    }

    fun scrollTo(text: String) {
        var direction = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        repeat(24) {
            if (matching(text) != null) return
            val container = checkNotNull(nodes().firstOrNull { it.isScrollable && it.isVisibleToUser }) {
                "No scroll container while looking for '$text'"
            }
            val available = container.actionList.map { it.id }
            if (direction !in available) {
                direction = if (direction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            check(direction in available && container.performAction(direction)) {
                "Scroll ended before '$text'"
            }
            SystemClock.sleep(280)
            instrumentation.waitForIdleSync()
        }
        error("Could not reach '$text'")
    }

    fun visibleAppFrame(activity: Activity): Rect = Rect().also { rect ->
        instrumentation.runOnMainSync { activity.window.decorView.getWindowVisibleDisplayFrame(rect) }
    }

    fun capture(activity: Activity, output: File, name: String) {
        val expectedPackage = instrumentation.targetContext.packageName
        var observedPackage: String? = null
        var previousWindowId: Int? = null
        var stableSamples = 0
        try {
            // Dismissing an app dialog can briefly leave accessibility without an active root.
            // Wait for our own window; never capture a different app during that transition.
            await("stable app window before capture '$name'", timeoutMs = 2_000) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                observedPackage = root?.packageName?.toString()
                val ready = observedPackage == expectedPackage && root?.refresh() == true
                val windowId = root?.windowId.takeIf { ready }
                stableSamples = if (ready && windowId == previousWindowId) stableSamples + 1
                    else if (ready) 1 else 0
                previousWindowId = windowId
                stableSamples >= 2
            }
        } catch (failure: IllegalStateException) {
            throw IllegalStateException("Cannot capture '$name': expected active package '$expectedPackage', " +
                "observed '${observedPackage ?: "<no active root>"}'. Another app, system dialog or window transition covers the fixture.", failure)
        }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        var cropped: Bitmap? = null
        try {
            val packageAfterCapture = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
            check(packageAfterCapture == expectedPackage) {
                "Discarding capture '$name': active package changed to '${packageAfterCapture ?: "<no active root>"}', expected '$expectedPackage'."
            }
            val rect = visibleAppFrame(activity)
            check(rect.intersect(0, 0, bitmap.width, bitmap.height))
            val capture = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
            cropped = capture
            val file = File(output, "$name.png")
            file.outputStream().use { check(capture.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            captures.put(JSONObject().put("name", name).put("file", file.name)
                .put("app_frame", rect.flattenToString()))
        } finally {
            if (cropped !== bitmap) cropped?.recycle()
            bitmap.recycle()
        }
    }
}
