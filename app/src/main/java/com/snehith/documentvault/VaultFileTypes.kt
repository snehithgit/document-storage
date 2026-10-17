package com.snehith.documentvault

import java.util.Locale

/** Trust verified file signatures over generic or misleading provider MIME labels. */
data class VaultTransferType(val name:String,val mime:String)

object VaultFileTypes {
    private val extensions=mapOf(
        "jpg" to "image/jpeg","jpeg" to "image/jpeg","png" to "image/png",
        "webp" to "image/webp","gif" to "image/gif","bmp" to "image/bmp",
        "heic" to "image/heic","heif" to "image/heif","avif" to "image/avif",
        "tif" to "image/tiff","tiff" to "image/tiff","pdf" to "application/pdf",
        "txt" to "text/plain","csv" to "text/csv",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    )
    private val preferred=mapOf(
        "image/jpeg" to "jpg","image/png" to "png","image/webp" to "webp",
        "image/gif" to "gif","image/bmp" to "bmp","image/heic" to "heic",
        "image/heif" to "heif","image/avif" to "avif",
        "image/tiff" to "tiff","application/pdf" to "pdf"
    )
    fun mimeFromName(filename:String):String? =
        extensions[filename.substringAfterLast('.', "").lowercase(Locale.ROOT)]

    /** Header-only identification; never decodes a document or trusts its extension. */
    fun signature(bytes:ByteArray):String? {
        fun b(i:Int)=bytes[i].toInt() and 255
        if(bytes.size>=5 && String(bytes,0,5,Charsets.US_ASCII)=="%PDF-")return "application/pdf"
        if(bytes.size>=3 && b(0)==255&&b(1)==216&&b(2)==255)return "image/jpeg"
        if(bytes.size>=8 && listOf(137,80,78,71,13,10,26,10).indices.all{
            b(it)==listOf(137,80,78,71,13,10,26,10)[it]})return "image/png"
        if(bytes.size>=6 && (String(bytes,0,6,Charsets.US_ASCII)=="GIF87a"||
            String(bytes,0,6,Charsets.US_ASCII)=="GIF89a"))return "image/gif"
        if(bytes.size>=12 && String(bytes,0,4,Charsets.US_ASCII)=="RIFF"&&
            String(bytes,8,4,Charsets.US_ASCII)=="WEBP")return "image/webp"
        if(bytes.size>=2 && b(0)==66&&b(1)==77)return "image/bmp"
        if(bytes.size>=4 && ((b(0)==73&&b(1)==73&&b(2)==42&&b(3)==0)||
            (b(0)==77&&b(1)==77&&b(2)==0&&b(3)==42)))return "image/tiff"
        if(bytes.size>=12 && String(bytes,4,4,Charsets.US_ASCII)=="ftyp") {
            val brand=String(bytes,8,4,Charsets.US_ASCII)
            if(brand in setOf("heic","heix","hevc","hevx","heim","heis"))return "image/heic"
            if(brand in setOf("mif1","msf1"))return "image/heif"
            if(brand in setOf("avif","avis"))return "image/avif"
        }
        return null
    }

    fun resolve(filename:String,reportedMime:String?,header:ByteArray):VaultTransferType {
        val name=filename.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\r\\n\\u0000-\\u001f]"),"_").trim('.', ' ')
            .ifBlank{"Document"}
            .take(150)
        val declared=reportedMime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        val detected=signature(header)
        val effective=detected ?: when {
            !declared.isNullOrBlank()&&declared!="application/octet-stream" -> declared
            else -> mimeFromName(name)?:"application/octet-stream"
        }
        val oldExt=name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val preferredExt=preferred[detected]
        val safeName=if(preferredExt!=null &&
            mimeFromName(name)!=detected) {
            // A WhatsApp BIN attachment may be an intact JPEG/PDF.
            val stem=if(oldExt.isNotBlank()&&name.contains('.'))
                name.substringBeforeLast('.') else name
            "$stem.$preferredExt"
        } else if(preferredExt!=null && !name.contains('.')) "$name.$preferredExt"
        else name
        return VaultTransferType(safeName,effective)
    }

    fun chooserMime(types:List<VaultTransferType>):String {
        if(types.isEmpty())return "application/octet-stream"
        val kinds=types.map{it.mime}.distinct()
        if(kinds.size==1)return kinds.single()
        return if(kinds.all{it.startsWith("image/")})"image/*" else "*/*"
    }
}
