package com.snehith.documentvault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Runs the real OCR worker on generated fictional images in the isolated package. */
@RunWith(AndroidJUnit4::class)
class VaultOcrDeviceTest {
    private lateinit var context:Context
    private lateinit var repo:VaultRepository
    private val created=mutableListOf<VaultCard>()
    @Before fun setup() {
        context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="com.snehith.documentvault.storagecheck") {
            "OCR device tests require -PvaultDebugSuffix=.storagecheck"
        }
        repo=VaultStorage.get(context)
    }
    @After fun cleanup() { created.forEach{card->repo.cards.firstOrNull{it.id==card.id}?.let(repo::deleteCard)} }
    private fun image(text:String?):VaultFile {
        val id=UUID.randomUUID().toString()
        val bitmap=Bitmap.createBitmap(1200,400,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.WHITE)}
        if(text!=null)Canvas(bitmap).drawText(text,60f,240f,Paint().apply{color=Color.BLACK;textSize=110f;isAntiAlias=true})
        val target=File(context.filesDir,"blobs/$id.dat").apply{parentFile!!.mkdirs()}
        target.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
        return VaultFile(id,"fictional-scan.png","image/png",target.length(),
            VaultBackupChecks.sha256(target.inputStream()),"Saved fictional OCR","On-device OCR")
    }
    private fun save(file:VaultFile,vararg fields:DataField)=VaultCard(title="Fictional OCR card",
        fields=fields.toList(),files=listOf(file)).also{repo.saveCard(it);created.add(it)}
    private fun run(card:VaultCard):ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<VaultOcrWorker>(context).setInputData(workDataOf(
            VaultOcrWorker.KEY_CARD to card.id,VaultOcrWorker.KEY_FILE to card.files.single().id,
            VaultOcrWorker.KEY_SCRIPT to "Latin")).build().doWork()
    }
    @Test fun blankPageKeepsSavedOcrAndReportsIt() {
        val card=save(image(null))
        val result=run(card) as ListenableWorker.Result.Success
        assertFalse(result.outputData.getString(VaultOcrWorker.KEY_ERROR).isNullOrBlank())
        assertEquals("Saved fictional OCR",VaultRepository(context).cards.first{it.id==card.id}.files.single().extractedText)
    }
    @Test fun readablePageUpdatesTextAndKeepsManualField() {
        val manual=DataField("Reference","MANUAL-001",source="manual")
        val card=save(image("FICTIONAL 4821"),manual)
        val result=run(card) as ListenableWorker.Result.Success
        assertNull(result.outputData.getString(VaultOcrWorker.KEY_ERROR))
        val saved=VaultRepository(context).cards.first{it.id==card.id}
        assertTrue(saved.files.single().extractedText,saved.files.single().extractedText.contains("4821"))
        assertEquals(manual,saved.fields.first{it.id==manual.id})
    }
}
