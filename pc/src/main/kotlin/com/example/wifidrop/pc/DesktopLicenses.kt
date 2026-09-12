package com.example.wifidrop.pc

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun desktopLicenseNotices(): String =
    DesktopWorkspaceState::class.java.classLoader
        .getResourceAsStream("open_source_licenses/NOTICES.txt")
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: "No se encontró el archivo de licencias de esta instalación. Consulta LICENSE y los avisos de terceros que acompañan la distribución."

@Composable
internal fun DesktopLicensesDialog(onDismiss: () -> Unit) {
    val notices = remember { desktopLicenseNotices() }
    val scroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 720.dp),
        title = { Text("Licencias de código abierto") },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                SelectionContainer {
                    Text(
                        notices,
                        style = MaterialTheme.typography.body2,
                        color = qetaraInk,
                        modifier = Modifier.verticalScroll(scroll).padding(end = 16.dp)
                    )
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scroll),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
