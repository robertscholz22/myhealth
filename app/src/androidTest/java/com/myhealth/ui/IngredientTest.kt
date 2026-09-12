package com.myhealth.ui

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myhealth.MainActivity
import com.myhealth.R
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PLAN P10.2: hand-create an ingredient (name + kcal/protein/carbs/fat), find it again by search,
 * then delete it (an unused ingredient is deleted outright rather than archived — §2.2.5).
 */
@RunWith(AndroidJUnit4::class)
class IngredientTest {

    @get:Rule(order = 0)
    val clearState = ClearAppStateRule()

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedProfile() {
        testGraph().seedProfileBlocking()
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
    }

    @Test
    fun createIngredient_findsItBySearch_thenDeletes() {
        val activity = composeTestRule.activity
        val name = "Test Oatmeal"

        composeTestRule.onNodeWithText(activity.getString(R.string.nav_more)).performClick()
        composeTestRule.waitUntilTextExists(activity.getString(R.string.more_entry_ingredients))
        composeTestRule.onNodeWithText(activity.getString(R.string.more_entry_ingredients)).performClick()
        composeTestRule.waitUntilTextExists(
            activity.getString(R.string.ingredients_new_content_description),
            byContentDescription = true,
        )

        composeTestRule.onNodeWithContentDescription(activity.getString(R.string.ingredients_new_content_description))
            .performClick()
        composeTestRule.waitUntilTextExists(activity.getString(R.string.ingredient_edit_title_new))

        composeTestRule.onNodeWithText(activity.getString(R.string.ingredient_edit_name_label)).performTextInput(name)

        // The energy/macro fields sit in cards below the fold of the ingredient edit LazyColumn,
        // so they are not composed yet: scroll the list itself to each field's node before typing
        // into it (PLAN P10.2 test-support notes on `performScrollToNode`).
        val caloriesLabel = activity.getString(R.string.ingredient_edit_calories_label)
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText(caloriesLabel))
        composeTestRule.onNodeWithText(caloriesLabel).performTextInput("350")

        val proteinLabel = activity.getString(R.string.ingredient_edit_protein_label)
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText(proteinLabel))
        composeTestRule.onNodeWithText(proteinLabel).performTextInput("12")

        val carbsLabel = activity.getString(R.string.ingredient_edit_carbs_label)
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText(carbsLabel))
        composeTestRule.onNodeWithText(carbsLabel).performTextInput("60")

        val fatLabel = activity.getString(R.string.ingredient_edit_fat_label)
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText(fatLabel))
        composeTestRule.onNodeWithText(fatLabel).performTextInput("6")

        // The Save button is a fixed footer below the LazyColumn, not a list item, so it is
        // already on screen and needs no scroll.
        composeTestRule.onNodeWithText(activity.getString(R.string.action_save)).performClick()

        // Back on the list with an empty query: the new ingredient is there.
        composeTestRule.waitUntilTextExists(name)

        // Search finds it too. A partial query (rather than the full name) keeps the search
        // field's own current-text node from colliding with the exact-text match on the list row.
        composeTestRule.onNodeWithText(activity.getString(R.string.ingredients_search_label)).performTextInput("Oatmeal")
        composeTestRule.waitUntilTextExists(name)

        // Open it and delete it (unused, so this is a real delete, not an archive).
        composeTestRule.onNodeWithText(name).performClick()
        composeTestRule.waitUntilTextExists(activity.getString(R.string.ingredient_edit_delete_content_description), byContentDescription = true)
        composeTestRule.onNodeWithContentDescription(activity.getString(R.string.ingredient_edit_delete_content_description))
            .performClick()
        composeTestRule.onNodeWithText(activity.getString(R.string.action_delete)).performClick()

        composeTestRule.waitUntilTextExists(activity.getString(R.string.ingredients_empty_title))
    }
}
