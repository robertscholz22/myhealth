package com.myhealth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.myhealth.di.HealthConnectIntegration
import com.myhealth.ui.common.PlatformUi

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

/**
 * Falls back to the raw string when the SDK's own (deprecated)
 * `HealthConnectClient.getHealthConnectSettingsAction()` accessor is unavailable to callers.
 */
private const val ACTION_HEALTH_CONNECT_SETTINGS = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"

/** [PlatformUi] on Android: the CameraX/ML Kit scanner and the Health Connect permission flow. */
class AndroidPlatformUi(
    private val context: Context,
    private val healthConnect: HealthConnectIntegration,
) : PlatformUi {

    @Composable
    override fun ScanScreen(onBack: () -> Unit, onReview: () -> Unit, onIngredient: (barcode: String) -> Unit) {
        com.myhealth.ui.camera.ScanScreen(onBack = onBack, onReview = onReview, onIngredient = onIngredient)
    }

    @Composable
    override fun rememberHealthPermissionRequest(onResult: (granted: Set<String>) -> Unit): (permissions: Set<String>) -> Unit {
        val latest by rememberUpdatedState(onResult)
        val contract = remember { healthConnect.permissionContract() }
        val launcher = rememberLauncherForActivityResult(contract) { result -> latest(result) }
        return remember(launcher) { { permissions -> launcher.launch(permissions) } }
    }

    override fun openHealthAppStore() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE"))
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No Play Store on this device — nothing more we can do from here.
        }
    }

    override fun openHealthSettings() {
        val intent = Intent(ACTION_HEALTH_CONNECT_SETTINGS)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Health Connect is not installed — the status card already says so.
        }
    }
}
