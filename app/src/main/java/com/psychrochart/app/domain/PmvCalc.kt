package com.psychrochart.app.domain

import kotlin.math.*

object PmvCalc {

    data class Result(
        val pmv: Double,
        val ppd: Double,
        val tClothes: Double,
        val hConv: Double,
    )

    fun calculate(
        ta: Double,   // air temperature °C
        tr: Double,   // mean radiant temperature °C
        va: Double,   // air velocity m/s
        rh: Double,   // relative humidity %
        met: Double,  // metabolic rate (met); 1 met = 58.15 W/m²
        clo: Double,  // clothing insulation (clo); 1 clo = 0.155 m²K/W
    ): Result {
        val M   = met * 58.15
        val W   = 0.0
        val Icl = clo * 0.155
        val mw  = M - W
        val fcl = if (Icl <= 0.078) 1.0 + 1.29 * Icl else 1.05 + 0.645 * Icl
        val hcf = 12.1 * sqrt(va.coerceAtLeast(0.001))

        // Vapor pressure (Pa) — Antoine approximation
        val pvSat = 133.322 * exp(18.956 - 4030.18 / (ta + 235.0))
        val pa    = (rh / 100.0) * pvSat

        // Iterative solution for clothing surface temperature
        var tcl = ta + (35.5 - ta) / (3.5 * (6.45 * Icl + 0.1))
        repeat(150) {
            val hc     = maxOf(hcf, 2.38 * abs(tcl - ta).pow(0.25))
            val tclNew = 35.7 - 0.028 * mw - Icl * (
                3.96e-8 * fcl * ((tcl + 273.0).pow(4) - (tr + 273.0).pow(4)) +
                fcl * hc * (tcl - ta))
            tcl = (tcl + tclNew) / 2.0
        }
        val hc = maxOf(hcf, 2.38 * abs(tcl - ta).pow(0.25))

        // Heat loss components (Fanger / ISO 7730)
        val hl1 = 3.05e-3 * (5733.0 - 6.99 * mw - pa)                          // skin diffusion
        val hl2 = if (mw > 58.15) 0.42 * (mw - 58.15) else 0.0                 // sweat evaporation
        val hl3 = 1.7e-5 * M * (5867.0 - pa)                                    // respiration latent
        val hl4 = 0.0014 * M * (34.0 - ta)                                      // respiration sensible
        val hl5 = 3.96e-8 * fcl * ((tcl + 273.0).pow(4) - (tr + 273.0).pow(4)) // radiation
        val hl6 = fcl * hc * (tcl - ta)                                         // convection

        val pmv = (0.303 * exp(-0.036 * M) + 0.028) * (mw - hl1 - hl2 - hl3 - hl4 - hl5 - hl6)
        val ppd = (100.0 - 95.0 * exp(-0.03353 * pmv.pow(4) - 0.2179 * pmv.pow(2))).coerceIn(5.0, 100.0)

        return Result(
            pmv      = pmv.coerceIn(-3.5, 3.5),
            ppd      = ppd,
            tClothes = tcl,
            hConv    = hc,
        )
    }

    fun category(pmv: Double) = when {
        abs(pmv) <= 0.2 -> "A  —  < 6% dissatisfied  (excellent)"
        abs(pmv) <= 0.5 -> "B  —  < 10% dissatisfied  (good)"
        abs(pmv) <= 0.7 -> "C  —  < 15% dissatisfied  (acceptable)"
        else             -> "Outside acceptable range"
    }

    fun sensation(pmv: Double) = when {
        pmv < -2.5 -> "Cold"
        pmv < -1.5 -> "Cool"
        pmv < -0.5 -> "Slightly Cool"
        pmv <=  0.5 -> "Neutral"
        pmv <=  1.5 -> "Slightly Warm"
        pmv <=  2.5 -> "Warm"
        else         -> "Hot"
    }
}
