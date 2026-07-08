package com.psychrochart.app.domain

import kotlin.math.*

data class Refrigerant(
    val name: String,
    val gwp: Int,
    val molarMass: Double,   // g/mol
    val tBpK: Double,        // Normal boiling point (K)
    val tCritK: Double,      // Critical temperature (K)
    val pCritKpa: Double,    // Critical pressure (kPa)
    val hVapNbp: Double,     // Latent heat at NBP (kJ/kg)
    val cpLiq: Double,       // Liquid Cp (kJ/kg·K)
    val cpVap: Double,       // Vapor Cp (kJ/kg·K)
    val gamma: Double,       // Cp/Cv
    val safetyClass: String,
    val notes: String = "",
)

object RefrigData {

    val refrigerants = listOf(
        Refrigerant("R-22",   1810, 86.47,  232.35, 369.30, 4990.0, 234.4, 1.19, 0.76, 1.18, "A1",  "HCFC — phased out"),
        Refrigerant("R-134a", 1430, 102.03, 246.78, 374.21, 4059.0, 217.1, 1.30, 0.90, 1.14, "A1",  "Common for chillers"),
        Refrigerant("R-410A", 2088, 72.58,  221.85, 344.49, 4902.6, 274.7, 1.44, 0.88, 1.18, "A1",  "Standard AC/HP"),
        Refrigerant("R-32",    675, 52.02,  221.50, 351.26, 5782.0, 382.5, 1.63, 1.07, 1.28, "A2L", "Low-GWP HFC"),
        Refrigerant("R-454B",  466, 58.25,  226.06, 345.25, 4940.0, 303.5, 1.52, 0.95, 1.18, "A2L", "R-410A replacement"),
        Refrigerant("R-290",     3, 44.10,  231.02, 369.83, 4247.0, 428.4, 2.45, 1.73, 1.14, "A3",  "Propane — natural"),
    )

    // β = ΔHvap_mol / R  (Clausius-Clapeyron constant, Kelvin)
    private fun beta(r: Refrigerant) =
        r.hVapNbp * (r.molarMass / 1000.0) / 0.008314

    // Saturation pressure via Clausius-Clapeyron (~2–5% accuracy vs REFPROP)
    fun pSatKpa(r: Refrigerant, tC: Double): Double {
        val T = tC + 273.15
        return 101.325 * exp(-beta(r) * (1.0 / T - 1.0 / r.tBpK))
    }

    // Latent heat at temperature T via Watson correlation
    fun hVapAt(r: Refrigerant, tC: Double): Double {
        val T  = tC + 273.15
        val tr = ((r.tCritK - T) / (r.tCritK - r.tBpK)).coerceAtLeast(0.0)
        return r.hVapNbp * tr.pow(0.38)
    }

    // Enthalpy of saturated liquid (IIR reference: hf = 200 kJ/kg at 0 °C)
    fun hfAt(r: Refrigerant, tC: Double) = 200.0 + r.cpLiq * tC

    // Enthalpy of saturated vapour
    fun hgAt(r: Refrigerant, tC: Double) = hfAt(r, tC) + hVapAt(r, tC)

    data class CycleResult(
        val pEvapKpa: Double,
        val pCondKpa: Double,
        val pressureRatio: Double,
        val h1: Double, val h2: Double, val h3: Double, val h4: Double,
        val tDischC: Double,
        val copCooling: Double,
        val copHeating: Double,
        val eerBtuWh: Double,
        val kwPerTr: Double,
        val massFlowPerKwKgs: Double,  // kg/s per kW cooling
    )

    fun calculateCycle(
        r: Refrigerant,
        tEvapC: Double,
        tCondC: Double,
        superHeatK: Double = 5.0,
        subCoolK: Double  = 5.0,
        etaIs: Double     = 0.75,
    ): CycleResult {
        // State 1: superheated vapour at compressor suction
        val h1  = hgAt(r, tEvapC) + r.cpVap * superHeatK
        val T1K = tEvapC + 273.15 + superHeatK
        val pE  = pSatKpa(r, tEvapC)
        val pC  = pSatKpa(r, tCondC)
        val pr  = pC / pE

        // State 2: after compression (isentropic → actual)
        val T2sK = T1K * pr.pow((r.gamma - 1.0) / r.gamma)
        val h2s  = h1 + r.cpVap * (T2sK - T1K)
        val h2   = h1 + (h2s - h1) / etaIs
        val T2K  = T1K + (h2 - h1) / r.cpVap

        // State 3: subcooled liquid leaving condenser
        val h3 = hfAt(r, tCondC) - r.cpLiq * subCoolK

        // State 4: after isenthalpic expansion
        val h4 = h3

        val copC = (h1 - h4) / (h2 - h1)
        val copH = (h2 - h3) / (h2 - h1)

        return CycleResult(
            pEvapKpa        = pE,
            pCondKpa        = pC,
            pressureRatio   = pr,
            h1 = h1, h2 = h2, h3 = h3, h4 = h4,
            tDischC         = T2K - 273.15,
            copCooling      = copC,
            copHeating      = copH,
            eerBtuWh        = copC * 3.412,
            kwPerTr         = 3.516 / copC,
            massFlowPerKwKgs = 1.0 / (h1 - h4),
        )
    }
}
