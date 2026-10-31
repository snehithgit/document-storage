package com.snehith.documentvault

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

/** Validate bounded local bytes before they can become a recoverable attachment. */
internal object VaultImportValidation {
    fun validate(file:File, media:VaultTransferType) {
        if(file.length()==0L)throw IOException("The selected file is empty")
        when {
            media.mime=="application/pdf" -> try {
                ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        if(renderer.pageCount<1)throw IOException("The PDF has no pages")
                    }
                }
            } catch(e:Exception) { throw IOException("The selected PDF cannot be read",e) }
            media.mime.startsWith("image/") -> {
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                // Some decoders throw on truncated headers instead of returning no bounds.
                try { BitmapFactory.decodeFile(file.absolutePath,bounds) } catch(_:Exception) {}
                if(bounds.outWidth<=0||bounds.outHeight<=0)
                    throw IOException("The selected image cannot be read on this device")
            }
            media.mime=="application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                validateDocx(file)
        }
    }
    private fun validateDocx(file:File) {
        try {
            ZipFile(file).use { zip ->
                val names=mutableSetOf<String>()
                val entries=zip.entries()
                while(entries.hasMoreElements()) {
                    val entry=entries.nextElement()
                    if(!names.add(entry.name)||names.size>VaultBackupChecks.MAX_ENTRIES)
                        throw IOException("Duplicate or excessive DOCX entries")
                }
                for((name,root) in listOf("[Content_Types].xml" to "Types","word/document.xml" to "document")) {
                    val entry=zip.getEntry(name)?:throw IOException("DOCX content is missing")
                    val bytes=zip.getInputStream(entry).use { input ->
                        val output=java.io.ByteArrayOutputStream()
                        val buffer=ByteArray(8192)
                        while(true) {
                            val n=input.read(buffer);if(n<0)break
                            if(output.size().toLong()+n>5_000_000L)throw IOException("DOCX text exceeds size limit")
                            output.write(buffer,0,n)
                        }
                        output.toByteArray()
                    }
                    val parser=android.util.Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true)
                    parser.setInput(bytes.inputStream(),null)
                    var first=true
                    while(true) {
                        val event=parser.nextToken()
                        if(event==XmlPullParser.END_DOCUMENT)break
                        if(event==XmlPullParser.DOCDECL)throw IOException("DOCX document declarations are unsupported")
                        if(event==XmlPullParser.START_TAG&&first) {
                            val namespace=if(root=="Types")"http://schemas.openxmlformats.org/package/2006/content-types"
                                else "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                            val strictNamespace="http://purl.oclc.org/ooxml/wordprocessingml/main"
                            if(parser.name!=root || (parser.namespace!=namespace&&
                                !(root=="document"&&parser.namespace==strictNamespace)))throw IOException("Invalid DOCX content")
                            first=false
                        }
                    }
                    if(first)throw IOException("DOCX content is empty")
                }
            }
        } catch(e:Exception) { throw IOException("The selected DOCX cannot be read",e) }
    }
}
