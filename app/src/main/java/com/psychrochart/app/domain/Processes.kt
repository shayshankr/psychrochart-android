package com.psychrochart.app.domain

import com.psychrochart.app.domain.PsychroCalc.fromDbtRh
import com.psychrochart.app.domain.PsychroCalc.fromDbtW
import com.psychrochart.app.domain.PsychroCalc.fromDbtWbt

object Processes {

    // ── Private format helpers (unit embedded in value string) ─────────────────

    private fun fmtTemp(c: Double, isIp: Boolean) =
        if (isIp) "%.1f °F".format(UnitConverter.cToF(c)) else "%.1f °C".format(c)

    private fun fmtDeltaT(deltaC: Double, isIp: Boolean) =
        if (isIp) "%.2f Δ°F".format(deltaC * 9.0 / 5.0) else "%.2f Δ°C".format(deltaC)

    private fun fmtEnthalpy(kjkg: Double, isIp: Boolean) =
        if (isIp) "%.3f BTU/lb".format(UnitConverter.kjkgToBtuLb(kjkg))
        else       "%.3f kJ/kg".format(kjkg)

    private fun fmtW(kgkg: Double, isIp: Boolean) =
        if (isIp) "%.2f gr/lb".format(UnitConverter.kgkgToGrLb(kgkg))
        else       "%.6f kg/kg".format(kgkg)

    private fun fmtLoad(kw: Double, isIp: Boolean) =
        if (isIp) "%.0f BTU/hr".format(UnitConverter.kwToBtuh(kw))
        else       "%.3f kW".format(kw)

    private fun fmtPressure(pa: Double, isIp: Boolean) =
        if (isIp) "%.3f inH₂O".format(UnitConverter.paToInH2O(pa))
        else       "%.1f Pa".format(pa)

    // ── Processes ──────────────────────────────────────────────────────────────

    fun sensibleHeating(
        s1: PsychroState,
        dbt2: Double,
        mDot: Double? = null,
        unitSystem: UnitSystem = UnitSystem.SI,
    ): ProcessResult {
        require(dbt2 > s1.dbt) {
            "Sensible heating target DBT (%.1f°C) must be greater than inlet DBT (%.1f°C)".format(dbt2, s1.dbt)
        }
        val s2 = fromDbtW(dbt2, s1.w)
        val isIp = unitSystem == UnitSystem.IP
        val metrics = mutableMapOf(
            "Heat Added"   to fmtEnthalpy(s2.h - s1.h, isIp),
            "ΔT"           to fmtDeltaT(dbt2 - s1.dbt, isIp),
            "W (constant)" to fmtW(s1.w, isIp),
        )
        if (mDot != null) {
            val kw = (s2.h - s1.h) * mDot
            metrics["Total Load"] = fmtLoad(kw, isIp)
            metrics["Total Load (TR)"] = "%.3f TR".format(kw / 3.5169)
        }
        return ProcessResult(state1 = s1, state2 = s2, processType = ProcessType.SENSIBLE_HEATING, metrics = metrics)
    }

    fun sensibleCooling(
        s1: PsychroState,
        dbt2: Double,
        mDot: Double? = null,
        unitSystem: UnitSystem = UnitSystem.SI,
    ): ProcessResult {
        require(dbt2 < s1.dbt) {
            "Sensible cooling target DBT (%.1f°C) must be less than inlet DBT (%.1f°C)".format(dbt2, s1.dbt)
        }
        val s2 = fromDbtW(dbt2, s1.w)
        val isIp = unitSystem == UnitSystem.IP
        val metrics = mutableMapOf(
            "Heat Removed" to fmtEnthalpy(s1.h - s2.h, isIp),
            "ΔT"           to fmtDeltaT(s1.dbt - dbt2, isIp),
            "W (constant)" to fmtW(s1.w, isIp),
        )
        if (mDot != null) {
            val kw = (s1.h - s2.h) * mDot
            metrics["Total Load"] = fmtLoad(kw, isIp)
            metrics["Total Load (TR)"] = "%.3f TR".format(kw / 3.5169)
        }
        return ProcessResult(state1 = s1, state2 = s2, processType = ProcessType.SENSIBLE_COOLING, metrics = metrics)
    }

