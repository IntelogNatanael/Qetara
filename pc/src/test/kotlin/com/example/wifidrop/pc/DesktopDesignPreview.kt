package com.example.wifidrop.pc

import androidx.compose.material.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.example.wifidrop.protocol.flash.FlashApproval
import com.example.wifidrop.protocol.flash.FlashListener
import com.example.wifidrop.protocol.flash.FlashOfferedFile
import com.example.wifidrop.protocol.flash.FlashPeer
import com.example.wifidrop.protocol.flash.FlashState
import com.example.wifidrop.protocol.flash.FlashOperation
import com.example.wifidrop.protocol.flash.FlashReceived
import com.example.wifidrop.protocol.flash.FlashCompleted
import com.example.wifidrop.protocol.flash.FlashError
import com.example.wifidrop.protocol.flash.FlashProgress
import com.example.wifidrop.protocol.flash.FlashText
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import org.jetbrains.skia.EncodedImageFormat

/**
 * Manual design tool, not a JUnit test. Renders the production composables in an offscreen scene.
 * No sockets, device discovery, production preferences, native windows, or OS input are used.
 * Run :pc:designPreview [-PpreviewScenario=all|empty|selected|selected-many|send-needs-input|
 * receive-starting|receive-stopping|receive-expired|flash|flash-off|flash-empty|flash-no-receiver|
 * flash-ready|flash-text|flash-transfer|flash-error|flash-expired|flash-approvals].
 * Flash approval scenarios also accept flash-approval-single|send|receive|many individually.
 * The optional files-dialog scenario checks the actual review dialog separately.
 */
