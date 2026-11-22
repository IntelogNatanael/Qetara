@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.example.wifidrop.pc

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.platform.AwtDragAndDropManager
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import javax.swing.JPanel
import javax.swing.SwingUtilities
import org.jetbrains.skia.Surface
import org.junit.Assume.assumeFalse
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercise Compose 1.8.2's actual AWT bridge, not a direct onDrop callback. The narrow internal API
 * opt-in lets us use an offscreen scene: no visible window, OS input, clipboard, sockets or Robot.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DesktopFileDropTargetTest {
    @Test
    fun firstDropOverTextFieldWithoutAnotherMoveReachesFileTargetAndForcesCopy() = onEdt {
        Fixture().use { fixture ->
            val files = listOf(File("first.txt"), File("second.txt"))
            val transfer = fileTransfer(files)
            fixture.enter(transfer)
            // A native drag may enter at its final position and drop without any dragOver event.
            val drop = fixture.drop(transfer)
            assertEquals(true, drop.completed)
            assertEquals(DnDConstants.ACTION_COPY, drop.acceptedAction)
            assertEquals(listOf(files), fixture.deliveries)
            assertEquals(Fixture.DRAFT, fixture.draft)
            assertFalse(fixture.dragging)
        }
    }

    @Test
    fun normalMoveThenDropUsesTheSameHandlerExactlyOnce() = onEdt {
        Fixture().use { fixture ->
            val files = listOf(File("single.txt"))
            val transfer = fileTransfer(files)
            fixture.enter(transfer)
            fixture.move(transfer)
            assertTrue(fixture.dragging)
            val drop = fixture.drop(transfer)
            assertEquals(true, drop.completed)
            assertEquals(listOf(files), fixture.deliveries)
            assertEquals(Fixture.DRAFT, fixture.draft)
        }
    }

    @Test
    fun unsupportedTextAndMoveOnlySourcesAreNotClaimedByTheFileTarget() = onEdt {
        Fixture().use { fixture ->
            assertTrue(fixture.enter(StringSelection("text")).rejected)
            assertTrue(fixture.enter(fileTransfer(listOf(File("only-move.txt"))), DnDConstants.ACTION_MOVE).rejected)
            assertTrue(fixture.deliveries.isEmpty())
            assertFalse(fixture.dragging)
            assertEquals(Fixture.DRAFT, fixture.draft)
        }
    }

    @Test
    fun becomingBusyAfterDragEnterRejectsDropAndClearsFeedback() = onEdt {
        Fixture().use { fixture ->
            val transfer = fileTransfer(listOf(File("busy.txt")))
            fixture.enter(transfer)
            fixture.enabled = false
            fixture.render()
            assertEquals(false, fixture.drop(transfer).completed)
            assertTrue(fixture.deliveries.isEmpty())
            assertFalse(fixture.dragging)
            assertEquals(Fixture.DRAFT, fixture.draft)
        }
    }

    @Test
    fun unreadableNativeTransferReportsFailureWithoutChangingTheDraft() = onEdt {
        Fixture().use { fixture ->
            val transfer = object : Transferable {
                override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
                override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.javaFileListFlavor
                override fun getTransferData(flavor: DataFlavor): Any = throw java.io.IOException("No native payload")
            }
            fixture.enter(transfer)
            assertEquals(false, fixture.drop(transfer).completed)
            assertEquals(1, fixture.failures.size)
            assertTrue(fixture.deliveries.isEmpty())
            assertFalse(fixture.dragging)
            assertEquals(Fixture.DRAFT, fixture.draft)
        }
    }

    private class Fixture : AutoCloseable {
        var enabled by mutableStateOf(true)
        var draft by mutableStateOf(DRAFT)
        var dragging = false
        val deliveries = mutableListOf<List<File>>()
        val failures = mutableListOf<Exception>()
        private val scene = CanvasLayersComposeScene(density = Density(1f), size = IntSize(400, 300))
        private val surface = Surface.makeRasterN32Premul(400, 300)
        private val manager = AwtDragAndDropManager(object : JPanel() {
            override fun getGraphicsConfiguration() = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice.defaultConfiguration
        }.apply { setSize(400, 300) }) { scene.rootDragAndDropNode }

        init {
            scene.setContent {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().desktopFileDropTarget(enabled,
                        onDragActiveChange = { dragging = it },
                        onFilesDropped = { deliveries += it; true },
                        onFailure = { failures += it })) {
                        OutlinedTextField(draft, { draft = it }, Modifier.fillMaxSize())
                    }
                }
            }
            render()
        }

        fun render() { scene.render(surface.canvas.asComposeCanvas(), System.nanoTime()) }
        fun enter(transfer: Transferable, actions: Int = DnDConstants.ACTION_COPY_OR_MOVE): DragEvent =
            DragEvent(manager.dropTarget, transfer, actions).also { manager.dropTarget.dragEnter(it) }
        fun move(transfer: Transferable) { manager.dropTarget.dragOver(DragEvent(manager.dropTarget, transfer)) }
        fun drop(transfer: Transferable): DropEvent =
            DropEvent(manager.dropTarget, transfer).also { manager.dropTarget.drop(it) }
        override fun close() { manager.dropTarget.component = null; scene.close(); surface.close() }

        companion object { const val DRAFT = "Borrador exacto: ñ, 你好 👋\nSegunda línea." }
    }

    private class DragEvent(target: DropTarget, val payload: Transferable,
        actions: Int = DnDConstants.ACTION_COPY_OR_MOVE) :
        DropTargetDragEvent(target.dropTargetContext, Point(100, 75), DnDConstants.ACTION_MOVE, actions) {
        var rejected = false
        override fun getTransferable() = payload
        override fun getCurrentDataFlavors() = payload.transferDataFlavors
        override fun isDataFlavorSupported(flavor: DataFlavor) = payload.isDataFlavorSupported(flavor)
        override fun acceptDrag(action: Int) { rejected = false }
        override fun rejectDrag() { rejected = true }
    }

    private class DropEvent(target: DropTarget, val payload: Transferable) :
        DropTargetDropEvent(target.dropTargetContext, Point(100, 75), DnDConstants.ACTION_MOVE, DnDConstants.ACTION_COPY_OR_MOVE) {
        var completed: Boolean? = null
        var acceptedAction: Int? = null
        override fun getTransferable() = payload
        override fun getCurrentDataFlavors() = payload.transferDataFlavors
        override fun isDataFlavorSupported(flavor: DataFlavor) = payload.isDataFlavorSupported(flavor)
        override fun acceptDrop(action: Int) { acceptedAction = action }
        override fun dropComplete(success: Boolean) { completed = success }
    }

    private companion object {
        fun fileTransfer(files: List<File>) = object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.javaFileListFlavor
            override fun getTransferData(flavor: DataFlavor): Any = files
        }
        fun onEdt(action: () -> Unit) {
            assumeFalse("AWT drag-and-drop requires a desktop toolkit", GraphicsEnvironment.isHeadless())
            var result: Result<Unit>? = null
            SwingUtilities.invokeAndWait { result = runCatching(action) }
            result!!.getOrThrow()
        }
    }
}
