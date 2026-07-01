@file:OptIn(ExperimentalMaterial3Api::class)

package com.psychrochart.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
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

// ASHRAE 62.1-2022 Table 6-1 reference rates: Triple(name, Rp L/s·person, Ra L/s·m²)
private val ventRates = listOf(
    Triple("Office",               5.0,  0.06),
    Triple("Conference Room",     10.0,  0.06),
    Triple("Classroom (K-12)",     5.0,  0.06),
    Triple("University Lecture",   7.5,  0.06),
    Triple("Restaurant / Dining",  7.5,  0.18),
    Triple("Retail Store",         3.8,  0.12),
    Triple("Lobby / Reception",    3.8,  0.06),
    Triple("Corridor",             0.0,  0.06),
    Triple("Gymnasium / Fitness", 10.0,  0.06),
    Triple("Hotel Room",           3.8,  0.06),
    Triple("Hospital Patient Rm",  5.0,  0.18),
    Triple("Laboratory",           5.0,  0.30),
    Triple("Auditorium / Theatre", 7.5,  0.06),
    Triple("Library / Reading",    5.0,  0.12),
    Triple("Computer Lab",         5.0,  0.12),
    Triple("Barber / Beauty",      7.5,  0.18),
    Triple("Parking Garage",       0.0,  0.75),
)

