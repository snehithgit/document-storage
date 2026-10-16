package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class ScanQuadGeometryTest {
    @Test fun crossingQuadRejected(){
        assertFalse(ScanQuadGeometry.valid(listOf(.1f to .1f,.9f to .9f,
            .9f to .1f,.1f to .9f)))
    }
    @Test fun tooSmallQuadRejected(){
        assertFalse(ScanQuadGeometry.valid(listOf(.1f to .1f,.13f to .1f,
            .13f to .13f,.1f to .13f)))
    }
    @Test fun regularQuadAccepted(){
        assertTrue(ScanQuadGeometry.valid(listOf(.1f to .1f,.9f to .1f,
            .9f to .9f,.1f to .9f)))
    }
    @Test fun canonicalizeAlwaysStartsTopLeft(){
        val mixed=listOf(.8 to .8,.1 to .1,.1 to .8,.8 to .1)
        assertEquals(listOf(.1 to .1,.8 to .1,.8 to .8,.1 to .8),
            ScanQuadGeometry.canonicalize(mixed))
    }
    @Test fun rotatedCandidateMapsBackToSameCorners(){
        val w=120;val h=80;val angle=90
        val size=ScanQuadGeometry.rotatedSize(w,h,angle)
        assertEquals(80 to 120,size)
        val src=listOf(10.0 to 10.0,110.0 to 10.0,110.0 to 70.0,10.0 to 70.0)
        val forward=src.map{(x,y)->(h-y) to x}
        val back=ScanQuadGeometry.backTransform(forward,angle,w,h,size.first,size.second)
        for(i in 0..3){
            assertEquals(src[i].first,back[i].first,1e-5)
            assertEquals(src[i].second,back[i].second,1e-5)
        }
    }
}
