package com.snehith.documentvault

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.math.abs

/** Generated fixtures only; nothing is written to the vault. */
@RunWith(AndroidJUnit4::class)
class ScannerPerformanceDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun quadrants():Bitmap=Bitmap.createBitmap(600,400,Bitmap.Config.ARGB_8888).apply {
        val c=Canvas(this);val p=Paint()
        listOf(Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW).forEachIndexed{i,color->
            p.color=color
            c.drawRect((i%2)*300f,(i/2)*200f,(i%2)*300f+300f,(i/2)*200f+200f,p)
        }
    }
    private fun difference(a:Bitmap,b:Bitmap):Double {
        var total=0L;var count=0
        for(y in 5 until minOf(a.height,b.height)-5 step 7)for(x in 5 until minOf(a.width,b.width)-5 step 7) {
            val p=a.getPixel(x,y);val q=b.getPixel(x,y)
            total+=abs(Color.red(p)-Color.red(q))+abs(Color.green(p)-Color.green(q))+abs(Color.blue(p)-Color.blue(q))
            count++
        }
        return total.toDouble()/count
    }
    @Test fun quarterTurnFoldedIntoWarpMatchesRotateThenCrop() {
        val source=quadrants()
        val corners=listOf(PointF(.1f,.08f),PointF(.9f,.12f),PointF(.88f,.9f),PointF(.12f,.92f))
        for(turns in 1..3) {
            val rotated=Bitmap.createBitmap(source,0,0,source.width,source.height,
                Matrix().apply{postRotate(turns*90f)},true)
            val before=DocumentCropEngine.crop(rotated,corners)
            val after=DocumentCropEngine.crop(source,corners.map{
                CropGeometry.unrotate(it.x to it.y,turns*90).let{(x,y)->PointF(x,y)}})
            try {
                assertEquals("width, turn $turns",before.width.toFloat(),after.width.toFloat(),2f)
                assertEquals("height, turn $turns",before.height.toFloat(),after.height.toFloat(),2f)
                assertTrue("pixels, turn $turns",difference(before,after)<12.0)
            } finally { rotated.recycle();before.recycle();after.recycle() }
        }
        source.recycle()
    }
    @Test fun longPdfPageRendersInProportion() {
        val pdf=File(context.cacheDir,"fictional-receipt-${UUID.randomUUID()}.pdf")
        PdfDocument().apply {
            val page=startPage(PdfDocument.PageInfo.Builder(300,3000,1).create())
            page.canvas.drawText("FICTIONAL RECEIPT",20f,40f,Paint().apply{textSize=18f})
            finishPage(page)
            pdf.outputStream().use{writeTo(it)}
            close()
        }
        try {
            PdfPageSession(pdf).use { session ->
                val image=session.render(0,1700)
                assertTrue(image.height<=4200)
                assertEquals(10f,image.height.toFloat()/image.width,.1f)
            }
        } finally { pdf.delete() }
    }
}