    fun humidification(
        s1: PsychroState,
        w2: Double? = null,
        rh2Pct: Double? = null,
        unitSystem: UnitSystem = UnitSystem.SI,
    ): ProcessResult {
        val s2 = if (w2 != null) fromDbtW(s1.dbt, w2) else fromDbtRh(s1.dbt, rh2Pct!!)
        val isIp = unitSystem == UnitSystem.IP
        return ProcessResult(
            state1 = s1, state2 = s2,
            processType = ProcessType.HUMIDIFICATION,
            metrics = mapOf(
                "Moisture Added"  to fmtW(s2.w - s1.w, isIp),
                "Enthalpy Change" to fmtEnthalpy(s2.h - s1.h, isIp),
                "DBT (constant)"  to fmtTemp(s1.dbt, isIp),
            )
        )
    }

    fun dehumidification(
        s1: PsychroState,
        w2: Double? = null,
        rh2Pct: Double? = null,
        unitSystem: UnitSystem = UnitSystem.SI,
    ): ProcessResult {
        val s2 = if (w2 != null) fromDbtW(s1.dbt, w2) else fromDbtRh(s1.dbt, rh2Pct!!)
        val isIp = unitSystem == UnitSystem.IP
        return ProcessResult(
            state1 = s1, state2 = s2,
            processType = ProcessType.DEHUMIDIFICATION,
            metrics = mapOf(
                "Moisture Removed" to fmtW(s1.w - s2.w, isIp),
                "Enthalpy Change"  to fmtEnthalpy(s1.h - s2.h, isIp),
                "DBT (constant)"   to fmtTemp(s1.dbt, isIp),
            )
        )
    }

    fun coolingDehumidification(
        s1: PsychroState,
        dbt2: Double,
        w2: Double? = null,
        rh2Pct: Double? = null,
        mDot: Double? = null,
        unitSystem: UnitSystem = UnitSystem.SI,
    ): ProcessResult {
        val s2 = if (w2 != null) fromDbtW(dbt2, w2) else fromDbtRh(dbt2, rh2Pct!!)
        val totalHeat = s1.h - s2.h
        val deltaW    = s1.w - s2.w
        val sensible  = 1.006 * (s1.dbt - s2.dbt) + 1.86 * (s1.w * s1.dbt - s2.w * s2.dbt)
        val latent    = totalHeat - sensible
        val isIp = unitSystem == UnitSystem.IP
        val metrics = mutableMapOf(
            "Total Heat Removed"    to fmtEnthalpy(totalHeat, isIp),
            "Sensible Heat Removed" to fmtEnthalpy(sensible, isIp),
            "Latent Heat Removed"   to fmtEnthalpy(latent, isIp),
            "Moisture Removed"      to fmtW(deltaW, isIp),
        )
        if (mDot != null) {
            val kw = totalHeat * mDot
            metrics["Total Load"] = fmtLoad(kw, isIp)
            metrics["Total Load (TR)"] = "%.3f TR".format(kw / 3.5169)
        }
        return ProcessResult(state1 = s1, state2 = s2, processType = ProcessType.COOLING_DEHUMIDIFICATION, metrics = metrics)
    }

    fun heatingHumidification(
        s1: PsychroState,
        dbt2: Double,
        w2: Double? = null,
        rh2Pct: Double? = null,
        unitSystem: UnitSystem = UnitSystem.SI,
    ): ProcessResult {
        val s2 = if (w2 != null) fromDbtW(dbt2, w2) else fromDbtRh(dbt2, rh2Pct!!)
        val isIp = unitSystem == UnitSystem.IP
        return ProcessResult(
            state1 = s1, state2 = s2,
            processType = ProcessType.HEATING_HUMIDIFICATION,
            metrics = mapOf(
                "Total Heat Added" to fmtEnthalpy(s2.h - s1.h, isIp),
                "Moisture Added"   to fmtW(s2.w - s1.w, isIp),
                "ΔT"               to fmtDeltaT(s2.dbt - s1.dbt, isIp),
            )
        )
    }

