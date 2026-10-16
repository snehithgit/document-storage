package com.snehith.documentvault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class PreparedScan(val preview:Bitmap,val corners:DetectedScan,val rotation:Int)

object ScanPrepare {
    /** Re-edit preserves existing corners; no new automatic detection. */
    fun loadForEdit(file:File,corners:String,rotation:Int):PreparedScan {
        val decoded=ImageDecode.decode(file,1600,highQuality=true)
        val normalized=((rotation%360)+360)%360
        val oriented=if(normalized==0)decoded else {
            try{
                Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,
                    Matrix().apply{postRotate(normalized.toFloat())},true)
            }finally{decoded.recycle()}
        }
        val values=CropGeometry.decode(corners)?:CropGeometry.fullPage
        return PreparedScan(oriented,DetectedScan(values.map{PointF(it.first,it.second)},false),
            normalized)
    }
    /** Run OCR orientation and document border detection concurrently, off the UI thread. */
    suspend fun load(context:Context,file:File):PreparedScan = coroutineScope {
        val decoded=ImageDecode.decode(file,1600,highQuality=true)
        var oriented:Bitmap=decoded
        var transferred=false
        try{
            val orientation=async(Dispatchers.IO){ScanOrientation.bestRotation(decoded)}
            val corners=async(Dispatchers.Default){DocumentCropEngine.detect(decoded,context)}
            val rotation=orientation.await()
            val base=corners.await()
            if(rotation!=0){
                oriented=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,
                    Matrix().apply{postRotate(rotation.toFloat())},true)
            }
            var quad=base.corners.map{it.x to it.y}
            repeat(((rotation%360)+360)%360/90){quad=CropGeometry.rotateClockwise(quad)}
            transferred=true
            PreparedScan(oriented,DetectedScan(quad.map{PointF(it.first,it.second)},
                base.automatic),rotation)
        }finally{
            if(oriented!==decoded&&!decoded.isRecycled)decoded.recycle()
            if(!transferred&&!oriented.isRecycled)oriented.recycle()
        }
    }
}
