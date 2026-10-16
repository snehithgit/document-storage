package com.snehith.documentvault

import kotlin.math.*

/**
 * Kotlin postprocessing of the DocQuad corner/mask heads, following MakeACopy
 * DocQuadPostprocessor.canonicalizeQuadOrderV1, quadFromMask256 and
 * cornerMaskIou (Apache-2.0; pinned cdf0e66833e5a918b90643692e2894a25262f284).
 * Model space is 256x256, output heatmaps are 64x64. This code performs no inference.
 */
internal object DocQuadPostprocess {
    data class Result(val corners:List<Pair<Double,Double>>,val confidence:Double,
        val cornerProbability:Double,val agreement:Double,val fromMask:Boolean)

    private fun valid256(p:List<Pair<Double,Double>>):Boolean{
        if(p.size!=4 || p.any{!it.first.isFinite()||!it.second.isFinite()||
                    it.first < -3||it.second < -3||it.first > 259||it.second > 259})return false
        return ScanQuadGeometry.valid(p.map{(x,y)->(x/256).toFloat() to (y/256).toFloat()},.025)
    }
    fun postprocess(heatmaps:Array<Array<FloatArray>>,mask:Array<FloatArray>?):Result?{
        if(heatmaps.size!=4 ||heatmaps.any{it.size!=64 || it.any{row->row.size!=64}})return null
        val raw=ArrayList<Pair<Double,Double>>(4)
        var peakMin=1.0
        for(channel in heatmaps){
            var best=Float.NEGATIVE_INFINITY
            var yy=0;var xx=0
            for(y in 0..63)for(x in 0..63)if(channel[y][x]>best){
                best=channel[y][x];yy=y;xx=x
            }
            val prob=1.0/(1.0+exp(-best.toDouble()))
            if(!prob.isFinite())return null
            peakMin=min(peakMin,prob)
            raw.add((xx+.5)*4.0 to (yy+.5)*4.0)
        }
        val cornerQuad=ScanQuadGeometry.canonicalize(raw)
        val maskQuad=mask?.let(::quadFromMask)
        val cornerValid=valid256(cornerQuad)
        val maskValid=maskQuad!=null&&valid256(maskQuad)
        // Preserve reliable corner order; use mask only if the corners are malformed
        // or clearly weak and the mask has a geometrically plausible page.
        val chooseMask=maskValid&&(!cornerValid ||
            (peakMin<.35 && maskQuad!=null && largestDistance(cornerQuad,maskQuad)<32))
        if(!cornerValid&&!chooseMask)return null
        val chosen=if(chooseMask)maskQuad!! else cornerQuad
        val overlap=mask?.let{cornerMaskIou(chosen,it)}?:1.0
        // MakeACopy uses peak confidence × corner/mask agreement for rotating passes.
        val confidence=peakMin*overlap
        return Result(chosen,confidence,peakMin,overlap,chooseMask)
    }
    private fun largestDistance(a:List<Pair<Double,Double>>,b:List<Pair<Double,Double>>):Double =
        a.indices.maxOf{hypot(a[it].first-b[it].first,a[it].second-b[it].second)}

    /** Oriented bounding box of the mask foreground via covariance principal axes. */
    fun quadFromMask(mask:Array<FloatArray>):List<Pair<Double,Double>>?{
        if(mask.size!=64||mask.any{it.size!=64})return null
        val points=ArrayList<Pair<Double,Double>>()
        for(y in 0..63)for(x in 0..63)if(mask[y][x]>0f)
            points.add(x+.5 to y+.5)
        if(points.size<100)return null
        val cx=points.map{it.first}.average();val cy=points.map{it.second}.average()
        val sxx=points.sumOf{(it.first-cx)*(it.first-cx)}/points.size
        val syy=points.sumOf{(it.second-cy)*(it.second-cy)}/points.size
        val sxy=points.sumOf{(it.first-cx)*(it.second-cy)}/points.size
        val trace=sxx+syy; val det=sxx*syy-sxy*sxy
        val lambda=trace/2+sqrt(max(0.0,trace*trace/4-det))
        val rawAxis=if(abs(sxy)>1e-12)lambda-syy to sxy
            else if(sxx>=syy)1.0 to 0.0 else 0.0 to 1.0
        val norm=hypot(rawAxis.first,rawAxis.second)
        if(norm<1e-9)return null
        val ax=rawAxis.first/norm;val ay=rawAxis.second/norm
        val bx=-ay;val by=ax
        val projection=points.map{(x,y)->
            (x-cx)*ax+(y-cy)*ay to (x-cx)*bx+(y-cy)*by
        }
        val u0=projection.minOf{it.first};val u1=projection.maxOf{it.first}
        val v0=projection.minOf{it.second};val v1=projection.maxOf{it.second}
        if(u1-u0<1 || v1-v0<1)return null
        val corners=listOf(u1 to v1,u0 to v1,u0 to v0,u1 to v0)
            .map{(u,v)->(cx+u*ax+v*bx)*4 to (cy+u*ay+v*by)*4}
        return ScanQuadGeometry.canonicalize(corners)
    }

    /** Scanline point-in-polygon IoU, same even/odd cell-centre policy as MakeACopy. */
    fun cornerMaskIou(quad:List<Pair<Double,Double>>,mask:Array<FloatArray>):Double {
        if(quad.size!=4 || mask.size!=64 || mask.any{it.size!=64})return 0.0
        val p=quad.map{it.first/4 to it.second/4}
        var union=0;var intersection=0
        for(y in 0..63){
            val cy=y+.5
            val xs=ArrayList<Double>()
            for(i in 0..3){
                val a=p[i];val b=p[(i+1)%4]
                if((a.second>cy)!=(b.second>cy))
                    xs.add(a.first+(cy-a.second)*(b.first-a.first)/(b.second-a.second))
            }
            xs.sort()
            var k=0;var inside=false
            for(x in 0..63){
                val cx=x+.5
                while(k<xs.size && xs[k]<=cx){inside=!inside;k++}
                val inMask=mask[y][x]>0f
                if(inside&&inMask)intersection++
                if(inside||inMask)union++
            }
        }
        return if(union==0)0.0 else intersection.toDouble()/union
    }
    fun acceptsRotated(base:Double,rotated:Double):Boolean =
        rotated>=.10 && (base<0 || rotated>=base+.15)
}
