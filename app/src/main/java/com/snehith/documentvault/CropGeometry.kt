package com.snehith.documentvault

/** Normalized crop geometry survives image resolution and screen orientation changes. */
object CropGeometry {
    val fullPage:List<Pair<Float,Float>> = listOf(
        0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f
    )
    fun rotateClockwise(points:List<Pair<Float,Float>>):List<Pair<Float,Float>> {
        require(points.size==4)
        fun rotate(p:Pair<Float,Float>)=(1f-p.second) to p.first
        return listOf(points[3],points[0],points[1],points[2]).map(::rotate)
    }
    /** Maps a normalized point in an image turned [degrees] clockwise back onto the
     * unturned image (inverse of [rotateClockwise]'s point mapping), keeping order. */
    fun unrotate(p:Pair<Float,Float>,degrees:Int):Pair<Float,Float> = when(((degrees%360)+360)%360) {
        90 -> p.second to 1f-p.first
        180 -> 1f-p.first to 1f-p.second
        270 -> 1f-p.second to p.first
        else -> p
    }
    fun encode(points:List<Pair<Float,Float>>):String {
        require(points.size==4 && points.all { it.first.isFinite() && it.second.isFinite()
            && it.first in 0f..1f && it.second in 0f..1f })
        return points.joinToString(";"){"${it.first},${it.second}"}
    }
    fun decode(value:String):List<Pair<Float,Float>>? = runCatching {
        val points=value.split(";").map {
            val parts=it.split(",")
            require(parts.size==2)
            parts[0].toFloat() to parts[1].toFloat()
        }
        require(points.size==4 && points.all{it.first.isFinite() && it.second.isFinite()
            && it.first in 0f..1f && it.second in 0f..1f})
        points
    }.getOrNull()
}
