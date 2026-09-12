package com.myhealth.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource

/**
 * A user-facing message produced outside a composable (PLAN P8.1).
 *
 * ViewModels must not build display strings — `domain/` is Android-free and a `ViewModel` has no
 * `Context` — so they emit a resource id plus its format arguments and the composable resolves it.
 * [args] are passed straight to `getString(id, …)`, so they must already be primitives or strings.
 */
@Immutable
data class UiMessage(@StringRes val resId: Int, val args: List<Any> = emptyList()) {

    /** Resolves the message outside composition (snackbars, `LaunchedEffect`, workers). */
    fun resolve(context: Context): String =
        if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args.toTypedArray())

    companion object {
        fun of(@StringRes resId: Int, vararg args: Any): UiMessage = UiMessage(resId, args.toList())
    }
}

/** Resolves the message inside composition. */
@Composable
fun UiMessage.resolve(): String =
    if (args.isEmpty()) stringResource(resId) else stringResource(resId, *args.toTypedArray())
