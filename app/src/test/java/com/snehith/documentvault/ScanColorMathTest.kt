package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class ScanColorMathTest {
    @Test fun brightnessContrastDoNotChangeAlpha(){
        val rgba=intArrayOf(70,120,180,255)
        val output=rgba.copyOf()
        for(i in 0..2)output[i]=ScanColorMath.adjust(rgba[i],0.55f,-12)
        assertEquals(255,output[3])
        assertTrue(output[0]<rgba[0])
    }
    @Test fun sharpenBlurSigmaScalesWithImageSize(){
        assertTrue(ScanColorMath.sharpnessSigma(3200,2000)>
            ScanColorMath.sharpnessSigma(1000,650))
    }
}
