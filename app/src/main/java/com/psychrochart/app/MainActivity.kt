@file:OptIn(ExperimentalMaterial3Api::class)

package com.psychrochart.app

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.psychrochart.app.ui.navigation.Screen
import com.psychrochart.app.ui.navigation.bottomNavScreens
import com.psychrochart.app.ui.screens.AhuChainScreen
import com.psychrochart.app.ui.screens.ChartScreen
import com.psychrochart.app.ui.screens.ProcessScreen
import com.psychrochart.app.ui.screens.StatePointScreen
import com.psychrochart.app.ui.theme.PsychroChartTheme
import com.psychrochart.app.viewmodel.MainViewModel

private const val PREFS_NAME       = "psychro_prefs"
private const val KEY_ONBOARDED    = "onboarded"
private const val KEY_LAST_VERSION = "last_version_code"

// ── Changelog ─────────────────────────────────────────────────────────────────

private data class VersionEntry(val version: String, val changes: List<String>)

private val changelog = listOf(
    VersionEntry("12.0.0", listOf(
        "In-app onboarding guide shown on first launch",
        "Help sheet accessible anytime via the info icon in the toolbar",
        "What's New screen shown automatically after each update",
    )),
    VersionEntry("11.0.0", listOf(
        "Fixed 4 critical bugs in AHU chain and psychrometric calculations",
        "Improved accuracy of enthalpy, humidity ratio, and saturation curve",
    )),
    VersionEntry("10.0.0", listOf(
        "Redesigned chart with toggleable RH, WBT, Enthalpy, and Specific Volume layers",
        "Curves clipped cleanly to plot area with edge labels",
        "Tap any point on the chart to inspect all psychrometric properties",
        "Pinch-to-zoom and pan gesture support",
    )),
)

// ── Activity ──────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PsychroChartTheme {
                val navController = rememberNavController()
                val vm: MainViewModel = viewModel()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val context = LocalContext.current

                var showHelp       by remember { mutableStateOf(false) }
                var showOnboarding by remember { mutableStateOf(false) }
                var showWhatsNew   by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
                    val currentCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        pkg.longVersionCode.toInt()
                    else
                        @Suppress("DEPRECATION") pkg.versionCode

                    when {
                        !prefs.getBoolean(KEY_ONBOARDED, false) -> {
                            showOnboarding = true
                            prefs.edit()
                                .putBoolean(KEY_ONBOARDED, true)
                                .putInt(KEY_LAST_VERSION, currentCode)
                                .apply()
                        }
                        currentCode > prefs.getInt(KEY_LAST_VERSION, 0) -> {
                            showWhatsNew = true
                            prefs.edit().putInt(KEY_LAST_VERSION, currentCode).apply()
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("PsychroChart", fontWeight = FontWeight.Bold) },
                            actions = {
                                IconButton(onClick = { showHelp = true }) {
                                    Icon(Icons.Default.Info, contentDescription = "How to use")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            bottomNavScreens.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.label) },
                                    label = { Text(screen.label) },
                                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.StatePoint.route,
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable(Screen.StatePoint.route) { StatePointScreen(vm) }
                        composable(Screen.Processes.route)  { ProcessScreen(vm) }
                        composable(Screen.Chart.route)      { ChartScreen(vm) }
                        composable(Screen.AhuChain.route)   { AhuChainScreen(vm) }
                    }
                }

                // ── First-launch onboarding dialog ─────────────────────────────
                if (showOnboarding) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Welcome to PsychroChart", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OnboardingStep("1", "Calculator",
                                    "Enter Dry-Bulb Temp + a second input (RH, WBT, etc.), then tap Calculate & Plot.")
                                OnboardingStep("2", "Chart",
                                    "See your point on the chart. Toggle layers, pinch to zoom, tap a dot to inspect.")
                                OnboardingStep("3", "Processes",
                                    "Simulate HVAC processes (heating, cooling, mixing). Results auto-plot on the chart.")
                                OnboardingStep("4", "AHU Chain",
                                    "Chain multiple processes and view a full before/after summary table.")
                            }
                        },
                        confirmButton = {
                            Button(onClick = { showOnboarding = false }) {
                                Text("Got it!")
                            }
                        }
                    )
                }

                // ── What's New bottom sheet ────────────────────────────────────
                if (showWhatsNew) {
                    ModalBottomSheet(onDismissRequest = { showWhatsNew = false }) {
                        WhatsNewSheetContent()
                    }
                }

                // ── Help bottom sheet ──────────────────────────────────────────
                if (showHelp) {
                    ModalBottomSheet(onDismissRequest = { showHelp = false }) {
                        HelpSheetContent(onShowWhatsNew = { showHelp = false; showWhatsNew = true })
                    }
                }
            }
        }
    }
}

// ── Onboarding ────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingStep(number: String, tab: String, description: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                number,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tab, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── What's New ────────────────────────────────────────────────────────────────

@Composable
private fun WhatsNewSheetContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.NewReleases,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp))
            Text("What's New",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()

        changelog.forEachIndexed { index, entry ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.version,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    if (index == 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text("LATEST",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
                entry.changes.forEach { change ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold)
                        Text(change,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (index < changelog.lastIndex) HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

// ── Help ──────────────────────────────────────────────────────────────────────

@Composable
private fun HelpSheetContent(onShowWhatsNew: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("How to use PsychroChart",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            TextButton(onClick = onShowWhatsNew) {
                Icon(Icons.Default.NewReleases,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("What's New", style = MaterialTheme.typography.labelMedium)
            }
        }
        HorizontalDivider()
        HelpSection(
            icon = Icons.Default.Thermostat,
            title = "Calculator",
            steps = listOf(
                "Enter Dry-Bulb Temperature (°C)",
                "Choose a second input: RH, WBT, DPT, W, v, or h",
                "Enter the second value",
                "Tap \"Calculate & Plot on Chart\"",
            )
        )
        HelpSection(
            icon = Icons.Default.Analytics,
            title = "Chart",
            steps = listOf(
                "Toggle curve layers: RH lines, Wet-Bulb, Enthalpy, Specific Volume",
                "Pinch to zoom in/out, drag to pan",
                "Tap any plotted dot to inspect all psychrometric properties",
                "Use the Save button (bottom-right) to export the chart as PNG",
            )
        )
        HelpSection(
            icon = Icons.AutoMirrored.Filled.CompareArrows,
            title = "Processes",
            steps = listOf(
                "Set the initial air state (DBT + secondary input)",
                "Choose a process type (Sensible Cooling, Humidification, etc.)",
                "Enter the target parameters",
                "Tap \"Run Process\" — result plots automatically on Chart",
            )
        )
        HelpSection(
            icon = Icons.Default.DeviceHub,
            title = "AHU Chain",
            steps = listOf(
                "Set initial supply air conditions and tap Set Initial State",
                "Add process steps one by one using Add Step",
                "View the chain flow diagram and before/after summary table",
                "Tap Reset Chain to start over",
            )
        )
    }
}

@Composable
private fun HelpSection(icon: ImageVector, title: String, steps: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
        }
        steps.forEachIndexed { i, step ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("${i + 1}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold)
                Text(step,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
