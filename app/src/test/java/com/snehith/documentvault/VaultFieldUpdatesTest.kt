package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class VaultFieldUpdatesTest {
    private fun f(id:String,value:String)=DataField("Name",value,id=id)
    @Test fun ocrAddedFieldsSurviveConcurrentUiEditor(){
        val original=listOf(f("one","old"))
        val edited=listOf(f("one","new"))
        val latest=listOf(f("one","old"),f("ocr","OCR value"))
        val merged=VaultFieldUpdates.merge(original,edited,latest)
        assertEquals(listOf("new","OCR value"),merged.map{it.value})
    }
    @Test fun deletedFieldDoesNotDeleteUnrelatedOcr(){
        val edited=emptyList<DataField>()
        val latest=listOf(f("one","old"),f("ocr","OCR value"))
        assertEquals(listOf("ocr"),VaultFieldUpdates.merge(listOf(f("one","old")),edited,latest).map{it.id})
    }
    @Test fun partialOcrFailureHasCorrectProgressAndFilename(){
        val summary=OcrBatchSummary.message(listOf(
            OcrBatchOutcome("front.jpg",true),
            OcrBatchOutcome("middle.jpg",false,"unsupported"),
            OcrBatchOutcome("back.jpg",true)))
        assertEquals("2 of 3 read; failed: middle.jpg",summary)
    }
}
