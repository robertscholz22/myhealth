package com.myhealth.ui.common

import com.myhealth.domain.model.SportGroup
import kotlinx.serialization.json.Json

/**
 * `Profile.preferredSportsJson` (§2.2.1) is a raw JSON string on the domain model — the object
 * caps sessions/week per [SportGroup], e.g. `{"RUN":3,"STRENGTH":2,"SOCCER":2}` — so onboarding
 * and settings, the only screens that edit it, share this tiny codec instead of each rolling
 * their own JSON handling.
 */
private val json = Json { ignoreUnknownKeys = true }

val ONBOARDING_SPORT_GROUPS: List<SportGroup> = listOf(SportGroup.RUN, SportGroup.STRENGTH, SportGroup.SOCCER)

fun encodePreferredSports(sessionsPerWeek: Map<SportGroup, Int>): String =
    json.encodeToString(sessionsPerWeek.entries.associate { (group, count) -> group.name to count })

fun decodePreferredSports(preferredSportsJson: String): Map<SportGroup, Int> = try {
    json.decodeFromString<Map<String, Int>>(preferredSportsJson)
        .mapNotNull { (name, count) -> runCatching { SportGroup.valueOf(name) }.getOrNull()?.let { it to count } }
        .toMap()
} catch (e: Exception) {
    emptyMap()
}
