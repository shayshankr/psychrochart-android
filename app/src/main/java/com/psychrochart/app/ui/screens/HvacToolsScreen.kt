@file:OptIn(ExperimentalMaterial3Api::class)

package com.psychrochart.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psychrochart.app.domain.*
import com.psychrochart.app.viewmodel.MainViewModel

@Composable
fun HvacToolsScreen(vm: MainViewModel) {
    val tabs = listOf("SHR / Loads", "Ventilation", "Property Table", "Cooling Tower")
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = selectedTab == i,
                    onClick  = { selectedTab = i },
                    text     = { Text(title, fontSize = 13.sp) },
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> ShrTab(vm)
                1 -> VentilationTab(vm)
                2 -> PropertyTableTab()
                3 -> CoolingTowerTab(vm)
            }
        }
    }
}

// ── Tab 1: SHR / Room Load Calculator ─────────────────────────────────────────

@Composable
private fun ShrTab(vm: MainViewModel) {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val uc = UnitConverter
    val shrResult by vm.shrResult.collectAsState()
    val shrError  by vm.shrError.collectAsState()

    var roomDbt   by remember { mutableStateOf(uc.defaultTemp(26.0, unitSystem)) }
    var roomRh    by remember { mutableStateOf("50") }
    var sensible  by remember { mutableStateOf("50.0") }
    var latent    by remember { mutableStateOf("15.0") }
    var massFlow  by remember { mutableStateOf("5.0") }

    LaunchedEffect(unitSystem) {
        roomDbt  = uc.defaultTemp(26.0, unitSystem)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Room Load & SHR Calculator",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Given room sensible + latent loads and air flow, calculates the SHR and required supply air state.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HvacSectionLabel("Room Design Conditions")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Room DBT (${uc.tempUnit(unitSystem)})", roomDbt, { roomDbt = it }, Modifier.weight(1f))
            HvacField("Room RH (%)", roomRh, { roomRh = it }, Modifier.weight(1f))
        }

        HvacSectionLabel("Room Loads")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Sensible (kW)", sensible, { sensible = it }, Modifier.weight(1f))
            HvacField("Latent (kW)", latent, { latent = it }, Modifier.weight(1f))
        }

        HvacSectionLabel("Air Flow")
        HvacField("Mass Flow (${uc.flowUnit(unitSystem)})", massFlow, { massFlow = it })

        Button(
            onClick = {
                val dbt = roomDbt.toDoubleOrNull() ?: return@Button
                val rh  = roomRh.toDoubleOrNull()  ?: return@Button
                val qs  = sensible.toDoubleOrNull() ?: return@Button
                val ql  = latent.toDoubleOrNull()   ?: return@Button
                val mf  = massFlow.toDoubleOrNull() ?: return@Button
                vm.calculateShr(
                    uc.inputTemp(dbt, unitSystem),
                    rh, qs, ql,
                    uc.inputFlow(mf, unitSystem),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Calculate SHR & Supply Air")
        }

        shrError?.let { ErrorCard(it) }

        shrResult?.let { r ->
            val tUnit = uc.tempUnit(unitSystem)
            val wUnit = uc.wUnit(unitSystem)
            val hUnit = uc.hUnit(unitSystem)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    HvacResultRow("Sensible Heat Ratio (SHR)", "%.3f".format(r.shr))
                    HvacResultRow("Room DBT", "%.1f %s".format(uc.displayTemp(r.roomState.dbt, unitSystem), tUnit))
                    HvacResultRow("Room RH",  "%.1f %%".format(r.roomState.rh))
                    HvacResultRow("Room h",   "%.2f %s".format(uc.displayH(r.roomState.h, unitSystem), hUnit))
                    HorizontalDivider()
                    HvacResultRow("Supply Air DBT", "%.1f %s".format(uc.displayTemp(r.supplyState.dbt, unitSystem), tUnit))
                    HvacResultRow("Supply Air RH",  "%.1f %%".format(r.supplyState.rh))
                    HvacResultRow("Supply Air W",
                        if (unitSystem == UnitSystem.IP) "%.1f gr/lb".format(uc.kgkgToGrLb(r.supplyState.w))
                        else "%.5f kg/kg".format(r.supplyState.w))
                    HvacResultRow("Supply Air h",   "%.2f %s".format(uc.displayH(r.supplyState.h, unitSystem), hUnit))
                    HorizontalDivider()
                    HvacResultRow("Total Load", "%.2f kW  /  %.3f TR".format(r.totalLoadKw, r.totalLoadKw / 3.5169))
                }
            }
        }
    }
}

