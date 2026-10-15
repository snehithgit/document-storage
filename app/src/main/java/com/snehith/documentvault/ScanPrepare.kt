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
        try {
            val rotation=ScanOrientation.bestRotation(decoded)
            val oriented=if(rotation==0)decoded else
                Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,
                    Matrix().apply{postRotate(rotation.toFloat())},true)
            try {
                val corners=DocumentCropEngine.detect(oriented,context)
                return PreparedScan(oriented,corners,rotation)
            }catch(e:Exception) {
                if(oriented!==decoded&&!oriented.isRecycled)oriented.recycle()
                throw e
            }
        }finally {
            // When orientation did not change, ownership transfers to PreparedScan.
            // A rotated bitmap owns its own pixel buffer.
            // A failure in detect() releases the rotated copy in catch above.
        }
    }
}
