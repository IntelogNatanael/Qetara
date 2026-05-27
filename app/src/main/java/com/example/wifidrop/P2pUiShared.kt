package com.example.wifidrop

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.example.wifidrop.presentation.actionableTransferIssue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val UiSpaceS = 8.dp
internal val UiSpaceM = 16.dp

internal data class ActionMenuItem(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
internal fun StatusChip(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        modifier = modifier.border(
            width = 1.dp,
            color = contentColor.copy(alpha = 0.08f),
            shape = shape
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
internal fun EmptyStateBlock(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpaceM),
            verticalArrangement = Arrangement.spacedBy(UiSpaceS),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                FilledTonalButton(
                    onClick = onAction
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
internal fun PrimaryActionBar(
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimaryClick: () -> Unit,
    menuItems: List<ActionMenuItem>,
    modifier: Modifier = Modifier,
    primaryHeight: Dp? = null
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onPrimaryClick,
            enabled = primaryEnabled,
            modifier = if (primaryHeight != null) {
                Modifier
                    .weight(1f)
                    .height(primaryHeight)
            } else {
                Modifier.weight(1f)
            }
        ) {
            Text(primaryLabel, maxLines = 1)
        }
        Box {
            OutlinedButton(
                onClick = { menuExpanded = true },
                modifier = if (primaryHeight != null) Modifier.height(primaryHeight) else Modifier,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Text("Mas")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                menuItems.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        enabled = item.enabled,
                        onClick = {
                            menuExpanded = false
                            item.onClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun StageHeader(
    title: String,
    summary: String,
    minimized: Boolean,
    expanded: Boolean,
    onOpen: () -> Unit,
    onToggleExpanded: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
    showControls: Boolean = true,
    minimizedContent: @Composable ColumnScope.() -> Unit,
    expandedContent: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiSpaceS)
    ) {
        if (minimized) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (showControls) {
                    TextButton(onClick = onOpen) {
                        Text("Abrir")
                    }
                }
            }
            minimizedContent()
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 3 else 1
                    )
                }
                if (showControls) {
                    Row {
                        TextButton(onClick = onToggleExpanded) {
                            Text(if (expanded) "Menos" else "Detalles")
                        }
                        TextButton(onClick = onMinimize) {
                            Text("Ocultar")
                        }
                    }
                }
            }
            expandedContent()
        }
    }
}

@Composable
internal fun PreferenceToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
internal fun OnboardingStepRow(
    step: Int,
    title: String,
    body: String,
    done: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (done) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpaceS),
            horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(
                label = if (done) "OK" else "Paso $step",
                containerColor = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (isActive && !done) {
                Text(
                    text = "Ahora",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

internal fun buildStatusBarSummary(
    connected: Boolean,
    isHost: Boolean,
    queuePendingCount: Int,
    queueRunningCount: Int,
    queueFailedCount: Int,
    chatCount: Int,
    sending: Boolean,
    receiving: Boolean
): String {
    val role = when {
        !connected -> "sin sesion P2P"
        isHost -> "rol Host"
        else -> "rol Cliente"
    }
    val transfer = when {
        sending && receiving -> "envio y recepcion activos"
        sending -> "enviando en progreso"
        receiving -> "recibiendo en progreso"
        else -> "transferencia en espera"
    }
    val queueSummary = "cola $queuePendingCount pendientes / $queueRunningCount activos / $queueFailedCount fallidos"
    return "$role · $transfer · $queueSummary · mensajes $chatCount"
}

internal fun peerStatusLabel(status: Int): String {
    return when (status) {
        WifiP2pDevice.AVAILABLE -> "Disponible"
        WifiP2pDevice.INVITED -> "Invitado"
        WifiP2pDevice.CONNECTED -> "Conectado"
        WifiP2pDevice.FAILED -> "Fallo"
        WifiP2pDevice.UNAVAILABLE -> "No disponible"
        else -> "Desconocido ($status)"
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "-"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.getDefault(), "%.2f GB", gb)
}

internal fun formatRate(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return "0 B/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB/s", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB/s", mb)
    val gb = mb / 1024.0
    return String.format(Locale.getDefault(), "%.2f GB/s", gb)
}

internal fun formatEta(etaSeconds: Long?): String {
    if (etaSeconds == null) return "-"
    val sec = etaSeconds.coerceAtLeast(0L)
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

internal fun formatSessionExpiry(expiresAtMs: Long, nowMs: Long): String {
    val remaining = expiresAtMs - nowMs
    if (remaining <= 0L) return "Expirada"
    val min = remaining / 60000L
    val sec = (remaining % 60000L) / 1000L
    return "Expira en ${min}m ${sec}s"
}

internal fun formatHistoryTime(timestampMs: Long): String {
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        fmt.format(Date(timestampMs))
    } catch (_: Exception) {
        timestampMs.toString()
    }
}

internal fun formatRelativeSeen(lastSeenAtMs: Long, nowMs: Long): String {
    if (lastSeenAtMs <= 0L) return "sin registro"
    val diff = (nowMs - lastSeenAtMs).coerceAtLeast(0L)
    val sec = diff / 1000L
    return when {
        sec < 10L -> "ahora"
        sec < 60L -> "hace ${sec}s"
        sec < 3600L -> "hace ${sec / 60L}m"
        sec < 86_400L -> "hace ${sec / 3600L}h"
        else -> "hace ${sec / 86_400L}d"
    }
}

internal fun historyDirectionLabel(direction: TransferDirection): String {
    return when (direction) {
        TransferDirection.SENT -> "Enviado"
        TransferDirection.RECEIVED -> "Recibido"
    }
}

internal fun historyOutcomeLabel(outcome: TransferOutcome): String {
    return when (outcome) {
        TransferOutcome.SUCCESS -> "OK"
        TransferOutcome.FAILED -> "FALLIDO"
        TransferOutcome.CANCELED -> "CANCELADO"
    }
}

internal fun queueStatusLabel(status: SendQueueStatus): String {
    return when (status) {
        SendQueueStatus.QUEUED -> "En cola"
        SendQueueStatus.RUNNING -> "Enviando"
        SendQueueStatus.PAUSED -> "Pausado"
        SendQueueStatus.RETRY_WAIT -> "Reintento"
        SendQueueStatus.SUCCESS -> "OK"
        SendQueueStatus.FAILED -> "Fallo"
        SendQueueStatus.CANCELED -> "Cancelado"
    }
}

internal fun isTerminalQueueStatus(status: SendQueueStatus): Boolean {
    return status == SendQueueStatus.SUCCESS ||
        status == SendQueueStatus.FAILED ||
        status == SendQueueStatus.CANCELED
}

internal fun chatStatusLabel(status: ChatMessageStatus): String {
    return when (status) {
        ChatMessageStatus.QUEUED -> "En cola"
        ChatMessageStatus.SENDING -> "Enviando"
        ChatMessageStatus.PUBLISHED -> "Publicado"
        ChatMessageStatus.SENT -> "Enviado"
        ChatMessageStatus.RECEIVED -> "Recibido"
        ChatMessageStatus.CANCELED -> "Cancelado"
        ChatMessageStatus.FAILED -> "Fallido"
    }
}

internal fun friendlyTransferIssue(cause: String?, fallback: String): String? {
    return actionableTransferIssue(cause, fallback)
}
