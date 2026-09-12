package com.myhealth.ui.more

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.myhealth.R
import com.myhealth.ui.nav.ActivitiesRoute
import com.myhealth.ui.nav.BackupRoute
import com.myhealth.ui.nav.BodyRoute
import com.myhealth.ui.nav.GoalsRoute
import com.myhealth.ui.nav.ImportRoute
import com.myhealth.ui.nav.IngredientsRoute
import com.myhealth.ui.nav.IntegrationsRoute
import com.myhealth.ui.nav.LoadRoute
import com.myhealth.ui.nav.MealTemplatesRoute
import com.myhealth.ui.nav.RunningPrsRoute
import com.myhealth.ui.nav.SettingsRoute
import com.myhealth.ui.theme.MyHealthTheme

/** Navigation hub (§4.1): links to every destination that is not on the bottom bar. */
data class MoreEntry(@param:StringRes val labelRes: Int, val route: Any)

val moreEntries: List<MoreEntry> = listOf(
    MoreEntry(R.string.more_entry_activities, ActivitiesRoute),
    MoreEntry(R.string.body_title, BodyRoute),
    MoreEntry(R.string.more_entry_running_prs, RunningPrsRoute),
    MoreEntry(R.string.more_entry_load_recovery, LoadRoute),
    MoreEntry(R.string.more_entry_ingredients, IngredientsRoute),
    MoreEntry(R.string.more_entry_meal_templates, MealTemplatesRoute),
    MoreEntry(R.string.more_entry_goals, GoalsRoute),
    MoreEntry(R.string.more_entry_import, ImportRoute),
    MoreEntry(R.string.more_entry_backup, BackupRoute),
    MoreEntry(R.string.settings_title, SettingsRoute),
    MoreEntry(R.string.more_entry_integrations, IntegrationsRoute),
)

@Composable
fun MoreScreen(onNavigate: (Any) -> Unit, modifier: Modifier = Modifier) {
    MoreContent(entries = moreEntries, onNavigate = onNavigate, modifier = modifier)
}

@Composable
private fun MoreContent(
    entries: List<MoreEntry>,
    onNavigate: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(entries) { entry ->
            ListItem(
                headlineContent = { Text(stringResource(entry.labelRes)) },
                modifier = Modifier.clickable { onNavigate(entry.route) },
            )
            HorizontalDivider()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MoreContentPreview() {
    MyHealthTheme(dynamicColor = false) { MoreContent(moreEntries, onNavigate = {}) }
}
