package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

/** Fictional fixtures only. */
class OcrMergeTest {
    private val page=VaultFile("file-1","scan.jpg","image/jpeg",10,"aa","Saved OCR text","On-device OCR")
    private val other=VaultFile("file-2","back.jpg","image/jpeg",10,"bb","Other page text","On-device OCR")
    private fun card(vararg fields:DataField)=VaultCard(id="card",title="My fictional CDC",kind="Seafarer CDC",
        category="Work",expiry="2031-01-31",fields=fields.toList(),files=listOf(page,other))
    private fun result(text:String,vararg fields:DataField,smart:SmartDocument?=null)=
        ExtractedDocument(text,fields.toList(),"On-device OCR",1,smart)
    private fun extracted(name:String,value:String,confidence:Float=.7f)=
        DataField(name,value,source="extracted",confidence=confidence)

    @Test fun blankResultNeverErasesSavedText() {
        assertNull(OcrMerge.apply(card(),"file-1",result("  \n")))
    }
    @Test fun newTextReplacesOnlyItsOwnAttachment() {
        val merged=OcrMerge.apply(card(),"file-1",result("New fictional text"))!!
        assertEquals("New fictional text",merged.files[0].extractedText)
        assertEquals("Other page text",merged.files[1].extractedText)
    }
    @Test fun removedAttachmentIsReported() {
        try { OcrMerge.apply(card(),"gone",result("text"));fail("Must report removal") }
        catch(_:java.io.IOException) {}
    }
    @Test fun manualAndSelectedValuesAreNeverReplaced() {
        val manual=DataField("Full Name","A. Fictional",source="manual",id="m")
        val selected=DataField("CDC Number","FIC123",source="selected",id="s")
        val merged=OcrMerge.apply(card(manual,selected),"file-1",result("text",
            extracted("Full Name","B FICTIONAL",.99f),extracted("CDC Number","XXX999",.99f)))!!
        assertEquals(listOf(manual,selected),merged.fields)
    }
    @Test fun blankTemplateFieldIsFilledInPlaceKeepingIdAndName() {
        val blank=DataField("Expiry Date","",id="linked-field")
        val merged=OcrMerge.apply(card(blank),"file-1",result("text",extracted("EXPIRY DATE","2031-01-31")))!!
        val field=merged.fields.single()
        assertEquals("linked-field",field.id)
        assertEquals("Expiry Date",field.name)
        assertEquals("2031-01-31",field.value)
    }
    @Test fun lowConfidenceExtractionIsImprovedByMoreConfidentRun() {
        val low=extracted("Full Name","F1CTIONAL",.65f).copy(id="keep")
        val merged=OcrMerge.apply(card(low),"file-1",result("text",extracted("Full Name","FICTIONAL",.99f)))!!
        assertEquals("FICTIONAL",merged.fields.single().value)
        assertEquals("keep",merged.fields.single().id)
        assertEquals(.99f,merged.fields.single().confidence)
    }
    @Test fun lessConfidentRunCannotOverwriteBetterExtraction() {
        val good=extracted("Full Name","FICTIONAL",.99f)
        val merged=OcrMerge.apply(card(good),"file-1",result("text",extracted("Full Name","F1CT",.65f)))!!
        assertEquals(good,merged.fields.single())
    }
    @Test fun userTitleCategoryAndExpiryArePreserved() {
        val smart=SmartDocument("Passport","FICTIONAL PERSON","2040-12-31",emptyList(),.99f)
        val merged=OcrMerge.apply(card(),"file-1",result("text",smart=smart))!!
        assertEquals("My fictional CDC",merged.title)
        assertEquals("Seafarer CDC",merged.kind)
        assertEquals("Work",merged.category)
        assertEquals("2031-01-31",merged.expiry)
    }
    @Test fun editedValueBecomesManualAndLeavesReview() {
        val low=extracted("Full Name","F1CTIONAL",.65f)
        val edited=VaultFieldUpdates.edited(low,"Full Name","FICTIONAL")
        assertEquals("manual",edited.source)
        assertEquals(0f,edited.confidence)
        assertEquals(low.id,edited.id)
        // A later confident OCR run must keep the correction.
        val merged=OcrMerge.mergeFields(listOf(edited),listOf(extracted("Full Name","OTHER",.99f)))
        assertEquals("FICTIONAL",merged.single().value)
    }
    @Test fun renamingWithoutValueChangeKeepsOrigin() {
        val low=extracted("Name","FICTIONAL",.65f)
        val renamed=VaultFieldUpdates.edited(low,"Full Name","FICTIONAL")
        assertEquals("extracted",renamed.source)
        assertEquals(.65f,renamed.confidence)
    }
}
