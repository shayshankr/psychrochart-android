package com.psychrochart.app.ui.screens

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
import com.psychrochart.app.domain.SecondaryInput
import com.psychrochart.app.ui.components.StateResultCard
import com.psychrochart.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatePointScreen(vm: MainViewModel) {
    val stateResult by vm.stateResult.collectAsState()
    val stateError  by vm.stateError.collectAsState()

    var dbtText  by remember { mutableStateOf("25") }
    var secValue by remember { mutableStateOf("50") }
    var selected by remember { mutableStateOf(SecondaryInput.RH) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("State Point Calculator", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)

        // ── Input card ─────────────────────────────────────────────────────────
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Air Conditions",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                // DBT + secondary type side by side
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = dbtText,
                        onValueChange = { dbtText = it },
                        label = { Text("DBT (°C)") },
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
                            value = secondaryShortLabel(selected),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("2nd Input") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            singleLine = true,
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            SecondaryInput.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(secondaryLabel(opt)) },
                                    onClick = {
                                        selected = opt
                                        secValue = defaultValue(opt)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = secValue,
                    onValueChange = { secValue = it },
                    label = { Text("${secondaryLabel(selected)}  (${secondaryUnit(selected)})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        val dbt = dbtText.toDoubleOrNull() ?: return@Button
                        val sec = secValue.toDoubleOrNull() ?: return@Button
                        vm.calculateState(dbt, selected, sec)
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
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ── Result ─────────────────────────────────────────────────────────────
        stateResult?.let { state ->
            StateResultCard(state = state, title = "Calculated State")
        }
    }
}

internal fun secondaryLabel(input: SecondaryInput) = when (input) {
    SecondaryInput.WBT -> "Wet-Bulb Temp"
    SecondaryInput.DPT -> "Dew-Point Temp"
    SecondaryInput.RH  -> "Relative Humidity"
    SecondaryInput.W   -> "Humidity Ratio"
    SecondaryInput.V   -> "Specific Volume"
    SecondaryInput.H   -> "Specific Enthalpy"
}

private fun secondaryShortLabel(input: SecondaryInput) = when (input) {
    SecondaryInput.WBT -> "WBT"
    SecondaryInput.DPT -> "DPT"
    SecondaryInput.RH  -> "RH %"
    SecondaryInput.W   -> "W kg/kg"
    SecondaryInput.V   -> "v m³/kg"
    SecondaryInput.H   -> "h kJ/kg"
}

internal fun secondaryUnit(input: SecondaryInput) = when (input) {
    SecondaryInput.WBT -> "°C"
    SecondaryInput.DPT -> "°C"
    SecondaryInput.RH  -> "%"
    SecondaryInput.W   -> "kg/kg"
    SecondaryInput.V   -> "m³/kg"
    SecondaryInput.H   -> "kJ/kg"
}

internal fun defaultValue(input: SecondaryInput) = when (input) {
    SecondaryInput.RH  -> "50"
    SecondaryInput.WBT -> "18"
    SecondaryInput.DPT -> "13"
    SecondaryInput.W   -> "0.0099"
    SecondaryInput.V   -> "0.855"
    SecondaryInput.H   -> "55.0"
}
