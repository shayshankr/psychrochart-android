@file:Suppress("DEPRECATION")

package com.psychrochart.app.ui.screens

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.unit.dp
import com.psychrochart.app.domain.AppSettings
import com.psychrochart.app.domain.ProcessType
import com.psychrochart.app.domain.PsychroCalc
import com.psychrochart.app.domain.UnitConverter
import com.psychrochart.app.domain.UnitSystem
import com.psychrochart.app.ui.theme.*
import com.psychrochart.app.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.sqrt

private const val DBT_MIN = -10.0
private const val DBT_MAX =  50.0
private const val W_MIN   =   0.0
private const val W_MAX   =   0.035

// Chart margin constants (pixels in canvas space)
private const val LEFT_PAD   = 106f
private const val RIGHT_PAD  =  72f
private const val TOP_PAD    =  52f
private const val BOTTOM_PAD =  70f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(vm: MainViewModel) {
    val plottedStates by vm.plottedStates.collectAsState()
    val processResult  by vm.processResult.collectAsState()
    val ahuChain       by vm.ahuChain.collectAsState()
    val chartLayers    by vm.chartLayers.collectAsState()
    val selectedIdx    by vm.selectedPointIdx.collectAsState()
    val unitSystem     by AppSettings.unitSystem.collectAsState()
    val darkChart      by AppSettings.darkChart.collectAsState()

    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val textMeasurer = rememberTextMeasurer()

    // Canvas size captured for hit-testing outside DrawScope
    var canvasW by remember { mutableFloatStateOf(0f) }
    var canvasH by remember { mutableFloatStateOf(0f) }

    // Coordinate helpers using captured canvas size (for pointer input)
    fun cToX(dbt: Double): Float {
        val plotW = canvasW - LEFT_PAD - RIGHT_PAD
        return (LEFT_PAD + ((dbt - DBT_MIN) / (DBT_MAX - DBT_MIN)) * plotW).toFloat()
    }
    fun cToY(w: Double): Float {
        val plotH = canvasH - TOP_PAD - BOTTOM_PAD
        return (TOP_PAD + plotH - ((w - W_MIN) / (W_MAX - W_MIN)) * plotH).toFloat()
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale  = (scale * zoomChange).coerceIn(0.5f, 12f)
        offset = Offset(offset.x + panChange.x * scale, offset.y + panChange.y * scale)
    }

    val graphicsContext = LocalGraphicsContext.current
    val captureLayer    = remember { graphicsContext.createGraphicsLayer() }
    DisposableEffect(captureLayer) { onDispose { graphicsContext.releaseGraphicsLayer(captureLayer) } }

    val coroutineScope = rememberCoroutineScope()
    val context        = LocalContext.current

    val sheetState = rememberModalBottomSheetState()
    var showSheet  by remember { mutableStateOf(false) }
    LaunchedEffect(selectedIdx) { if (selectedIdx != null) showSheet = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── Layer toggle row ───────────────────────────────────────────────────
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    Text(
                        "Layers:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                item {
                    FilterChip(
                        selected = chartLayers.rh,
                        onClick = { vm.toggleChartLayer("rh") },
                        label = { Text("RH %") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ChartRH.copy(alpha = 0.18f),
                            selectedLabelColor = ChartRH,
                        ),
                    )
                }
                item {
                    FilterChip(
                        selected = chartLayers.wbt,
                        onClick = { vm.toggleChartLayer("wbt") },
                        label = { Text("WBT") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ChartWBT.copy(alpha = 0.18f),
                            selectedLabelColor = ChartWBT,
                        ),
                    )
                }
                item {
                    FilterChip(
                        selected = chartLayers.enthalpy,
                        onClick = { vm.toggleChartLayer("enthalpy") },
                        label = { Text("h (kJ/kg)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ChartEnthalpy.copy(alpha = 0.18f),
                            selectedLabelColor = ChartEnthalpy,
                        ),
                    )
                }
                item {
                    FilterChip(
                        selected = chartLayers.specVol,
                        onClick = { vm.toggleChartLayer("specVol") },
                        label = { Text("v (m³/kg)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ChartSpecVol.copy(alpha = 0.18f),
                            selectedLabelColor = ChartSpecVol,
                        ),
                    )
                }
                item {
                    FilterChip(
                        selected = chartLayers.comfortZone,
                        onClick = { vm.toggleChartLayer("comfortZone") },
                        label = { Text("ASHRAE 55") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.18f),
                            selectedLabelColor = Color(0xFF2E7D32),
                        ),
                    )
                }
            }
        }

        // ── Chart ──────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .drawWithContent {
                    captureLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(captureLayer)
                },
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(plottedStates, scale, offset, canvasW, canvasH) {
                        detectTapGestures { tapPos ->
                            if (canvasW == 0f) return@detectTapGestures
                            // Transform tap from screen coords to canvas coords
                            val cx = (tapPos.x - offset.x) / scale
                            val cy = (tapPos.y - offset.y) / scale
                            val hitR = 52f
                            var hit: Int? = null
                            var minD = Float.MAX_VALUE
                            plottedStates.forEachIndexed { i, ps ->
                                val w = minOf(ps.state.w, PsychroCalc.wSat(ps.state.dbt))
                                    .coerceIn(W_MIN, W_MAX)
                                val px = cToX(ps.state.dbt)
                                val py = cToY(w)
                                val d  = hypot(cx - px, cy - py)
                                if (d < hitR && d < minD) { minD = d; hit = i }
                            }
                            vm.selectChartPoint(hit)
                        }
                    }
                    .transformable(state = transformState)
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offset.x, translationY = offset.y,
                    ),
            ) {
                canvasW = size.width
                canvasH = size.height

                val cw    = size.width
                val ch    = size.height
                val plotW = cw - LEFT_PAD - RIGHT_PAD
                val plotH = ch - TOP_PAD  - BOTTOM_PAD

                // Canvas-local coordinate helpers
                fun toX(dbt: Double) =
                    (LEFT_PAD + ((dbt - DBT_MIN) / (DBT_MAX - DBT_MIN)) * plotW).toFloat()
                fun toY(w: Double) =
                    (TOP_PAD + plotH - ((w - W_MIN) / (W_MAX - W_MIN)) * plotH).toFloat()

                // ── Text styles (dark-chart aware) ────────────────────────────
                val axisClr    = if (darkChart) Color(0xFFB0BEC5) else Color(0xFF455A64)
                val axisTitleC = if (darkChart) Color(0xFFECEFF1) else Color(0xFF1A252F)
                val axisLbl    = TextStyle(fontSize = 9.sp,  color = axisClr)
                val axisTitle  = TextStyle(fontSize = 10.sp, color = axisTitleC,
                                           fontWeight = FontWeight.SemiBold)
                val tickClr    = if (darkChart) Color(0xFF78909C) else Color(0xFF546E7A)
                val rhLbl      = TextStyle(fontSize = 8.sp,  color = ChartRH,
                                           fontWeight = FontWeight.SemiBold)
                val wbtLbl     = TextStyle(fontSize = 7.sp,  color = ChartWBT,
                                           fontWeight = FontWeight.SemiBold)
                val hLbl       = TextStyle(fontSize = 7.sp,  color = ChartEnthalpy,
                                           fontWeight = FontWeight.SemiBold)
                val vLbl       = TextStyle(fontSize = 7.sp,  color = ChartSpecVol)
                val rightAxis  = TextStyle(fontSize = 8.sp,  color = axisClr)

                // ── Plot background ────────────────────────────────────────────
                drawRect(
                    if (darkChart) Color(0xFF0D1B2A) else Color(0xFFF5F9FF),
                    topLeft = Offset(0f, 0f),
                    size = Size(cw, ch),
                )
                drawRect(
                    if (darkChart) Color(0xFF1A2744) else Color(0xFFF5F9FF),
                    topLeft = Offset(LEFT_PAD, TOP_PAD),
                    size = Size(plotW, plotH),
                )

                // ── Grid lines (clipped to plot) ───────────────────────────────
                clipRect(LEFT_PAD, TOP_PAD, LEFT_PAD + plotW, TOP_PAD + plotH) {
                    // Minor vertical (every 1 °C)
                    for (t in DBT_MIN.toInt()..DBT_MAX.toInt()) {
                        if (t % 5 == 0) continue
                        val x = toX(t.toDouble())
                        drawLine(Color.Gray.copy(alpha = 0.07f),
                            Offset(x, TOP_PAD), Offset(x, TOP_PAD + plotH), 0.5f)
                    }
                    // Minor horizontal (every 0.001 kg/kg)
                    var wv = W_MIN
                    while (wv <= W_MAX + 1e-9) {
                        if ((wv * 1000).toInt() % 5 != 0) {
                            val y = toY(wv)
                            drawLine(Color.Gray.copy(alpha = 0.07f),
                                Offset(LEFT_PAD, y), Offset(LEFT_PAD + plotW, y), 0.5f)
                        }
                        wv += 0.001
                    }
                    // Major vertical (every 5 °C)
                    for (t in -10..50 step 5) {
                        val x = toX(t.toDouble())
                        drawLine(Color.Gray.copy(alpha = 0.18f),
                            Offset(x, TOP_PAD), Offset(x, TOP_PAD + plotH), 0.7f)
                    }
                    // Major horizontal (every 0.005 kg/kg)
                    for (i in 0..7) {
                        val y = toY(i * 0.005)
                        drawLine(Color.Gray.copy(alpha = 0.18f),
                            Offset(LEFT_PAD, y), Offset(LEFT_PAD + plotW, y), 0.7f)
                    }
                }

                // ── Chart curves — all clipped to plot area ────────────────────
                clipRect(LEFT_PAD, TOP_PAD, LEFT_PAD + plotW, TOP_PAD + plotH) {
                    // Constant RH (10 %–90 %)
                    if (chartLayers.rh) {
                        for (rhInt in 10..90 step 10) {
                            drawChartPath(
                                PsychroCalc.constantRhCurve(rhInt / 100.0).points,
                                ChartRH.copy(alpha = 0.70f), 1.5f, ::toX, ::toY,
                            )
                        }
                    }
                    // Saturation curve — always visible
                    drawChartPath(
                        PsychroCalc.saturationCurve().points,
                        ChartSaturation, 3.5f, ::toX, ::toY,
                    )
                    // Constant WBT
                    if (chartLayers.wbt) {
                        for (wbt in -5..30 step 5) {
                            drawChartPath(
                                PsychroCalc.constantWbtCurve(wbt.toDouble()).points,
                                ChartWBT.copy(alpha = 0.65f), 1.0f, ::toX, ::toY,
                                dashInterval = 12f,
                            )
                        }
                    }
                    // Constant enthalpy
                    if (chartLayers.enthalpy) {
                        for (hKj in -10..120 step 10) {
                            drawChartPath(
                                PsychroCalc.constantEnthalpyCurve(hKj.toDouble()).points,
                                ChartEnthalpy.copy(alpha = 0.55f), 0.9f, ::toX, ::toY,
                                dashInterval = 9f,
                            )
                        }
                    }
                    // Constant specific volume
                    if (chartLayers.specVol) {
                        for (v in listOf(0.78, 0.80, 0.82, 0.84, 0.86, 0.88, 0.90, 0.92, 0.94)) {
                            drawChartPath(
                                PsychroCalc.constantSpecVolCurve(v).points,
                                ChartSpecVol.copy(alpha = 0.50f), 0.9f, ::toX, ::toY,
                                dashInterval = 5f,
                            )
                        }
                    }
                    // ASHRAE 55 comfort zone (summer, ~0.5 clo, 1.0 met, approximate)
                    if (chartLayers.comfortZone) {
                        val comfortPts = listOf(
                            Pair(20.0, PsychroCalc.humRatioFromRelHum(20.0, 0.10)),
                            Pair(26.7, PsychroCalc.humRatioFromRelHum(26.7, 0.10)),
                            Pair(26.7, 0.012),
                            Pair(22.5, 0.012),
                            Pair(20.0, PsychroCalc.humRatioFromRelHum(20.0, 0.65)),
                        )
                        val comfortPath = Path().apply {
                            comfortPts.forEachIndexed { i, (dbt, w) ->
                                val x = toX(dbt); val y = toY(w.coerceIn(W_MIN, W_MAX))
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            close()
                        }
                        drawPath(comfortPath, Color(0xFF4CAF50).copy(alpha = 0.12f))
                        drawPath(comfortPath, Color(0xFF4CAF50).copy(alpha = 0.55f),
                            style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))))
                        val clx = toX(23.0); val cly = toY(0.006f.toDouble())
                        drawText(textMeasurer, "ASHRAE 55",
                            topLeft = Offset(clx - 22f, cly),
                            style = TextStyle(fontSize = 7.sp, color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold, background = Color.White.copy(alpha = 0.6f)))
                    }
                    // Process arrow
                    processResult?.let { result ->
                        val x1 = toX(result.state1.dbt)
                        val y1 = toY(result.state1.w.coerceIn(W_MIN, W_MAX))
                        val x2 = toX(result.state2.dbt)
                        val y2 = toY(result.state2.w.coerceIn(W_MIN, W_MAX))
                        if (result.processType == ProcessType.HEATING_HUMIDIFICATION) {
                            drawArrowSegment(x2 - x1, 0f, x1, y1, x2, y1, Color(0xFFE53935))
                            drawArrowSegment(0f, y2 - y1, x2, y1, x2, y2, Color(0xFF1E88E5))
                        } else {
                            val arrowColor = processArrowColor(result.processType)
                            drawArrowSegment(x2 - x1, y2 - y1, x1, y1, x2, y2, arrowColor)
                        }
                        val lx = (x1 + x2) / 2f
                        val ly = (y1 + y2) / 2f
                        val procStyle = TextStyle(
                            fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A252F),
                            background = Color(0xCCFFFDE7),
                        )
                        val pm = textMeasurer.measure(result.processType.label, procStyle)
                        val procLabelCy = ly - 18f - pm.size.width / 2f
                        withTransform({ rotate(90f, pivot = Offset(lx, procLabelCy)) }) {
                            drawText(
                                textMeasurer, result.processType.label,
                                topLeft = Offset(lx - pm.size.width / 2f, procLabelCy - pm.size.height / 2f),
                                style = procStyle,
                            )
                        }
                    }
                    // AHU chain arrows — one segment per step, colour-coded by process type
                    ahuChain.forEach { step ->
                        val x1 = toX(step.stateIn.dbt)
                        val y1 = toY(step.stateIn.w.coerceIn(W_MIN, W_MAX))
                        val x2 = toX(step.stateOut.dbt)
                        val y2 = toY(step.stateOut.w.coerceIn(W_MIN, W_MAX))
                        if (step.processType == ProcessType.HEATING_HUMIDIFICATION) {
                            drawArrowSegment(x2 - x1, 0f, x1, y1, x2, y1, Color(0xFFE53935))
                            drawArrowSegment(0f, y2 - y1, x2, y1, x2, y2, Color(0xFF1E88E5))
                        } else {
                            drawArrowSegment(x2 - x1, y2 - y1, x1, y1, x2, y2,
                                processArrowColor(step.processType))
                        }
                    }
                }

                // ── X-axis ticks + labels ──────────────────────────────────────
                val isIp = unitSystem == UnitSystem.IP
                for (t in -10..50 step 5) {
                    val x = toX(t.toDouble())
                    drawLine(tickClr, Offset(x, TOP_PAD + plotH),
                        Offset(x, TOP_PAD + plotH + 7f), 1.5f)
                    val lbl = if (isIp) "${UnitConverter.cToF(t.toDouble()).toInt()}" else "$t"
                    val m = textMeasurer.measure(lbl, axisLbl)
                    drawText(textMeasurer, lbl,
                        topLeft = Offset(x - m.size.width / 2f, TOP_PAD + plotH + 10f),
                        style = axisLbl)
                }
                for (t in DBT_MIN.toInt()..DBT_MAX.toInt()) {
                    if (t % 5 == 0) continue
                    val x = toX(t.toDouble())
                    drawLine(tickClr.copy(alpha = 0.35f),
                        Offset(x, TOP_PAD + plotH), Offset(x, TOP_PAD + plotH + 4f), 0.8f)
                }
                val xAxisTitle = if (isIp) "Dry-Bulb Temperature  (°F)" else "Dry-Bulb Temperature  (°C)"
                val xTitleM = textMeasurer.measure(xAxisTitle, axisTitle)
                drawText(textMeasurer, xAxisTitle,
                    topLeft = Offset(
                        LEFT_PAD + plotW / 2f - xTitleM.size.width / 2f,
                        TOP_PAD + plotH + 36f),
                    style = axisTitle)

                // ── Y-axis (kg/kg or gr/lb) ────────────────────────────────────
                for (i in 0..7) {
                    val wVal = i * 0.005
                    val y = toY(wVal)
                    drawLine(tickClr, Offset(LEFT_PAD - 7f, y), Offset(LEFT_PAD, y), 1.5f)
                    val lbl = if (isIp) "%.0f".format(UnitConverter.kgkgToGrLb(wVal))
                              else "%.3f".format(wVal)
                    val m = textMeasurer.measure(lbl, axisLbl)
                    drawText(textMeasurer, lbl,
                        topLeft = Offset(LEFT_PAD - 10f - m.size.width, y - m.size.height / 2f),
                        style = axisLbl)
                }
                var wvt = W_MIN
                while (wvt <= W_MAX + 1e-9) {
                    if ((wvt * 1000).toInt() % 5 != 0) {
                        val y = toY(wvt)
                        drawLine(tickClr.copy(alpha = 0.35f),
                            Offset(LEFT_PAD - 4f, y), Offset(LEFT_PAD, y), 0.8f)
                    }
                    wvt += 0.001
                }
                withTransform({ rotate(90f, pivot = Offset(14f, TOP_PAD + plotH / 2f)) }) {
                    val yAxisTitle = if (isIp) "Humidity Ratio  W  (gr/lb dry air)"
                                    else "Humidity Ratio  W  (kg/kg dry air)"
                    val m = textMeasurer.measure(yAxisTitle, axisTitle)
                    drawText(textMeasurer, yAxisTitle,
                        topLeft = Offset(14f - m.size.width / 2f,
                            TOP_PAD + plotH / 2f - m.size.height / 2f),
                        style = axisTitle)
                }

                // ── Right axis (g/kg) ──────────────────────────────────────────
                for (i in 0..7) {
                    val wVal = i * 0.005
                    val y = toY(wVal)
                    drawLine(tickClr.copy(alpha = 0.5f),
                        Offset(LEFT_PAD + plotW, y), Offset(LEFT_PAD + plotW + 6f, y), 1.2f)
                    val lbl = "${(wVal * 1000).toInt()}"
                    drawText(textMeasurer, lbl,
                        topLeft = Offset(LEFT_PAD + plotW + 9f, y - 7f),
                        style = rightAxis)
                }
                withTransform({ rotate(90f, pivot = Offset(cw - 10f, TOP_PAD + plotH / 2f)) }) {
                    val m = textMeasurer.measure("W  (g/kg dry air)", rightAxis)
                    drawText(textMeasurer, "W  (g/kg dry air)",
                        topLeft = Offset(cw - 10f - m.size.width / 2f,
                            TOP_PAD + plotH / 2f - m.size.height / 2f),
                        style = rightAxis)
                }

                // ── Margin labels for chart curves ─────────────────────────────
                // RH labels — right margin or top margin
                if (chartLayers.rh) {
                    for (rhInt in 10..90 step 10) {
                        val visible = PsychroCalc.constantRhCurve(rhInt / 100.0).points
                            .filter { (d, w) -> d in DBT_MIN..DBT_MAX && w in W_MIN..W_MAX }
                        if (visible.isEmpty()) continue
                        val (lastD, lastW) = visible.last()
                        val lx = toX(lastD); val ly = toY(lastW)
                        if (lx > LEFT_PAD + plotW * 0.80f) {
                            drawText(textMeasurer, "$rhInt%",
                                topLeft = Offset(LEFT_PAD + plotW + 4f, ly - 6f),
                                style = rhLbl)
                        } else {
                            drawText(textMeasurer, "$rhInt%",
                                topLeft = Offset(lx - 8f, TOP_PAD - 15f),
                                style = rhLbl)
                        }
                    }
                }
                // WBT labels — top margin at saturation-curve intersection
                if (chartLayers.wbt) {
                    for (wbt in -5..30 step 5) {
                        val visible = PsychroCalc.constantWbtCurve(wbt.toDouble()).points
                            .filter { (d, w) -> d in DBT_MIN..DBT_MAX && w in W_MIN..W_MAX }
                        if (visible.isEmpty()) continue
                        val (firstD, _) = visible.first()
                        drawText(textMeasurer, "${wbt}°",
                            topLeft = Offset(toX(firstD) - 8f, TOP_PAD - 16f),
                            style = wbtLbl)
                    }
                }
                // Enthalpy labels — top or left margin
                if (chartLayers.enthalpy) {
                    for (hKj in -10..120 step 10) {
                        val visible = PsychroCalc.constantEnthalpyCurve(hKj.toDouble()).points
                            .filter { (d, w) -> d in DBT_MIN..DBT_MAX && w in W_MIN..W_MAX }
                        if (visible.isEmpty()) continue
                        val (firstD, firstW) = visible.first()
                        if (firstW >= W_MAX - 0.001) {
                            drawText(textMeasurer, "$hKj",
                                topLeft = Offset(toX(firstD) - 8f, TOP_PAD - 15f),
                                style = hLbl)
                        } else {
                            drawText(textMeasurer, "$hKj",
                                topLeft = Offset(4f, toY(firstW) - 6f),
                                style = hLbl)
                        }
                    }
                }
                // SpecVol labels — right or top margin
                if (chartLayers.specVol) {
                    for (v in listOf(0.78, 0.80, 0.82, 0.84, 0.86, 0.88, 0.90, 0.92, 0.94)) {
                        val visible = PsychroCalc.constantSpecVolCurve(v).points
                            .filter { (d, w) -> d in DBT_MIN..DBT_MAX && w in W_MIN..W_MAX }
                        if (visible.isEmpty()) continue
                        val (lastD, lastW) = visible.last()
                        val lx = toX(lastD); val ly = toY(lastW)
                        val lbl = "%.2f".format(v)
                        if (lx > LEFT_PAD + plotW * 0.80f) {
                            drawText(textMeasurer, lbl,
                                topLeft = Offset(LEFT_PAD + plotW + 4f, ly - 22f),
                                style = vLbl)
                        } else {
                            drawText(textMeasurer, lbl,
                                topLeft = Offset(lx - 10f, TOP_PAD - 28f),
                                style = vLbl)
                        }
                    }
                }

                // ── Axes border ────────────────────────────────────────────────
                drawRect(
                    Color(0xFF37474F),
                    topLeft = Offset(LEFT_PAD, TOP_PAD),
                    size = Size(plotW, plotH),
                    style = Stroke(1.5f),
                )

                // ── State point dots ───────────────────────────────────────────
                plottedStates.forEachIndexed { i, ps ->
                    val col      = PointColors[i % PointColors.size]
                    val px       = toX(ps.state.dbt)
                    val wClamp   = minOf(ps.state.w, PsychroCalc.wSat(ps.state.dbt))
                        .coerceIn(W_MIN, W_MAX)
                    val py       = toY(wClamp)
                    val selected = i == selectedIdx

                    if (selected) drawCircle(col.copy(alpha = 0.22f), 28f, Offset(px, py))
                    drawCircle(col, if (selected) 17f else 13f, Offset(px, py))
                    drawCircle(Color.White, if (selected) 7f else 5f, Offset(px, py))
                    val lblStyle = TextStyle(
                        fontSize = 8.sp, fontWeight = FontWeight.Bold,
                        color = col,
                        background = Color.White.copy(alpha = 0.80f),
                    )
                    val lm = textMeasurer.measure(ps.label, lblStyle)
                    val lblCy = py - 20f - lm.size.width / 2f
                    withTransform({ rotate(90f, pivot = Offset(px, lblCy)) }) {
                        drawText(
                            textMeasurer, ps.label,
                            topLeft = Offset(px - lm.size.width / 2f, lblCy - lm.size.height / 2f),
                            style = lblStyle,
                        )
                    }
                }
            }

            // ── FABs ──────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            val bmp = captureLayer.toImageBitmap().asAndroidBitmap()
                            withContext(Dispatchers.IO) { saveChartToGallery(context, bmp) }
                            Toast.makeText(context, "Chart saved to Gallery", Toast.LENGTH_SHORT)
                                .show()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(Icons.Default.SaveAlt, "Save chart as image",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                SmallFloatingActionButton(
                    onClick = { AppSettings.setDarkChart(!darkChart) },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Icon(
                        if (darkChart) Icons.Default.LightMode else Icons.Default.DarkMode,
                        "Toggle dark chart",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                if (plottedStates.isNotEmpty()) {
                    SmallFloatingActionButton(
                        onClick = { vm.removeLastPlottedState() },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Icon(Icons.Default.Undo, "Undo last point",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SmallFloatingActionButton(
                        onClick = { vm.clearPlottedStates() },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Icon(Icons.Default.DeleteSweep, "Clear all points",
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                FloatingActionButton(
                    onClick = { scale = 1f; offset = Offset.Zero },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(Icons.Default.CenterFocusStrong, "Reset zoom",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        // ── Plotted points strip ───────────────────────────────────────────────
        if (plottedStates.isNotEmpty()) {
            Surface(tonalElevation = 3.dp) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(plottedStates) { idx, ps ->
                        val col    = PointColors[idx % PointColors.size]
                        val isSel  = idx == selectedIdx
                        FilterChip(
                            selected = isSel,
                            onClick  = { vm.selectChartPoint(if (isSel) null else idx) },
                            label    = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .background(col, RoundedCornerShape(50)),
                                    )
                                    Text(ps.label, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "%.1f%s  %.0f%%".format(
                                            UnitConverter.displayTemp(ps.state.dbt, unitSystem),
                                            UnitConverter.tempUnit(unitSystem),
                                            ps.state.rh),
                                        style  = MaterialTheme.typography.labelSmall,
                                        color  = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = col.copy(alpha = 0.15f),
                                selectedLabelColor     = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }
        }
    }

    // ── Bottom sheet: full state detail ───────────────────────────────────────
    if (showSheet) {
        val ps = selectedIdx?.let { plottedStates.getOrNull(it) }
        if (ps != null) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false; vm.selectChartPoint(null) },
                sheetState = sheetState,
            ) {
                val col = PointColors[(selectedIdx ?: 0) % PointColors.size]
                StateDetailSheet(ps, col, unitSystem)
            }
        } else {
            showSheet = false
        }
    }
}

@Composable
private fun StateDetailSheet(
    ps: MainViewModel.PlottedState,
    color: Color,
    unitSystem: UnitSystem,
) {
    val uc = UnitConverter
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(16.dp).background(color, RoundedCornerShape(50)))
            Text(ps.label, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()

        val tU = uc.tempUnit(unitSystem)
        val isIp = unitSystem == UnitSystem.IP
        val props = listOf(
            "DBT" to "%.1f %s".format(uc.displayTemp(ps.state.dbt, unitSystem), tU),
            "WBT" to "%.1f %s".format(uc.displayTemp(ps.state.wbt, unitSystem), tU),
            "DPT" to "%.1f %s".format(uc.displayTemp(ps.state.dpt, unitSystem), tU),
            "RH"  to "%.1f %%".format(ps.state.rh),
            "W"   to if (isIp) "%.1f\ngr/lb".format(uc.kgkgToGrLb(ps.state.w))
                     else       "%.5f\nkg/kg".format(ps.state.w),
            "h"   to if (isIp) "%.2f\nBTU/lb".format(uc.kjkgToBtuLb(ps.state.h))
                     else       "%.2f\nkJ/kg".format(ps.state.h),
            "v"   to if (isIp) "%.4f\nft³/lb".format(uc.m3kgToFt3Lb(ps.state.v))
                     else       "%.4f\nm³/kg".format(ps.state.v),
            "Pv"  to if (isIp) "%.4f\npsi".format(uc.kPaToPsi(ps.state.pv))
                     else       "%.3f\nkPa".format(ps.state.pv),
            "μ"   to "%.4f".format(ps.state.mu),
        )
        props.chunked(3).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { (label, value) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun processArrowColor(type: ProcessType) = when (type) {
    ProcessType.SENSIBLE_HEATING         -> Color(0xFFE53935)
    ProcessType.SENSIBLE_COOLING         -> Color(0xFF1E88E5)
    ProcessType.HUMIDIFICATION           -> Color(0xFF43A047)
    ProcessType.DEHUMIDIFICATION         -> Color(0xFF1565C0)
    ProcessType.COOLING_DEHUMIDIFICATION -> Color(0xFF039BE5)
    ProcessType.EVAPORATIVE_COOLING      -> Color(0xFF00ACC1)
    ProcessType.ADIABATIC_MIXING         -> Color(0xFF8E24AA)
    ProcessType.FAN_HEAT_RISE            -> Color(0xFFFF6F00)
    ProcessType.ENERGY_RECOVERY          -> Color(0xFF00695C)
    ProcessType.COOLING_COIL             -> Color(0xFF0277BD)
    ProcessType.HEATING_HUMIDIFICATION   -> Color(0xFFE53935)
}

private fun DrawScope.drawArrowSegment(
    dx: Float, dy: Float,
    x1: Float, y1: Float,
    x2: Float, y2: Float,
    color: Color,
) {
    val len = sqrt(dx * dx + dy * dy)
    if (len < 5f) return
    drawLine(color, Offset(x1, y1), Offset(x2, y2), 3f)
    val ux = dx / len; val uy = dy / len
    val px = -uy
    val headLen = 18f; val hw = 8f
    val hx = x2 - ux * headLen; val hy = y2 - uy * headLen
    drawPath(Path().apply {
        moveTo(x2, y2)
        lineTo(hx + px * hw, hy + ux * hw)
        lineTo(hx - px * hw, hy - ux * hw)
        close()
    }, color)
}

private fun DrawScope.drawChartPath(
    points: List<Pair<Double, Double>>,
    color: Color,
    strokeWidth: Float,
    toX: (Double) -> Float,
    toY: (Double) -> Float,
    dashInterval: Float = 0f,
) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(toX(points[0].first), toY(points[0].second))
        for (i in 1 until points.size) lineTo(toX(points[i].first), toY(points[i].second))
    }
    drawPath(path, color, style =
        if (dashInterval > 0f)
            Stroke(strokeWidth, pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(dashInterval, dashInterval)))
        else Stroke(strokeWidth))
}

private fun saveChartToGallery(context: android.content.Context, bitmap: android.graphics.Bitmap) {
    val filename = "HVACSuite_${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/HVAC Suite")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
    context.contentResolver.openOutputStream(uri)?.use { out ->
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }
}
