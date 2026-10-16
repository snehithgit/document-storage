package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class DocQuadPostprocessTest {
    @Test fun weakOrMissingCornerHeatmapFallsBackToMask(){
        val heat=Array(4){Array(64){FloatArray(64){-9f}}}
        val mask=Array(64){y->FloatArray(64){x->if(x in 9..53 && y in 11..52)8f else -8f}}
        val chosen=DocQuadPostprocess.postprocess(heat,mask)
        assertNotNull(chosen)
        assertTrue(chosen!!.fromMask)
        assertTrue(chosen.agreement>0.70)
    }
    @Test fun confidenceRewardsAgreement(){
        val mask=Array(64){y->FloatArray(64){x->if(x in 10..54&&y in 10..54)5f else -5f}}
        val close=listOf(40.0 to 40.0,220.0 to 40.0,220.0 to 220.0,40.0 to 220.0)
        val far=listOf(4.0 to 4.0,70.0 to 4.0,70.0 to 70.0,4.0 to 70.0)
        assertTrue(DocQuadPostprocess.cornerMaskIou(close,mask)>
            DocQuadPostprocess.cornerMaskIou(far,mask))
        assertTrue(DocQuadPostprocess.acceptsRotated(-1.0,.3))
        assertFalse(DocQuadPostprocess.acceptsRotated(.5,.55))
    }
}