@Composable
fun HvacToolsScreen(vm: MainViewModel) {
    val tabs = listOf(
        "SHR / Loads", "Ventilation", "Property Table", "Cooling Tower",
        "Duct Sizing", "Pipe Sizing", "Fan Laws", "Economizer",
        "Room Load", "VRF", "Refrigerant",
        "PMV/Comfort", "VAV/Reheat", "Zone Summary",
        "Pump Sizing", "Coil Select", "Equip. Check", "CT Water",
    )
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
                4 -> DuctSizingTab()
                5 -> PipeSizingTab()
                6 -> FanLawsTab()
                7 -> EconomizerTab()
                8 -> RoomLoadTab()
                9 -> VrfTab()
               10 -> RefrigerantTab()
               11 -> PmvTab()
               12 -> VavReheatTab()
               13 -> ZoneSummaryTab()
               14 -> PumpSizingTab()
               15 -> CoilSelectTab()
               16 -> EquipCheckTab()
               17 -> CtWaterTab()
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
    var deltaT    by remember { mutableStateOf(if (unitSystem == UnitSystem.IP) "14.4" else "8.0") }

    LaunchedEffect(unitSystem) {
        roomDbt  = uc.defaultTemp(26.0, unitSystem)
        deltaT   = if (unitSystem == UnitSystem.IP) "14.4" else "8.0"
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

        HvacSectionLabel("Supply Temperature Difference")
        HvacField("Supply ΔT (Δ${uc.tempUnit(unitSystem)})", deltaT, { deltaT = it })
        Text(
            "Room DBT − Supply DBT. Typical: 8–12 °C (14–22 °F) for AHU systems.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = {
                val dbt = roomDbt.toDoubleOrNull() ?: return@Button
                val rh  = roomRh.toDoubleOrNull()  ?: return@Button
                val qs  = sensible.toDoubleOrNull() ?: return@Button
                val ql  = latent.toDoubleOrNull()   ?: return@Button
                val dt  = deltaT.toDoubleOrNull()   ?: return@Button
                if (dt <= 0) return@Button
                val dtC = if (unitSystem == UnitSystem.IP) dt * 5.0 / 9.0 else dt
                val mf  = qs / (1.006 * dtC)
                vm.calculateShr(
                    uc.inputTemp(dbt, unitSystem),
                    rh, qs, ql,
                    mf,
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
                    HorizontalDivider()
                    HvacResultRow("Computed Mass Flow",
                        "%.3f kg/s  (%.2f ${uc.flowUnit(unitSystem)})".format(
                            r.massFlowKgs, uc.displayFlow(r.massFlowKgs, unitSystem)))
                    HvacResultRow("Supply Air ΔT",
                        "%.1f Δ${uc.tempUnit(unitSystem)}".format(
                            uc.displayTemp(r.roomState.dbt, unitSystem) -
                            uc.displayTemp(r.supplyState.dbt, unitSystem)))
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

        var showRef by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ASHRAE 62.1-2022 Table 6-1 Reference",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { showRef = !showRef }) {
                        Text(if (showRef) "Hide" else "Show")
                    }
                }
                if (showRef) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Box(Modifier.horizontalScroll(rememberScrollState())) {
                        Column {
                            Row {
                                TableCell("Space Type",   true, 130)
                                TableCell("Rp\nL/s·p",   true,  52)
                                TableCell("Ra\nL/s·m²",  true,  58)
                                TableCell("Rp\nCFM/p",   true,  52)
                                TableCell("Ra\nCFM/ft²", true,  62)
                            }
                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                            ventRates.forEach { (name, rp, ra) ->
                                Row {
                                    TableCell(name,                      false, 130)
                                    TableCell("%.1f".format(rp),          false,  52)
                                    TableCell("%.2f".format(ra),          false,  58)
                                    TableCell("%.1f".format(rp * 2.119),  false,  52)
                                    TableCell("%.3f".format(ra * 0.197),  false,  62)
                                }
                            }
                        }
                    }
                    Text(
                        "Voz = (Rp·Pz + Ra·Az) / Ez  |  Ez: 1.0 ceiling supply, 0.8 floor/low-wall supply",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
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

        var showInfo by remember { mutableStateOf(false) }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("What do these values mean?",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { showInfo = !showInfo }) {
                        Text(if (showInfo) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (showInfo) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("W (kg/kg or gr/lb): humidity ratio — mass of water vapour per kg of dry air. " +
                            "Multiply by 2501 kJ/kg to get approximate latent heat per kg.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("h (kJ/kg or BTU/lb): total enthalpy = sensible + latent. " +
                            "Formula: h = 1.006·T + W·(2501 + 1.86·T). Used for coil and AHU sizing.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Values use ASHRAE 2017 equations at current site pressure (Settings → Altitude).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Table: weight(1f) fills remaining height; horizontalScroll on Box, verticalScroll on Column
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Header row
                Row {
                    PropCell("DBT\n(${uc.tempUnit(unitSystem)})", isHeader = true, width = 56)
                    rhValues.forEach { rh -> PropCell("$rh%", isHeader = true, width = 68) }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

                dbtValues.forEach { dbtSi ->
                    val dbtDisplay = uc.displayTemp(dbtSi.toDouble(), unitSystem)
                    Row {
                        PropCell("%.0f".format(dbtDisplay), isHeader = true, width = 56)
                        rhValues.forEach { rh ->
                            val state = PsychroCalc.fromDbtRh(dbtSi.toDouble(), rh.toDouble())
                            val value = if (showEnthalpy)
                                "%.1f".format(uc.displayH(state.h, unitSystem))
                            else
                                if (unitSystem == UnitSystem.IP) "%.1f".format(uc.kgkgToGrLb(state.w))
                                else "%.4f".format(state.w)
                            PropCell(value, isHeader = false, width = 68)
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

// Dedicated cell for the psychrometric property table — no wrapping, clear borders
@Composable
private fun PropCell(text: String, isHeader: Boolean, width: Int) {
    val bgColor = if (isHeader) MaterialTheme.colorScheme.secondaryContainer
                  else          MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Surface(
        color = bgColor,
        modifier = Modifier
            .width(width.dp)
            .border(0.5.dp, borderColor),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                ),
                maxLines = if (isHeader) 2 else 1,
                softWrap = isHeader,
                overflow = TextOverflow.Ellipsis,
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

// ── Tab 5: Duct Sizing ────────────────────────────────────────────────────────

@Composable
private fun DuctSizingTab() {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val isIp = unitSystem == UnitSystem.IP

    val flowUnit = if (isIp) "CFM"           else "L/s"
    val fricUnit = if (isIp) "inH₂O/100ft"   else "Pa/m"
    val velUnit  = if (isIp) "FPM"            else "m/s"
    val dimUnit  = if (isIp) "in"             else "mm"

    var isRound      by remember { mutableStateOf(true) }
    var isEqFriction by remember { mutableStateOf(true) }
    var airflowText  by remember { mutableStateOf(if (isIp) "1000" else "500") }
    var frictionText by remember { mutableStateOf(if (isIp) "0.10" else "0.80") }
    var velocityText by remember { mutableStateOf(if (isIp) "1000" else "5.0") }
    var aspectRatio  by remember { mutableStateOf(1.5) }
    var arExpanded   by remember { mutableStateOf(false) }
    var result       by remember { mutableStateOf<DuctSizingResult?>(null) }
    var error        by remember { mutableStateOf<String?>(null) }
    var convToRect   by remember { mutableStateOf(true) }
    var convDiam     by remember { mutableStateOf(if (isIp) "12" else "300") }
    var convW        by remember { mutableStateOf(if (isIp) "16" else "400") }
    var convH        by remember { mutableStateOf(if (isIp) "10" else "250") }
    var convAr       by remember { mutableStateOf(1.5) }
    var convArExp    by remember { mutableStateOf(false) }
    var convResult   by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(unitSystem) {
        airflowText  = if (isIp) "1000" else "500"
        frictionText = if (isIp) "0.10" else "0.80"
        velocityText = if (isIp) "1000" else "5.0"
        result = null; error = null
    }

    val aspectRatios = listOf(
        1.0 to "1:1 (square)", 1.25 to "1.25:1", 1.5 to "1.5:1",
        2.0 to "2:1", 2.5 to "2.5:1", 3.0 to "3:1", 4.0 to "4:1",
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Duct Sizing Calculator",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Size supply/return/exhaust ducts by equal friction or target velocity. Standard air at 20 °C, galvanized steel (ε = 0.09 mm).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        // ── Round ↔ Rectangular Conversion ────────────────────────────────────
        HvacSectionLabel("Round ↔ Rectangular Conversion (ASHRAE De formula)")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(0, 2),
                onClick = { convToRect = true; convResult = null }, selected = convToRect) {
                Text("Round → Rect")
            }
            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(1, 2),
                onClick = { convToRect = false; convResult = null }, selected = !convToRect) {
                Text("Rect → Round")
            }
        }
        if (convToRect) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HvacField("Diameter ($dimUnit)", convDiam, { convDiam = it; convResult = null }, Modifier.weight(1f))
                ExposedDropdownMenuBox(expanded = convArExp, onExpandedChange = { convArExp = it }, modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = aspectRatios.find { it.first == convAr }?.second ?: "1.5:1",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Aspect Ratio") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(convArExp) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = convArExp, onDismissRequest = { convArExp = false }) {
                        aspectRatios.forEach { (r, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { convAr = r; convArExp = false; convResult = null },
                            )
                        }
                    }
                }
            }
            Button(
                onClick = {
                    val d = convDiam.toDoubleOrNull() ?: return@Button
                    val dMm = if (isIp) d * 25.4 else d
                    val h = dMm * (convAr + 1.0).pow(0.25) / (1.30 * convAr.pow(0.625))
                    val w = convAr * h
                    convResult = if (isIp)
                        "W × H = %.1f × %.1f in  (%.0f × %.0f mm)".format(w / 25.4, h / 25.4, w, h)
                    else
                        "W × H = %.0f × %.0f mm".format(w, h)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Convert Round → Rectangular") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HvacField("Width ($dimUnit)", convW, { convW = it; convResult = null }, Modifier.weight(1f))
                HvacField("Height ($dimUnit)", convH, { convH = it; convResult = null }, Modifier.weight(1f))
            }
            Button(
                onClick = {
                    val w = convW.toDoubleOrNull() ?: return@Button
                    val h = convH.toDoubleOrNull() ?: return@Button
                    val wMm = if (isIp) w * 25.4 else w
                    val hMm = if (isIp) h * 25.4 else h
                    val de = 1.30 * (wMm * hMm).pow(0.625) / (wMm + hMm).pow(0.25)
                    convResult = if (isIp)
                        "De = %.2f in  (%.0f mm)".format(de / 25.4, de)
                    else
                        "De = %.0f mm".format(de)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Convert Rectangular → Round") }
        }
        convResult?.let {
            Text(it,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }
        Text("De = 1.30·(W·H)^0.625 / (W+H)^0.25  [ASHRAE Handbook Fundamentals]",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Shape
        HvacSectionLabel("Duct Shape")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(0, 2),
                onClick = { isRound = true; result = null }, selected = isRound) { Text("Round") }
            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(1, 2),
                onClick = { isRound = false; result = null }, selected = !isRound) { Text("Rectangular") }
        }

        // Method
        HvacSectionLabel("Sizing Method")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(0, 2),
                onClick = { isEqFriction = true; result = null }, selected = isEqFriction) { Text("Equal Friction") }
            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(1, 2),
                onClick = { isEqFriction = false; result = null }, selected = !isEqFriction) { Text("Velocity") }
        }

        // Inputs
        HvacSectionLabel("Inputs")
        HvacField("Airflow ($flowUnit)", airflowText, { airflowText = it })

        if (isEqFriction) {
            HvacField("Design Friction Rate ($fricUnit)", frictionText, { frictionText = it })
            Text(if (isIp) "Typical: 0.08–0.15 inH₂O/100ft" else "Typical: 0.8–1.2 Pa/m",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            HvacField("Design Velocity ($velUnit)", velocityText, { velocityText = it })
            Text(if (isIp) "Main: 1000–1600 FPM  ·  Branch: 600–1000 FPM  ·  Near terminal: 400–600 FPM"
                 else      "Main: 5–8 m/s  ·  Branch: 3–5 m/s  ·  Near terminal: 2–3 m/s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (!isRound) {
            HvacSectionLabel("Aspect Ratio (W : H)")
            ExposedDropdownMenuBox(expanded = arExpanded, onExpandedChange = { arExpanded = it }) {
                OutlinedTextField(
                    value = aspectRatios.find { it.first == aspectRatio }?.second ?: "1.5:1",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Aspect Ratio") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(arExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = arExpanded, onDismissRequest = { arExpanded = false }) {
                    aspectRatios.forEach { (r, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { aspectRatio = r; arExpanded = false; result = null },
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                error = null; result = null
                val q  = airflowText.toDoubleOrNull()
                val fr = frictionText.toDoubleOrNull()
                val v  = velocityText.toDoubleOrNull()
                if (q == null)                    { error = "Invalid airflow value.";       return@Button }
                if (isEqFriction && fr == null)   { error = "Invalid friction rate value."; return@Button }
                if (!isEqFriction && v == null)   { error = "Invalid velocity value.";      return@Button }
                val qSi  = if (isIp) q  * 4.71947e-4 else q  * 1e-3
                val frSi = if (isIp) (fr ?: 0.0) * 8.172  else fr ?: 0.0
                val vSi  = if (isIp) (v  ?: 0.0) * 5.08e-3 else v ?: 0.0
                result = runCatching {
                    when {
                        isRound  && isEqFriction  -> DuctSizer.solveRoundEqualFriction(qSi, frSi)
                        isRound  && !isEqFriction -> DuctSizer.solveRoundVelocity(qSi, vSi)
                        !isRound && isEqFriction  -> DuctSizer.solveRectEqualFriction(qSi, frSi, aspectRatio)
                        else                      -> DuctSizer.solveRectVelocity(qSi, vSi, aspectRatio)
                    }
                }.onFailure { e -> error = e.message ?: "Calculation error." }.getOrNull()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Calculate Duct Size")
        }

        error?.let { ErrorCard(it) }

        result?.let { r ->
            val velMs = r.velocityMs
            val velCategory = when {
                velMs < 2.0  -> "Very low — risk of dust settling"
                velMs < 3.0  -> "Low (near terminal / exhaust)"
                velMs < 5.0  -> "Good (branch duct)"
                velMs < 8.0  -> "Good (main duct)"
                velMs < 10.0 -> "High — check for noise"
                else         -> "Too high — noise & pressure issues"
            }
            val velColor = when {
                velMs < 2.0 || velMs >= 10.0 -> MaterialTheme.colorScheme.error
                velMs in 8.0..10.0           -> MaterialTheme.colorScheme.tertiary
                else                         -> MaterialTheme.colorScheme.primary
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()

                    if (r.isRound) {
                        val diamDisp = if (isIp) r.diameterMm / 25.4 else r.diameterMm
                        HvacResultRow("Duct Diameter", "%.1f $dimUnit".format(diamDisp))
                        if (!isIp) {
                            val nextStd = (ceil(r.diameterMm / 25.0) * 25).toInt()
                            HvacResultRow("Next 25 mm standard", "$nextStd mm")
                        }
                    } else {
                        val wDisp  = if (isIp) r.widthMm!!         / 25.4 else r.widthMm!!
                        val hDisp  = if (isIp) r.heightMm!!        / 25.4 else r.heightMm!!
                        val dhDisp = if (isIp) r.hydraulicDiamMm!! / 25.4 else r.hydraulicDiamMm!!
                        val deDisp = if (isIp) r.diameterMm        / 25.4 else r.diameterMm
                        HvacResultRow("Duct Size (W × H)",       "%.0f × %.0f $dimUnit".format(wDisp, hDisp))
                        HvacResultRow("Hydraulic Diameter",      "%.1f $dimUnit".format(dhDisp))
                        HvacResultRow("Equivalent Round (De)",   "%.1f $dimUnit".format(deDisp))
                    }

                    HorizontalDivider()
                    val velDisp = if (isIp) velMs * 196.85        else velMs
                    val frDisp  = if (isIp) r.frictionPaPerM / 8.172 else r.frictionPaPerM
                    HvacResultRow("Air Velocity",    "%.1f $velUnit".format(velDisp))
                    HvacResultRow("Friction Loss",   "%.3f $fricUnit".format(frDisp))
                    HvacResultRow("Reynolds Number", "%.0f".format(r.reynoldsNumber))
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Velocity Assessment",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.weight(1f))
                        Text(velCategory,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = velColor)
                    }
                    val ncLevel = when {
                        velMs < 2.0  -> "NC ≤ 15 (recording studio)"
                        velMs < 3.0  -> "NC 20–25 (bedroom / quiet office)"
                        velMs < 4.0  -> "NC 25–30 (private office)"
                        velMs < 5.0  -> "NC 30–35 (open-plan office)"
                        velMs < 6.5  -> "NC 35–40 (retail / lobby)"
                        velMs < 8.0  -> "NC 40–45 (industrial)"
                        else         -> "NC > 45 (plant room only)"
                    }
                    HvacResultRow("Est. NC Level", ncLevel)
                }
            }
        }
    }
}

// ── Tab 6: Pipe Sizing ────────────────────────────────────────────────────────

@Composable
private fun PipeSizingTab() {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val isIp = unitSystem == UnitSystem.IP
    val flowUnit = if (isIp) "GPM" else "L/s"
    val fricUnit = if (isIp) "ft H₂O/100ft" else "Pa/m"
    val velUnit  = if (isIp) "FPM" else "m/s"
    val dimUnit  = if (isIp) "in" else "mm"

    var fluidIdx    by remember { mutableIntStateOf(0) }
    var matIdx      by remember { mutableIntStateOf(0) }
    var isEqFric    by remember { mutableStateOf(true) }
    var flowText    by remember { mutableStateOf(if (isIp) "30" else "2.0") }
    var fricText    by remember { mutableStateOf(if (isIp) "4.0" else "300") }
    var velText     by remember { mutableStateOf(if (isIp) "400" else "2.0") }
    var fluidExp    by remember { mutableStateOf(false) }
    var matExp      by remember { mutableStateOf(false) }
    var result      by remember { mutableStateOf<PipeSizingResult?>(null) }
    var error       by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(unitSystem) {
        flowText = if (isIp) "30" else "2.0"
        fricText = if (isIp) "4.0" else "300"
        velText  = if (isIp) "400" else "2.0"
        result = null; error = null
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pipe Sizing Calculator", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Sizes CHW, HHW, and condenser water pipes using Darcy-Weisbach with Swamee-Jain friction factor.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HvacSectionLabel("Fluid & Material")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExposedDropdownMenuBox(expanded = fluidExp, onExpandedChange = { fluidExp = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = PipeFluid.entries[fluidIdx].label, onValueChange = {}, readOnly = true,
                    label = { Text("Fluid") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(fluidExp) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), singleLine = true,
                )
                ExposedDropdownMenu(expanded = fluidExp, onDismissRequest = { fluidExp = false }) {
                    PipeFluid.entries.forEachIndexed { i, f ->
                        DropdownMenuItem(text = { Text(f.label) }, onClick = { fluidIdx = i; fluidExp = false })
                    }
                }
            }
            ExposedDropdownMenuBox(expanded = matExp, onExpandedChange = { matExp = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = PipeMaterial.entries[matIdx].label, onValueChange = {}, readOnly = true,
                    label = { Text("Material") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(matExp) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), singleLine = true,
                )
                ExposedDropdownMenu(expanded = matExp, onDismissRequest = { matExp = false }) {
                    PipeMaterial.entries.forEachIndexed { i, m ->
                        DropdownMenuItem(text = { Text(m.label) }, onClick = { matIdx = i; matExp = false })
                    }
                }
            }
        }

        HvacSectionLabel("Sizing Method")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(0, 2), onClick = { isEqFric = true; result = null }, selected = isEqFric) { Text("Equal Friction") }
            SegmentedButton(shape = SegmentedButtonDefaults.itemShape(1, 2), onClick = { isEqFric = false; result = null }, selected = !isEqFric) { Text("Velocity") }
        }

        HvacSectionLabel("Inputs")
        HvacField("Flow Rate ($flowUnit)", flowText, { flowText = it })
        if (isEqFric) {
            HvacField("Friction Rate ($fricUnit)", fricText, { fricText = it })
            Text(if (isIp) "Typical: 2-4 ft H2O/100ft" else "Typical: 200-400 Pa/m",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            HvacField("Design Velocity ($velUnit)", velText, { velText = it })
            Text(if (isIp) "Typical: 300-600 FPM" else "Typical: 1.5-3.0 m/s",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Button(
            onClick = {
                error = null; result = null
                val q  = flowText.toDoubleOrNull()
                val fr = fricText.toDoubleOrNull()
                val v  = velText.toDoubleOrNull()
                if (q == null)              { error = "Invalid flow rate."; return@Button }
                if (isEqFric && fr == null) { error = "Invalid friction rate."; return@Button }
                if (!isEqFric && v == null) { error = "Invalid velocity."; return@Button }
                val qLs  = if (isIp) (q) * 0.0630902 else q
                val frSi = if (isIp) (fr ?: 0.0) * 98.0638 else fr ?: 0.0
                val vSi  = if (isIp) (v ?: 0.0) * 0.00508 else v ?: 0.0
                result = runCatching {
                    if (isEqFric) PipeSizer.solveEqualFriction(qLs, frSi, PipeFluid.entries[fluidIdx], PipeMaterial.entries[matIdx])
                    else          PipeSizer.solveVelocity(qLs, vSi, PipeFluid.entries[fluidIdx], PipeMaterial.entries[matIdx])
                }.onFailure { e -> error = e.message }.getOrNull()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Icon(Icons.Default.Calculate, null); Spacer(Modifier.width(8.dp)); Text("Calculate Pipe Size") }

        error?.let { ErrorCard(it) }

        result?.let { r ->
            val dCalc = if (isIp) r.diameterMm / 25.4 else r.diameterMm
            val dNb   = if (isIp) r.recommendedNbMm / 25.4 else r.recommendedNbMm.toDouble()
            val vCalc = if (isIp) r.velocityMs * 196.85 else r.velocityMs
            val vNb   = if (isIp) r.velocityAtNb * 196.85 else r.velocityAtNb
            val fpC   = if (isIp) r.frictionPaPerM / 98.0638 else r.frictionPaPerM
            val fpNb  = if (isIp) r.frictionAtNb / 98.0638 else r.frictionAtNb
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    HvacResultRow("Calculated Diameter", "%.1f $dimUnit".format(dCalc))
                    HvacResultRow("Recommended NB", if (isIp) "%.2f $dimUnit  (NB ${r.recommendedNbMm} mm)".format(dNb) else "NB ${r.recommendedNbMm} mm")
                    HorizontalDivider()
                    HvacResultRow("Velocity (calc)", "%.2f $velUnit".format(vCalc))
                    HvacResultRow("Velocity at NB", "%.2f $velUnit".format(vNb))
                    HvacResultRow("Friction (calc)", "%.1f $fricUnit".format(fpC))
                    HvacResultRow("Friction at NB", "%.1f $fricUnit".format(fpNb))
                    HvacResultRow("Reynolds No.", "%.0f".format(r.reynoldsNumber))
                }
            }
        }
    }
}

// ── Tab 7: Fan Laws ───────────────────────────────────────────────────────────

@Composable
private fun FanLawsTab() {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val isIp = unitSystem == UnitSystem.IP
    val flowUnit = if (isIp) "CFM" else "m3/s"
    val presUnit = if (isIp) "inH2O" else "Pa"
    val powrUnit = if (isIp) "HP" else "kW"

    var rpm1   by remember { mutableStateOf("1450") }
    var q1     by remember { mutableStateOf(if (isIp) "5000" else "2.5") }
    var dp1    by remember { mutableStateOf(if (isIp) "1.5" else "375") }
    var pw1    by remember { mutableStateOf(if (isIp) "5.0" else "3.7") }
    var pct    by remember { mutableStateOf("80") }
    var result by remember { mutableStateOf<List<Pair<String, String>>?>(null) }

    LaunchedEffect(unitSystem) {
        q1 = if (isIp) "5000" else "2.5"
        dp1 = if (isIp) "1.5" else "375"
        pw1 = if (isIp) "5.0" else "3.7"
        result = null
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Fan Laws Calculator", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Fan affinity laws: Q proportional N, dP proportional N^2, P proportional N^3. Enter rated conditions and desired speed percentage.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HvacSectionLabel("Rated Conditions")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Speed (RPM)", rpm1, { rpm1 = it }, Modifier.weight(1f))
            HvacField("Flow ($flowUnit)", q1, { q1 = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Pressure ($presUnit)", dp1, { dp1 = it }, Modifier.weight(1f))
            HvacField("Power ($powrUnit)", pw1, { pw1 = it }, Modifier.weight(1f))
        }

        HvacSectionLabel("New Operating Speed")
        HvacField("Speed (% of rated)", pct, { pct = it })
        Text("e.g. 80 = 80% speed via VFD",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Button(
            onClick = {
                val n1 = rpm1.toDoubleOrNull() ?: return@Button
                val q  = q1.toDoubleOrNull()   ?: return@Button
                val dp = dp1.toDoubleOrNull()   ?: return@Button
                val pw = pw1.toDoubleOrNull()   ?: return@Button
                val sp = pct.toDoubleOrNull()   ?: return@Button
                val r  = sp / 100.0
                val n2  = n1 * r
                val q2  = q  * r
                val dp2 = dp * r.pow(2)
                val pw2 = pw * r.pow(3)
                val freq = 50.0 * r
                result = listOf(
                    "New Speed"     to "%.0f RPM (%.1f%%)".format(n2, sp),
                    "New Flow"      to "%.2f $flowUnit".format(q2),
                    "New Pressure"  to "%.2f $presUnit".format(dp2),
                    "New Power"     to "%.3f $powrUnit".format(pw2),
                    "VFD Frequency" to "%.1f Hz".format(freq),
                    "Power Saving"  to "%.1f%%".format((1 - r.pow(3)) * 100),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Icon(Icons.Default.Calculate, null); Spacer(Modifier.width(8.dp)); Text("Calculate New Conditions") }

        result?.let { rows ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Fan Laws Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    rows.forEach { (k, v) -> HvacResultRow(k, v) }
                }
            }
        }
    }
}

// ── Tab 8: Economizer Analysis ────────────────────────────────────────────────

@Composable
private fun EconomizerTab() {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val uc = UnitConverter
    val isIp = unitSystem == UnitSystem.IP
    val tUnit = uc.tempUnit(unitSystem)

    var oaDbt  by remember { mutableStateOf(uc.defaultTemp(35.0, unitSystem)) }
    var oaRh   by remember { mutableStateOf("40") }
    var raDbt  by remember { mutableStateOf(uc.defaultTemp(24.0, unitSystem)) }
    var raRh   by remember { mutableStateOf("50") }
    var result by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var error  by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(unitSystem) {
        oaDbt = uc.defaultTemp(35.0, unitSystem)
        raDbt = uc.defaultTemp(24.0, unitSystem)
        result = null; error = null
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Economizer / Free Cooling", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Determines if free cooling is available and calculates mixed-air states at various OA fractions.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HvacSectionLabel("Outdoor Air")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("OA DBT ($tUnit)", oaDbt, { oaDbt = it }, Modifier.weight(1f))
            HvacField("OA RH (%)", oaRh, { oaRh = it }, Modifier.weight(1f))
        }
        HvacSectionLabel("Return Air")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("RA DBT ($tUnit)", raDbt, { raDbt = it }, Modifier.weight(1f))
            HvacField("RA RH (%)", raRh, { raRh = it }, Modifier.weight(1f))
        }

        Button(
            onClick = {
                error = null; result = null
                val oaT = oaDbt.toDoubleOrNull() ?: run { error = "Invalid OA DBT"; return@Button }
                val oaR = oaRh.toDoubleOrNull()  ?: run { error = "Invalid OA RH"; return@Button }
                val raT = raDbt.toDoubleOrNull()  ?: run { error = "Invalid RA DBT"; return@Button }
                val raR = raRh.toDoubleOrNull()   ?: run { error = "Invalid RA RH"; return@Button }
                runCatching {
                    val oa = PsychroCalc.fromDbtRh(uc.inputTemp(oaT, unitSystem), oaR)
                    val ra = PsychroCalc.fromDbtRh(uc.inputTemp(raT, unitSystem), raR)
                    val enthEcon = oa.h < ra.h
                    val dbtEcon  = oa.dbt < ra.dbt
                    val rows = mutableListOf(
                        "OA Enthalpy"    to "%.2f ${uc.hUnit(unitSystem)}".format(uc.displayH(oa.h, unitSystem)),
                        "RA Enthalpy"    to "%.2f ${uc.hUnit(unitSystem)}".format(uc.displayH(ra.h, unitSystem)),
                        "Enthalpy Econ?" to if (enthEcon) "YES - h_OA < h_RA" else "NO - h_OA >= h_RA",
                        "Dry-Bulb Econ?" to if (dbtEcon)  "YES - T_OA < T_RA" else "NO - T_OA >= T_RA",
                    )
                    listOf(0, 25, 50, 75, 100).forEach { pctOa ->
                        val f      = pctOa / 100.0
                        val mixDbt = oa.dbt * f + ra.dbt * (1 - f)
                        val mixW   = oa.w   * f + ra.w   * (1 - f)
                        val mix    = PsychroCalc.fromDbtW(mixDbt, mixW)
                        rows += "$pctOa% OA Mixed" to "%.1f $tUnit / %.1f%% RH".format(
                            uc.displayTemp(mix.dbt, unitSystem), mix.rh)
                    }
                    result = rows
                }.onFailure { e -> error = e.message }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Icon(Icons.Default.Calculate, null); Spacer(Modifier.width(8.dp)); Text("Analyse Economizer") }

        error?.let { ErrorCard(it) }
        result?.let { rows ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Economizer Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    rows.forEach { (k, v) -> HvacResultRow(k, v) }
                }
            }
        }
    }
}

// ── Tab 9: Room Load (Simplified) ─────────────────────────────────────────────

@Composable
private fun RoomLoadTab() {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val uc = UnitConverter
    val isIp = unitSystem == UnitSystem.IP
    val tUnit = uc.tempUnit(unitSystem)
    val aUnit = if (isIp) "ft2" else "m2"

    var floorArea  by remember { mutableStateOf("100") }
    var ceilHeight by remember { mutableStateOf("3.0") }
    var occupants  by remember { mutableStateOf("10") }
    var lighting   by remember { mutableStateOf("12") }
    var equipment  by remember { mutableStateOf("15") }
    var glassE     by remember { mutableStateOf("5") }
    var glassW     by remember { mutableStateOf("5") }
    var glassS     by remember { mutableStateOf("3") }
    var glassN     by remember { mutableStateOf("2") }
    var uWall      by remember { mutableStateOf("0.5") }
    var uRoof      by remember { mutableStateOf("0.35") }
    var outdoorDbt by remember { mutableStateOf(uc.defaultTemp(43.0, unitSystem)) }
    var outdoorRh  by remember { mutableStateOf("30") }
    var indoorDbt  by remember { mutableStateOf(uc.defaultTemp(24.0, unitSystem)) }
    var indoorRh   by remember { mutableStateOf("50") }
    var result     by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var error      by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(unitSystem) {
        outdoorDbt = uc.defaultTemp(43.0, unitSystem)
        indoorDbt  = uc.defaultTemp(24.0, unitSystem)
        result = null; error = null
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Room Load Estimator", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Simplified cooling load: occupants, lighting, equipment, solar (glass), conduction, and infiltration.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HvacSectionLabel("Room Geometry ($aUnit / m)")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Floor Area ($aUnit)", floorArea, { floorArea = it }, Modifier.weight(1f))
            HvacField("Ceiling Height (m)", ceilHeight, { ceilHeight = it }, Modifier.weight(1f))
        }
        HvacSectionLabel("Internal Loads")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Occupants", occupants, { occupants = it }, Modifier.weight(1f))
            HvacField("Lighting (W/m2)", lighting, { lighting = it }, Modifier.weight(1f))
        }
        HvacField("Equipment (W/m2)", equipment, { equipment = it })

        HvacSectionLabel("Glass Area (m2) - E / W / S / N")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HvacField("E", glassE, { glassE = it }, Modifier.weight(1f))
            HvacField("W", glassW, { glassW = it }, Modifier.weight(1f))
            HvacField("S", glassS, { glassS = it }, Modifier.weight(1f))
            HvacField("N", glassN, { glassN = it }, Modifier.weight(1f))
        }
        HvacSectionLabel("Envelope U-Values (W/m2K)")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Wall U-value", uWall, { uWall = it }, Modifier.weight(1f))
            HvacField("Roof U-value", uRoof, { uRoof = it }, Modifier.weight(1f))
        }
        HvacSectionLabel("Conditions")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Outdoor DBT ($tUnit)", outdoorDbt, { outdoorDbt = it }, Modifier.weight(1f))
            HvacField("Outdoor RH (%)", outdoorRh, { outdoorRh = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Indoor DBT ($tUnit)", indoorDbt, { indoorDbt = it }, Modifier.weight(1f))
            HvacField("Indoor RH (%)", indoorRh, { indoorRh = it }, Modifier.weight(1f))
        }

        Button(
            onClick = {
                error = null; result = null
                runCatching {
                    val aM2  = floorArea.toDouble() * (if (isIp) 0.0929 else 1.0)
                    val h    = ceilHeight.toDouble()
                    val n    = occupants.toInt()
                    val lW   = lighting.toDouble()
                    val eW   = equipment.toDouble()
                    val gE   = glassE.toDouble(); val gW = glassW.toDouble()
                    val gS   = glassS.toDouble(); val gN = glassN.toDouble()
                    val uW   = uWall.toDouble();  val uR = uRoof.toDouble()
                    val odT  = uc.inputTemp(outdoorDbt.toDouble(), unitSystem)
                    val odR  = outdoorRh.toDouble()
                    val idT  = uc.inputTemp(indoorDbt.toDouble(), unitSystem)
                    val od   = PsychroCalc.fromDbtRh(odT, odR)
                    val id   = PsychroCalc.fromDbtRh(idT, indoorRh.toDouble())
                    val dtC  = odT - idT
                    val qOccS = n * 75.0
                    val qOccL = n * 55.0
                    val qLit  = lW * aM2
                    val qEqS  = eW * aM2
                    val qSol  = (gE + gW) * 200.0 * 0.6 + gS * 120.0 * 0.6 + gN * 80.0 * 0.6
                    val perim = 4 * sqrt(aM2)
                    val qWall = uW * (perim * h) * (dtC + 5.0)
                    val qRoof = uR * aM2 * (dtC + 15.0)
                    val volM3 = aM2 * h
                    val qInfS = 1.2 * 0.5 * volM3 / 3600.0 * 1006.0 * dtC
                    val qInfL = 1.2 * 0.5 * volM3 / 3600.0 * 2501000.0 * maxOf(0.0, od.w - id.w)
                    val totalS = (qOccS + qLit + qEqS + qSol + qWall + qRoof + qInfS) / 1000.0
                    val totalL = (qOccL + qInfL) / 1000.0
                    val total  = totalS + totalL
                    val shr    = totalS / total
                    val tr     = total / 3.517
                    // ADP: intersect ESHF/GSHF line with saturation curve (binary search)
                    val shrSlope = 1.006 * (1.0 - shr) / (2501.0 * shr)
                    var adpLo = -15.0; var adpHi = idT - 0.05
                    repeat(60) {
                        val mid   = (adpLo + adpHi) / 2.0
                        val wLine = id.w + shrSlope * (mid - idT)
                        val wSat  = PsychroCalc.humRatioFromRelHum(mid, 1.0)
                        if (wLine > wSat) adpLo = mid else adpHi = mid
                    }
                    val tAdp     = (adpLo + adpHi) / 2.0
                    val adpState = PsychroCalc.fromDbtRh(tAdp, 100.0)
                    result = listOf(
                        "Occupant Sensible" to "%.2f kW".format(qOccS / 1000),
                        "Occupant Latent"   to "%.2f kW".format(qOccL / 1000),
                        "Lighting"          to "%.2f kW".format(qLit / 1000),
                        "Equipment"         to "%.2f kW".format(qEqS / 1000),
                        "Solar (glass)"     to "%.2f kW".format(qSol / 1000),
                        "Wall Conduction"   to "%.2f kW".format(qWall / 1000),
                        "Roof Conduction"   to "%.2f kW".format(qRoof / 1000),
                        "Infiltration Sens" to "%.2f kW".format(qInfS / 1000),
                        "Infiltration Lat"  to "%.2f kW".format(qInfL / 1000),
                        "Total Sensible"    to "%.2f kW".format(totalS),
                        "Total Latent"      to "%.2f kW".format(totalL),
                        "Total Load"        to "%.2f kW  /  %.2f TR".format(total, tr),
                        "SHR"               to "%.3f".format(shr),
                        "---"               to "",
                        "ESHF (= SHR here)" to "%.3f  (no separate OA in this model)".format(shr),
                        "Rec. ADP"          to "%.1f %s".format(uc.displayTemp(tAdp, unitSystem), tUnit),
                        "ADP WBT"           to "%.1f %s".format(uc.displayTemp(adpState.wbt, unitSystem), tUnit),
                        "ADP note"          to "Coil leaving air ≤ %.1f %s".format(
                            uc.displayTemp(tAdp + 2.0, unitSystem), tUnit),
                    )
                }.onFailure { e -> error = e.message ?: "Calculation error" }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Icon(Icons.Default.Calculate, null); Spacer(Modifier.width(8.dp)); Text("Estimate Room Load") }

        error?.let { ErrorCard(it) }
        result?.let { rows ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Room Cooling Load", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    rows.forEach { (k, v) ->
                        if (k == "---") HorizontalDivider()
                        else HvacResultRow(k, v)
                    }
                }
            }
        }
    }
}

