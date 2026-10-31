package com.snehith.documentvault

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.io.File
import java.util.UUID

@Composable fun DocumentViewerScreen(file:VaultFile,repo:VaultRepository,
    engine:DocumentEngine,onShare:()->Unit,sourceOverride:File?=null,initialPage:Int=0) {
    val pdf=file.mime=="application/pdf"||file.name.endsWith(".pdf",true)
    val isImage=file.mime.startsWith("image/")
    val context=LocalContext.current
    val viewPrefs=remember(context){
        context.getSharedPreferences("viewer_rotation",android.content.Context.MODE_PRIVATE)
    }
    var page by rememberSaveable(file.id){mutableIntStateOf(initialPage.coerceAtLeast(0))}
    LaunchedEffect(file.id,initialPage){page=initialPage.coerceAtLeast(0)}
    var rotation by rememberSaveable(file.id,page){
        mutableIntStateOf(viewPrefs.getInt(file.id+":"+page,0))
    }
    var pageCount by remember(file.id){mutableIntStateOf(1)}
    var bitmap by remember(file.id){mutableStateOf<Bitmap?>(null)}
    var session by remember(file.id){mutableStateOf<PdfPageSession?>(null)}
    var text by remember(file.id){mutableStateOf(file.extractedText)}
    LaunchedEffect(file.id,file.extractedText){text=file.extractedText}
    var showText by rememberSaveable(file.id){mutableStateOf(!pdf&&!isImage)}
    var error by remember(file.id){mutableStateOf("")}
    var zoom by remember(file.id){mutableFloatStateOf(1f)}
    var panX by remember(file.id){mutableFloatStateOf(0f)}
    var panY by remember(file.id){mutableFloatStateOf(0f)}
    var loading by remember(file.id){mutableStateOf(false)}
    var decodedEdge by remember(file.id){mutableIntStateOf(1550)}
    LaunchedEffect(zoom){
        delay(170)
        decodedEdge=when{
            zoom>=2.4f->3400
            zoom>=1.45f->2400
            else->1550
        }
    }

    DisposableEffect(session,file.id) {
        val active=session
        onDispose {
            // PdfRenderer's synchronized render() may still run in Dispatchers.IO.
            // Defer close off the UI thread; do not recycle its page bitmap while drawn.
            if(active!=null) kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                runCatching { active.close() }
            }
        }
    }
    // Do not recycle a displayed or cached page on Back/page changes: renderer cache
    // can legitimately return that bitmap while Compose is still painting it.
    LaunchedEffect(file.id){
        if(pdf)try{
            loading=true
            val opened=withContext(Dispatchers.IO){
                PdfPageSession(sourceOverride?:repo.documentFile(file))
            }
            if(!kotlinx.coroutines.currentCoroutineContext().isActive){
                withContext(Dispatchers.IO){opened.close()}
                return@LaunchedEffect
            }
            session=opened
            pageCount=opened.pageCount
            page=page.coerceIn(0,(pageCount-1).coerceAtLeast(0))
        }catch(e:Exception){error=UserMessages.of(e,"Unable to open PDF")}
        finally{loading=false}
    }
    LaunchedEffect(file.id,page,showText,session,decodedEdge) {
        if(showText){
            // Viewer reads only the persisted OCR result. Request extraction through
            // the regular worker so language choice, errors and storage stay consistent.
            if(sourceOverride!=null && text.isBlank())
                text="Save this PDF to the vault to read text."
            return@LaunchedEffect
        }
        if(pdf&&session==null)return@LaunchedEffect
        try{
            loading=true
            val rendered=withContext(Dispatchers.IO){
                if(pdf)session!!.render(page,decodedEdge)
                else ImageDecode.decode(sourceOverride?:repo.documentFile(file),
                    decodedEdge,highQuality=true)
            }
            bitmap=rendered
            error=""
            loading=false
        }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
        catch(e:Exception){error=UserMessages.of(e,"Unable to preview document");loading=false}
    }
    Column(Modifier.fillMaxSize().background(Color.Black).padding(horizontal=12.dp,vertical=8.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(VaultNames.label(file.name),style=MaterialTheme.typography.titleMedium,
                maxLines=1,modifier=Modifier.weight(1f))
            IconButton(onClick={
                showText=!showText
                // Reading saved text never starts another OCR job; use Get Details on the card.
            },modifier=Modifier.size(48.dp)){
                Icon(if(showText)Icons.Outlined.Image else Icons.Outlined.TextSnippet,
                    if(showText)"Show document" else "View saved text")
            }
            IconButton(onClick={
                rotation=(rotation+90)%360
                viewPrefs.edit().putInt(file.id+":"+page,rotation).apply()
            },modifier=Modifier.size(48.dp)){
                Icon(Icons.Outlined.RotateRight,"Rotate viewed page")
            }
             IconButton(onClick=onShare,modifier=Modifier.size(48.dp)){
                Icon(Icons.Outlined.Share,"Share document")
            }
        }
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,
            style=MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black),
            contentAlignment=Alignment.Center){
            if(showText){
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
                    if(text.isBlank()&&!loading)Text("No saved text yet. Return to the document and choose Get Details.",
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    else SelectionContainer{Text(text,style=MaterialTheme.typography.bodyLarge)}
                }
            }else{
                val picture=bitmap
                if(picture!=null && !picture.isRecycled){
                    Image(picture.asImageBitmap(),"Document page",
                        modifier=Modifier.fillMaxSize()
                            .pointerInput(file.id,page){
                                detectTransformGestures{_,move,factor,_->
                                    zoom=(zoom*factor).coerceIn(1f,5f)
                                    panX=(panX+move.x).coerceIn(-1100f,1100f)
                                    panY=(panY+move.y).coerceIn(-1800f,1800f)
                                }
                            }
                            .graphicsLayer(scaleX=zoom,scaleY=zoom,rotationZ=rotation.toFloat(),
                                translationX=panX,translationY=panY),
                        contentScale=androidx.compose.ui.layout.ContentScale.Fit)
                }
            }
            if(loading)CircularProgressIndicator(Modifier.size(32.dp))
        }
        if(pdf&&!showText&&pageCount>1){
            Text("All PDF pages",style=MaterialTheme.typography.labelMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp),
                contentPadding=PaddingValues(horizontal=4.dp)){
                items(pageCount){index->
                    val active=page==index
                    Surface(onClick={
                        page=index;zoom=1f;panX=0f;panY=0f
                    },shape=RoundedCornerShape(10.dp),
                        color=if(active)MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant){
                        Text("Page "+(index+1),
                            Modifier.padding(horizontal=13.dp,vertical=10.dp),
                            color=if(active)MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize=12.sp)
                    }
                }
            }
        }
        if(pdf&&!showText){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceBetween){
                IconButton(onClick={
                    page=(page-1).coerceAtLeast(0);zoom=1f;panX=0f;panY=0f
                },enabled=page>0,modifier=Modifier.size(48.dp)){
                    Icon(Icons.Outlined.ChevronLeft,"Previous page")
                }
                Text("Page ${page+1} of $pageCount",style=MaterialTheme.typography.bodyMedium)
                IconButton(onClick={
                    page=(page+1).coerceAtMost(pageCount-1);zoom=1f;panX=0f;panY=0f
                },enabled=page<pageCount-1,modifier=Modifier.size(48.dp)){
                    Icon(Icons.Outlined.ChevronRight,"Next page")
                }
            }
        }else if(!showText)Text("Pinch to zoom. Drag to move the page.",
            style=MaterialTheme.typography.bodySmall,
            modifier=Modifier.fillMaxWidth(),
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun PdfToolsScreen(card:VaultCard,repo:VaultRepository,
    onAdd:()->Unit,onExport:(String,List<VaultFile>,Int,Int)->Unit,
    onEnhance:(VaultFile,Int)->Unit={_,_->}) {
    // Prefer the first PDF for page operations; other attachments can be added for combining.
    var selected by rememberSaveable(card.id){mutableStateOf(
        card.files.firstOrNull{it.mime=="application/pdf"||it.name.endsWith(".pdf",true)}
            ?.let{listOf(it.id)} ?: card.files.map{it.id}
    )}
    var fromPage by rememberSaveable(card.id){mutableStateOf("1")}
    var toPage by rememberSaveable(card.id){mutableStateOf("1")}
    val selectedFiles=selected.mapNotNull{id->card.files.firstOrNull{it.id==id}}
    val combining=selectedFiles.isNotEmpty()&&selectedFiles.all{
        it.mime=="application/pdf"||it.name.endsWith(".pdf",true)||it.mime.startsWith("image/")
    }
    val onlyPdf=selectedFiles.size==1 && (selectedFiles[0].mime=="application/pdf"||
        selectedFiles[0].name.endsWith(".pdf",true))
    val pdfFile=if(onlyPdf)selectedFiles.first() else null
    val pdfPages=rememberVaultDocumentPages(repo,pdfFile?.let{listOf(it)}?:emptyList())
    val pdfPageCount=if(pdfFile!=null)pdfPages.size else 0
    LaunchedEffect(pdfFile?.id,pdfPageCount){
        fromPage="1"
        toPage=pdfPageCount.coerceAtLeast(1).toString()
    }
    val startIndex=fromPage.toIntOrNull()
    val endIndex=toPage.toIntOrNull()
    val validRange=onlyPdf&&startIndex!=null&&endIndex!=null&&
        startIndex in 1..pdfPageCount&&endIndex in startIndex..pdfPageCount
    val p=mockupPalette
    LazyColumn(Modifier.fillMaxSize().background(p.panel),
        contentPadding=PaddingValues(18.dp,22.dp,18.dp,28.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {
            Eyebrow("PDF Studio · "+VaultNames.title(card.title))
            Spacer(Modifier.height(10.dp))
            Text("Make one PDF",color=p.ink,fontSize=32.sp,lineHeight=34.sp,
                fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Choose files and use the arrows to set the order. The original files stay unchanged.",
                color=p.inkMuted,fontSize=14.sp)
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick=onAdd,shape=RoundedCornerShape(16.dp),
                border=BorderStroke(1.dp,p.line),
                colors=ButtonDefaults.outlinedButtonColors(contentColor=p.ink),
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Icon(Icons.Outlined.Add,null)
                Text(" Add photos or documents")
            }
        }
        item {
            MockupSectionHeader("Selected files",
                VaultCounts.format(selectedFiles.size,"of "+card.files.size+" file"))
        }
        items(selectedFiles,key={it.id}){file->
            val index=selected.indexOf(file.id)
            Card(colors=CardDefaults.cardColors(containerColor=p.panelRaised),
                shape=RoundedCornerShape(18.dp),
                border=BorderStroke(1.dp,p.line)){
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=10.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    VaultThumbnail(file,repo,Modifier.size(54.dp,70.dp))
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)){
                        Text(VaultNames.label(file.name),color=p.ink,fontSize=14.sp,
                            fontWeight=FontWeight.SemiBold,maxLines=2)
                        Text("File ${(index+1).toString().padStart(2,'0')}",color=p.inkFaint,
                            fontSize=12.sp)
                    }
                    IconButton(onClick={
                        if(index>0){
                            val next=selected.toMutableList()
                            java.util.Collections.swap(next,index,index-1)
                            selected=next
                        }
                    },enabled=index>0,modifier=Modifier.size(48.dp)){
                        Icon(Icons.Outlined.KeyboardArrowUp,"Move file up")
                    }
                    IconButton(onClick={
                        if(index>=0&&index<selected.lastIndex){
                            val next=selected.toMutableList()
                            java.util.Collections.swap(next,index,index+1)
                            selected=next
                        }
                    },enabled=index>=0&&index<selected.lastIndex,modifier=Modifier.size(48.dp)){
                        Icon(Icons.Outlined.KeyboardArrowDown,"Move file down")
                    }
                    IconButton(onClick={selected=selected-file.id},modifier=Modifier.size(48.dp)){
                        Icon(Icons.Outlined.Close,"Deselect file")
                    }
                }
            }
        }
        if(card.files.any{it.id !in selected})item {
            Text("Add more files",style=MaterialTheme.typography.titleMedium)
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                card.files.filter{it.id !in selected}.forEach{file->
                    OutlinedButton(onClick={selected=selected+file.id},
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                        Icon(Icons.Outlined.Add,null,Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(VaultNames.label(file.name),maxLines=1,modifier=Modifier.weight(1f))
                    }
                }
            }
        }
        item{
            Button(onClick={onExport("combine",selectedFiles,1,1)},
                enabled=combining,shape=RoundedCornerShape(16.dp),
                colors=ButtonDefaults.buttonColors(containerColor=p.accent,contentColor=p.accentInk),
                modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){
                Icon(Icons.Outlined.PictureAsPdf,null)
                Spacer(Modifier.width(9.dp))
                Text("Save combined PDF to document",style=MaterialTheme.typography.titleMedium)
            }
        }
        item{
            OutlinedButton(onClick={onExport("export-combine",selectedFiles,1,1)},
                enabled=combining,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Text("Export combined PDF…")
            }
        }
        if(onlyPdf)item{
            MockupSectionHeader("Pages in PDF",VaultCounts.format(pdfPageCount,"page"))
            Text("Tap a page to choose the export range. All pages remain in the original PDF.",
                color=p.inkMuted,fontSize=12.sp)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement=Arrangement.spacedBy(9.dp)){
                itemsIndexed(pdfPages,key={_,page->page.file.id+":"+page.index}){index,page->
                    val number=index+1
                    val active=validRange&&number>=startIndex!!&&number<=endIndex!!
                    Column(Modifier.width(88.dp).clickable{
                        if(startIndex==null||number<startIndex)fromPage=number.toString()
                        else toPage=number.toString()
                    },horizontalAlignment=Alignment.CenterHorizontally){
                        Surface(shape=RoundedCornerShape(12.dp),
                            border=BorderStroke(if(active)2.dp else 1.dp,
                                if(active)p.accent else p.line)){
                            VaultDocumentPaper(page,repo,Modifier.fillMaxWidth(),maxEdge=250)
                        }
                        Text("Page "+number,fontSize=11.sp,
                            color=if(active)p.accent else p.inkMuted)
                    }
                }
            }
        }
        item{
            Text("Other PDF tools",color=p.ink,fontSize=17.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                OutlinedTextField(fromPage,{fromPage=it},label={Text("First page")},
                    modifier=Modifier.weight(1f),singleLine=true)
                OutlinedTextField(toPage,{toPage=it},label={Text("Last page")},
                    modifier=Modifier.weight(1f),singleLine=true)
            }
            Spacer(Modifier.height(8.dp))
            if(onlyPdf&&!validRange)
                Text("Choose a range from 1 to "+pdfPageCount+".",
                    color=p.warning,fontSize=12.sp)
            OutlinedButton(onClick={
                onExport("split",selectedFiles,startIndex?:1,endIndex?:1)
            },enabled=validRange,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Text("Export selected pages")
            }
            OutlinedButton(onClick={onExport("rotate",selectedFiles,1,pdfPageCount)},
                enabled=onlyPdf,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Text("Rotate all PDF pages 90°")
            }
            if(pdfFile!=null)OutlinedButton(
                onClick={onEnhance(pdfFile,((startIndex?:1)-1).coerceIn(0,
                    (pdfPageCount-1).coerceAtLeast(0)))},
                enabled=validRange,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Text("Enhance page "+(startIndex?:1)+" (save PDF copy)")
            }
            OutlinedButton(onClick={onExport("searchable",selectedFiles,1,pdfPageCount)},
                enabled=onlyPdf&&selectedFiles[0].extractedText.isNotBlank(),
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Text("Make searchable PDF")
            }
            if(!onlyPdf)Text("Select exactly one PDF to preview or export individual pages.",
                color=p.inkMuted,fontSize=12.sp)
            Text("Original files are kept unchanged. You choose where exports are saved.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun ProfilesScreen(profiles:List<VaultProfile>,onSave:(VaultProfile)->Unit) {
    var name by remember{mutableStateOf("")}
    LazyColumn(contentPadding=PaddingValues(16.dp,12.dp,16.dp,90.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Text("Organize documents by person, entirely on this device.",
            style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.onSurfaceVariant)}
        items(profiles){p->ElevatedCard{
            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person,null);Spacer(Modifier.width(12.dp))
                Text(p.name,fontWeight=FontWeight.SemiBold)
            }
        }}
        item{OutlinedTextField(name,{name=it},label={Text("Profile name")},modifier=Modifier.fillMaxWidth())}
        item{Button(onClick={onSave(VaultProfile(UUID.randomUUID().toString(),name.trim()));name=""},enabled=name.isNotBlank(),
            modifier=Modifier.fillMaxWidth()){Text("Add family profile")}}
    }
}
@Composable fun TemplatesScreen(templates:List<CardTemplate>,onUse:(CardTemplate)->Unit) {
    LazyColumn(contentPadding=PaddingValues(16.dp,12.dp,16.dp,90.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        item{Text("Start from a structure and edit any field.",
            style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.onSurfaceVariant)}
        items(templates){t->Card(onClick={onUse(t)}) {
            Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Outlined.ViewAgenda,null);Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)){
                    Text(t.name,fontWeight=FontWeight.SemiBold)
                    Text(t.fieldNames.joinToString(" · ").take(130),style=MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Outlined.ChevronRight,null)
            }
        }}
    }
}
@Composable fun BackupScreen(onBackup:(String)->Unit,onRestore:(String)->Unit) {
    val p=mockupPalette
    LazyColumn(Modifier.fillMaxSize().background(p.panel),
        contentPadding=PaddingValues(18.dp,22.dp,18.dp,30.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{
            Eyebrow("Backup & Recovery")
            Spacer(Modifier.height(10.dp))
            Text("ZIP backup",color=p.ink,fontSize=32.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Keep a separate copy of your documents.",color=p.inkMuted,fontSize=14.sp)
        }
        item{
            MockupNote(Icons.Outlined.WarningAmber,
                "The ZIP is not encrypted. Anyone who has the file may read its contents. "+
                "Keep it somewhere private.",warning=true)
        }
        item{
            MockupGroup{
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("Export backup",color=p.ink,fontSize=17.sp,fontWeight=FontWeight.Bold)
                    Text("Save documents, information, profiles and templates in one backup file.",
                        color=p.inkMuted,fontSize=13.sp)
                    Button(onClick={onBackup("")},shape=RoundedCornerShape(16.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=p.accent,
                            contentColor=p.accentInk),
                        modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                        Icon(Icons.Outlined.FileUpload,null,Modifier.size(19.dp))
                        Text(" Create backup")
                    }
                }
            }
        }
        item{
            MockupGroup{
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("Restore from ZIP",color=p.ink,fontSize=17.sp,fontWeight=FontWeight.Bold)
                    Text("Replaces the card index on this device. Back up first if you have new cards. "+
                        "You will be asked to confirm before existing documents are replaced.",
                        color=p.inkMuted,fontSize=13.sp)
                    OutlinedButton(onClick={onRestore("")},shape=RoundedCornerShape(16.dp),
                        border=BorderStroke(1.dp,p.line),
                        colors=ButtonDefaults.outlinedButtonColors(contentColor=p.ink),
                        modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                        Icon(Icons.Outlined.Restore,null,Modifier.size(19.dp))
                        Text(" Choose backup to restore")
                    }
                }
            }
        }
    }
}
@Composable fun RecoveryScreen(files:List<VaultFile>,recoveryError:String?,onRecover:(VaultFile,String)->Unit,onBackup:()->Unit) {
    var name by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf<VaultFile?>(null)}
    LazyColumn(contentPadding=PaddingValues(16.dp,12.dp,16.dp,90.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Heading("Recover old imports","Preserve files from earlier failed saves")}
        if(recoveryError!=null) item {
            Text(recoveryError,color=MaterialTheme.colorScheme.error)
            OutlinedButton(onClick=onBackup){Text("Open Backup & Restore")}
        }
        item{Text(VaultCounts.format(files.size,"unattached file")+" found. These are not automatically deleted. Recovery only applies to this installation.",
            style=MaterialTheme.typography.bodyMedium)}
        items(files){file->
            ElevatedCard {
                Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(if(file.name.startsWith("Recovered-",ignoreCase=true))"Recovered file" else VaultNames.label(file.name),fontWeight=FontWeight.SemiBold)
                    Text(((file.bytes/1024).coerceAtLeast(1)).toString()+" KB",style=MaterialTheme.typography.labelSmall)
                    OutlinedButton(onClick={selected=file;name="Recovered document"}){Text("Recover into new card")}
                }
            }
        }
    }
    if(selected!=null)AlertDialog(onDismissRequest={selected=null},title={Text("Recover document")},
        text={
            OutlinedTextField(name,{name=it},label={Text("New card title")})
        },
        confirmButton={TextButton(onClick={
            val file=selected
            selected=null
            if(file!=null)onRecover(file,name)
        }){Text("Recover")}},
        dismissButton={TextButton(onClick={selected=null}){Text("Cancel")}})
}
