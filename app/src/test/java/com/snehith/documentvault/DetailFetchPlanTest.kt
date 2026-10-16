package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class DetailFetchPlanTest {
    private fun file(id:String,name:String,mime:String)=VaultFile(id,name,mime,1,"sha")

    @Test fun acceptsImagesPdfsOfficeAndTextFiles(){
        val candidates=listOf(
            file("a","Scan.JPG","image/jpeg"),
            file("b","cards.pdf","application/pdf"),
            file("c","letter.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            file("d","notes.txt","text/plain")
        )
        assertEquals(listOf("a","b","c","d"),
            DetailFetchPlan.filesToProcess(candidates).map{it.id})
    }

    @Test fun unsupportedOrUnattachedFormatsAreNotQueued(){
        val candidates=listOf(file("a","video.mp4","video/mp4"),
            file("b","archive.zip","application/zip"),
            file("c","photo.png","image/png"))
        assertEquals(listOf("c"),DetailFetchPlan.filesToProcess(candidates).map{it.id})
    }

    @Test fun repeatedAttachmentReferenceGetsOnlyOneOcrJob(){
        val image=file("same","page.jpg","image/jpeg")
        assertEquals(1,DetailFetchPlan.filesToProcess(listOf(image,image)).size)
    }

    @Test fun emptyCardHasNothingToFetch(){
        assertTrue(DetailFetchPlan.filesToProcess(emptyList()).isEmpty())
    }
}
