package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class AuditFixesTest {
    private fun f(id:String,v:String="x")=DataField(name=id,value=v,id=id)

    @Test fun reorderIsKeptAndConcurrentOcrFieldStaysLast() {
        val original=listOf(f("a"),f("b"),f("c"))
        val edited=listOf(f("c"),f("a"),f("b"))
        val latest=original+f("ocr")
        assertEquals(listOf("c","a","b","ocr"),
            VaultFieldUpdates.merge(original,edited,latest).map{it.id})
    }
    @Test fun unchangedOrderStillMergesConcurrentEdits() {
        val original=listOf(f("a"),f("b"))
        val edited=listOf(f("a","new"),f("b"))
        val latest=listOf(f("a"),f("b"),f("ocr"))
        val merged=VaultFieldUpdates.merge(original,edited,latest)
        assertEquals(listOf("a","b","ocr"),merged.map{it.id})
        assertEquals("new",merged[0].value)
    }
    @Test fun deletingCardScrubsOnlySensitiveLinkedValues() {
        val list=listOf(
            ProfileEntry(personId="p",section="s",label="Passport number",cardId="c1",fieldId="f",value="X123",sensitive=true),
            ProfileEntry(personId="p",section="s",label="Name",cardId="c1",fieldId="g",value="Asha"),
            ProfileEntry(personId="p",section="s",label="Passport number",cardId="c2",fieldId="f",value="Y9",sensitive=true))
        val out=ProfileLogic.scrubSensitive(list,"c1")
        assertEquals("",out[0].value);assertEquals("Asha",out[1].value);assertEquals("Y9",out[2].value)
    }
    @Test fun alternativesIncludeLinkedCardFiledUnderAnotherProfile() {
        val linked=VaultCard(id="c1",profile="other",fields=listOf(
            DataField("Phone","111",id="f1"),DataField("Mobile","222",id="f2")))
        val e=ProfileEntry(personId="p",section="s",label="Phone",cardId="c1",fieldId="f1",value="111")
        assertEquals(1,ProfileLogic.alternatives(e,listOf(linked)).size)
    }
    @Test fun backupDropsAttachmentsWithoutBytes() {
        val card=VaultCard(id="c",files=listOf(
            VaultFile("ok","a.jpg","image/jpeg",1,"s1"),VaultFile("gone","b.jpg","image/jpeg",1,"s2")))
        val out=BackupIntegrity.dropMissingPrimaries(listOf(card)){it=="ok"}
        assertEquals(listOf("ok"),out[0].files.map{it.id})
        assertEquals(listOf("b.jpg"),BackupIntegrity.missingPrimaries(listOf(card)){it=="ok"})
    }
}
