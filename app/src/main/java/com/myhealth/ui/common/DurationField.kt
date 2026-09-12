package com.myhealth.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.myhealth.R
import com.myhealth.ui.theme.MyHealthTheme

/**
 * A whole-minutes duration field (§4.2 Event edit / Planned session edit) — a thin [NumberField]
 * wrapper so every duration input in the app rounds to an `Int` and shows the same "min" suffix.
 */
@Composable
fun DurationField(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.common_duration_label),
    isError: Boolean = false,
    supportingText: String? = null,
) {
    NumberField(
        label = label,
        value = value?.toDouble(),
        onValueChange = { d -> onValueChange(d?.toInt()) },
        modifier = modifier,
        suffix = stringResource(R.string.common_duration_suffix),
        decimals = 0,
        isError = isError,
        supportingText = supportingText,
    )
}

@Preview(showBackground = true)
@Composable
private fun DurationFieldPreview() {
    MyHealthTheme(dynamicColor = false) {
        DurationField(value = 60, onValueChange = {})
    }
}
