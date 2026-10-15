package com.snehith.documentvault

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/** One bundled ML Kit client for four low-resolution orientations; call from Dispatchers.IO. */
object ScanOrientation {
    fun bestRotation(bitmap:Bitmap):Int {
        val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val scores=ArrayList<Pair<Int,Int>>()
            for(degrees in listOf(0,90,180,270)){
                val rotated=if(degrees==0)bitmap else
                    Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,
                        Matrix().apply{postRotate(degrees.toFloat())},true)
                try {
                    val text=Tasks.await(recognizer.process(InputImage.fromBitmap(rotated,0)),
                        25,TimeUnit.SECONDS)
                    // Lines and recognised letters are less sensitive to OCR noise than raw length.
                    val score=text.textBlocks.sumOf{block->
                        block.lines.sumOf{line->
                            4+line.text.count{it.isLetterOrDigit()}
                        }
                    }
                    scores.add(degrees to score)
                }catch(_:Exception){scores.add(degrees to 0)}
                finally{if(rotated!==bitmap&&!rotated.isRecycled)rotated.recycle()}
            }
            val top=scores.maxByOrNull{it.second}?:return 0
            // When almost no text is found, don't rotate the photo speculatively.
            if(top.second<18)return 0
            val upright=scores.first().second
            if(top.first!=0 && top.second-upright<6)return 0
            top.first
        }finally{recognizer.close()}
    }
}
