package com.example.wifidrop

import androidx.compose.ui.res.stringResource

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material3.ButtonDefaults
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
import java.text.DateFormat
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
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
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                FilledTonalButton(
                    onClick = onAction,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    .heightIn(min = primaryHeight.coerceAtLeast(48.dp))
            } else {
                Modifier.weight(1f).heightIn(min = 48.dp)
            }
        ) {
            Text(primaryLabel, textAlign = TextAlign.Center)
        }
        Box {
            OutlinedButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.heightIn(min = (primaryHeight ?: 48.dp).coerceAtLeast(48.dp)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Text(stringResource(R.string.shell_more))
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
                        Text(stringResource(R.string.shell_open))
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
                            Text(if (expanded) stringResource(R.string.shell_less) else stringResource(R.string.shell_details))
                        }
                        TextButton(onClick = onMinimize) {
                            Text(stringResource(R.string.shell_hide))
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
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
            onCheckedChange = null
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
        color = if (done || isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = if (done || isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
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
                label = if (done) stringResource(R.string.shell_ok) else stringResource(R.string.shell_step_number, step),
                containerColor = when {
                    done -> MaterialTheme.colorScheme.primary
                    isActive -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = when {
                    done -> MaterialTheme.colorScheme.onPrimary
                    isActive -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
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
                    text = stringResource(R.string.shell_now),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
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
        !connected -> appString(R.string.shell_no_p2p_session)
        isHost -> appString(R.string.shell_role_host)
        else -> appString(R.string.shell_role_client)
    }
    val transfer = when {
        sending && receiving -> appString(R.string.shell_sending_receiving_active)
        sending -> appString(R.string.shell_sending_in_progress)
        receiving -> appString(R.string.shell_receiving_in_progress)
        else -> appString(R.string.shell_transfer_waiting)
    }
    val queueSummary = appString(
        R.string.shell_queue_accessibility_summary,
        appQuantityString(R.plurals.shell_pending_summary, queuePendingCount, queuePendingCount),
        appQuantityString(R.plurals.shell_active_summary, queueRunningCount, queueRunningCount),
        appQuantityString(R.plurals.shell_failed_summary, queueFailedCount, queueFailedCount)
    )
    return appString(R.string.shell_status_bar_summary, (role).toString(), (transfer).toString(), (queueSummary).toString(), chatCount)
}

internal fun peerStatusLabel(status: Int): String {
    return when (status) {
        WifiP2pDevice.AVAILABLE -> appString(R.string.shell_available)
        WifiP2pDevice.INVITED -> appString(R.string.shell_invited)
        WifiP2pDevice.CONNECTED -> appString(R.string.shell_connected)
        WifiP2pDevice.FAILED -> appString(R.string.shell_failure)
        WifiP2pDevice.UNAVAILABLE -> appString(R.string.shell_unavailable)
        else -> appString(R.string.shell_unknown_status, status)
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 0) return appString(R.string.shell_unknown_value)
    if (bytes < 1024) return appString(R.string.shell_byte_count, bytes)
    val kb = bytes / 1024.0
    if (kb < 1024) return appString(R.string.shell_kilobytes, kb)
    val mb = kb / 1024.0
    if (mb < 1024) return appString(R.string.shell_megabytes, mb)
    val gb = mb / 1024.0
    return appString(R.string.shell_gigabytes, gb)
}

internal fun formatRate(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return appString(R.string.shell_zero_rate)
    val kb = bytesPerSec / 1024.0
    if (kb < 1024) return appString(R.string.shell_kilobytes_per_second, kb)
    val mb = kb / 1024.0
    if (mb < 1024) return appString(R.string.shell_megabytes_per_second, mb)
    val gb = mb / 1024.0
    return appString(R.string.shell_gigabytes_per_second, gb)
}

internal fun formatEta(etaSeconds: Long?): String {
    if (etaSeconds == null) return appString(R.string.shell_unknown_value)
    val sec = etaSeconds.coerceAtLeast(0L)
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) {
        appString(R.string.shell_eta_hours, h, m, s)
    } else {
        appString(R.string.shell_eta_minutes, m, s)
    }
}

internal fun formatSessionExpiry(expiresAtMs: Long, nowMs: Long): String {
    val remaining = expiresAtMs - nowMs
    if (remaining <= 0L) return appString(R.string.shell_expired)
    val min = remaining / 60000L
    val sec = (remaining % 60000L) / 1000L
    return appString(R.string.shell_expires_in, min, sec)
}

internal fun formatHistoryTime(timestampMs: Long): String {
    return try {
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
        fmt.format(Date(timestampMs))
    } catch (_: Exception) {
        timestampMs.toString()
    }
}

internal fun formatRelativeSeen(lastSeenAtMs: Long, nowMs: Long): String {
    if (lastSeenAtMs <= 0L) return appString(R.string.shell_no_record)
    val diff = (nowMs - lastSeenAtMs).coerceAtLeast(0L)
    val sec = diff / 1000L
    return when {
        sec < 10L -> appString(R.string.shell_now_lowercase)
        sec < 60L -> appString(R.string.shell_seconds_ago, sec)
        sec < 3600L -> appString(R.string.shell_minutes_ago, sec / 60L)
        sec < 86_400L -> appString(R.string.shell_hours_ago, sec / 3600L)
        else -> appString(R.string.shell_days_ago, sec / 86_400L)
    }
}

internal fun historyDirectionLabel(direction: TransferDirection): String {
    return when (direction) {
        TransferDirection.SENT -> appString(R.string.shell_sent)
        TransferDirection.RECEIVED -> appString(R.string.shell_received)
    }
}

internal fun historyOutcomeLabel(outcome: TransferOutcome): String {
    return when (outcome) {
        TransferOutcome.SUCCESS -> appString(R.string.shell_completed)
        TransferOutcome.FAILED -> appString(R.string.shell_not_completed)
        TransferOutcome.CANCELED -> appString(R.string.shell_canceled)
    }
}

internal fun queueStatusLabel(status: SendQueueStatus): String {
    return when (status) {
        SendQueueStatus.QUEUED -> appString(R.string.shell_queued)
        SendQueueStatus.RUNNING -> appString(R.string.shell_sending)
        SendQueueStatus.PAUSED -> appString(R.string.shell_paused)
        SendQueueStatus.RETRY_WAIT -> appString(R.string.shell_retry_status)
        SendQueueStatus.SUCCESS -> appString(R.string.shell_ok)
        SendQueueStatus.FAILED -> appString(R.string.shell_failure)
        SendQueueStatus.CANCELED -> appString(R.string.shell_canceled)
    }
}

internal fun isTerminalQueueStatus(status: SendQueueStatus): Boolean {
    return status == SendQueueStatus.SUCCESS ||
        status == SendQueueStatus.FAILED ||
        status == SendQueueStatus.CANCELED
}

internal fun chatStatusLabel(status: ChatMessageStatus): String {
    return when (status) {
        ChatMessageStatus.QUEUED -> appString(R.string.shell_queued)
        ChatMessageStatus.SENDING -> appString(R.string.shell_sending)
        ChatMessageStatus.PUBLISHED -> appString(R.string.shell_published)
        ChatMessageStatus.SENT -> appString(R.string.shell_sent)
        ChatMessageStatus.RECEIVED -> appString(R.string.shell_received)
        ChatMessageStatus.CANCELED -> appString(R.string.shell_canceled)
        ChatMessageStatus.FAILED -> appString(R.string.shell_failed)
    }
}

internal fun friendlyTransferIssue(cause: String?, fallback: String): String? {
    return actionableTransferIssue(cause, fallback)?.let(::runtimeFailureText)
}
