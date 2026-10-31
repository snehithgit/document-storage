package com.snehith.documentvault

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException

abstract class VaultActionsContract {
    protected abstract fun appContext():Context
    private lateinit var context:Context
    private lateinit var repo:VaultRepository
    @Before fun setupActions(){
        val app=appContext();val root=File(app.cacheDir,"actions-${UUID.randomUUID()}").apply{mkdirs()}
        context=object:android.content.ContextWrapper(app){
            override fun getFilesDir()=File(root,"files").apply{mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply{mkdirs()}
            override fun getSharedPreferences(name:String,mode:Int)=app.getSharedPreferences("fixture-${root.name}-$name",mode)
        }
        repo=VaultRepository(context)
    }
    private fun file(name:String="fictional.txt",text:String="Fictional bytes",mime:String="text/plain"):VaultFile {
        val id=UUID.randomUUID().toString();val data=File(context.filesDir,"blobs/$id.dat").apply{writeText(text)}
        return VaultFile(id,name,mime,data.length(),VaultBackupChecks.sha256(data.inputStream()),"saved fictional OCR")
    }
    private fun card(title:String="Fictional",files:List<VaultFile> = listOf(file()))=VaultCard(title=title,
        fields=listOf(DataField("Reference","FIC-001",id=UUID.randomUUID().toString())),files=files).also{repo.saveCard(it)}
    @Test fun replacementKeepsDocumentAndAttachmentPositionAndSavedText(){
        val first=file("fictional.pdf",mime="application/pdf");val second=file("second.txt")
        val old=card(files=listOf(first,second));val edited=file("enhanced.pdf","new bytes","application/pdf")
        repo.replaceAttachment(old.id,first.id,edited,false)
        val saved=VaultRepository(context).cards.single()
        assertEquals(old.id,saved.id);assertEquals(2,saved.files.size);assertEquals(listOf(edited.id,second.id),saved.files.map{it.id})
        assertEquals(first.name,saved.files[0].name);assertEquals(first.extractedText,saved.files[0].extractedText)
        assertEquals("",saved.files[0].originalId);assertEquals(old.fields,saved.fields)
    }
    @Test fun originalsRespectSettingAndMissingOriginalFallsBackToCurrent(){
        val source=file("photo.png",mime="image/png");val old=card(files=listOf(source))
        val edited=file("edited.png","new image bytes","image/png")
        repo.replaceImageFile(old.id,source.id,edited,"corners",90,true)
        val saved=repo.cards.single().files.single();assertEquals(source.id,saved.originalId)
        File(context.filesDir,"blobs/${source.id}.dat").delete()
        assertFalse(repo.hasOriginal(saved));assertEquals(repo.documentFile(saved),repo.originalDocumentFile(saved))
        val again=file("again.png","third bytes","image/png")
        repo.replaceImageFile(old.id,saved.id,again,"other",180,false)
        val final=repo.cards.single().files.single()
        assertEquals("",final.originalId);assertEquals("",final.cropCorners);assertEquals(0,final.cropRotation)
    }
    @Test fun failedReplacementDoesNotChangeDocument(){
        val original=card();val next=file("next.txt","next bytes")
        File(context.filesDir,"vault.json.tmp").mkdir()
        try{repo.replaceAttachment(original.id,original.files.single().id,next);fail("Must fail")}
        catch(_:IOException){}
        assertEquals(original,repo.cards.single());assertTrue(repo.hasDocument(original.files.single()))
    }
    @Test fun recoveryRemovalCannotDeleteLiveOrRetainedOriginal(){
        val original=file();val page=file("page.txt","page bytes")
        card(files=listOf(page.copy(originalId=original.id)))
        for(id in listOf(page.id,original.id))try{repo.clearRecovery(setOf(id));fail("Must protect linked bytes")}
        catch(_:IllegalArgumentException){}
        assertTrue(repo.hasDocument(page));assertTrue(repo.hasDocument(original))
    }
    @Test fun permanentRecoveryRemovalAlsoPreventsBackupResurrection(){
        val deleted=card();repo.deleteCard(deleted)
        assertEquals(1,repo.recoverableFiles().size)
        assertEquals(1,repo.clearRecovery(setOf(deleted.files.single().id)).removed)
        assertTrue(repo.recoverableFiles().isEmpty())
        File(context.filesDir,"vault.json").writeText("corrupt")
        assertTrue(VaultRepository(context).cards.isEmpty())
        assertFalse(repo.hasDocument(deleted.files.single()))
    }
    @Test fun failedRecoveryCheckpointDoesNotDeleteBytes(){
        val deleted=card();repo.deleteCard(deleted)
        File(context.filesDir,"vault.json.bak.tmp").mkdir()
        try{repo.clearRecovery(setOf(deleted.files.single().id));fail("Must fail safely")}
        catch(_:IOException){}
        assertTrue(repo.hasDocument(deleted.files.single()));assertEquals(1,repo.recoverableFiles().size)
    }
    @Test fun staleRecoverySelectionIsRechecked(){
        val loose=file();card(files=listOf(loose))
        try{repo.clearRecovery(setOf(loose.id));fail("Must protect newly attached file")}
        catch(_:IllegalArgumentException){}
        assertTrue(repo.hasDocument(loose))
    }
    @Test fun mergeMoveRetainsFieldsNotesAndRepairsProfileLinks(){
        val target=card("Target");val source=card("Source").copy(notes="Fictional notes",tags="source-tag",expiry="2030-12-31")
        repo.saveCard(source)
        val entry=ProfileEntry(personId="self",section="Other",label="Reference",cardId=source.id,fieldId=source.fields.single().id)
        repo.changeEntries(add=listOf(entry))
        repo.mergeDocuments(target.id,setOf(source.id),true)
        val merged=VaultRepository(context).cards.single()
        assertEquals(target.id,merged.id);assertEquals(2,merged.files.size);assertEquals(2,merged.fields.size)
        assertTrue(merged.notes.contains("Fictional notes"));assertTrue(merged.notes.contains("2030-12-31"))
        assertEquals("source-tag",merged.tags);assertEquals("FIC-001",ProfileLogic.resolve(repo.entries.single(),repo.cards).value)
        assertEquals(target.id,repo.entries.single().cardId)
    }
    @Test fun mergeCopyLeavesSourceAndSharedBytesSafeAfterDelete(){
        val target=card("Target");val source=card("Source")
        repo.mergeDocuments(target.id,setOf(source.id),false)
        assertEquals(source,repo.cards.first{it.id==source.id})
        repo.deleteCard(repo.cards.first{it.id==target.id})
        assertTrue(repo.hasDocument(source.files.single()))
    }
    @Test fun mergeFieldIdentityConflictDoesNotLinkWrongValue(){
        val target=card("Target");val source=card("Source").copy(fields=listOf(target.fields.single().copy(value="SOURCE-002")))
        repo.saveCard(source);repo.changeEntries(add=listOf(ProfileEntry(personId="self",section="Other",label="Reference",
            cardId=source.id,fieldId=source.fields.single().id)))
        repo.mergeDocuments(target.id,setOf(source.id),true)
        assertEquals("SOURCE-002",ProfileLogic.resolve(repo.entries.single(),repo.cards).value)
        assertNotEquals(target.fields.single().id,repo.entries.single().fieldId)
    }
    @Test fun staleMergeAndConflictingSharedMetadataAreRejected(){
        val target=card("Target");val source=card("Source",listOf(target.files.single().copy(extractedText="different text")))
        val before=repo.cards.toList()
        try{repo.mergeDocuments(target.id,setOf(source.id),true);fail("Must not lose conflicting text")}
        catch(_:IllegalArgumentException){}
        assertEquals(before,repo.cards.toList())
        try{repo.mergeDocuments(target.id,setOf("missing"),true);fail("Must reject stale selection")}
        catch(_:IOException){}
        assertEquals(before,repo.cards.toList())
    }
    @Test fun mergeRollbackKeepsAllSourceRecordsAndLinks(){
        val a=card("A");val b=card("B");val before=repo.cards.toList()
        File(context.filesDir,"vault.json.tmp").mkdir()
        try{repo.mergeDocuments(a.id,setOf(b.id),true);fail("Must fail")}
        catch(_:IOException){}
        assertEquals(before,repo.cards.toList());assertEquals(before,VaultRepository(context).cards.toList())
    }
    @Test fun splitCopyRetainsOriginalAndMoveAllRebindsLinks(){
        val source=card(files=listOf(file("front.txt"),file("back.txt","back")))
        val selection=source.files.associate{it.id to setOf(0)}
        val copied=repo.splitDocument(source.id,selection,false,{error("Not PDF")},{_,_->error("Not PDF")})
        assertEquals(2,copied.size);assertEquals(source,repo.cards.first{it.id==source.id})
        repo.changeEntries(add=listOf(ProfileEntry(personId="self",section="Other",label="Reference",cardId=source.id,fieldId=source.fields.single().id)))
        val moved=repo.splitDocument(source.id,selection,true,{error("Not PDF")},{_,_->error("Not PDF")})
        assertFalse(repo.cards.any{it.id==source.id});assertEquals(moved.first(),repo.entries.single().cardId)
        assertEquals("FIC-001",ProfileLogic.resolve(repo.entries.single(),repo.cards).value)
        assertTrue(source.files.all{repo.hasDocument(it)})
    }
    @Test fun splitMoveOnlySelectedAttachmentKeepsRemainingSource(){
        val source=card(files=listOf(file("first.txt"),file("second.txt","second")))
        repo.splitDocument(source.id,mapOf(source.files.first().id to setOf(0)),true,{0},{_,_->error("Not PDF")})
        assertEquals(listOf(source.files.last()),repo.cards.first{it.id==source.id}.files)
    }
    @Test fun movingPdfPageRemapsOcrAndKeepsOtherPages(){
        val pdf=file("fictional.pdf","synthetic PDF storage fixture","application/pdf").copy(
            extractedText="--- PAGE 1 ---\nONE\n--- PAGE 2 ---\nTWO\n--- PAGE 3 ---\nTHREE")
        val source=card(files=listOf(pdf))
        val made=mutableListOf<List<Int>>()
        val ids=repo.splitDocument(source.id,mapOf(pdf.id to setOf(1)),true,{3},{_,pages->
            made.add(pages);file("output.pdf",pages.toString(),"application/pdf")
        })
        assertEquals(listOf(listOf(1),listOf(0,2)),made)
        assertEquals("TWO",repo.cards.first{it.id==ids.single()}.files.single().extractedText)
        val remainder=repo.cards.first{it.id==source.id}.files.single()
        assertEquals(listOf("ONE","THREE"),PdfPageTexts.parse(remainder.extractedText,2))
        assertTrue(repo.hasDocument(pdf))
    }
    @Test fun unmappedLegacyOcrIsPreservedInSplitNotes(){
        val pdf=file("fictional.pdf",mime="application/pdf").copy(extractedText="Saved text without page markers")
        val source=card(files=listOf(pdf))
        val ids=repo.splitDocument(source.id,mapOf(pdf.id to setOf(0,1)),true,{2},{_,pages->file("part.pdf",pages.toString(),"application/pdf")})
        assertTrue(repo.cards.first{it.id==ids.first()}.notes.contains(pdf.extractedText))
        assertTrue(repo.cards.filter{it.id in ids}.all{it.files.single().extractedText.isBlank()})
    }
    @Test fun cancelledSplitRemovesGeneratedBlobsWithoutChangingSource(){
        val pdf=file("fictional.pdf",mime="application/pdf");val source=card(files=listOf(pdf))
        val generated=mutableListOf<VaultFile>()
        try{repo.splitDocument(source.id,mapOf(pdf.id to setOf(0,1)),true,{2},{_,pages->
            file("part.pdf",pages.toString(),"application/pdf").also{generated.add(it)}
        },checkCancelled={if(generated.isNotEmpty())throw CancellationException("Fixture cancellation")});fail("Must cancel")}
        catch(_:CancellationException){}
        assertEquals(source,repo.cards.single());assertTrue(generated.none{repo.hasDocument(it)})
    }
    @Test fun failedSplitCommitRollsBackGeneratedOutputs(){
        val pdf=file("fictional.pdf",mime="application/pdf");val source=card(files=listOf(pdf));val generated=mutableListOf<VaultFile>()
        File(context.filesDir,"vault.json.tmp").mkdir()
        try{repo.splitDocument(source.id,mapOf(pdf.id to setOf(0)),false,{1},{_,_->file("part.pdf",mime="application/pdf").also{generated.add(it)}});fail("Must fail")}
        catch(_:IOException){}
        assertEquals(source,repo.cards.single());assertTrue(generated.none{repo.hasDocument(it)})
    }
    @Test fun stalePageSelectionAndLargeBatchFailBeforeCommit(){
        val pdf=file("fictional.pdf",mime="application/pdf");val source=card(files=listOf(pdf))
        for(selection in listOf(setOf(2),(0..100).toSet()))try{
            repo.splitDocument(source.id,mapOf(pdf.id to selection),true,{2},{_,_->error("Must not create output")});fail("Must reject")
        }catch(_:IllegalArgumentException){}
        assertEquals(source,repo.cards.single())
    }
}
