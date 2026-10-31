package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class AttachmentSelectionTest {
    private fun f(id:String)=VaultFile(id,"page.jpg","image/jpeg",1,id)
    @Test fun enhancedThirdPageStaysSelectedDespiteNewBlobId() {
        val after=listOf(f("a"),f("b"),f("new-c"))
        assertEquals("new-c",AttachmentSelection.select(after,"old-c",2)?.id)
    }
    @Test fun deletingSelectedLastPageFallsBackToLastExistingPage() {
        val after=listOf(f("a"),f("b"))
        assertEquals("b",AttachmentSelection.select(after,"deleted-c",2)?.id)
    }
    @Test fun preservedStableIdOverridesStaleSlot(){
        val files=listOf(f("a"),f("b"))
        assertEquals("b",AttachmentSelection.select(files,"b",0)?.id)
    }
    @Test fun removingAllPagesDoesNotCrash(){
        assertNull(AttachmentSelection.select(emptyList(),"missing",12))
    }
}
