package com.snehith.documentvault

import java.util.Locale

/**
 * Selects files that the existing DocumentEngine.extract can read.
 * Never sends photos or documents to a network service.
 */
object DetailFetchPlan {
    private val supportedExtensions=setOf(
        "pdf","jpg","jpeg","png","webp","bmp","doc","docx",
        "txt","csv","md","json","xml","log"
    )

    fun supported(file:VaultFile):Boolean {
        val extension=file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mime=file.mime.lowercase(Locale.ROOT)
        return extension in supportedExtensions ||
            mime=="application/pdf" || mime.startsWith("image/") ||
            mime.startsWith("text/")
    }

    fun filesToProcess(files:List<VaultFile>):List<VaultFile> =
        files.filter(::supported).distinctBy{it.id}
}
