package com.myhealth.ui.settings

import com.google.common.truth.Truth.assertThat
import com.myhealth.data.healthconnect.HcPermissions
import org.junit.Test

/**
 * [permissionLabel] must never leave a permission unexplained on the Integrations screen (P2.8) —
 * this only touches `data.healthconnect.HcPermissions` from a test, never from `ui/` main sources
 * (`ArchitectureTest` only scans `src/main`).
 */
class IntegrationsUiStateTest {

    @Test
    fun every_permission_in_hc_permissions_all_has_a_non_blank_label() {
        val offenders = HcPermissions.ALL.filter { permissionLabel(it).isBlank() }

        assertThat(offenders).isEmpty()
    }

    @Test
    fun known_permissions_get_their_curated_label() {
        assertThat(permissionLabel("android.permission.health.READ_SLEEP")).isEqualTo("Sleep")
        assertThat(permissionLabel(HcPermissions.HISTORY)).isEqualTo("Full history (30+ days)")
    }

    @Test
    fun an_unrecognised_permission_still_gets_a_readable_fallback_label() {
        val label = permissionLabel("android.permission.health.READ_SOMETHING_NEW")

        assertThat(label).isEqualTo("Something New")
    }
}
