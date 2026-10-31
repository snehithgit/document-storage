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
            val smart=result.document
            repo.updateCard(cardId){latest->
                val current=latest.files.firstOrNull{it.id==fileId}
                    ?:throw java.io.IOException("Attachment was removed")
                val revised=current.copy(extractedText=result.text,ocrStatus=result.method)
                val titleIsGeneric=latest.title.isBlank() ||
                    latest.title.startsWith("Scan ",true) ||
                    latest.title.startsWith("Scanned document",true) ||
                    latest.title.equals(current.name.substringBeforeLast('.'),true)
                val fields=latest.fields.toMutableList()
                result.fields.filter{it.name.isNotBlank()&&it.value.isNotBlank()}.forEach{f->
                    val index=fields.indexOfFirst{it.name.equals(f.name,true)}
                    if(index<0)fields.add(f)
                    else if(fields[index].value.isBlank())fields[index]=f
                }
                val identity=smart?.kind in setOf("Passport","Aadhaar","PAN","Driving Licence")
                latest.copy(
                    files=latest.files.map{if(it.id==fileId)revised else it},
                    fields=fields,
                    category=if(identity && (latest.category=="Personal"||
                        latest.category.isBlank()))"Identity" else latest.category,
                    kind=smart?.kind?.takeIf{latest.kind=="Custom"||
                        latest.kind=="Recovered"}?:latest.kind,
                    title=if(titleIsGeneric&&smart!=null&&smart.confidence>=.7f&&
                        !smart.fullName.isNullOrBlank())smart.title else latest.title,
                    expiry=if(latest.expiry.isBlank())smart?.expiry?:"" else latest.expiry
                )
            }
            setProgress(workDataOf(KEY_PAGE to result.pageCount,KEY_TOTAL to result.pageCount))
            Result.success(workDataOf(KEY_CARD to cardId, KEY_FILE to fileId))
        }catch(c:CancellationException){throw c}
        catch(e:Exception){
            // A file-level failure must not cancel subsequent items in a WorkManager chain.
            Result.success(workDataOf(KEY_FILE to inputData.getString(KEY_FILE),
                KEY_ERROR to (e.message?:"OCR failed")))
        }
    }
}
