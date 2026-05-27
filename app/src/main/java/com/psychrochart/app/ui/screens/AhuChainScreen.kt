package com.psychrochart.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.psychrochart.app.domain.ProcessType
import com.psychrochart.app.domain.SecondaryInput
import com.psychrochart.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AhuChainScreen(vm: MainViewModel) {
    val initialState  by vm.ahuInitialState.collectAsState()
    val chain         by vm.ahuChain.collectAsState()
    val ahuError      by vm.ahuError.collectAsState()

    // ── Initial state inputs ───────────────────────────────────────────────────
    var initDbt    by remember { mutableStateOf("30") }
    var initSecVal by remember { mutableStateOf("50") }
    var initSec    by remember { mutableStateOf(SecondaryInput.RH) }
    var initSecExp by remember { mutableStateOf(false) }

    // ── Process step inputs ────────────────────────────────────────────────────
    var stepType   by remember { mutableStateOf(ProcessType.SENSIBLE_COOLING) }
    var stepTypeExp by remember { mutableStateOf(false) }
    var p1 by remember { mutableStateOf("20") }
    var p2 by remember { mutableStateOf("40") }
    var p3 by remember { mutableStateOf("1.0") }
    var useW by remember { mutableStateOf(false) }

    // Adiabatic Mixing stream 2
    var mixDbt2    by remember { mutableStateOf("15") }
    var mixSec2    by remember { mutableStateOf(SecondaryInput.RH) }
    var mixSec2Val by remember { mutableStateOf("80") }
    var mixM1      by remember { mutableStateOf("1.0") }
    var mixM2      by remember { mutableStateOf("1.0") }
    var mixSec2Exp by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("AHU Multi-Process Chain", style = MaterialTheme.typography.headlineMedium)

        // ── Step 1: Set Initial State ─────────────────────────────────────────
        ChainSectionLabel("Step 1 — Set Initial Supply Air State")

        OutlinedTextField(
            value = initDbt,
            onValueChange = { initDbt = it },
            label = { Text("Dry-Bulb Temperature (°C)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        ExposedDropdownMenuBox(expanded = initSecExp, onExpandedChange = { initSecExp = it }) {
            OutlinedTextField(
                value = ahuSecLabel(initSec),
                onValueChange = {},
                readOnly = true,
                label = { Text("Second Input") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(initSecExp) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = initSecExp, onDismissRequest = { initSecExp = false }) {
                SecondaryInput.entries.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(ahuSecLabel(opt)) },
                        onClick = {
                            initSec    = opt
                            initSecVal = ahuSecDefault(opt)
                            initSecExp = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = initSecVal,
            onValueChange = { initSecVal = it },
            label = { Text("${ahuSecLabel(initSec)} (${ahuSecUnit(initSec)})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val dbt = initDbt.toDoubleOrNull() ?: return@Button
                    val v   = initSecVal.toDoubleOrNull() ?: return@Button
                    vm.setAhuInitialState(dbt, initSec, v)
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Set Initial State")
            }
            OutlinedButton(
                onClick = { vm.clearAhuChain() },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Reset Chain")
            }
        }

        // Show initial state if set
        initialState?.let { s ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Initial State — DBT: %.1f°C | RH: %.1f%% | W: %.5f kg/kg | h: %.2f kJ/kg"
                            .format(s.dbt, s.rh, s.w, s.h),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // ── Step 2: Add Process Steps ─────────────────────────────────────────
        if (initialState != null) {
            HorizontalDivider()
            ChainSectionLabel("Step 2 — Add Process to Chain")

            // Process type dropdown
            ExposedDropdownMenuBox(expanded = stepTypeExp, onExpandedChange = { stepTypeExp = it }) {
                OutlinedTextField(
                    value = stepType.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Process Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(stepTypeExp) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = stepTypeExp, onDismissRequest = { stepTypeExp = false }) {
                    ProcessType.entries.forEach { pt ->
                        DropdownMenuItem(
                            text = { Text(pt.label) },
                            onClick = { stepType = pt; stepTypeExp = false }
                        )
                    }
                }
            }

            // Dynamic parameter inputs
            AhuProcessParams(
                type = stepType,
                p1 = p1, onP1 = { p1 = it },
                p2 = p2, onP2 = { p2 = it },
                p3 = p3, onP3 = { p3 = it },
                useW = useW, onUseW = { useW = it },
                mixDbt2 = mixDbt2, onMixDbt2 = { mixDbt2 = it },
                mixSec2 = mixSec2, onMixSec2 = { mixSec2 = it; mixSec2Val = ahuSecDefault(it) },
                mixSec2Val = mixSec2Val, onMixSec2Val = { mixSec2Val = it },
                mixSec2Exp = mixSec2Exp, onMixSec2Exp = { mixSec2Exp = it },
                mixM1 = mixM1, onMixM1 = { mixM1 = it },
                mixM2 = mixM2, onMixM2 = { mixM2 = it },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val pp1 = p1.toDoubleOrNull() ?: return@Button
                        val pp2 = p2.toDoubleOrNull()
                        val pp3 = p3.toDoubleOrNull()
                        vm.addAhuStep(
                            processType = stepType,
                            param1 = pp1, param2 = pp2, param3 = pp3,
                            useW = useW,
                            mixSec2    = mixSec2,
                            mixDbt2    = mixDbt2.toDoubleOrNull() ?: 15.0,
                            mixSec2Val = mixSec2Val.toDoubleOrNull() ?: 50.0,
                            mixM1      = mixM1.toDoubleOrNull() ?: 1.0,
                            mixM2      = mixM2.toDoubleOrNull() ?: 1.0,
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Step")
                }
                if (chain.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { vm.removeLastAhuStep() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Remove Last")
                    }
                }
            }
        }

        // Error display
        ahuError?.let {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(it, modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        // ── Process Chain Flow Visualization ──────────────────────────────────
        if (chain.isNotEmpty()) {
            HorizontalDivider()
            ChainSectionLabel("Process Chain Flow")

            // Initial state node
            initialState?.let { s ->
                ChainNode(
                    label    = "Supply Air (Initial)",
                    subtitle = "DBT %.1f°C  RH %.1f%%  W %.5f".format(s.dbt, s.rh, s.w),
                    isStart  = true,
                )
            }

            chain.forEach { step ->
                // Arrow + process label
                ChainArrow(step.processType.label)

                // Output state node
                ChainNode(
                    label    = "After Step ${step.stepNumber}",
                    subtitle = "DBT %.1f°C  RH %.1f%%  W %.5f  h %.2f kJ/kg"
                        .format(step.stateOut.dbt, step.stateOut.rh,
                                step.stateOut.w, step.stateOut.h),
                    isStart  = false,
                )
            }

            // ── Before / After Summary Table ──────────────────────────────────
            HorizontalDivider()
            ChainSectionLabel("Before / After Summary")

            val headers = listOf("Step", "Process", "DBT in", "DBT out", "RH in", "RH out", "h in", "h out")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(8.dp),
                       verticalArrangement = Arrangement.spacedBy(4.dp)) {

                    // Header row
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Step", modifier = Modifier.weight(0.5f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold)
                        Text("Process", modifier = Modifier.weight(1.8f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold)
                        Text("DBT\nin→out", modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold)
                        Text("RH\nin→out", modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold)
                        Text("h\nin→out", modifier = Modifier.weight(1.2f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()

                    chain.forEach { step ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${step.stepNumber}",
                                modifier = Modifier.weight(0.5f),
                                style = MaterialTheme.typography.bodySmall)
                            Text(step.processType.label,
                                modifier = Modifier.weight(1.8f),
                                style = MaterialTheme.typography.bodySmall)
                            Text("%.1f→%.1f°C".format(step.stateIn.dbt, step.stateOut.dbt),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall)
                            Text("%.0f→%.0f%%".format(step.stateIn.rh, step.stateOut.rh),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall)
                            Text("%.1f→%.1f".format(step.stateIn.h, step.stateOut.h),
                                modifier = Modifier.weight(1.2f),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // ── Per-step metrics ──────────────────────────────────────────────
            HorizontalDivider()
            ChainSectionLabel("Step-by-Step Metrics")
            chain.forEach { step ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(modifier = Modifier.padding(12.dp),
                           verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Step ${step.stepNumber}: ${step.processType.label}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        step.metrics.forEach { (k, v) ->
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(k, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f))
                                Text(v, style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Helper composables ────────────────────────────────────────────────────────

@Composable
private fun ChainSectionLabel(text: String) {
    Text(text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun ChainNode(label: String, subtitle: String, isStart: Boolean) {
    val color = if (isStart) MaterialTheme.colorScheme.primary
                else         MaterialTheme.colorScheme.secondary
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChainArrow(processLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .padding(start = 4.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
        Text(
            "→  $processLabel",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ── Process parameter inputs ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AhuProcessParams(
    type: ProcessType,
    p1: String, onP1: (String) -> Unit,
    p2: String, onP2: (String) -> Unit,
    p3: String, onP3: (String) -> Unit,
    useW: Boolean, onUseW: (Boolean) -> Unit,
    mixDbt2: String, onMixDbt2: (String) -> Unit,
    mixSec2: SecondaryInput, onMixSec2: (SecondaryInput) -> Unit,
    mixSec2Val: String, onMixSec2Val: (String) -> Unit,
    mixSec2Exp: Boolean, onMixSec2Exp: (Boolean) -> Unit,
    mixM1: String, onMixM1: (String) -> Unit,
    mixM2: String, onMixM2: (String) -> Unit,
) {
    when (type) {
        ProcessType.SENSIBLE_HEATING,
        ProcessType.SENSIBLE_COOLING,
        ProcessType.EVAPORATIVE_COOLING -> {
            AhuNumberField("Final DBT (°C)", p1, onP1)
        }
        ProcessType.HUMIDIFICATION,
        ProcessType.DEHUMIDIFICATION -> {
            AhuWOrRhToggle(useW, onUseW)
            if (useW) AhuNumberField("Final W (kg/kg)", p1, onP1)
            else      AhuNumberField("Final RH (%)", p1, onP1)
        }
        ProcessType.COOLING_DEHUMIDIFICATION,
        ProcessType.HEATING_HUMIDIFICATION -> {
            AhuNumberField("Final DBT (°C)", p1, onP1)
            AhuWOrRhToggle(useW, onUseW)
            if (useW) AhuNumberField("Final W (kg/kg)", p2, onP2)
            else      AhuNumberField("Final RH (%)", p2, onP2)
        }
        ProcessType.ADIABATIC_MIXING -> {
            Text("Stream 2 (mixed air)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary)

            AhuNumberField("Stream 2 — DBT (°C)", mixDbt2, onMixDbt2)

            ExposedDropdownMenuBox(expanded = mixSec2Exp, onExpandedChange = onMixSec2Exp) {
                OutlinedTextField(
                    value = ahuSecLabel(mixSec2),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Stream 2 — Second Input") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mixSec2Exp) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = mixSec2Exp,
                                    onDismissRequest = { onMixSec2Exp(false) }) {
                    SecondaryInput.entries.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(ahuSecLabel(opt)) },
                            onClick = { onMixSec2(opt); onMixSec2Exp(false) }
                        )
                    }
                }
            }

            AhuNumberField(
                "${ahuSecLabel(mixSec2)} (${ahuSecUnit(mixSec2)})",
                mixSec2Val, onMixSec2Val
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = mixM1,
                    onValueChange = onMixM1,
                    label = { Text("Mass flow m1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = mixM2,
                    onValueChange = onMixM2,
                    label = { Text("Mass flow m2") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AhuWOrRhToggle(useW: Boolean, onToggle: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !useW, onClick = { onToggle(false) }, label = { Text("By RH %") })
        FilterChip(selected = useW,  onClick = { onToggle(true) },  label = { Text("By W kg/kg") })
    }
}

@Composable
private fun AhuNumberField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── Label / unit / default helpers ────────────────────────────────────────────

private fun ahuSecLabel(s: SecondaryInput) = when (s) {
    SecondaryInput.RH  -> "Relative Humidity"
    SecondaryInput.WBT -> "Wet-Bulb Temp"
    SecondaryInput.DPT -> "Dew-Point Temp"
    SecondaryInput.W   -> "Humidity Ratio"
    SecondaryInput.V   -> "Specific Volume"
    SecondaryInput.H   -> "Specific Enthalpy"
}

private fun ahuSecUnit(s: SecondaryInput) = when (s) {
    SecondaryInput.RH  -> "%"
    SecondaryInput.WBT, SecondaryInput.DPT -> "°C"
    SecondaryInput.W   -> "kg/kg"
    SecondaryInput.V   -> "m³/kg"
    SecondaryInput.H   -> "kJ/kg"
}

private fun ahuSecDefault(s: SecondaryInput) = when (s) {
    SecondaryInput.RH  -> "50"
    SecondaryInput.WBT -> "18"
    SecondaryInput.DPT -> "13"
    SecondaryInput.W   -> "0.0099"
    SecondaryInput.V   -> "0.855"
    SecondaryInput.H   -> "55.0"
}
