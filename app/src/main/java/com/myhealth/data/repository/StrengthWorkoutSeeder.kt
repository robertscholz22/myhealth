package com.myhealth.data.repository

import com.myhealth.domain.engine.strength.StrengthTemplates
import com.myhealth.domain.model.StrengthWorkout
import com.myhealth.domain.repository.StrengthRepository
import com.myhealth.domain.util.Outcome
import java.time.Clock

/**
 * Materialises the six built-in workouts of [StrengthTemplates] into `strength_workout`
 * (PLAN §3.12.3, P14.4).
 *
 * Seeding happens from **code, not from the migration**, the first time the Workouts screen opens
 * or a strength suggestion is accepted, so a template that turns out to be wrong can be corrected
 * in a later release without a schema change.
 *
 * It is idempotent on [com.myhealth.domain.model.StrengthWorkout.templateId] (`sw05`): a template
 * whose row already exists is left exactly as it is — including the user's own edits to it — and
 * `uq_strength_workout_template` is the database-side backstop for the same rule. Seeding twice
 * therefore leaves six rows, not twelve.
 */
class StrengthWorkoutSeeder(
    private val repo: StrengthRepository,
    private val clock: Clock = Clock.systemUTC(),
) {

    /**
     * Inserts the built-ins that are missing and returns how many were written (0 on every run
     * after the first). Failures are swallowed per template: a seeder is a convenience, and a
     * half-seeded catalog completes itself on the next call.
     */
    suspend fun seed(): Int {
        var inserted = 0
        for (template in StrengthTemplates.ALL) {
            val templateId = template.templateId ?: continue
            if (repo.getByTemplateId(templateId) != null) continue
            val now = clock.millis()
            val written = repo.upsertWorkout(template.copy(createdAtMillis = now, updatedAtMillis = now))
            if (written is Outcome.Ok) inserted++
        }
        return inserted
    }

    /**
     * The row of one built-in, seeding the whole set first if it has never run — what `accept`
     * calls when a suggestion names a `workoutTemplateId` (P14.5).
     */
    suspend fun workoutFor(templateId: String): StrengthWorkout? {
        repo.getByTemplateId(templateId)?.let { return it }
        seed()
        return repo.getByTemplateId(templateId)
    }
}
