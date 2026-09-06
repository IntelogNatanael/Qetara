package com.example.wifidrop

import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal fun credentialRequestFingerprint(request: PendingCredentialShareRequest): String? = request.noiseStaticKey?.let { key ->
    runCatching {
        val decodedKey = Base64.decode(key, Base64.DEFAULT)
        require(decodedKey.size == 32) { "Invalid device identity" }
        NoiseIdentityStore.fingerprintShort(decodedKey).chunked(4).joinToString(" ")
    }.getOrNull()
}

@Composable
internal fun QetaraCredentialRequestDialog(
    request: PendingCredentialShareRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit
) {
    val fingerprint = credentialRequestFingerprint(request)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Devices, contentDescription = null) },
        title = { Text("¿Conectar con este equipo?") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(request.label, fontWeight = FontWeight.Bold)
                        Text(request.ip, style = MaterialTheme.typography.bodySmall)
                        if (fingerprint != null) {
                            Text("Huella del equipo", style = MaterialTheme.typography.labelSmall)
                            SelectionContainer {
                                Text(fingerprint, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                Text("Al aprobar, este equipo podrá usar tu sesión para intercambiar archivos y mensajes contigo.")
                if (fingerprint != null) {
                    Text("Comprueba que la huella coincide con la que aparece en Ajustes o Acerca de Qetara del otro equipo.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("No podemos comprobar la identidad de este equipo. Actualiza Qetara en el otro equipo y vuelve a conectar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onApprove, enabled = fingerprint != null) { Text("Aprobar conexión") } },
        dismissButton = { TextButton(onClick = onReject) { Text("Rechazar") } }
    )
}
