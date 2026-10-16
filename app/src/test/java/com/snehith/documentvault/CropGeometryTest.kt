package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Test

class CropGeometryTest {
    @Test fun rotationKeepsCanonicalCornerOrder() {
        val points=listOf(.1f to .2f,.8f to .2f,.8f to .9f,.1f to .9f)
        val rotated=CropGeometry.rotateClockwise(points)
        assertEquals(4,rotated.size)
        assertEquals(.1f,rotated[0].first,.0001f)
        assertEquals(.1f,rotated[0].second,.0001f)
        assertEquals(.8f,rotated[1].second,.0001f)
        var four=points
        repeat(4){four=CropGeometry.rotateClockwise(four)}
        four.zip(points).forEach{(a,b)->
            assertEquals(b.first,a.first,.0001f)
            assertEquals(b.second,a.second,.0001f)
        }
    }
    @Test fun encodedManualCornersSurviveReload() {
        val manual=listOf(.17f to .05f,.92f to .12f,.85f to .89f,.09f to .91f)
        assertEquals(manual,CropGeometry.decode(CropGeometry.encode(manual)))
    }
    @Test fun malformedOrNonFiniteCoordinatesRejected(){
        assertNull(CropGeometry.decode("0,0;1,0;2,2;0,1"))
        assertNull(CropGeometry.decode("NaN,0;1,0;1,1;0,1"))
        assertNull(CropGeometry.decode("0,0;1,0;0,1"))
    }
}