// ── Tab 2: ASHRAE 62.1 Ventilation ────────────────────────────────────────────

@Composable
private fun VentilationTab(vm: MainViewModel) {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val uc = UnitConverter
    val ventResult by vm.ventResult.collectAsState()

    var selectedZone by remember { mutableStateOf(MainViewModel.VentZoneType.OFFICE) }
    var zoneExpanded by remember { mutableStateOf(false) }
    var occupancy    by remember { mutableStateOf("20") }
    var floorArea    by remember { mutableStateOf("100") }
    var floorSupply  by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ASHRAE 62.1 Ventilation Rate",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Calculates minimum outdoor air flow per ASHRAE Standard 62.1.\nVoz = (Rp×Pz + Ra×Az) / Ez",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HvacSectionLabel("Zone Type")
        ExposedDropdownMenuBox(expanded = zoneExpanded, onExpandedChange = { zoneExpanded = it }) {
            OutlinedTextField(
                value = selectedZone.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Occupancy Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(zoneExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = zoneExpanded, onDismissRequest = { zoneExpanded = false }) {
                MainViewModel.VentZoneType.entries.forEach { z ->
                    DropdownMenuItem(
                        text = { Text("${z.displayName}  (Rp=${z.rpLsPerPerson} L/s·p, Ra=${z.raLsPerM2} L/s·m²)") },
                        onClick = { selectedZone = z; zoneExpanded = false },
                    )
                }
            }
        }

        HvacSectionLabel("Zone Parameters")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Occupancy (persons)", occupancy, { occupancy = it }, Modifier.weight(1f))
            HvacField("Floor Area (${uc.areaUnit(unitSystem)})", floorArea, { floorArea = it }, Modifier.weight(1f))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = floorSupply, onCheckedChange = { floorSupply = it })
            Text("Floor/low-sidewall supply (Ez = 0.8)")
        }
        Text("Ceiling supply uses Ez = 1.0", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Button(
            onClick = {
                val occ  = occupancy.toIntOrNull() ?: return@Button
                val area = floorArea.toDoubleOrNull() ?: return@Button
                val areaM2 = uc.inputAlt(area, unitSystem).let {
                    if (unitSystem == UnitSystem.IP) uc.ft2ToM2(area) else area
                }
                vm.calculateVentilation(selectedZone, occ, areaM2, floorSupply)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Calculate Minimum OA Flow") }

        ventResult?.let { r ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ventilation Results — ${r.zoneType}",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    HvacResultRow("Ez (zone air distribution)", "%.1f".format(r.ezFactor))
                    HvacResultRow("Occupancy component (Rp×Pz)", "%.1f L/s".format(r.occupancyLs))
                    HvacResultRow("Area component (Ra×Az)",       "%.1f L/s".format(r.areaLs))
                    HorizontalDivider()
                    HvacResultRow("Min OA Flow (Voz)", "%.1f L/s".format(r.totalVozLs))
                    HvacResultRow("Min OA Flow",       "%.4f m³/s".format(r.totalVozM3s))
                    HvacResultRow("Min OA Flow",       "%.1f CFM".format(r.totalVozCfm))
                }
            }
        }
    }
}

// ── Tab 3: Psychrometric Property Table ───────────────────────────────────────

