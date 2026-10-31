package com.snehith.documentvault

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

abstract class VaultStorageContract {
    protected abstract fun appContext():Context
    private lateinit var context:Context
    private lateinit var repo:VaultRepository
    private lateinit var attachment:VaultFile
    @Before fun setup() {
        val app=appContext()
        val root=File(app.cacheDir,"storage-contract-${UUID.randomUUID()}").apply{mkdirs()}
        context=object:android.content.ContextWrapper(app) {
            override fun getFilesDir()=File(root,"files").apply{mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply{mkdirs()}
        }
        repo=VaultRepository(context)
        val id="11111111-1111-4111-8111-111111111111"
        val bytes="Fictional test document".toByteArray()
        File(context.filesDir,"blobs/$id.dat").writeBytes(bytes)
        attachment=VaultFile(id,"fictional.txt","text/plain",bytes.size.toLong(),
            VaultBackupChecks.sha256(bytes.inputStream()),"Fictional OCR")
        repo.saveCard(VaultCard(id="fictional-card",title="Fictional",fields=listOf(DataField("Reference","FICTION-001",id="fictional-field")),files=listOf(attachment)))
    }
    private fun backup():File = File(context.cacheDir,"test-${UUID.randomUUID()}.zip").also {
        repo.exportBackup(Uri.fromFile(it),"")
    }
    private fun entries(file:File):LinkedHashMap<String,ByteArray> {
        val result=linkedMapOf<String,ByteArray>()
        ZipInputStream(file.inputStream()).use { zip ->
            while(true) { val e=zip.nextEntry?:break; result[e.name]=zip.readBytes() }
        }
        return result
    }
    private fun zip(contents:Map<String,ByteArray>):File = File(context.cacheDir,"test-${UUID.randomUUID()}.zip").also {
        ZipOutputStream(it.outputStream()).use { out -> contents.forEach { (name,bytes) ->
            out.putNextEntry(ZipEntry(name));out.write(bytes);out.closeEntry()
        } }
    }
    private fun rejects(file:File) {
        val before=File(context.filesDir,"vault.json").readBytes()
        try { repo.restoreBackup(Uri.fromFile(file),"");fail("Must reject invalid backup") }
        catch (_:Exception) {}
        assertArrayEquals(before,File(context.filesDir,"vault.json").readBytes())
        assertEquals("Fictional OCR",VaultRepository(context).cards.single().files.single().extractedText)
    }
    @Test fun repeatedBackupRestorePreservesIdsFieldsAndOcr() {
        repeat(3) { repo.restoreBackup(Uri.fromFile(backup()),"") }
        val loaded=VaultRepository(context).cards.single()
        assertEquals("fictional-card",loaded.id)
        assertEquals("fictional-field",loaded.fields.single().id)
        assertEquals("FICTION-001",loaded.fields.single().value)
        assertEquals(attachment,loaded.files.single())
    }
    @Test fun incorrectBlobHashRejected() {
        val e=entries(backup());e["blobs/${attachment.id}"]="corrupt".toByteArray();rejects(zip(e))
    }
    @Test fun incorrectOcrHashRejected() {
        val e=entries(backup());e["texts/${attachment.id}.txt"]="tampered".toByteArray();rejects(zip(e))
    }
    @Test fun missingAttachmentRejected() {
        val e=entries(backup());e.remove("blobs/${attachment.id}");rejects(zip(e))
    }
    @Test fun legacyWithoutHashesOrSidecarsRestoresInlineOcr() {
        val e=entries(backup());e.remove("checksums.json");e.remove("texts/${attachment.id}.txt")
        val root=JSONObject(String(e.getValue("index.json")))
        root.remove("formatVersion")
        root.getJSONArray("cards").getJSONObject(0).getJSONArray("files").getJSONObject(0).apply {
            remove("sha256");remove("bytes")
        }
        e["index.json"]=root.toString().toByteArray()
        repo.restoreBackup(Uri.fromFile(zip(e)),"")
        assertEquals("Fictional OCR",VaultRepository(context).cards.single().files.single().extractedText)
        repo.restoreBackup(Uri.fromFile(backup()),"") // normalized legacy metadata remains exportable
    }
    @Test fun legacyBlobOnlyManifestIsAccepted() {
        val e=entries(backup())
        val root=JSONObject(String(e.getValue("index.json"))).apply{remove("formatVersion")}
        e["index.json"]=root.toString().toByteArray()
        val manifest=JSONObject(String(e.getValue("checksums.json")))
        manifest.remove("texts/${attachment.id}.txt")
        e["checksums.json"]=manifest.toString().toByteArray()
        repo.restoreBackup(Uri.fromFile(zip(e)),"")
        assertEquals(attachment,repo.cards.single().files.single())
    }
    @Test fun newBackupCannotSilentlyLoseOcrPayload() {
        val e=entries(backup());e.remove("texts/${attachment.id}.txt")
        val manifest=JSONObject(String(e.getValue("checksums.json")))
        manifest.remove("texts/${attachment.id}.txt")
        e["checksums.json"]=manifest.toString().toByteArray();rejects(zip(e))
    }
    @Test fun hiddenOriginalRestored() {
        val original="22222222-2222-4222-8222-222222222222"
        File(context.filesDir,"blobs/$original.dat").writeText("fictional original scan")
        repo.saveCard(repo.cards.single().copy(files=listOf(attachment.copy(originalId=original))))
        val saved=backup()
        File(context.filesDir,"blobs/$original.dat").delete()
        repo.restoreBackup(Uri.fromFile(saved),"")
        assertEquals("fictional original scan",repo.originalDocumentFile(repo.cards.single().files.single()).readText())
    }
    @Test fun unfinishedTemporaryIndexIsIgnored() {
        File(context.filesDir,"vault.json.tmp").writeText("{\"cards\":[]}")
        assertEquals(1,VaultRepository(context).cards.size)
    }
    @Test fun staleOrUnwritableSidecarCannotLoseCommittedOcr() {
        File(context.filesDir,"blobs/${attachment.id}.txt").mkdir()
        repo.saveCard(repo.cards.single().copy(files=listOf(attachment.copy(extractedText="updated OCR"))))
        assertEquals("updated OCR",VaultRepository(context).cards.single().files.single().extractedText)
    }
    @Test fun failedIndexWriteRollsBackInMemoryAndOnDisk() {
        File(context.filesDir,"vault.json.tmp").mkdir()
        try { repo.saveCard(repo.cards.single().copy(title="changed"));fail("Must fail") }
        catch (_:IOException) {}
        assertEquals("Fictional",repo.cards.single().title)
        assertEquals("Fictional",VaultRepository(context).cards.single().title)
    }
    @Test fun duplicateZipEntriesRejected() {
        val e=entries(backup());e["other.json"]=e.getValue("index.json")
        val archive=zip(e)
        // ZIP names have no checksum: replace the same-length name in both headers.
        val bytes=archive.readBytes()
        val from="other.json".toByteArray();val to="index.json".toByteArray()
        for(i in 0..bytes.size-from.size)if(from.indices.all{bytes[i+it]==from[it]})
            to.copyInto(bytes,i)
        archive.writeBytes(bytes);rejects(archive)
    }
    @Test fun unsupportedPathRejected() {
        val e=entries(backup());e["../unsafe"]="fictional".toByteArray();rejects(zip(e))
    }
    @Test fun oversizedOcrEntryRejected() {
        val e=entries(backup());e["texts/${attachment.id}.txt"]=ByteArray(5_000_001){65}
        rejects(zip(e))
    }
    @Test fun exportDoesNotReplaceGoodBackupWhenAttachmentIsCorrupt() {
        val saved=backup();val before=saved.readBytes()
        repo.documentFile(attachment).writeText("corrupt")
        try { repo.exportBackup(Uri.fromFile(saved),"");fail("Must fail") }
        catch (_:IllegalArgumentException) {}
        assertArrayEquals(before,saved.readBytes())
    }
    @Test fun legacyIndexWithoutHashOrSizeIsStillExportable() {
        val data=File(context.filesDir,"vault.json")
        val root=JSONObject(data.readText())
        root.getJSONArray("cards").getJSONObject(0).getJSONArray("files").getJSONObject(0)
            .put("sha256","").remove("bytes")
        data.writeText(root.toString())
        repo=VaultRepository(context)
        repo.restoreBackup(Uri.fromFile(backup()),"")
        assertEquals(attachment,VaultRepository(context).cards.single().files.single())
    }
    @Test fun removingPersonKeepsTheirDocumentsAfterRestart() {
        repo.saveProfile(VaultProfile("fictional-person","Fictional Person"))
        repo.saveCard(repo.cards.single().copy(profile="fictional-person"))
        repo.removePerson("fictional-person")
        val reloaded=VaultRepository(context)
        assertEquals("self",reloaded.cards.single().profile)
        assertEquals(attachment,reloaded.cards.single().files.single())
        assertEquals("all",VaultPersonFilter.effective("fictional-person",reloaded.profiles))
    }
    @Test fun restoreWithoutSelectedPersonLeavesDocumentsReachable() {
        val saved=backup()
        repo.saveProfile(VaultProfile("fictional-person","Fictional Person"))
        repo.restoreBackup(Uri.fromFile(saved),"")
        val person=VaultPersonFilter.effective("fictional-person",repo.profiles)
        assertEquals("all",person)
        assertEquals(listOf("fictional-card"),VaultPersonFilter.filter(repo.cards,person).map{it.id})
    }
    @Test fun recoveredAttachmentWithoutMetadataIsExportable() {
        val id="33333333-3333-4333-8333-333333333333"
        File(context.filesDir,"blobs/$id.dat").writeText("fictional recovered page")
        repo.attachRecovered(id,"Recovered fictional")
        repo.restoreBackup(Uri.fromFile(backup()),"")
        val recovered=VaultRepository(context).cards.flatMap{it.files}.single{it.id==id}
        assertEquals(VaultBackupChecks.sha256("fictional recovered page".byteInputStream()),recovered.sha256)
    }
    @Test fun legacySidecarMigratesWithoutChangingAttachmentOrFieldIds() {
        val data=File(context.filesDir,"vault.json")
        val root=JSONObject(data.readText())
        root.getJSONArray("cards").getJSONObject(0).getJSONArray("files").getJSONObject(0)
            .remove("extractedText")
        File(context.filesDir,"blobs/${attachment.id}.txt").writeText("legacy OCR")
        data.writeText(root.toString())
        assertEquals("legacy OCR",VaultRepository(context).cards.single().files.single().extractedText)
        File(context.filesDir,"blobs/${attachment.id}.txt").writeText("stale sidecar")
        assertEquals("legacy OCR",VaultRepository(context).cards.single().files.single().extractedText)
        assertEquals(attachment.id,VaultRepository(context).cards.single().files.single().id)
    }
    @Test fun corruptedZipCrcRejected() {
        val e=entries(backup())
        val archive=File(context.cacheDir,"crc-test.zip")
        ZipOutputStream(archive.outputStream()).use { out -> e.forEach { (name,bytes) ->
            val crc=java.util.zip.CRC32().apply{update(bytes)}
            out.putNextEntry(ZipEntry(name).apply {
                method=ZipEntry.STORED;size=bytes.size.toLong();compressedSize=size;this.crc=crc.value
            });out.write(bytes);out.closeEntry()
        } }
        val bytes=archive.readBytes();val marker="Fictional test document".toByteArray()
        val offset=(0..bytes.size-marker.size).first{i->marker.indices.all{bytes[i+it]==marker[it]}}
        bytes[offset]=(bytes[offset].toInt() xor 1).toByte();archive.writeBytes(bytes)
        rejects(archive)
    }
    @Test fun failedRestoreCommitPreservesOldVaultAndOcr() {
        val saved=backup()
        repo.saveCard(repo.cards.single().copy(title="Current vault"))
        File(context.filesDir,"vault.json.tmp").mkdir()
        try { repo.restoreBackup(Uri.fromFile(saved),"");fail("Must fail") }
        catch (_:IOException) {}
        assertEquals("Current vault",repo.cards.single().title)
        assertEquals("Current vault",VaultRepository(context).cards.single().title)
        assertEquals("Fictional OCR",repo.cards.single().files.single().extractedText)
    }
    @Test fun missingOptionalOriginalKeepsVisiblePageAndReportsWarning() {
        repo.saveCard(repo.cards.single().copy(files=listOf(attachment.copy(originalId=UUID.randomUUID().toString()))))
        val warnings=repo.exportBackup(Uri.fromFile(File(context.cacheDir,"optional.zip")),"")
        assertTrue(warnings.isNotEmpty())
        repo.restoreBackup(Uri.fromFile(File(context.cacheDir,"optional.zip")),"")
        assertEquals(attachment.id,repo.cards.single().files.single().id)
    }
    @Test fun corruptIndexFallsBackWithPreviousOcr() {
        repo.saveCard(repo.cards.single().copy(files=listOf(attachment.copy(extractedText="new OCR"))))
        File(context.filesDir,"vault.json").writeText("{}")
        val recovered=VaultRepository(context)
        assertEquals("Fictional OCR",recovered.cards.single().files.single().extractedText)
    }
    @Test fun healthIsReadOnlyAndCountsUnlinkedFiles() {
        File(context.filesDir,"blobs/${UUID.randomUUID()}.dat").writeText("recoverable")
        val before=File(context.filesDir,"vault.json").readBytes()
        assertTrue(repo.storageHealth().contains("Recoverable unlinked attachments: 1"))
        assertArrayEquals(before,File(context.filesDir,"vault.json").readBytes())
    }
    @Test fun malformedCurrentProfilesCannotOverwriteGoodRecoveryIndex() {
        repo.saveCard(repo.cards.single().copy(title="second"))
        val prior=File(context.filesDir,"vault.json.bak").readBytes()
        val current=File(context.filesDir,"vault.json")
        current.writeText(JSONObject(current.readText()).put("profiles",
            org.json.JSONArray().put(JSONObject().put("id","broken"))).toString())
        repo.saveCard(repo.cards.single().copy(title="third"))
        assertArrayEquals(prior,File(context.filesDir,"vault.json.bak").readBytes())
    }
    @Test fun unreadableLegacySidecarDoesNotCommitBlankOcr() {
        val data=File(context.filesDir,"vault.json")
        val root=JSONObject(data.readText())
        root.getJSONArray("cards").getJSONObject(0).getJSONArray("files").getJSONObject(0)
            .remove("extractedText")
        data.writeText(root.toString())
        File(context.filesDir,"blobs/${attachment.id}.txt").mkdir()
        val before=data.readBytes()
        val loaded=VaultRepository(context)
        assertNotNull("Unreadable legacy text must require recovery",loaded.recoveryError)
        assertArrayEquals(before,data.readBytes())
    }
}
