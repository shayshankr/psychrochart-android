package com.psychrochart.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.psychrochart.app.domain.AppSettings
import com.psychrochart.app.domain.PsychroState
import com.psychrochart.app.domain.UnitConverter
import com.psychrochart.app.domain.UnitSystem

@Composable
fun StateResultCard(
    state: PsychroState,
    title: String = "Psychrometric State",
    modifier: Modifier = Modifier,
) {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val uc = UnitConverter
    val clipboard = LocalClipboardManager.current

    val tUnit = uc.tempUnit(unitSystem)
    val wUnit = uc.wUnit(unitSystem)
    val hUnit = uc.hUnit(unitSystem)
    val vUnit = uc.vUnit(unitSystem)

    val props = remember(state, unitSystem) {
        listOf(
            "DBT" to "%.1f $tUnit".format(uc.displayTemp(state.dbt, unitSystem)),
            "WBT" to "%.1f $tUnit".format(uc.displayTemp(state.wbt, unitSystem)),
            "DPT" to "%.1f $tUnit".format(uc.displayTemp(state.dpt, unitSystem)),
            "RH"  to "%.1f %%".format(state.rh),
            "W"   to if (unitSystem == UnitSystem.IP) "%.1f $wUnit".format(uc.kgkgToGrLb(state.w))
                     else "%.5f $wUnit".format(state.w),
            "h"   to "%.2f $hUnit".format(uc.displayH(state.h, unitSystem)),
            "v"   to "%.4f $vUnit".format(uc.displayV(state.v, unitSystem)),
            "Pv"  to if (unitSystem == UnitSystem.IP) "%.4f psi".format(uc.kPaToPsi(state.pv))
                     else "%.3f kPa".format(state.pv),
            "μ"   to "%.4f".format(state.mu),
        )
    }

    val clipboardText = remember(state, unitSystem) {
        props.joinToString("\n") { (k, v) -> "$k: $v" }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(clipboardText)) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy to clipboard",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()

            // 3-column property grid
            props.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { (label, value) ->
                        PropCell(label = label, value = value, modifier = Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun MetricsCard(
    metrics: Map<String, String>,
    title: String,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val clipboardText = remember(metrics) {
        metrics.entries.joinToString("\n") { (k, v) -> "$k: $v" }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(clipboardText)) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy to clipboard",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            metrics.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(key,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f))
                    Text(value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun PropCell(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
