package com.psychrochart.app.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.psychrochart.app.domain.*
import com.psychrochart.app.ui.components.StateResultCard
import com.psychrochart.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatePointScreen(vm: MainViewModel) {
    val stateResult by vm.stateResult.collectAsState()
    val stateError  by vm.stateError.collectAsState()
    val unitSystem  by AppSettings.unitSystem.collectAsState()
    val uc = UnitConverter

    var dbtText    by remember { mutableStateOf(uc.defaultTemp(25.0, unitSystem)) }
    var secValue   by remember { mutableStateOf("50") }
    var selected   by remember { mutableStateOf(SecondaryInput.RH) }
    var expanded   by remember { mutableStateOf(false) }
    var pointLabel by remember { mutableStateOf("") }
    var showCityPicker    by remember { mutableStateOf(false) }
    var citySearch        by remember { mutableStateOf("") }
    // 0 = Summer, 1 = Monsoon/DP, 2 = Winter
    var citySeasonMode by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("city_favourites", Context.MODE_PRIVATE) }
    var favourites by remember {
        mutableStateOf(prefs.getStringSet("fav", emptySet())?.toSet() ?: emptySet())
    }

    // Re-init DBT default when unit system changes
    LaunchedEffect(unitSystem) {
        dbtText  = uc.defaultTemp(25.0, unitSystem)
        secValue = defaultValue(selected, unitSystem)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("State Point Calculator",
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = { showCityPicker = true; citySearch = "" },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Default.LocationCity, contentDescription = null,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("City")
            }
        }

        // ── Input card ─────────────────────────────────────────────────────────
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Air Conditions",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = dbtText,
                        onValueChange = { dbtText = it },
                        label = { Text("DBT (${uc.tempUnit(unitSystem)})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.weight(1.4f),
                    ) {
                        OutlinedTextField(
                            value = secondaryShortLabel(selected, unitSystem),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("2nd Input") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            singleLine = true,
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            SecondaryInput.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(secondaryLabel(opt)) },
                                    onClick = {
                                        selected  = opt
                                        secValue  = defaultValue(opt, unitSystem)
                                        expanded  = false
                                    },
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = secValue,
                    onValueChange = { secValue = it },
                    label = { Text("${secondaryLabel(selected)}  (${secondaryUnit(selected, unitSystem)})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = pointLabel,
                    onValueChange = { pointLabel = it },
                    label = { Text("Point Label (optional)") },
                    placeholder = { Text("e.g. OA, RA, SA, Room") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        val dbtRaw = dbtText.toDoubleOrNull() ?: return@Button
                        val secRaw = secValue.toDoubleOrNull() ?: return@Button
                        val dbtSi  = uc.inputTemp(dbtRaw, unitSystem)
                        val secSi  = convertSecondaryToSi(selected, secRaw, unitSystem)
                        vm.calculateState(dbtSi, selected, secSi,
                            pointLabel = pointLabel.trim().ifBlank { null })
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Calculate  &  Plot on Chart")
                }
            }
        }

        // ── Error ──────────────────────────────────────────────────────────────
        stateError?.let { err ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = MaterialTheme.shapes.medium) {
                Text(err, modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }

        // ── Result ─────────────────────────────────────────────────────────────
        stateResult?.let { state ->
            StateResultCard(state = state, title = "Calculated State")
        }
    }

    // ── City Picker Dialog ─────────────────────────────────────────────────────
    if (showCityPicker) {
        val filtered = ashraeCities
            .filter { city ->
                citySearch.isBlank() ||
                city.name.contains(citySearch, ignoreCase = true) ||
                city.country.contains(citySearch, ignoreCase = true)
            }
            .sortedWith(compareByDescending { favourites.contains(it.name) })
        AlertDialog(
            onDismissRequest = { showCityPicker = false },
            title = { Text("ASHRAE Outdoor Design Conditions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = citySearch,
                        onValueChange = { citySearch = it },
                        label = { Text("Search city…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = citySeasonMode == 0,
                            onClick  = { citySeasonMode = 0 },
                            label    = { Text("Summer") },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE53935).copy(alpha = 0.15f),
                                selectedLabelColor     = Color(0xFFE53935),
                            ),
                        )
                        FilterChip(
                            selected = citySeasonMode == 1,
                            onClick  = { citySeasonMode = 1 },
                            label    = { Text("Monsoon") },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF43A047).copy(alpha = 0.15f),
                                selectedLabelColor     = Color(0xFF43A047),
                            ),
                        )
                        FilterChip(
                            selected = citySeasonMode == 2,
                            onClick  = { citySeasonMode = 2 },
                            label    = { Text("Winter") },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1E88E5).copy(alpha = 0.15f),
                                selectedLabelColor     = Color(0xFF1E88E5),
                            ),
                        )
                    }
                    Text(
                        when (citySeasonMode) {
                            0    -> "1% cooling — fills DBT + coincident WBT (${uc.tempUnit(unitSystem)})"
                            1    -> "1% dew-point — fills coincident DBT + DPT (${uc.tempUnit(unitSystem)})"
                            else -> "99.6% heating — fills DBT (${uc.tempUnit(unitSystem)}) + RH 80%"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        filtered.forEach { city ->
                            val sumDBT = uc.displayTemp(city.summerDbt, unitSystem)
                            val sumWBT = uc.displayTemp(city.summerWbt, unitSystem)
                            val winDBT = uc.displayTemp(city.winterDbt, unitSystem)
                            val monDP  = uc.displayTemp(city.dehumidDpt, unitSystem)
                            val monDBT = uc.displayTemp(city.dehumidDbt, unitSystem)
                            val tU = uc.tempUnit(unitSystem)
                            val isFav = favourites.contains(city.name)
                            ListItem(
                                headlineContent = { Text(city.name, fontWeight = FontWeight.Medium) },
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            val updated = if (isFav) favourites - city.name
                                                          else       favourites + city.name
                                            favourites = updated
                                            prefs.edit().putStringSet("fav", updated).apply()
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            if (isFav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                            contentDescription = "Favourite",
                                            tint = if (isFav) Color(0xFFFFC107) else Color(0xFFB0BEC5),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                                supportingContent = {
                                    Text(
                                        when (citySeasonMode) {
                                            0    -> "Sum: %.0f/%.0f %s  Win: %.0f %s  Alt: %.0f m"
                                                        .format(sumDBT, sumWBT, tU, winDBT, tU, city.altitudeM)
                                            1    -> "DP: %.0f %s  DBT: %.0f %s  Alt: %.0f m"
                                                        .format(monDP, tU, monDBT, tU, city.altitudeM)
                                            else -> "Win: %.0f %s  Sum: %.0f %s  Alt: %.0f m"
                                                        .format(winDBT, tU, sumDBT, tU, city.altitudeM)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                modifier = Modifier.clickable {
                                    when (citySeasonMode) {
                                        0 -> {
                                            dbtText  = uc.defaultTemp(city.summerDbt, unitSystem)
                                            secValue = uc.defaultTemp(city.summerWbt, unitSystem)
                                            selected = SecondaryInput.WBT
                                        }
                                        1 -> {
                                            dbtText  = uc.defaultTemp(city.dehumidDbt, unitSystem)
                                            secValue = uc.defaultTemp(city.dehumidDpt, unitSystem)
                                            selected = SecondaryInput.DPT
                                        }
                                        else -> {
                                            dbtText  = uc.defaultTemp(city.winterDbt, unitSystem)
                                            secValue = "80"
                                            selected = SecondaryInput.RH
                                        }
                                    }
                                    AppSettings.setAltitude(city.altitudeM)
                                    showCityPicker = false
                                },
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCityPicker = false }) { Text("Close") }
            },
        )
    }
}

// ── Label / unit / default helpers ────────────────────────────────────────────

internal fun secondaryLabel(input: SecondaryInput) = when (input) {
    SecondaryInput.WBT -> "Wet-Bulb Temp"
    SecondaryInput.DPT -> "Dew-Point Temp"
    SecondaryInput.RH  -> "Relative Humidity"
    SecondaryInput.W   -> "Humidity Ratio"
    SecondaryInput.V   -> "Specific Volume"
    SecondaryInput.H   -> "Specific Enthalpy"
}

private fun secondaryShortLabel(input: SecondaryInput, us: UnitSystem) = when (input) {
    SecondaryInput.WBT -> "WBT"
    SecondaryInput.DPT -> "DPT"
    SecondaryInput.RH  -> "RH %"
    SecondaryInput.W   -> if (us == UnitSystem.IP) "W gr/lb" else "W kg/kg"
    SecondaryInput.V   -> if (us == UnitSystem.IP) "v ft³/lb" else "v m³/kg"
    SecondaryInput.H   -> if (us == UnitSystem.IP) "h BTU/lb" else "h kJ/kg"
}

internal fun secondaryUnit(input: SecondaryInput, us: UnitSystem = UnitSystem.SI) = when (input) {
    SecondaryInput.WBT -> UnitConverter.tempUnit(us)
    SecondaryInput.DPT -> UnitConverter.tempUnit(us)
    SecondaryInput.RH  -> "%"
    SecondaryInput.W   -> UnitConverter.wUnit(us)
    SecondaryInput.V   -> UnitConverter.vUnit(us)
    SecondaryInput.H   -> UnitConverter.hUnit(us)
}

internal fun defaultValue(input: SecondaryInput, us: UnitSystem = UnitSystem.SI): String {
    val uc = UnitConverter
    return when (input) {
        SecondaryInput.RH  -> "50"
        SecondaryInput.WBT -> uc.defaultTemp(18.0, us)
        SecondaryInput.DPT -> uc.defaultTemp(13.0, us)
        SecondaryInput.W   -> uc.defaultW(0.0099, us)
        SecondaryInput.V   -> uc.defaultV(0.855, us)
        SecondaryInput.H   -> uc.defaultH(55.0, us)
    }
}

internal fun convertSecondaryToSi(input: SecondaryInput, value: Double, us: UnitSystem): Double {
    val uc = UnitConverter
    return when (input) {
        SecondaryInput.WBT -> uc.inputTemp(value, us)
        SecondaryInput.DPT -> uc.inputTemp(value, us)
        SecondaryInput.RH  -> value  // % unchanged
        SecondaryInput.W   -> uc.inputW(value, us)
        SecondaryInput.V   -> uc.inputV(value, us)
        SecondaryInput.H   -> uc.inputH(value, us)
    }
}
