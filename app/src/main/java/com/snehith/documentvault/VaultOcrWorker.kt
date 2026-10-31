package com.snehith.documentvault

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One process-local repository keeps WorkManager and Compose writes consistent. */
object VaultStorage {
    @Volatile private var instance:VaultRepository?=null
    fun get(context:Context):VaultRepository =
        instance ?: synchronized(this) {
            instance ?: VaultRepository(context.applicationContext).also{instance=it}
        }
}

/** On-device OCR. WorkManager persists the run ID/progress through process recreation. */
class VaultOcrWorker(appContext:Context,params:WorkerParameters):CoroutineWorker(appContext,params) {
    companion object {
        const val UNIQUE="document-vault-ocr"
        const val KEY_CARD="cardId"
        const val KEY_FILE="fileId"
        const val KEY_SCRIPT="language"
        const val KEY_PAGE="page"
        const val KEY_TOTAL="total"
        const val KEY_ERROR="error"
        const val KEY_FILLED="filled"
    }
    override suspend fun doWork():Result {
        val cardId=inputData.getString(KEY_CARD)?:return Result.success(workDataOf(KEY_ERROR to "Card ID missing"))
        val fileId=inputData.getString(KEY_FILE)?:return Result.success(workDataOf(KEY_ERROR to "Attachment ID missing"))
        val script=inputData.getString(KEY_SCRIPT)?:"Latin"
        return try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
            val repo=VaultStorage.get(applicationContext)
            val source=repo.cards.firstOrNull{it.id==cardId}
                ?:return Result.success(workDataOf(KEY_ERROR to "Card not found"))
            val file=source.files.firstOrNull{it.id==fileId}
                ?:return Result.success(workDataOf(KEY_ERROR to "Attachment removed"))
            val result=DocumentEngine(applicationContext,repo).extract(file,script){done,total->
                if(isStopped)throw CancellationException("OCR cancelled")
                setProgressAsync(workDataOf(KEY_PAGE to done,KEY_TOTAL to total))
            }
            currentCoroutineContext().ensureActive()
            if(isStopped)throw CancellationException("OCR cancelled")
            if(result.text.isBlank())return Result.success(workDataOf(KEY_FILE to fileId,
                KEY_ERROR to "No text was recognized. Existing details were kept."))
            var filled=0
            repo.updateCard(cardId){latest->
                val merged=OcrMerge.apply(latest,fileId,result)?:latest
                filled=OcrMerge.filledCount(latest.fields,merged.fields)
                merged
            }
            setProgress(workDataOf(KEY_PAGE to result.pageCount,KEY_TOTAL to result.pageCount))
            Result.success(workDataOf(KEY_CARD to cardId, KEY_FILE to fileId,KEY_FILLED to filled))
        }catch(c:CancellationException){throw c}
        catch(e:Exception){
            // A file-level failure must not cancel subsequent items in a WorkManager chain.
            Result.success(workDataOf(KEY_FILE to inputData.getString(KEY_FILE),
                KEY_ERROR to (UserMessages.of(e,"Text could not be read from this file"))))
        }
    }
}
