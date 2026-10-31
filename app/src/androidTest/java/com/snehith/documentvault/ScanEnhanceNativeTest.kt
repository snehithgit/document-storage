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
