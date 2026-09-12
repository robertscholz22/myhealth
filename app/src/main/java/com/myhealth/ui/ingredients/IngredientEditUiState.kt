package com.myhealth.ui.ingredients

import com.myhealth.domain.util.EngineWarning

/** ViewModel state for [IngredientEditScreen] (PLAN §4.2 Ingredient edit, P4.3). */
data class IngredientEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val draft: IngredientDraft = IngredientDraft(),
    val errors: Map<IngredientField, String> = emptyMap(),
    /** Live plausibility warnings from [com.myhealth.domain.engine.nutrition.NutritionValidator]
     * — informational, never blocks [errors]-free saves (§3.6.1 step 7 / P4.3). */
    val warnings: List<EngineWarning> = emptyList(),
    /** Warnings that came with a scan (P4.9) — shown once, alongside the live [warnings]. */
    val scanWarnings: List<EngineWarning> = emptyList(),
    /** An Open Food Facts lookup for the barcode in the form is in flight (P4.10). */
    val isLookingUp: Boolean = false,
    val lookupError: String? = null,
    val lookupNote: String? = null,
    val pendingDelete: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val loadError: String? = null,
    /** One-shot: the screen navigates back once either flips to `true`. */
    val saved: Boolean = false,
    val deleted: Boolean = false,
)
