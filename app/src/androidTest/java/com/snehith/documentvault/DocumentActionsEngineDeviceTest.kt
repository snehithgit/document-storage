package com.snehith.documentvault

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real PDF transformations, using only generated coloured pages in an isolated fixture vault. */
@RunWith(AndroidJUnit4::class)
class DocumentActionsEngineDeviceTest {
    private lateinit var context:Context
    private lateinit var repo:VaultRepository
    private lateinit var engine:DocumentEngine
    @Before fun setup(){
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        check(app.packageName=="com.snehith.documentvault.uxcheck")
        val root=File(app.cacheDir,"shared/pdf-actions-${UUID.randomUUID()}").apply{mkdirs()}
        context=object:ContextWrapper(app){
            override fun getFilesDir()=File(root,"files").apply{mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply{mkdirs()}
            override fun getSharedPreferences(name:String,mode:Int)=app.getSharedPreferences("${root.name}-$name",mode)
        }
        repo=VaultRepository(context);engine=DocumentEngine(context,repo)
    }
    private fun staged(extension:String)=File(context.cacheDir,"${UUID.randomUUID()}.$extension")
    private fun uri(file:File):Uri=FileProvider.getUriForFile(context.applicationContext,context.packageName+".provider",file)
    private fun source():VaultCard {
        val pdf=staged("pdf")
        val doc=PdfDocument()
        try {
            listOf(Color.RED,Color.GREEN,Color.BLUE).forEachIndexed { i,color ->
                val page=doc.startPage(PdfDocument.PageInfo.Builder(200,200,i+1).create())
                page.canvas.drawColor(color);doc.finishPage(page)
            }
            pdf.outputStream().use{doc.writeTo(it)}
        }finally{doc.close()}
        return VaultCard(title="Fictional three colour pages",files=listOf(repo.importFile(uri(pdf))),
            fields=listOf(DataField("Reference","FIC-PDF-001"))).also{repo.saveCard(it)}
    }
    private fun assertColour(file:VaultFile,page:Int,expected:Int){
        ParcelFileDescriptor.open(repo.documentFile(file),ParcelFileDescriptor.MODE_READ_ONLY).use{fd->
            PdfRenderer(fd).use{renderer->renderer.openPage(page).use{p->
                val bitmap=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888)
                try{
                    p.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val actual=bitmap.getPixel(100,100)
                    assertEquals(Color.red(expected).toDouble(),Color.red(actual).toDouble(),8.0)
                    assertEquals(Color.green(expected).toDouble(),Color.green(actual).toDouble(),8.0)
                    assertEquals(Color.blue(expected).toDouble(),Color.blue(actual).toDouble(),8.0)
                }finally{bitmap.recycle()}
            }}
        }
    }
    @Test fun enhancementReplacesAttachmentAndOnlyTheChosenPage(){
        val original=source();val file=original.files.single()
        val image=staged("png");val bitmap=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888)
        try{bitmap.eraseColor(Color.YELLOW);image.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
        finally{bitmap.recycle()}
        val output=staged("pdf")
        engine.enhancedPdfPage(file,1,uri(image),uri(output))
        repo.replaceAttachment(original.id,file.id,repo.importFile(uri(output)),true)
        val saved=VaultRepository(context).cards.single()
        assertEquals(original.id,saved.id);assertEquals(original.fields,saved.fields)
        val updated=saved.files.single();assertEquals(file.id,updated.originalId)
        assertEquals(3,engine.pdfPageCount(updated))
        assertColour(updated,0,Color.RED);assertColour(updated,1,Color.YELLOW);assertColour(updated,2,Color.BLUE)
        assertColour(file,1,Color.GREEN)
    }
    @Test fun splitMovePreservesEverySelectedAndRemainingPage(){
        val original=source();val file=original.files.single()
        val ids=repo.splitDocument(original.id,mapOf(file.id to setOf(0,2)),true,engine::pdfPageCount,{f,pages->
            val output=staged("pdf");engine.copyPdfPages(f,pages,uri(output));repo.importFile(uri(output))
        })
        assertEquals(2,ids.size);assertEquals(3,repo.cards.size)
        val parts=ids.map{id->repo.cards.first{it.id==id}.files.single()}
        parts.forEach{assertEquals(1,engine.pdfPageCount(it))}
        assertColour(parts[0],0,Color.RED);assertColour(parts[1],0,Color.BLUE)
        val remaining=repo.cards.first{it.id==original.id}.files.single()
        assertEquals(1,engine.pdfPageCount(remaining));assertColour(remaining,0,Color.GREEN)
        assertColour(file,0,Color.RED);assertColour(file,1,Color.GREEN);assertColour(file,2,Color.BLUE)
    }
}
