package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class VaultUxTest {
    private val now=LocalDateTime.of(2026,10,31,14,5)
    @Test fun cameraAndNumericFilenamesGetReadableTitles(){
        for(name in listOf("IMG_20261031.jpg","Screenshot_20261031.png","123456.pdf","capture-1.png"))
            assertEquals("Document · 31 Oct 2026 14:05",VaultUx.importTitle(name,"Custom",emptyList(),now))
    }
    @Test fun descriptiveTitlesAndRecognizedTypeAreKept(){
        assertEquals("School certificate",VaultUx.importTitle("School certificate.pdf","Custom",emptyList(),now))
        assertEquals("Passport",VaultUx.importTitle("IMG_1.jpg","Passport",emptyList(),now))
    }
    @Test fun duplicateTitlesUseFirstAvailableNumberIgnoringCase(){
        assertEquals("Report (3)",VaultUx.importTitle("Report.pdf","Custom",listOf("report","Report (2)"),now))
        assertEquals("Report (2)",VaultUx.importTitle("Report.pdf","Custom",listOf("Report","Report (3)"),now))
    }
    @Test fun avatarsUseTwoLettersAndStableColor(){
        assertEquals("AF",VaultUx.initials("Asha Fictional"))
        assertEquals("ME",VaultUx.initials("Me"))
        assertEquals("?",VaultUx.initials(" "))
        assertEquals(VaultUx.avatarColor("person-1"),VaultUx.avatarColor("person-1"))
        assertTrue(VaultUx.avatarColor("negative-hash-identity") in 0..5)
    }
    @Test fun pickedValueFillsEquivalentNameWithoutBreakingProfileLink(){
        val old=DataField("CDC Number","",id="linked-field")
        val result=VaultFieldUpdates.pick(listOf(old),"cdc no.","FICTIONAL-001",2).single()
        assertEquals("linked-field",result.id);assertEquals("CDC Number",result.name)
        assertEquals("FICTIONAL-001",result.value);assertEquals("selected",result.source);assertEquals(2,result.page)
    }
    @Test fun pickedNewDetailKeepsExistingFields(){
        val old=DataField("Name","Fictional",id="name")
        val result=VaultFieldUpdates.pick(listOf(old),"Reference","FIC-001",0)
        assertEquals(old,result.first());assertEquals(2,result.size)
    }
    @Test fun suggestionsPutEmptyFieldsFirstAndRemoveEquivalentNames(){
        val card=VaultCard(kind="Passport",fields=listOf(DataField("Full Name","Fictional"),
            DataField("Passport Number",""),DataField("Passport No.","")))
        val suggestions=VaultFieldUpdates.nameSuggestions(card)
        assertEquals("Passport Number",suggestions.first())
        assertEquals(1,suggestions.count{it.startsWith("Passport")})
        assertTrue("Date of Birth" in suggestions)
    }
    @Test fun likelyValuesIncludeDatesIdentifiersAndLongNumbers(){
        for(line in listOf("31/10/2026","2030-12-31","AB12345","1234 5678 9012"))
            assertTrue(line,VaultFieldUpdates.likelyValue(line))
        assertFalse(VaultFieldUpdates.likelyValue("Fictional Republic"))
    }
    @Test fun filledCountIgnoresUnchangedAndBlankFields(){
        val before=listOf(DataField("Name","Fictional",id="one"),DataField("Reference","",id="two"))
        val after=listOf(before[0].copy(source="selected"),before[1].copy(value="FIC-001"),DataField("Blank","",id="three"))
        assertEquals(1,OcrMerge.filledCount(before,after))
    }
    @Test fun summaryDistinguishesReadingTextFromFillingDetails(){
        assertEquals("Text was read, but no details were recognised. Use “Select from page” to pick them.",
            OcrBatchSummary.message(listOf(OcrBatchOutcome("fictional.png",true))))
        assertEquals("Reading complete: 2 details filled. Please check them.",
            OcrBatchSummary.message(listOf(OcrBatchOutcome("fictional.png",true,filled=2))))
    }
}
