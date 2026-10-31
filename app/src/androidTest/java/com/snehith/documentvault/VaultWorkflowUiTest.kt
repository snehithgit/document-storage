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
import java.io.File
import androidx.core.content.FileProvider

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
    private val extraIds=mutableListOf<String>()
    private val optionKeys=listOf("keepScanOriginal","hideDocumentPreviews","hideHomePreviews")
    private val priorOptions=optionKeys.associateWith{key->context.getSharedPreferences("vault_options",0).let{if(it.contains(key))it.getBoolean(key,true)else null}}
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
            repo.cards.toList().filter{it.id==card.id||it.id in extraIds||it.fields.any{field->field.id=="ui-field-$tag"}}.forEach(repo::deleteCard)
            val edit=context.getSharedPreferences("vault_options",0).edit()
            priorOptions.forEach{(key,value)->if(value==null)edit.remove(key)else edit.putBoolean(key,value)}
            edit.apply()
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
        compose.onAllNodesWithText(title).onFirst().assertExists()
        openEditor()
        compose.onNodeWithText("Profile: "+person.name).performClick()
        compose.onNodeWithText("Me").performClick()
        save()
        assertEquals("self",repo.cards.first{it.id==card.id}.profile)
        pressBack()
        compose.onAllNodesWithText(title).assertCountEquals(0)
        compose.onNodeWithText("All documents").performClick()
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
    @Test fun editingAnotherDocumentNeverRestoresPreviousEditorValues() {
        val other=card.copy(id="ui-other-$tag",title="UI Other Fictional $tag",
            fields=listOf(DataField("Reference","OTHER-FIC-$tag",id="ui-other-field-$tag")))
        compose.runOnIdle{repo.saveCard(other);extraIds.add(other.id)}
        openCards();openEditor()
        compose.onNodeWithText("Document title").assertTextContains(title)
        compose.onNodeWithText("Cancel").performClick()
        pressBack()
        compose.onNodeWithText(other.title).performClick()
        compose.onNodeWithContentDescription("More document options").performClick()
        compose.onNodeWithText("Edit details").performClick()
        compose.onNodeWithText("Document title").assertTextContains(other.title)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000){compose.onAllNodesWithText("Document title").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Document title").assertTextContains(other.title)
        compose.onNodeWithText("Document title").performTextReplacement("UI Other Edited $tag")
        save()
        assertEquals(title,repo.cards.first{it.id==card.id}.title)
        assertEquals("UI Other Edited $tag",repo.cards.first{it.id==other.id}.title)
        assertEquals("OTHER-FIC-$tag",repo.cards.first{it.id==other.id}.fields.single().value)
    }
    @Test fun originalSwitchUpdatesImmediatelyAndSurvivesRecreation() {
        compose.runOnIdle{context.getSharedPreferences("vault_options",0).edit().putBoolean("keepScanOriginal",true).apply()}
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Keep untouched scan original").performScrollTo().assertIsOn().performClick().assertIsOff()
        assertFalse(context.getSharedPreferences("vault_options",0).getBoolean("keepScanOriginal",true))
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000){compose.onAllNodesWithText("Preferences").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Keep untouched scan original").performScrollTo().assertIsOff()
        compose.onNodeWithText("Keep untouched scan original").performClick()
        compose.onNodeWithContentDescription("Keep untouched scan original").assertIsOn()
    }
    @Test fun previewSettingControlsCardsWithoutHidingOpenedDocument() {
        compose.runOnIdle{
            repo.updateCard(card.id){it.copy(category="Identity")}
            context.getSharedPreferences("vault_options",0).edit().putBoolean("hideDocumentPreviews",true).apply()
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Hide identity previews").performScrollTo().assertIsOn().performClick().assertIsOff()
        openCards()
        compose.onAllNodesWithContentDescription("Hidden identity preview").assertCountEquals(0)
        compose.onNodeWithText("All documents").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Hide identity previews").performScrollTo().performClick().assertIsOn()
        openCards()
        compose.onAllNodesWithContentDescription("Hidden identity preview").assertCountEquals(1)
        compose.onNodeWithText(title).performClick()
        compose.onAllNodesWithContentDescription("Hidden identity preview").assertCountEquals(0)
    }
    @Test fun mergeCopyKeepsBothOriginalDocumentIdentities() {
        val other=card.copy(id="ui-other-$tag",title="UI Merge Source $tag",
            fields=listOf(DataField("Reference","MERGE-FIC-$tag",id="ui-merge-field-$tag")))
        compose.runOnIdle{repo.saveCard(other);extraIds.add(other.id)}
        openCards();compose.onNodeWithText(title).performClick()
        compose.onNodeWithContentDescription("More document options").performClick()
        compose.onNodeWithText("Merge documents").performClick()
        compose.onNodeWithText(other.title).performClick()
        compose.onNodeWithText("Copy and merge selected documents").performClick()
        compose.onNodeWithText("Copy and merge").performClick()
        compose.waitUntil(10_000){repo.cards.first{it.id==card.id}.fields.size==2}
        assertEquals(other,repo.cards.first{it.id==other.id})
        assertTrue(repo.cards.first{it.id==card.id}.fields.any{it.value=="MERGE-FIC-$tag"})
    }
    @Test fun splitMoveUsesSameBytesAndDoesNotLeaveAnEmptySourceDocument() {
        fun imported(name:String,value:String):VaultFile {
            val staged=File(context.cacheDir,"shared/ui-$tag-$name.txt").apply{parentFile!!.mkdirs();writeText(value)}
            return repo.importFile(FileProvider.getUriForFile(context,context.packageName+".provider",staged))
        }
        val one=imported("one","Fictional one");val two=imported("two","Fictional two")
        compose.runOnIdle{repo.updateCard(card.id){it.copy(files=listOf(one,two))}}
        openCards();compose.onNodeWithText(title).performClick()
        compose.onNodeWithContentDescription("More document options").performClick()
        compose.onNodeWithText("Split document").performClick()
        compose.onNodeWithText("Move").performClick()
        compose.onNodeWithText("Select up to 100").performClick()
        compose.onNodeWithText("Move selected items into documents").performClick()
        compose.onNodeWithText("Move into documents").performClick()
        compose.waitUntil(10_000){repo.cards.none{it.id==card.id}}
        val children=repo.cards.filter{it.fields.any{f->f.id=="ui-field-$tag"}}
        assertEquals(2,children.size);assertEquals(setOf(one.id,two.id),children.flatMap{it.files}.map{it.id}.toSet())
    }
    @Test fun recoveryRemovalCanBeCancelledAndRefreshesAfterConfirmation() {
        val staged=File(context.cacheDir,"shared/ui-recovery-$tag.txt").apply{parentFile!!.mkdirs();writeText("Fictional recovery fixture $tag")}
        val loose=repo.importFile(FileProvider.getUriForFile(context,context.packageName+".provider",staged))
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Recover imports"))
        compose.onNodeWithText("Recover imports").performClick()
        compose.waitUntil(10_000){compose.onAllNodesWithText("Clear all recovery files").fetchSemanticsNodes().isNotEmpty()}
        fun remove(){
            compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("recovery-${loose.id}"))
            compose.onAllNodesWithText("Remove permanently").filterToOne(hasAnyAncestor(hasTestTag("recovery-${loose.id}"))).performClick()
        }
        remove();compose.onNodeWithText("Cancel").performClick();assertTrue(repo.hasDocument(loose))
        remove();compose.onAllNodesWithText("Remove permanently").filterToOne(hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10_000){!repo.hasDocument(loose)}
        compose.waitUntil(10_000){compose.onAllNodesWithTag("recovery-${loose.id}").fetchSemanticsNodes().isEmpty()}
    }
    @Test fun profileDocumentsAreDedicatedAndNeverIncludeOtherOwners() {
        val other=card.copy(id="ui-other-$tag",title="UI Other Owner $tag",profile="self")
        compose.runOnIdle{repo.saveCard(other);extraIds.add(other.id)}
        compose.onNodeWithContentDescription("People").performClick()
        compose.onNodeWithText(person.name).performClick()
        compose.onNodeWithText("Search this profile").assertExists()
        compose.onNodeWithText(title).assertExists()
        compose.onAllNodesWithText(other.title).assertCountEquals(0)
        compose.onNodeWithText("Search this profile").performTextInput("no-match-$tag")
        compose.onAllNodesWithText(title).assertCountEquals(0)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000){compose.onAllNodesWithText("Search this profile").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Search this profile").assertTextContains("no-match-$tag")
        compose.onNodeWithText("Search this profile").performTextClearance()
        compose.onNodeWithText("Profile details").performClick()
        compose.onNodeWithContentDescription("Profile options").assertExists()
        pressBack()
        compose.onNodeWithText(title).assertExists()
        compose.onNodeWithText("All documents").performClick()
        compose.onNodeWithText(other.title).assertExists()
    }

}
