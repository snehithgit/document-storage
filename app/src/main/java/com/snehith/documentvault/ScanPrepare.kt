package com.snehith.documentvault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.File

data class PreparedScan(val preview:Bitmap,val corners:DetectedScan,val rotation:Int)

object ScanPrepare {
    /** Expensive processing: never call on the main thread. */
    /**
     * Reopening an already processed image must NOT auto-detect a new crop.
     * Restore the persisted corners against the retained raw image where possible.
     */
    fun loadForEdit(file:File,corners:String,rotation:Int):PreparedScan {
        val decoded=ImageDecode.decode(file,1600)
        val normalized=((rotation%360)+360)%360
        val oriented=if(normalized==0)decoded else {
            try {
                Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,
                    Matrix().apply{postRotate(normalized.toFloat())},true)
            }finally{decoded.recycle()}
        }
        val values=CropGeometry.decode(corners)?:CropGeometry.fullPage
        return PreparedScan(oriented,DetectedScan(values.map{android.graphics.PointF(it.first,it.second)},false),normalized)
    }
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
