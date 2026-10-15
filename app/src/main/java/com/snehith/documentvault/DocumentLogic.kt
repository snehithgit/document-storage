package com.snehith.documentvault

/** Pure rules for field labels, PDF export, and regression tests. */
object VaultCounts {
    fun format(count:Int,singular:String,plural:String=singular+"s"):String =
        "$count ${if(count==1)singular else plural}"
}
object VaultNames {
    private val uuid=Regex("(?i)(scan|capture|img)[-_]?[a-f0-9]{8}[-a-f0-9]{12,}")
    fun title(filename:String):String {
        val cleaned=filename.substringAfterLast('/').substringBeforeLast('.',filename)
        return if(uuid.matches(cleaned))"Scanned document" else cleaned.ifBlank{"Document"}
    }
    fun label(filename:String):String {
        val suffix=filename.substringAfterLast('.', "").takeIf{it.length in 2..5}?.let{"."+it}?:""
        return title(filename)+suffix
    }
}
object PdfPageTexts {
    private val marker=Regex("(?m)^--- PAGE (\\d+) ---\\s*$")
    /** Empty list means text has no markers, so never duplicate it over many pages. */
    fun parse(input:String,pageCount:Int):List<String> {
        if(pageCount<=0)return emptyList()
        val matches=marker.findAll(input).toList()
        if(matches.isEmpty())return if(pageCount==1)listOf(input.trim())else emptyList()
        val result=MutableList(pageCount){""}
        for((i,m) in matches.withIndex()) {
            val index=(m.groupValues[1].toIntOrNull()?:continue)-1
            if(index !in result.indices)continue
            val until=matches.getOrNull(i+1)?.range?.first?:input.length
            result[index]=input.substring(m.range.last+1,until).trim()
        }
        return result
    }
}
object PdfPageGeometry {
    /** Page dimensions in PDF points. */
    fun fitA4(imageWidth:Int,imageHeight:Int):FloatArray {
        require(imageWidth>0&&imageHeight>0)
        val pageW=595f;val pageH=842f
        val scale=minOf((pageW-30f)/imageWidth,(pageH-30f)/imageHeight)
        val w=imageWidth*scale;val h=imageHeight*scale
        return floatArrayOf(pageW,pageH,(pageW-w)/2,(pageH-h)/2,w,h)
    }
}
object ImageSampling {
    fun sample(width:Int,height:Int,maxEdge:Int):Int {
        require(maxEdge>0)
        var sample=1
        while(maxOf(width,height)/sample>maxEdge && sample<(1 shl 29))sample*=2
        return sample
    }
}