// ── Tab 10: VRF Capacity Correction ──────────────────────────────────────────

@Composable
private fun VrfTab() {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val uc = UnitConverter
    val isIp = unitSystem == UnitSystem.IP
    val tUnit = uc.tempUnit(unitSystem)

    var load       by remember { mutableStateOf("50") }
    var outdoorT   by remember { mutableStateOf(uc.defaultTemp(43.0, unitSystem)) }
    var pipingLen  by remember { mutableStateOf("60") }
    var heightDiff by remember { mutableStateOf("10") }
    var result     by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var error      by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(unitSystem) { outdoorT = uc.defaultTemp(43.0, unitSystem); result = null; error = null }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("VRF Capacity Correction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Derates VRF outdoor unit nominal capacity for outdoor temperature, piping length, and height difference.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HvacSectionLabel("Required Cooling Load (kW)")
        HvacField("Design Load (kW)", load, { load = it })
        HvacSectionLabel("Site Conditions")
        HvacField("Outdoor Design DBT ($tUnit)", outdoorT, { outdoorT = it })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Piping Length (m)", pipingLen, { pipingLen = it }, Modifier.weight(1f))
            HvacField("Height Diff (m)", heightDiff, { heightDiff = it }, Modifier.weight(1f))
        }
        Text("Height = indoor elevation above outdoor unit (negative if outdoor is above indoor)",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Button(
            onClick = {
                error = null; result = null
                runCatching {
                    val qKw   = load.toDouble()
                    val tC    = uc.inputTemp(outdoorT.toDouble(), unitSystem)
                    val pLen  = pipingLen.toDouble()
                    val hDiff = heightDiff.toDouble()
                    val dbts  = doubleArrayOf(25.0, 30.0, 35.0, 38.0, 40.0, 43.0, 45.0, 47.0)
                    val fdbts = doubleArrayOf(1.10, 1.05, 1.00, 0.93, 0.88, 0.80, 0.75, 0.68)
                    fun interp(xs: DoubleArray, ys: DoubleArray, x: Double): Double {
                        if (x <= xs.first()) return ys.first()
                        if (x >= xs.last())  return ys.last()
                        val i = xs.indexOfFirst { it >= x } - 1
                        val f = (x - xs[i]) / (xs[i + 1] - xs[i])
                        return ys[i] * (1 - f) + ys[i + 1] * f
                    }
                    val fTemp = interp(dbts, fdbts, tC)
                    val fPipe = when {
                        pLen <=  30 -> 1.00
                        pLen <=  60 -> 0.97
                        pLen <=  90 -> 0.94
                        pLen <= 120 -> 0.90
                        else        -> 0.85
                    }
                    val fHgt = when {
                        hDiff <= 0  -> 1.00
                        hDiff <= 10 -> 0.98
                        hDiff <= 20 -> 0.96
                        hDiff <= 30 -> 0.93
                        else        -> 0.90
                    }
                    val fTotal    = fTemp * fPipe * fHgt
                    val required  = qKw / fTotal
                    val stdSizes  = doubleArrayOf(22.4, 28.0, 33.5, 40.0, 45.0, 50.0, 56.0, 63.0, 71.0, 80.0, 90.0, 100.0, 112.0, 125.0, 140.0)
                    val recommended = stdSizes.firstOrNull { it >= required } ?: stdSizes.last()
                    result = listOf(
                        "Design Load"                to "%.1f kW / %.2f TR".format(qKw, qKw / 3.517),
                        "Temp Factor (${tC.toInt()}C)" to "%.3f".format(fTemp),
                        "Piping Factor (${pLen.toInt()} m)" to "%.3f".format(fPipe),
                        "Height Factor (${hDiff.toInt()} m)" to "%.3f".format(fHgt),
                        "Combined Correction"         to "%.3f  (%.1f%% of rated)".format(fTotal, fTotal * 100),
                        "Required Nominal Cap"        to "%.1f kW".format(required),
                        "Recommended Std Size"        to "%.1f kW / %.1f TR".format(recommended, recommended / 3.517),
                    )
                }.onFailure { e -> error = e.message ?: "Calculation error" }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Icon(Icons.Default.Calculate, null); Spacer(Modifier.width(8.dp)); Text("Calculate VRF Correction") }

        error?.let { ErrorCard(it) }
        result?.let { rows ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("VRF Correction Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    rows.forEach { (k, v) -> HvacResultRow(k, v) }
                }
            }
        }
    }
}

