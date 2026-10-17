package com.snehith.documentvault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Offline DocQuadNet-256, adapted from MakeACopy (Apache-2.0), pinned
 * cdf0e66833e5a918b90643692e2894a25262f284.
 * Includes rotated test-time passes, mask fallback and head-agreement scoring.
 * All methods are called from non-main dispatchers.
 */
object DocQuadModel {
    private const val MODEL="docquad/docquadnet256_trained_opset17.ort"
    private val env by lazy { OrtEnvironment.getEnvironment() }
    @Volatile private var session:OrtSession?=null
    private val lock=Any()
    private val angles=intArrayOf(90,-30,30,-60,60)

    private fun open(context:Context):OrtSession {
        session?.let{return it}
        synchronized(lock){
            session?.let{return it}
            val bytes=context.assets.open(MODEL).use{it.readBytes()}
            val options=OrtSession.SessionOptions().apply{setIntraOpNumThreads(2)}
            return try{env.createSession(bytes,options).also{session=it}}
            finally{options.close()}
        }
    }
    private data class Candidate(val corners:List<PointF>,val confidence:Double)

    private fun infer(context:Context,bitmap:Bitmap):Candidate? = synchronized(lock) {
        if(bitmap.width<64||bitmap.height<64)return@synchronized null
        val scale=min(256f/bitmap.width,256f/bitmap.height)
        val w=(bitmap.width*scale).roundToInt().coerceIn(1,256)
        val h=(bitmap.height*scale).roundToInt().coerceIn(1,256)
        val dx=(256-w)/2f;val dy=(256-h)/2f
        val letterboxed=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888)
        try {
            letterboxed.eraseColor(AndroidColor.BLACK)
            Canvas(letterboxed).drawBitmap(bitmap,null,RectF(dx,dy,dx+w,dy+h),
                Paint(Paint.FILTER_BITMAP_FLAG))
            val pixels=IntArray(65536)
            letterboxed.getPixels(pixels,0,256,0,0,256,256)
            val input=FloatArray(3*65536)
            for(i in pixels.indices){
                input[i]=AndroidColor.red(pixels[i])/255f
                input[65536+i]=AndroidColor.green(pixels[i])/255f
                input[131072+i]=AndroidColor.blue(pixels[i])/255f
            }
            OnnxTensor.createTensor(env,FloatBuffer.wrap(input),longArrayOf(1,3,256,256)).use{tensor->
                open(context).run(mapOf("input" to tensor)).use{result->
                    @Suppress("UNCHECKED_CAST")
                    val cornerMaps=result.get("corner_heatmaps").orElse(null)?.value
                        as? Array<Array<Array<FloatArray>>> ?:return@synchronized null
                    @Suppress("UNCHECKED_CAST")
                    val masks=result.get("mask_logits").orElse(null)?.value
                        as? Array<Array<Array<FloatArray>>>
                    if(cornerMaps.size!=1||cornerMaps[0].size!=4)return@synchronized null
                    val detection=DocQuadPostprocess.postprocess(cornerMaps[0],masks?.getOrNull(0)?.getOrNull(0))
                        ?:return@synchronized null
                    val points=detection.corners.map{(x,y)->
                        PointF(((x-dx)/scale/bitmap.width).toFloat(),
                            ((y-dy)/scale/bitmap.height).toFloat())
                    }
                    if(!ScanQuadGeometry.valid(points.map{it.x to it.y},.025))return@synchronized null
                    return@synchronized Candidate(points,detection.confidence)
                }
            }
        }finally{letterboxed.recycle()}
    }

    fun detect(context:Context,bitmap:Bitmap):List<PointF>? {
        val first=infer(context,bitmap)
        if(first!=null&&first.confidence>=.50)return first.corners
        var best=first
        val originalConfidence=first?.confidence?:-1.0
        val scale=min(1f,720f/max(bitmap.width,bitmap.height))
        val small=if(scale<1f)Bitmap.createScaledBitmap(bitmap,
            (bitmap.width*scale).roundToInt().coerceAtLeast(64),
            (bitmap.height*scale).roundToInt().coerceAtLeast(64),true) else bitmap
        try {
            for(angle in angles){
                val (rotW,rotH)=ScanQuadGeometry.rotatedSize(small.width,small.height,angle)
                val rotated=Bitmap.createBitmap(rotW,rotH,Bitmap.Config.ARGB_8888)
                val candidate=try{
                    val canvas=Canvas(rotated)
                    canvas.drawColor(AndroidColor.BLACK)
                    val matrix=Matrix().apply{
                        setRotate(angle.toFloat(),small.width/2f,small.height/2f)
                        postTranslate((rotW-small.width)/2f,(rotH-small.height)/2f)
                    }
                    canvas.drawBitmap(small,matrix,Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                    infer(context,rotated)
                }finally{rotated.recycle()}
                if(candidate!=null){
                    val pixels=candidate.corners.map{it.x*rotW.toDouble() to it.y*rotH.toDouble()}
                    val restored=ScanQuadGeometry.backTransform(pixels,angle,
                        small.width,small.height,rotW,rotH)
                    val normalized=restored.map{(x,y)->
                        PointF((x/small.width).toFloat(),(y/small.height).toFloat())
                    }
                    if(ScanQuadGeometry.valid(normalized.map{it.x to it.y},.025)&&
                        candidate.confidence>(best?.confidence?:-1.0)){
                        best=Candidate(normalized,candidate.confidence)
                    }
                    if((best?.confidence?:0.0)>=.70)break
                }
            }
        }finally{if(small!==bitmap)small.recycle()}
        return if(first==null)best?.corners
        else if(best!==first && best!=null &&
            DocQuadPostprocess.acceptsRotated(originalConfidence,best.confidence))best.corners
        else first.corners
    }
}
