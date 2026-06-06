package com.psychrochart.app.domain

import kotlin.math.*

enum class PipeFluid(val label: String, val rho: Double, val mu: Double) {
    CHW_SUPPLY ("CHW Supply (6 °C)",  999.8, 1.307e-3),
    CHW_RETURN ("CHW Return (12 °C)", 999.5, 1.235e-3),
    LTHW_50    ("HHW 50 °C",          988.0, 0.548e-3),
    MTHW_80    ("HHW 80 °C",          971.8, 0.355e-3),
    CW_32      ("Condenser 32 °C",    995.0, 0.765e-3),
    CW_38      ("Condenser 38 °C",    992.5, 0.672e-3),
}

enum class PipeMaterial(val label: String, val eps: Double) {
    COPPER       ("Copper",        1.5e-6),
    GI_STEEL     ("GI / MS Steel", 4.6e-5),
    CPVC         ("CPVC / uPVC",   1.5e-6),
    STAINLESS    ("Stainless Steel",1.5e-6),
}

data class PipeSizingResult(
    val diameterMm: Double,
    val velocityMs: Double,
    val frictionPaPerM: Double,
    val reynoldsNumber: Double,
    val recommendedNbMm: Int,      // next standard NB above calculated diameter
    val velocityAtNb: Double,      // actual velocity at recommended NB
    val frictionAtNb: Double,      // actual friction at recommended NB
)

private val standardNbMm = intArrayOf(
    15, 20, 25, 32, 40, 50, 65, 80, 100, 125, 150, 200, 250, 300
)

object PipeSizer {
    private fun ff(re: Double, d: Double, eps: Double): Double {
        val arg = eps / (3.7 * d) + 5.74 / re.pow(0.9)
        return if (arg > 1e-10) 0.25 / log10(arg).pow(2)
               else             0.316 / re.pow(0.25)
    }

    private fun darcy(f: Double, d: Double, v: Double, rho: Double) = f * rho * v * v / (2.0 * d)

    fun solveEqualFriction(
        qLs: Double,
        targetPaPerM: Double,
        fluid: PipeFluid,
        material: PipeMaterial,
    ): PipeSizingResult {
        require(qLs > 0)          { "Flow must be positive." }
        require(targetPaPerM > 0) { "Friction rate must be positive." }
        val q = qLs * 1e-3
        val rho = fluid.rho; val mu = fluid.mu; val eps = material.eps
        // Iterate: D^5 = 8·f·ρ·Q² / (π²·ΔP/L)
        var d = (8 * 0.02 * rho * q * q / (PI * PI * targetPaPerM)).pow(0.2)
        repeat(30) {
            val v  = 4 * q / (PI * d * d)
            val re = rho * v * d / mu
            d = (8 * ff(re, d, eps) * rho * q * q / (PI * PI * targetPaPerM)).pow(0.2)
        }
        return finish(q, d, fluid, material)
    }

    fun solveVelocity(
        qLs: Double,
        velocityMs: Double,
        fluid: PipeFluid,
        material: PipeMaterial,
    ): PipeSizingResult {
        require(qLs > 0)        { "Flow must be positive." }
        require(velocityMs > 0) { "Velocity must be positive." }
        val q = qLs * 1e-3
        val d = sqrt(4 * q / (PI * velocityMs))
        return finish(q, d, fluid, material)
    }

    private fun finish(q: Double, d: Double, fluid: PipeFluid, material: PipeMaterial): PipeSizingResult {
        val rho = fluid.rho; val mu = fluid.mu; val eps = material.eps
        val v  = 4 * q / (PI * d * d)
        val re = rho * v * d / mu
        val fp = darcy(ff(re, d, eps), d, v, rho)

        val nbMm = standardNbMm.firstOrNull { it >= d * 1000 } ?: standardNbMm.last()
        val dNb  = nbMm * 1e-3
        val vNb  = 4 * q / (PI * dNb * dNb)
        val reNb = rho * vNb * dNb / mu
        val fpNb = darcy(ff(reNb, dNb, eps), dNb, vNb, rho)

        return PipeSizingResult(d * 1000, v, fp, re, nbMm, vNb, fpNb)
    }
}
