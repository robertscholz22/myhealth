package com.myhealth.ui.ingredients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myhealth.domain.model.MeasureBasis
import com.myhealth.domain.util.EngineWarning
import com.myhealth.ui.common.DropdownField
import com.myhealth.ui.common.ErrorBanner
import com.myhealth.ui.common.NumberField
import com.myhealth.ui.common.SectionCard

/**
 * The four cards of [IngredientEditScreen] plus its warnings banner (PLAN P4.3), split out of the
 * screen file when the barcode lookup of P4.10 pushed it past the ~400-line limit (R10).
 */

@Composable
internal fun WarningsBanner(warnings: List<EngineWarning>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        warnings.distinctBy { it.message }.forEach { warning -> ErrorBanner(message = warning.message) }
    }
}

@Composable
internal fun IdentityCard(
    draft: IngredientDraft,
    errors: Map<IngredientField, String>,
    isLookingUp: Boolean,
    onDraftChange: ((IngredientDraft) -> IngredientDraft) -> Unit,
    onScan: () -> Unit,
    onLookUp: () -> Unit,
) {
    SectionCard(title = "Ingredient") {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { name -> onDraftChange { it.copy(name = name) } },
            label = { Text("Name") },
            singleLine = true,
            isError = errors.containsKey(IngredientField.NAME),
            supportingText = errors[IngredientField.NAME]?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.brand,
            onValueChange = { brand -> onDraftChange { it.copy(brand = brand) } },
            label = { Text("Brand (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft.barcode,
                onValueChange = { code -> onDraftChange { it.copy(barcode = code) } },
                label = { Text("Barcode (optional)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onScan) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Text(" Scan")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLookingUp) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            TextButton(onClick = onLookUp, enabled = !isLookingUp && draft.barcode.isNotBlank()) {
                Text("Look up barcode")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Favorite", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = draft.isFavorite, onCheckedChange = { v -> onDraftChange { it.copy(isFavorite = v) } })
        }
    }
}

@Composable
internal fun BasisCard(
    draft: IngredientDraft,
    errors: Map<IngredientField, String>,
    onDraftChange: ((IngredientDraft) -> IngredientDraft) -> Unit,
) {
    SectionCard(title = "Basis & serving") {
        DropdownField(
            label = "Values are per",
            options = MeasureBasis.entries,
            selected = draft.basis,
            optionLabel = { it.unitSuffix() },
            onSelect = { basis -> onDraftChange { it.copy(basis = basis) } },
        )
        if (draft.basis == MeasureBasis.PER_PIECE) {
            NumberField(
                label = "Piece weight",
                value = draft.pieceGrams,
                onValueChange = { v -> onDraftChange { it.copy(pieceGrams = v) } },
                suffix = "g",
                decimals = 1,
                isError = errors.containsKey(IngredientField.PIECE_GRAMS),
                supportingText = errors[IngredientField.PIECE_GRAMS],
            )
        }
        NumberField(
            label = "Default serving",
            value = draft.servingGrams,
            onValueChange = { v -> onDraftChange { it.copy(servingGrams = v) } },
            suffix = "g",
            decimals = 1,
        )
        OutlinedTextField(
            value = draft.servingLabel,
            onValueChange = { label -> onDraftChange { it.copy(servingLabel = label) } },
            label = { Text("Serving label (optional, e.g. \"1 slice (30 g)\")") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun EnergyMacrosCard(
    draft: IngredientDraft,
    errors: Map<IngredientField, String>,
    onDraftChange: ((IngredientDraft) -> IngredientDraft) -> Unit,
) {
    val suffix = draft.basis.unitSuffix()
    SectionCard(title = "Energy & macros ($suffix)") {
        NumberField(
            label = "Calories",
            value = draft.kcal,
            onValueChange = { v -> onDraftChange { it.copy(kcal = v) } },
            suffix = "kcal",
            decimals = 0,
            isError = errors.containsKey(IngredientField.KCAL),
            supportingText = errors[IngredientField.KCAL],
        )
        NumberField(
            label = "Protein",
            value = draft.proteinG,
            onValueChange = { v -> onDraftChange { it.copy(proteinG = v) } },
            suffix = "g",
            isError = errors.containsKey(IngredientField.PROTEIN),
            supportingText = errors[IngredientField.PROTEIN],
        )
        NumberField(
            label = "Carbohydrate",
            value = draft.carbsG,
            onValueChange = { v -> onDraftChange { it.copy(carbsG = v) } },
            suffix = "g",
            isError = errors.containsKey(IngredientField.CARBS),
            supportingText = errors[IngredientField.CARBS],
        )
        NumberField(
            label = "Fat",
            value = draft.fatG,
            onValueChange = { v -> onDraftChange { it.copy(fatG = v) } },
            suffix = "g",
            isError = errors.containsKey(IngredientField.FAT),
            supportingText = errors[IngredientField.FAT],
        )
    }
}

@Composable
internal fun MoreNutrientsCard(
    draft: IngredientDraft,
    errors: Map<IngredientField, String>,
    onDraftChange: ((IngredientDraft) -> IngredientDraft) -> Unit,
) {
    SectionCard(title = "More nutrients") {
        NumberField(
            label = "of which sugars",
            value = draft.sugarG,
            onValueChange = { v -> onDraftChange { it.copy(sugarG = v) } },
            suffix = "g",
            isError = errors.containsKey(IngredientField.SUGAR),
            supportingText = errors[IngredientField.SUGAR],
        )
        NumberField(
            label = "of which saturates",
            value = draft.satFatG,
            onValueChange = { v -> onDraftChange { it.copy(satFatG = v) } },
            suffix = "g",
            isError = errors.containsKey(IngredientField.SAT_FAT),
            supportingText = errors[IngredientField.SAT_FAT],
        )
        NumberField(
            label = "Fiber",
            value = draft.fiberG,
            onValueChange = { v -> onDraftChange { it.copy(fiberG = v) } },
            suffix = "g",
            isError = errors.containsKey(IngredientField.FIBER),
            supportingText = errors[IngredientField.FIBER],
        )
        NumberField(
            label = "Salt",
            value = draft.saltG,
            onValueChange = { v -> onDraftChange { it.copy(saltG = v) } },
            suffix = "g",
            decimals = 2,
            isError = errors.containsKey(IngredientField.SALT),
            supportingText = errors[IngredientField.SALT],
        )
        NumberField(
            label = "Sodium (derived)",
            value = draft.sodiumG,
            onValueChange = {},
            suffix = "g",
            decimals = 3,
            enabled = false,
        )
    }
}
