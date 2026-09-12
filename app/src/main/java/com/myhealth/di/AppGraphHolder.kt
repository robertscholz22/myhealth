package com.myhealth.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** Provides the process-wide [AppGraph] to composables. Set once in `MainActivity`. */
val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph missing") }

@Composable
@ReadOnlyComposable
fun appGraph(): AppGraph = LocalAppGraph.current
