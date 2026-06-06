package com.psychrochart.app.domain

import com.psychrochart.app.domain.PsychroCalc.fromDbtRh
import com.psychrochart.app.domain.PsychroCalc.fromDbtW
import com.psychrochart.app.domain.PsychroCalc.fromDbtWbt

object Processes {

    fun sensibleHeating(s1: PsychroState, dbt2: Double, mDot: Double? = null): ProcessResult {
        require(dbt2 > s1.dbt) {
            "Sensible heating target DBT (%.1f°C) must be greater than inlet DBT (%.1f°C)".format(dbt2, s1.dbt)
        }
        val s2 = fromDbtW(dbt2, s1.w)
        val metrics = mutableMapOf(
            "Heat Added (kJ/kg dry air)" to "%.3f".format(s2.h - s1.h),
            "ΔT (°C)"                   to "%.2f".format(dbt2 - s1.dbt),
            "W constant (kg/kg)"        to "%.6f".format(s1.w),
        )
        if (mDot != null) {
            val kw = (s2.h - s1.h) * mDot
            metrics["Total Load (kW)"] = "%.3f".format(kw)
            metrics["Total Load (TR)"] = "%.3f".format(kw / 3.5169)
        }
        return ProcessResult(state1 = s1, state2 = s2, processType = ProcessType.SENSIBLE_HEATING, metrics = metrics)
    }

    fun sensibleCooling(s1: PsychroState, dbt2: Double, mDot: Double? = null): ProcessResult {
        require(dbt2 < s1.dbt) {
            "Sensible cooling target DBT (%.1f°C) must be less than inlet DBT (%.1f°C)".format(dbt2, s1.dbt)
        }
        val s2 = fromDbtW(dbt2, s1.w)
        val metrics = mutableMapOf(
            "Heat Removed (kJ/kg dry air)" to "%.3f".format(s1.h - s2.h),
            "ΔT (°C)"                      to "%.2f".format(s1.dbt - dbt2),
            "W constant (kg/kg)"           to "%.6f".format(s1.w),
        )
        if (mDot != null) {
            val kw = (s1.h - s2.h) * mDot
            metrics["Total Load (kW)"] = "%.3f".format(kw)
            metrics["Total Load (TR)"] = "%.3f".format(kw / 3.5169)
        }
        return ProcessResult(state1 = s1, state2 = s2, processType = ProcessType.SENSIBLE_COOLING, metrics = metrics)
    }

    fun humidification(s1: PsychroState, w2: Double? = null, rh2Pct: Double? = null): ProcessResult {
        val s2 = if (w2 != null) fromDbtW(s1.dbt, w2) else fromDbtRh(s1.dbt, rh2Pct!!)
        return ProcessResult(
            state1 = s1, state2 = s2,
            processType = ProcessType.HUMIDIFICATION,
            metrics = mapOf(
                "Moisture Added (kg/kg dry air)" to "%.6f".format(s2.w - s1.w),
                "Enthalpy Change (kJ/kg)"        to "%.3f".format(s2.h - s1.h),
                "DBT constant (°C)"              to "%.2f".format(s1.dbt),
            )
        )
    }

    fun dehumidification(s1: PsychroState, w2: Double? = null, rh2Pct: Double? = null): ProcessResult {
        val s2 = if (w2 != null) fromDbtW(s1.dbt, w2) else fromDbtRh(s1.dbt, rh2Pct!!)
        return ProcessResult(
            state1 = s1, state2 = s2,
            processType = ProcessType.DEHUMIDIFICATION,
            metrics = mapOf(
                "Moisture Removed (kg/kg dry air)" to "%.6f".format(s1.w - s2.w),
                "Enthalpy Change (kJ/kg)"          to "%.3f".format(s1.h - s2.h),
                "DBT constant (°C)"                to "%.2f".format(s1.dbt),
            )
        )
    }

