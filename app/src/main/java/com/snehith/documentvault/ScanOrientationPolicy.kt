package com.snehith.documentvault

/** Conservative early-stop criterion so portrait documents avoid unnecessary OCR passes. */
object ScanOrientationPolicy {
    fun uprightStrong(score:Int,lineCount:Int):Boolean=score>=130&&lineCount>=5
}
