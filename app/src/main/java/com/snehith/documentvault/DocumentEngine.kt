package com.snehith.documentvault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfDocument
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import java.io.*
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class ExtractedDocument(val text:String, val fields:List<DataField>, val method:String, val pageCount:Int)
class DocumentEngine(private val context:Context, private val repo:VaultRepository) {
    init {PDFBoxResourceLoader.init(context)}
    val mlScripts=listOf("Latin","Devanagari (Hindi)","Chinese","Japanese","Korean")
    fun availableLanguages():List<String> {
        val names=File(context.filesDir,"tessdata").listFiles()?.filter{it.name.endsWith(".traineddata")}?.map{it.name.removeSuffix(".traineddata")} ?: emptyList()
        return mlScripts+names.map{"Tesseract: "+it}
    }
    fun installLanguage(uri:Uri):String {
        val name=uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "custom.traineddata"
        require(name.matches(Regex("[a-zA-Z0-9_]+\\.traineddata"))){"Choose a valid .traineddata language file"}
        val dir=File(context.filesDir,"tessdata").apply{mkdirs()}
        val output=File(dir,name)
        context.contentResolver.openInputStream(uri)!!.use{src->output.outputStream().use{src.copyTo(it)}}
        if(output.length()<1000){output.delete();throw IOException("Invalid OCR language data")}
        return name.removeSuffix(".traineddata")
    }
    private fun recognize(bitmap:Bitmap,script:String):String {
        if(script.startsWith("Tesseract: ")){
            val lang=script.substringAfter("Tesseract: ")
            val tess=TessBaseAPI()
            try {
                if(!tess.init(context.filesDir.absolutePath,lang))throw IOException("Tesseract language data not found: "+lang)
                tess.setImage(bitmap)
                return tess.getUTF8Text() ?: ""
            } finally {tess.recycle()}
        }
        val client=when(script) {
            "Devanagari (Hindi)" -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            "Chinese" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            "Japanese" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            "Korean" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
        try { return Tasks.await(client.process(InputImage.fromBitmap(bitmap,0)),90,TimeUnit.SECONDS).text }
        finally {client.close()}
    }
    suspend fun extract(file:VaultFile,script:String="Latin",onProgress:((Int,Int)->Unit)?=null):ExtractedDocument=withContext(Dispatchers.IO) {
        val temp=repo.openDecrypted(file)
        try {
            val mime=file.mime.lowercase()
            val ext=file.name.substringAfterLast('.', "").lowercase()
            var text=""
            var method="Digital text"
            var pages=1
            if(ext=="pdf" || mime=="application/pdf") {
                PDDocument.load(temp).use{doc ->
                    pages=doc.numberOfPages
                    text=PDFTextStripper().getText(doc).trim()
                }
                // Scans are common in mixed PDFs: OCR the whole document if the
                // text layer is empty or unusually sparse.
                if(text.length<pages*12) {
                    method="On-device OCR"
                    val extracted=StringBuilder()
                    ParcelFileDescriptor.open(temp,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->
                        PdfRenderer(fd).use{renderer->
                            pages=renderer.pageCount
                            for(i in 0 until renderer.pageCount) {
                                renderer.openPage(i).use{page->
                                    val scale=(1800f/page.width).coerceIn(1f,2f)
                                    val w=(page.width*scale).toInt().coerceAtLeast(1)
                                    val h=(page.height*scale).toInt().coerceAtLeast(1)
                                    val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                                    try {bitmap.eraseColor(android.graphics.Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        extracted.append("\n--- PAGE ").append(i+1).append(" ---\n").append(recognize(bitmap,script))
                                    }finally{bitmap.recycle()}
                                }
                                onProgress?.invoke(i+1,pages)
                            }
                        }
                    }
                    text=extracted.toString().trim()
                }
            } else if(mime.startsWith("image/")||ext in listOf("jpg","jpeg","png","webp","bmp")) {
                method="On-device OCR"
                val bitmap=scaledBitmap(temp,2400)
                try{text=recognize(bitmap,script)}finally{bitmap.recycle()}
            } else if(ext=="docx") {
                method="DOCX text layer"
                text=docxText(temp)
            } else if(ext=="doc") {
                method="DOC text layer"
                HWPFDocument(FileInputStream(temp)).use{doc-> text=WordExtractor(doc).text}
            } else if(ext in listOf("txt","csv","md","json","xml","log")||mime.startsWith("text/")) {
                text=temp.readText(Charsets.UTF_8)
            } else {
                throw IOException("This file format has no built-in text extractor")
            }
            ExtractedDocument(text,FieldSuggestions.parse(text),method,pages)
        } finally {temp.delete()}
    }
    fun scaledBitmap(file:File,largest:Int):Bitmap {
        val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeFile(file.absolutePath,options)
        if(options.outWidth<=0||options.outHeight<=0)throw IOException("Cannot decode image")
        val sample=(maxOf(options.outWidth,options.outHeight)/largest).coerceAtLeast(1)
        return BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply{inSampleSize=sample})
            ?:throw IOException("Cannot decode image")
    }
    private fun docxText(file:File):String {
        ZipFile(file).use{zip->
            val entry=zip.getEntry("word/document.xml")?:throw IOException("Invalid DOCX file")
            val xml=zip.getInputStream(entry).bufferedReader().use{it.readText()}
            val normalized=xml.replace(Regex("</w:p>"),"\n")
                .replace(Regex("<w:tab[^>]*/>"),"\t")
                .replace(Regex("<w:br[^>]*/>"),"\n")
                .replace(Regex("<[^>]*>"),"")
            return normalized.replace("&amp;","&").replace("&lt;","<").replace("&gt;",">")
                .replace("&quot;","\"").replace("&apos;","'")
        }
    }
    fun pdfPageImage(file:File,pageIndex:Int,maxWidth:Int=1440):Pair<Bitmap,Int> {
        ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->
            PdfRenderer(fd).use{renderer ->
                val index=pageIndex.coerceIn(0,renderer.pageCount-1)
                renderer.openPage(index).use{page->
                    val scale=maxWidth.toFloat()/page.width
                    val image=Bitmap.createBitmap(maxWidth,(page.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                    image.eraseColor(android.graphics.Color.WHITE)
                    page.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return image to renderer.pageCount
                }
            }
        }
    }
    fun merge(files:List<VaultFile>,target:Uri) {
        require(files.size>=2){"Select at least two PDFs"}
        val temps=mutableListOf<File>()
        try {
            PDDocument().use{out ->
                files.forEach{f ->
                    require(f.name.endsWith(".pdf",true)){"All inputs must be PDFs"}
                    val plain=repo.openDecrypted(f);temps.add(plain)
                    PDDocument.load(plain).use{doc ->
                        for(page in doc.pages) out.importPage(page)
                    }
                }
                context.contentResolver.openOutputStream(target)!!.use{out.save(it)}
            }
        } finally {temps.forEach{it.delete()}}
    }
    fun pages(file:VaultFile,start:Int,end:Int,target:Uri) {
        val plain=repo.openDecrypted(file)
        try {
            PDDocument.load(plain).use{source ->
                val from=(start-1).coerceAtLeast(0);val to=end.coerceAtMost(source.numberOfPages)
                require(from<to){"Page range is invalid"}
                PDDocument().use{out ->
                    for(i in from until to) out.importPage(source.getPage(i))
                    context.contentResolver.openOutputStream(target)!!.use{out.save(it)}
                }
            }
        }finally{plain.delete()}
    }
    fun rotate(file:VaultFile,angle:Int,target:Uri) {
        val plain=repo.openDecrypted(file)
        try{PDDocument.load(plain).use{doc ->
            for(page in doc.pages)page.rotation=(page.rotation+angle+360)%360
            context.contentResolver.openOutputStream(target)!!.use{doc.save(it)}
        }}finally{plain.delete()}
    }
    fun imagesToPdf(files:List<VaultFile>,target:Uri,maxWidth:Int=1800) {
        val doc=PdfDocument()
        val temps=mutableListOf<File>()
        try {
            files.forEachIndexed{index,file ->
                val plain=repo.openDecrypted(file);temps.add(plain)
                val image=scaledBitmap(plain,maxWidth)
                try{
                    val info=PdfDocument.PageInfo.Builder(image.width,image.height,index+1).create()
                    val page=doc.startPage(info)
                    page.canvas.drawColor(android.graphics.Color.WHITE)
                    page.canvas.drawBitmap(image,0f,0f,null)
                    doc.finishPage(page)
                }finally{image.recycle()}
            }
            context.contentResolver.openOutputStream(target)!!.use{doc.writeTo(it)}
        }finally{doc.close();temps.forEach{it.delete()}}
    }
    fun searchableCopy(file:VaultFile,target:Uri) {
        require(file.name.endsWith(".pdf",true))
        val plain=repo.openDecrypted(file)
        try{PDDocument.load(plain).use{doc ->
            // Retain the original pages. Append invisible searchable Latin text,
            // not a replacement raster image; OCR text is user-reviewable.
            val text=file.extractedText.lines().filter{it.isNotBlank()}.joinToString(" ").replace(Regex("[^ -~]")," ")
            if(text.isBlank())throw IOException("Run OCR before exporting a searchable PDF")
            doc.pages.forEach{page ->
                PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use{stream ->
                    stream.beginText();stream.setFont(PDType1Font.HELVETICA,4f)
                    stream.setRenderingMode(RenderingMode.NEITHER)
                    stream.newLineAtOffset(8f,8f)
                    // PDF literal string encoding must be ASCII-safe.
                    text.chunked(120).take(80).forEach{line->stream.showText(line);stream.newLineAtOffset(0f,5f)}
                    stream.endText()
                }
            }
            context.contentResolver.openOutputStream(target)!!.use{doc.save(it)}
        }}finally{plain.delete()}
    }
}