    fun coolingDehumidification(s1: PsychroState, dbt2: Double, w2: Double? = null, rh2Pct: Double? = null, mDot: Double? = null): ProcessResult {
        val s2 = if (w2 != null) fromDbtW(dbt2, w2) else fromDbtRh(dbt2, rh2Pct!!)
        val totalHeat = s1.h - s2.h
        val deltaW    = s1.w - s2.w
        val sensible  = 1.006 * (s1.dbt - s2.dbt) + 1.86 * (s1.w * s1.dbt - s2.w * s2.dbt)
        val latent    = totalHeat - sensible
        val metrics = mutableMapOf(
            "Total Heat Removed (kJ/kg)"    to "%.3f".format(totalHeat),
            "Sensible Heat Removed (kJ/kg)" to "%.3f".format(sensible),
            "Latent Heat Removed (kJ/kg)"   to "%.3f".format(latent),
            "Moisture Removed (kg/kg)"      to "%.6f".format(deltaW),
        )
        if (mDot != null) {
            val kw = totalHeat * mDot
            metrics["Total Load (kW)"] = "%.3f".format(kw)
            metrics["Total Load (TR)"] = "%.3f".format(kw / 3.5169)
        }
        return ProcessResult(state1 = s1, state2 = s2, processType = ProcessType.COOLING_DEHUMIDIFICATION, metrics = metrics)
    }

    fun heatingHumidification(s1: PsychroState, dbt2: Double, w2: Double? = null, rh2Pct: Double? = null): ProcessResult {
        val s2 = if (w2 != null) fromDbtW(dbt2, w2) else fromDbtRh(dbt2, rh2Pct!!)
        return ProcessResult(
            state1 = s1, state2 = s2,
            processType = ProcessType.HEATING_HUMIDIFICATION,
            metrics = mapOf(
                "Total Heat Added (kJ/kg)"  to "%.3f".format(s2.h - s1.h),
                "Moisture Added (kg/kg)"    to "%.6f".format(s2.w - s1.w),
                "ΔT (°C)"                  to "%.2f".format(s2.dbt - s1.dbt),
            )
        )
    }

    fun evaporativeCooling(s1: PsychroState, dbt2: Double): ProcessResult {
        require(dbt2 >= s1.wbt) {
            "Evaporative cooling target DBT (%.1f°C) must be ≥ WBT (%.1f°C)".format(dbt2, s1.wbt)
        }
        require(dbt2 < s1.dbt) {
            "Evaporative cooling target DBT (%.1f°C) must be less than inlet DBT (%.1f°C)".format(dbt2, s1.dbt)
        }
        val s2 = fromDbtWbt(dbt2, s1.wbt)
        return ProcessResult(
            state1 = s1, state2 = s2,
            processType = ProcessType.EVAPORATIVE_COOLING,
            metrics = mapOf(
                "Temperature Drop (°C)"   to "%.2f".format(s1.dbt - s2.dbt),
                "Moisture Added (kg/kg)"  to "%.6f".format(s2.w - s1.w),
                "WBT constant (°C)"       to "%.2f".format(s1.wbt),
                "Enthalpy Change (kJ/kg)" to "%.3f".format(s2.h - s1.h),
            )
        )
    }

    fun adiabaticMixing(s1: PsychroState, s2: PsychroState, m1: Double, m2: Double): ProcessResult {
        val total = m1 + m2
        val wMix  = (m1 * s1.w + m2 * s2.w) / total
        val hMix  = (m1 * s1.h + m2 * s2.h) / total
        val dbtMix = (hMix - 2501.0 * wMix) / (1.006 + 1.86 * wMix)
        val sMix  = fromDbtW(dbtMix, wMix)
        return ProcessResult(
            state1 = s1, state2 = sMix,
            processType = ProcessType.ADIABATIC_MIXING,
            metrics = mapOf(
                "Mixed DBT (°C)"        to "%.2f".format(dbtMix),
                "Mixed W (kg/kg)"       to "%.6f".format(wMix),
                "Mixed h (kJ/kg)"       to "%.3f".format(hMix),
                "Mass flow ratio m₁:m₂" to "$m1 : $m2",
            )
        )
    }

    // ── New processes ──────────────────────────────────────────────────────────

    /**
     * Fan Heat Rise — temperature rise due to fan motor inefficiency.
     * ΔT = ΔP × v / (η × 1000)  where ΔP is in Pa, v in m³/kg, η is fraction.
     */
    fun fanHeatRise(
        s1: PsychroState,
        totalPressurePa: Double,
        fanEffPct: Double,
        mDot: Double? = null,
    ): ProcessResult {
        require(totalPressurePa > 0) { "Fan total pressure must be positive." }
        require(fanEffPct in 5.0..100.0) { "Fan efficiency must be 5–100 %." }
        val eta    = fanEffPct / 100.0
        val deltaT = totalPressurePa * s1.v / (eta * 1000.0)
        val s2     = fromDbtW(s1.dbt + deltaT, s1.w)
        val metrics = mutableMapOf(
            "Temperature Rise (°C)"   to "%.3f".format(deltaT),
            "Fan Total Pressure (Pa)" to "%.1f".format(totalPressurePa),
            "Fan Efficiency (%)"      to "%.1f".format(fanEffPct),
            "Inlet DBT (°C)"          to "%.2f".format(s1.dbt),
            "Outlet DBT (°C)"         to "%.2f".format(s2.dbt),
            "W constant (kg/kg)"      to "%.6f".format(s1.w),
        )
        if (mDot != null) {
            val powerKw = mDot * s1.v * totalPressurePa / (eta * 1000.0)
            metrics["Fan Power Input (kW)"] = "%.3f".format(powerKw)
        }
        return ProcessResult(
            state1 = s1, state2 = s2,
            processType = ProcessType.FAN_HEAT_RISE, metrics = metrics,
        )
    }

