package com.snehith.documentvault

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test

/** Requires device/emulator OpenCV; native Mat is not available in plain JVM tests. */
class ScanEnhanceNativeTest {
    @Test fun solidColourMatKeepsOpaqueAlphaUnderLowContrast() {
        val src=Bitmap.createBitmap(60,60,Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(130,70,40))
        }
        val result=ScanEnhance.apply(src,ScanSettings(contrast=.55f,brightness=-18))
        try {
            val pixel=result.getPixel(20,20)
            assertEquals(255,Color.alpha(pixel))
            assertTrue(Color.red(pixel)<130)
        }finally{result.recycle();src.recycle()}
    }
    @Test fun removeShadowsOnColourDoesNotForceGrayscale() {
        val src=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(200,100,40))
        }
        val result=ScanEnhance.apply(src,ScanSettings(preset="Original",removeShadows=true))
        try {
            val pixel=result.getPixel(70,70)
            assertEquals(255,Color.alpha(pixel))
            assertNotEquals(Color.red(pixel),Color.green(pixel))
        }finally{result.recycle();src.recycle()}
    }
}
