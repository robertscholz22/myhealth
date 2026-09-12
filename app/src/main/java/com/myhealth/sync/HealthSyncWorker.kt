package com.myhealth.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.myhealth.MyHealthApp
import com.myhealth.data.healthconnect.BackfillResult
import com.myhealth.data.healthconnect.SyncSummary
import com.myhealth.data.healthconnect.describe
import com.myhealth.domain.util.AppError
import com.myhealth.domain.util.Outcome
import java.time.LocalDate

/**
 * The outcome of one worker run, decoupled from [androidx.work.ListenableWorker.Result] so
 * [mapOutcome] is unit-testable without WorkManager (PLAN P2.7).
 */
sealed interface WorkerVerdict {
    data object Success : WorkerVerdict
    data object Retry : WorkerVerdict
    data class Failure(val reason: String) : WorkerVerdict
}

/**
 * `HealthConnectUnavailable` is treated as transient (the provider may still be starting up, or
 * temporarily unbound) and retried with WorkManager's exponential backoff; every other error is a
 * terminal failure carrying its description as the output data reason.
 */
internal fun mapOutcome(outcome: Outcome<*>): WorkerVerdict = when (outcome) {
    is Outcome.Ok -> WorkerVerdict.Success
    is Outcome.Err -> if (outcome.error == AppError.HealthConnectUnavailable) {
        WorkerVerdict.Retry
    } else {
        WorkerVerdict.Failure(outcome.error.describe())
    }
}

/**
 * The day [com.myhealth.sync.SyncScheduler.requestLoadRecompute] has to start from: the earliest
 * day this run touched an exercise record on, so an initial sync or a backfill re-derives TRIMP
 * for the whole ingested history and not only for the last 28 days (verification BUG-5).
 * [LoadRecomputeWorker] widens the window by the 28-day EWMA prefix itself.
 */
internal fun loadRecomputeDay(
    outcome: Outcome<*>,
    backfillFromDay: Long?,
    today: Long,
): Long {
    val reported = when (val value = (outcome as? Outcome.Ok)?.value) {
        is SyncSummary -> value.minAffectedDay
        is BackfillResult -> value.minAffectedDay
        else -> null
    }
    val fallback = backfillFromDay ?: today
    return minOf(reported ?: fallback, fallback)
}

/**
 * `CoroutineWorker` driving Health Connect sync (PLAN P2.7). The graph is pulled from
 * `(applicationContext as MyHealthApp).graph` (§1.3) — no `WorkerFactory` is registered.
 *
 * With [KEY_BACKFILL_FROM_DAY] present in the input data, [com.myhealth.data.healthconnect.HcBackfill]
 * runs for that start day instead of the incremental sync — this is how "Sync now" and "Start
 * backfill" share one worker.
 */
class HealthSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = (applicationContext as MyHealthApp).graph
        val backfillFromDay = inputData.getLong(KEY_BACKFILL_FROM_DAY, NO_BACKFILL).takeIf { it >= 0 }

        val outcome: Outcome<*> = if (backfillFromDay != null) {
            val backfill = graph.hcBackfill
                ?: return Result.failure(failureData("Health Connect unavailable"))
            backfill.run(backfillFromDay)
        } else {
            val sync = graph.hcSync ?: return Result.failure(failureData("Health Connect unavailable"))
            sync.syncIncremental()
        }

        return when (val verdict = mapOutcome(outcome)) {
            WorkerVerdict.Success -> {
                // Fresh activities / daily totals change the measured TDEE, so the day's targets
                // have to be re-derived (P4.12); the request is debounced by 30 s.
                graph.syncScheduler.requestTargetRecompute()
                // New or changed activities/HR/sleep also change TRIMP, ACWR and recovery (P5.5).
                val today = LocalDate.now(graph.clock).toEpochDay()
                graph.syncScheduler.requestLoadRecompute(
                    loadRecomputeDay(outcome, backfillFromDay, today),
                )
                Result.success()
            }
            WorkerVerdict.Retry -> Result.retry()
            is WorkerVerdict.Failure -> Result.failure(failureData(verdict.reason))
        }
    }

    private fun failureData(reason: String): Data = workDataOf(KEY_FAILURE_REASON to reason)


    companion object {
        /** Input key: epoch day to backfill from. Absent for the ordinary incremental sync. */
        const val KEY_BACKFILL_FROM_DAY: String = "backfillFromDay"

        /** Output key: a human-readable reason, set whenever the worker returns `Result.failure()`. */
        const val KEY_FAILURE_REASON: String = "reason"

        private const val NO_BACKFILL: Long = -1L
    }
}
