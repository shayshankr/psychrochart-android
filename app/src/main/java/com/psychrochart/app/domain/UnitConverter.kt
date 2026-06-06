package com.psychrochart.app.domain

object UnitConverter {

    // ── Temperature ────────────────────────────────────────────────────────────
    fun cToF(c: Double) = c * 9.0 / 5.0 + 32.0
    fun fToC(f: Double) = (f - 32.0) * 5.0 / 9.0

    // ── Humidity ratio ─────────────────────────────────────────────────────────
    fun kgkgToGrLb(w: Double) = w * 7000.0
    fun grLbToKgkg(gr: Double) = gr / 7000.0

    // ── Enthalpy ───────────────────────────────────────────────────────────────
    fun kjkgToBtuLb(h: Double) = h * 0.429923
    fun btuLbToKjkg(h: Double) = h / 0.429923

    // ── Specific volume ────────────────────────────────────────────────────────
    fun m3kgToFt3Lb(v: Double) = v * 16.0185
    fun ft3LbToM3kg(v: Double) = v / 16.0185

    // ── Pressure ───────────────────────────────────────────────────────────────
    fun kPaToPsi(p: Double) = p * 0.145038
    fun inH2OToPa(inH2O: Double) = inH2O * 249.089
    fun paToInH2O(pa: Double) = pa / 249.089

    // ── Mass flow ──────────────────────────────────────────────────────────────
    fun kgsToLbMin(kgs: Double) = kgs * 132.277
    fun lbMinToKgs(lbmin: Double) = lbmin / 132.277

    // ── Altitude ───────────────────────────────────────────────────────────────
    fun mToFt(m: Double) = m * 3.28084
    fun ftToM(ft: Double) = ft / 3.28084

    // ── Area ───────────────────────────────────────────────────────────────────
    fun m2ToFt2(m2: Double) = m2 * 10.7639
    fun ft2ToM2(ft2: Double) = ft2 / 10.7639

    // ── Flow rates ─────────────────────────────────────────────────────────────
    fun lsToCfm(ls: Double) = ls * 2.11888
    fun cfmToLs(cfm: Double) = cfm / 2.11888

    // ── Load ───────────────────────────────────────────────────────────────────
    fun kwToBtuh(kw: Double) = kw * 3412.14

    // ── Unit labels ────────────────────────────────────────────────────────────
    fun tempUnit(us: UnitSystem) = if (us == UnitSystem.IP) "°F" else "°C"
    fun wUnit(us: UnitSystem) = if (us == UnitSystem.IP) "gr/lb" else "kg/kg"
    fun hUnit(us: UnitSystem) = if (us == UnitSystem.IP) "BTU/lb" else "kJ/kg"
    fun vUnit(us: UnitSystem) = if (us == UnitSystem.IP) "ft³/lb" else "m³/kg"
    fun altUnit(us: UnitSystem) = if (us == UnitSystem.IP) "ft" else "m"
    fun flowUnit(us: UnitSystem) = if (us == UnitSystem.IP) "lb/min" else "kg/s"
    fun areaUnit(us: UnitSystem) = if (us == UnitSystem.IP) "ft²" else "m²"
    fun pressureUnit(us: UnitSystem) = if (us == UnitSystem.IP) "inH₂O" else "Pa"

    // ── Display conversions (SI → user unit) ───────────────────────────────────
    fun displayTemp(c: Double, us: UnitSystem) = if (us == UnitSystem.IP) cToF(c) else c
    fun displayW(kgkg: Double, us: UnitSystem) = if (us == UnitSystem.IP) kgkgToGrLb(kgkg) else kgkg
    fun displayH(kjkg: Double, us: UnitSystem) = if (us == UnitSystem.IP) kjkgToBtuLb(kjkg) else kjkg
    fun displayV(m3kg: Double, us: UnitSystem) = if (us == UnitSystem.IP) m3kgToFt3Lb(m3kg) else m3kg
    fun displayPv(kpa: Double, us: UnitSystem) = if (us == UnitSystem.IP) kPaToPsi(kpa) else kpa
    fun displayAlt(m: Double, us: UnitSystem) = if (us == UnitSystem.IP) mToFt(m) else m
    fun displayFlow(kgs: Double, us: UnitSystem) = if (us == UnitSystem.IP) kgsToLbMin(kgs) else kgs
    fun displayPressure(pa: Double, us: UnitSystem) = if (us == UnitSystem.IP) paToInH2O(pa) else pa

    // ── Input conversions (user unit → SI) ────────────────────────────────────
    fun inputTemp(v: Double, us: UnitSystem) = if (us == UnitSystem.IP) fToC(v) else v
    fun inputW(v: Double, us: UnitSystem) = if (us == UnitSystem.IP) grLbToKgkg(v) else v
    fun inputH(v: Double, us: UnitSystem) = if (us == UnitSystem.IP) btuLbToKjkg(v) else v
    fun inputV(v: Double, us: UnitSystem) = if (us == UnitSystem.IP) ft3LbToM3kg(v) else v
    fun inputFlow(v: Double, us: UnitSystem) = if (us == UnitSystem.IP) lbMinToKgs(v) else v
    fun inputAlt(v: Double, us: UnitSystem) = if (us == UnitSystem.IP) ftToM(v) else v
    fun inputPressure(v: Double, us: UnitSystem) = if (us == UnitSystem.IP) inH2OToPa(v) else v

    // ── Default text values for input fields ───────────────────────────────────
    fun defaultTemp(c: Double, us: UnitSystem) = "%.1f".format(displayTemp(c, us))
    fun defaultW(kgkg: Double, us: UnitSystem) =
        if (us == UnitSystem.IP) "%.1f".format(kgkgToGrLb(kgkg)) else "%.5f".format(kgkg)
    fun defaultH(kjkg: Double, us: UnitSystem) = "%.1f".format(displayH(kjkg, us))
    fun defaultV(m3kg: Double, us: UnitSystem) = "%.3f".format(displayV(m3kg, us))
}
