package com.myhealth.ui.settings

import com.myhealth.domain.model.AppSettings
import com.myhealth.domain.model.Profile

/** ViewModel state for [SettingsScreen]: the profile row plus every [AppSettings] key (§4.2 Settings). */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val profile: Profile? = null,
    val settings: AppSettings = AppSettings(),
)
