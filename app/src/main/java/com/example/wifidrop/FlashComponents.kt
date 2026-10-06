package com.example.wifidrop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.wifidrop.protocol.flash.FlashPeer

/** Presentation only: activation, discovery and exact-batch approval remain in the service. */
@Composable
internal fun FlashIntroduction(
    label: String,
    editingName: Boolean,
    onLabelChange: (String) -> Unit,
    onToggleName: () -> Unit,
    onActivate: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.padding(16.dp).size(32.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Comparte en un momento", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
            Text("Envía y recibe archivos con otro equipo en la misma Wi-Fi.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlashCard {
            FlashIntroStep("1", "Activa Flash en ambos equipos")
            FlashIntroStep("2", "Elige el equipo y los archivos")
            FlashIntroStep("3", "Compara el código y acepta el lote en ambos equipos")
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Este equipo", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (editingName) {
                OutlinedTextField(value = label, onValueChange = onLabelChange,
                    label = { Text("Nombre visible en Flash") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            } else {
                Text(label, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground)
            }
            TextButton(onClick = onToggleName, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (editingName) "Guardar nombre" else "Cambiar nombre")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onActivate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp)) { Text("Activar Flash", textAlign = TextAlign.Center) }
            Text("Disponible durante 30 minutos", modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FlashIntroStep(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(10.dp)) {
            Text(number, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelLarge)
        }
        Text(text, modifier = Modifier.weight(1f).padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlashSessionSummary(
    deviceLabel: String,
    remaining: String,
    transferring: Boolean,
    awaitingApproval: Boolean,
    showAddress: Boolean,
    addresses: List<String>,
    onToggleAddress: () -> Unit,
    onStop: () -> Unit
) {
    FlashCard {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Flash activo", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Surface(color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(12.dp)) {
                Text("$remaining restantes", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (awaitingApproval) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = RoundedCornerShape(10.dp)) {
                    Text("Verificación pendiente", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(if (transferring) "Transferencia en curso" else "Disponible para recibir",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(deviceLabel, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onToggleAddress, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (showAddress) "Ocultar dirección" else "Mi dirección")
            }
            TextButton(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp)) { Text("Apagar Flash") }
        }
        if (showAddress) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Dirección de este equipo", style = MaterialTheme.typography.labelLarge)
            Text(addresses.ifEmpty { listOf("No disponible") }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Flash sigue activo al volver a Qetara. Al apagarlo, los archivos recibidos se conservan.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun FlashSendBar(
    importing: Boolean,
    selectedPeer: FlashPeer?,
    selectedFileCount: Int,
    enabled: Boolean,
    onChoosePeer: () -> Unit,
    onChooseFiles: () -> Unit,
    onSend: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(when {
                importing -> "Preparando los archivos…"
                selectedPeer == null -> "Elige un equipo para continuar"
                selectedFileCount == 0 -> "Añade los archivos que quieres compartir"
                selectedFileCount == 1 -> "1 archivo preparado para enviar"
                else -> "$selectedFileCount archivos preparados para enviar"
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {
                when {
                    selectedPeer == null -> onChoosePeer()
                    selectedFileCount == 0 -> onChooseFiles()
                    else -> onSend()
                }
            }, enabled = enabled && !importing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) {
                Text(when {
                    importing -> "Preparando archivos…"
                    selectedPeer == null -> "Elegir receptor"
                    selectedFileCount == 0 -> "Elegir archivos"
                    else -> "Solicitar envío"
                }, textAlign = TextAlign.Center)
            }
            Text("Compara el código y acepta una vez en cada equipo para todo el lote.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun FlashPeerChoice(peer: FlashPeer, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled,
        role = Role.RadioButton, onClick = onSelect), shape = RoundedCornerShape(16.dp),
        color = container, contentColor = content,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, enabled = enabled,
                modifier = Modifier.clearAndSetSemantics {})
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(peer.label, style = MaterialTheme.typography.titleSmall)
                Text(peer.address + if (selected) " · Seleccionado" else "", style = MaterialTheme.typography.bodySmall,
                    color = if (selected) content else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun FlashCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun FlashSectionHeading(title: String, step: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(10.dp)) {
            Text(step, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge)
        }
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun FlashFileSummary(name: String, size: String, showFullName: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(10.dp).size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                maxLines = if (showFullName) Int.MAX_VALUE else 2,
                overflow = if (showFullName) TextOverflow.Clip else TextOverflow.Ellipsis)
            Text(size, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
