package com.example.wifidrop

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun QetaraBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.background))
}

/** Keeps the launcher geometry and safe area, matching the wordmark's color in either theme. */
@Composable
internal fun QetaraBrandIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = modifier.size(40.dp),
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DirectFlowPanel(
    title: String,
    body: String,
    steps: List<DirectFlowStepUi>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
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
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(UiSpaceS),
                verticalArrangement = Arrangement.spacedBy(UiSpaceS)
            ) {
                steps.forEach { step ->
                    val containerColor = when {
                        step.done -> MaterialTheme.colorScheme.primaryContainer
                        step.active -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                    val contentColor = when {
                        step.done -> MaterialTheme.colorScheme.onPrimaryContainer
                        step.active -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    StatusChip(
                        label = step.label,
                        containerColor = containerColor,
                        contentColor = contentColor
                    )
                }
            }
        }
    }
}

@Composable
internal fun DeveloperFooter(
    onOpenGithub: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        TextButton(
            onClick = onOpenGithub,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_github_invertocat_white),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Creado por Intelog Natanael",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun QetaraBottomNavigation(
    tabs: List<P2pMainTab>,
    selectedTabIndex: Int,
    statusLabel: String,
    onTabSelected: (Int, P2pMainTab) -> Unit,
    tabLabel: (P2pMainTab) -> String,
    tabIcon: (P2pMainTab) -> androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val labelStyle = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp)
            val textMeasurer = rememberTextMeasurer()
            val widestLabelPx = tabs.maxOfOrNull {
                textMeasurer.measure(AnnotatedString(tabLabel(it)), style = labelStyle, softWrap = false).size.width
            } ?: 0
            val availableItemWidthPx = with(density) { maxWidth.toPx() } / tabs.size.coerceAtLeast(1)
            val compactLabels = widestLabelPx + with(density) { 8.dp.toPx() } > availableItemWidthPx
            Column {
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup().semantics { stateDescription = statusLabel },
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = selectedTabIndex == index
                    val label = tabLabel(tab)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = if (compactLabels) 56.dp else 80.dp)
                            .selectable(selected = selected, role = Role.Tab, onClick = { onTabSelected(index, tab) })
                            .semantics(mergeDescendants = true) { contentDescription = label }
                            .padding(horizontal = 2.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ) {
                            Icon(
                                imageVector = tabIcon(tab),
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = if (compactLabels) 8.dp else 16.dp, vertical = 4.dp).size(24.dp)
                            )
                        }
                        if (!compactLabels) {
                            Text(
                                label,
                                modifier = Modifier.clearAndSetSemantics { },
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                style = labelStyle,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (compactLabels) {
                // Keep a full label at large text sizes without taking width away from the tabs.
                Text(
                    tabLabel(tabs[selectedTabIndex.coerceIn(tabs.indices)]),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clearAndSetSemantics { },
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            }
        }
    }
}
