package com.myhealth.ui.today

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Pure helpers of `TodayUiState.kt` (PLAN P2.10): the sync-status label and the body-chip label. */
class TodayUiStateTest {

    @Test
    fun today01_lastSyncedLabel_formatsTimeOrReportsNeverSynced() {
        val zone = ZoneOffset.UTC
        val millis = LocalDateTime.of(2026, 9, 12, 14, 5).toInstant(ZoneOffset.UTC).toEpochMilli()

        assertThat(lastSyncedLabel(millis, zone)).isEqualTo("Last synced 14:05")
        assertThat(lastSyncedLabel(null, zone)).isEqualTo("Not yet synced")
    }

    @Test
    fun today02_weightChipLabel_showsSignedDeltaToGoalOrPlainWeight() {
        assertThat(weightChipLabel(78.4, 75.0)).isEqualTo("78.4 kg (+3.4 kg to goal)")
        assertThat(weightChipLabel(73.0, 75.0)).isEqualTo("73.0 kg (-2.0 kg to goal)")
        assertThat(weightChipLabel(78.4, null)).isEqualTo("78.4 kg")
        assertThat(weightChipLabel(null, 75.0)).isNull()
    }
}
