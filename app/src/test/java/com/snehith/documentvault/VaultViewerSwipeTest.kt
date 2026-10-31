package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class VaultViewerSwipeTest {
    private fun file(id:String,mime:String)=VaultFile(id,"$id.x",mime,1,"h")
    private val front=file("front","image/jpeg");private val pdf=file("pdf","application/pdf");private val back=file("back","image/png")
    // Pages as rememberVaultDocumentPages expands them: one image, a 3-page PDF, one image.
    private val pages=listOf(VaultDocumentPage(front,0,1))+
        (0..2).map{VaultDocumentPage(pdf,it,3)}+VaultDocumentPage(back,0,1)

    @Test fun forwardOpensFirstPageOfNextAttachment() {
        assertEquals("pdf" to 0,VaultDocumentPages.neighbour(pages,"front",1))
        assertEquals("back" to 0,VaultDocumentPages.neighbour(pages,"pdf",1))
    }
    @Test fun backwardOpensLastPageOfPreviousAttachment() {
        assertEquals("pdf" to 2,VaultDocumentPages.neighbour(pages,"back",-1))
        assertEquals("front" to 0,VaultDocumentPages.neighbour(pages,"pdf",-1))
    }
    @Test fun endsAndUnknownFilesStayPut() {
        assertNull(VaultDocumentPages.neighbour(pages,"front",-1))
        assertNull(VaultDocumentPages.neighbour(pages,"back",1))
        assertNull(VaultDocumentPages.neighbour(pages,"missing",1))
    }
}
