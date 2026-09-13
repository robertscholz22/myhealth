package com.myhealth.di

import androidx.activity.result.contract.ActivityResultContract
import com.myhealth.data.healthconnect.HcPermissions
import com.myhealth.data.healthconnect.HealthConnectProvider
import com.myhealth.data.healthconnect.HcStatus as DataHcStatus

/**
 * UI-safe mirror of [DataHcStatus] (PLAN P2.8): `ui/` must never import `com.myhealth.data.*`
 * (enforced by `ArchitectureTest`), so the Integrations screen sees this `di`-owned copy instead.
 */
enum class HcStatus { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

/**
 * What the Integrations screen needs from Health Connect, without touching `data.healthconnect`
 * types directly (P2.8). `di/` is allowed to reference `data/` (§1.2), so this interface — and its
 * one implementation below — is the seam.
 */
interface HcIntegration {
    fun status(): HcStatus
    suspend fun granted(): Set<String>
    val allPermissions: Set<String>

    /** The per-session detail permissions (P12: power) that are optional for sync. */
    val optionalDetailPermissions: Set<String>
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>>
}

/** [HcIntegration] backed by the real [HealthConnectProvider] held in [AppGraph]. */
class HealthConnectIntegration(private val provider: HealthConnectProvider) : HcIntegration {

    override fun status(): HcStatus = when (provider.status()) {
        DataHcStatus.AVAILABLE -> HcStatus.AVAILABLE
        DataHcStatus.UPDATE_REQUIRED -> HcStatus.UPDATE_REQUIRED
        DataHcStatus.UNAVAILABLE -> HcStatus.UNAVAILABLE
    }

    override suspend fun granted(): Set<String> = provider.granted()

    override val allPermissions: Set<String> = HcPermissions.ALL

    override val optionalDetailPermissions: Set<String> = HcPermissions.OPTIONAL_DETAIL

    override fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        provider.permissionContract()
}
