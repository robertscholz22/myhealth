package com.myhealth.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.myhealth.domain.model.NeatLevel
import com.myhealth.domain.model.Sex
import kotlinx.serialization.Serializable

/**
 * Single-row `profile` table (PLAN §2.2.1). [id] is always [SINGLETON_ID]; the repository never
 * inserts a second row.
 */
@Serializable
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    val displayName: String,
    val sex: Sex,
    /** Epoch day. */
    val birthDay: Long,
    val heightCm: Double,
    val neatLevel: NeatLevel = NeatLevel.LIGHT_ACTIVE,
    val goalWeightKg: Double? = null,
    /** UI clamps to [-1.0, +0.5]. */
    val goalPaceKgPerWeek: Double = 0.0,
    val restingHrManual: Int? = null,
    val maxHrManual: Int? = null,
    val fallbackWeightKg: Double? = null,
    val sleepTargetHours: Double = 8.0,
    /** JSON `{"RUN":3,"STRENGTH":2,"SOCCER":2}` — sessions/week caps. */
    val preferredSportsJson: String = "{}",
    val mobilityOnRestDays: Boolean = true,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    companion object {
        const val SINGLETON_ID: Long = 1L
    }
}
