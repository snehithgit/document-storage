package com.snehith.documentvault

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import com.docuscan.app.scan.Cleanup
import kotlin.math.max

/** Non-destructive scanner filters. Must run on a worker dispatcher. */
data class ScanSettings(
    val preset:String="Original",
    val brightness:Int=0,
    val contrast:Float=1f,
    val sharpness:Float=0f,
    val removeShadows:Boolean=false
)

/** Small pure helper used by the native path without ever changing alpha. */
object ScanColorMath {
    fun adjust(channel:Int,contrast:Float,brightness:Int):Int =
        (channel*contrast+brightness).toInt().coerceIn(0,255)
    fun sharpnessSigma(width:Int,height:Int):Double =
        (1.2*max(width,height).toDouble()/1200.0).coerceIn(0.45,2.6)
}

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
        val rgba=Mat()
        val rgb=Mat()
        val lab=Mat()
        val gray=Mat()
        val temp=Mat()
        val blurred=Mat()
        try {
            Utils.bitmapToMat(initial,rgba)
            // Always process only RGB. convertTo on RGBA silently scales the alpha
            // channel as well, producing dark/transparent output on lower contrast.
            Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB)
            val monochrome=settings.preset=="Grayscale"||settings.preset=="B&W"
            if(settings.removeShadows){
                if(monochrome){
                    Imgproc.cvtColor(rgb,gray,Imgproc.COLOR_RGB2GRAY)
                    removeBackground(gray,gray)
                    Imgproc.cvtColor(gray,rgb,Imgproc.COLOR_GRAY2RGB)
                }else{
                    // Lab luminance correction retains the original colour channels.
                    Imgproc.cvtColor(rgb,lab,Imgproc.COLOR_RGB2Lab)
                    val channels=ArrayList<Mat>(3)
                    try{
                        Core.split(lab,channels)
                        removeBackground(channels[0],channels[0])
                        Core.merge(channels,lab)
                    }finally{channels.forEach{it.release()}}
                    Imgproc.cvtColor(lab,rgb,Imgproc.COLOR_Lab2RGB)
                }
            }
            if(monochrome){
                Imgproc.cvtColor(rgb,gray,Imgproc.COLOR_RGB2GRAY)
                if(settings.preset=="B&W"){
                    Imgproc.adaptiveThreshold(gray,temp,255.0,
                        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,41,11.0)
                    Imgproc.cvtColor(temp,rgb,Imgproc.COLOR_GRAY2RGB)
                }else Imgproc.cvtColor(gray,rgb,Imgproc.COLOR_GRAY2RGB)
            }
            if(settings.contrast!=1f||settings.brightness!=0)
                rgb.convertTo(rgb,-1,settings.contrast.toDouble(),settings.brightness.toDouble())
            if(settings.sharpness>0f){
                Imgproc.GaussianBlur(rgb,blurred,Size(0.0,0.0),
                    ScanColorMath.sharpnessSigma(rgb.cols(),rgb.rows()))
                Core.addWeighted(rgb,1.0+settings.sharpness,blurred,
                    -settings.sharpness.toDouble(),0.0,rgb)
            }
            Imgproc.cvtColor(rgb,rgba,Imgproc.COLOR_RGB2RGBA) // opaque alpha 255
            val output=Bitmap.createBitmap(rgba.cols(),rgba.rows(),Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba,output)
            return output
        }finally {
            rgba.release();rgb.release();lab.release();gray.release();temp.release();blurred.release()
            if(initial!==source&&!initial.isRecycled)initial.recycle()
        }
    }

    /** Same background-divide method as Cleanup, applied to gray or Lab L only. */
    private fun removeBackground(gray:Mat,result:Mat){
        val smaller=minOf(gray.cols(),gray.rows())
        var kernel=max(15,(smaller*Cleanup.KERNEL_FRACTION_BW).toInt())
        if(kernel%2==0)kernel++
        val bg=Mat();val foreground=Mat();val background=Mat();val divided=Mat()
        try{
            Imgproc.GaussianBlur(gray,bg,Size(kernel.toDouble(),kernel.toDouble()),0.0)
            gray.convertTo(foreground,CvType.CV_32F)
            bg.convertTo(background,CvType.CV_32F)
            Core.max(background,Scalar(1.0),background)
            Core.divide(foreground,background,divided)
            Core.multiply(divided,Scalar(255.0),divided)
            divided.convertTo(result,CvType.CV_8U)
        }finally{
            bg.release();foreground.release();background.release();divided.release()
        }
    }
}
