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
    }
    override suspend fun doWork():Result {
        val cardId=inputData.getString(KEY_CARD)?:return Result.failure(workDataOf(KEY_ERROR to "Card ID missing"))
        val fileId=inputData.getString(KEY_FILE)?:return Result.failure(workDataOf(KEY_ERROR to "Attachment ID missing"))
        val script=inputData.getString(KEY_SCRIPT)?:"Latin"
        return try {
            val repo=VaultStorage.get(applicationContext)
            val source=repo.cards.firstOrNull{it.id==cardId}
                ?:return Result.failure(workDataOf(KEY_ERROR to "Card not found"))
            val file=source.files.firstOrNull{it.id==fileId}
                ?:return Result.failure(workDataOf(KEY_ERROR to "Attachment removed"))
            val result=DocumentEngine(applicationContext,repo).extract(file,script){done,total->
                if(isStopped)throw CancellationException("OCR cancelled")
                setProgressAsync(workDataOf(KEY_PAGE to done,KEY_TOTAL to total))
            }
            currentCoroutineContext().ensureActive()
            if(isStopped)throw CancellationException("OCR cancelled")
            val latest=repo.cards.firstOrNull{it.id==cardId}
                ?:return Result.failure(workDataOf(KEY_ERROR to "Card was removed"))
            if(latest.files.none{it.id==fileId})return Result.failure(workDataOf(KEY_ERROR to "Attachment was removed"))
            val smart=result.document
            val updated=file.copy(extractedText=result.text,ocrStatus=result.method)
            val titleIsGeneric=latest.title.isBlank() || latest.title.startsWith("Scan ",true) ||
                latest.title.startsWith("Scanned document",true) ||
                latest.title.equals(file.name.substringBeforeLast('.'),true)
            val fields=latest.fields.toMutableList()
            result.fields.filter{it.name.isNotBlank()&&it.value.isNotBlank()}.forEach{f->
                val existing=fields.indexOfFirst{it.name.equals(f.name,true)}
                if(existing<0)fields.add(f)
                else if(fields[existing].value.isBlank())fields[existing]=f
            }
            repo.saveCard(latest.copy(
                files=latest.files.map{if(it.id==fileId)updated else it},
                fields=fields,
                kind=smart?.kind?.takeIf{latest.kind=="Custom"||latest.kind=="Recovered"}?:latest.kind,
                title=if(titleIsGeneric&&smart!=null&&smart.confidence>=.7f&&
                    !smart.fullName.isNullOrBlank())smart.title else latest.title,
                expiry=if(latest.expiry.isBlank())smart?.expiry?:"" else latest.expiry
            ))
            setProgress(workDataOf(KEY_PAGE to result.pageCount,KEY_TOTAL to result.pageCount))
            Result.success(workDataOf(KEY_CARD to cardId, KEY_FILE to fileId))
        }catch(c:CancellationException){throw c}
        catch(e:Exception){Result.failure(workDataOf(KEY_ERROR to (e.message?:"OCR failed")))}
    }
}
