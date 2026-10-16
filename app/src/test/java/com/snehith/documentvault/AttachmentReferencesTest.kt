package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class AttachmentReferencesTest {
    private fun f(id:String,original:String="")=VaultFile(id,"scan.jpg","image/jpeg",16,id,
        originalId=original)
    @Test fun deletingOnePhotoLeavesOtherPhotosIntact(){
        val first=f("first","raw")
        val second=f("second")
        val after=listOf(VaultCard(files=listOf(second)))
        assertEquals(setOf("first","raw"),AttachmentReferences.unusedAfter(after,first))
        assertEquals(setOf("second"),AttachmentReferences.referencedIds(after))
    }
    @Test fun sharingRawReferencePreventsPrematureDeletion(){
        val old=f("render1","raw")
        val shared=f("render2","raw")
        val remaining=listOf(VaultCard(files=listOf(shared)))
        assertEquals(setOf("render1"),AttachmentReferences.unusedAfter(remaining,old))
    }
    @Test fun replacingImageRetainsOriginalAndAvoidsExtraVisiblePage(){
        val before=listOf(f("old"))
        val updated=f("new","old")
        val after=before.map{if(it.id=="old")updated else it}
        assertEquals(1,after.size)
        assertEquals(setOf("old","new"),
            AttachmentReferences.referencedIds(listOf(VaultCard(files=after))))
    }
}
