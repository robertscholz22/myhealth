package com.myhealth.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.myhealth.domain.model.AppSettings
import com.myhealth.domain.model.ThemeMode
import com.myhealth.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The single DataStore Preferences file backing [AppSettings] (PLAN P1.8). */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * [SettingsRepository] backed by `androidx.datastore.preferences` (PLAN P1.8).
 *
 * [settings] never fails: DataStore's own `Flow` already recovers from a corrupt file to
 * [emptyPreferences] (which this class then reads back as [SettingsKeys.Defaults]), so no
 * `catch`/fallback is needed here.
 */
class DataStoreSettingsRepository(context: Context) : SettingsRepository {

    private val dataStore = context.settingsDataStore
    private val defaults = SettingsKeys.Defaults

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs -> prefs.toAppSettings() }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        sleepTargetHours = this[SettingsKeys.SLEEP_TARGET_HOURS] ?: defaults.sleepTargetHours,
        includeTreadmillInPrs = this[SettingsKeys.INCLUDE_TREADMILL_IN_PRS] ?: defaults.includeTreadmillInPrs,
        mobilityOnRestDays = this[SettingsKeys.MOBILITY_ON_REST_DAYS] ?: defaults.mobilityOnRestDays,
        syncIntervalHours = this[SettingsKeys.SYNC_INTERVAL_HOURS] ?: defaults.syncIntervalHours,
        themeMode = SettingsKeys.themeModeOf(this[SettingsKeys.THEME_MODE]),
        useDynamicColor = this[SettingsKeys.USE_DYNAMIC_COLOR] ?: defaults.useDynamicColor,
        allowDestructiveMigration = this[SettingsKeys.ALLOW_DESTRUCTIVE_MIGRATION]
            ?: defaults.allowDestructiveMigration,
        suggestionHorizonDays = this[SettingsKeys.SUGGESTION_HORIZON_DAYS] ?: defaults.suggestionHorizonDays,
        offUserAgentContact = this[SettingsKeys.OFF_USER_AGENT_CONTACT] ?: defaults.offUserAgentContact,
        garminDirectEnabled = this[SettingsKeys.GARMIN_DIRECT_ENABLED] ?: defaults.garminDirectEnabled,
        hasCompletedOnboarding = this[SettingsKeys.HAS_COMPLETED_ONBOARDING] ?: defaults.hasCompletedOnboarding,
    )

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toAppSettings())
            prefs[SettingsKeys.SLEEP_TARGET_HOURS] = next.sleepTargetHours
            prefs[SettingsKeys.INCLUDE_TREADMILL_IN_PRS] = next.includeTreadmillInPrs
            prefs[SettingsKeys.MOBILITY_ON_REST_DAYS] = next.mobilityOnRestDays
            prefs[SettingsKeys.SYNC_INTERVAL_HOURS] = next.syncIntervalHours
            prefs[SettingsKeys.THEME_MODE] = next.themeMode.name
            prefs[SettingsKeys.USE_DYNAMIC_COLOR] = next.useDynamicColor
            prefs[SettingsKeys.ALLOW_DESTRUCTIVE_MIGRATION] = next.allowDestructiveMigration
            prefs[SettingsKeys.SUGGESTION_HORIZON_DAYS] = next.suggestionHorizonDays
            prefs[SettingsKeys.OFF_USER_AGENT_CONTACT] = next.offUserAgentContact
            prefs[SettingsKeys.GARMIN_DIRECT_ENABLED] = next.garminDirectEnabled
            prefs[SettingsKeys.HAS_COMPLETED_ONBOARDING] = next.hasCompletedOnboarding
        }
    }

    override suspend fun setSleepTargetHours(hours: Double) = update { it.copy(sleepTargetHours = hours) }

    override suspend fun setIncludeTreadmillInPrs(value: Boolean) =
        update { it.copy(includeTreadmillInPrs = value) }

    override suspend fun setMobilityOnRestDays(value: Boolean) = update { it.copy(mobilityOnRestDays = value) }

    override suspend fun setSyncIntervalHours(hours: Int) = update { it.copy(syncIntervalHours = hours) }

    override suspend fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    override suspend fun setUseDynamicColor(value: Boolean) = update { it.copy(useDynamicColor = value) }

    override suspend fun setAllowDestructiveMigration(value: Boolean) =
        update { it.copy(allowDestructiveMigration = value) }

    override suspend fun setSuggestionHorizonDays(days: Int) = update { it.copy(suggestionHorizonDays = days) }

    override suspend fun setOffUserAgentContact(contact: String) =
        update { it.copy(offUserAgentContact = contact) }

    override suspend fun setGarminDirectEnabled(value: Boolean) = update { it.copy(garminDirectEnabled = value) }

    override suspend fun setHasCompletedOnboarding(value: Boolean) =
        update { it.copy(hasCompletedOnboarding = value) }
}
