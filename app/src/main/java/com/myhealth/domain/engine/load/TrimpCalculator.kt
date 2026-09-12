package com.myhealth.domain.engine.load

import com.myhealth.domain.engine.load.TrimpDefaults as D
import com.myhealth.domain.model.ActivitySession
import com.myhealth.domain.model.ActivityStreams
import com.myhealth.domain.model.EngineWarningCode
import com.myhealth.domain.model.LoadMethod
import com.myhealth.domain.model.Sex
import com.myhealth.domain.model.SportType
import com.myhealth.domain.util.EngineWarning
import kotlin.math.exp

/** One session's inputs for [TrimpCalculator] (PLAN §3.2.2). The engine reads no clock and no DB. */
data class TrimpInput(
    val sportType: SportType,
    /** Moving/timer seconds if known, else elapsed (`activity_session.durationSec`). */
    val durationSec: Int,
    val sex: Sex,
    val bounds: HrBounds,
    val avgHr: Int? = null,
    /** User-entered session RPE, 1–10. */
    val rpe: Int? = null,
    val streams: ActivityStreams? = null,
)

/** [TrimpCalculator] output: the AU value, which rung of the ladder produced it, and why. */
data class TrimpResult(
    val trimp: Double,
    val method: LoadMethod,
    val warnings: List<EngineWarning> = emptyList(),
)

/**
 * Session TRIMP, Banister's heart-rate-reserve form (PLAN §3.2.2):
 *
 * ```
 * trimpRate(hr) = hrr * 0.64 * exp(y * hrr)      [AU per minute],  y = 1.92 (MALE/OTHER), 1.67 (FEMALE)
 * ```
 *
 * Method ladder, first match wins: [LoadMethod.HR_SAMPLES] (≥ 10 non-null HR readings in the
 * stream, integrated pair-by-pair with each interval capped at 60 s) → [LoadMethod.HR_AVERAGE] →
 * [LoadMethod.RPE_ESTIMATE] (`0.30 * rpe * minutes`, RPE either given or the sport default) →
 * [LoadMethod.DURATION_ONLY] (`0.30 * 5 * minutes`).
 *
 * Ambiguity in §3.2.2 and the choice made: the default-RPE table ends with "other 5.0", which would
 * make `DURATION_ONLY` unreachable since every sport would then have a default. It is therefore read
 * as "the sports listed have a default" — [TrimpDefaults.DEFAULT_RPE] holds exactly those, and the
 * unlisted ones (`ROWING`, `OTHER`, `UNKNOWN`) fall through to `DURATION_ONLY`, whose own factor 5.0
 * reproduces the "other 5.0" number anyway. Both estimated rungs add
 * [EngineWarningCode.ESTIMATED_LOAD]; every non-HR rung also adds [EngineWarningCode.MISSING_HR].
 *
 * The result is clamped to `[0, 600]` AU; a clamped value adds [EngineWarningCode.IMPLAUSIBLE_VALUE].
 */
object TrimpCalculator {

    /** AU per minute at heart-rate-reserve fraction [hrr] (already clamped to `0..1`). */
    fun trimpRate(hrr: Double, y: Double): Double = hrr * D.BANISTER_K * exp(y * hrr)

    fun compute(input: TrimpInput): TrimpResult {
        val warnings = mutableListOf<EngineWarning>()
        val y = D.banisterY(input.sex)
        val minutes = (input.durationSec.coerceAtLeast(0)) / 60.0
        val streams = input.streams

        val (raw, method) = when {
            streams != null && usableHrSampleCount(streams) >= D.MIN_HR_SAMPLES ->
                fromSamples(streams, input.bounds, y) to LoadMethod.HR_SAMPLES

            input.avgHr != null ->
                minutes * trimpRate(input.bounds.hrr(input.avgHr), y) to LoadMethod.HR_AVERAGE

            else -> {
                warnings += EngineWarning(
                    EngineWarningCode.MISSING_HR,
                    "No HR stream and no average HR for this ${input.sportType} session.",
                )
                val rpe = effectiveRpe(input, warnings)
                if (rpe != null) {
                    D.RPE_TO_TRIMP * rpe * minutes to LoadMethod.RPE_ESTIMATE
                } else {
                    warnings += EngineWarning(
                        EngineWarningCode.ESTIMATED_LOAD,
                        "Neither HR nor RPE nor a sport default; assuming RPE ${D.FALLBACK_RPE}.",
                    )
                    D.RPE_TO_TRIMP * D.FALLBACK_RPE * minutes to LoadMethod.DURATION_ONLY
                }
            }
        }

        val trimp = raw.coerceIn(D.TRIMP_MIN, D.TRIMP_MAX)
        if (raw > D.TRIMP_MAX) {
            warnings += EngineWarning(
                EngineWarningCode.IMPLAUSIBLE_VALUE,
                "TRIMP ${format(raw)} AU exceeds ${D.TRIMP_MAX} AU; clamped.",
            )
        }
        return TrimpResult(trimp = trimp, method = method, warnings = warnings + input.bounds.warnings)
    }

    /** Convenience for the recompute pipeline (P5.5): the same ladder, read off a loaded session. */
    fun computeForSession(session: ActivitySession, sex: Sex, bounds: HrBounds): TrimpResult =
        compute(
            TrimpInput(
                sportType = session.sportType,
                durationSec = session.durationSec,
                sex = sex,
                bounds = bounds,
                avgHr = session.avgHr,
                rpe = session.rpe,
                streams = session.streams,
            ),
        )

    /**
     * `Σ_i dt_i * trimpRate(meanHr_i)` over consecutive sample pairs, `dt_i` capped at 60 s
     * (same convention as `timeInZones`, P2.9) and pairs touching a null reading skipped entirely.
     */
    fun fromSamples(streams: ActivityStreams, bounds: HrBounds, y: Double): Double {
        val offsets = streams.sampleOffsetsSec
        val hr = streams.hr
        val count = minOf(offsets.size, hr.size)
        var sum = 0.0
        for (i in 0 until count - 1) {
            val a = hr[i] ?: continue
            val b = hr[i + 1] ?: continue
            val dtSec = (offsets[i + 1] - offsets[i]).coerceIn(0, D.MAX_SAMPLE_INTERVAL_SEC)
            if (dtSec == 0) continue
            val meanHr = (a + b) / 2.0
            sum += (dtSec / 60.0) * trimpRate(bounds.hrr(meanHr), y)
        }
        return sum
    }

    /** Non-null HR readings on the shared time axis — what the `HR_SAMPLES` rung counts. */
    fun usableHrSampleCount(streams: ActivityStreams): Int {
        val count = minOf(streams.sampleOffsetsSec.size, streams.hr.size)
        var usable = 0
        for (i in 0 until count) if (streams.hr[i] != null) usable++
        return usable
    }

    private fun effectiveRpe(input: TrimpInput, warnings: MutableList<EngineWarning>): Double? {
        val given = input.rpe
        if (given != null) {
            val clamped = given.coerceIn(1, 10)
            if (clamped != given) {
                warnings += EngineWarning(
                    EngineWarningCode.IMPLAUSIBLE_VALUE,
                    "RPE $given outside 1..10; clamped to $clamped.",
                )
            }
            return clamped.toDouble()
        }
        val default = D.defaultRpeFor(input.sportType) ?: return null
        warnings += EngineWarning(
            EngineWarningCode.ESTIMATED_LOAD,
            "No RPE entered; using the ${input.sportType} default $default.",
        )
        return default
    }

    private fun format(value: Double): String = D.roundTo(value, 0.1).toString()
}
