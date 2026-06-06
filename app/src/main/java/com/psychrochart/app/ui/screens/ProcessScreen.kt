package com.psychrochart.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.psychrochart.app.domain.*
import com.psychrochart.app.ui.components.MetricsCard
import com.psychrochart.app.ui.components.StateResultCard
import com.psychrochart.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessScreen(vm: MainViewModel) {
    val processResult by vm.processResult.collectAsState()
    val processError  by vm.processError.collectAsState()
    val unitSystem    by AppSettings.unitSystem.collectAsState()
    val uc = UnitConverter

    // Initial state inputs
    var dbt1   by remember { mutableStateOf(uc.defaultTemp(30.0, unitSystem)) }
    var sec1   by remember { mutableStateOf("50") }
    var secIn1 by remember { mutableStateOf(SecondaryInput.RH) }
    var dropIn1 by remember { mutableStateOf(false) }

    // Process type
    var processType by remember { mutableStateOf(ProcessType.SENSIBLE_COOLING) }
    var dropType    by remember { mutableStateOf(false) }

    // Process parameters
    var param1    by remember { mutableStateOf("20") }
    var param2    by remember { mutableStateOf("40") }
    var param3    by remember { mutableStateOf("1.0") }
    var param4    by remember { mutableStateOf("1.0") }
    var useW      by remember { mutableStateOf(false) }
    var secIn2Mix by remember { mutableStateOf(SecondaryInput.W) }

    LaunchedEffect(unitSystem) {
        dbt1 = uc.defaultTemp(30.0, unitSystem)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Process Analysis", style = MaterialTheme.typography.headlineMedium)

        // ── Initial state ──────────────────────────────────────────────────────
        SectionLabel("Initial State (State 1)")

        OutlinedTextField(
            value = dbt1,
            onValueChange = { dbt1 = it },
            label = { Text("DBT (${uc.tempUnit(unitSystem)})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        ExposedDropdownMenuBox(expanded = dropIn1, onExpandedChange = { dropIn1 = it }) {
            OutlinedTextField(
                value = secInputLabel(secIn1),
                onValueChange = {},
                readOnly = true,
                label = { Text("Second input") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dropIn1) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = dropIn1, onDismissRequest = { dropIn1 = false }) {
                SecondaryInput.entries.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(secInputLabel(opt)) },
                        onClick = { secIn1 = opt; dropIn1 = false }
                    )
                }
            }
        }

        OutlinedTextField(
            value = sec1,
            onValueChange = { sec1 = it },
            label = { Text("${secInputLabel(secIn1)} (${secondaryUnit(secIn1, unitSystem)})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // ── Process type ───────────────────────────────────────────────────────
        SectionLabel("Process Type")

        ExposedDropdownMenuBox(expanded = dropType, onExpandedChange = { dropType = it }) {
            OutlinedTextField(
                value = processType.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Process") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dropType) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = dropType, onDismissRequest = { dropType = false }) {
                ProcessType.entries.forEach { pt ->
                    DropdownMenuItem(
                        text = { Text(pt.label) },
                        onClick = { processType = pt; dropType = false }
                    )
                }
            }
        }

        // ── Process parameters ─────────────────────────────────────────────────
        SectionLabel("Process Parameters")
        ProcessParams(
            type = processType,
            unitSystem = unitSystem,
            p1 = param1, onP1 = { param1 = it },
            p2 = param2, onP2 = { param2 = it },
            p3 = param3, onP3 = { param3 = it },
            useW = useW, onUseW = { useW = it },
            p4 = param4, onP4 = { param4 = it },
            secIn2 = secIn2Mix, onSecIn2 = { secIn2Mix = it },
        )

        // ── Run button ─────────────────────────────────────────────────────────
        Button(
            onClick = {
                val dbtRaw = dbt1.toDoubleOrNull()   ?: return@Button
                val secRaw = sec1.toDoubleOrNull()   ?: return@Button
                val dbtSi  = uc.inputTemp(dbtRaw, unitSystem)
                val secSi  = convertSecondaryToSi(secIn1, secRaw, unitSystem)

                vm.calculateState(dbtSi, secIn1, secSi)
                val s1 = vm.stateResult.value ?: return@Button

                val p1d = param1.toDoubleOrNull() ?: return@Button
                val p2d = param2.toDoubleOrNull()
                val p3d = param3.toDoubleOrNull()
                val p4d = param4.toDoubleOrNull()

                // Convert params from user units to SI for temperature-based params
                val p1si = convertProcessParam(processType, 1, p1d, unitSystem, secIn2Mix, useW)
                val p2si = p2d?.let { convertProcessParam(processType, 2, it, unitSystem, secIn2Mix, useW) }
                val p3si = p3d?.let { convertProcessParam(processType, 3, it, unitSystem, secIn2Mix, useW) }

                vm.runProcess(processType, s1, p1si, p2si, p3si, useW, p4d, secIn2Mix)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Run Process")
        }

        processError?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(it, modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        processResult?.let { result ->
            MetricsCard(result.metrics, title = result.processType.label)
            Spacer(Modifier.height(4.dp))
            StateResultCard(result.state2, title = "Final State (State 2)")
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProcessParams(
    type: ProcessType,
    unitSystem: UnitSystem,
    p1: String, onP1: (String) -> Unit,
    p2: String, onP2: (String) -> Unit,
    p3: String, onP3: (String) -> Unit,
    useW: Boolean, onUseW: (Boolean) -> Unit,
    p4: String = "1.0", onP4: (String) -> Unit = {},
    secIn2: SecondaryInput = SecondaryInput.W, onSecIn2: (SecondaryInput) -> Unit = {},
) {
    val uc = UnitConverter
    val tU = uc.tempUnit(unitSystem)
    val fU = uc.flowUnit(unitSystem)
    val pU = uc.pressureUnit(unitSystem)

    when (type) {
        ProcessType.SENSIBLE_HEATING, ProcessType.SENSIBLE_COOLING -> {
            NumberField("Final DBT ($tU)", p1, onP1)
            NumberField("Total air quantity ($fU)", p2, onP2)
        }
        ProcessType.EVAPORATIVE_COOLING -> {
            NumberField("Final DBT ($tU)", p1, onP1)
        }
        ProcessType.HUMIDIFICATION, ProcessType.DEHUMIDIFICATION -> {
            WOrRhToggle(useW, onUseW, unitSystem)
            if (useW) NumberField("Final W (${uc.wUnit(unitSystem)})", p1, onP1)
            else      NumberField("Final RH (%)", p1, onP1)
        }
        ProcessType.COOLING_DEHUMIDIFICATION -> {
            NumberField("Final DBT ($tU)", p1, onP1)
            WOrRhToggle(useW, onUseW, unitSystem)
            if (useW) NumberField("Final W (${uc.wUnit(unitSystem)})", p2, onP2)
            else      NumberField("Final RH (%)", p2, onP2)
            NumberField("Total air quantity ($fU)", p3, onP3)
        }
        ProcessType.HEATING_HUMIDIFICATION -> {
            NumberField("Final DBT ($tU)", p1, onP1)
            WOrRhToggle(useW, onUseW, unitSystem)
            if (useW) NumberField("Final W (${uc.wUnit(unitSystem)})", p2, onP2)
            else      NumberField("Final RH (%)", p2, onP2)
        }
        ProcessType.ADIABATIC_MIXING -> {
            var dropIn2 by remember { mutableStateOf(false) }
            NumberField("State 2 — DBT ($tU)", p1, onP1)
            ExposedDropdownMenuBox(expanded = dropIn2, onExpandedChange = { dropIn2 = it }) {
                OutlinedTextField(
                    value = secInputLabel(secIn2),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("State 2 — second input") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dropIn2) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = dropIn2, onDismissRequest = { dropIn2 = false }) {
                    listOf(SecondaryInput.WBT, SecondaryInput.DPT, SecondaryInput.W, SecondaryInput.H)
                        .forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(secInputLabel(opt)) },
                                onClick = { onSecIn2(opt); dropIn2 = false },
                            )
                        }
                }
            }
            NumberField("State 2 — ${secInputLabel(secIn2)} (${secondaryUnit(secIn2, unitSystem)})", p2, onP2)
            NumberField("Mass flow m₁ ($fU)", p3, onP3)
            NumberField("Mass flow m₂ ($fU)", p4, onP4)
        }
        ProcessType.FAN_HEAT_RISE -> {
            NumberField("Fan Total Pressure ($pU)", p1, onP1)
            NumberField("Fan Efficiency (%)", p2, onP2)
            NumberField("Mass flow ($fU, optional)", p3, onP3)
        }
        ProcessType.COOLING_COIL -> {
            NumberField("ADP Temperature ($tU)", p1, onP1)
            NumberField("Bypass Factor (0.0 – 1.0)", p2, onP2)
            NumberField("Mass flow ($fU, optional)", p3, onP3)
        }
        ProcessType.ENERGY_RECOVERY -> {
            var dropExh by remember { mutableStateOf(false) }
            Text("Exhaust / Return Air State",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary)
            NumberField("Exhaust DBT ($tU)", p1, onP1)
            ExposedDropdownMenuBox(expanded = dropExh, onExpandedChange = { dropExh = it }) {
                OutlinedTextField(
                    value = secInputLabel(secIn2),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Exhaust — second input") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dropExh) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = dropExh, onDismissRequest = { dropExh = false }) {
                    SecondaryInput.entries.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(secInputLabel(opt)) },
                            onClick = { onSecIn2(opt); dropExh = false },
                        )
                    }
                }
            }
            NumberField("Exhaust ${secInputLabel(secIn2)} (${secondaryUnit(secIn2, unitSystem)})", p2, onP2)
            NumberField("Sensible Effectiveness (0.0 – 1.0)", p3, onP3)
            NumberField("Latent Effectiveness (0.0 – 1.0)", p4, onP4)
        }
    }
}

