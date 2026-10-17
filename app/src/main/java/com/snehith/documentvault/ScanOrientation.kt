package com.snehith.documentvault

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** OCR only the 800px orientation preview; call from Dispatchers.IO. */
object ScanOrientation {
    fun bestRotation(bitmap:Bitmap):Int {
        val small=if(maxOf(bitmap.width,bitmap.height)>800) {
            val factor=800f/maxOf(bitmap.width,bitmap.height)
            Bitmap.createScaledBitmap(bitmap,
                (bitmap.width*factor).toInt().coerceAtLeast(1),
                (bitmap.height*factor).toInt().coerceAtLeast(1),true)
        } else bitmap
        val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try{
            val scores=ArrayList<Pair<Int,Int>>()
            for(degrees in listOf(0,90,180,270)){
                val rotated=if(degrees==0)small else
                    Bitmap.createBitmap(small,0,0,small.width,small.height,
                        Matrix().apply{postRotate(degrees.toFloat())},true)
                try{
                    val text=Tasks.await(recognizer.process(InputImage.fromBitmap(rotated,0)),
                        18,TimeUnit.SECONDS)
                    val count=text.textBlocks.sumOf{block->
                        block.lines.sumOf{line->4+line.text.count{it.isLetterOrDigit()}}
                    }
                    val lines=text.textBlocks.sumOf{it.lines.size}
                    scores.add(degrees to count)
                    // Strong upright text has enough confidence to stop the other three passes.
                    if(degrees==0&&ScanOrientationPolicy.uprightStrong(count,lines))return 0
                }catch(cancelled:CancellationException){throw cancelled}
                catch(_:Exception){scores.add(degrees to 0)}
                finally{if(rotated!==small&&!rotated.isRecycled)rotated.recycle()}
            }
            val best=scores.maxByOrNull{it.second}?:return 0
            if(best.second<18)return 0
            if(best.first!=0&&best.second-scores.first().second<6)return 0
            best.first
        }finally{
            recognizer.close()
            if(small!==bitmap&&!small.isRecycled)small.recycle()
        }
    }
}
