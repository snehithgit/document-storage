package com.snehith.documentvault

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar

/**
 * Native OpenCV regression: the old RGBA convertTo() dimmed alpha as well as RGB.
 * Run on an Android emulator/device with :app:connectedDebugAndroidTest.
 * (Ordinary host-JVM tests cannot load the Android OpenCV native library.)
 */
@RunWith(AndroidJUnit4::class)
class ScanEnhanceNativeTest {
    @Test fun rgbContrastPreservesFullyOpaqueMatAlpha(){
        assertTrue("OpenCV must initialize on this device",OpenCVLoader.initLocal())
        val solid=Mat(72,72,CvType.CV_8UC4,Scalar(80.0,120.0,180.0,255.0))
        val original=Bitmap.createBitmap(72,72,Bitmap.Config.ARGB_8888)
        val output=Mat()
        var enhanced:Bitmap?=null
        try{
            Utils.matToBitmap(solid,original)
            enhanced=ScanEnhance.apply(original,ScanSettings(
                preset="Original",contrast=.55f,brightness=-12))
            Utils.bitmapToMat(enhanced,output)
            val rgba=output.get(36,36)
            assertEquals("Saved preview must stay fully opaque",255.0,rgba[3],0.0)
            assertTrue("RGB must be adjusted rather than left untouched",rgba[0]<80.0)
            assertTrue(rgba[1]<120.0)
            assertTrue(rgba[2]<180.0)
        }finally{
            solid.release();output.release()
            enhanced?.recycle();original.recycle()
        }
    }

    /** Every filter choice must visibly change a shaded, coloured fictional page. */
    @Test fun everyFilterAndShadowRemovalVisiblyChangesThePage(){
        assertTrue(OpenCVLoader.initLocal())
        val page=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(page);val paint=android.graphics.Paint()
        for(x in 0 until 400){ // left-to-right shadow over a warm paper tone
            val shade=(150+x*90/400)
            paint.color=android.graphics.Color.rgb(shade,shade-12,shade-30)
            canvas.drawLine(x.toFloat(),0f,x.toFloat(),300f,paint)
        }
        paint.color=android.graphics.Color.rgb(30,40,90);paint.textSize=34f
        repeat(5){canvas.drawText("FICTIONAL LINE $it",20f,50f+it*50f,paint)}
        val base=ScanEnhance.apply(page,ScanSettings())
        fun difference(a:Bitmap,b:Bitmap):Double {
            var total=0L
            for(y in 0 until a.height step 4)for(x in 0 until a.width step 4){
                val p=a.getPixel(x,y);val q=b.getPixel(x,y)
                total+=kotlin.math.abs(android.graphics.Color.red(p)-android.graphics.Color.red(q))+
                    kotlin.math.abs(android.graphics.Color.green(p)-android.graphics.Color.green(q))+
                    kotlin.math.abs(android.graphics.Color.blue(p)-android.graphics.Color.blue(q))
            }
            return total.toDouble()/((a.width/4)*(a.height/4))
        }
        try{
            val variants=ScanEnhance.presets.filter{it!="Original"}.map{ScanSettings(preset=it)}+
                ScanSettings(removeShadows=true)
            variants.forEach{settings->
                val out=ScanEnhance.apply(page,settings)
                try{ assertTrue("$settings changed too little",difference(base,out)>6.0) }
                finally{out.recycle()}
            }
        }finally{base.recycle();page.recycle()}
    }

    @Test fun shadowRemovalRetainsColorAndOpaqueAlpha(){
        assertTrue(OpenCVLoader.initLocal())
        val solid=Mat(72,72,CvType.CV_8UC4,Scalar(54.0,112.0,180.0,255.0))
        val original=Bitmap.createBitmap(72,72,Bitmap.Config.ARGB_8888)
        val output=Mat()
        var enhanced:Bitmap?=null
        try{
            Utils.matToBitmap(solid,original)
            enhanced=ScanEnhance.apply(original,ScanSettings(
                preset="Original",removeShadows=true))
            Utils.bitmapToMat(enhanced,output)
            val rgba=output.get(36,36)
            assertEquals(255.0,rgba[3],0.0)
            assertTrue("Shadow removal must preserve chroma",kotlin.math.abs(rgba[0]-rgba[2])>8.0)
        }finally{
            solid.release();output.release()
            enhanced?.recycle();original.recycle()
        }
    }
}
