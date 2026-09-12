package com.myhealth.ui.ingredients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.di.rememberVm
import com.myhealth.domain.model.MeasureBasis
import com.myhealth.domain.util.EngineWarning
import com.myhealth.ui.common.ErrorBanner
import com.myhealth.ui.theme.MyHealthTheme

/**
 * Create/edit screen for an [com.myhealth.domain.model.Ingredient] (PLAN §4.2 Ingredient edit,
 * P4.3). `id = -1` creates a new ingredient, optionally prefilled with a scanned [barcode];
 * "Scan" opens [onScan] (P4.8/P4.10 fill in the camera and OFF lookup behind it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientEditScreen(
    id: Long,
    barcode: String?,
    onBack: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = rememberVm { graph ->
        IngredientEditViewModel(
            id = id,
            barcode = barcode,
            ingredientRepo = graph.ingredientRepo,
            clock = graph.clock,
            draftStore = graph.draftStore,
            offLookup = graph.offLookup,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New ingredient" else "Edit ingredient") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = vm::requestDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ingredient")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        IngredientEditBody(
            state = state,
            onDraftChange = vm::updateDraft,
            onSave = vm::save,
            onScan = onScan,
            onLookUp = vm::lookUpBarcode,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }

    if (state.pendingDelete) {
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("Delete ingredient?") },
            text = { Text("If it's still used in a logged meal or a template, it will be archived instead.") },
            confirmButton = { TextButton(onClick = vm::confirmDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("Cancel") } },
        )
    }
}

@Composable
private fun IngredientEditBody(
    state: IngredientEditUiState,
    onDraftChange: ((IngredientDraft) -> IngredientDraft) -> Unit,
    onSave: () -> Unit,
    onScan: () -> Unit,
    onLookUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.loadError != null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { Text(state.loadError) }
        return
    }

    val draft = state.draft
    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val banners = (state.scanWarnings + state.warnings).distinctBy { it.message }
            if (banners.isNotEmpty()) {
                item { WarningsBanner(banners) }
            }
            state.lookupError?.let { message ->
                item { ErrorBanner(message = message, onRetry = onLookUp) }
            }
            state.lookupNote?.let { note ->
                item {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { IdentityCard(draft, state.errors, state.isLookingUp, onDraftChange, onScan, onLookUp) }
            item { BasisCard(draft, state.errors, onDraftChange) }
            item { EnergyMacrosCard(draft, state.errors, onDraftChange) }
            item { MoreNutrientsCard(draft, state.errors, onDraftChange) }
            state.saveError?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            Button(onClick = onSave, enabled = !state.isSaving) { Text("Save") }
        }
    }
}


@Preview(showBackground = true, widthDp = 380, heightDp = 900, name = "New ingredient")
@Composable
private fun IngredientEditBodyNewPreview() {
    MyHealthTheme(dynamicColor = false) {
        IngredientEditBody(
            state = IngredientEditUiState(isLoading = false, isNew = true, draft = newIngredientDraft()),
            onDraftChange = {},
            onSave = {},
            onScan = {},
            onLookUp = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 900, name = "Edit with warnings")
@Composable
private fun IngredientEditBodyWarningsPreview() {
    val draft = IngredientDraft(
        id = 5,
        name = "Fruit bar",
        basis = MeasureBasis.PER_100G,
        kcal = 370.0,
        proteinG = 5.0,
        carbsG = 60.0,
        sugarG = 70.0,
        fatG = 7.0,
        satFatG = 9.0,
    )
    MyHealthTheme(dynamicColor = false) {
        IngredientEditBody(
            state = IngredientEditUiState(
                isLoading = false,
                isNew = false,
                draft = draft,
                warnings = listOf(
                    EngineWarning(com.myhealth.domain.model.EngineWarningCode.IMPLAUSIBLE_VALUE, "Sugar (70.0g) is greater than total carbohydrate (60.0g)."),
                ),
            ),
            onDraftChange = {},
            onSave = {},
            onScan = {},
            onLookUp = {},
        )
    }
}
