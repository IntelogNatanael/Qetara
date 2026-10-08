package com.example.wifidrop

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun QetaraAppearanceControls() {
    val appearance by rememberQetaraAppearance()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceContainerLow,
        contentColor = colors.onSurface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.shell_appearance),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            // A single radio group keeps automatic and manual choices mutually exclusive.
            Column(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QetaraAppearanceChoice(
                    title = stringResource(R.string.shell_follow_system),
                    selected = appearance == QetaraAppearance.SYSTEM,
                    onClick = { QetaraAppearanceStore.save(context, QetaraAppearance.SYSTEM) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = colors.outlineVariant
                )
                listOf(
                    QetaraAppearance.IVORY to Color(0xFFFFF7ED),
                    QetaraAppearance.BLUE_GRAY to Color(0xFFE7EFF2),
                    QetaraAppearance.DARK to Color(0xFF101B22)
                ).forEach { (choice, swatch) ->
                    QetaraAppearanceChoice(
                        title = choice.title,
                        selected = appearance == choice,
                        swatch = swatch,
                        onClick = { QetaraAppearanceStore.save(context, choice) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QetaraAppearanceChoice(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    swatch: Color? = null
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.primaryContainer else colors.surface,
        contentColor = contentColor,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (swatch != null) {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(swatch, RoundedCornerShape(6.dp))
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(6.dp))
                        .clearAndSetSemantics { }
                )
            }
            // Let names wrap and rows grow when the application or system enlarges text.
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.size(24.dp).clearAndSetSemantics { },
                colors = RadioButtonDefaults.colors(
                    selectedColor = colors.onPrimaryContainer,
                    unselectedColor = colors.onSurfaceVariant
                )
            )
        }
    }
}
