package com.snehith.documentvault

import kotlin.math.*

/** Normalized TL/TR/BR/BL corner validation; rejects collapsed or crossing selections. */
object ScanQuadGeometry {
    fun valid(points:List<Pair<Float,Float>>,minimumArea:Double=0.018):Boolean{
        if(points.size!=4 || points.any{!it.first.isFinite()||!it.second.isFinite()||
                    it.first !in 0f..1f||it.second !in 0f..1f})return false
        var signedArea=0.0
        var direction=0.0
        for(i in 0..3){
            val a=points[i];val b=points[(i+1)%4];val c=points[(i+2)%4]
            signedArea+=a.first*b.second-b.first*a.second
            val cross=(b.first-a.first)*(c.second-b.second)-
                (b.second-a.second)*(c.first-b.first)
            if(abs(cross)<1e-5)return false
            if(direction!=0.0 && cross.toDouble()*direction<=0)return false
            direction=cross.toDouble()
            if(hypot((b.first-a.first).toDouble(),(b.second-a.second).toDouble())<.045)
                return false
        }
        return abs(signedArea)*.5>=minimumArea
    }
    /** Centroid-angle canonicalization from MakeACopy DocQuadPostprocessor V1 (Apache-2.0),
     * pinned source cdf0e66833e5a918b90643692e2894a25262f284. */
    fun canonicalize(quad:List<Pair<Double,Double>>):List<Pair<Double,Double>>{
        require(quad.size==4)
        val cx=quad.map{it.first}.average()
        val cy=quad.map{it.second}.average()
        val ordered=quad.withIndex().sortedWith(compareBy<IndexedValue<Pair<Double,Double>>>{
            atan2(it.value.second-cy,it.value.first-cx)
        }.thenBy{it.index}).map{it.value}
        val start=ordered.indices.minByOrNull{ordered[it].first+ordered[it].second}?:0
        return List(4){ordered[(start+it)%4]}
    }
    fun rotatedSize(width:Int,height:Int,degrees:Int):Pair<Int,Int>{
        val r=Math.toRadians(degrees.toDouble())
        val c=abs(cos(r));val s=abs(sin(r))
        return ceil(width*c+height*s-1e-9).toInt().coerceAtLeast(1) to
            ceil(width*s+height*c-1e-9).toInt().coerceAtLeast(1)
    }
    fun backTransform(
        quad:List<Pair<Double,Double>>,degrees:Int,
        width:Int,height:Int,rotatedWidth:Int,rotatedHeight:Int
    ):List<Pair<Double,Double>>{
        val angle=Math.toRadians(degrees.toDouble())
        val c=cos(angle);val s=sin(angle)
        return canonicalize(quad.map{point->
            val dx=point.first-rotatedWidth/2.0
            val dy=point.second-rotatedHeight/2.0
            c*dx+s*dy+width/2.0 to -s*dx+c*dy+height/2.0
        })
    }
}
