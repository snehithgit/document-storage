package com.snehith.documentvault

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.net.Uri
import kotlinx.coroutines.CancellationException
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Disk, native crop and multi-file import operations. Caller runs these on Dispatchers.IO. */
object VaultLongOperations {
    data class ImportResult(val cardId:String?,val count:Int,val skipped:Int)
    fun importDocuments(
        repo:VaultRepository, uris:List<Uri>, targetId:String?,
        profile:String, originalUri:Uri?=null,corners:String="",rotation:Int=0
    ):ImportResult {
        var last:VaultCard?=null
        var count=0
        var skipped=0
        for(uri in uris) {
            ImportTargetPolicy.requireAvailable(targetId,repo.cards.map{it.id}.toSet())
            val file=repo.importFile(uri)
            try {
                if(repo.isDuplicate(file.sha256)) {
                    skipped++
                    repo.removeOrphan(file)
                    continue
                }
                val existing=targetId?.let{id->repo.cards.firstOrNull{it.id==id}}
                val card=if(existing!=null)existing.copy(files=existing.files+file)
                else {
                    val kind=when {
                        file.name.contains("passport",true)->"Passport"
                        file.name.contains("cdc",true)->"Seafarer CDC"
                        file.name.contains("medical",true)->"Medical"
                        file.name.contains("certificate",true)->"COC / STCW"
                        else->"Custom"
                    }
                    repo.newCard(kind,profile).copy(
                        title=if(file.name.startsWith("scan-",true)||file.name.startsWith("capture-",true))
                            "Scanned document · "+LocalDateTime.now()
                                .format(DateTimeFormatter.ofPattern("dd MMM HH:mm"))
                        else file.name.substringBeforeLast('.',file.name),
                        files=listOf(file))
                }
                repo.saveCard(card)
                last=card
                count++
            }catch(cancelled:CancellationException){throw cancelled}
            catch(e:Exception){
                // The new blob must not be orphaned if its index write failed.
                if(repo.cards.none{card->card.files.any{it.id==file.id}})repo.removeOrphan(file)
                throw e
            }
        }
        val result=last?:return ImportResult(null,count,skipped)
        if(originalUri!=null){
            try {
                val retained=repo.importFile(originalUri)
                try {repo.keepScanSource(result.id,result.files.last().id,retained,corners,rotation)}
                catch(cancelled:CancellationException){throw cancelled}
                catch(e:Exception){repo.removeOrphan(retained);throw e}
            }catch(cancelled:CancellationException){throw cancelled}
            catch(e:Exception){
                // Saved page remains usable if the optional raw camera cache was removed.
                skipped++
            }
        }
        return ImportResult(result.id,count,skipped)
    }
    fun correctedUri(
        context:Context,sourcePath:String,rotation:Int,
        corners:List<PointF>,settings:ScanSettings
    ):Uri {
        val original=ImageDecode.decode(File(sourcePath),Int.MAX_VALUE,
            fullResolution=true,highQuality=true)
        val oriented=if(rotation%360==0)original else {
            try {
                android.graphics.Bitmap.createBitmap(original,0,0,original.width,original.height,
                    Matrix().apply{postRotate(rotation.toFloat())},true)
            }finally{original.recycle()}
        }
        try {
            val cropped=DocumentCropEngine.crop(oriented,corners)
            try {
                val enhanced=ScanEnhance.apply(cropped,settings)
                try {
                    val output=ScanOutputFiles.newScan(context.cacheDir)
                    DocumentCropEngine.save(enhanced,output)
                    return FileProvider.getUriForFile(context,context.packageName+".provider",output)
                }finally{if(enhanced!==cropped)enhanced.recycle()}
            }finally{cropped.recycle()}
        }finally{oriented.recycle()}
    }
    fun viewPdfTemporary(context:Context,uri:Uri):File {
        val directory=File(context.cacheDir,"view-only").apply{mkdirs()}
        val target=File(directory,"open-"+UUID.randomUUID()+".pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use{input->
                target.outputStream().use{output->input.copyTo(output,65536)}
            }?:throw java.io.IOException("Cannot read selected PDF")
            if(target.length()==0L)throw java.io.IOException("The PDF is empty")
            return target
        }catch(e:Exception){target.delete();throw e}
    }
}
