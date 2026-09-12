package com.myhealth.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
data class MoreEntry(val label: String, val route: Any)

val moreEntries: List<MoreEntry> = listOf(
    MoreEntry("Activities", ActivitiesRoute),
    MoreEntry("Body & Health", BodyRoute),
    MoreEntry("Running PRs", RunningPrsRoute),
    MoreEntry("Load & Recovery", LoadRoute),
    MoreEntry("Ingredients", IngredientsRoute),
    MoreEntry("Meal Templates", MealTemplatesRoute),
    MoreEntry("Goals", GoalsRoute),
    MoreEntry("Import", ImportRoute),
    MoreEntry("Backup", BackupRoute),
    MoreEntry("Settings", SettingsRoute),
    MoreEntry("Integrations", IntegrationsRoute),
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
                headlineContent = { Text(entry.label) },
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
