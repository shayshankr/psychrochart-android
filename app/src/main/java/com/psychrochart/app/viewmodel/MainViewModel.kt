package com.psychrochart.app.viewmodel

import androidx.lifecycle.ViewModel
import com.psychrochart.app.domain.*
import com.psychrochart.app.domain.PsychroCalc.fromDbtDpt
import com.psychrochart.app.domain.PsychroCalc.fromDbtH
import com.psychrochart.app.domain.PsychroCalc.fromDbtRh
import com.psychrochart.app.domain.PsychroCalc.fromDbtV
import com.psychrochart.app.domain.PsychroCalc.fromDbtW
import com.psychrochart.app.domain.PsychroCalc.fromDbtWbt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel : ViewModel() {

    // ── State point calculator ─────────────────────────────────────────────────

    private val _stateResult = MutableStateFlow<PsychroState?>(null)
    val stateResult: StateFlow<PsychroState?> = _stateResult.asStateFlow()

    private val _stateError = MutableStateFlow<String?>(null)
    val stateError: StateFlow<String?> = _stateError.asStateFlow()

    fun calculateState(dbt: Double, secondary: SecondaryInput, value: Double) {
        runCatching {
            when (secondary) {
                SecondaryInput.WBT -> fromDbtWbt(dbt, value)
                SecondaryInput.DPT -> fromDbtDpt(dbt, value)
                SecondaryInput.RH  -> fromDbtRh(dbt, value)
                SecondaryInput.W   -> fromDbtW(dbt, value)
                SecondaryInput.V   -> fromDbtV(dbt, value)
                SecondaryInput.H   -> fromDbtH(dbt, value)
            }
        }.onSuccess { state ->
            _stateResult.value = state
            _stateError.value = null
            addPlottedState(state)
        }.onFailure { e ->
            _stateError.value = "Calculation error: ${e.message}"
        }
    }

    // ── Process analysis ──────────────────────────────────────────────────────

    private val _processResult = MutableStateFlow<ProcessResult?>(null)
    val processResult: StateFlow<ProcessResult?> = _processResult.asStateFlow()

    private val _processError = MutableStateFlow<String?>(null)
    val processError: StateFlow<String?> = _processError.asStateFlow()

    fun runProcess(
        processType: ProcessType,
        s1: PsychroState,
        param1: Double,
        param2: Double? = null,
        param3: Double? = null,
        useW: Boolean = true,
        param4: Double? = null,
        mixSec2: SecondaryInput = SecondaryInput.W,
    ) {
        runCatching {
            when (processType) {
                ProcessType.SENSIBLE_HEATING ->
                    Processes.sensibleHeating(s1, param1, param2)
                ProcessType.SENSIBLE_COOLING ->
                    Processes.sensibleCooling(s1, param1, param2)
                ProcessType.HUMIDIFICATION ->
                    if (useW) Processes.humidification(s1, w2 = param1)
                    else Processes.humidification(s1, rh2Pct = param1)
                ProcessType.DEHUMIDIFICATION ->
                    if (useW) Processes.dehumidification(s1, w2 = param1)
                    else Processes.dehumidification(s1, rh2Pct = param1)
                ProcessType.COOLING_DEHUMIDIFICATION ->
                    if (useW) Processes.coolingDehumidification(s1, param1, w2 = param2, mDot = param3)
                    else Processes.coolingDehumidification(s1, param1, rh2Pct = param2, mDot = param3)
                ProcessType.HEATING_HUMIDIFICATION ->
                    if (useW) Processes.heatingHumidification(s1, param1, w2 = param2)
                    else Processes.heatingHumidification(s1, param1, rh2Pct = param2)
                ProcessType.EVAPORATIVE_COOLING ->
                    Processes.evaporativeCooling(s1, param1)
                ProcessType.ADIABATIC_MIXING -> {
                    val s2 = when (mixSec2) {
                        SecondaryInput.WBT -> fromDbtWbt(param1, param2!!)
                        SecondaryInput.DPT -> fromDbtDpt(param1, param2!!)
                        SecondaryInput.RH  -> fromDbtRh(param1, param2!!)
                        SecondaryInput.W   -> fromDbtW(param1, param2!!)
                        SecondaryInput.V   -> fromDbtV(param1, param2!!)
                        SecondaryInput.H   -> fromDbtH(param1, param2!!)
                    }
                    Processes.adiabaticMixing(s1, s2, param3 ?: 1.0, param4 ?: 1.0)
                }
            }
        }.onSuccess { result ->
            _processResult.value = result
            _processError.value = null
            addPlottedState(result.state1, "State 1")
            addPlottedState(result.state2, "State 2")
        }.onFailure { e ->
            _processError.value = "Process error: ${e.message}"
        }
    }

    // ── Chart layer visibility ─────────────────────────────────────────────────

    data class ChartLayers(
        val rh: Boolean = true,
        val wbt: Boolean = false,
        val enthalpy: Boolean = false,
        val specVol: Boolean = false,
    )

    private val _chartLayers = MutableStateFlow(ChartLayers())
    val chartLayers: StateFlow<ChartLayers> = _chartLayers.asStateFlow()

    fun toggleChartLayer(name: String) {
        _chartLayers.update { l ->
            when (name) {
                "rh"       -> l.copy(rh = !l.rh)
                "wbt"      -> l.copy(wbt = !l.wbt)
                "enthalpy" -> l.copy(enthalpy = !l.enthalpy)
                "specVol"  -> l.copy(specVol = !l.specVol)
                else       -> l
            }
        }
    }

    private val _selectedPointIdx = MutableStateFlow<Int?>(null)
    val selectedPointIdx: StateFlow<Int?> = _selectedPointIdx.asStateFlow()

    fun selectChartPoint(idx: Int?) { _selectedPointIdx.value = idx }

    // ── Chart state management ─────────────────────────────────────────────────

    data class PlottedState(val state: PsychroState, val label: String)

    private val _plottedStates = MutableStateFlow<List<PlottedState>>(emptyList())
    val plottedStates: StateFlow<List<PlottedState>> = _plottedStates.asStateFlow()

    private var labelCounter = 1

    fun addPlottedState(state: PsychroState, label: String? = null) {
        val lbl = label ?: "P${labelCounter++}"
        val current = _plottedStates.value.toMutableList()
        current.removeAll { it.label == lbl }
        current.add(PlottedState(state, lbl))
        _plottedStates.value = current.takeLast(6)
    }

    fun clearPlottedStates() {
        _plottedStates.value = emptyList()
        labelCounter = 1
    }

    fun clearResults() {
        _stateResult.value = null
        _stateError.value = null
        _processResult.value = null
        _processError.value = null
    }

    // ── AHU Multi-Process Chain ───────────────────────────────────────────────

    data class AhuStep(
        val stepNumber: Int,
        val processType: ProcessType,
        val stateIn: PsychroState,
        val stateOut: PsychroState,
        val metrics: Map<String, String>,
    )

    private val _ahuInitialState = MutableStateFlow<PsychroState?>(null)
    val ahuInitialState: StateFlow<PsychroState?> = _ahuInitialState.asStateFlow()

    private val _ahuChain = MutableStateFlow<List<AhuStep>>(emptyList())
    val ahuChain: StateFlow<List<AhuStep>> = _ahuChain.asStateFlow()

    private val _ahuError = MutableStateFlow<String?>(null)
    val ahuError: StateFlow<String?> = _ahuError.asStateFlow()

    fun setAhuInitialState(dbt: Double, secondary: SecondaryInput, value: Double) {
        runCatching {
            when (secondary) {
                SecondaryInput.WBT -> fromDbtWbt(dbt, value)
                SecondaryInput.DPT -> fromDbtDpt(dbt, value)
                SecondaryInput.RH  -> fromDbtRh(dbt, value)
                SecondaryInput.W   -> fromDbtW(dbt, value)
                SecondaryInput.V   -> fromDbtV(dbt, value)
                SecondaryInput.H   -> fromDbtH(dbt, value)
            }
        }.onSuccess { state ->
            _ahuInitialState.value = state
            _ahuChain.value = emptyList()
            _ahuError.value = null
        }.onFailure { e ->
            _ahuError.value = "Initial state error: ${e.message}"
        }
    }

    fun addAhuStep(
        processType: ProcessType,
        param1: Double,
        param2: Double? = null,
        param3: Double? = null,
        useW: Boolean = true,
        mixSec2: SecondaryInput = SecondaryInput.RH,
        mixDbt2: Double = 20.0,
        mixSec2Val: Double = 50.0,
        mixM1: Double = 1.0,
        mixM2: Double = 1.0,
    ) {
        val currentIn = if (_ahuChain.value.isEmpty()) _ahuInitialState.value
                        else _ahuChain.value.last().stateOut
        if (currentIn == null) {
            _ahuError.value = "Set an initial state before adding steps."
            return
        }
        runCatching {
            when (processType) {
                ProcessType.SENSIBLE_HEATING         -> Processes.sensibleHeating(currentIn, param1)
                ProcessType.SENSIBLE_COOLING         -> Processes.sensibleCooling(currentIn, param1)
                ProcessType.HUMIDIFICATION           ->
                    if (useW) Processes.humidification(currentIn, w2 = param1)
                    else      Processes.humidification(currentIn, rh2Pct = param1)
                ProcessType.DEHUMIDIFICATION         ->
                    if (useW) Processes.dehumidification(currentIn, w2 = param1)
                    else      Processes.dehumidification(currentIn, rh2Pct = param1)
                ProcessType.COOLING_DEHUMIDIFICATION ->
                    if (useW) Processes.coolingDehumidification(currentIn, param1, w2 = param2)
                    else      Processes.coolingDehumidification(currentIn, param1, rh2Pct = param2)
                ProcessType.HEATING_HUMIDIFICATION   ->
                    if (useW) Processes.heatingHumidification(currentIn, param1, w2 = param2)
                    else      Processes.heatingHumidification(currentIn, param1, rh2Pct = param2)
                ProcessType.EVAPORATIVE_COOLING      -> Processes.evaporativeCooling(currentIn, param1)
                ProcessType.ADIABATIC_MIXING         -> {
                    val s2 = when (mixSec2) {
                        SecondaryInput.WBT -> fromDbtWbt(mixDbt2, mixSec2Val)
                        SecondaryInput.DPT -> fromDbtDpt(mixDbt2, mixSec2Val)
                        SecondaryInput.RH  -> fromDbtRh(mixDbt2, mixSec2Val)
                        SecondaryInput.W   -> fromDbtW(mixDbt2, mixSec2Val)
                        SecondaryInput.V   -> fromDbtV(mixDbt2, mixSec2Val)
                        SecondaryInput.H   -> fromDbtH(mixDbt2, mixSec2Val)
                    }
                    Processes.adiabaticMixing(currentIn, s2, mixM1, mixM2)
                }
            }
        }.onSuccess { result ->
            val stepNum = _ahuChain.value.size + 1
            val step = AhuStep(
                stepNumber  = stepNum,
                processType = processType,
                stateIn     = result.state1,
                stateOut    = result.state2,
                metrics     = result.metrics,
            )
            _ahuChain.value = _ahuChain.value + step
            _ahuError.value = null
            // Plot both endpoints on the chart so the AHU chain is visible
            addPlottedState(result.state1, "A${stepNum}in")
            addPlottedState(result.state2, "A${stepNum}out")
        }.onFailure { e ->
            _ahuError.value = "Step error: ${e.message}"
        }
    }

    fun removeLastAhuStep() {
        if (_ahuChain.value.isNotEmpty())
            _ahuChain.value = _ahuChain.value.dropLast(1)
        _ahuError.value = null
    }

    fun clearAhuChain() {
        _ahuChain.value = emptyList()
        _ahuInitialState.value = null
        _ahuError.value = null
    }
}
