package com.psychrochart.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.pow

enum class UnitSystem { SI, IP }

object AppSettings {
    private val _unitSystem = MutableStateFlow(UnitSystem.SI)
    val unitSystem: StateFlow<UnitSystem> = _unitSystem

    private val _altitudeM = MutableStateFlow(0.0)
    val altitudeM: StateFlow<Double> = _altitudeM

    fun setUnitSystem(us: UnitSystem) { _unitSystem.value = us }

    fun setAltitude(m: Double) {
        val clamped = m.coerceIn(0.0, 8849.0)
        _altitudeM.value = clamped
        PsychroCalc.updatePressure(pressureAtAltitude(clamped))
    }

    fun pressureAtAltitude(altM: Double): Double =
        101325.0 * (1.0 - 2.25577e-5 * altM).pow(5.25588)
}
