package com.myhealth.domain.engine.running

import kotlin.math.exp
import kotlin.math.sqrt

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

    /** The `v²` coefficient of the §3.4 oxygen-cost polynomial. */
    const val VO2_A: Double = 0.000104

    /** The `v` coefficient of the §3.4 oxygen-cost polynomial. */
    const val VO2_B: Double = 0.182258

    /** The constant term of the §3.4 oxygen-cost polynomial (negative: resting cost is subtracted). */
    const val VO2_C: Double = -4.60

    /** Fraction of VO₂max sustainable for [minutes] of racing. */
    fun percentMax(minutes: Double): Double =
        0.8 + 0.1894393 * exp(-0.012778 * minutes) + 0.2989558 * exp(-0.1932605 * minutes)

    /** Oxygen cost (ml/kg/min) of running at [metersPerMin]. */
    fun vo2(metersPerMin: Double): Double =
        VO2_C + VO2_B * metersPerMin + VO2_A * metersPerMin * metersPerMin

    /**
     * The inverse of [vo2] (PLAN §3.10.1): the velocity in m/min whose oxygen cost is [pct] of
     * [vdot] — the root of `0.000104 v² + 0.182258 v - (4.60 + vdot·pct) = 0`, of which only the
     * positive one is a running speed.
     *
     * `null` for a non-positive [vdot] or [pct]; the `DanielsPaces` table is its only caller.
     */
    fun velocityFor(vdot: Double, pct: Double): Double? {
        if (vdot <= 0.0 || pct <= 0.0) return null
        val discriminant = VO2_B * VO2_B + 4.0 * VO2_A * (-VO2_C + vdot * pct)
        if (discriminant < 0.0) return null
        val velocity = (-VO2_B + sqrt(discriminant)) / (2.0 * VO2_A)
        return velocity.takeIf { it > 0.0 }
    }

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
