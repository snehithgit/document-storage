package com.snehith.documentvault

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import com.docuscan.app.scan.Cleanup

/** Non-destructive scanner filter parameters; processed off the main thread. */
data class ScanSettings(
    val preset:String="Original",
    val brightness:Int=0,
    val contrast:Float=1f,
    val sharpness:Float=0f,
    val removeShadows:Boolean=false
)

object ScanEnhance {
    val presets=listOf("Original","Auto","Magic Color","Clean Text","Grayscale","B&W")
    fun apply(source:Bitmap,settings:ScanSettings):Bitmap {
        if(!OpenCVLoader.initLocal())return source.copy(Bitmap.Config.ARGB_8888,true)
        val initial=when(settings.preset){
            "Auto"->Cleanup.apply(source,"natural")
            "Magic Color"->Cleanup.apply(source,"enhanced")
            "Clean Text"->Cleanup.apply(source,"cleantext")
            else->source
        }
        val rgba=Mat();val gray=Mat();val temp=Mat();val blurred=Mat()
        try {
            Utils.bitmapToMat(initial,rgba)
            if(settings.preset=="Grayscale"||settings.preset=="B&W"||settings.removeShadows){
                Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY)
                if(settings.removeShadows) {
                    val clahe=Imgproc.createCLAHE(2.0,Size(9.0,9.0))
                    clahe.apply(gray,gray)
                    clahe.collectGarbage()
                }
                if(settings.preset=="B&W"){
                    Imgproc.adaptiveThreshold(gray,temp,255.0,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                        Imgproc.THRESH_BINARY,41,11.0)
                    Imgproc.cvtColor(temp,rgba,Imgproc.COLOR_GRAY2RGBA)
                }else{
                    Imgproc.cvtColor(gray,rgba,Imgproc.COLOR_GRAY2RGBA)
                }
            }
            if(settings.contrast!=1f||settings.brightness!=0){
                rgba.convertTo(rgba,-1,settings.contrast.toDouble(),settings.brightness.toDouble())
            }
            if(settings.sharpness>0f){
                Imgproc.GaussianBlur(rgba,blurred,Size(0.0,0.0),1.2)
                Core.addWeighted(rgba,1.0+settings.sharpness,blurred,-settings.sharpness.toDouble(),0.0,rgba)
            }
            val output=Bitmap.createBitmap(rgba.cols(),rgba.rows(),Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba,output)
            return output
        }finally {
            rgba.release();gray.release();temp.release();blurred.release()
            if(initial!==source&&!initial.isRecycled)initial.recycle()
        }
    }
}
