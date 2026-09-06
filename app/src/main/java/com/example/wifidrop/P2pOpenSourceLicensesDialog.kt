package com.example.wifidrop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun QetaraOpenSourceLicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val notices by produceState<Result<String>?>(null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("open_source_licenses/NOTICES.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Licencias de código abierto") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    notices == null -> Text("Cargando licencias…")
                    notices?.isFailure == true -> Text("No se pudieron abrir las licencias incluidas en esta instalación.")
                    else -> SelectionContainer {
                        Text(notices?.getOrNull().orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Volver a Acerca de Qetara") } }
    )
}
