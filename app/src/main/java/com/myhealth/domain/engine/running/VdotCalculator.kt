package com.myhealth.domain.engine.running

import kotlin.math.exp

/**
 * Daniels & Gilbert VDOT (PLAN §3.4) — the pseudo-VO₂max a race performance corresponds to:
 *
 * ```
 * percentMax(t) = 0.8 + 0.1894393 * exp(-0.012778 * t) + 0.2989558 * exp(-0.1932605 * t)   // t in min
 * vo2(v)        = -4.60 + 0.182258 * v + 0.000104 * v * v                                   // v in m/min
 * vdot          = vo2(v) / percentMax(t)
 * ```
 *
 * Pure arithmetic: no clamping, no warnings. `5 km in 20:00` (v = 250 m/min, t = 20 min) gives
 * VDOT ≈ 49.8, the `pr11` reference value.
 */
object VdotCalculator {

    /** Fraction of VO₂max sustainable for [minutes] of racing. */
    fun percentMax(minutes: Double): Double =
        0.8 + 0.1894393 * exp(-0.012778 * minutes) + 0.2989558 * exp(-0.1932605 * minutes)

    /** Oxygen cost (ml/kg/min) of running at [metersPerMin]. */
    fun vo2(metersPerMin: Double): Double =
        -4.60 + 0.182258 * metersPerMin + 0.000104 * metersPerMin * metersPerMin

    /** VDOT for covering [distanceMeters] in [timeSec]; `null` for a non-positive input. */
    fun vdot(distanceMeters: Double, timeSec: Double): Double? {
        if (distanceMeters <= 0.0 || timeSec <= 0.0) return null
        val minutes = timeSec / 60.0
        val velocity = distanceMeters / minutes
        val percent = percentMax(minutes)
        if (percent <= 0.0) return null
        return vo2(velocity) / percent
    }
}
