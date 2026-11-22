package com.example.wifidrop.pc

import com.example.wifidrop.protocol.flash.*
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopFlashControllerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun droppedFileCanBePreparedWithoutAReceiverAndIsSentOnlyByExplicitAction() {
        Fixture(autoSelectPeer = false).use { fixture ->
            val file = temporary.newFile("arrastrado ñ.txt").apply { writeText("Original intacto") }
            onEdt {
                fixture.controller.updateTextDraft(PREPARED_DRAFT)
                assertTrue(fixture.controller.chooseDroppedFiles(listOf(file)))
                fixture.controller.send()
            }
            assertNull(fixture.state().selectedPeer)
            assertEquals(listOf(file), fixture.state().selectedFiles)
            assertEquals(PREPARED_DRAFT, fixture.state().textDraft)
            assertTrue(fixture.transport.batches.isEmpty())
            onEdt { fixture.controller.choosePeer(fixture.transport.peer) }
            assertTrue(fixture.transport.batches.isEmpty())
            onEdt { fixture.controller.send() }
            fixture.await { !sendPending && transfers.isNotEmpty() }
            assertEquals(listOf(listOf(file)), fixture.transport.batches.toList())
            assertEquals(PREPARED_DRAFT, fixture.state().textDraft)
            assertEquals("Original intacto", file.readText())
        }
    }

    @Test
    fun droppedFilesAppendAndDeduplicateWhilePreservingTheDraftAndOriginalFiles() {
        Fixture(autoSelectPeer = false).use { fixture ->
            val existing = fixture.prepareDraftAndFile()
            val first = temporary.newFile("uno.txt").apply { writeText("Uno") }
            val second = temporary.newFile("dos.txt").apply { writeText("Dos") }
            val directory = temporary.newFolder("carpeta-no-enviable")
            onEdt {
                assertTrue(fixture.controller.chooseDroppedFiles(listOf(first, directory, second, existing, first)))
            }
            assertEquals(listOf(existing, first, second), fixture.state().selectedFiles)
            assertEquals(PREPARED_DRAFT, fixture.state().textDraft)
            assertEquals("Uno", first.readText())
            assertEquals("Dos", second.readText())
            assertTrue(fixture.state().statusText.resolve(java.util.Locale.forLanguageTag("es")).contains("se omitieron 1"))
            assertTrue(fixture.transport.batches.isEmpty())
        }
    }

    @Test
    fun droppingBeforeActivationRetainsFilesWhenFlashStarts() {
        Fixture(autoStart = false).use { fixture ->
            val file = temporary.newFile()
            onEdt {
                assertTrue(fixture.controller.state.canAttachFiles)
                assertTrue(fixture.controller.chooseDroppedFiles(listOf(file)))
                fixture.controller.start("Test")
            }
            fixture.await { session.active && session.peers.isNotEmpty() }
            assertEquals(listOf(file), fixture.state().selectedFiles)
            assertNull(fixture.state().selectedPeer)
            assertTrue(fixture.transport.batches.isEmpty())
        }
    }

    @Test
    fun invalidOrUnreadableDropsKeepPreparedContentAndReportFailure() {
        Fixture().use { fixture ->
            val existing = fixture.prepareDraftAndFile()
            val directory = temporary.newFolder()
            val missing = File(temporary.root, "missing.txt")
            for (files in listOf(emptyList(), listOf(directory), listOf(missing))) {
                onEdt { assertFalse(fixture.controller.chooseDroppedFiles(files)) }
                assertTrue(fixture.state().error)
                fixture.assertPreparedContent(existing)
            }
            onEdt { fixture.controller.fileDropFailed() }
            assertTrue(fixture.state().error)
            assertTrue(fixture.state().statusText.resolve(java.util.Locale.forLanguageTag("es")).contains("usa Adjuntar"))
            fixture.assertPreparedContent(existing)
        }
    }

    @Test
    fun busyTransferRejectsDropsWithoutChangingPreparedContentOrSubmittingAgain() {
        Fixture().use { fixture ->
            val existing = fixture.prepareDraftAndFile()
            val extra = temporary.newFile("no-reemplazar.txt")
            onEdt { fixture.controller.send() }
            fixture.await { !sendPending && transfers.isNotEmpty() }
            val before = fixture.state()
            onEdt {
                assertFalse(fixture.controller.state.canAttachFiles)
                assertFalse(fixture.controller.chooseDroppedFiles(listOf(extra)))
            }
            assertEquals(before, fixture.state())
            assertEquals(listOf(existing), fixture.state().selectedFiles)
            assertEquals(PREPARED_DRAFT, fixture.state().textDraft)
            assertEquals(listOf(listOf(existing)), fixture.transport.batches.toList())
        }
    }

    @Test
    fun aDroppedFileRemovedBeforeSendingDoesNotDiscardTheDraftOrStartATransfer() {
        Fixture().use { fixture ->
            val file = temporary.newFile("removed.txt")
            onEdt {
                fixture.controller.updateTextDraft(PREPARED_DRAFT)
                assertTrue(fixture.controller.chooseDroppedFiles(listOf(file)))
            }
            assertTrue(file.delete())
            onEdt { fixture.controller.send() }
            assertEquals(listOf(file), fixture.state().selectedFiles)
            assertEquals(PREPARED_DRAFT, fixture.state().textDraft)
            assertTrue(fixture.state().error)
            assertFalse(fixture.state().busy)
            assertTrue(fixture.transport.batches.isEmpty())
        }
    }

    @Test
    fun activationFindsReceiversWithoutAManualSearch() {
        Fixture().use { fixture ->
            assertEquals(1, fixture.transport.discoverCalls.get())
            assertEquals(listOf(fixture.transport.peer), fixture.state().session.peers)
        }
    }

    @Test
    fun removingOneAttachmentKeepsDraftReceiverOtherAttachmentsAndOriginalFiles() {
        Fixture().use { fixture ->
            val first = fixture.prepareDraftAndFile()
            val other = temporary.newFile("keep.txt").apply { writeText("Keep this file") }
            onEdt {
                fixture.controller.chooseFile(other)
                fixture.controller.removeFile(first)
                fixture.controller.removeFile(first) // A stale row must not remove a different file.
            }
            assertEquals(listOf(other), fixture.state().selectedFiles)
            assertEquals(PREPARED_DRAFT, fixture.state().textDraft)
            assertEquals(fixture.transport.peer, fixture.state().selectedPeer)
            assertTrue(first.isFile)
            assertEquals("Keep this file", other.readText())
            assertTrue(fixture.transport.batches.isEmpty())
        }
    }

    @Test
    fun removingAnAttachmentDuringTransferCannotMutateTheApprovedSelection() {
        Fixture().use { fixture ->
            val file = fixture.prepareDraftAndFile()
            onEdt { fixture.controller.send() }
            fixture.await { !sendPending && transfers.isNotEmpty() }
            val before = fixture.state()
            onEdt { fixture.controller.removeFile(file) }
            assertEquals(before, fixture.state())
            assertTrue(file.isFile)
        }
    }

    @Test
    fun manualSearchCanFindReceiversAgainAfterActivation() {
        Fixture().use { fixture ->
            fixture.transport.listener.onState(FlashState(active = true))
            fixture.await { session.peers.isEmpty() }
            onEdt { fixture.controller.discover() }
            fixture.await { session.peers == listOf(fixture.transport.peer) }
            assertEquals(2, fixture.transport.discoverCalls.get())
            assertEquals(1, fixture.transport.startCalls.get())
        }
    }

    @Test
    fun choosingTheSameOrAnotherReceiverPreservesPreparedContentWithoutSending() {
        Fixture().use { fixture ->
            val file = fixture.prepareDraftAndFile()
            val other = fixture.transport.peer.copy(id = "other-peer", label = "Other receiver", address = "127.0.0.2")
            fixture.transport.listener.onState(FlashState(active = true, peers = listOf(fixture.transport.peer, other)))
            fixture.await { session.peers.size == 2 }

            for (receiver in listOf(fixture.transport.peer, other, fixture.transport.peer)) {
                onEdt { fixture.controller.choosePeer(receiver) }
                assertEquals(receiver, fixture.state().selectedPeer)
                fixture.assertPreparedContent(file)
            }
        }
    }

    @Test
    fun refreshAndTemporaryReceiverLossPreserveContentWithoutFallingBackToAnotherReceiver() {
        Fixture().use { fixture ->
            val file = fixture.prepareDraftAndFile()
            val peer = fixture.transport.peer
            val other = peer.copy(id = "other-peer", address = "127.0.0.2")
            val refreshed = peer.copy(label = "Updated label", expiresAtMs = Long.MAX_VALUE - 1)
            fixture.transport.listener.onState(FlashState(active = true, peers = listOf(other, refreshed)))
            fixture.await { selectedPeer == refreshed }
            fixture.assertPreparedContent(file)

            fixture.transport.listener.onState(FlashState(active = true, peers = listOf(other)))
            fixture.await { session.peers == listOf(other) }
            assertNull(fixture.state().selectedPeer)
            onEdt { fixture.controller.sendText(); fixture.controller.send() }
            assertFalse(fixture.state().busy)
            fixture.assertPreparedContent(file)

            onEdt { fixture.controller.discover() }
            fixture.await { selectedPeer == peer }
            fixture.assertPreparedContent(file)
        }
    }

    @Test
    fun changedIdentityOrEndpointRequiresExplicitSelectionBeforeSendingPreparedContent() {
        for (change in listOf<(FlashPeer) -> FlashPeer>(
            { it.copy(id = "replacement-peer") },
            { it.copy(address = "127.0.0.2") },
            { it.copy(port = 8990) }
        )) {
            Fixture().use { fixture ->
                val file = fixture.prepareDraftAndFile()
                val old = fixture.transport.peer
                val changed = change(old)
                fixture.transport.listener.onState(FlashState(active = true, peers = listOf(changed)))
                fixture.await { session.peers == listOf(changed) }
                assertNull(fixture.state().selectedPeer)
                onEdt {
                    fixture.controller.choosePeer(old)
                    fixture.controller.sendText()
                    fixture.controller.send()
                }
                assertNull(fixture.state().selectedPeer)
                assertFalse(fixture.state().busy)
                fixture.assertPreparedContent(file)

                onEdt { fixture.controller.choosePeer(changed) }
                assertEquals(changed, fixture.state().selectedPeer)
                fixture.assertPreparedContent(file)
            }
        }
    }

    @Test
    fun expiredReceiverCannotBeChosenOrReceivePreparedContent() {
        Fixture().use { fixture ->
            val file = fixture.prepareDraftAndFile()
            val expired = fixture.transport.peer.copy(id = "expired-peer", expiresAtMs = 0)
            fixture.transport.listener.onState(FlashState(active = true, peers = listOf(expired)))
            fixture.await { session.peers == listOf(expired) }
            onEdt {
                fixture.controller.choosePeer(expired)
                fixture.controller.sendText()
                fixture.controller.send()
            }
            assertNull(fixture.state().selectedPeer)
            assertFalse(fixture.state().busy)
            fixture.assertPreparedContent(file)
        }
    }

    @Test
    fun stoppingOrExpiringTheSessionPreservesContentAndRequiresReceiverSelectionAfterReactivation() {
        for (expires in listOf(false, true)) {
            Fixture().use { fixture ->
                val file = fixture.prepareDraftAndFile()
                if (expires) {
                    fixture.transport.listener.onState(FlashState())
                    fixture.await { !session.active }
                } else onEdt { fixture.controller.stop() }
                assertNull(fixture.state().selectedPeer)
                fixture.assertPreparedContent(file)

                onEdt { fixture.controller.start("Test") }
                fixture.await { session.active && session.peers.isNotEmpty() }
                assertNull(fixture.state().selectedPeer)
                fixture.assertPreparedContent(file)
                onEdt { fixture.controller.choosePeer(fixture.transport.peer) }
                assertEquals(fixture.transport.peer, fixture.state().selectedPeer)
                fixture.assertPreparedContent(file)
            }
        }
    }

    @Test
    fun startingAnActiveSessionDoesNotRepeatTheInitialSearch() {
        Fixture().use { fixture ->
            onEdt { fixture.controller.start("Test") }
            assertEquals(1, fixture.transport.startCalls.get())
            assertEquals(1, fixture.transport.discoverCalls.get())
        }
    }

    @Test
    fun stoppingDuringStartupPreventsTheAutomaticSearch() {
        val enteredStart = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        Fixture(autoStart = false) { transport ->
            transport.onStart = {
                enteredStart.countDown()
                assertTrue(releaseStart.await(5, TimeUnit.SECONDS))
            }
        }.use { fixture ->
            try {
                onEdt { fixture.controller.start("Test") }
                assertTrue(enteredStart.await(5, TimeUnit.SECONDS))
                onEdt { fixture.controller.stop() }
                releaseStart.countDown()
                fixture.await { fixture.transport.stopCalls.get() > 0 }
                assertFalse(fixture.state().session.active)
                assertFalse(fixture.state().starting)
                assertEquals(0, fixture.transport.discoverCalls.get())
            } finally {
                releaseStart.countDown()
            }
        }
    }

    @Test
    fun completionBeforeSendReturnsKeepsEveryReceiptAndUsesOneSubmission() {
        Fixture().use { fixture ->
            val first = temporary.newFile("first.txt")
            val second = temporary.newFile("second.txt")
            val releaseReturn = CountDownLatch(1)
            fixture.transport.onSend = { id, file ->
                fixture.transport.announce(id, file)
                fixture.transport.complete(id, file)
                assertTrue(releaseReturn.await(5, TimeUnit.SECONDS))
            }
            try {
                fixture.send(first, second)
                fixture.await { selectedFiles == listOf(second) && sendPending }
                fixture.await { transfers.any { it.id == "out-2" && it.file == second } }
                fixture.transport.complete("out-2", second)
                fixture.transport.empty()
                fixture.await { !busy }
                assertTrue(fixture.state().selectedFiles.isEmpty())
                assertEquals(listOf(first, second), fixture.transport.sent.toList())
                assertEquals(listOf(listOf(first, second)), fixture.transport.batches.toList())
                releaseReturn.countDown()
                onEdt { }
                assertFalse(fixture.state().busy)
            } finally {
                releaseReturn.countDown()
            }
        }
    }

    @Test
    fun cancellationStopsTheBatchEvenWhenTheReceiptWinsTheRace() {
        Fixture().use { fixture ->
            val first = temporary.newFile("first.txt")
            val second = temporary.newFile("second.txt")
            fixture.send(first, second)
            fixture.await { transfers.any { it.id == "out-1" && it.file == first } }
            onEdt { fixture.controller.cancel("out-1") }
            fixture.await { fixture.transport.cancelCalls.get() == 1 }
            fixture.transport.complete("out-1", first)
            fixture.transport.empty()
            fixture.await { !busy }
            assertEquals(listOf(second), fixture.state().selectedFiles)
            assertEquals(listOf(first), fixture.transport.sent.toList())
            assertFalse(fixture.state().batchSending)
        }
    }

    @Test
    fun failurePreservesOnlyUnconfirmedFilesForAnExplicitRetry() {
        Fixture().use { fixture ->
            val first = temporary.newFile("first.txt")
            val second = temporary.newFile("second.txt")
            val third = temporary.newFile("third.txt")
            fixture.send(first, second, third)
            fixture.await { transfers.any { it.id == "out-1" && it.file == first } }
            fixture.transport.complete("out-1", first)
            fixture.await { transfers.any { it.id == "out-2" && it.file == second } }
            fixture.transport.fail("out-2")
            fixture.transport.empty()
            fixture.await { !busy }
            assertEquals(listOf(second, third), fixture.state().selectedFiles)
            assertEquals(listOf(first, second), fixture.transport.sent.toList())

            onEdt { fixture.controller.send() }
            fixture.await { transfers.any { it.id == "out-4" && it.file == second } }
            // A late error from the previous attempt must not tear down the retry.
            fixture.transport.fail("out-2")
            onEdt { }
            assertTrue(fixture.state().batchSending)
            fixture.transport.complete("out-4", second)
            fixture.await { transfers.any { it.id == "out-5" && it.file == third } }
            fixture.transport.complete("out-5", third)
            fixture.transport.empty()
            fixture.await { !busy }
            assertTrue(fixture.state().selectedFiles.isEmpty())
            assertEquals(listOf(first, second, second, third), fixture.transport.sent.toList())
        }
    }

    @Test
    fun missingSelectedFileDoesNotSilentlySendAPartialBatch() {
        Fixture().use { fixture ->
            val first = temporary.newFile("first.txt")
            val missing = temporary.newFile("missing.txt")
            onEdt { fixture.controller.chooseFiles(listOf(first, missing)) }
            assertTrue(missing.delete())
            onEdt { fixture.controller.send() }
            assertTrue(fixture.state().error)
            assertFalse(fixture.state().busy)
            assertEquals(listOf(first, missing), fixture.state().selectedFiles)
            assertTrue(fixture.transport.sent.isEmpty())
        }
    }

    @Test
    fun unrelatedCallbacksCannotClaimOrReleaseAReservedOutgoingFile() {
        Fixture().use { fixture ->
            val first = temporary.newFile("first.txt")
            val second = temporary.newFile("second.txt")
            val releaseSend = CountDownLatch(1)
            fixture.transport.onSend = { id, file ->
                fixture.transport.announce("incoming", File("incoming.txt"), outgoing = false)
                fixture.transport.complete("incoming", File("incoming.txt"), outgoing = false)
                fixture.transport.fail(null)
                fixture.transport.empty()
                assertTrue(releaseSend.await(5, TimeUnit.SECONDS))
                fixture.transport.announce(id, file)
            }
            try {
                fixture.send(first, second)
                fixture.await { transfers.any { it.id == "incoming" && it.phase == DesktopFlashPhase.COMPLETE } }
                assertNull(fixture.state().transfers.single { it.id == "incoming" }.file)
                assertTrue(fixture.state().sendPending)
                assertEquals(listOf(first, second), fixture.state().selectedFiles)
                assertEquals(listOf(first), fixture.transport.sent.toList())
            } finally {
                releaseSend.countDown()
            }
        }
    }

    @Test
    fun throwingAfterPublishingAnOperationDoesNotLeaveABusyTransfer() {
        Fixture().use { fixture ->
            val first = temporary.newFile("first.txt")
            val second = temporary.newFile("second.txt")
            fixture.transport.onSend = { id, file ->
                fixture.transport.announce(id, file)
                fixture.transport.empty()
                throw IllegalStateException("worker unavailable")
            }
            fixture.send(first, second)
            fixture.await { error && !busy }
            assertEquals(listOf(first, second), fixture.state().selectedFiles)
            assertEquals(DesktopFlashPhase.FAILED, fixture.state().transfers.single().phase)
        }
    }

    @Test
    fun clipboardIsExplicitAndPastePreservesTextAroundTheSelection() {
        val clipboard = RecordingClipboard("  café\r\n🙂  ")
        Fixture(clipboard = clipboard).use { fixture ->
            assertEquals(0, clipboard.reads)
            assertTrue(clipboard.writes.isEmpty())
            onEdt {
                fixture.controller.updateTextDraft("antes [sustituir] después")
                fixture.controller.pasteText(6, 17)
            }
            val expected = "antes   café\r\n🙂   después"
            assertEquals(expected, fixture.state().textDraft)
            assertEquals(1, clipboard.reads)
            clipboard.text = "x".repeat(FLASH_TEXT_MAX_BYTES + 1)
            onEdt { fixture.controller.pasteText() }
            assertEquals(expected, fixture.state().textDraft)
            assertTrue(fixture.state().error)
            assertTrue(clipboard.writes.isEmpty())
        }
    }

    @Test
    fun textUsesOneOrdinaryTransferKeepsFilesAndClearsDraftOnlyAfterAcknowledgement() {
        val clipboard = RecordingClipboard()
        Fixture(clipboard = clipboard).use { fixture ->
            val selectedFile = temporary.newFile("selected.txt")
            val body = "  café\r\n🙂\n  "
            onEdt {
                fixture.controller.chooseFiles(listOf(selectedFile))
                fixture.controller.updateTextDraft(body)
                fixture.controller.sendText()
            }
            fixture.await { transfers.any { it.textBody == body } && !sendPending }
            val staged = fixture.transport.sent.single()
            assertTrue(FlashText.isTextFileName(staged.name))
            assertEquals(body, staged.readText(Charsets.UTF_8))
            assertEquals(listOf(listOf(staged)), fixture.transport.batches.toList())
            assertEquals(listOf(selectedFile), fixture.state().selectedFiles)
            assertEquals(body, fixture.state().textDraft)
            assertEquals(0, clipboard.reads)
            assertTrue(clipboard.writes.isEmpty())
            onEdt { fixture.controller.copyText("out-1") }
            assertEquals(listOf(body), clipboard.writes)
            fixture.transport.complete("out-1", staged)
            fixture.transport.empty()
            fixture.await { !busy && textDraft.isEmpty() && !staged.exists() }
            assertEquals(body, fixture.state().transfers.single().textBody)
            assertEquals(listOf(selectedFile), fixture.state().selectedFiles)
            assertFalse(fixture.state().status.contains(body))
        }
    }

    @Test
    fun failedTextKeepsItsDraftAndDeletesTheTemporaryAttachment() {
        Fixture().use { fixture ->
            fixture.sendText("Reintentar explícitamente")
            fixture.await { transfers.isNotEmpty() && !sendPending }
            val staged = fixture.transport.sent.single()
            fixture.transport.fail("out-1")
            fixture.transport.empty()
            fixture.await { !busy && !staged.exists() }
            assertEquals("Reintentar explícitamente", fixture.state().textDraft)
            assertEquals(DesktopFlashPhase.FAILED, fixture.state().transfers.single().phase)
            assertEquals(1, fixture.transport.batches.size)
        }
    }

    @Test
    fun cancelledTextKeepsItsDraftAndDeletesTheTemporaryAttachment() {
        Fixture().use { fixture ->
            fixture.sendText("Cancelar este mensaje")
            fixture.await { transfers.isNotEmpty() && !sendPending }
            val staged = fixture.transport.sent.single()
            onEdt { fixture.controller.cancel("out-1") }
            fixture.await { fixture.transport.cancelCalls.get() == 1 }
            fixture.transport.listener.onError(FlashError("out-1", "cancelled", "Transferencia cancelada."))
            fixture.transport.empty()
            fixture.await { !busy && !staged.exists() }
            assertEquals("Cancelar este mensaje", fixture.state().textDraft)
            assertEquals(DesktopFlashPhase.CANCELLED, fixture.state().transfers.single().phase)
            assertEquals(1, fixture.transport.batches.size)
        }
    }

    @Test
    fun textAcknowledgementDoesNotClearANewerDraft() {
        Fixture().use { fixture ->
            fixture.sendText("Primer texto")
            fixture.await { transfers.isNotEmpty() && !sendPending }
            onEdt { fixture.controller.updateTextDraft("Nuevo borrador") }
            fixture.transport.complete("out-1", fixture.transport.sent.single())
            fixture.transport.empty()
            fixture.await { !busy }
            assertEquals("Nuevo borrador", fixture.state().textDraft)
        }
    }

    @Test
    fun textCompletionBeforeSendReturnsStillClearsTheMatchingDraftAndCleansUp() {
        val releaseReturn = CountDownLatch(1)
        Fixture { transport ->
            transport.onSend = { id, file ->
                transport.announce(id, file)
                transport.complete(id, file)
                assertTrue(releaseReturn.await(5, TimeUnit.SECONDS))
            }
        }.use { fixture ->
            try {
                fixture.sendText("Entrega rápida")
                fixture.await { textDraft.isEmpty() && transfers.any { it.phase == DesktopFlashPhase.COMPLETE } }
                val staged = fixture.transport.sent.single()
                releaseReturn.countDown()
                fixture.transport.empty()
                fixture.await { !busy && !staged.exists() }
                assertEquals(1, fixture.transport.batches.size)
            } finally {
                releaseReturn.countDown()
            }
        }
    }

    @Test
    fun stoppingOrClosingTextDeletesItsTemporaryAttachmentAndRetainsTheDraft() {
        for (close in listOf(false, true)) {
            Fixture().use { fixture ->
                fixture.sendText("Sin confirmar")
                fixture.await { transfers.isNotEmpty() && !sendPending }
                val staged = fixture.transport.sent.single()
                onEdt { if (close) fixture.controller.close() else fixture.controller.stop() }
                fixture.await { !staged.exists() }
                assertEquals("Sin confirmar", fixture.state().textDraft)
            }
        }
    }

    @Test
    fun verifiedReceivedTextCanBeCopiedExactlyWithoutAutomaticClipboardWrites() {
        val clipboard = RecordingClipboard()
        Fixture(clipboard = clipboard).use { fixture ->
            val body = "  recibido\r\n👋🏽  "
            val received = FlashText.createFile(temporary.newFolder("received-text"), body)
            fixture.transport.announce("incoming-text", received, outgoing = false)
            fixture.transport.listener.onReceived(FlashReceived("incoming-text", fixture.transport.peer, received))
            fixture.transport.complete("incoming-text", received, outgoing = false)
            fixture.transport.empty()
            fixture.await { transfers.any { it.textBody == body && it.phase == DesktopFlashPhase.COMPLETE } }
            assertTrue(clipboard.writes.isEmpty())
            assertEquals(0, clipboard.reads)
            onEdt { fixture.controller.copyText("incoming-text") }
            assertEquals(listOf(body), clipboard.writes)
            val firstCopy = fixture.state()
            onEdt { fixture.controller.copyText("incoming-text") }
            assertEquals(listOf(body, body), clipboard.writes)
            assertEquals(firstCopy.statusText, fixture.state().statusText)
            assertEquals(firstCopy.statusRevision + 1, fixture.state().statusRevision)
            assertTrue(received.isFile)
            assertFalse(fixture.state().status.contains(body))
        }
    }

    @Test
    fun ordinaryTextFileKeepsNormalFilePresentation() {
        Fixture().use { fixture ->
            val received = temporary.newFile("notes.txt").apply { writeText("Ordinary file") }
            fixture.transport.announce("incoming-file", received, outgoing = false)
            fixture.transport.listener.onReceived(FlashReceived("incoming-file", fixture.transport.peer, received))
            fixture.await { transfers.any { it.id == "incoming-file" && it.phase == DesktopFlashPhase.COMPLETE } }
            assertNull(fixture.state().transfers.single().textBody)
            assertEquals(received, fixture.state().transfers.single().file)
        }
    }

    private inner class Fixture(
        autoStart: Boolean = true,
        autoSelectPeer: Boolean = true,
        clipboard: DesktopFlashClipboard = RecordingClipboard(),
        configureTransport: (FakeTransport) -> Unit = {}
    ) : AutoCloseable {
        lateinit var transport: FakeTransport
        val controller = onEdt {
            DesktopFlashController(temporary.root, clipboard) { _, _, listener ->
                FakeTransport(listener).also { transport = it; configureTransport(it) }
            }
        }

        init {
            if (autoStart) {
                onEdt { controller.start("Test") }
                await { session.active && session.peers.isNotEmpty() }
                if (autoSelectPeer) onEdt { controller.choosePeer(transport.peer) }
            }
        }

        fun send(vararg files: File) = onEdt {
            controller.chooseFiles(files.toList())
            controller.send()
        }

        fun sendText(text: String) = onEdt {
            controller.updateTextDraft(text)
            controller.sendText()
        }

        fun state() = onEdt { controller.state }

        fun prepareDraftAndFile(): File = temporary.newFile().apply {
            writeText("Adjunto que debe conservarse.\n")
            onEdt {
                controller.chooseFile(this)
                controller.updateTextDraft(PREPARED_DRAFT)
            }
        }

        fun assertPreparedContent(file: File) {
            val current = state()
            assertEquals(PREPARED_DRAFT, current.textDraft)
            assertEquals(listOf(file), current.selectedFiles)
            assertEquals("Adjunto que debe conservarse.\n", file.readText())
            assertTrue(transport.batches.isEmpty(), "Receiver transitions must not submit prepared content")
        }

        fun await(predicate: DesktopFlashUiState.() -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline) {
                if (state().predicate()) return
                Thread.sleep(5)
            }
            assertTrue(state().predicate(), "Controller did not reach expected state: ${state()}")
        }

        override fun close() = onEdt { controller.close() }
    }

    private class RecordingClipboard(var text: String? = null) : DesktopFlashClipboard {
        var reads = 0
        val writes = mutableListOf<String>()
        override fun readText(): String? { reads++; return text }
        override fun writeText(text: String) { writes += text }
    }

    private class FakeTransport(val listener: FlashListener) : DesktopFlashTransport {
        val peer = FlashPeer("peer", "Test peer", "127.0.0.1", 8989, Long.MAX_VALUE)
        val sent = CopyOnWriteArrayList<File>()
        val batches = CopyOnWriteArrayList<List<File>>()
        private data class Batch(val files: List<File>, val ids: List<String>, var cancelled: Boolean = false)
        private val operationBatches = java.util.concurrent.ConcurrentHashMap<String, Batch>()
        private val sequence = AtomicInteger()
        val startCalls = AtomicInteger()
        val stopCalls = AtomicInteger()
        val discoverCalls = AtomicInteger()
        val cancelCalls = AtomicInteger()
        var onStart: (() -> Unit)? = null
        var onSend: ((String, File) -> Unit)? = null

        override fun start() {
            startCalls.incrementAndGet()
            onStart?.invoke()
            listener.onState(FlashState(active = true))
        }
        override fun stop() { stopCalls.incrementAndGet() }
        override fun sendBatch(files: List<File>, peer: FlashPeer): String {
            batches += files.toList()
            val batch = Batch(files, files.map { "out-${sequence.incrementAndGet()}" })
            batch.ids.forEach { operationBatches[it] = batch }
            val file = files.first()
            sent += file
            val id = batch.ids.first()
            onSend?.invoke(id, file) ?: announce(id, file)
            return id
        }

        fun announce(id: String, file: File, outgoing: Boolean = true) {
            val batch = operationBatches[id]
            listener.onState(FlashState(active = true, peers = listOf(peer), operations = listOf(
                FlashOperation(id, peer, file.name, file.length(), outgoing,
                    batchId = batch?.ids?.first() ?: id, fileIndex = batch?.ids?.indexOf(id) ?: 0,
                    fileCount = batch?.files?.size ?: 1))))
        }

        fun empty() = listener.onState(FlashState(active = true, peers = listOf(peer)))
        fun complete(id: String, file: File, outgoing: Boolean = true) {
            listener.onCompleted(FlashCompleted(id, peer, file.name, outgoing))
            val batch = operationBatches[id] ?: return
            val next = batch.ids.indexOf(id) + 1
            if (outgoing && !batch.cancelled && next < batch.files.size) {
                sent += batch.files[next]
                announce(batch.ids[next], batch.files[next])
            }
        }
        fun fail(id: String?) = listener.onError(FlashError(id, "connection_failed", "Connection failed"))
        override fun approve(requestId: String, accepted: Boolean) = true
        override fun cancel(operationId: String): Boolean {
            operationBatches[operationId]?.cancelled = true
            cancelCalls.incrementAndGet()
            return true
        }
        override fun discover() {
            discoverCalls.incrementAndGet()
            empty()
        }
        override fun discoverAt(address: String, port: Int) = Unit
    }

    private companion object {
        const val PREPARED_DRAFT = "  Borrador exacto: ñ, 你好 👋\r\nSegunda línea.  "

        fun <T> onEdt(action: () -> T): T {
            if (SwingUtilities.isEventDispatchThread()) return action()
            var result: Result<T>? = null
            SwingUtilities.invokeAndWait { result = runCatching(action) }
            return result!!.getOrThrow()
        }
    }
}
