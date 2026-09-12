package com.myhealth.domain.repository

import com.myhealth.domain.model.SuggestedSession
import com.myhealth.domain.model.SuggestionBatch
import com.myhealth.domain.util.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * `suggestion_batch` + `suggested_session` (PLAN §2.2.4, P6.5).
 *
 * [generate] gathers the engine inputs, runs `SuggestionEngine` (§3.5), writes a new batch and
 * marks any previous `PROPOSED` batch `SUPERSEDED`. [accept] copies the chosen sessions into
 * `planned_session` (status `PLANNED`, `sourceSuggestionId` set).
 */
interface SuggestionRepository {

    fun observeLatestBatch(): Flow<SuggestionBatch?>

    fun observeSessions(batchId: Long): Flow<List<SuggestedSession>>

    suspend fun getBatch(id: Long): SuggestionBatch?

    suspend fun generate(horizonDays: Int): Outcome<SuggestionBatch>

    suspend fun accept(sessionIds: List<Long>): Outcome<Unit>

    suspend fun reject(sessionIds: List<Long>): Outcome<Unit>

    suspend fun supersedeProposed(): Outcome<Unit>
}
