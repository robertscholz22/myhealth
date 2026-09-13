package com.myhealth.domain.model

import com.myhealth.domain.util.toLocalDate
import java.time.LocalDate
import java.time.Period

/**
 * Mirrors the single-row `profile` table (PLAN §2.2.1), plus the computed helpers from §2.3.
 */
data class Profile(
    val id: Long = 1L,
    val displayName: String,
    val sex: Sex,
    val birthDay: Long,
    val heightCm: Double,
    val neatLevel: NeatLevel = NeatLevel.LIGHT_ACTIVE,
    val goalWeightKg: Double? = null,
    val goalPaceKgPerWeek: Double = 0.0,
    val restingHrManual: Int? = null,
    val maxHrManual: Int? = null,
    val fallbackWeightKg: Double? = null,
    val sleepTargetHours: Double = 8.0,
    val preferredSportsJson: String = "{}",
    val mobilityOnRestDays: Boolean = true,
    /** Manual FTP override in watts (P12); wins over every estimate when set. */
    val ftpWattsManual: Int? = null,
    /** The owner has an indoor trainer, so the planner may offer indoor rides (P12). */
    val indoorTrainerAvailable: Boolean = false,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    /** Whole years of age on [on], from [birthDay] (epoch day). */
    fun ageYears(on: LocalDate): Int = Period.between(birthDay.toLocalDate(), on).years

    /**
     * Tanaka estimate `208 - 0.7*age`, overridden by [maxHrManual] when the user supplied one.
     * The load engine (§3.2.1) additionally considers an observed max HR from recent activities —
     * that blending happens there, not here, since it needs activity history.
     */
    fun estimatedMaxHr(ageYears: Int): Int =
        maxHrManual ?: Math.round(208.0 - 0.7 * ageYears).toInt()

    /** Weight resolution ladder (§3.1.1), minus the fail-safe 75.0 kg and warnings — see the engine. */
    fun effectiveWeightKg(latest: BodyMeasurement?): Double =
        latest?.weightKg ?: fallbackWeightKg ?: goalWeightKg ?: 75.0
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Domain projection of the DataStore-backed settings (P1.8); not a Room table. */
data class AppSettings(
    val sleepTargetHours: Double = 8.0,
    val includeTreadmillInPrs: Boolean = false,
    val mobilityOnRestDays: Boolean = true,
    val syncIntervalHours: Int = 6,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * Wallpaper-derived Material You colours instead of the app's own green scheme (P8.6a).
     * Off by default: the green palette is the owner's request, dynamic colour is the opt-in.
     */
    val useDynamicColor: Boolean = false,
    val allowDestructiveMigration: Boolean = false,
    val suggestionHorizonDays: Int = 7,
    val offUserAgentContact: String = "",
    /** P9 direct Garmin client toggle; false until that phase ships. */
    val garminDirectEnabled: Boolean = false,
    /** Drives the start destination (Onboarding vs Today) — see P1.9/P1.10. */
    val hasCompletedOnboarding: Boolean = false,
    /**
     * The menstrual-cycle tracker (P11.1). `false` by default because `AppSettings` knows nothing
     * about the profile: onboarding turns it on when `sex == FEMALE` is saved, and
     * `CycleRepository.isTrackingEnabled` ors it with `profile.sex == FEMALE` anyway, so a FEMALE
     * profile is tracked even if this flag was never written. `MALE`/`OTHER` opt in from Settings.
     */
    val cycleTrackingEnabled: Boolean = false,
    /**
     * Set when the calendar changes underneath an open `PROPOSED` suggestion batch (POLISH-8).
     * Training and the Today card then show a "Calendar changed — regenerate" hint. It lives here
     * rather than in `suggestion_batch.status` so the fix needs no schema migration.
     */
    val suggestionsStale: Boolean = false,
)
