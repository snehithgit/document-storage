package com.snehith.documentvault

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.net.Uri
import kotlinx.coroutines.ensureActive
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Disk, native crop and multi-file import operations. Caller runs these on Dispatchers.IO. */
object VaultLongOperations {
    data class ImportResult(val cardId:String?,val count:Int,val skipped:Int)
    suspend fun importDocuments(
        repo:VaultRepository, uris:List<Uri>, targetId:String?,
        profile:String, originalUri:Uri?=null,corners:String="",rotation:Int=0,
        groupAsOne:Boolean=false
    ):ImportResult {
        val job=kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
        return repo.importDocuments(uris,targetId,profile,originalUri,corners,rotation,groupAsOne) {
            job?.ensureActive()
        }
    }
    fun correctedUri(
        context:Context,sourcePath:String,rotation:Int,
        corners:List<PointF>,settings:ScanSettings
    ):Uri {
        val original=ImageDecode.decode(File(sourcePath),Int.MAX_VALUE,
            fullResolution=true,highQuality=true)
        // Quarter turns are folded into the perspective warp: mapping the corners back
        // onto the original avoids a second full-resolution bitmap at peak memory.
        val oriented=if(rotation%90==0)original else {
            try {
                android.graphics.Bitmap.createBitmap(original,0,0,original.width,original.height,
                    Matrix().apply{postRotate(rotation.toFloat())},true)
            }finally{original.recycle()}
        }
        try {
            val quad=if(oriented===original)corners.map{
                CropGeometry.unrotate(it.x to it.y,rotation).let{(x,y)->PointF(x,y)}
            } else corners
            val cropped=DocumentCropEngine.crop(oriented,quad)
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
