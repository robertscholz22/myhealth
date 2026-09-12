package com.myhealth.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.myhealth.MyHealthApp
import com.myhealth.domain.util.AppError
import com.myhealth.domain.util.Outcome
import java.time.LocalDate

/**
 * Recomputes the `nutrition_target_snapshot` rows of the `[today - 1, today + 7]` window (PLAN
 * P4.12) through `NutritionRepository.ensureTarget`, which itself skips any day whose `inputsHash`
 * is unchanged — so a run over a window that nothing touched writes nothing.
 *
 * Scheduled two ways by [SyncScheduler]: daily at 03:00 local
 * ([SyncScheduler.scheduleDailyTargetRecompute]) and debounced after any profile / weight / plan /
 * event write and after every Health Connect sync ([SyncScheduler.requestTargetRecompute]).
 *
 * Like [HealthSyncWorker] it reads the graph from `(applicationContext as MyHealthApp).graph`
 * (§1.3) — no `WorkerFactory` is registered.
 */
class TargetRecomputeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = (applicationContext as MyHealthApp).graph
        val today = LocalDate.now(graph.clock).toEpochDay()

        var recomputed = 0
        var skipped = 0
        var failure: AppError? = null
        for (day in (today + PAST_DAYS)..(today + FUTURE_DAYS)) {
            when (val outcome = graph.nutritionRepo.ensureTarget(day)) {
                is Outcome.Ok -> recomputed++
                is Outcome.Err -> when (outcome.error) {
                    // No profile yet (onboarding not finished): there is nothing to compute, and
                    // retrying would never help — the profile write itself re-enqueues this worker.
                    is AppError.Validation -> skipped++
                    else -> failure = outcome.error
                }
            }
        }

        return when {
            failure != null -> Result.retry()
            else -> Result.success(workDataOf(KEY_DAYS_RECOMPUTED to recomputed, KEY_DAYS_SKIPPED to skipped))
        }
    }

    companion object {
        /** The window of §P4.12: yesterday through a week out. */
        const val PAST_DAYS: Long = -1L
        const val FUTURE_DAYS: Long = 7L

        /** Output keys, shown by the Integrations screen's diagnostics (P8). */
        const val KEY_DAYS_RECOMPUTED: String = "daysRecomputed"
        const val KEY_DAYS_SKIPPED: String = "daysSkipped"
    }
}
