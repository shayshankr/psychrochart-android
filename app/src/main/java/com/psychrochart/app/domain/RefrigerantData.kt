package com.psychrochart.app.domain

data class RefrigerantInfo(
    val name: String,
    val gwp: Int,
    val status: String,
    val typicalApp: String,
    /** Saturation pressure in bar at temperatures -20,-15,-10,-5,0,5,10,15,20,25,30,35,40,45,50,55,60 °C */
    val psatBar: List<Double>,
)

val temps = intArrayOf(-20, -15, -10, -5, 0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60)

val refrigerants: List<RefrigerantInfo> = listOf(
    RefrigerantInfo(
        name = "R-410A", gwp = 2088, status = "Being phased down (HFC)",
        typicalApp = "Split ACs, chillers (being replaced by R-32)",
        psatBar = listOf(2.7, 3.4, 4.2, 5.2, 6.3, 7.6, 9.1, 10.8, 12.8, 15.0, 17.5, 20.3, 23.5, 27.0, 30.9, 35.2, 39.9),
    ),
    RefrigerantInfo(
        name = "R-32", gwp = 675, status = "Growing use — lower GWP HFC",
        typicalApp = "Inverter split ACs, VRF systems",
        psatBar = listOf(1.7, 2.2, 2.8, 3.5, 4.4, 5.4, 6.6, 8.1, 9.7, 11.6, 13.8, 16.2, 19.0, 22.1, 25.5, 29.4, 33.6),
    ),
    RefrigerantInfo(
        name = "R-22", gwp = 1810, status = "Phased out (HCFC) — use R-410A or R-32",
        typicalApp = "Legacy equipment only",
        psatBar = listOf(1.6, 2.0, 2.5, 3.1, 3.9, 4.7, 5.8, 7.0, 8.4, 10.0, 11.9, 14.0, 16.4, 19.1, 22.1, 25.5, 29.2),
    ),
    RefrigerantInfo(
        name = "R-134a", gwp = 1430, status = "Being phased (HFC) — use R-1234yf",
        typicalApp = "Chillers, automotive, commercial refrigeration",
        psatBar = listOf(0.8, 1.1, 1.4, 1.8, 2.2, 2.8, 3.5, 4.3, 5.3, 6.4, 7.7, 9.2, 10.9, 12.9, 15.1, 17.6, 20.3),
    ),
    RefrigerantInfo(
        name = "R-600a", gwp = 3, status = "Recommended — natural refrigerant",
        typicalApp = "Domestic refrigerators, small freezers",
        psatBar = listOf(0.4, 0.5, 0.7, 0.9, 1.1, 1.4, 1.7, 2.1, 2.6, 3.2, 3.8, 4.6, 5.5, 6.5, 7.7, 9.1, 10.6),
    ),
    RefrigerantInfo(
        name = "R-290 (Propane)", gwp = 3, status = "Recommended — natural refrigerant",
        typicalApp = "Commercial refrigeration, heat pumps",
        psatBar = listOf(1.1, 1.5, 1.9, 2.5, 3.1, 3.9, 4.8, 5.9, 7.2, 8.7, 10.4, 12.4, 14.7, 17.3, 20.2, 23.5, 27.1),
    ),
)

/** Interpolate saturation pressure for a given temperature in °C */
fun RefrigerantInfo.psatAtTemp(tC: Double): Double {
    val tMin = temps.first().toDouble()
    val tMax = temps.last().toDouble()
    val tClamped = tC.coerceIn(tMin, tMax)
    val step = 5.0
    val idx = ((tClamped - tMin) / step).toInt().coerceIn(0, psatBar.size - 2)
    val frac = ((tClamped - tMin) / step) - idx
    return psatBar[idx] * (1 - frac) + psatBar[idx + 1] * frac
}
