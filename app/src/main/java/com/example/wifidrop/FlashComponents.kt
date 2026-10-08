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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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
            Text(stringResource(R.string.flash_intro_title), style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.flash_intro_description),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlashCard {
            FlashIntroStep("1", stringResource(R.string.flash_intro_step_activate))
            FlashIntroStep("2", stringResource(R.string.flash_intro_step_choose))
            FlashIntroStep("3", stringResource(R.string.flash_intro_step_verify))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.flash_this_device), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (editingName) {
                OutlinedTextField(value = label, onValueChange = onLabelChange,
                    label = { Text(stringResource(R.string.flash_visible_name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            } else {
                Text(label, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground)
            }
            TextButton(onClick = onToggleName, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (editingName) stringResource(R.string.flash_save_name) else stringResource(R.string.flash_change_name))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onActivate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.flash_activate), textAlign = TextAlign.Center) }
            Text(stringResource(R.string.flash_available_duration), modifier = Modifier.fillMaxWidth(),
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
            Text(stringResource(R.string.flash_active_title), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Surface(color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.flash_time_remaining, remaining), Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (awaitingApproval) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = RoundedCornerShape(10.dp)) {
                    Text(stringResource(R.string.flash_verification_pending), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(if (transferring) stringResource(R.string.flash_transfer_in_progress) else stringResource(R.string.flash_available_to_receive),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(deviceLabel, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onToggleAddress, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (showAddress) stringResource(R.string.flash_hide_address) else stringResource(R.string.flash_my_address))
            }
            TextButton(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.flash_stop)) }
        }
        if (showAddress) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.flash_this_device_address), style = MaterialTheme.typography.labelLarge)
            Text(addresses.ifEmpty { listOf(stringResource(R.string.flash_unavailable)) }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.flash_session_help),
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
                importing -> stringResource(R.string.flash_preparing_files_description)
                selectedPeer == null -> stringResource(R.string.flash_choose_device_to_continue)
                selectedFileCount == 0 -> stringResource(R.string.flash_add_files_to_continue)
                else -> pluralStringResource(R.plurals.flash_files_ready_to_send, selectedFileCount, selectedFileCount)
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
                    importing -> stringResource(R.string.flash_preparing_files)
                    selectedPeer == null -> stringResource(R.string.flash_choose_receiver)
                    selectedFileCount == 0 -> stringResource(R.string.flash_choose_files)
                    else -> stringResource(R.string.flash_request_send)
                }, textAlign = TextAlign.Center)
            }
            Text(stringResource(R.string.flash_batch_verification_help),
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
                Text(if (selected) stringResource(R.string.flash_selected_address, peer.address) else peer.address, style = MaterialTheme.typography.bodySmall,
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
