package com.example.wifidrop.pc

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.example.wifidrop.protocol.flash.FlashListener
import com.example.wifidrop.protocol.flash.FlashPeer
import com.example.wifidrop.protocol.flash.FlashState
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import org.jetbrains.skia.EncodedImageFormat

/**
 * Manual design tool, not a JUnit test. Renders the production composables in an offscreen scene.
 * No sockets, device discovery, production preferences, native windows, or OS input are used.
 * Run :pc:designPreview [-PpreviewScenario=empty|selected|flash-off|flash-ready].
 */
@OptIn(ExperimentalComposeUiApi::class)
object DesktopDesignPreview {
    @JvmStatic
    fun main(args: Array<String>) {
        val output = File(args.getOrElse(0) { ".local/design-review" }).absoluteFile
        check(output.isDirectory || output.mkdirs()) { "Cannot create design preview output" }
        val requested = args.getOrElse(1) { "all" }
        val scenarios = listOf("empty", "selected", "flash-off", "flash-ready")
        require(requested == "all" || requested in scenarios) { "Unknown preview scenario: $requested" }
        val fixtures = createFixtures(output)
        val artifacts = mutableListOf<String>()

        for (scenario in scenarios.filter { requested == "all" || requested == it }) {
            val controller = onEdt {
                DesktopFlashController(File(PREVIEW_RECEIVED_DIRECTORY)) { _, _, listener ->
                    PreviewFlashTransport(listener)
                }
            }
            try {
                if (scenario == "flash-ready") {
                    onEdt { controller.start("Qetara de ejemplo") }
                    awaitActivation(controller)
                    onEdt {
                        controller.chooseFiles(fixtures)
                        controller.choosePeer(controller.state.session.peers.single())
                    }
                }
                for ((width, height) in listOf(1160 to 800, 800 to 620)) {
                    val state = workspaceFixture(fixtures, selected = scenario == "selected")
                    val png = onEdt { render(width, height, scenario, state, controller) }
                    val stem = "desktop-$scenario-${width}x$height"
                    File(output, "$stem.png").writeBytes(png)
                    val encoded = Base64.getEncoder().encodeToString(png)
                    File(output, "$stem.svg").writeText(
                        """<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height"><title>Qetara production Compose preview: $scenario</title><desc>Raster image embedded in SVG; synthetic data, no live network. This is not an editable vector reconstruction.</desc><image width="$width" height="$height" href="data:image/png;base64,$encoded"/></svg>"""
                    )
                    val hash = MessageDigest.getInstance("SHA-256").digest(png).joinToString("") { "%02x".format(it) }
                    artifacts += """{"scenario":"$scenario","width":$width,"height":$height,"png":"$stem.png","svg":"$stem.svg","png_sha256":"$hash"}"""
                    println("Rendered $stem.png (${png.size} bytes)")
                }
            } finally {
                onEdt { controller.close() }
            }
        }
        File(output, "design-preview-manifest.json").writeText(
            """{"renderer":"ImageComposeScene 1.8.2 / Skia","density":1,"source":"current working tree","data":"synthetic","network":"none","svg_kind":"embedded PNG","artifacts":[${artifacts.joinToString(",")}]}""" + "\n"
        )
        File(output, "README-preview.md").writeText(
            """
            # Offline desktop design previews

            These PNGs render the real DesktopWorkspace and DesktopFlashPanel with the production
            colors, Inter typography, shapes and logo. Data and selected files are synthetic. Flash
            uses an injected in-memory transport; no sockets, servers, ADB, native windows, saved
            sessions or OS input are involved. This tool does not validate transfers or interaction.

            Each SVG embeds the matching PNG; it is a raster container for import, not reconstructed
            vector UI. qetara-brand.svg in desktop resources remains the original vector artwork.

            This run exported ${artifacts.size} views: ${artifacts.size} PNGs and ${artifacts.size} SVG containers.
            Default all-scenario coverage is eight views: Compartir empty/selected and Flash off/ready
            at 1160x800 and 800x620, density 1. A filtered run exports only its selected scenario.
            The empty workspace has missing-code/PIN/file/destination issues, so sending and receiving
            are disabled as in production. The selected fixture has valid synthetic credentials, a
            destination and two readable files; its issue lists are empty. Both provide nonempty
            discovery and sending status text. These are supplied states, not interaction-test results.
            Recibir and Mensajes are not represented: tab selection and the real chat content are
            private production details. The preview does not replace those views with imitation UI.
            No claim of complete visual QA or Figma alignment follows from generating these images.
            """.trimIndent() + "\n"
        )
    }

