package com.snehith.documentvault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
 * DocQuadNet-256 corner detector.
 * Model and preprocessing adapted from egdels/makeacopy (Apache-2.0).
 * All inference runs on-device. Caller MUST use Dispatchers.Default/IO.
 */
object DocQuadModel {
    private const val MODEL="docquad/docquadnet256_trained_opset17.ort"
    private val env by lazy { OrtEnvironment.getEnvironment() }
    @Volatile private var session:OrtSession?=null
    private val lock=Any()

    private fun open(context:Context):OrtSession {
        session?.let{return it}
        synchronized(lock){
            session?.let{return it}
            val bytes=context.assets.open(MODEL).use{it.readBytes()}
            val options=OrtSession.SessionOptions().apply{
                setIntraOpNumThreads(2)
            }
            return try {
                env.createSession(bytes,options).also{session=it}
            }finally{options.close()}
        }
    }

    fun detect(context:Context,bitmap:Bitmap):List<PointF>? = synchronized(lock) {
        if(bitmap.width<64||bitmap.height<64)return@synchronized null
        val scale=min(256f/bitmap.width,256f/bitmap.height)
        val outW=(bitmap.width*scale).roundToInt().coerceIn(1,256)
        val outH=(bitmap.height*scale).roundToInt().coerceIn(1,256)
        val offX=(256-outW)/2f
        val offY=(256-outH)/2f
        val resized=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888)
        try {
            resized.eraseColor(AndroidColor.BLACK)
            val canvas=Canvas(resized)
            val paint=Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(bitmap,null,RectF(offX,offY,offX+outW,offY+outH),paint)
            val pixels=IntArray(256*256)
            resized.getPixels(pixels,0,256,0,0,256,256)
            val tensor=FloatArray(3*256*256)
            for(i in pixels.indices){
                tensor[i]=AndroidColor.red(pixels[i])/255f
                tensor[65536+i]=AndroidColor.green(pixels[i])/255f
                tensor[131072+i]=AndroidColor.blue(pixels[i])/255f
            }
            val model=open(context)
            OnnxTensor.createTensor(env,FloatBuffer.wrap(tensor),longArrayOf(1,3,256,256)).use{input->
                model.run(mapOf("input" to input)).use{result->
                    @Suppress("UNCHECKED_CAST")
                    val heatmaps=result.get("corner_heatmaps").orElse(null)?.value
                        as? Array<Array<Array<FloatArray>>> ?: return@synchronized null
                    if(heatmaps.size!=1 || heatmaps[0].size!=4)return@synchronized null
                    val corners=ArrayList<PointF>(4)
                    for(channel in 0..3){
                        val plane=heatmaps[0][channel]
                        if(plane.size!=64)return@synchronized null
                        var best=Float.NEGATIVE_INFINITY
                        var row=0;var col=0
                        for(y in 0..63)for(x in 0..63){
                            val v=plane[y][x]
                            if(v>best){best=v;row=y;col=x}
                        }
                        val probability=1f/(1f+exp(-best))
                        if(probability<0.28f || !probability.isFinite())return@synchronized null
                        val px=((col+.5f)*4f-offX)/scale
                        val py=((row+.5f)*4f-offY)/scale
                        corners.add(PointF((px/bitmap.width).coerceIn(0f,1f),
                            (py/bitmap.height).coerceIn(0f,1f)))
                    }
                    if(!validQuad(corners))return@synchronized null
                    return@synchronized corners
                }
            }
        }finally{resized.recycle()}
    }

    private fun validQuad(p:List<PointF>):Boolean {
        if(p.size!=4)return false
        var area=0.0
        var previous=0.0
        for(i in 0..3){
            val a=p[i];val b=p[(i+1)%4];val c=p[(i+2)%4]
            area+=(a.x*b.y-b.x*a.y)
            val cross=((b.x-a.x)*(c.y-b.y)-(b.y-a.y)*(c.x-b.x)).toDouble()
            if(abs(cross)<.0001)return false
            if(previous!=0.0 && cross*previous<=0)return false
            previous=cross
        }
        return abs(area)*.5>=.07
    }
}