    /**
     * Energy Recovery Ventilator (ERV / HRV).
     * Pre-conditions outdoor air using exhaust stream via sensible + latent effectiveness.
     */
    fun energyRecovery(
        oaState: PsychroState,
        exhaustState: PsychroState,
        sensEff: Double,    // 0.0–1.0
        latEff: Double,     // 0.0–1.0
        mDot: Double? = null,
    ): ProcessResult {
        require(sensEff in 0.0..1.0) { "Sensible effectiveness must be 0.0–1.0." }
        require(latEff  in 0.0..1.0) { "Latent effectiveness must be 0.0–1.0." }
        val dbt2 = oaState.dbt + sensEff * (exhaustState.dbt - oaState.dbt)
        val w2   = maxOf(1e-7, oaState.w + latEff * (exhaustState.w - oaState.w))
        val s2   = fromDbtW(dbt2, w2)
        val heatRecovered = s2.h - oaState.h
        val metrics = mutableMapOf(
            "Sensible Effectiveness (%)"  to "%.1f".format(sensEff * 100),
            "Latent Effectiveness (%)"    to "%.1f".format(latEff  * 100),
            "OA DBT in (°C)"              to "%.2f".format(oaState.dbt),
            "OA DBT out (°C)"             to "%.2f".format(dbt2),
            "OA W in (kg/kg)"             to "%.6f".format(oaState.w),
            "OA W out (kg/kg)"            to "%.6f".format(s2.w),
            "Exhaust DBT (°C)"            to "%.2f".format(exhaustState.dbt),
            "Exhaust RH (%)"              to "%.1f".format(exhaustState.rh),
            "Energy Recovered (kJ/kg)"    to "%.3f".format(heatRecovered),
        )
        if (mDot != null) {
            metrics["Recovery Load (kW)"] = "%.3f".format(heatRecovered * mDot)
        }
        return ProcessResult(
            state1 = oaState, state2 = s2,
            processType = ProcessType.ENERGY_RECOVERY, metrics = metrics,
        )
    }

    /**
     * Cooling Coil with Apparatus Dew Point (ADP) and Bypass Factor (BF).
     * Leaving air = mix of bypassed fraction (s1) and fully-cooled fraction (ADP saturation state).
     */
    fun coolingCoil(
        s1: PsychroState,
        adpTemp: Double,
        bypassFactor: Double,   // 0.0–1.0
        mDot: Double? = null,
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
        val metrics = mutableMapOf(
            "ADP Temperature (°C)"        to "%.2f".format(adpTemp),
            "ADP Humidity Ratio (kg/kg)"  to "%.6f".format(adpState.w),
            "Bypass Factor (BF)"          to "%.3f".format(bf),
            "Contact Factor (1–BF)"       to "%.3f".format(1.0 - bf),
            "Leaving DBT (°C)"            to "%.2f".format(dbt2),
            "Leaving W (kg/kg)"           to "%.6f".format(w2),
            "Total Heat Removed (kJ/kg)"  to "%.3f".format(totalHeat),
            "Sensible Heat (kJ/kg)"       to "%.3f".format(sensible),
            "Latent Heat (kJ/kg)"         to "%.3f".format(latent),
            "Moisture Removed (kg/kg)"    to "%.6f".format(s1.w - s2.w),
        )
        if (mDot != null) {
            val kw = totalHeat * mDot
            metrics["Total Load (kW)"] = "%.3f".format(kw)
            metrics["Total Load (TR)"] = "%.3f".format(kw / 3.5169)
        }
        return ProcessResult(
            state1 = s1, state2 = s2,
            processType = ProcessType.COOLING_COIL, metrics = metrics,
        )
    }
}