    private fun render(
        width: Int,
        height: Int,
        scenario: String,
        state: DesktopWorkspaceState,
        controller: DesktopFlashController
    ): ByteArray {
        val scene = ImageComposeScene(width, height, density = Density(1f)) {
            MaterialTheme(colors = qetaraDesktopColors, typography = qetaraTypography, shapes = qetaraShapes) {
                DesktopWorkspace(
                    state = state.copy(flashActive = controller.state.session.active),
                    actions = previewActions(),
                    chatContent = {},
                    activityContent = {},
                    flashVisible = scenario.startsWith("flash-"),
                    flashContent = {
                        DesktopFlashPanel(
                            controller = controller,
                            deviceName = "Qetara de ejemplo",
                            localAddresses = listOf("192.0.2.10"),
                            onBack = {}, onChooseFile = {}, onChooseDirectory = {}
                        )
                    }
                )
            }
        }
        try {
            // Advance only the offscreen frame clock, with bounded work for active animations.
            repeat(4) { frame -> scene.render(frame * 16_666_667L).close() }
            val image = scene.render(1_000_000_000L)
            try {
                val encoded = checkNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "PNG encoding failed" }
                return try { encoded.bytes } finally { encoded.close() }
            } finally {
                image.close()
            }
        } finally {
            scene.close()
        }
    }

    private fun workspaceFixture(files: List<File>, selected: Boolean) = DesktopWorkspaceState(
        token = if (selected) "DEMO1234" else "",
        pin = if (selected) "123456" else "",
        deviceName = "Qetara de ejemplo",
        outputDirectory = PREVIEW_RECEIVED_DIRECTORY,
        host = if (selected) "192.0.2.20" else "",
        filePaths = if (selected) files.map { it.absolutePath } else emptyList(),
        port = "8988", retries = "3", sessionMinutes = "30",
        localEndpoints = listOf(LocalNetworkEndpoint("Red de ejemplo", "192.0.2.10", 0)),
        peers = if (selected) listOf(DesktopLanPeer("preview-peer", "Equipo de ejemplo", "192.0.2.20", true, true, false, 0L)) else emptyList(),
        discoveryPhase = DesktopTaskPhase.IDLE,
        discoveryStatus = if (selected) "Equipos detectados: 1."
            else "Busca los equipos Qetara cercanos para elegir un destino.",
        receiverPhase = DesktopTaskPhase.IDLE, receiverStatus = "Recepción desactivada.",
        sendingPhase = DesktopTaskPhase.IDLE,
        sendingStatus = if (selected) "${files.size} archivos listos para enviar."
            else "Elige uno o varios archivos para compartir.",
        sendingProgress = null, receivingProgress = null,
        // Same missing-input issues and ordering as production's token/PIN/file/host validation.
        receiverIssues = if (selected) emptyList() else listOf(
            "Crea una sesión o escribe el código del equipo receptor.",
            "Escribe el PIN de 6 dígitos del equipo receptor."
        ),
        sendIssues = if (selected) emptyList() else listOf(
            "Crea una sesión o escribe el código del equipo receptor.",
            "Escribe el PIN de 6 dígitos del equipo receptor.",
            "Selecciona al menos un archivo.",
            "Busca un equipo receptor o escribe su IP."
        ),
        credentialsReady = selected, isFileDragActive = false,
        transfers = emptyList(), notice = null,
        sessionRemaining = if (selected) "30 min" else "Sin sesión",
        identityFingerprint = "3f1c 7a20 94d8 0b5e a612 2d80 fbc4 719e"
    )

    private fun previewActions() = DesktopWorkspaceActions(
        onTokenChange = {}, onPinChange = {}, onDeviceNameChange = {}, onOutputDirectoryChange = {},
        onHostChange = {}, onPortChange = {}, onRetriesChange = {}, onSessionMinutesChange = {},
        onCreateSession = {}, onCopySession = {}, onChooseFile = {}, onClearFiles = {},
        onChooseDirectory = {}, onOpenDirectory = {}, onOpenReceivedFile = {},
        onRefreshPeers = {}, onSelectPeer = {}, onStartReceiver = {}, onStopReceiver = {},
        onSend = {}, onCancelSend = {}, onDismissNotice = {}, onChatVisibilityChange = {},
        onSaveSettings = {}, onOpenSource = {}
    )

    private fun createFixtures(output: File): List<File> {
        val directory = File(output, "fixtures").apply { check(isDirectory || mkdirs()) }
        return listOf(
            File(directory, "Notas-del-proyecto.txt").apply {
                writeText("Documento sintetico para revisar la seleccion de archivos.\n".repeat(300))
            },
            File(directory, "Mediciones.csv").apply {
                writeText("muestra,valor\n" + (1..150).joinToString("\n") { "$it,${it * 3}" } + "\n")
            }
        )
    }

    private class PreviewFlashTransport(private val listener: FlashListener) : DesktopFlashTransport {
        private val expiry = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)
        private val peer = FlashPeer("00000000-0000-4000-8000-000000000002", "Equipo de ejemplo", "192.0.2.20", 8989, expiry)
        private val active = FlashState(true, expiry, "00000000-0000-4000-8000-000000000001", 8989, peers = listOf(peer))
        override fun start() = listener.onState(active)
        override fun discover() = listener.onState(active)
        override fun stop() = listener.onState(FlashState())
        override fun discoverAt(address: String, port: Int) = error("Preview cannot discover network peers")
        override fun send(file: File, peer: FlashPeer): String = error("Preview cannot send files")
        override fun approve(requestId: String, accepted: Boolean): Boolean = error("Preview cannot approve transfers")
        override fun cancel(operationId: String): Boolean = false
    }

    private fun awaitActivation(controller: DesktopFlashController) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!onEdt { controller.state.session.active && controller.state.session.peers.isNotEmpty() }) {
            check(System.nanoTime() < deadline) { "Synthetic Flash state did not activate" }
            Thread.sleep(10)
        }
    }

    private fun <T> onEdt(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return action()
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }

    private const val PREVIEW_RECEIVED_DIRECTORY = "/Qetara-demo/Recibidos"
}
