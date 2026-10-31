package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class VaultDocumentActionsTest {
    @Test fun screenStateIsScopedToDocumentAndOpeningSession(){
        assertNotEquals(VaultDocumentActions.screenKey("detail","a"),VaultDocumentActions.screenKey("detail","b"))
        assertNotEquals(VaultDocumentActions.screenKey("edit","a","first"),VaultDocumentActions.screenKey("edit","a","second"))
        assertNotEquals(VaultDocumentActions.screenKey("viewer","a","first","file"),VaultDocumentActions.screenKey("viewer","a","second","file"))
        assertNotEquals(VaultDocumentActions.screenKey("person","",personId="a"),VaultDocumentActions.screenKey("person","",personId="b"))
    }
    @Test fun movedHistoryCannotPointBackToItself(){
        val a=VaultCard(id="a",previousId="b");val b=VaultCard(id="b");val c=VaultCard(id="c",previousId="b")
        val next=VaultDocumentActions.merge(listOf(a,b,c),emptyList(),"a",setOf("b"),true).cards
        assertEquals("",next.first{it.id=="a"}.previousId)
        assertEquals("a",next.first{it.id=="c"}.previousId)
    }
    @Test fun brokenSourceLinkDoesNotResolveToUnrelatedDestinationField(){
        val a=VaultCard(id="a",fields=listOf(DataField("Reference","TARGET",id="same")));val b=VaultCard(id="b")
        val entry=ProfileEntry(personId="self",section="Other",label="Reference",cardId="b",fieldId="same",value="OLD")
        val change=VaultDocumentActions.merge(listOf(a,b),listOf(entry),"a",setOf("b"),true)
        assertTrue(ProfileLogic.resolve(change.entries.single(),change.cards).sourceMissing)
        assertEquals("OLD",ProfileLogic.resolve(change.entries.single(),change.cards).value)
    }
}