@Composable
private fun WOrRhToggle(useW: Boolean, onToggle: (Boolean) -> Unit, us: UnitSystem) {
    val wLabel = if (us == UnitSystem.IP) "By W gr/lb" else "By W kg/kg"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !useW, onClick = { onToggle(false) }, label = { Text("By RH %") })
        FilterChip(selected = useW,  onClick = { onToggle(true) },  label = { Text(wLabel) })
    }
}

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun secInputLabel(s: SecondaryInput) = when (s) {
    SecondaryInput.RH  -> "Relative Humidity"
    SecondaryInput.WBT -> "Wet-Bulb Temp"
    SecondaryInput.DPT -> "Dew-Point Temp"
    SecondaryInput.W   -> "Humidity Ratio"
    SecondaryInput.V   -> "Specific Volume"
    SecondaryInput.H   -> "Specific Enthalpy"
}

/** Convert a process parameter from display unit to SI where needed. */
private fun convertProcessParam(
    type: ProcessType,
    paramIndex: Int,
    value: Double,
    us: UnitSystem,
    secIn2: SecondaryInput,
    useW: Boolean,
): Double {
    val uc = UnitConverter
    return when (type) {
        ProcessType.SENSIBLE_HEATING, ProcessType.SENSIBLE_COOLING -> when (paramIndex) {
            1 -> uc.inputTemp(value, us)          // target DBT
            2 -> uc.inputFlow(value, us)           // mass flow
            else -> value
        }
        ProcessType.EVAPORATIVE_COOLING -> if (paramIndex == 1) uc.inputTemp(value, us) else value
        ProcessType.HUMIDIFICATION, ProcessType.DEHUMIDIFICATION -> when {
            paramIndex == 1 && useW -> uc.inputW(value, us)   // final W
            else -> value                                       // RH unchanged
        }
        ProcessType.COOLING_DEHUMIDIFICATION -> when (paramIndex) {
            1 -> uc.inputTemp(value, us)
            2 -> if (useW) uc.inputW(value, us) else value
            3 -> uc.inputFlow(value, us)
            else -> value
        }
        ProcessType.HEATING_HUMIDIFICATION -> when (paramIndex) {
            1 -> uc.inputTemp(value, us)
            2 -> if (useW) uc.inputW(value, us) else value
            else -> value
        }
        ProcessType.ADIABATIC_MIXING -> when (paramIndex) {
            1 -> uc.inputTemp(value, us)           // DBT of stream 2
            2 -> convertSecondaryToSi(secIn2, value, us)
            3, 4 -> uc.inputFlow(value, us)
            else -> value
        }
        ProcessType.FAN_HEAT_RISE -> when (paramIndex) {
            1 -> uc.inputPressure(value, us)       // Pa or inH2O
            3 -> uc.inputFlow(value, us)
            else -> value                           // efficiency %: unchanged
        }
        ProcessType.COOLING_COIL -> when (paramIndex) {
            1 -> uc.inputTemp(value, us)            // ADP temperature
            3 -> uc.inputFlow(value, us)
            else -> value                            // bypass factor: unchanged
        }
        ProcessType.ENERGY_RECOVERY -> when (paramIndex) {
            1 -> uc.inputTemp(value, us)            // exhaust DBT
            2 -> convertSecondaryToSi(secIn2, value, us)
            else -> value                            // effectiveness: unchanged
        }
    }
}
