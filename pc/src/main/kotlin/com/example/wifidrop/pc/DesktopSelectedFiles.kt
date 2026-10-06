package com.example.wifidrop.pc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

/** A compact summary always provides access to every selected file and its full path. */
@Composable
internal fun DesktopSelectedFiles(files: List<File>, enabled: Boolean, onClear: () -> Unit) {
    if (files.isEmpty()) return
    var reviewing by remember { mutableStateOf(false) }
    if (reviewing) DesktopSelectedFilesDialog(files) { reviewing = false }
    Surface(color = qetaraCanvas, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, qetaraLine)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            files.take(3).forEachIndexed { index, file ->
                if (index > 0) Divider(color = qetaraLine)
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WorkspaceIcon(WorkspaceGlyph.FILE, color = qetaraTeal)
                    Text(file.name, Modifier.weight(1f), style = MaterialTheme.typography.body2, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatBytes(file.length()), style = MaterialTheme.typography.caption, color = qetaraMuted)
                }
            }
            if (files.size > 3) Text("+${files.size - 3} más", Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.caption, color = qetaraMuted)
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ reviewing = true }, modifier = Modifier.weight(1f).heightIn(min = 44.dp), contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(if (files.size == 1) "Revisar archivo" else "Revisar ${files.size} archivos", modifier = Modifier.fillMaxWidth())
        }
        TextButton(onClear, enabled = enabled, modifier = Modifier.heightIn(min = 44.dp)) { Text("Quitar todos") }
    }
}

@Composable
internal fun DesktopSelectedFilesDialog(files: List<File>, onDismiss: () -> Unit) {
    val scroll = rememberLazyListState()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 720.dp),
        shape = RoundedCornerShape(16.dp), backgroundColor = qetaraCanvasElevated, contentColor = qetaraInk,
        title = { Text(if (files.size == 1) "Archivo seleccionado" else "${files.size} archivos seleccionados", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Revisa los nombres completos y sus carpetas antes de enviar.", style = MaterialTheme.typography.body2, color = qetaraMuted)
                Box(Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                    LazyColumn(state = scroll, modifier = Modifier.fillMaxWidth().padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(files, key = { _, file -> file.absolutePath }) { index, file ->
                            if (index > 0) Divider(Modifier.padding(bottom = 12.dp), color = qetaraLine)
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(file.name, style = MaterialTheme.typography.body2, fontWeight = FontWeight.SemiBold)
                                    Text(file.absolutePath, style = MaterialTheme.typography.caption, color = qetaraMuted)
                                    Text(formatBytes(file.length()), style = MaterialTheme.typography.caption, color = qetaraMuted)
                                }
                            }
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        },
        confirmButton = { TextButton(onDismiss, modifier = Modifier.heightIn(min = 44.dp)) { Text("Cerrar") } }
    )
}
