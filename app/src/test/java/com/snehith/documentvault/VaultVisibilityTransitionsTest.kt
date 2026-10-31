package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

/** Every metadata transition must leave a saved document reachable from the Cards
 * list with the filters the app ends up with. Fictional fixtures only. */
class VaultVisibilityTransitionsTest {
    private val asha=VaultProfile("asha","Asha Fictional")
    private val page=VaultFile("f1","front.jpg","image/jpeg",10,"h1","FICTIONAL TEXT LINE")
    private val doc=VaultCard(id="doc",title="Fictional licence",category="Personal",profile="self",
        expiry="2030-05-01",created=1L,files=listOf(page),
        fields=listOf(DataField("Licence Number","FIC-001",id="num")))
    private val neighbour=VaultCard(id="other",title="Other",category="Work",profile="self",created=2L)

    /** Same pipeline as VaultNavCards: person filter, then category, then browse. */
    private fun shown(cards:List<VaultCard>,person:String,category:String,
        profiles:List<VaultProfile> = listOf(asha)):List<String> {
        val personCards=VaultPersonFilter.filter(cards,VaultPersonFilter.effective(person,profiles))
        return DocumentBrowseLogic.browse(personCards,
            DocumentBrowseLogic.effectiveCategory(personCards,category),"Recent").map{it.id}
    }
    /** An editor save widens the person filter and resets the category to All. */
    private fun saveEdit(edited:VaultCard,person:String,cards:List<VaultCard> = listOf(neighbour)):List<String> {
        val saved=VaultEditedCard.merge(doc,edited,doc)
        return shown(cards+saved,VaultSavedDocumentVisibility.profileAfterSave(person,saved.profile),"All")
    }

    @Test fun ownerChangeUnderPersonFilterStaysVisible() =
        assertTrue("doc" in saveEdit(doc.copy(profile="asha"),"self"))
    @Test fun ownerChangeUnderCategoryFilterStaysVisible() =
        assertTrue("doc" in saveEdit(doc.copy(profile="asha"),"all"))
    @Test fun categoryChangeStaysVisible() =
        assertTrue("doc" in saveEdit(doc.copy(category="Identity"),"self"))
    @Test fun ownerAndCategoryChangeTogetherStayVisible() =
        assertTrue("doc" in saveEdit(doc.copy(profile="asha",category="Identity"),"self"))
    @Test fun blankCategoryIsListedAsOther() {
        assertTrue("doc" in saveEdit(doc.copy(category=""),"self"))
        assertEquals(listOf("doc"),shown(listOf(doc.copy(category=""),neighbour),"all","Other"))
    }
    @Test fun renameKeepsAttachmentsAndIsSearchableByNewTitleOnly() {
        val saved=VaultEditedCard.merge(doc,doc.copy(title="Renamed fictional"),doc)
        assertEquals(doc.files,saved.files)
        assertTrue("doc" in shown(listOf(saved),"self","Personal"))
        assertEquals(listOf("doc"),SearchLogic.search(listOf(saved),"Renamed","All").map{it.card.id})
        assertTrue(SearchLogic.search(listOf(saved),"licence","Documents").isEmpty())
    }
    @Test fun expiryChangeOrRemovalNeverHidesDocument() {
        listOf("2020-01-01","","not a date").forEach{expiry->
            assertTrue(expiry,"doc" in saveEdit(doc.copy(expiry=expiry),"self"))
        }
    }
    @Test fun fieldEditIsSearchableAfterSave() {
        val saved=VaultEditedCard.merge(doc,doc.copy(fields=listOf(doc.fields[0].copy(value="FIC-777"))),doc)
        assertEquals(listOf("doc"),SearchLogic.search(listOf(saved),"FIC-777","Information").map{it.card.id})
        assertTrue(SearchLogic.search(listOf(saved),"FIC-001","Information").isEmpty())
    }
    @Test fun attachmentRemovalKeepsDocumentListed() =
        assertTrue("doc" in shown(listOf(doc.copy(files=emptyList())),"self","Personal"))
    @Test fun ocrRecategorizationUnderOldCategoryFilterStaysVisible() {
        val passport=SmartDocument("Passport","FICTIONAL PERSON",null,emptyList(),.99f)
        val updated=OcrMerge.apply(doc,"f1",ExtractedDocument("P<FIC",emptyList(),"On-device OCR",1,passport))!!
        assertEquals("Identity",updated.category)
        // The remembered "Personal" chip has no documents left: fall back to All.
        assertTrue("doc" in shown(listOf(updated,neighbour),"self","Personal"))
    }
    @Test fun deletingLastDocumentOfCategoryDoesNotBlankList() =
        assertEquals(listOf("other"),shown(listOf(neighbour),"all","Personal"))
    @Test fun nonEmptyCategoryFilterStillFilters() =
        assertEquals(listOf("other"),shown(listOf(doc,neighbour),"all","Work"))
    @Test fun deletedPersonFilterFallsBackToEveryone() {
        val moved=doc.copy(profile="self")   // VaultRepository.removePerson hands documents to self
        assertEquals("all",VaultPersonFilter.effective("asha",emptyList()))
        assertEquals(listOf("other","doc"),shown(listOf(moved,neighbour),"asha","All",profiles=emptyList()))
    }
    @Test fun restoredDocumentsOwnedByMissingPersonRemainReachable() {
        val orphan=doc.copy(profile="removed-person")
        assertTrue("doc" in shown(listOf(orphan),"removed-person","All",profiles=emptyList()))
    }
    @Test fun selfAndExistingPeopleAreKept() {
        assertEquals("self",VaultPersonFilter.effective("self",emptyList()))
        assertEquals("asha",VaultPersonFilter.effective("asha",listOf(asha)))
        assertEquals("all",VaultPersonFilter.effective("all",emptyList()))
    }
    @Test fun renewalHidesOldCardOnlyFromExpiryViews() {
        val renewed=doc.copy(id="new",previousId="doc",expiry="2035-05-01",created=3L)
        assertEquals(listOf("new"),VaultExpiryRules.active(listOf(doc,renewed)).map{it.id})
        assertEquals(setOf("doc","new"),shown(listOf(doc,renewed),"self","All").toSet())
    }
}
