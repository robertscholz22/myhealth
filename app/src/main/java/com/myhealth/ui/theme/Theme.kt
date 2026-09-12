package com.myhealth.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.myhealth.domain.model.ThemeMode

/**
 * App theme: Material 3 with the green scheme of [LightColors]/[DarkColors] (PLAN P8.6a).
 *
 * Dynamic (wallpaper) colour is opt-in: [dynamicColor] defaults to **false** and is fed from
 * `AppSettings.useDynamicColor` by `MainActivity`, which also resolves `AppSettings.themeMode`
 * into [darkTheme] via [isDarkTheme].
 */
@Composable
fun MyHealthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MyHealthTypography,
        content = content,
    )
}

/** `AppSettings.themeMode` → the `darkTheme` flag; `SYSTEM` follows the device setting. */
@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
