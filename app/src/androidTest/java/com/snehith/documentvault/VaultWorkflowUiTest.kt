package com.snehith.documentvault

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Drives the real app in the isolated uxcheck package with fictional records,
 * which are removed afterwards. Never runs against the production or .v2 app. */
@RunWith(AndroidJUnit4::class)
class VaultWorkflowUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val tag=UUID.randomUUID().toString().take(6).uppercase()
    private val title="UI Fictional Licence $tag"
    private val person=VaultProfile("ui-person-$tag","UI Person $tag")
    private val card=VaultCard(id="ui-card-$tag",title=title,category="Work",profile=person.id,expiry="2030-12-31",
        fields=listOf(DataField("Licence Number","FIC-$tag",id="ui-field-$tag")))
    private val repo get()=VaultStorage.get(context)

    @Before fun seed() {
        check(context.packageName=="com.snehith.documentvault.uxcheck") {
            "UI workflow tests require -PvaultDebugSuffix=.uxcheck"
        }
        // The dock renders its labels in capitals.
        compose.waitUntil(20_000){ compose.onAllNodesWithText("Cards",ignoreCase=true).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { repo.saveProfile(person);repo.saveCard(card) }
    }
    @After fun cleanup() {
        compose.runOnIdle {
            repo.cards.firstOrNull{it.id==card.id}?.let(repo::deleteCard)
            if(repo.profiles.any{it.id==person.id})repo.removePerson(person.id)
        }
    }
    /** System Back reaches Compose's BackHandler through this dispatcher. */
    private fun pressBack() {
        compose.activityRule.scenario.onActivity{it.onBackPressedDispatcher.onBackPressed()}
        compose.waitForIdle()
    }
    private fun clickLabel(label:String)=compose.onNode(SemanticsMatcher("click label $label"){
        it.config.getOrNull(SemanticsActions.OnClick)?.label==label}).performClick()
    private fun openCards() {
        compose.onAllNodesWithText("Cards",ignoreCase=true).onFirst().performClick()
        clickLabel("Choose whose documents to show")
        compose.onNodeWithText(person.name).performClick()
        compose.waitUntil(10_000){ compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun openEditor() {
        compose.onNodeWithText(title,substring=true).performClick()
        compose.onNodeWithContentDescription("More document options").performClick()
        compose.onNodeWithText("Edit details").performClick()
        compose.waitUntil(10_000){ compose.onAllNodesWithText("Document title").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun save() {
        compose.onNodeWithText(" Save").performClick()
        compose.waitUntil(10_000){ compose.onAllNodesWithText("Document title").fetchSemanticsNodes().isEmpty() }
    }

    /** Workflows 1 and 20: open a saved document, survive recreation, system Back. */
    @Test fun openDocumentSurvivesRecreationAndBackReturnsToList() {
        openCards()
        compose.onNodeWithText(title,substring=true).performClick()
        compose.onNodeWithContentDescription("More document options").assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000){ compose.onAllNodesWithContentDescription("More document options").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(title,substring=true).onFirst().assertExists()
        pressBack()
        compose.waitUntil(10_000){ compose.onAllNodesWithContentDescription("More document options").fetchSemanticsNodes().isEmpty() }
        compose.onAllNodesWithText(title).onFirst().assertExists()
    }
    /** Workflows 2 and 6: change owner while a person filter is active. */
    @Test fun ownerChangeUnderPersonFilterKeepsDocumentListed() {
        openCards()
        clickLabel("Choose whose documents to show")
        compose.onNodeWithText(person.name).performClick()
        compose.onAllNodesWithText(title).onFirst().assertExists()
        openEditor()
        compose.onNodeWithText("Profile: "+person.name).performClick()
        compose.onNodeWithText("Me").performClick()
        save()
        assertEquals("self",repo.cards.first{it.id==card.id}.profile)
        pressBack()
        compose.waitUntil(10_000){ compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
    }
    /** Workflows 3 and 5: change category and a field, then restart the activity. */
    @Test fun categoryAndFieldEditsSurviveRestart() {
        openCards()
        openEditor()
        compose.onNodeWithText("Category: Work").performClick()
        compose.onNodeWithText("Identity").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("FIC-$tag"))
        compose.onNodeWithText("FIC-$tag").performTextReplacement("EDITED-$tag")
        save()
        compose.activityRule.scenario.recreate()
        // The recreated activity reloads the vault before it shows (and handles Back on) screens.
        compose.waitUntil(10_000){ compose.onAllNodesWithText(title,substring=true).fetchSemanticsNodes().isNotEmpty() }
        val saved=VaultRepository(context).cards.first{it.id==card.id}
        assertEquals("Identity",saved.category)
        assertEquals("EDITED-$tag",saved.fields.single().value)
        assertEquals("manual",saved.fields.single().source)
        pressBack()
        compose.waitUntil(10_000){ compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun newCategoryAndFixedSaveBarWork() {
        openCards();openEditor()
        compose.onNodeWithText(" Save").assertIsDisplayed()
        compose.onNodeWithText("Category: Work").performClick()
        compose.onNodeWithText("New…").performClick()
        compose.onNodeWithText("Category name").performTextInput("Fictional category $tag")
        compose.onNodeWithText("Add").performClick()
        save()
        assertEquals("Fictional category $tag",repo.cards.first{it.id==card.id}.category)
    }
    @Test fun expiryPickerCanClearExistingDate() {
        openCards();openEditor()
        compose.onNodeWithContentDescription("Choose expiry date").performScrollTo().performClick()
        compose.onNodeWithText("Choose date").assertIsDisplayed()
        compose.onNodeWithText("Clear").performClick()
        save()
        assertEquals("",repo.cards.first{it.id==card.id}.expiry)
    }
}
