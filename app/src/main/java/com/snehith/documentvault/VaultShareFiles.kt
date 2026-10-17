package com.snehith.documentvault

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.UUID

/**
 * The vault stores UUID.dat blobs. A recipient must instead receive a real filename and
 * extension, because other apps query the provider MIME or inspect the URI path.
 * Execute only on Dispatchers.IO. Originals and the vault index are never changed.
 */
object VaultShareFiles {
    data class Prepared(val uri:Uri,val type:VaultTransferType)

    fun prepare(context:Context,repo:VaultRepository,files:List<VaultFile>):List<Prepared> {
        val batch=File(context.cacheDir,"shared/"+UUID.randomUUID().toString())
        if(!batch.mkdirs())throw java.io.IOException("Could not prepare sharing folder")
        try {
            return files.mapIndexed{index,file->
                val original=repo.documentFile(file)
                val header=original.inputStream().use{input->
                    val bytes=ByteArray(32)
                    val read=input.read(bytes)
                    if(read<0)byteArrayOf() else bytes.copyOf(read)
                }
                val transfer=VaultFileTypes.resolve(VaultNames.label(file.name),file.mime,header)
                val directory=File(batch,index.toString()).apply{mkdirs()}
                val staged=File(directory,transfer.name)
                original.inputStream().buffered(65536).use{source->
                    staged.outputStream().buffered(65536).use{destination->
                        source.copyTo(destination,65536)
                    }
                }
                if(staged.length()!=original.length())
                    throw java.io.IOException("Sharing copy incomplete for "+transfer.name)
                Prepared(FileProvider.getUriForFile(context,context.packageName+".provider",staged),
                    transfer)
            }
        }catch(cancelled:CancellationException){batch.deleteRecursively();throw cancelled}
        catch(e:Exception){batch.deleteRecursively();throw e}
    }
}