@OptIn(ExperimentalComposeUiApi::class)
object DesktopDesignPreview {
    @JvmStatic
    fun main(args: Array<String>) {
        val output = File(args.getOrElse(0) { ".local/design-review" }).absoluteFile
        check(output.isDirectory || output.mkdirs()) { "Cannot create design preview output" }
        val requested = args.getOrElse(1) { "all" }
        val scenarios = listOf(
            "empty", "selected", "selected-many", "send-needs-input",
            "receive-starting", "receive-stopping", "receive-expired", "flash-off", "flash-empty", "flash-no-receiver",
            "flash-ready", "flash-text", "flash-transfer", "flash-error", "flash-expired",
            "flash-approval-single", "flash-approval-send", "flash-approval-receive", "flash-approval-many",
            "channel-receiver-off", "channel-text-only", "channel-ready", "channel-queued", "channel-close"
        )
        require(requested in listOf("all", "files-dialog", "flash", "flash-approvals", "channel") || requested in scenarios) { "Unknown preview scenario: $requested" }
        val fixtures = createFixtures(output)
        val artifacts = mutableListOf<String>()

        val requestedScenarios = if (requested == "files-dialog") listOf(requested) else scenarios.filter {
            requested == "all" || requested == it || (requested == "flash-approvals" && it.startsWith("flash-approval-")) ||
                (requested == "flash" && it.startsWith("flash-")) ||
                (requested == "channel" && it.startsWith("channel-"))
        }
        for (scenario in requestedScenarios) {
            var flashTransport: PreviewFlashTransport? = null
            val controller = onEdt {
                DesktopFlashController(File(PREVIEW_RECEIVED_DIRECTORY)) { _, _, listener ->
                    PreviewFlashTransport(listener, hasReceiver = scenario !in listOf("flash-empty", "flash-no-receiver"))
                        .also { flashTransport = it }
                }
            }
            try {
                if (scenario.startsWith("flash-") && scenario != "flash-off") {
                    onEdt { controller.start("Qetara de ejemplo") }
                    awaitState(controller, "activation") { session.active }
                    onEdt {
                        if (scenario in listOf("flash-ready", "flash-text", "flash-transfer", "flash-error")) controller.chooseFiles(fixtures)
                        else if (scenario.startsWith("flash-approval-")) controller.chooseFiles(fixtures.take(2))
                        controller.state.session.peers.singleOrNull()?.let(controller::choosePeer)
                    }
                }
                if (scenario in listOf("flash-text", "flash-no-receiver", "flash-expired")) {
                    onEdt {
                        controller.updateTextDraft("Revisemos la propuesta a las 10:00.\nLleva tus comentarios 🙂")
                    }
                }
                if (scenario == "flash-text") {
                    fixtures.forEach { checkNotNull(flashTransport).showReceivedFile(it) }
                    listOf(
                        "De acuerdo. Ya tengo las notas.\nNos vemos a las 10:00 🙂",
                        "Texto completo para revisar la expansión y el copiado.\n" +
                            (1..8).joinToString("\n") { "Punto $it: coordinar la revisión del proyecto y conservar los comentarios de todo el equipo." }
                    ).forEach { body ->
                        checkNotNull(flashTransport).showReceivedFile(FlashText.createFile(File(output, "fixtures"), body))
                    }
                    awaitState(controller, "seven completed recent items") { transfers.size == 7 && transfers.none { it.busy } }
                }
                if (scenario in listOf("flash-transfer", "flash-error")) {
                    onEdt { controller.send() }
                    awaitState(controller, "visible transfer progress") { transfers.any { it.phase == DesktopFlashPhase.TRANSFERRING } }
                    if (scenario == "flash-error") {
                        checkNotNull(flashTransport).failCurrentTransfer()
                        awaitState(controller, "failed transfer") { error && !busy }
                    }
                }
                if (scenario == "flash-expired") {
                    checkNotNull(flashTransport).showReceivedFile(fixtures.first())
                    awaitState(controller, "completed receipt before expiry") { transfers.singleOrNull()?.phase == DesktopFlashPhase.COMPLETE }
                    checkNotNull(flashTransport).expire()
                    awaitState(controller, "expired session") { !session.active }
                }
                val sizes = if (scenario.startsWith("channel-")) listOf(1160 to 1180, 800 to 1180)
                    else listOf(1160 to 800, 800 to 620)
                for ((width, height) in sizes) {
                    val state = workspaceFixture(fixtures, scenario)
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
            Coverage includes Compartir empty/selected, a larger selection with more than eight peers,
            a failed send with a current input issue, Recibir starting/stopping/expired, and Flash
            off, active without peers, text without a receiver, five ready files, text with seven
            recent items, a transferring batch, a failed transfer and an expired session at 1160x800
            and 800x620, density 1. These are full workspace dimensions including the sidebar.
            The active transfer is driven through the real controller's send command and its progress
            buffer; the in-memory transport emits synthetic callbacks without opening a connection.
            Flash approval previews cover a single file,
            three-file sending/receiving and 128 files with a long name, using the production dialog.
            The flash-approvals filter exports those four approval scenarios only.
            The flash filter exports all Flash scenarios, including the four approval dialogs.
            The production desktop palette is currently fixed to its light colors; this tool does
            not invent a dark theme or modify the user's appearance preference.
            A filtered run exports only its selected scenarios.
            The empty workspace has missing-code/PIN/file/destination issues, so sending and receiving
            are disabled as in production. The selected fixture has valid synthetic credentials, a
            destination and two readable files; its issue lists are empty. Both provide nonempty
            discovery and sending status text. These are supplied states, not interaction-test results.
            Recibir renders the production tab selected through its initial-state parameter.
            The channel filter renders the production channel content with synthetic states for a
            stopped receiver, text only, a ready attachment, queued requests and the close dialog.
            Its tall 1160/800 x 1180 views expose the complete content for layout inspection; they
            do not validate scrolling or keyboard interaction in a real application window.
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
                    chatContent = { openReceive ->
                        if (scenario.startsWith("channel-")) channelFixture(scenario, state, openReceive)
                    },
                    activityContent = {},
                    initialTab = when {
                        scenario.startsWith("receive-") -> WorkspaceTab.RECEIVE
                        scenario.startsWith("channel-") -> WorkspaceTab.CHAT
                        else -> WorkspaceTab.SHARE
                    },
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
                if (scenario == "files-dialog") DesktopSelectedFilesDialog(state.filePaths.map(::File), onDismiss = {})
                if (scenario.startsWith("flash-approval-")) {
                    DesktopFlashApprovalDialog(approvalFixture(scenario), count = 1, now = 1_000L, decide = {})
                }
                if (scenario == "channel-close") DesktopCloseConfirmationDialog(onContinue = {}, onExit = {})
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

    @Composable
    private fun channelFixture(scenario: String, state: DesktopWorkspaceState, openReceive: () -> Unit) {
        val queued = scenario == "channel-queued"
        val offerMessages = if (queued) listOf("first", "second").mapIndexed { index, id ->
            DesktopChatEntry(
                timestamp = "10:00", scope = DesktopChatScope.GLOBAL_LAN, direction = DesktopChatDirection.INCOMING,
                peerLabel = "Equipo de ejemplo", peerAddress = "192.0.2.20",
                message = "\u2063QCT\u2063{\"kind\":\"file_offer\",\"scope\":\"GLOBAL_LAN\",\"offer_id\":\"$id\",\"file_name\":\"Documento ${index + 1}.txt\",\"file_size_bytes\":1024,\"sender_id\":\"preview-peer\",\"sender_label\":\"Equipo de ejemplo\",\"sender_ip\":\"192.0.2.20\",\"created_at_ms\":1000}"
            )
        } else emptyList()
        Column {
            Text("Canal Wi-Fi", style = MaterialTheme.typography.h6)
            DesktopChatScopeContent(
                scope = DesktopChatScope.GLOBAL_LAN,
                lanPeers = state.peers.map { it.copy(globalLanJoined = true) },
                selectedDirectPeerIp = null, onSelectDirectPeer = {}, globalLanJoined = true,
                draft = if (queued) "" else "Notas para la próxima reunión.", onDraftChange = {},
                attachmentPath = if (scenario in listOf("channel-receiver-off", "channel-ready")) state.filePaths.first() else "",
                onChooseAttachment = {}, onClearAttachment = {}, messages = offerMessages,
                status = if (queued) "2 solicitudes del canal pendientes." else "Conversación del Canal Wi-Fi.",
                messagePhase = DesktopTaskPhase.IDLE, discoveryStatus = "1 equipo disponible.",
                discoveryPhase = DesktopTaskPhase.IDLE, onRefreshLan = {}, onSend = {},
                autoDownloadChannelFiles = queued, onAutoDownloadChannelFilesChange = {},
                onDownloadChannelFileOffer = {}, credentialsReady = true,
                receiverActive = scenario !in listOf("channel-receiver-off", "channel-text-only"),
                onOpenReceive = openReceive,
                channelRequestStatuses = if (queued) mapOf(
                    DesktopChannelOfferKey("192.0.2.20", "first") to DesktopChannelRequestStatus(DesktopChannelRequestPhase.REQUESTING, "Solicitando el archivo…"),
                    DesktopChannelOfferKey("192.0.2.20", "second") to DesktopChannelRequestStatus(DesktopChannelRequestPhase.QUEUED, "Solicitud en cola.")
                ) else emptyMap()
            )
        }
    }

    internal fun workspaceFixture(files: List<File>, scenario: String): DesktopWorkspaceState {
        val selected = scenario != "empty" && !scenario.startsWith("flash-")
        val selectedFiles = if (scenario in listOf("selected-many", "files-dialog")) files else files.take(2)
        val base = DesktopWorkspaceState(
            token = if (selected) "DEMO1234" else "",
            pin = if (selected) "123456" else "",
            deviceName = "Qetara de ejemplo",
            outputDirectory = PREVIEW_RECEIVED_DIRECTORY,
            host = if (selected) "192.0.2.20" else "",
            filePaths = if (selected) selectedFiles.map { it.absolutePath } else emptyList(),
            port = "8988", retries = "3", sessionMinutes = "30",
            localEndpoints = listOf(LocalNetworkEndpoint("Red de ejemplo", "192.0.2.10", 0)),
            peers = if (selected) listOf(DesktopLanPeer("preview-peer", "Equipo de ejemplo", "192.0.2.20", true, true, false, 0L)) else emptyList(),
            discoveryPhase = DesktopTaskPhase.IDLE,
            discoveryStatus = if (selected) "Equipos detectados: 1."
                else "Busca los equipos Qetara cercanos para elegir un destino.",
            receiverPhase = DesktopTaskPhase.IDLE, receiverStatus = "Recepción desactivada.",
            sendingPhase = DesktopTaskPhase.IDLE,
            sendingStatus = if (selected) "${selectedFiles.size} archivos listos para enviar."
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
        return when (scenario) {
            "channel-ready", "channel-queued", "channel-close" -> base.copy(
                receiverPhase = DesktopTaskPhase.RUNNING, receiverStatus = "Recepción activa.",
                receivingProgress = if (scenario == "channel-close") .4f else null
            )
            "selected-many" -> base.copy(
                host = "192.0.2.30",
                peers = (21..30).map { address ->
                    DesktopLanPeer("preview-$address", "Equipo de ejemplo $address", "192.0.2.$address", true, true, false, 0L)
                }
            )
            "send-needs-input" -> base.copy(
                host = "", sendingPhase = DesktopTaskPhase.ERROR,
                sendingStatus = "El equipo no responde. Activa Recibir en el otro equipo y comprueba la misma red y puerto.",
                sendIssues = listOf("Busca un equipo receptor o escribe su IP.")
            )
            "receive-starting" -> base.copy(receiverPhase = DesktopTaskPhase.STARTING, receiverStatus = "Iniciando receptor en puerto 8988…")
            "receive-stopping" -> base.copy(receiverPhase = DesktopTaskPhase.STOPPING, receiverStatus = "Deteniendo receptor…")
            "receive-expired" -> base.copy(receiverStatus = "La sesión terminó. Activa Recibir para iniciar otra sesión.", sessionRemaining = "Sin sesión")
            else -> base
        }
    }

    private fun approvalFixture(scenario: String): FlashApproval {
        val files = when (scenario) {
            "flash-approval-single" -> listOf(FlashOfferedFile("Notas de reunión.txt", 1024L * 1024))
            "flash-approval-many" -> List(128) { index ->
                FlashOfferedFile(
                    if (index == 0) "Notas-de-la-reunión-del-proyecto-con-un-nombre-largo-para-comprobar-que-se-puede-identificar-completo-sin-recortes.txt"
                    else "Documento ${index + 1} de la selección.pdf", (index + 1L) * 1024 * 1024
                )
            }
            else -> listOf(
                FlashOfferedFile("Notas de reunión.txt", 1024L * 1024),
                FlashOfferedFile("Esquema del proyecto.pdf", 2L * 1024 * 1024),
                FlashOfferedFile("Imagen de muestra.png", 3L * 1024 * 1024)
            )
        }
        return FlashApproval(
            requestId = "preview-approval", operationId = "preview-batch",
            peer = FlashPeer("preview-peer", "Equipo de ejemplo", "192.0.2.20", 8989, 1_800_000L),
            fileName = files.first().fileName, totalBytes = files.sumOf { it.totalBytes },
            outgoing = scenario != "flash-approval-receive", verificationCode = "ABCD 2345 EF67 89AB",
            expiresAtMs = 91_000L, files = files
        )
    }

    internal fun previewActions() = DesktopWorkspaceActions(
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
            },
            File(directory, "Notas-de-la-reunion-con-un-nombre-largo-para-verificar-la-seleccion.txt").apply { writeText("Vista de ejemplo.\n") },
            File(directory, "Resumen.txt").apply { writeText("Resumen de ejemplo.\n") },
            File(directory, "Copia/Resumen.txt").apply { parentFile.mkdirs(); writeText("Otro resumen de ejemplo.\n") }
        )
    }

    private class PreviewFlashTransport(private val listener: FlashListener, hasReceiver: Boolean) : DesktopFlashTransport {
        private val expiry = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)
        private val peer = FlashPeer("00000000-0000-4000-8000-000000000002", "Equipo de ejemplo", "192.0.2.20", 8989, expiry)
        private val active = FlashState(true, expiry, "00000000-0000-4000-8000-000000000001", 8989,
            peers = if (hasReceiver) listOf(peer) else emptyList())
        private var currentOperation: FlashOperation? = null
        private var receivedCount = 0
        override fun start() = listener.onState(active)
        override fun discover() = listener.onState(active)
        override fun stop() = listener.onState(FlashState())
        override fun discoverAt(address: String, port: Int) = error("Preview cannot discover network peers")
        override fun sendBatch(files: List<File>, peer: FlashPeer): String {
            val file = files.first()
            val operation = FlashOperation("preview-sending-file", peer, file.name, file.length(), true,
                batchId = "preview-sending-batch", fileCount = files.size)
            currentOperation = operation
            listener.onState(active.copy(operations = listOf(operation)))
            listener.onProgress(FlashProgress(operation.id, operation.totalBytes * 43 / 100, operation.totalBytes, true))
            return operation.batchId
        }
        override fun approve(requestId: String, accepted: Boolean): Boolean = error("Preview cannot approve transfers")
        override fun cancel(operationId: String): Boolean = false
        fun showReceivedFile(file: File) {
            val id = "preview-received-${++receivedCount}"
            listener.onState(active.copy(operations = listOf(FlashOperation(id, peer, file.name, file.length(), false))))
            listener.onReceived(FlashReceived(id, peer, file))
            listener.onCompleted(FlashCompleted(id, peer, file.name, false))
            listener.onState(active)
        }
        fun failCurrentTransfer() {
            val operation = checkNotNull(currentOperation)
            listener.onError(FlashError(operation.id, "connection_lost",
                "Se perdió la conexión con el otro equipo. Comprueba que Flash siga activo antes de volver a enviar."))
            listener.onState(active)
            currentOperation = null
        }
        fun expire() = listener.onState(FlashState())
    }

    private fun awaitState(controller: DesktopFlashController, description: String, predicate: DesktopFlashUiState.() -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!onEdt { controller.state.predicate() }) {
            check(System.nanoTime() < deadline) { "Synthetic Flash state did not reach $description" }
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