    fun evaporativeCooling(
        s1: PsychroState,
        dbt2: Double,
        unitSystem: UnitSystem = UnitSystem.SI,
    ): ProcessResult {
        require(dbt2 >= s1.wbt) {
            "Evaporative cooling target DBT (%.1f°C) must be ≥ WBT (%.1f°C)".format(dbt2, s1.wbt)
        }
        require(dbt2 < s1.dbt) {
            "Evaporative cooling target DBT (%.1f°C) must be less than inlet DBT (%.1f°C)".format(dbt2, s1.dbt)
        }
        val s2 = fromDbtWbt(dbt2, s1.wbt)
        val isIp = unitSystem == UnitSystem.IP
        return ProcessResult(
            state1 = s1, state2 = s2,
            processType = ProcessType.EVAPORATIVE_COOLING,
            metrics = mapOf(
                "Temperature Drop"  to fmtDeltaT(s1.dbt - s2.dbt, isIp),
                "Moisture Added"    to fmtW(s2.w - s1.w, isIp),
                "WBT (constant)"    to fmtTemp(s1.wbt, isIp),
                "Enthalpy Change"   to fmtEnthalpy(s2.h - s1.h, isIp),
            )
        )
    }

    fun adiabaticMixing(
        s1: PsychroState,
        s2: PsychroState,
        m1: Double,
        m2: Double,
        unitSystem: UnitSystem = UnitSystem.SI,
    ): ProcessResult {
        val total = m1 + m2
        val wMix  = (m1 * s1.w + m2 * s2.w) / total
        val hMix  = (m1 * s1.h + m2 * s2.h) / total
        val dbtMix = (hMix - 2501.0 * wMix) / (1.006 + 1.86 * wMix)
        val sMix  = fromDbtW(dbtMix, wMix)
        val isIp = unitSystem == UnitSystem.IP
        return ProcessResult(
            state1 = s1, state2 = sMix,
            processType = ProcessType.ADIABATIC_MIXING,
            metrics = mapOf(
                "Mixed DBT"             to fmtTemp(dbtMix, isIp),
                "Mixed W"               to fmtW(wMix, isIp),
                "Mixed h"               to fmtEnthalpy(hMix, isIp),
                "Mass flow ratio m₁:m₂" to "$m1 : $m2",
            )
        )
    }

    fun fanHeatRise(
        s1: PsychroState,
        totalPressurePa: Double,
        fanEffPct: Double,
        mDot: Double? = null,
        unitSystem: UnitSystem = UnitSystem.SI,
    ): ProcessResult {
        require(totalPressurePa > 0) { "Fan total pressure must be positive." }
        require(fanEffPct in 5.0..100.0) { "Fan efficiency must be 5–100 %." }
        val eta    = fanEffPct / 100.0
        val deltaT = totalPressurePa * s1.v / (eta * 1000.0)
        val s2     = fromDbtW(s1.dbt + deltaT, s1.w)
        val isIp = unitSystem == UnitSystem.IP
        val metrics = mutableMapOf(
            "Temperature Rise"    to fmtDeltaT(deltaT, isIp),
            "Fan Total Pressure"  to fmtPressure(totalPressurePa, isIp),
            "Fan Efficiency"      to "%.1f %%".format(fanEffPct),
            "Inlet DBT"           to fmtTemp(s1.dbt, isIp),
            "Outlet DBT"          to fmtTemp(s2.dbt, isIp),
            "W (constant)"        to fmtW(s1.w, isIp),
        )
        if (mDot != null) {
            val powerKw = mDot * s1.v * totalPressurePa / (eta * 1000.0)
            metrics["Fan Power Input"] = fmtLoad(powerKw, isIp)
        }
        return ProcessResult(
            state1 = s1, state2 = s2,
            processType = ProcessType.FAN_HEAT_RISE, metrics = metrics,
        )
    }

