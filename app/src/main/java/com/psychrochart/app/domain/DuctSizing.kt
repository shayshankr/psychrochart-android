package com.psychrochart.app.domain

import kotlin.math.*

data class DuctSizingResult(
    val isRound: Boolean,
    /** Round duct: actual diameter. Rectangular: equivalent round diameter (De). */
    val diameterMm: Double,
    val widthMm: Double?,
    val heightMm: Double?,
    val hydraulicDiamMm: Double?,
    val velocityMs: Double,
    val frictionPaPerM: Double,
    val reynoldsNumber: Double,
)

object DuctSizer {
    private const val RHO = 1.2       // kg/m³  standard air
    private const val MU  = 1.81e-5   // Pa·s   dynamic viscosity
    private const val EPS = 9e-5      // m      galvanized steel roughness (0.09 mm)

    // Swamee-Jain explicit friction factor (valid Re 5000–1e8, ε/D 1e-6–0.05)
    private fun ff(re: Double, d: Double): Double {
        val arg = EPS / (3.7 * d) + 5.74 / re.pow(0.9)
        return if (arg > 1e-10) 0.25 / log10(arg).pow(2)
               else             0.316 / re.pow(0.25)   // Blasius fallback
    }

    private fun darcy(f: Double, d: Double, v: Double) = f * RHO * v * v / (2.0 * d)

    // ── Round duct ────────────────────────────────────────────────────────────────

    fun solveRoundEqualFriction(qM3s: Double, targetPaPerM: Double): DuctSizingResult {
        require(qM3s > 0)         { "Airflow must be positive." }
        require(targetPaPerM > 0) { "Friction rate must be positive." }
        // Iterative solve: D from ΔP/L = 8·f·ρ·Q² / (π²·D⁵)
        var d = (8 * 0.02 * RHO * qM3s * qM3s / (PI * PI * targetPaPerM)).pow(0.2)
        repeat(30) {
            val v  = 4 * qM3s / (PI * d * d)
            val re = RHO * v * d / MU
            d = (8 * ff(re, d) * RHO * qM3s * qM3s / (PI * PI * targetPaPerM)).pow(0.2)
        }
        return finishRound(qM3s, d)
    }

    fun solveRoundVelocity(qM3s: Double, velocityMs: Double): DuctSizingResult {
        require(qM3s > 0)      { "Airflow must be positive." }
        require(velocityMs > 0) { "Velocity must be positive." }
        val d = sqrt(4 * qM3s / (PI * velocityMs))
        return finishRound(qM3s, d)
    }

    private fun finishRound(qM3s: Double, d: Double): DuctSizingResult {
        val v  = 4 * qM3s / (PI * d * d)
        val re = RHO * v * d / MU
        return DuctSizingResult(
            isRound = true, diameterMm = d * 1000,
            widthMm = null, heightMm = null, hydraulicDiamMm = null,
            velocityMs = v, frictionPaPerM = darcy(ff(re, d), d, v), reynoldsNumber = re,
        )
    }

    // ── Rectangular duct ──────────────────────────────────────────────────────────

    fun solveRectEqualFriction(qM3s: Double, targetPaPerM: Double, ratio: Double): DuctSizingResult {
        require(qM3s > 0)         { "Airflow must be positive." }
        require(targetPaPerM > 0) { "Friction rate must be positive." }
        require(ratio >= 1.0)     { "Aspect ratio must be ≥ 1.0." }
        // Find equivalent round De, then convert to W×H via ASHRAE formula
        // De = 1.30·(W·H)^0.625 / (W+H)^0.25  where W = ratio·H
        // → H = De·(ratio+1)^0.25 / (1.30·ratio^0.625)
        val de = solveRoundEqualFriction(qM3s, targetPaPerM).diameterMm / 1000.0
        val h  = de * (ratio + 1).pow(0.25) / (1.30 * ratio.pow(0.625))
        val w  = ratio * h
        return finishRect(qM3s, w, h, de)
    }

    fun solveRectVelocity(qM3s: Double, velocityMs: Double, ratio: Double): DuctSizingResult {
        require(qM3s > 0)       { "Airflow must be positive." }
        require(velocityMs > 0) { "Velocity must be positive." }
        require(ratio >= 1.0)   { "Aspect ratio must be ≥ 1.0." }
        // WH = Q/V, W = ratio·H  →  H = √(Q/(V·ratio))
        val h  = sqrt(qM3s / (velocityMs * ratio))
        val w  = ratio * h
        val de = 1.30 * (w * h).pow(0.625) / (w + h).pow(0.25)
        return finishRect(qM3s, w, h, de)
    }

    private fun finishRect(qM3s: Double, w: Double, h: Double, de: Double): DuctSizingResult {
        val dh = 2 * w * h / (w + h)
        val v  = qM3s / (w * h)
        val re = RHO * v * dh / MU
        return DuctSizingResult(
            isRound = false, diameterMm = de * 1000,
            widthMm = w * 1000, heightMm = h * 1000, hydraulicDiamMm = dh * 1000,
            velocityMs = v, frictionPaPerM = darcy(ff(re, dh), dh, v), reynoldsNumber = re,
        )
    }
}
