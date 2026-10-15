package com.snehith.documentvault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.provider.OpenableColumns
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfDocument
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.TimeUnit

data class ExtractedDocument(val text:String, val fields:List<DataField>, val method:String, val pageCount:Int)

// Single PDF renderer per viewer session, with a small 2-page bitmap cache.
// Opening the renderer on every page turn was noticeably slow on low-end phones.
class PdfPageSession(private val source:File):Closeable {
    private val descriptor=ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer=try{PdfRenderer(descriptor)}catch(e:Exception){descriptor.close();throw e}
    private var closed=false
    val pageCount:Int get()=renderer.pageCount
    private val cached=object:LinkedHashMap<Int,Bitmap>(3,0.75f,true){
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<Int,Bitmap>?):Boolean=size>2
    }
    @Synchronized fun render(index:Int,maxWidth:Int=1050):Bitmap {
        check(!closed){"PDF viewing session already closed"}
        cached[index]?.let{return it}
        renderer.openPage(index.coerceIn(0,renderer.pageCount-1)).use{page->
            val scale=(maxWidth.toFloat()/page.width).coerceIn(0.5f,2.25f)
            val w=(page.width*scale).toInt().coerceIn(1,1500)
            val h=(page.height*scale).toInt().coerceIn(1,3200)
            val image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            try {
                image.eraseColor(android.graphics.Color.WHITE)
                page.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cached[index]=image
                return image
            }catch(e:Exception){image.recycle();throw e}
        }
    }
    @Synchronized override fun close(){
        if(closed)return
        closed=true
        cached.clear()
        try{renderer.close()}finally{descriptor.close()}
    }
}
class DocumentEngine(private val context:Context, private val repo:VaultRepository) {
    init {PDFBoxResourceLoader.init(context)}
    val mlScripts=listOf("Latin","Devanagari (Hindi)","Chinese","Japanese","Korean")
    fun availableLanguages():List<String> {
        val names=File(context.filesDir,"tessdata").listFiles()?.filter{it.name.endsWith(".traineddata")}?.map{it.name.removeSuffix(".traineddata")} ?: emptyList()
        return mlScripts+names.map{"Tesseract: "+it}
    }
    fun installLanguage(uri:Uri):String {
        var name="custom.traineddata"
        context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
            if(cursor.moveToFirst())name=cursor.getString(0)?:name
        }
        name=name.substringAfterLast('/').substringAfterLast(':')
        require(name.matches(Regex("[a-zA-Z0-9_]+\\.traineddata"))){"Choose a valid .traineddata language file"}
        val dir=File(context.filesDir,"tessdata").apply{mkdirs()}
        val output=File(dir,name)
        context.contentResolver.openInputStream(uri)!!.use{src->output.outputStream().use{src.copyTo(it)}}
        if(output.length()<1000){output.delete();throw IOException("Invalid OCR language data")}
        return name.removeSuffix(".traineddata")
    }
    private class OcrSession(script:String,private val home:File):Closeable {
        private val tess=if(script.startsWith("Tesseract: "))TessBaseAPI()else null
        private val client:TextRecognizer?=if(tess!=null)null else when(script) {
            "Devanagari (Hindi)"->TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            "Chinese"->TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            "Japanese"->TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            "Korean"->TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            else->TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
        init {
            if(tess!=null) {
                val lang=script.substringAfter("Tesseract: ")
                if(!tess.init(home.absolutePath,lang)) {
                    tess.recycle()
                    throw IOException("Tesseract language data missing: "+lang)
                }
            }
        }
        fun recognize(bitmap:Bitmap):String {
            tess?.let{it.setImage(bitmap);return it.getUTF8Text()?:""}
            return Tasks.await(client!!.process(InputImage.fromBitmap(bitmap,0)),90,TimeUnit.SECONDS).text
        }
        override fun close(){try{client?.close()}finally{tess?.recycle()}}
    }
    suspend fun extract(file:VaultFile,script:String="Latin",onProgress:((Int,Int)->Unit)?=null):ExtractedDocument=withContext(Dispatchers.IO) {
        val temp=repo.documentFile(file)
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
                    OcrSession(script,context.filesDir).use{session->
                    ParcelFileDescriptor.open(temp,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->
                        PdfRenderer(fd).use{renderer->
                            pages=renderer.pageCount
                            for(i in 0 until renderer.pageCount) {
                                currentCoroutineContext().ensureActive()
                                renderer.openPage(i).use{page->
                                    val scale=(2100f/page.width).coerceIn(1f,3.25f)
                                    val w=(page.width*scale).toInt().coerceIn(1,2400)
                                    val h=(page.height*scale).toInt().coerceIn(1,3200)
                                    val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                                    try {bitmap.eraseColor(android.graphics.Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        extracted.append("\n--- PAGE ").append(i+1).append(" ---\n").append(session.recognize(bitmap))
                                    }finally{bitmap.recycle()}
                                }
                                withContext(Dispatchers.Main) { onProgress?.invoke(i+1,pages) }
                            }
                        }
                    }
                    }
                    text=extracted.toString().trim()
                }
            } else if(mime.startsWith("image/")||ext in listOf("jpg","jpeg","png","webp","bmp")) {
                method="On-device OCR"
                val bitmap=scaledBitmap(temp,2400)
                try {OcrSession(script,context.filesDir).use{session->text=session.recognize(bitmap)}}
                finally{bitmap.recycle()}
                withContext(Dispatchers.Main){onProgress?.invoke(1,1)}
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
        } finally { /* original is retained in private storage */ }
    }
    fun scaledBitmap(file:File,largest:Int):Bitmap=ImageDecode.decode(file,largest)
    private fun docxText(file:File):String {
        ZipFile(file).use{zip->
            val entry=zip.getEntry("word/document.xml")?:throw IOException("Invalid DOCX file")
            val xml=zip.getInputStream(entry).bufferedReader().use{it.readText()}
            val normalized=xml.replace(Regex("</w:p>"),"\n")
                .replace(Regex("<w:tab[^>]*/>"),"\t")
                .replace(Regex("<w:br[^>]*/>"),"\n")
                .replace(Regex("<[^>]*>"),"")
            return normalized.replace("&lt;","<").replace("&gt;",">")
                .replace("&quot;","\"").replace("&apos;","'").replace("&amp;","&")
        }
    }
    fun pdfPageImage(file:File,pageIndex:Int,maxWidth:Int=1440):Pair<Bitmap,Int> {
        ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->
            PdfRenderer(fd).use{renderer ->
                val index=pageIndex.coerceIn(0,renderer.pageCount-1)
                renderer.openPage(index).use{page->
                    val scale=maxWidth.toFloat()/page.width
                    val image=Bitmap.createBitmap(maxWidth,(page.height*scale).toInt().coerceIn(1,4200),Bitmap.Config.ARGB_8888)
                    try {
                        image.eraseColor(android.graphics.Color.WHITE)
                        page.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return image to renderer.pageCount
                    }catch(e:Exception){image.recycle();throw e}
                }
            }
        }
    }
    // Combine PDFs and captured pictures in the requested order.
    // Sources remain intact. This also supports single images/PDFs.
    fun combineDocuments(files:List<VaultFile>,target:Uri) {
        require(files.isNotEmpty()){"Choose at least one attachment"}
        val opened=mutableListOf<PDDocument>()
        try {
            PDDocument().use{output->
                files.forEach{file->
                    val raw=repo.documentFile(file)
                    when {
                        file.mime=="application/pdf"||file.name.endsWith(".pdf",true)->{
                            val input=PDDocument.load(raw)
                            opened.add(input)
                            input.pages.forEach{output.importPage(it)}
                        }
                        file.mime.startsWith("image/")->{
                            val bmp=scaledBitmap(raw,2000)
                            try {
                                val page=PDPage(PDRectangle.A4)
                                output.addPage(page)
                                val ximage=JPEGFactory.createFromImage(output,bmp,0.88f)
                                val pageWidth=page.mediaBox.width
                                val pageHeight=page.mediaBox.height
                                val factor=minOf((pageWidth-28f)/bmp.width,(pageHeight-28f)/bmp.height)
                                val w=bmp.width*factor
                                val h=bmp.height*factor
                                PDPageContentStream(output,page).use{stream->
                                    stream.drawImage(ximage,(pageWidth-w)/2f,(pageHeight-h)/2f,w,h)
                                }
                            }finally{bmp.recycle()}
                        }
                        else->throw IOException("Combine supports PDFs and images only: "+file.name)
                    }
                }
                context.contentResolver.openOutputStream(target)!!.use{output.save(it)}
            }
        }finally{opened.forEach{try{it.close()}catch(_:Exception){}}}
    }
    fun merge(files:List<VaultFile>,target:Uri) {
        require(files.size>=2){"Select at least two PDFs"}
        val temps=mutableListOf<File>()
        val sources=mutableListOf<PDDocument>()
        try {
            PDDocument().use{out ->
                files.forEach{f ->
                    require(f.name.endsWith(".pdf",true)||f.mime=="application/pdf"){"All inputs must be PDFs"}
                    val plain=repo.documentFile(f);temps.add(plain)
                    val doc=PDDocument.load(plain)
                    sources.add(doc)
                    for(page in doc.pages)out.importPage(page)
                }
                // Keep source documents open until the imported font/image resources
                // have been serialized to the merged output.
                context.contentResolver.openOutputStream(target)!!.use{out.save(it)}
            }
        }finally{sources.forEach{try{it.close()}catch(_:Exception){}};/* originals retained */}
    }
    fun pages(file:VaultFile,start:Int,end:Int,target:Uri) {
        val plain=repo.documentFile(file)
        try {
            PDDocument.load(plain).use{source ->
                val from=(start-1).coerceAtLeast(0);val to=end.coerceAtMost(source.numberOfPages)
                require(from<to){"Page range is invalid"}
                PDDocument().use{out ->
                    for(i in from until to) out.importPage(source.getPage(i))
                    context.contentResolver.openOutputStream(target)!!.use{out.save(it)}
                }
            }
        }finally{ /* original retained */ }
    }
    fun rotate(file:VaultFile,angle:Int,target:Uri) {
        val plain=repo.documentFile(file)
        try{PDDocument.load(plain).use{doc ->
            for(page in doc.pages)page.rotation=(page.rotation+angle+360)%360
            context.contentResolver.openOutputStream(target)!!.use{doc.save(it)}
        }}finally{ /* original retained */ }
    }
    fun imagesToPdf(files:List<VaultFile>,target:Uri,maxWidth:Int=1800) {
        val doc=PdfDocument()
        try {
            files.forEachIndexed{index,file->
                val image=scaledBitmap(repo.documentFile(file),maxWidth)
                try {
                    val geometry=PdfPageGeometry.fitA4(image.width,image.height)
                    val pageInfo=PdfDocument.PageInfo.Builder(geometry[0].toInt(),geometry[1].toInt(),index+1).create()
                    val page=doc.startPage(pageInfo)
                    try {
                        page.canvas.drawColor(android.graphics.Color.WHITE)
                        val src=android.graphics.Rect(0,0,image.width,image.height)
                        val dst=android.graphics.RectF(geometry[2],geometry[3],geometry[2]+geometry[4],geometry[3]+geometry[5])
                        page.canvas.drawBitmap(image,src,dst,android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                    }finally{doc.finishPage(page)}
                }finally{image.recycle()}
            }
            context.contentResolver.openOutputStream(target)!!.use{doc.writeTo(it)}
        }finally{doc.close()}
    }
    fun searchableCopy(file:VaultFile,target:Uri) {
        require(file.name.endsWith(".pdf",true)||file.mime=="application/pdf")
        val plain=repo.documentFile(file)
        PDDocument.load(plain).use{doc->
            val pages=doc.numberOfPages
            val sections=PdfPageTexts.parse(file.extractedText,pages)
            val textByPage=when {
                sections.size==pages->sections
                else->{
                    // A combined OCR block cannot be safely repeated on every page.
                    // Prefer real per-page digital text when available, otherwise ask for OCR.
                    val digital=(1..pages).map{i->
                        PDFTextStripper().apply{startPage=i;endPage=i}.getText(doc).trim()
                    }
                    if(digital.any{it.isNotBlank()})digital
                    else throw IOException("Run OCR again to obtain page-specific text before exporting")
                }
            }
            doc.pages.forEachIndexed{i,page->
                val ascii=textByPage[i].replace(Regex("[^ -~]")," ").trim()
                if(ascii.isNotBlank()){
                    PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use{stream->
                        stream.beginText()
                        stream.setFont(PDType1Font.HELVETICA,4f)
                        stream.setRenderingMode(RenderingMode.NEITHER)
                        stream.newLineAtOffset(8f,8f)
                        ascii.chunked(110).take(75).forEach{line->
                            stream.showText(line)
                            stream.newLineAtOffset(0f,5f)
                        }
                        stream.endText()
                    }
                }
            }
            context.contentResolver.openOutputStream(target)!!.use{doc.save(it)}
        }
    }
}
