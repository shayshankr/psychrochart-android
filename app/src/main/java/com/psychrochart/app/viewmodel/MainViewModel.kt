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

    fun calculateState(
        dbt: Double,
        secondary: SecondaryInput,
        value: Double,
        pointLabel: String? = null,
    ) {
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
            addPlottedState(state, pointLabel)
        }.onFailure { e ->
            _stateError.value = "Calculation error: ${e.message}"
        }
    }

    // ── Process analysis ──────────────────────────────────────────────────────

    private val _processResult = MutableStateFlow<ProcessResult?>(null)
    val processResult: StateFlow<ProcessResult?> = _processResult.asStateFlow()

    private val _processError = MutableStateFlow<String?>(null)
    val processError: StateFlow<String?> = _processError.asStateFlow()

    @Suppress("CyclomaticComplexMethod")
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
                    else      Processes.humidification(s1, rh2Pct = param1)
                ProcessType.DEHUMIDIFICATION ->
                    if (useW) Processes.dehumidification(s1, w2 = param1)
                    else      Processes.dehumidification(s1, rh2Pct = param1)
                ProcessType.COOLING_DEHUMIDIFICATION ->
                    if (useW) Processes.coolingDehumidification(s1, param1, w2 = param2, mDot = param3)
                    else      Processes.coolingDehumidification(s1, param1, rh2Pct = param2, mDot = param3)
                ProcessType.HEATING_HUMIDIFICATION ->
                    if (useW) Processes.heatingHumidification(s1, param1, w2 = param2)
                    else      Processes.heatingHumidification(s1, param1, rh2Pct = param2)
                ProcessType.EVAPORATIVE_COOLING ->
                    Processes.evaporativeCooling(s1, param1)
                ProcessType.ADIABATIC_MIXING -> {
                    val s2 = buildSecondaryState(param1, mixSec2, param2!!)
                    Processes.adiabaticMixing(s1, s2, param3 ?: 1.0, param4 ?: 1.0)
                }
                ProcessType.FAN_HEAT_RISE ->
                    Processes.fanHeatRise(s1, param1, param2 ?: 70.0, param3)
                ProcessType.COOLING_COIL ->
                    Processes.coolingCoil(s1, param1, param2 ?: 0.10, param3)
                ProcessType.ENERGY_RECOVERY -> {
                    val exhaustState = buildSecondaryState(param1, mixSec2, param2!!)
                    Processes.energyRecovery(s1, exhaustState, param3 ?: 0.70, param4 ?: 0.60)
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

    private fun buildSecondaryState(dbt: Double, sec: SecondaryInput, val2: Double) = when (sec) {
        SecondaryInput.WBT -> fromDbtWbt(dbt, val2)
        SecondaryInput.DPT -> fromDbtDpt(dbt, val2)
        SecondaryInput.RH  -> fromDbtRh(dbt, val2)
        SecondaryInput.W   -> fromDbtW(dbt, val2)
        SecondaryInput.V   -> fromDbtV(dbt, val2)
        SecondaryInput.H   -> fromDbtH(dbt, val2)
    }

    // ── Chart layer visibility ─────────────────────────────────────────────────

    data class ChartLayers(
        val rh: Boolean = true,
        val wbt: Boolean = false,
        val enthalpy: Boolean = false,
        val specVol: Boolean = false,
        val comfortZone: Boolean = false,
    )

    private val _chartLayers = MutableStateFlow(ChartLayers())
    val chartLayers: StateFlow<ChartLayers> = _chartLayers.asStateFlow()

    fun toggleChartLayer(name: String) {
        _chartLayers.update { l ->
            when (name) {
                "rh"          -> l.copy(rh = !l.rh)
                "wbt"         -> l.copy(wbt = !l.wbt)
                "enthalpy"    -> l.copy(enthalpy = !l.enthalpy)
                "specVol"     -> l.copy(specVol = !l.specVol)
                "comfortZone" -> l.copy(comfortZone = !l.comfortZone)
                else          -> l
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
        val lbl = if (!label.isNullOrBlank()) label else "P${labelCounter++}"
        val current = _plottedStates.value.toMutableList()
        current.removeAll { it.label == lbl }
        current.add(PlottedState(state, lbl))
        _plottedStates.value = current.takeLast(16)
    }

    fun clearPlottedStates() {
        _plottedStates.value = emptyList()
        labelCounter = 1
    }

    fun clearResults() {
        _stateResult.value = null
        _stateError.value  = null
        _processResult.value = null
        _processError.value  = null
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

    @Suppress("CyclomaticComplexMethod")
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
                ProcessType.SENSIBLE_HEATING ->
                    Processes.sensibleHeating(currentIn, param1)
                ProcessType.SENSIBLE_COOLING ->
                    Processes.sensibleCooling(currentIn, param1)
                ProcessType.HUMIDIFICATION ->
                    if (useW) Processes.humidification(currentIn, w2 = param1)
                    else      Processes.humidification(currentIn, rh2Pct = param1)
                ProcessType.DEHUMIDIFICATION ->
                    if (useW) Processes.dehumidification(currentIn, w2 = param1)
                    else      Processes.dehumidification(currentIn, rh2Pct = param1)
                ProcessType.COOLING_DEHUMIDIFICATION ->
                    if (useW) Processes.coolingDehumidification(currentIn, param1, w2 = param2)
                    else      Processes.coolingDehumidification(currentIn, param1, rh2Pct = param2)
                ProcessType.HEATING_HUMIDIFICATION ->
                    if (useW) Processes.heatingHumidification(currentIn, param1, w2 = param2)
                    else      Processes.heatingHumidification(currentIn, param1, rh2Pct = param2)
                ProcessType.EVAPORATIVE_COOLING ->
                    Processes.evaporativeCooling(currentIn, param1)
                ProcessType.ADIABATIC_MIXING -> {
                    val s2 = buildSecondaryState(mixDbt2, mixSec2, mixSec2Val)
                    Processes.adiabaticMixing(currentIn, s2, mixM1, mixM2)
                }
                ProcessType.FAN_HEAT_RISE ->
                    Processes.fanHeatRise(currentIn, param1, param2 ?: 70.0, param3)
                ProcessType.COOLING_COIL ->
                    Processes.coolingCoil(currentIn, param1, param2 ?: 0.10, param3)
                ProcessType.ENERGY_RECOVERY -> {
                    val exhaustState = buildSecondaryState(mixDbt2, mixSec2, mixSec2Val)
                    Processes.energyRecovery(currentIn, exhaustState, param1, param2 ?: 0.60)
                }
            }
        }.onSuccess { result ->
            val stepNum = _ahuChain.value.size + 1
            _ahuChain.value = _ahuChain.value + AhuStep(
                stepNumber  = stepNum,
                processType = processType,
                stateIn     = result.state1,
                stateOut    = result.state2,
                metrics     = result.metrics,
            )
            _ahuError.value = null
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

    // ── HVAC Tools: SHR / Room Load Calculator ─────────────────────────────────

    data class ShrResult(
        val shr: Double,
        val roomState: PsychroState,
        val supplyState: PsychroState,
        val totalLoadKw: Double,
        val massFlowKgs: Double,
    )

    private val _shrResult = MutableStateFlow<ShrResult?>(null)
    val shrResult: StateFlow<ShrResult?> = _shrResult.asStateFlow()

    private val _shrError = MutableStateFlow<String?>(null)
    val shrError: StateFlow<String?> = _shrError.asStateFlow()

    fun calculateShr(
        roomDbt: Double,
        roomRhPct: Double,
        sensibleKw: Double,
        latentKw: Double,
        massFlowKgs: Double,
    ) {
        runCatching {
            require(sensibleKw > 0) { "Sensible load must be positive." }
            require(latentKw >= 0) { "Latent load must be ≥ 0." }
            require(massFlowKgs > 0) { "Mass flow must be positive." }
            val roomState  = fromDbtRh(roomDbt, roomRhPct)
            val totalKw    = sensibleKw + latentKw
            val shr        = sensibleKw / totalKw
            val hSupply    = roomState.h - totalKw / massFlowKgs
            val wSupply    = maxOf(1e-7, roomState.w - latentKw / (massFlowKgs * 2501.0))
            val dbtSupply  = (hSupply - 2501.0 * wSupply) / (1.006 + 1.86 * wSupply)
            val supplyState = fromDbtW(dbtSupply, wSupply)
            ShrResult(shr, roomState, supplyState, totalKw, massFlowKgs)
        }.onSuccess { result ->
            _shrResult.value = result
            _shrError.value = null
            addPlottedState(result.roomState,   "Room")
            addPlottedState(result.supplyState, "SA")
        }.onFailure { e ->
            _shrError.value = "SHR error: ${e.message}"
        }
    }

    // ── HVAC Tools: ASHRAE 62.1 Ventilation ───────────────────────────────────

    enum class VentZoneType(
        val displayName: String,
        val rpLsPerPerson: Double,
        val raLsPerM2: Double,
    ) {
        OFFICE("Office",                     5.0,  0.06),
        CONFERENCE("Conference Room",       10.0,  0.06),
        CLASSROOM("Classroom (K-12)",        5.0,  0.06),
        UNIVERSITY("University Classroom",   7.5,  0.06),
        RESTAURANT("Restaurant / Dining",    7.5,  0.18),
        RETAIL("Retail Store",               3.8,  0.12),
        LOBBY("Lobby / Reception",           3.8,  0.06),
        CORRIDOR("Corridor",                 0.0,  0.06),
        GYM("Gymnasium / Fitness",          10.0,  0.06),
        HOTEL("Hotel Room",                  3.8,  0.06),
        HOSPITAL("Hospital Patient Room",    5.0,  0.18),
        LABORATORY("Laboratory",             5.0,  0.30),
    }

    data class VentResult(
        val zoneType: String,
        val occupancyLs: Double,
        val areaLs: Double,
        val totalVozLs: Double,
        val totalVozM3s: Double,
        val totalVozCfm: Double,
        val ezFactor: Double,
    )

    private val _ventResult = MutableStateFlow<VentResult?>(null)
    val ventResult: StateFlow<VentResult?> = _ventResult.asStateFlow()

    fun calculateVentilation(
        zoneType: VentZoneType,
        occupancy: Int,
        floorAreaM2: Double,
        floorSupply: Boolean,
    ) {
        val ez  = if (floorSupply) 0.8 else 1.0
        val occ = (zoneType.rpLsPerPerson * occupancy)
        val area = (zoneType.raLsPerM2 * floorAreaM2)
        val voz  = (occ + area) / ez
        _ventResult.value = VentResult(
            zoneType     = zoneType.displayName,
            occupancyLs  = occ,
            areaLs       = area,
            totalVozLs   = voz,
            totalVozM3s  = voz / 1000.0,
            totalVozCfm  = voz * 2.11888,
            ezFactor     = ez,
        )
    }

    // ── HVAC Tools: Cooling Tower ──────────────────────────────────────────────

    data class CoolingTowerResult(
        val leavingWaterTemp: Double,
        val approach: Double,
        val range: Double,
        val effectiveness: Double,
        val enteringWaterTemp: Double,
        val ambientWbt: Double,
    )

    private val _coolingTowerResult = MutableStateFlow<CoolingTowerResult?>(null)
    val coolingTowerResult: StateFlow<CoolingTowerResult?> = _coolingTowerResult.asStateFlow()

    private val _coolingTowerError = MutableStateFlow<String?>(null)
    val coolingTowerError: StateFlow<String?> = _coolingTowerError.asStateFlow()

    fun calculateCoolingTower(
        enteringWaterTemp: Double,
        ambientWbt: Double,
        approachDeg: Double,
    ) {
        runCatching {
            require(approachDeg > 0) { "Approach must be positive." }
            val leaving = ambientWbt + approachDeg
            require(leaving < enteringWaterTemp) {
                "Leaving water temp (%.1f°C) must be less than entering (%.1f°C).".format(leaving, enteringWaterTemp)
            }
            val range = enteringWaterTemp - leaving
            val effectiveness = range / (range + approachDeg)
            CoolingTowerResult(leaving, approachDeg, range, effectiveness, enteringWaterTemp, ambientWbt)
        }.onSuccess { result ->
            _coolingTowerResult.value = result
            _coolingTowerError.value = null
        }.onFailure { e ->
            _coolingTowerError.value = "Cooling tower error: ${e.message}"
        }
    }
}
