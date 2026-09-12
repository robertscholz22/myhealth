package com.myhealth.ui.meals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.di.rememberVm
import com.myhealth.domain.model.Ingredient
import com.myhealth.domain.model.MealSlot
import com.myhealth.domain.model.QuantityUnit
import com.myhealth.ui.calendar.displayName
import com.myhealth.ui.common.DropdownField
import com.myhealth.ui.common.ErrorBanner
import com.myhealth.ui.common.NumberField
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.nutrition.label
import com.myhealth.ui.nutrition.validUnitsFor
import com.myhealth.ui.theme.MyHealthTheme

/** Create/edit a meal template: name, default slot, item rows, live totals (PLAN §4.2, P4.4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealTemplateEditScreen(id: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm = rememberVm { graph ->
        MealTemplateEditViewModel(id, graph.mealRepo, graph.ingredientRepo, graph.clock)
    }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New template" else "Edit template") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = vm::requestDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete template")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            MealTemplateEditContent(
                state = state,
                actions = TemplateEditActions(
                    onNameChange = vm::setName,
                    onSlotChange = vm::setDefaultSlot,
                    onNoteChange = vm::setNote,
                    onFavoriteChange = vm::setFavorite,
                    onQuantityChange = vm::setItemQuantity,
                    onUnitChange = vm::setItemUnit,
                    onRemoveItem = vm::removeItem,
                    onAddItemClick = vm::openPicker,
                    onSave = vm::save,
                ),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }

    if (state.pickerOpen) {
        IngredientPickerSheet(
            query = state.pickerQuery,
            results = state.pickerResults,
            onQueryChange = vm::setPickerQuery,
            onSelect = vm::addIngredient,
            onDismiss = vm::closePicker,
        )
    }

    if (state.pendingDelete) {
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("Delete this template?") },
            text = { Text("Meals already logged from it keep their own copy of the ingredients.") },
            confirmButton = { TextButton(onClick = vm::confirmDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("Cancel") } },
        )
    }
}

/** Callbacks of [MealTemplateEditContent]. */
data class TemplateEditActions(
    val onNameChange: (String) -> Unit,
    val onSlotChange: (MealSlot?) -> Unit,
    val onNoteChange: (String) -> Unit,
    val onFavoriteChange: (Boolean) -> Unit,
    val onQuantityChange: (Int, Double?) -> Unit,
    val onUnitChange: (Int, QuantityUnit) -> Unit,
    val onRemoveItem: (Int) -> Unit,
    val onAddItemClick: () -> Unit,
    val onSave: () -> Unit,
)

@Composable
private fun MealTemplateEditContent(
    state: MealTemplateEditUiState,
    actions: TemplateEditActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.loadError?.let { item { ErrorBanner(message = it) } }
        state.saveError?.let { item { ErrorBanner(message = it) } }

        item {
            SectionCard(title = "Template") {
                OutlinedTextField(
                    value = state.draft.name,
                    onValueChange = actions.onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    isError = state.errors.containsKey(TemplateField.NAME),
                    supportingText = state.errors[TemplateField.NAME]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(
                    label = "Default slot",
                    options = listOf(null) + MealSlot.entries.toList(),
                    selected = state.draft.defaultSlot,
                    optionLabel = { slot -> slot?.displayName() ?: "No default" },
                    onSelect = actions.onSlotChange,
                )
                OutlinedTextField(
                    value = state.draft.note,
                    onValueChange = actions.onNoteChange,
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Favorite", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = state.draft.isFavorite, onCheckedChange = actions.onFavoriteChange)
                }
            }
        }

        item {
            SectionCard(
                title = "Ingredients",
                action = {
                    IconButton(onClick = actions.onAddItemClick) {
                        Icon(Icons.Filled.Add, contentDescription = "Add ingredient")
                    }
                },
            ) {
                if (state.draft.items.isEmpty()) {
                    Text(
                        text = "No ingredients yet — add one with +.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.draft.items.forEachIndexed { index, item ->
                    TemplateItemRow(
                        item = item,
                        onQuantityChange = { value -> actions.onQuantityChange(index, value) },
                        onUnitChange = { unit -> actions.onUnitChange(index, unit) },
                        onRemove = { actions.onRemoveItem(index) },
                    )
                }
                state.errors[TemplateField.ITEMS]?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            SectionCard(title = "Totals") {
                Text(text = macroSummary(state.totals), style = MaterialTheme.typography.bodyLarge)
            }
        }

        item {
            Button(
                onClick = actions.onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save template") }
        }
    }
}

@Composable
private fun TemplateItemRow(
    item: TemplateItemDraft,
    onQuantityChange: (Double?) -> Unit,
    onUnitChange: (QuantityUnit) -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove ${item.name}")
            }
        }
        if (item.ingredient == null) {
            Text(
                text = "This ingredient is no longer available; it will not count towards the totals.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberField(
                label = "Quantity",
                value = item.quantity,
                onValueChange = onQuantityChange,
                decimals = 0,
                modifier = Modifier.weight(1f),
            )
            val units = item.ingredient?.let { validUnitsFor(it.basis, it) } ?: listOf(item.unit)
            DropdownField(
                label = "Unit",
                options = units,
                selected = item.unit,
                optionLabel = { it.label() },
                onSelect = onUnitChange,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider()
    }
}

/** Searchable ingredient picker (§4.2 Meal template edit: "item rows (ingredient + qty + unit)"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientPickerSheet(
    query: String,
    results: List<Ingredient>,
    onQueryChange: (String) -> Unit,
    onSelect: (Ingredient) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search ingredients") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (results.isEmpty()) {
                Text(
                    text = "No matching ingredients.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(results, key = { it.id }) { ingredient ->
                    ListItem(
                        headlineContent = { Text(ingredient.name) },
                        supportingContent = ingredient.brand?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(ingredient) },
                    )
                    HorizontalDivider()
                }
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ) { Text("Close") }
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun MealTemplateEditContentPreview() {
    MyHealthTheme(dynamicColor = false) {
        MealTemplateEditContent(
            state = MealTemplateEditUiState(
                isLoading = false,
                isNew = false,
                draft = MealTemplateDraft(
                    id = 1L,
                    name = "Oatmeal with berries",
                    defaultSlot = MealSlot.BREAKFAST,
                    items = listOf(
                        TemplateItemDraft(1L, "Rolled oats", 80.0, QuantityUnit.G, null),
                        TemplateItemDraft(2L, "Blueberries", 100.0, QuantityUnit.G, null),
                    ),
                ),
                totals = com.myhealth.domain.model.MacroTotals(
                    kcal = 350.0, proteinG = 12.0, carbsG = 58.0, fatG = 7.0,
                    fiberG = 9.0, sugarG = 12.0, satFatG = 1.0, saltG = 0.1,
                ),
            ),
            actions = TemplateEditActions({}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}),
        )
    }
}