    fun energyRecovery(
        oaState: PsychroState,
        exhaustState: PsychroState,
        sensEff: Double,
        latEff: Double,
        mDot: Double? = null,
        unitSystem: UnitSystem = UnitSystem.SI,
    ): ProcessResult {
        require(sensEff in 0.0..1.0) { "Sensible effectiveness must be 0.0–1.0." }
        require(latEff  in 0.0..1.0) { "Latent effectiveness must be 0.0–1.0." }
        val dbt2 = oaState.dbt + sensEff * (exhaustState.dbt - oaState.dbt)
        val w2   = maxOf(1e-7, oaState.w + latEff * (exhaustState.w - oaState.w))
        val s2   = fromDbtW(dbt2, w2)
        val heatRecovered = s2.h - oaState.h
        val isIp = unitSystem == UnitSystem.IP
        val metrics = mutableMapOf(
            "Sensible Effectiveness" to "%.1f %%".format(sensEff * 100),
            "Latent Effectiveness"   to "%.1f %%".format(latEff  * 100),
            "OA DBT in"              to fmtTemp(oaState.dbt, isIp),
            "OA DBT out"             to fmtTemp(dbt2, isIp),
            "OA W in"                to fmtW(oaState.w, isIp),
            "OA W out"               to fmtW(s2.w, isIp),
            "Exhaust DBT"            to fmtTemp(exhaustState.dbt, isIp),
            "Exhaust RH"             to "%.1f %%".format(exhaustState.rh),
            "Energy Recovered"       to fmtEnthalpy(heatRecovered, isIp),
        )
        if (mDot != null) {
            metrics["Recovery Load"] = fmtLoad(heatRecovered * mDot, isIp)
        }
        return ProcessResult(
            state1 = oaState, state2 = s2,
            processType = ProcessType.ENERGY_RECOVERY, metrics = metrics,
        )
    }

    fun coolingCoil(
        s1: PsychroState,
        adpTemp: Double,
        bypassFactor: Double,
        mDot: Double? = null,
        unitSystem: UnitSystem = UnitSystem.SI,
    ): ProcessResult {
        require(adpTemp < s1.dbt) { "ADP (%.1f°C) must be below entering DBT (%.1f°C).".format(adpTemp, s1.dbt) }
        require(bypassFactor in 0.0..0.99) { "Bypass factor must be 0.0–0.99." }
        val bf       = bypassFactor
        val adpState = fromDbtRh(adpTemp, 100.0)
        val dbt2     = bf * s1.dbt + (1.0 - bf) * adpTemp
        val w2       = maxOf(1e-7, bf * s1.w + (1.0 - bf) * adpState.w)
        val s2       = fromDbtW(dbt2, w2)
        val totalHeat = s1.h - s2.h
        val sensible  = 1.006 * (s1.dbt - s2.dbt) + 1.86 * (s1.w * s1.dbt - s2.w * s2.dbt)
        val latent    = totalHeat - sensible
        val isIp = unitSystem == UnitSystem.IP
        val metrics = mutableMapOf(
            "ADP Temperature"    to fmtTemp(adpTemp, isIp),
            "ADP Humidity Ratio" to fmtW(adpState.w, isIp),
            "Bypass Factor (BF)" to "%.3f".format(bf),
            "Contact Factor"     to "%.3f".format(1.0 - bf),
            "Leaving DBT"        to fmtTemp(dbt2, isIp),
            "Leaving W"          to fmtW(w2, isIp),
            "Total Heat Removed" to fmtEnthalpy(totalHeat, isIp),
            "Sensible Heat"      to fmtEnthalpy(sensible, isIp),
            "Latent Heat"        to fmtEnthalpy(latent, isIp),
            "Moisture Removed"   to fmtW(s1.w - s2.w, isIp),
        )
        if (mDot != null) {
            val kw = totalHeat * mDot
            metrics["Total Load"] = fmtLoad(kw, isIp)
            metrics["Total Load (TR)"] = "%.3f TR".format(kw / 3.5169)
        }
        return ProcessResult(
            state1 = s1, state2 = s2,
            processType = ProcessType.COOLING_COIL, metrics = metrics,
        )
    }
}
