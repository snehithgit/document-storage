package com.snehith.documentvault

import android.content.Context
import android.net.Uri
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException

abstract class VaultImportContract {
    protected abstract fun appContext():Context
    protected abstract fun fixtureUri(file:File):Uri
    private lateinit var context:Context
    private lateinit var sources:File
    private lateinit var repo:VaultRepository
    @Before fun setupImports() {
        val app=appContext()
        val root=File(app.cacheDir,"shared/import-contract-${UUID.randomUUID()}").apply{mkdirs()}
        sources=File(root,"sources").apply{mkdirs()}
        context=object:android.content.ContextWrapper(app) {
            override fun getFilesDir()=File(root,"files").apply{mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply{mkdirs()}
            override fun getSharedPreferences(name:String,mode:Int)=app.getSharedPreferences("fixture-${root.name}-$name",mode)
        }
        repo=VaultRepository(context)
    }
    private fun source(name:String,bytes:ByteArray)=fixtureUri(File(sources,name).apply{writeBytes(bytes)})
    private fun text(name:String="fictional.txt",value:String="FICTIONAL fixture")=source(name,value.toByteArray())
    private fun documents()=File(context.filesDir,"blobs").listFiles()?.filter{it.extension=="dat"}?:emptyList()
    private fun importFiles(vararg uris:Uri,target:String?=null,profile:String="self",group:Boolean=false)=
        repo.importDocuments(uris.toList(),target,profile,groupAsOne=group)
    private fun png(red:Int):ByteArray {
        val output=ByteArrayOutputStream()
        output.write(byteArrayOf(-119,80,78,71,13,10,26,10))
        fun chunk(name:String,bytes:ByteArray) {
            val tag=name.toByteArray(Charsets.US_ASCII)
            DataOutputStream(output).apply {
                writeInt(bytes.size);write(tag);write(bytes)
                writeInt(CRC32().apply{update(tag);update(bytes)}.value.toInt())
            }
        }
        val header=ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).apply {
            writeInt(12);writeInt(12);write(byteArrayOf(8,2,0,0,0))
        } }.toByteArray()
        chunk("IHDR",header)
        val pixels=ByteArrayOutputStream()
        DeflaterOutputStream(pixels).use { zip -> repeat(12) {
            zip.write(0);repeat(12){zip.write(byteArrayOf(red.toByte(),40,80))}
        } }
        chunk("IDAT",pixels.toByteArray());chunk("IEND",byteArrayOf())
        return output.toByteArray()
    }
    private fun pdf():ByteArray {
        val out=ByteArrayOutputStream()
        fun write(value:String){out.write(value.toByteArray(Charsets.US_ASCII))}
        write("%PDF-1.4\n")
        val offsets=mutableListOf(0)
        listOf("<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Resources << >> /Contents 4 0 R >>",
            "<< /Length 0 >>\nstream\n\nendstream").forEachIndexed { i,body ->
            offsets.add(out.size());write("${i+1} 0 obj\n$body\nendobj\n")
        }
        val xref=out.size()
        write("xref\n0 5\n0000000000 65535 f \n")
        offsets.drop(1).forEach{write(String.format(java.util.Locale.ROOT,"%010d 00000 n \n",it))}
        write("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }
    private fun docx(document:String="""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>FICTIONAL fixture</w:t></w:r></w:p></w:body></w:document>"""):ByteArray {
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            mapOf("[Content_Types].xml" to """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""",
                "word/document.xml" to document).forEach { (name,value) ->
                zip.putNextEntry(ZipEntry(name));zip.write(value.toByteArray());zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
    @Test fun multipleImagesCommitAsOneDocument() {
        val result=importFiles(source("red.png",png(180)),source("blue.png",png(20)),group=true)
        assertEquals(2,result.count);assertEquals(1,repo.cards.size)
        assertEquals(2,VaultRepository(context).cards.single().files.size)
        assertTrue(repo.cards.single().files.all{it.mime=="image/png"&&repo.hasDocument(it)})
    }
    /** Workflow 12: re-editing a scanned page replaces it in place and keeps the original. */
    @Test fun reEditingScannedPageReplacesItWithoutExtraPage() {
        importFiles(source("front.png",png(180)),source("back.png",png(20)),group=true)
        val card=repo.cards.single();val front=card.files[0]
        val edited=repo.importFile(source("front-edited.png",png(90)))
        repo.replaceImageFile(card.id,front.id,edited,"0.1,0.1;0.9,0.1;0.9,0.9;0.1,0.9",90)
        val saved=VaultRepository(context).cards.single().files
        assertEquals(listOf(edited.id,card.files[1].id),saved.map{it.id})
        assertEquals(front.id,saved[0].originalId)
        assertEquals(front.name,saved[0].name)
        assertEquals(90,saved[0].cropRotation)
        assertTrue("the original is retained for re-cropping",repo.hasDocument(front))
    }
    /** Robolectric's PdfRenderer always reports zero pages; real PDF parsing runs on device. */
    protected open val rendersPdf=true
    @Test fun pdfAndDocxImportAndReopen() {
        Assume.assumeTrue("PdfRenderer is device-only",rendersPdf)
        importFiles(source("fictional.pdf",pdf()),source("fictional.docx",docx()))
        assertEquals(setOf("application/pdf","application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            VaultRepository(context).cards.flatMap{it.files}.map{it.mime}.toSet())
    }
    @Test fun invalidSecondFileRollsBackWholeSelection() {
        try { importFiles(text(),source("empty.txt",byteArrayOf()));fail("Must reject empty file") }
        catch(_:IOException) {}
        assertTrue(repo.cards.isEmpty());assertTrue(documents().isEmpty())
        assertTrue(VaultRepository(context).cards.isEmpty())
    }
    @Test fun corruptPdfIsNotStoredAsRecoverableImport() {
        try { importFiles(source("bad.pdf","%PDF-1.4 garbage".toByteArray()));fail("Must reject invalid PDF") }
        catch(_:IOException) {}
        assertTrue(repo.recoverableFiles().isEmpty());assertTrue(documents().isEmpty())
    }
    @Test fun corruptImageIsRejected() {
        try { importFiles(source("bad.png",byteArrayOf(-119,80,78,71,13,10,26,10)));fail("Must reject invalid image") }
        catch(_:IOException) {}
        assertTrue(documents().isEmpty())
    }
    @Test fun ordinaryZipClaimingDocxIsRejected() {
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use{it.putNextEntry(ZipEntry("ordinary.txt"));it.write("fictional".toByteArray());it.closeEntry()}
        try { importFiles(source("bad.docx",out.toByteArray()));fail("ZIP is not necessarily DOCX") }
        catch(_:IOException) {}
        assertTrue(documents().isEmpty())
    }
    @Test fun docxExternalEntityDeclarationRejected() {
        val xml="""<!DOCTYPE document [<!ENTITY external SYSTEM "file:///fictional-never-read">]><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>&external;</w:body></w:document>"""
        try { importFiles(source("bad.docx",docx(xml)));fail("Must reject document declaration") }
        catch(_:IOException) {}
        assertTrue(documents().isEmpty())
    }
    @Test fun duplicateSelectionIsSkippedWithoutOrphans() {
        val same=text()
        val result=importFiles(same,same,group=true)
        assertEquals(1,result.count);assertEquals(1,result.skipped)
        assertEquals(1,documents().size);assertTrue(repo.recoverableFiles().isEmpty())
    }
    @Test fun destinationOwnerControlsDuplicateDetection() {
        repo.saveProfile(VaultProfile("fictional-person","Fictional Person"))
        val first=importFiles(text(),profile="fictional-person")
        val duplicate=importFiles(text(),target=first.cardId,profile="self")
        assertEquals(0,duplicate.count);assertEquals(1,duplicate.skipped)
        assertEquals(1,repo.cards.single().files.size)
    }
    @Test fun sameContentCanBelongToDifferentPeople() {
        repo.saveProfile(VaultProfile("fictional-person","Fictional Person"))
        importFiles(text(),profile="self");importFiles(text(),profile="fictional-person")
        assertEquals(2,repo.cards.size);assertEquals(2,documents().size)
    }
    @Test fun addPageKeepsOwnerFieldsAndExistingAttachment() {
        val first=importFiles(text())
        val old=repo.cards.single()
        repo.saveCard(old.copy(fields=listOf(DataField("Reference","FICTIONAL-001",id="stable-field"))))
        importFiles(text("page2.txt","second fictional page"),target=first.cardId)
        val updated=VaultRepository(context).cards.single()
        assertEquals(old.files.single(),updated.files.first())
        assertEquals("stable-field",updated.fields.single().id);assertEquals(2,updated.files.size)
    }
    @Test fun staleTargetCannotCreateAnUnexpectedDocument() {
        try { importFiles(text(),target="deleted-target");fail("Must reject missing target") }
        catch(_:IllegalStateException) {}
        assertTrue(repo.cards.isEmpty());assertTrue(documents().isEmpty())
    }
    @Test fun failedIndexCommitCleansSelectionAndKeepsOldDocument() {
        importFiles(text())
        val old=repo.cards.single()
        File(context.filesDir,"vault.json.tmp").mkdir()
        try { importFiles(text("second.txt","second fictional fixture"));fail("Expected index failure") }
        catch(_:IOException) {}
        assertEquals(old,repo.cards.single());assertEquals(1,documents().size)
        assertEquals(old,VaultRepository(context).cards.single())
    }
    @Test fun cancellationAfterStagingDoesNotCommitPartialSelection() {
        try {
            repo.importDocuments(listOf(text(),text("second.txt","second")),null,"self",checkCancelled={
                if(documents().isNotEmpty())throw CancellationException("Fixture cancellation")
            });fail("Expected cancellation")
        } catch(_:CancellationException) {}
        assertTrue(repo.cards.isEmpty());assertTrue(documents().isEmpty())
    }
    @Test fun staleOrphanDeleteCannotRemoveNowLinkedFile() {
        val file=repo.importFile(text())
        val stale=repo.recoverableFiles().single()
        repo.saveCard(repo.newCard().copy(files=listOf(file)))
        try { repo.removeOrphan(stale);fail("Must protect linked attachment") }
        catch(_:IOException) {}
        assertTrue(repo.hasDocument(file))
    }
    @Test fun hiddenOriginalIsAlsoProtectedFromStaleOrphanDelete() {
        importFiles(text())
        val original=repo.importFile(text("original.txt","fictional original"))
        val stale=repo.recoverableFiles().single()
        repo.keepScanSource(repo.cards.single().id,repo.cards.single().files.single().id,original,"",0)
        try { repo.removeOrphan(stale);fail("Must protect retained original") }
        catch(_:IOException) {}
        assertTrue(repo.hasDocument(original))
    }
    @Test fun removingOneAttachmentDoesNotDeleteSharedBytes() {
        val file=repo.importFile(text())
        val a=repo.newCard().copy(files=listOf(file));val b=repo.newCard().copy(files=listOf(file))
        repo.saveCard(a);repo.saveCard(b);repo.removeFile(a.id,file.id)
        assertTrue(repo.hasDocument(repo.cards.first{it.id==b.id}.files.single()))
        assertTrue(repo.cards.first{it.id==a.id}.files.isEmpty())
    }
    @Test fun recoveredFileRecomputesStaleMetadata() {
        val file=repo.importFile(text())
        File(context.filesDir,"blobs/${file.id}.meta").writeText("""{"name":"fictional.txt","mime":"text/plain","size":999,"sha":"stale"}""")
        repo.attachRecovered(file.id,"Recovered fictional fixture")
        val saved=repo.cards.single().files.single()
        assertEquals(file.sha256,saved.sha256);assertEquals(file.bytes,saved.bytes)
        assertTrue(repo.recoverableFiles().isEmpty())
    }
    @Test fun recoveryPreservesPreviousIndexOcr() {
        importFiles(text())
        val card=repo.cards.single()
        repo.saveCard(card.copy(files=listOf(card.files.single().copy(extractedText="saved fictional OCR"))))
        repo.deleteCard(repo.cards.single())
        repo.attachRecovered(card.files.single().id,"Recovered fictional fixture")
        assertEquals("saved fictional OCR",repo.cards.single().files.single().extractedText)
    }
    @Test fun genericRecoveredZipIsNotMislabelledDocx() {
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use{it.putNextEntry(ZipEntry("fictional.txt"));it.write(65);it.closeEntry()}
        val id="11111111-1111-4111-8111-111111111111"
        File(context.filesDir,"blobs/$id.dat").writeBytes(out.toByteArray())
        repo.attachRecovered(id,"Fictional ZIP")
        assertEquals("application/octet-stream",repo.cards.single().files.single().mime)
    }
    @Test fun failedRecoveryKeepsOrphanBytes() {
        val file=repo.importFile(text())
        File(context.filesDir,"vault.json.tmp").mkdir()
        try { repo.attachRecovered(file.id,"Fictional");fail("Must fail") }
        catch(_:IOException) {}
        assertTrue(repo.cards.isEmpty());assertTrue(repo.hasDocument(file))
        assertEquals(file.id,repo.recoverableFiles().single().id)
    }
}
