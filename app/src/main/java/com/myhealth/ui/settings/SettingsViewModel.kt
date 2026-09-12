package com.myhealth.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealth.domain.model.AppSettings
import com.myhealth.domain.model.Profile
import com.myhealth.domain.repository.ProfileRepository
import com.myhealth.domain.repository.SettingsRepository
import com.myhealth.sync.SyncScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

/** Every profile field plus every [AppSettings] key, edited in place (§4.2 Settings). */
class SettingsViewModel(
    private val profileRepo: ProfileRepository,
    private val settingsRepo: SettingsRepository,
    private val syncScheduler: SyncScheduler,
    private val clock: Clock,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        profileRepo.observeProfile(),
        settingsRepo.settings,
    ) { profile, settings ->
        SettingsUiState(isLoading = false, profile = profile, settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onProfileChange(profile: Profile) {
        viewModelScope.launch {
            profileRepo.upsert(profile.copy(updatedAtMillis = clock.millis()))
            // Sex, height, NEAT, goal weight and pace all feed the target engine (P4.12).
            syncScheduler.requestTargetRecompute()
        }
    }

    fun onSettingsChange(settings: AppSettings) {
        viewModelScope.launch { settingsRepo.update { settings } }
    }
}
