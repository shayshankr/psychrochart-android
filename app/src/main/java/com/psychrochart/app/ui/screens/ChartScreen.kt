package com.psychrochart.app.ui.screens

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SaveAlt
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
import androidx.compose.ui.graphics.layer.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.unit.dp
import com.psychrochart.app.domain.ProcessType
import com.psychrochart.app.domain.PsychroCalc
import com.psychrochart.app.ui.theme.*
import com.psychrochart.app.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

private const val DBT_MIN = -10.0
private const val DBT_MAX =  50.0
private const val W_MIN   =   0.0
private const val W_MAX   =   0.030

@Composable
fun ChartScreen(vm: MainViewModel) {
    val plottedStates by vm.plottedStates.collectAsState()
    val processResult  by vm.processResult.collectAsState()

    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val textMeasurer = rememberTextMeasurer()

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale  = (scale * zoomChange).coerceIn(0.8f, 8f)
        offset = Offset(offset.x + panChange.x * scale, offset.y + panChange.y * scale)
    }

    // For saving the chart as an image
    val captureLayer   = rememberGraphicsLayer()
    val coroutineScope = rememberCoroutineScope()
    val context        = LocalContext.current

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .drawWithContent {
            captureLayer.record { this@drawWithContent.drawContent() }
            drawLayer(captureLayer)
        }
    ) {

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .transformable(transformState)
                .graphicsLayer(
                    scaleX = scale, scaleY = scale,
                    translationX = offset.x, translationY = offset.y,
                )
        ) {
            val cw = size.width
            val ch = size.height

            // ── Margins ───────────────────────────────────────────────────────
            val leftPad   = 88f   // Y-axis tick labels + title
            val rightPad  = 58f   // right g/kg axis labels
            val topPad    = 44f   // enthalpy labels at top
            val bottomPad = 64f   // X-axis tick labels + title

            val plotW = cw - leftPad - rightPad
            val plotH = ch - topPad  - bottomPad

            // ── Coordinate transforms ─────────────────────────────────────────
            fun toX(dbt: Double) =
                (leftPad + ((dbt - DBT_MIN) / (DBT_MAX - DBT_MIN)) * plotW).toFloat()
            fun toY(w: Double) =
                (topPad + plotH - ((w - W_MIN) / (W_MAX - W_MIN)) * plotH).toFloat()

            // ── Plot background ───────────────────────────────────────────────
            drawRect(Color(0xFFF5F8FC),
                topLeft = Offset(leftPad, topPad), size = Size(plotW, plotH))

            // ── Minor grid (every 1 °C / every 0.001 kg/kg) ──────────────────
            for (t in DBT_MIN.toInt()..DBT_MAX.toInt()) {
                if (t % 5 == 0) continue
                val x = toX(t.toDouble())
                drawLine(Color.Gray.copy(alpha = 0.08f),
                    Offset(x, topPad), Offset(x, topPad + plotH), 0.5f)
            }
            var wv = W_MIN
            while (wv <= W_MAX + 1e-9) {
                if ((wv * 1000).toInt() % 5 != 0) {
                    val y = toY(wv)
                    drawLine(Color.Gray.copy(alpha = 0.08f),
                        Offset(leftPad, y), Offset(leftPad + plotW, y), 0.5f)
                }
                wv += 0.001
            }

            // ── Major grid (every 5 °C / every 0.005 kg/kg) ──────────────────
            for (t in -10..50 step 5) {
                val x = toX(t.toDouble())
                drawLine(Color.Gray.copy(alpha = 0.20f),
                    Offset(x, topPad), Offset(x, topPad + plotH), 0.8f)
            }
            for (i in 0..6) {
                val y = toY(i * 0.005)
                drawLine(Color.Gray.copy(alpha = 0.20f),
                    Offset(leftPad, y), Offset(leftPad + plotW, y), 0.8f)
            }

            // ── Text styles ───────────────────────────────────────────────────
            val axisLblStyle  = TextStyle(fontSize = 9.sp,   color = Color(0xFF2C3E50))
            val axisTitleStyle= TextStyle(fontSize = 10.sp,  color = Color(0xFF1A252F),
                                          fontWeight = FontWeight.SemiBold)
            val tickDark      = Color(0xFF2C3E50)
            val rhLblStyle    = TextStyle(fontSize = 7.sp,   color = Color(0xFF1055A8))
            val wbtLblStyle   = TextStyle(fontSize = 6.sp,   color = Color(0xFF007A38))
            val hLblStyle     = TextStyle(fontSize = 7.sp,   color = Color(0xFFB50009),
                                          fontWeight = FontWeight.Medium,
                                          background = Color(0xFFFFEEEE))
            val vLblStyle     = TextStyle(fontSize = 6.sp,   color = Color(0xFF7A1DB8))
            val rightAxisStyle= TextStyle(fontSize = 8.sp,   color = Color(0xFF555555))

            // ── X-axis ticks + labels ─────────────────────────────────────────
            for (t in -10..50 step 5) {
                val x = toX(t.toDouble())
                // Major tick
                drawLine(tickDark, Offset(x, topPad + plotH), Offset(x, topPad + plotH + 7f), 1.5f)
                // Label
                val lbl = "$t"
                val measured = textMeasurer.measure(lbl, axisLblStyle)
                drawText(textMeasurer, lbl,
                    topLeft = Offset(x - measured.size.width / 2f, topPad + plotH + 10f),
                    style = axisLblStyle)
            }
            // Minor X ticks (every 1°C)
            for (t in DBT_MIN.toInt()..DBT_MAX.toInt()) {
                if (t % 5 == 0) continue
                val x = toX(t.toDouble())
                drawLine(tickDark.copy(alpha = 0.4f),
                    Offset(x, topPad + plotH), Offset(x, topPad + plotH + 4f), 0.8f)
            }
            // X-axis title
            val xTitle = "Dry-Bulb Temperature  (°C)"
            val xTitleM = textMeasurer.measure(xTitle, axisTitleStyle)
            drawText(textMeasurer, xTitle,
                topLeft = Offset(leftPad + plotW / 2f - xTitleM.size.width / 2f,
                                 topPad + plotH + 36f),
                style = axisTitleStyle)

            // ── Y-axis ticks + labels (kg/kg) ─────────────────────────────────
            for (i in 0..6) {
                val wVal = i * 0.005
                val y    = toY(wVal)
                // Major tick
                drawLine(tickDark, Offset(leftPad - 7f, y), Offset(leftPad, y), 1.5f)
                // Label  e.g. "0.010"
                val lbl     = "%.3f".format(wVal)
                val measured = textMeasurer.measure(lbl, axisLblStyle)
                drawText(textMeasurer, lbl,
                    topLeft = Offset(leftPad - 10f - measured.size.width, y - measured.size.height / 2f),
                    style = axisLblStyle)
            }
            // Minor Y ticks (every 0.001)
            var wvt = W_MIN
            while (wvt <= W_MAX + 1e-9) {
                if ((wvt * 1000).toInt() % 5 != 0) {
                    val y = toY(wvt)
                    drawLine(tickDark.copy(alpha = 0.4f),
                        Offset(leftPad - 4f, y), Offset(leftPad, y), 0.8f)
                }
                wvt += 0.001
            }
            // Y-axis title (rotated)
            val yTitle  = "Humidity Ratio  W  (kg/kg dry air)"
            withTransform({
                rotate(-90f, pivot = Offset(14f, topPad + plotH / 2f))
            }) {
                val yTitleM = textMeasurer.measure(yTitle, axisTitleStyle)
                drawText(textMeasurer, yTitle,
                    topLeft = Offset(14f - yTitleM.size.width / 2f,
                                     topPad + plotH / 2f - yTitleM.size.height / 2f),
                    style = axisTitleStyle)
            }

            // ── Right Y-axis: g/kg labels ─────────────────────────────────────
            for (i in 0..6) {
                val wVal = i * 0.005
                val y    = toY(wVal)
                drawLine(tickDark.copy(alpha = 0.5f),
                    Offset(leftPad + plotW, y), Offset(leftPad + plotW + 6f, y), 1.2f)
                val lbl = "${(wVal * 1000).toInt()}"
                drawText(textMeasurer, lbl,
                    topLeft = Offset(leftPad + plotW + 9f, y - 7f),
                    style = rightAxisStyle)
            }
            // Right axis title (rotated)
            val rightTitle = "Humidity Ratio  (g/kg dry air)"
            withTransform({
                rotate(90f, pivot = Offset(cw - 12f, topPad + plotH / 2f))
            }) {
                val rtM = textMeasurer.measure(rightTitle, rightAxisStyle)
                drawText(textMeasurer, rightTitle,
                    topLeft = Offset(cw - 12f - rtM.size.width / 2f,
                                     topPad + plotH / 2f - rtM.size.height / 2f),
                    style = rightAxisStyle)
            }

            // ── Constant RH lines (10 %–90 %) ────────────────────────────────
            for (rhInt in 10..90 step 10) {
                val pts = PsychroCalc.constantRhCurve(rhInt / 100.0).points
                drawChartPath(pts, ChartRH.copy(alpha = 0.65f), 1.8f, ::toX, ::toY)
                if (pts.isNotEmpty()) {
                    val last = pts.last()
                    drawText(textMeasurer, "$rhInt%",
                        topLeft = Offset(toX(last.first) + 3f, toY(last.second) - 16f),
                        style = rhLblStyle)
                }
            }

            // ── Saturation curve ──────────────────────────────────────────────
            drawChartPath(PsychroCalc.saturationCurve().points, ChartSaturation, 4f, ::toX, ::toY)

            // ── Constant WBT lines ────────────────────────────────────────────
            for (wbt in -5..30 step 5) {
                val pts = PsychroCalc.constantWbtCurve(wbt.toDouble()).points
                drawChartPath(pts, ChartWBT.copy(alpha = 0.55f), 1.2f, ::toX, ::toY,
                    dashInterval = 15f)
                if (pts.isNotEmpty()) {
                    val first = pts.first()
                    val ly    = toY(first.second)
                    // Clamp label inside plot area
                    val labelY = ly.coerceIn(topPad + 2f, topPad + plotH - 22f)
                    drawText(textMeasurer, "WBT\n${wbt}°C",
                        topLeft = Offset(
                            (toX(first.first) - 24f).coerceAtLeast(leftPad + 2f),
                            labelY),
                        style = wbtLblStyle)
                }
            }

            // ── Constant enthalpy lines ───────────────────────────────────────
            for (hKj in -10..120 step 10) {
                val pts = PsychroCalc.constantEnthalpyCurve(hKj.toDouble()).points
                drawChartPath(pts, ChartEnthalpy.copy(alpha = 0.50f), 1.0f, ::toX, ::toY,
                    dashInterval = 10f)
                if (pts.isNotEmpty()) {
                    // Label where line enters from top (W_MAX) or from left (DBT_MIN)
                    val first = pts.first()
                    val lx = toX(first.first)
                    val ly = toY(first.second)
                    if (first.second >= W_MAX - 0.001) {
                        // enters from top — label above plot
                        drawText(textMeasurer, "$hKj",
                            topLeft = Offset(lx - 8f, topPad - 18f),
                            style = hLblStyle)
                    } else {
                        // enters from left — label on left side
                        drawText(textMeasurer, "$hKj",
                            topLeft = Offset(leftPad - 30f, ly - 8f),
                            style = hLblStyle)
                    }
                }
            }
            // h-axis label top
            drawText(textMeasurer, "h (kJ/kg)",
                topLeft = Offset(leftPad + 4f, topPad - 20f),
                style = TextStyle(fontSize = 8.sp, color = Color(0xFFB50009),
                    fontWeight = FontWeight.Bold))

            // ── Constant specific volume lines ────────────────────────────────
            for (vVal in listOf(0.78, 0.80, 0.82, 0.84, 0.86, 0.88, 0.90, 0.92, 0.94)) {
                val pts = PsychroCalc.constantSpecVolCurve(vVal).points
                drawChartPath(pts, ChartSpecVol.copy(alpha = 0.45f), 0.9f, ::toX, ::toY,
                    dashInterval = 6f)
                if (pts.isNotEmpty()) {
                    val first = pts.first()
                    drawText(textMeasurer, "v=$vVal",
                        topLeft = Offset(toX(first.first) + 2f, toY(first.second) - 28f),
                        style = vLblStyle)
                }
            }

            // ── Axes border ───────────────────────────────────────────────────
            drawRect(Color(0xFF2C3E50),
                topLeft = Offset(leftPad, topPad),
                size = Size(plotW, plotH),
                style = Stroke(2f))

            // ── Process arrows ────────────────────────────────────────────────
            processResult?.let { result ->
                val x1 = toX(result.state1.dbt); val y1 = toY(result.state1.w)
                val x2 = toX(result.state2.dbt); val y2 = toY(result.state2.w)

                if (result.processType == ProcessType.HEATING_HUMIDIFICATION) {
                    // Two-step: horizontal red arrow (sensible heating) then
                    //           vertical  blue arrow (humidification)
                    val xMid = x2; val yMid = y1   // corner point
                    val colorH = Color(0xFFE53935)  // red  – heating leg
                    val colorW = Color(0xFF1E88E5)  // blue – humidification leg
                    drawArrowSegment(xMid - x1, yMid - y1, x1, y1, xMid, yMid, colorH)
                    drawArrowSegment(x2 - xMid, y2 - yMid, xMid, yMid, x2, y2, colorW)
                    // Label near the corner
                    drawText(textMeasurer, result.processType.label,
                        topLeft = Offset(xMid + 6f, yMid - 22f),
                        style = TextStyle(fontSize = 9.sp, color = Color(0xFF2C3E50),
                            fontWeight = FontWeight.Bold,
                            background = Color(0xCCFEF9E7)))
                } else {
                    // Single straight arrow with process-type colour
                    val arrowColor = when (result.processType) {
                        ProcessType.SENSIBLE_HEATING          -> Color(0xFFE53935)
                        ProcessType.SENSIBLE_COOLING          -> Color(0xFF1E88E5)
                        ProcessType.HUMIDIFICATION            -> Color(0xFF1E88E5)
                        ProcessType.DEHUMIDIFICATION          -> Color(0xFF1E88E5)
                        ProcessType.COOLING_DEHUMIDIFICATION  -> Color(0xFF1E88E5)
                        ProcessType.EVAPORATIVE_COOLING       -> Color(0xFF00ACC1)
                        ProcessType.ADIABATIC_MIXING          -> Color(0xFF8E24AA)
                        else                                  -> Color(0xFF2C3E50)
                    }
                    val dx = x2 - x1; val dy = y2 - y1
                    drawArrowSegment(dx, dy, x1, y1, x2, y2, arrowColor)
                    drawText(textMeasurer, result.processType.label,
                        topLeft = Offset((x1 + x2) / 2f + 6f, (y1 + y2) / 2f - 20f),
                        style = TextStyle(fontSize = 9.sp, color = arrowColor,
                            fontWeight = FontWeight.Bold,
                            background = Color(0xCCFEF9E7)))
                }
            }

            // ── State point dots (no text labels — avoid double labelling) ──────
            plottedStates.forEachIndexed { i, ps ->
                val col = PointColors[i % PointColors.size]
                val cx  = toX(ps.state.dbt)
                // Clamp W to saturation so the dot never renders outside the chart
                val wClamped = minOf(ps.state.w, PsychroCalc.wSat(ps.state.dbt))
                val cy  = toY(wClamped)
                drawCircle(col, 14f, Offset(cx, cy))
                drawCircle(Color.White, 6f, Offset(cx, cy))
            }
        }

        // ── Legend ────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 92.dp, top = 8.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
                    RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            LegendRow(ChartSaturation,  "Saturation (100% RH)", lineWidth = 4.dp)
            LegendRow(ChartRH,          "Const. RH")
            LegendRow(ChartWBT,         "Const. WBT",    dashed = true)
            LegendRow(ChartEnthalpy,    "Const. Enthalpy", dashed = true)
            LegendRow(ChartSpecVol,     "Const. Sp. Volume", dashed = true)
        }

        // ── Plotted states list ───────────────────────────────────────────────
        if (plottedStates.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 92.dp, bottom = 70.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
                        RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                plottedStates.forEachIndexed { i, ps ->
                    val col = PointColors[i % PointColors.size]
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(col, RoundedCornerShape(50)))
                        Text(
                            "${ps.label}: ${ps.state.dbt}°C  W=${ps.state.w}  RH=${ps.state.rh}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // ── FABs ──────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Save chart as PNG
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        val bitmap = captureLayer.toImageBitmap().asAndroidBitmap()
                        withContext(Dispatchers.IO) {
                            saveChartToGallery(context, bitmap)
                        }
                        Toast.makeText(context, "Chart saved to Gallery", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(Icons.Default.SaveAlt, "Save chart as image")
            }

            if (plottedStates.isNotEmpty()) {
                SmallFloatingActionButton(onClick = { vm.clearPlottedStates() }) {
                    Icon(Icons.Default.DeleteSweep, "Clear points")
                }
            }
            FloatingActionButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                Icon(Icons.Default.CenterFocusStrong, "Reset zoom")
            }
        }
    }
}

// ── Canvas helpers ─────────────────────────────────────────────────────────────

/** Draw a line from (x1,y1) to (x2,y2) with an arrowhead at (x2,y2). */
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
    val px = -uy;      val py =  ux
    val headLen = 18f; val hw = 8f
    val hx = x2 - ux * headLen; val hy = y2 - uy * headLen
    drawPath(Path().apply {
        moveTo(x2, y2)
        lineTo(hx + px * hw, hy + py * hw)
        lineTo(hx - px * hw, hy - py * hw)
        close()
    }, color)
}

/** Save [bitmap] to the device's Pictures/PsychroChart folder via MediaStore. */
private fun saveChartToGallery(context: android.content.Context, bitmap: android.graphics.Bitmap) {
    val filename = "PsychroChart_${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE,    "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/PsychroChart")
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

@Composable
private fun LegendRow(
    color: Color,
    label: String,
    dashed: Boolean = false,
    lineWidth: Dp = 2.dp,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 18.dp, height = 10.dp)) {
            val y = size.height / 2f
            drawLine(
                color, Offset(0f, y), Offset(size.width, y),
                strokeWidth = lineWidth.toPx(),
                pathEffect = if (dashed)
                    PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface)
    }
}
