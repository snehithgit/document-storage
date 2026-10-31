package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class ScannerPerformanceGeometryTest {
    private fun viewer(w:Int,h:Int,target:Int=1050)=PdfPageGeometry.renderSize(w,h,target,.5f,4f,3200,4200)
    private fun aspect(size:Pair<Int,Int>)=size.second.toFloat()/size.first

    @Test fun ordinaryA4PageKeepsPreviousSize() {
        assertEquals(1050 to 1485,viewer(595,842))
        assertEquals(1700 to 2405,viewer(595,842,1700))
    }
    @Test fun longReceiptKeepsItsProportions() {
        val size=viewer(595,5000,1700)
        assertTrue(size.second<=4200)
        assertEquals(5000f/595,aspect(size),.05f)
    }
    @Test fun oversizedPageFitsBoundsWithoutDistortion() {
        val size=viewer(9000,6000)
        assertTrue(size.first<=3200&&size.second<=4200)
        assertEquals(6000f/9000,aspect(size),.01f)
    }
    @Test fun ocrRenderOfLongPageIsNotSquashed() {
        val size=PdfPageGeometry.renderSize(612,4000,2100,1f,3.25f,2400,3200)
        assertTrue(size.first<=2400&&size.second<=3200)
        assertEquals(4000f/612,aspect(size),.05f)
    }
    @Test fun degeneratePageStillGetsABitmap() {
        val size=viewer(0,0)
        assertTrue(size.first>=1&&size.second>=1)
    }
    @Test fun unrotateInvertsRotateClockwiseForEveryQuarterTurn() {
        val quad=listOf(.1f to .2f,.8f to .15f,.9f to .85f,.2f to .7f)
        var rotated=quad
        for(turn in 1..4) {
            rotated=CropGeometry.rotateClockwise(rotated)
            // rotateClockwise relabels corners; compare as point sets.
            val back=rotated.map{CropGeometry.unrotate(it,turn*90)}
            quad.forEach{p->assertTrue(back.any{q->kotlin.math.abs(q.first-p.first)<1e-6f&&
                kotlin.math.abs(q.second-p.second)<1e-6f})}
        }
    }
    @Test fun negativeAndFullTurnsNormalize() {
        val p=.25f to .6f
        assertEquals(CropGeometry.unrotate(p,270),CropGeometry.unrotate(p,-90))
        assertEquals(p,CropGeometry.unrotate(p,360))
    }
}