// ── Tab 11: Refrigerant Quick Reference ──────────────────────────────────────

@Composable
private fun RefrigerantTab() {
    var selectedRef by remember { mutableIntStateOf(0) }
    var tempText    by remember { mutableStateOf("10") }
    var calcResult  by remember { mutableStateOf<String?>(null) }
    var refExpanded by remember { mutableStateOf(false) }

    val ref = refrigerants[selectedRef]

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Refrigerant Reference", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Saturation pressures and operating limits for common HVAC refrigerants.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HvacSectionLabel("Refrigerant")
        ExposedDropdownMenuBox(expanded = refExpanded, onExpandedChange = { refExpanded = it }) {
            OutlinedTextField(
                value = ref.name, onValueChange = {}, readOnly = true,
                label = { Text("Select Refrigerant") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(refExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = refExpanded, onDismissRequest = { refExpanded = false }) {
                refrigerants.forEachIndexed { i, r ->
                    DropdownMenuItem(
                        text = { Text("${r.name}  (GWP ${r.gwp})") },
                        onClick = { selectedRef = i; refExpanded = false; calcResult = null },
                    )
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HvacResultRow("GWP", "${ref.gwp}")
                HvacResultRow("Status", ref.status)
                HvacResultRow("Application", ref.typicalApp)
            }
        }

        HvacSectionLabel("Saturation Pressure Lookup")
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HvacField("Temperature (C)", tempText, { tempText = it }, Modifier.weight(1f))
            Button(onClick = {
                val t = tempText.toDoubleOrNull()
                calcResult = if (t != null)
                    "%.3f bar  /  %.2f psia".format(ref.psatAtTemp(t), ref.psatAtTemp(t) * 14.504)
                else "Invalid temperature"
            }) { Text("Lookup") }
        }
        calcResult?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }

        HvacSectionLabel("Saturation Pressure Table (bar) — every 10 C")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp).horizontalScroll(rememberScrollState())) {
                Row {
                    RefTableCell("T (C)", true, 56)
                    refrigerants.forEach { r -> RefTableCell(r.name.substringBefore(" ("), true, 76) }
                }
                HorizontalDivider()
                temps.forEach { t ->
                    if (t % 10 == 0) {
                        Row {
                            RefTableCell("$t", false, 56)
                            refrigerants.forEach { r -> RefTableCell("%.2f".format(r.psatAtTemp(t.toDouble())), false, 76) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RefTableCell(text: String, isHeader: Boolean, widthDp: Int) {
    Text(
        text = text,
        modifier = Modifier.width(widthDp.dp).padding(horizontal = 4.dp, vertical = 2.dp),
        style = if (isHeader) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
    )
}

// ── Tab 12: PMV / Thermal Comfort (ASHRAE 55 / ISO 7730) ─────────────────────

@Composable
private fun PmvTab() {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val uc = UnitConverter
    val tUnit = uc.tempUnit(unitSystem)

    val activities = listOf(
        "Sleeping / reclining"           to 0.8,
        "Seated, relaxed (office)"       to 1.0,
        "Seated, light work"             to 1.1,
        "Standing, light activity"       to 1.2,
        "Walking 3 km/h"                 to 2.0,
        "Walking 5 km/h"                 to 2.6,
        "Moderate exercise"              to 3.0,
    )
    val clothings = listOf(
        "Naked"                                     to 0.0,
        "Light summer (T-shirt, shorts)"            to 0.3,
        "Typical summer (light shirt, trousers)"    to 0.5,
        "Typical office (shirt, suit jacket)"       to 1.0,
        "Typical winter (suit + coat)"              to 1.5,
        "Heavy winter (overcoat, boots)"            to 2.0,
    )

    var ta     by remember { mutableStateOf(uc.defaultTemp(23.0, unitSystem)) }
    var tr     by remember { mutableStateOf(uc.defaultTemp(24.0, unitSystem)) }
    var va     by remember { mutableStateOf("0.2") }
    var rh     by remember { mutableStateOf("50") }
    var metIdx by remember { mutableIntStateOf(1) }
    var cloIdx by remember { mutableIntStateOf(2) }
    var actExp by remember { mutableStateOf(false) }
    var cloExp by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PmvCalc.Result?>(null) }
    var error  by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(unitSystem) {
        ta = uc.defaultTemp(23.0, unitSystem); tr = uc.defaultTemp(24.0, unitSystem)
        result = null; error = null
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ASHRAE 55 Thermal Comfort — PMV / PPD",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("ISO 7730 Fanger model. PMV: −3 (cold) → +3 (hot). ASHRAE 55 requires |PMV| ≤ 0.5 (Cat. B) for most occupied spaces.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        HvacSectionLabel("Air Conditions")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Air Temp ($tUnit)", ta, { ta = it }, Modifier.weight(1f))
            HvacField("Mean Radiant Temp ($tUnit)", tr, { tr = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Air Velocity (m/s)", va, { va = it }, Modifier.weight(1f))
            HvacField("Relative Humidity (%)", rh, { rh = it }, Modifier.weight(1f))
        }
        Text("MRT ≈ air temp for well-insulated rooms. Min velocity: 0.1 m/s (still air).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        HvacSectionLabel("Occupant Parameters")
        ExposedDropdownMenuBox(expanded = actExp, onExpandedChange = { actExp = it }) {
            OutlinedTextField(
                value = "${activities[metIdx].first}  (${activities[metIdx].second} met)",
                onValueChange = {}, readOnly = true,
                label = { Text("Activity / Metabolic Rate") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(actExp) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = actExp, onDismissRequest = { actExp = false }) {
                activities.forEachIndexed { i, (name, m) ->
                    DropdownMenuItem(text = { Text("$name  ($m met)") }, onClick = { metIdx = i; actExp = false })
                }
            }
        }
        ExposedDropdownMenuBox(expanded = cloExp, onExpandedChange = { cloExp = it }) {
            OutlinedTextField(
                value = "${clothings[cloIdx].first}  (${clothings[cloIdx].second} clo)",
                onValueChange = {}, readOnly = true,
                label = { Text("Clothing Insulation Level") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cloExp) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = cloExp, onDismissRequest = { cloExp = false }) {
                clothings.forEachIndexed { i, (name, c) ->
                    DropdownMenuItem(text = { Text("$name  ($c clo)") }, onClick = { cloIdx = i; cloExp = false })
                }
            }
        }

        Button(
            onClick = {
                error = null; result = null
                runCatching {
                    val taC = uc.inputTemp(ta.toDouble(), unitSystem)
                    val trC = uc.inputTemp(tr.toDouble(), unitSystem)
                    PmvCalc.calculate(
                        ta  = taC,
                        tr  = trC,
                        va  = va.toDouble().coerceAtLeast(0.0),
                        rh  = rh.toDouble(),
                        met = activities[metIdx].second,
                        clo = clothings[cloIdx].second,
                    )
                }.onSuccess { result = it }.onFailure { e -> error = e.message ?: "Calculation error" }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Icon(Icons.Default.Calculate, null); Spacer(Modifier.width(8.dp)); Text("Calculate PMV / PPD") }

        error?.let { ErrorCard(it) }

        result?.let { r ->
            val pmvColor = when {
                abs(r.pmv) <= 0.2 -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
                abs(r.pmv) <= 0.5 -> MaterialTheme.colorScheme.primary
                abs(r.pmv) <= 0.7 -> androidx.compose.ui.graphics.Color(0xFFF57F17)
                else               -> MaterialTheme.colorScheme.error
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Comfort Result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("PMV  %.2f".format(r.pmv),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold, color = pmvColor)
                    }
                    HorizontalDivider()
                    HvacResultRow("Thermal Sensation", PmvCalc.sensation(r.pmv))
                    HvacResultRow("PPD", "%.1f %%  dissatisfied".format(r.ppd))
                    HvacResultRow("ISO 7730 Category", PmvCalc.category(r.pmv))
                    HorizontalDivider()
                    HvacResultRow("Clothing surface temp", "%.1f %s".format(uc.displayTemp(r.tClothes, unitSystem), tUnit))
                    HvacResultRow("Convective coefficient hc", "%.2f W/m²K".format(r.hConv))
                    HorizontalDivider()
                    Text(
                        "ASHRAE 55-2023: Cat A |PMV|<0.2 · Cat B |PMV|<0.5 · Cat C |PMV|<0.7",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ASHRAE 55 Comfort Zones (1.0 met, 0.5 clo, still air)",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Text("Summer: 23–27°C  ·  Winter: 20–23.5°C  ·  Max W = 0.012 kg/kg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Enable 'ASHRAE 55' layer on the Chart tab to see the comfort zone plotted.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Tab 13: VAV Box / Reheat Coil Sizing ──────────────────────────────────────

@Composable
private fun VavReheatTab() {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val uc   = UnitConverter
    val tUnit = uc.tempUnit(unitSystem)
    val isIp  = unitSystem == UnitSystem.IP

    var roomCool  by remember { mutableStateOf(uc.defaultTemp(26.0, unitSystem)) }
    var supCool   by remember { mutableStateOf(uc.defaultTemp(13.0, unitSystem)) }
    var qCool     by remember { mutableStateOf("5.0") }
    var roomHeat  by remember { mutableStateOf(uc.defaultTemp(21.0, unitSystem)) }
    var qHeat     by remember { mutableStateOf("2.0") }
    var minPct    by remember { mutableStateOf("30") }
    var result    by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var error     by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(unitSystem) {
        roomCool = uc.defaultTemp(26.0, unitSystem); supCool = uc.defaultTemp(13.0, unitSystem)
        roomHeat = uc.defaultTemp(21.0, unitSystem); result = null; error = null
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("VAV Box / Reheat Coil Sizing",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Calculates primary design airflow, minimum VAV position, and the electric or hot-water reheat coil capacity needed to maintain heating setpoint.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        HvacSectionLabel("Cooling Design")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Room DBT ($tUnit)", roomCool, { roomCool = it }, Modifier.weight(1f))
            HvacField("AHU Supply DBT ($tUnit)", supCool, { supCool = it }, Modifier.weight(1f))
        }
        HvacField("Zone Sensible Cooling Load (kW)", qCool, { qCool = it })
        Text("Typical cooling supply: 12–14 °C (54–57 °F).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        HvacSectionLabel("Heating Design")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Room Heating Setpoint ($tUnit)", roomHeat, { roomHeat = it }, Modifier.weight(1f))
            HvacField("Zone Heating Load (kW)", qHeat, { qHeat = it }, Modifier.weight(1f))
        }

        HvacSectionLabel("VAV Minimum Position")
        HvacField("Minimum Flow Fraction (%)", minPct, { minPct = it })
        Text("ASHRAE 90.1 / local code: typically 20–30 % for heating-dominant zones. Ventilation minimum may govern.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Button(
            onClick = {
                error = null; result = null
                runCatching {
                    val rcC  = uc.inputTemp(roomCool.toDouble(), unitSystem)
                    val scC  = uc.inputTemp(supCool.toDouble(), unitSystem)
                    val rhC  = uc.inputTemp(roomHeat.toDouble(), unitSystem)
                    val qs   = qCool.toDouble()
                    val qh   = qHeat.toDouble()
                    val fMin = minPct.toDouble() / 100.0
                    require(rcC > scC) { "Room DBT (cooling) must be above supply DBT." }
                    require(fMin in 0.05..1.0) { "Minimum fraction must be 5–100%." }
                    val cp      = 1.006
                    val dtCool  = rcC - scC
                    val mMax    = qs / (cp * dtCool)       // kg/s
                    val mMin    = mMax * fMin
                    val vMax    = mMax * 0.835             // m³/s  (specific vol ≈ 0.835 at ~23°C)
                    val vMin    = mMin * 0.835
                    val tSupHeat = rhC + qh / (mMin * cp)
                    val qReheat  = (mMin * cp * (tSupHeat - scC)).coerceAtLeast(0.0)
                    result = listOf(
                        "Cooling ΔT (Room − Supply)" to "%.1f Δ%s".format(if (isIp) dtCool * 1.8 else dtCool, tUnit),
                        "Design mass flow" to "%.3f kg/s  (%.1f %s)".format(mMax, uc.displayFlow(mMax, unitSystem), uc.flowUnit(unitSystem)),
                        "Design volume flow" to "%.3f m³/s  =  %.0f L/s  =  %.0f CFM".format(vMax, vMax * 1000, vMax * 2118.88),
                        "---" to "",
                        "Min flow fraction" to "%.0f%%".format(fMin * 100),
                        "Min mass flow" to "%.3f kg/s  (%.1f %s)".format(mMin, uc.displayFlow(mMin, unitSystem), uc.flowUnit(unitSystem)),
                        "Min volume flow" to "%.3f m³/s  =  %.0f L/s  =  %.0f CFM".format(vMin, vMin * 1000, vMin * 2118.88),
                        "---" to "",
                        "Required supply temp (heating)" to "%.1f %s".format(uc.displayTemp(tSupHeat, unitSystem), tUnit),
                        "Reheat coil capacity" to "%.2f kW  =  %.0f BTU/hr  =  %.3f TR".format(qReheat, uc.kwToBtuh(qReheat), qReheat / 3.517),
                        "Sensible cooling at min flow" to "%.2f kW".format(mMin * cp * dtCool),
                    )
                }.onFailure { e -> error = e.message ?: "Calculation error" }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Icon(Icons.Default.Calculate, null); Spacer(Modifier.width(8.dp)); Text("Calculate VAV / Reheat") }

        error?.let { ErrorCard(it) }
        result?.let { rows ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("VAV / Reheat Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    rows.forEach { (k, v) ->
                        if (k == "---") HorizontalDivider() else HvacResultRow(k, v)
                    }
                }
            }
        }
    }
}

// ── Tab 14: Multi-Zone Load Summary ───────────────────────────────────────────

private data class ZoneEntry(
    val name: String,
    val areaM2: Double,
    val sensKw: Double,
    val latKw: Double,
)

@Composable
private fun ZoneSummaryTab() {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val uc   = UnitConverter
    val isIp  = unitSystem == UnitSystem.IP
    val aUnit = if (isIp) "ft²" else "m²"

    val zones = remember { mutableStateListOf<ZoneEntry>() }
    var zoneName  by remember { mutableStateOf("Zone 1") }
    var zoneArea  by remember { mutableStateOf("100") }
    var zoneSens  by remember { mutableStateOf("5.0") }
    var zoneLat   by remember { mutableStateOf("1.5") }
    var addError  by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Multi-Zone Building Load Summary",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Add zones one by one. Building totals, peak TR, and overall SHR are calculated automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        HvacSectionLabel("Add Zone")
        HvacField("Zone Name", zoneName, { zoneName = it })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Floor Area ($aUnit)", zoneArea, { zoneArea = it }, Modifier.weight(1f))
            HvacField("Sensible Load (kW)", zoneSens, { zoneSens = it }, Modifier.weight(1f))
        }
        HvacField("Latent Load (kW)", zoneLat, { zoneLat = it })
        addError?.let { ErrorCard(it) }

        Button(
            onClick = {
                addError = null
                val area = zoneArea.toDoubleOrNull()
                val qs   = zoneSens.toDoubleOrNull()
                val ql   = zoneLat.toDoubleOrNull()
                if (area == null || qs == null || ql == null || area <= 0 || qs <= 0 || ql < 0) {
                    addError = "Enter valid positive values."; return@Button
                }
                val aM2 = if (isIp) uc.ft2ToM2(area) else area
                zones.add(ZoneEntry(zoneName.trim().ifBlank { "Zone ${zones.size + 1}" }, aM2, qs, ql))
                zoneName = "Zone ${zones.size + 1}"
                zoneArea = "100"; zoneSens = "5.0"; zoneLat = "1.5"
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add Zone") }

        if (zones.isNotEmpty()) {
            HvacSectionLabel("Zones (${zones.size})")
            zones.forEachIndexed { i, z ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(z.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            val aDisp = if (isIp) uc.m2ToFt2(z.areaM2) else z.areaM2
                            Text(
                                "%.0f $aUnit  ·  S=%.2f kW  ·  L=%.2f kW  ·  Total=%.2f kW  (%.3f TR)".format(
                                    aDisp, z.sensKw, z.latKw, z.sensKw + z.latKw, (z.sensKw + z.latKw) / 3.517),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { zones.removeAt(i) }) {
                            Icon(Icons.Default.Delete, "Remove zone",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Building totals
            val totalSens  = zones.sumOf { it.sensKw }
            val totalLat   = zones.sumOf { it.latKw }
            val totalKw    = totalSens + totalLat
            val totalTr    = totalKw / 3.517
            val totalArea  = zones.sumOf { it.areaM2 }
            val shr        = if (totalKw > 0) totalSens / totalKw else 0.0
            val wPerM2     = if (totalArea > 0) totalKw * 1000 / totalArea else 0.0
            val trPer100m2 = if (totalArea > 0) totalTr / (totalArea / 100.0) else 0.0

            HvacSectionLabel("Building Summary")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Building Totals  (${zones.size} zones)",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    HvacResultRow("Total Sensible Load", "%.2f kW".format(totalSens))
                    HvacResultRow("Total Latent Load",   "%.2f kW".format(totalLat))
                    HvacResultRow("Total Cooling Load",  "%.2f kW  /  %.2f TR".format(totalKw, totalTr))
                    HvacResultRow("Building SHR",        "%.3f".format(shr))
                    HorizontalDivider()
                    val areaDisp = if (isIp) uc.m2ToFt2(totalArea) else totalArea
                    HvacResultRow("Total Floor Area",   "%.0f $aUnit".format(areaDisp))
                    HvacResultRow("Load Density",
                        if (isIp) "%.1f BTU/(hr·ft²)".format(uc.kwToBtuh(totalKw) / uc.m2ToFt2(totalArea))
                        else      "%.0f W/m²".format(wPerM2))
                    HvacResultRow("Cooling Intensity",
                        if (isIp) "%.3f TR/100ft²".format(totalTr / (uc.m2ToFt2(totalArea) / 100.0))
                        else      "%.2f TR/100m²".format(trPer100m2))
                }
            }

            OutlinedButton(
                onClick = { zones.clear(); zoneName = "Zone 1" },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Clear All Zones") }
        }
    }
}

// ── Tab 15: Pump Sizing / System Head ─────────────────────────────────────────

@Composable
private fun PumpSizingTab() {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val isIp = unitSystem == UnitSystem.IP
    val flowUnit = if (isIp) "GPM" else "L/s"
    val headUnit = if (isIp) "ft H₂O" else "m H₂O"
    val lenUnit  = if (isIp) "ft"     else "m"
    val dimUnit  = if (isIp) "in"     else "mm"

    var fluidIdx  by remember { mutableIntStateOf(0) }
    var matIdx    by remember { mutableIntStateOf(1) }
    var nbIdx     by remember { mutableIntStateOf(5) }
    var flowText  by remember { mutableStateOf(if (isIp) "30" else "2.0") }
    var eqLen     by remember { mutableStateOf(if (isIp) "300" else "100") }
    var staticHd  by remember { mutableStateOf("5") }
    var fluidExp  by remember { mutableStateOf(false) }
    var matExp    by remember { mutableStateOf(false) }
    var nbExp     by remember { mutableStateOf(false) }
    var result    by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var error     by remember { mutableStateOf<String?>(null) }

    val stdNb = intArrayOf(15, 20, 25, 32, 40, 50, 65, 80, 100, 125, 150, 200, 250, 300)

    LaunchedEffect(unitSystem) {
        flowText = if (isIp) "30" else "2.0"
        eqLen    = if (isIp) "300" else "100"
        result = null; error = null
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pump Sizing / System Head",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Calculates system head from Darcy-Weisbach. Select the pipe NB already chosen in Pipe Sizing tab, enter total equivalent pipe length (pipes + fittings as equiv. length).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        HvacSectionLabel("Fluid & Pipe")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExposedDropdownMenuBox(expanded = fluidExp, onExpandedChange = { fluidExp = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = PipeFluid.entries[fluidIdx].label, onValueChange = {}, readOnly = true,
                    label = { Text("Fluid") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(fluidExp) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), singleLine = true,
                )
                ExposedDropdownMenu(expanded = fluidExp, onDismissRequest = { fluidExp = false }) {
                    PipeFluid.entries.forEachIndexed { i, f ->
                        DropdownMenuItem(text = { Text(f.label) }, onClick = { fluidIdx = i; fluidExp = false; result = null })
                    }
                }
            }
            ExposedDropdownMenuBox(expanded = matExp, onExpandedChange = { matExp = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = PipeMaterial.entries[matIdx].label, onValueChange = {}, readOnly = true,
                    label = { Text("Material") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(matExp) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), singleLine = true,
                )
                ExposedDropdownMenu(expanded = matExp, onDismissRequest = { matExp = false }) {
                    PipeMaterial.entries.forEachIndexed { i, m ->
                        DropdownMenuItem(text = { Text(m.label) }, onClick = { matIdx = i; matExp = false; result = null })
                    }
                }
            }
        }
        ExposedDropdownMenuBox(expanded = nbExp, onExpandedChange = { nbExp = it }) {
            val nbDisp = if (isIp) "NB ${stdNb[nbIdx]} mm  (%.2f in)".format(stdNb[nbIdx] / 25.4) else "NB ${stdNb[nbIdx]} mm"
            OutlinedTextField(
                value = nbDisp, onValueChange = {}, readOnly = true,
                label = { Text("Pipe Nominal Bore") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(nbExp) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = nbExp, onDismissRequest = { nbExp = false }) {
                stdNb.forEachIndexed { i, nb ->
                    val lbl = if (isIp) "NB $nb mm  (%.2f in)".format(nb / 25.4) else "NB $nb mm"
                    DropdownMenuItem(text = { Text(lbl) }, onClick = { nbIdx = i; nbExp = false; result = null })
                }
            }
        }

        HvacSectionLabel("System Inputs")
        HvacField("Flow Rate ($flowUnit)", flowText, { flowText = it })
        HvacField("Total Equivalent Length ($lenUnit)", eqLen, { eqLen = it })
        Text("Include straight pipe + equivalent lengths of all fittings, valves, and coils.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HvacField("Static Head / Elevation ($headUnit)", staticHd, { staticHd = it })
        Text("Height the pump must lift against. Use 0 for a closed loop.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Button(
            onClick = {
                error = null; result = null
                runCatching {
                    val fluid  = PipeFluid.entries[fluidIdx]
                    val mat    = PipeMaterial.entries[matIdx]
                    val nbMm   = stdNb[nbIdx].toDouble()
                    val d      = nbMm * 1e-3
                    val qRaw   = flowText.toDouble()
                    val lenRaw = eqLen.toDouble()
                    val hdRaw  = staticHd.toDouble()
                    val qLs    = if (isIp) qRaw * 0.0630902 else qRaw          // → L/s
                    val lenM   = if (isIp) lenRaw * 0.3048 else lenRaw         // → m
                    val hdM    = if (isIp) hdRaw * 0.3048 else hdRaw           // → m
                    val q      = qLs * 1e-3                                     // m³/s
                    val area   = Math.PI * d * d / 4.0
                    val v      = q / area
                    val re     = fluid.rho * v * d / fluid.mu
                    val eps    = mat.eps
                    val arg    = eps / (3.7 * d) + 5.74 / re.pow(0.9)
                    val f      = 0.25 / log10(arg).pow(2)
                    val dpPerM = f * fluid.rho * v * v / (2.0 * d)             // Pa/m
                    val dpTotal = dpPerM * lenM                                 // Pa
                    val rhoG   = fluid.rho * 9.81
                    val frictionHead = dpTotal / rhoG                           // m H₂O
                    val totalHead    = frictionHead + hdM
                    val powerKw      = fluid.rho * q * 9.81 * totalHead / 1000.0
                    val dispFric = if (isIp) frictionHead / 0.3048 else frictionHead
                    val dispStat = if (isIp) hdM / 0.3048 else hdM
                    val dispTot  = if (isIp) totalHead / 0.3048 else totalHead
                    val dispVel  = if (isIp) v * 196.85 else v
                    val velUnit2 = if (isIp) "FPM" else "m/s"
                    result = listOf(
                        "Pipe NB" to "${stdNb[nbIdx]} mm" + if (isIp) "  (%.2f in)".format(stdNb[nbIdx] / 25.4) else "",
                        "Flow velocity" to "%.2f $velUnit2".format(dispVel),
                        "Reynolds number" to "%.0f".format(re),
                        "Darcy friction factor" to "%.5f".format(f),
                        "Friction loss rate" to "%.1f Pa/m  (%.3f ${ if (isIp) "ft/100ft" else "Pa/m" })".format(dpPerM, if (isIp) dpPerM / 98.06 else dpPerM),
                        "---" to "",
                        "Friction head" to "%.2f $headUnit".format(dispFric),
                        "Static head" to "%.2f $headUnit".format(dispStat),
                        "Total system head" to "%.2f $headUnit".format(dispTot),
                        "Pump duty" to "%.2f $flowUnit  @  %.2f $headUnit".format(qRaw, dispTot),
                        "Hydraulic power" to "%.2f kW  (%.2f HP)".format(powerKw, powerKw * 1.341),
                    )
                }.onFailure { e -> error = e.message ?: "Calculation error" }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Icon(Icons.Default.Calculate, null); Spacer(Modifier.width(8.dp)); Text("Calculate System Head") }

        error?.let { ErrorCard(it) }
        result?.let { rows ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pump Sizing Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    rows.forEach { (k, v) ->
                        if (k == "---") HorizontalDivider() else HvacResultRow(k, v)
                    }
                }
            }
        }
    }
}

// ── Tab 16: Cooling Coil Selection (face-area method) ─────────────────────────

@Composable
private fun CoilSelectTab() {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val uc    = UnitConverter
    val isIp  = unitSystem == UnitSystem.IP
    val tUnit = uc.tempUnit(unitSystem)
    val aUnit = if (isIp) "ft²" else "m²"
    val vUnit = if (isIp) "FPM" else "m/s"
    val mUnit = if (isIp) "lb/min" else "kg/s"

    var entDbt  by remember { mutableStateOf(uc.defaultTemp(27.0, unitSystem)) }
    var entRh   by remember { mutableStateOf("55") }
    var lvgDbt  by remember { mutableStateOf(uc.defaultTemp(13.0, unitSystem)) }
    var lvgRh   by remember { mutableStateOf("95") }
    var mFlow   by remember { mutableStateOf(if (isIp) "100" else "1.0") }
    var faceArea by remember { mutableStateOf(if (isIp) "10.0" else "1.0") }
    var result  by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var error   by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(unitSystem) {
        entDbt = uc.defaultTemp(27.0, unitSystem); lvgDbt = uc.defaultTemp(13.0, unitSystem)
        mFlow  = if (isIp) "100" else "1.0"; faceArea = if (isIp) "10.0" else "1.0"
        result = null; error = null
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Cooling Coil Selection",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Calculates coil duty, ADP, bypass factor, face velocity, and estimated row count from entering/leaving air conditions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        HvacSectionLabel("Entering Air")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Entering DBT ($tUnit)", entDbt, { entDbt = it }, Modifier.weight(1f))
            HvacField("Entering RH (%)", entRh, { entRh = it }, Modifier.weight(1f))
        }
        HvacSectionLabel("Leaving Air")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Leaving DBT ($tUnit)", lvgDbt, { lvgDbt = it }, Modifier.weight(1f))
            HvacField("Leaving RH (%)", lvgRh, { lvgRh = it }, Modifier.weight(1f))
        }
        HvacSectionLabel("Airflow & Coil Face Area")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Mass Flow ($mUnit)", mFlow, { mFlow = it }, Modifier.weight(1f))
            HvacField("Face Area ($aUnit)", faceArea, { faceArea = it }, Modifier.weight(1f))
        }
        Text("Typical face velocity: 2.0–2.8 m/s (400–550 FPM). Above 3 m/s risks moisture carry-over.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Button(
            onClick = {
                error = null; result = null
                runCatching {
                    val entDbtC = uc.inputTemp(entDbt.toDouble(), unitSystem)
                    val lvgDbtC = uc.inputTemp(lvgDbt.toDouble(), unitSystem)
                    require(lvgDbtC < entDbtC) { "Leaving DBT must be less than entering DBT." }
                    val entRhVal = entRh.toDouble()
                    val lvgRhVal = lvgRh.toDouble()
                    val mKgs     = uc.inputFlow(mFlow.toDouble(), unitSystem)  // kg/s
                    val aM2      = if (isIp) uc.ft2ToM2(faceArea.toDouble()) else faceArea.toDouble()
                    val sEnt     = PsychroCalc.fromDbtRh(entDbtC, entRhVal)
                    val sLvg     = PsychroCalc.fromDbtRh(lvgDbtC, lvgRhVal)
                    val qTotal   = mKgs * (sEnt.h - sLvg.h)
                    val qSens    = mKgs * 1.006 * (entDbtC - lvgDbtC)
                    val qLat     = qTotal - qSens
                    val shrCoil  = qSens / qTotal
                    // Bypass factor from contact factor definition
                    // BF = (Tlvg - TADP) / (Tent - TADP);  ADP is on saturation curve
                    // Binary search for ADP
                    var adpLo = -15.0; var adpHi = lvgDbtC - 0.01
                    val shrSlope = 1.006 * (1.0 - shrCoil) / (2501.0 * shrCoil)
                    repeat(60) {
                        val mid   = (adpLo + adpHi) / 2.0
                        val wLine = sLvg.w + shrSlope * (mid - lvgDbtC)
                        val wSat  = PsychroCalc.humRatioFromRelHum(mid, 1.0)
                        if (wLine > wSat) adpLo = mid else adpHi = mid
                    }
                    val tAdp  = (adpLo + adpHi) / 2.0
                    val bf    = (lvgDbtC - tAdp) / (entDbtC - tAdp).coerceAtLeast(0.001)
                    val cf    = 1.0 - bf
                    val estRows = when {
                        bf >= 0.45 -> 2
                        bf >= 0.25 -> 4
                        bf >= 0.10 -> 6
                        bf >= 0.04 -> 8
                        else       -> 10
                    }
                    val vFaceMs = mKgs * sEnt.v / aM2
                    val vFaceDisp = if (isIp) vFaceMs * 196.85 else vFaceMs
                    val dispQTotal = if (isIp) uc.kwToBtuh(qTotal) else qTotal * 1000
                    val dispQSens  = if (isIp) uc.kwToBtuh(qSens)  else qSens * 1000
                    val dispQLatent = if (isIp) uc.kwToBtuh(qLat) else qLat * 1000
                    val dispUnit   = if (isIp) "BTU/hr" else "W"
                    result = listOf(
                        "Total coil duty"     to "%.0f $dispUnit  (%.2f kW  /  %.3f TR)".format(dispQTotal, qTotal, qTotal / 3.517),
                        "Sensible heat"       to "%.0f $dispUnit".format(dispQSens),
                        "Latent heat"         to "%.0f $dispUnit".format(dispQLatent),
                        "Coil SHR"            to "%.3f".format(shrCoil),
                        "---" to "",
                        "ADP (Apparatus Dew Point)" to "%.1f %s".format(uc.displayTemp(tAdp, unitSystem), tUnit),
                        "Bypass Factor (BF)"  to "%.3f".format(bf),
                        "Contact Factor (CF)" to "%.3f".format(cf),
                        "Estimated rows"      to "$estRows rows deep",
                        "---" to "",
                        "Face velocity"       to "%.2f $vUnit".format(vFaceDisp) +
                            if (vFaceMs > 3.0) "  ⚠ carry-over risk" else if (vFaceMs < 1.8) "  ⚠ low — may drop moisture" else "  ✓ OK",
                        "Face area"           to "%.2f $aUnit".format(if (isIp) uc.m2ToFt2(aM2) else aM2),
                    )
                }.onFailure { e -> error = e.message ?: "Calculation error" }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Icon(Icons.Default.Calculate, null); Spacer(Modifier.width(8.dp)); Text("Calculate Coil Duty") }

        error?.let { ErrorCard(it) }
        result?.let { rows ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Coil Selection Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    rows.forEach { (k, v) ->
                        if (k == "---") HorizontalDivider() else HvacResultRow(k, v)
                    }
                }
            }
        }
    }
}

// ── Tab 17: ASHRAE 90.1 Equipment Sizing Check ────────────────────────────────

@Composable
private fun EquipCheckTab() {
    val uc = UnitConverter
    val systemTypes = listOf(
        "Single-zone RTU / Split System",
        "Multi-zone VAV AHU",
        "Fan Coil Unit (FCU)",
        "Chiller + AHU",
        "VRF Outdoor Unit",
    )
    var sysIdx     by remember { mutableIntStateOf(0) }
    var sysExp     by remember { mutableStateOf(false) }
    var peakSens   by remember { mutableStateOf("50.0") }
    var peakTotal  by remember { mutableStateOf("65.0") }
    var instCool   by remember { mutableStateOf("80.0") }
    var peakHeat   by remember { mutableStateOf("25.0") }
    var instHeat   by remember { mutableStateOf("40.0") }
    var result     by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var error      by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ASHRAE 90.1 Equipment Sizing Check",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Checks cooling and heating oversizing ratios. ASHRAE 90.1-2022 §6.3: cooling ≤ 125% of peak, heating ≤ 140% of peak.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        HvacSectionLabel("System Type")
        ExposedDropdownMenuBox(expanded = sysExp, onExpandedChange = { sysExp = it }) {
            OutlinedTextField(
                value = systemTypes[sysIdx], onValueChange = {}, readOnly = true,
                label = { Text("System Type") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sysExp) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = sysExp, onDismissRequest = { sysExp = false }) {
                systemTypes.forEachIndexed { i, s ->
                    DropdownMenuItem(text = { Text(s) }, onClick = { sysIdx = i; sysExp = false })
                }
            }
        }

        HvacSectionLabel("Cooling")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Peak Sensible Load (kW)", peakSens, { peakSens = it }, Modifier.weight(1f))
            HvacField("Peak Total Load (kW)", peakTotal, { peakTotal = it }, Modifier.weight(1f))
        }
        HvacField("Installed Cooling Capacity (kW)", instCool, { instCool = it })

        HvacSectionLabel("Heating")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HvacField("Peak Heating Load (kW)", peakHeat, { peakHeat = it }, Modifier.weight(1f))
            HvacField("Installed Heating Capacity (kW)", instHeat, { instHeat = it }, Modifier.weight(1f))
        }

        Button(
            onClick = {
                error = null; result = null
                runCatching {
                    val qs  = peakSens.toDouble()
                    val qt  = peakTotal.toDouble()
                    val ic  = instCool.toDouble()
                    val qh  = peakHeat.toDouble()
                    val ih  = instHeat.toDouble()
                    require(qt >= qs) { "Total load must be ≥ sensible load." }
                    val coolRatio = ic / qt
                    val heatRatio = ih / qh
                    val coolLimit  = 1.25
                    val heatLimit  = 1.40
                    val coolOk     = coolRatio <= coolLimit
                    val heatOk     = heatRatio <= heatLimit
                    val coolStatus = if (coolOk) "PASS  (%.0f%% of peak)".format(coolRatio * 100)
                                     else         "FAIL  (%.0f%% > 125%%)".format(coolRatio * 100)
                    val heatStatus = if (heatOk) "PASS  (%.0f%% of peak)".format(heatRatio * 100)
                                     else         "FAIL  (%.0f%% > 140%%)".format(heatRatio * 100)
                    val maxAllowCool = qt * coolLimit
                    val maxAllowHeat = qh * heatLimit
                    result = listOf(
                        "System type" to systemTypes[sysIdx],
                        "---" to "",
                        "Peak sensible load" to "%.1f kW  (%.2f TR)".format(qs, qs / 3.517),
                        "Peak total cooling load" to "%.1f kW  (%.2f TR)".format(qt, qt / 3.517),
                        "Installed cooling capacity" to "%.1f kW  (%.2f TR)".format(ic, ic / 3.517),
                        "Cooling sizing ratio" to "%.2f×  (%.0f%%)".format(coolRatio, coolRatio * 100),
                        "Max allowable (125%)" to "%.1f kW  (%.2f TR)".format(maxAllowCool, maxAllowCool / 3.517),
                        "Cooling check (ASHRAE 90.1 §6.3)" to coolStatus,
                        "---" to "",
                        "Peak heating load" to "%.1f kW  (%.0f BTU/hr)".format(qh, uc.kwToBtuh(qh)),
                        "Installed heating capacity" to "%.1f kW  (%.0f BTU/hr)".format(ih, uc.kwToBtuh(ih)),
                        "Heating sizing ratio" to "%.2f×  (%.0f%%)".format(heatRatio, heatRatio * 100),
                        "Max allowable (140%)" to "%.1f kW".format(maxAllowHeat),
                        "Heating check (ASHRAE 90.1 §6.3)" to heatStatus,
                    )
                }.onFailure { e -> error = e.message ?: "Calculation error" }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Icon(Icons.Default.Calculate, null); Spacer(Modifier.width(8.dp)); Text("Check Equipment Sizing") }

        error?.let { ErrorCard(it) }
        result?.let { rows ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ASHRAE 90.1 Sizing Check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    rows.forEach { (k, v) ->
                        if (k == "---") { HorizontalDivider() } else {
                            val isPass = v.startsWith("PASS")
                            val isFail = v.startsWith("FAIL")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(k,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    modifier = Modifier.weight(1f))
                                Text(v,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when {
                                        isPass -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
                                        isFail -> MaterialTheme.colorScheme.error
                                        else   -> MaterialTheme.colorScheme.onPrimaryContainer
                                    })
                            }
                        }
                    }
                }
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("ASHRAE 90.1-2022 §6.3 Reference",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text("Cooling: Installed capacity ≤ 125% of the peak block cooling load.\nHeating: Installed capacity ≤ 140% of the peak block heating load.\nExemptions apply to heat pumps in heating mode, electric resistance when primary, and certain climate zones.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Tab 18: Cooling Tower Makeup Water ────────────────────────────────────────

@Composable
private fun CtWaterTab() {
    val unitSystem by AppSettings.unitSystem.collectAsState()
    val isIp = unitSystem == UnitSystem.IP
    val flowUnit = if (isIp) "GPM" else "L/s"
    val tempUnit = if (isIp) "°F"  else "°C"

    var flowText by remember { mutableStateOf(if (isIp) "200" else "12") }
    var rangeText by remember { mutableStateOf(if (isIp) "10" else "5.5") }
    var cocText   by remember { mutableStateOf("4.0") }
    var result    by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var error     by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(unitSystem) {
        flowText  = if (isIp) "200" else "12"
        rangeText = if (isIp) "10" else "5.5"
        result = null; error = null
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Cooling Tower Makeup Water",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Calculates evaporation loss, blowdown, drift, and total makeup water flow from circulation rate, temperature range, and cycles of concentration.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        HvacSectionLabel("Tower Conditions")
        HvacField("Condenser Water Circulation Flow ($flowUnit)", flowText, { flowText = it })
        HvacField("Tower Range (Δ$tempUnit) — entering minus leaving water temp", rangeText, { rangeText = it })
        HvacField("Cycles of Concentration (COC)", cocText, { cocText = it })
        Text("COC = TDS in circulating water / TDS in makeup water. Typical design: 3–6 for quality water supply. Below 3 wastes water; above 6 risks scale.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Button(
            onClick = {
                error = null; result = null
                runCatching {
                    val qRaw   = flowText.toDouble()
                    val rngRaw = rangeText.toDouble()
                    val coc    = cocText.toDouble()
                    require(coc > 1.0) { "COC must be > 1.0." }
                    // Convert to SI: L/s and °C
                    val qLs    = if (isIp) qRaw * 0.0630902 else qRaw
                    val rngC   = if (isIp) rngRaw * 5.0 / 9.0 else rngRaw
                    // Evaporation: energy balance  E × L_evap = ρ × Q × Cp × ΔT
                    // E (L/s) = Q(L/s) × 4.186 × ΔT / 2430
                    val evapLs = qLs * 4.186 * rngC / 2430.0
                    val evapPct = evapLs / qLs * 100.0
                    val driftLs = qLs * 0.0002   // 0.02% of circulation (drift eliminator)
                    val blowdownLs = evapLs / (coc - 1.0) - driftLs
                    val makeupLs   = evapLs + blowdownLs.coerceAtLeast(0.0) + driftLs

                    fun dispQ(ls: Double) = if (isIp) "%.2f GPM".format(ls / 0.0630902) else "%.3f L/s".format(ls)

                    result = listOf(
                        "Circulation flow" to dispQ(qLs),
                        "Tower range" to "%.1f %s".format(rngRaw, tempUnit),
                        "COC" to "%.1f".format(coc),
                        "---" to "",
                        "Evaporation loss" to "${dispQ(evapLs)}  (%.2f%% of flow)".format(evapPct),
                        "Drift loss" to "${dispQ(driftLs)}  (0.02% — modern eliminators)",
                        "Blowdown required" to dispQ(blowdownLs.coerceAtLeast(0.0)),
                        "Total makeup water" to dispQ(makeupLs),
                        "---" to "",
                        "Annual makeup (m³/yr)" to "%.0f m³/yr".format(makeupLs * 3.6 * 8760),
                        "Tip: raise COC by 1" to "saves ≈ %.0f m³/yr".format(
                            (evapLs / (coc - 1.0) - evapLs / coc) * 3.6 * 8760),
                    )
                }.onFailure { e -> error = e.message ?: "Calculation error" }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Icon(Icons.Default.Calculate, null); Spacer(Modifier.width(8.dp)); Text("Calculate Makeup Water") }

        error?.let { ErrorCard(it) }
        result?.let { rows ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cooling Tower Water Balance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    rows.forEach { (k, v) ->
                        if (k == "---") HorizontalDivider() else HvacResultRow(k, v)
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Water Treatment Guide",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Text("COC 2–3: marginal efficiency, high water waste.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("COC 4–6: good balance of water savings vs scale risk for typical water quality.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("COC > 6: maximum savings but requires chemical treatment and monitoring.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Drift eliminators should achieve < 0.002% drift. Replace if > 0.05%.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
