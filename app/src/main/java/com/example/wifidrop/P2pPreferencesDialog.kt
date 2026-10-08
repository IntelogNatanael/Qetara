package com.example.wifidrop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QetaraPreferencesDialog(
    state: P2pScreenState,
    onFontScaleChange: (Float) -> Unit,
    onCompactModeChange: (Boolean) -> Unit,
    onVibrateOnConnectChange: (Boolean) -> Unit,
    onVibrateOnErrorChange: (Boolean) -> Unit,
    onSilentSuccessFeedbackChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
                AlertDialog(
                    onDismissRequest = onDismiss,
                    title = { Text(appString(R.string.msg_preferences_title)) },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(UiSpaceM)
                        ) {
                            Text(
                                appString(R.string.msg_preferences_intro),
                                style = MaterialTheme.typography.bodySmall
                            )
                            QetaraAppearanceControls()
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(UiSpaceM),
                                    verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                                ) {
                                    Text(
                                        appString(R.string.msg_text_size),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        appString(R.string.msg_app_text_scale, (state.fontScale * 100).roundToInt()),
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                                        verticalArrangement = Arrangement.spacedBy(UiSpaceS)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                onFontScaleChange((state.fontScale - 0.05f).coerceAtLeast(0.85f))
                                            },
                                            enabled = state.fontScale > 0.85f,
                                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                                contentDescription = appString(R.string.msg_decrease_text_size)
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(appString(R.string.msg_text_size_decrease_symbol), modifier = Modifier.clearAndSetSemantics { })
                                        }
                                        OutlinedButton(
                                            onClick = { onFontScaleChange(1.0f) },
                                            enabled = kotlin.math.abs(state.fontScale - 1.0f) > 0.01f,
                                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                                contentDescription = appString(R.string.msg_reset_text_size)
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(appString(R.string.msg_normal), modifier = Modifier.clearAndSetSemantics { })
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onFontScaleChange((state.fontScale + 0.05f).coerceAtMost(1.25f))
                                            },
                                            enabled = state.fontScale < 1.25f,
                                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                                contentDescription = appString(R.string.msg_increase_text_size)
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(appString(R.string.msg_text_size_increase_symbol), modifier = Modifier.clearAndSetSemantics { })
                                        }
                                    }
                                    PreferenceToggleRow(
                                        title = appString(R.string.msg_compact_mode),
                                        subtitle = appString(R.string.msg_compact_mode_hint),
                                        checked = state.compactMode,
                                        onCheckedChange = onCompactModeChange
                                    )
                                    PreferenceToggleRow(
                                        title = appString(R.string.msg_vibrate_connect),
                                        subtitle = appString(R.string.msg_vibrate_connect_hint),
                                        checked = state.vibrateOnConnect,
                                        onCheckedChange = onVibrateOnConnectChange
                                    )
                                    PreferenceToggleRow(
                                        title = appString(R.string.msg_vibrate_errors),
                                        subtitle = appString(R.string.msg_vibrate_errors_hint),
                                        checked = state.vibrateOnError,
                                        onCheckedChange = onVibrateOnErrorChange
                                    )
                                    PreferenceToggleRow(
                                        title = appString(R.string.msg_quiet_confirmations),
                                        subtitle = appString(R.string.msg_quiet_confirmations_hint),
                                        checked = state.silentSuccessFeedback,
                                        onCheckedChange = onSilentSuccessFeedbackChange
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = onDismiss) {
                            Text(appString(R.string.msg_close))
                        }
                    }
                )
}
