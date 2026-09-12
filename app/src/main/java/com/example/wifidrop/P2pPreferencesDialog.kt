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
                    title = { Text("A tu manera") },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(UiSpaceM)
                        ) {
                            Text(
                                "Ajusta Qetara a tu forma de leer y compartir. Los cambios se guardan automáticamente.",
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
                                        "Tamaño del texto",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Escala de la aplicación: ${(state.fontScale * 100).roundToInt()}%",
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
                                            modifier = Modifier.heightIn(min = 48.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text("A-")
                                        }
                                        OutlinedButton(
                                            onClick = { onFontScaleChange(1.0f) },
                                            enabled = kotlin.math.abs(state.fontScale - 1.0f) > 0.01f,
                                            modifier = Modifier.heightIn(min = 48.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text("Normal")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onFontScaleChange((state.fontScale + 0.05f).coerceAtMost(1.25f))
                                            },
                                            enabled = state.fontScale < 1.25f,
                                            modifier = Modifier.heightIn(min = 48.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text("A+")
                                        }
                                    }
                                    PreferenceToggleRow(
                                        title = "Modo compacto",
                                        subtitle = "Acerca los elementos para ver más contenido.",
                                        checked = state.compactMode,
                                        onCheckedChange = onCompactModeChange
                                    )
                                    PreferenceToggleRow(
                                        title = "Vibrar al conectar",
                                        subtitle = "Una vibración cuando el otro equipo está listo.",
                                        checked = state.vibrateOnConnect,
                                        onCheckedChange = onVibrateOnConnectChange
                                    )
                                    PreferenceToggleRow(
                                        title = "Vibrar en errores",
                                        subtitle = "Una vibración cuando algo requiere tu atención.",
                                        checked = state.vibrateOnError,
                                        onCheckedChange = onVibrateOnErrorChange
                                    )
                                    PreferenceToggleRow(
                                        title = "Confirmaciones discretas",
                                        subtitle = "Muestra menos avisos cuando todo va bien.",
                                        checked = state.silentSuccessFeedback,
                                        onCheckedChange = onSilentSuccessFeedbackChange
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = onDismiss) {
                            Text("Cerrar")
                        }
                    }
                )
}
