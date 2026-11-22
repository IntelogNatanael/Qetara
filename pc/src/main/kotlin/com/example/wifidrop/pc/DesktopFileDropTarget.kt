package com.example.wifidrop.pc

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File

/** Register on the Compose surface: its native target receives drops before the outer JFrame. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.desktopFileDropTarget(
    enabled: Boolean,
    onDragActiveChange: (Boolean) -> Unit,
    onFilesDropped: (List<File>) -> Boolean,
    onFailure: (Exception) -> Unit
): Modifier {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentDragActiveChange by rememberUpdatedState(onDragActiveChange)
    val currentFilesDropped by rememberUpdatedState(onFilesDropped)
    val currentFailure by rememberUpdatedState(onFailure)
    val target = remember {
        object : DragAndDropTarget {
            private fun updateFeedback() = currentDragActiveChange(currentEnabled)
            override fun onStarted(event: DragAndDropEvent) {
                val native = event.nativeEvent as? DropTargetDragEvent ?: return
                // Compose 1.8.2 starts the session on dragEnter but only hit-tests on dragOver.
                // Windows can enter and drop at the same position without an intervening move.
                // The owner is already started here; feed its initial position through the same
                // native bridge so the first drop reaches our target too (no replacement target).
                native.dropTargetContext.dropTarget.dragOver(native)
            }
            override fun onEntered(event: DragAndDropEvent) = updateFeedback()
            override fun onMoved(event: DragAndDropEvent) = updateFeedback()
            override fun onExited(event: DragAndDropEvent) = currentDragActiveChange(false)
            override fun onEnded(event: DragAndDropEvent) = currentDragActiveChange(false)

            override fun onDrop(event: DragAndDropEvent): Boolean {
                currentDragActiveChange(false)
                if (!currentEnabled || !event.supportsFileCopy()) return false
                return try {
                    // Compose initially accepts the source's proposed action. Explicitly keep this
                    // a copy, including when Explorer proposes MOVE: attaching must retain originals.
                    (event.nativeEvent as? DropTargetDropEvent)?.acceptDrop(DnDConstants.ACTION_COPY)
                    val files = (event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                        .orEmpty().filterIsInstance<File>()
                    currentFilesDropped(files)
                } catch (error: Exception) {
                    currentFailure(error)
                    false
                }
            }
        }
    }
    DisposableEffect(enabled) {
        if (!enabled) currentDragActiveChange(false)
        onDispose { currentDragActiveChange(false) }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { currentEnabled && it.supportsFileCopy() },
        target = target
    )
}

@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.supportsFileCopy(): Boolean = when (val native = nativeEvent) {
    is DropTargetDragEvent -> native.sourceActions and DnDConstants.ACTION_COPY != 0 &&
        native.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
    is DropTargetDropEvent -> native.sourceActions and DnDConstants.ACTION_COPY != 0 &&
        native.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
    else -> false
}
