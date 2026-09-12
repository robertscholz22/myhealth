package com.myhealth.domain.model

/** Mirrors the `body_measurement` table (PLAN §2.2.1). */
data class BodyMeasurement(
    val id: Long = 0,
    val measuredAtMillis: Long,
    val day: Long,
    val weightKg: Double?,
    val bodyFatPercent: Double?,
    val muscleMassKg: Double?,
    val boneMassKg: Double?,
    val bodyWaterPercent: Double?,
    val source: ActivitySource,
    val externalId: String? = null,
    val note: String? = null,
)
