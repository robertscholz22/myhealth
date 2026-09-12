package com.myhealth.data.healthconnect

import androidx.health.connect.client.records.SleepSessionRecord
import com.myhealth.domain.model.SleepStage

/**
 * `SleepSessionRecord.Stage.stage` → [SleepStage] (PLAN P2.3). Like `ExerciseTypeMap`, only the
 * `STAGE_TYPE_*` constant names are relied on. An unrecognised value degrades to
 * [SleepStage.UNKNOWN] rather than throwing (§2.1 converter rule).
 */
object SleepStageMap {

    fun toSleepStage(stage: Int): SleepStage = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE -> SleepStage.AWAKE
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> SleepStage.AWAKE_IN_BED
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStage.OUT_OF_BED
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStage.SLEEPING
        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStage.LIGHT
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.REM
        else -> SleepStage.UNKNOWN
    }

    /** Stages that count towards `sleep_session.totalSleepMin` — everything except time awake. */
    fun isAsleep(stage: SleepStage): Boolean = when (stage) {
        SleepStage.SLEEPING, SleepStage.LIGHT, SleepStage.DEEP, SleepStage.REM -> true
        SleepStage.AWAKE, SleepStage.AWAKE_IN_BED, SleepStage.OUT_OF_BED, SleepStage.UNKNOWN ->
            false
    }
}
