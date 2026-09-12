package com.myhealth.ui.onboarding

import com.myhealth.R
import com.myhealth.domain.model.NeatLevel
import com.myhealth.domain.model.Sex
import com.myhealth.domain.model.SportGroup
import com.myhealth.ui.common.ONBOARDING_SPORT_GROUPS
import com.myhealth.ui.common.UiMessage
import java.time.LocalDate

/** The 3-step onboarding form state (§4.2 Onboarding), before it becomes a [com.myhealth.domain.model.Profile]. */
data class OnboardingDraft(
    val displayName: String = "",
    val sex: Sex = Sex.MALE,
    val birthDay: LocalDate? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val goalWeightKg: Double? = null,
    val goalPaceKgPerWeek: Double? = 0.0,
    val neatLevel: NeatLevel = NeatLevel.LIGHT_ACTIVE,
    val sessionsPerWeek: Map<SportGroup, Int> = ONBOARDING_SPORT_GROUPS.associateWith { 0 },
    val mobilityOnRestDays: Boolean = true,
    val sleepTargetHours: Double? = 8.0,
)

/** Field identity for validation errors and per-step gating. */
enum class OnboardingField { NAME, BIRTH_DATE, HEIGHT, WEIGHT, GOAL_WEIGHT, GOAL_PACE }

enum class OnboardingStep(val fields: Set<OnboardingField>) {
    IDENTITY(setOf(OnboardingField.NAME, OnboardingField.BIRTH_DATE)),
    BODY(setOf(OnboardingField.HEIGHT, OnboardingField.WEIGHT, OnboardingField.GOAL_WEIGHT, OnboardingField.GOAL_PACE)),
    PREFERENCES(emptySet()),
}

private const val MIN_HEIGHT_CM = 100.0
private const val MAX_HEIGHT_CM = 250.0
private const val MIN_WEIGHT_KG = 30.0
private const val MAX_WEIGHT_KG = 250.0
private const val MIN_GOAL_PACE_KG_PER_WEEK = -1.0
private const val MAX_GOAL_PACE_KG_PER_WEEK = 0.5
private const val MIN_ONBOARDING_AGE_YEARS = 10L

/**
 * Pure validation (unit-tested in `OnboardingValidationTest`) — no Android/Compose dependency.
 * [today] defaults to the real "now" but is overridable so birth-date rules are deterministic
 * in tests (§1.3: engines/pure functions take the clock/date as a parameter).
 */
fun validate(draft: OnboardingDraft, today: LocalDate = LocalDate.now()): Map<OnboardingField, UiMessage> {
    val errors = mutableMapOf<OnboardingField, UiMessage>()

    if (draft.displayName.isBlank()) {
        errors[OnboardingField.NAME] = UiMessage.of(R.string.onboarding_name_required)
    }

    val birthDay = draft.birthDay
    val earliestAllowedBirthDay = today.minusYears(MIN_ONBOARDING_AGE_YEARS)
    when {
        birthDay == null -> errors[OnboardingField.BIRTH_DATE] = UiMessage.of(R.string.onboarding_birth_date_required)
        birthDay.isAfter(today) -> errors[OnboardingField.BIRTH_DATE] = UiMessage.of(R.string.onboarding_birth_date_future)
        birthDay.isAfter(earliestAllowedBirthDay) ->
            errors[OnboardingField.BIRTH_DATE] =
                UiMessage.of(R.string.onboarding_birth_date_min_age, MIN_ONBOARDING_AGE_YEARS)
    }

    val height = draft.heightCm
    if (height == null || height < MIN_HEIGHT_CM || height > MAX_HEIGHT_CM) {
        errors[OnboardingField.HEIGHT] =
            UiMessage.of(R.string.onboarding_height_range, MIN_HEIGHT_CM.toInt(), MAX_HEIGHT_CM.toInt())
    }

    val weight = draft.weightKg
    if (weight == null || weight < MIN_WEIGHT_KG || weight > MAX_WEIGHT_KG) {
        errors[OnboardingField.WEIGHT] =
            UiMessage.of(R.string.onboarding_weight_range, MIN_WEIGHT_KG.toInt(), MAX_WEIGHT_KG.toInt())
    }

    val goalWeight = draft.goalWeightKg
    if (goalWeight != null && (goalWeight < MIN_WEIGHT_KG || goalWeight > MAX_WEIGHT_KG)) {
        errors[OnboardingField.GOAL_WEIGHT] =
            UiMessage.of(R.string.onboarding_goal_weight_range, MIN_WEIGHT_KG.toInt(), MAX_WEIGHT_KG.toInt())
    }

    val pace = draft.goalPaceKgPerWeek
    if (pace == null || pace < MIN_GOAL_PACE_KG_PER_WEEK || pace > MAX_GOAL_PACE_KG_PER_WEEK) {
        errors[OnboardingField.GOAL_PACE] =
            UiMessage.of(R.string.onboarding_goal_pace_range, MIN_GOAL_PACE_KG_PER_WEEK, MAX_GOAL_PACE_KG_PER_WEEK)
    }

    return errors
}
