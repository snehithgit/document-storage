package com.snehith.documentvault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException

/** The single EXIF-aware path for thumbnails, viewer, OCR, PDF and scanner. */
object ImageDecode {
    fun decode(file:File,maxEdge:Int,fullResolution:Boolean=false):Bitmap {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(file.absolutePath,bounds)
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw IOException("Unable to decode image: "+file.name)
        val pixels=bounds.outWidth.toLong()*bounds.outHeight.toLong()
        if(pixels>32_000_000L && fullResolution)
            throw IOException("Image is too large to crop safely on this device")
        val sample=if(fullResolution)1 else ImageSampling.sample(bounds.outWidth,bounds.outHeight,maxEdge)
        val decoded=BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply {
            inSampleSize=sample
            inPreferredConfig=Bitmap.Config.RGB_565
        })?:throw IOException("Could not load image: "+file.name)
        val orientation=try { ExifInterface(file).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
        }catch(_:Exception){ExifInterface.ORIENTATION_NORMAL}
        val matrix=Matrix()
        when(orientation) {
            ExifInterface.ORIENTATION_ROTATE_90->matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180->matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270->matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL->matrix.postScale(-1f,1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL->matrix.postScale(1f,-1f)
            ExifInterface.ORIENTATION_TRANSPOSE->{matrix.postScale(-1f,1f);matrix.postRotate(270f)}
            ExifInterface.ORIENTATION_TRANSVERSE->{matrix.postScale(-1f,1f);matrix.postRotate(90f)}
        }
        if(matrix.isIdentity)return decoded
        return try{
            Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)
        }finally{decoded.recycle()}
    }
}
