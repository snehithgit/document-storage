package com.snehith.documentvault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.File

data class PreparedScan(val preview:Bitmap,val corners:DetectedScan,val rotation:Int)

object ScanPrepare {
    /** Expensive processing: never call on the main thread. */
    fun load(context:Context,file:File):PreparedScan {
        val decoded=ImageDecode.decode(file,1600)
        var oriented:Bitmap=decoded
        var transferred=false
        try {
            val rotation=ScanOrientation.bestRotation(decoded)
            if(rotation!=0){
                oriented=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,
                    Matrix().apply{postRotate(rotation.toFloat())},true)
            }
            val corners=DocumentCropEngine.detect(oriented,context)
            transferred=true
            return PreparedScan(oriented,corners,rotation)
        }finally {
            if(oriented!==decoded&&!decoded.isRecycled)decoded.recycle()
            if(!transferred&&!oriented.isRecycled)oriented.recycle()
        }
    }
}
