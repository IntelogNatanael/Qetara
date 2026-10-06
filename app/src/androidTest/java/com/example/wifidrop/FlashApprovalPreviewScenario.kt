package com.example.wifidrop

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.wifidrop.protocol.flash.FlashApproval
import com.example.wifidrop.protocol.flash.FlashOfferedFile
import com.example.wifidrop.protocol.flash.FlashPeer
import com.example.wifidrop.protocol.flash.FlashState
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Opt-in visual QA: real FlashActivity and dialog, synthetic process-only state, no network/service.
 * Run the existing instrumentation runner with -e scenario flash-approval-preview.
 * Captures are retained under the app's external files directory for inspection, not asserted pixels.
 */
internal class FlashApprovalPreviewScenario(private val instrumentation: Instrumentation) {
    fun run() {
        val results = Bundle()
        val original = FlashAndroidRuntime.state.value
        val captures = JSONArray()
        var activity: Activity? = null
        var output: File? = null
        var replacedState = false
        try {
            check(original.phase == FlashAndroidPhase.OFF && original.engine?.active != true && !original.importing) {
                "Close Flash and finish file preparation before running the synthetic preview"
            }
            val context = instrumentation.targetContext
            val directory = File(context.getExternalFilesDir(null) ?: context.cacheDir,
                "flash-approval-preview-${System.currentTimeMillis()}")
            check(directory.mkdirs()) { "Cannot create preview directory" }
            output = directory
            results.putString("output_directory", directory.absolutePath)
            val scenarios = listOf("send-3", "receive-3", "send-128", "receive-128")
            for (scenario in scenarios) {
                val outgoing = scenario.startsWith("send-")
                val count = if (scenario.endsWith("128")) 128 else 3
                val approval = fixture(scenario, count, outgoing)
                replacedState = true
                instrumentation.runOnMainSync {
                    FlashAndroidRuntime.update {
                        FlashAndroidState(
                            phase = FlashAndroidPhase.ACTIVE,
                            engine = FlashState(active = true, expiresAtMs = approval.peer.expiresAtMs,
                                localId = "preview-local", approvals = listOf(approval), peers = listOf(approval.peer)),
                            status = "Vista de ejemplo sin conexión", deviceLabel = "Android de ejemplo"
                        )
                    }
                }
                if (activity == null) {
                    activity = instrumentation.startActivitySync(
                        Intent(context, FlashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                val action = "Coincide: ${if (outgoing) "enviar" else "recibir"} $count archivos"
                awaitVisible(action)
                awaitVisible("$count archivos ·")
                capture(directory, "$scenario-top", captures)
                if (count == 128) {
                    scrollFilesToEnd(approval.files.first().fileName, approval.files.last().fileName)
                    capture(directory, "$scenario-list-end", captures)
                }
            }
            File(directory, "manifest.json").writeText(JSONObject()
                .put("source", "Production FlashActivity with synthetic FlashAndroidRuntime state")
                .put("network", "none")
                .put("approvals_submitted", false)
                .put("captures", captures)
                .put("scope", "Visual artifacts and accessibility scroll reachability; not transfer validation")
                .toString(2))
            results.putInt("captures_created", captures.length())
            results.putString("result", "PASS")
        } catch (failure: Throwable) {
            results.putString("result", "FAIL")
            results.putString("failure", failure.stackTraceToString())
            val windowPackage = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
            results.putString("active_window_package", windowPackage ?: "unavailable")
            results.putBoolean("synthetic_approval_present", replacedState && FlashAndroidRuntime.state.value.engine?.approvals?.isNotEmpty() == true)
            // A permission dialog or another app can cover the fixture. Never capture that surface.
            val directory = output
            if (activity != null && directory != null && windowPackage == instrumentation.targetContext.packageName) {
                runCatching { capture(directory, "failure-app-only", captures) }
            }
        } finally {
            instrumentation.runOnMainSync {
                activity?.finish()
                if (replacedState) FlashAndroidRuntime.update { original }
            }
            instrumentation.waitForIdleSync()
            results.putBoolean("process_state_restored", FlashAndroidRuntime.state.value == original)
            instrumentation.finish(
                if (results.getString("result") == "PASS") Activity.RESULT_OK else Activity.RESULT_CANCELED, results
            )
        }
    }

    private fun fixture(scenario: String, count: Int, outgoing: Boolean): FlashApproval {
        val files = if (count == 3) listOf(
            FlashOfferedFile("Notas de reunión.txt", 1024L * 1024),
            FlashOfferedFile("Esquema del proyecto.pdf", 2L * 1024 * 1024),
            FlashOfferedFile("Imagen de muestra.png", 3L * 1024 * 1024)
        ) else List(count) { index ->
            FlashOfferedFile(
                if (index == 0) "Notas-de-la-reunión-del-proyecto-con-un-nombre-largo-para-comprobar-que-se-puede-identificar-completo-sin-recortes.txt"
                else "Documento ${index + 1} de la selección.pdf", (index + 1L) * 1024 * 1024
            )
        }
        val now = System.currentTimeMillis()
        return FlashApproval(
            requestId = "preview-$scenario", operationId = "preview-batch-$scenario",
            peer = FlashPeer("preview-peer", "Equipo de ejemplo", "192.0.2.20", 8989, now + 30 * 60_000L),
            fileName = files.first().fileName, totalBytes = files.sumOf { it.totalBytes }, outgoing = outgoing,
            verificationCode = "ABCD 2345 EF67 89AB", expiresAtMs = now + 120_000L, files = files
        )
    }

    private fun awaitVisible(text: String) {
        check(waitForVisible(text, 5_000L)) { "Preview text did not become visible: $text" }
    }

    private fun waitForVisible(text: String, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        do {
            instrumentation.waitForIdleSync()
            if (visibleNode(text) != null) return true
            SystemClock.sleep(50L)
        } while (SystemClock.uptimeMillis() < deadline)
        return false
    }

    private fun visibleNode(text: String): AccessibilityNodeInfo? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        if (root.packageName?.toString() != instrumentation.targetContext.packageName) return null
        // Compose exposes virtual nodes; search their actual hierarchy instead of relying on
        // AccessibilityNodeProvider's optional findAccessibilityNodeInfosByText implementation.
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited++ < 4096) {
            val node = pending.removeFirst()
            if (!node.refresh()) continue
            if (node.text?.contains(text) == true || node.contentDescription?.contains(text) == true) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (node.isVisibleToUser && !bounds.isEmpty) return node
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(pending::addLast)
        }
        return null
    }

    private fun scrollFilesToEnd(firstName: String, lastName: String) {
        val initial = checkNotNull(visibleNode(firstName)) { "First file is not visible in the preview" }
        var ancestor: AccessibilityNodeInfo? = initial
        while (ancestor != null && !ancestor.isScrollable) ancestor = ancestor.parent
        val list = checkNotNull(ancestor) { "File list has no accessible scroll container" }
        repeat(128) {
            val scrollRequested = list.refresh() && list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            // A partly exposed last row is visible to accessibility but is not a complete visual
            // check. Drain scrolling to its limit, then let the final animation finish before
            // checking the target on a fresh tree and capturing its full name and size.
            SystemClock.sleep(400L)
            instrumentation.waitForIdleSync()
            if (!scrollRequested) {
                check(waitForVisible(lastName, 2_000L)) {
                    "File list stopped and its last offered file remained inaccessible after waiting"
                }
                return
            }
        }
        error("Could not reach the last offered file in the preview")
    }

    private fun capture(output: File, name: String, captures: JSONArray) {
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(150L, 5_000L)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Android screenshot unavailable" }
        try {
            val file = File(output, "$name.png")
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            captures.put(JSONObject().put("name", name).put("file", file.name)
                .put("width", bitmap.width).put("height", bitmap.height))
        } finally { bitmap.recycle() }
    }
}
