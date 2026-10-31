package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class SearchLogicTest {
    private val passport=VaultCard(id="p",title="Passport",category="Identity",fields=listOf(
        DataField("Passport number","X1234567",id="n"),DataField("City","Kochi",id="c")))
    private val licence=VaultCard(id="l",title="Driving Licence",category="Transport",
        fields=listOf(DataField("Name","Asha Menon",id="m")),
        files=listOf(VaultFile("f","lic.jpg","image/jpeg",1,"s",
            extractedText="Issued at Kochi\nValid for transport vehicles")))

    @Test fun blankQueryListsEveryCardWithoutReasons() {
        val r=SearchLogic.search(listOf(passport,licence),"  ","All")
        assertEquals(2,r.size);assertTrue(r.all{it.hits.isEmpty()})
    }
    @Test fun titleOutranksFieldOutranksScannedText() {
        val r=SearchLogic.search(listOf(passport,licence),"kochi","All")
        assertEquals(listOf("p","l"),r.map{it.card.id}) // field(2) before text(1)
        assertEquals(SearchLogic.TEXT,r[1].hits.last().tag)
        val t=SearchLogic.search(listOf(licence,passport),"passport","All")
        assertEquals("p",t[0].card.id)
        assertEquals(SearchLogic.TITLE,t[0].hits[0].tag)
    }
    @Test fun sensitiveValuesAreMaskedInReasons() {
        val r=SearchLogic.search(listOf(passport),"X1234","All")
        val shown=r[0].hits.first{it.tag==SearchLogic.FIELD}.text
        assertFalse(shown.contains("X1234567"));assertTrue(shown.contains("••••"))
    }
    @Test fun modeLimitsWhereWeLook() {
        assertTrue(SearchLogic.search(listOf(licence),"transport","Information").isEmpty())
        assertEquals(1,SearchLogic.search(listOf(licence),"transport","Scanned Text").size)
        assertEquals(1,SearchLogic.search(listOf(licence),"transport","Documents").size)
    }
    @Test fun snippetsAreCompactAndMarkedWithEllipsis() {
        val r=SearchLogic.search(listOf(licence),"vehicles","Scanned Text")
        val s=r[0].hits[0].text
        assertFalse(s.contains("\n"));assertTrue(s.contains("vehicles"))
    }
    @Test fun matchRangesAreCaseInsensitiveAndNonOverlapping() {
        assertEquals(listOf(0..1,3..4),SearchLogic.matchRanges("abxAB","ab"))
        assertTrue(SearchLogic.matchRanges("abc"," ").isEmpty())
    }
    @Test fun suggestionsComeFromTheUsersOwnCards() {
        assertEquals(listOf("Passport","Driving Licence","Identity","Transport"),
            SearchLogic.suggestions(listOf(passport,licence)))
    }
}