@Composable
private fun PropertyTableTab() {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val uc = UnitConverter
    var showEnthalpy by remember { mutableStateOf(false) }

    val rhValues = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
    val dbtValues = (-5..45 step 2).toList()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Psychrometric Property Table",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = !showEnthalpy, onClick = { showEnthalpy = false },
                    label = { Text("W (${uc.wUnit(unitSystem)})") })
                FilterChip(selected = showEnthalpy, onClick = { showEnthalpy = true },
                    label = { Text("h (${uc.hUnit(unitSystem)})") })
            }
        }

        Text(
            "Rows = DBT (${uc.tempUnit(unitSystem)}), Columns = RH (%)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Box(
            Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                // Header row
                Row {
                    TableCell("DBT\n(${uc.tempUnit(unitSystem)})", isHeader = true, width = 64)
                    rhValues.forEach { rh -> TableCell("$rh%", isHeader = true, width = 72) }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

                dbtValues.forEach { dbtSi ->
                    val dbtDisplay = uc.displayTemp(dbtSi.toDouble(), unitSystem)
                    Row {
                        TableCell("%.0f".format(dbtDisplay), isHeader = true, width = 64)
                        rhValues.forEach { rh ->
                            val state = PsychroCalc.fromDbtRh(dbtSi.toDouble(), rh.toDouble())
                            val value = if (showEnthalpy)
                                "%.1f".format(uc.displayH(state.h, unitSystem))
                            else
                                if (unitSystem == UnitSystem.IP) "%.1f".format(uc.kgkgToGrLb(state.w))
                                else "%.4f".format(state.w)
                            TableCell(value, width = 72)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(text: String, isHeader: Boolean = false, width: Int) {
    val bgColor = if (isHeader) MaterialTheme.colorScheme.surfaceVariant
                  else          MaterialTheme.colorScheme.surface
    Surface(color = bgColor, modifier = Modifier.width(width.dp)) {
        Box(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 10.sp,
                ),
                maxLines = 2,
            )
        }
    }
}

// ── Tab 4: Cooling Tower ──────────────────────────────────────────────────────

@Composable
private fun CoolingTowerTab(vm: MainViewModel) {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val uc = UnitConverter
    val result by vm.coolingTowerResult.collectAsState()
    val error  by vm.coolingTowerError.collectAsState()
    val tUnit  = uc.tempUnit(unitSystem)

    var enteringTemp by remember { mutableStateOf(uc.defaultTemp(35.0, unitSystem)) }
    var ambientWbt   by remember { mutableStateOf(uc.defaultTemp(24.0, unitSystem)) }
    var approach     by remember { mutableStateOf(if (unitSystem == UnitSystem.IP) "9.0" else "5.0") }

    LaunchedEffect(unitSystem) {
        enteringTemp = uc.defaultTemp(35.0, unitSystem)
        ambientWbt   = uc.defaultTemp(24.0, unitSystem)
        approach     = if (unitSystem == UnitSystem.IP) "9.0" else "5.0"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Cooling Tower Performance",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Approach = leaving water temp − ambient WBT\nRange = entering − leaving water temp\nEffectiveness = Range / (Range + Approach)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HvacSectionLabel("Inputs")
        HvacField("Entering Hot Water Temp ($tUnit)", enteringTemp, { enteringTemp = it })
        HvacField("Ambient Wet-Bulb Temp ($tUnit)", ambientWbt, { ambientWbt = it })
        HvacField("Desired Approach (Δ$tUnit)", approach, { approach = it })

        Button(
            onClick = {
                val ewt = enteringTemp.toDoubleOrNull() ?: return@Button
                val wbt = ambientWbt.toDoubleOrNull()   ?: return@Button
                val app = approach.toDoubleOrNull()     ?: return@Button
                val appSi = if (unitSystem == UnitSystem.IP) app * 5.0 / 9.0 else app
                vm.calculateCoolingTower(
                    uc.inputTemp(ewt, unitSystem),
                    uc.inputTemp(wbt, unitSystem),
                    appSi,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Calculate") }

        error?.let { ErrorCard(it) }

        result?.let { r ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cooling Tower Results",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    HvacResultRow("Entering Water Temp",
                        "%.1f $tUnit".format(uc.displayTemp(r.enteringWaterTemp, unitSystem)))
                    HvacResultRow("Ambient WBT",
                        "%.1f $tUnit".format(uc.displayTemp(r.ambientWbt, unitSystem)))
                    HvacResultRow("Leaving Water Temp",
                        "%.1f $tUnit".format(uc.displayTemp(r.leavingWaterTemp, unitSystem)))
                    HorizontalDivider()
                    val displayDelta = { deltaC: Double ->
                        val v = if (unitSystem == UnitSystem.IP) deltaC * 9.0 / 5.0 else deltaC
                        "%.1f Δ$tUnit".format(v)
                    }
                    HvacResultRow("Range (ΔT)",  displayDelta(r.range))
                    HvacResultRow("Approach",    displayDelta(r.approach))
                    HvacResultRow("Effectiveness",       "%.1f %%".format(r.effectiveness * 100))
                }
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun HvacSectionLabel(text: String) {
    Text(text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun HvacField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun HvacResultRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f))
        Text(value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun ErrorCard(msg: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(msg,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall)
    }
}
