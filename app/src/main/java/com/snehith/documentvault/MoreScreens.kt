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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.io.File
import java.util.UUID

@Composable fun DocumentViewerScreen(file:VaultFile,repo:VaultRepository,
    engine:DocumentEngine,onShare:()->Unit,onOcr:()->Unit,sourceOverride:File?=null) {
    val pdf=file.mime=="application/pdf"||file.name.endsWith(".pdf",true)
    val isImage=file.mime.startsWith("image/")
    var page by rememberSaveable(file.id){mutableIntStateOf(0)}
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
        }catch(e:Exception){error=e.message?:"Unable to open PDF"}
        finally{loading=false}
    }
    LaunchedEffect(file.id,page,showText,session) {
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
                if(pdf)session!!.render(page)
                else engine.scaledBitmap(sourceOverride?:repo.documentFile(file),1550)
            }
            bitmap=rendered
            error=""
        }catch(e:Exception){error=e.message?:"Unable to preview document"}
        finally{loading=false}
    }
    Column(Modifier.fillMaxSize().background(Color.Black).padding(horizontal=12.dp,vertical=8.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(VaultNames.label(file.name),style=MaterialTheme.typography.titleMedium,
                maxLines=1,modifier=Modifier.weight(1f))
            IconButton(onClick={
                showText=!showText
                if(showText&&text.isBlank()&&sourceOverride==null)onOcr()
            },modifier=Modifier.size(48.dp)){
                Icon(if(showText)Icons.Outlined.Image else Icons.Outlined.TextSnippet,
                    if(showText)"Show document" else "Read text")
            }
            IconButton(onClick=onOcr,modifier=Modifier.size(48.dp)){
                Icon(Icons.Outlined.DocumentScanner,"Read page")
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
                    if(text.isBlank()&&!loading)Text("Text extraction queued or unavailable. Use Read page to retry.",
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
                            .graphicsLayer(scaleX=zoom,scaleY=zoom,
                                translationX=panX,translationY=panY),
                        contentScale=androidx.compose.ui.layout.ContentScale.Fit)
                }
            }
            if(loading)CircularProgressIndicator(Modifier.size(32.dp))
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
@Composable fun ScanImportScreen(onCamera:()->Unit,onImport:()->Unit,onNew:()->Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Heading("Add documents","Everything stays on your device unless you share or export it")
        ElevatedCard(onClick=onCamera,modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.padding(21.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.CameraAlt,null,Modifier.size(35.dp),tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(15.dp))
                Column {Text("Scan using camera",fontWeight=FontWeight.SemiBold);Text("Capture a page. Attach more pages from its card.",style=MaterialTheme.typography.bodySmall)}
            }
        }
        ElevatedCard(onClick=onImport,modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.padding(21.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.UploadFile,null,Modifier.size(35.dp),tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(15.dp))
                Column {Text("Import files",fontWeight=FontWeight.SemiBold);Text("PDF, image, DOC/DOCX, TXT; multiple files supported",style=MaterialTheme.typography.bodySmall)}
            }
        }
        ElevatedCard(onClick=onNew,modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.padding(21.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.AddCard,null,Modifier.size(35.dp),tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(15.dp))
                Column {Text("Information-only card",fontWeight=FontWeight.SemiBold);Text("Create details without an attachment",style=MaterialTheme.typography.bodySmall)}
            }
        }
        Text("Share from CamScanner, Files, WhatsApp or any app using Android's Share menu and choose Document Vault.",
            style=MaterialTheme.typography.bodyMedium)
    }
}
@Composable fun PdfToolsScreen(card:VaultCard,repo:VaultRepository,
    onAdd:()->Unit,onExport:(String,List<VaultFile>,Int,Int)->Unit) {
    var selected by rememberSaveable(card.id){mutableStateOf(card.files.map{it.id})}
    var fromPage by rememberSaveable(card.id){mutableStateOf("1")}
    var toPage by rememberSaveable(card.id){mutableStateOf("1")}
    val selectedFiles=selected.mapNotNull{id->card.files.firstOrNull{it.id==id}}
    val combining=selectedFiles.isNotEmpty()&&selectedFiles.all{
        it.mime=="application/pdf"||it.name.endsWith(".pdf",true)||it.mime.startsWith("image/")
    }
    val onlyPdf=selectedFiles.size==1 && (selectedFiles[0].mime=="application/pdf"||
        selectedFiles[0].name.endsWith(".pdf",true))
    val startIndex=fromPage.toIntOrNull()
    val endIndex=toPage.toIntOrNull()
    val validRange=startIndex!=null&&endIndex!=null&&startIndex>0&&endIndex>=startIndex
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding=PaddingValues(16.dp,16.dp,16.dp,28.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)){
        item {
            VaultHeader("PDF Tools",VaultNames.title(card.title))
            Spacer(Modifier.height(8.dp))
            Text("Choose the files to include, then create a PDF.",
                style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick=onAdd,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Icon(Icons.Outlined.Add,null)
                Text(" Add photos or documents")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text("Selected files",style=MaterialTheme.typography.titleLarge,
                    modifier=Modifier.weight(1f))
                Text(VaultCounts.format(selectedFiles.size,"file"),
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Move files up or down to choose their order.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(selectedFiles,key={it.id}){file->
            val index=selected.indexOf(file.id)
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                shape=RoundedCornerShape(14.dp),
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=10.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    VaultThumbnail(file,repo,Modifier.size(54.dp,70.dp))
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)){
                        Text(VaultNames.label(file.name),style=MaterialTheme.typography.bodyMedium,
                            maxLines=2)
                        Text("File ${index+1}",style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
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
                enabled=combining,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){
                Icon(Icons.Outlined.PictureAsPdf,null)
                Spacer(Modifier.width(9.dp))
                Text("Combine into PDF",style=MaterialTheme.typography.titleMedium)
            }
        }
        item{
            Text("Other PDF tools",style=MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                OutlinedTextField(fromPage,{fromPage=it},label={Text("First page")},
                    modifier=Modifier.weight(1f),singleLine=true)
                OutlinedTextField(toPage,{toPage=it},label={Text("Last page")},
                    modifier=Modifier.weight(1f),singleLine=true)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick={
                onExport("split",selectedFiles,startIndex?:1,endIndex?:1)
            },enabled=onlyPdf&&validRange,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Text("Export selected pages")
            }
            OutlinedButton(onClick={onExport("rotate",selectedFiles,1,1)},
                enabled=onlyPdf,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Text("Rotate PDF 90°")
            }
            OutlinedButton(onClick={onExport("searchable",selectedFiles,1,1)},
                enabled=onlyPdf&&selectedFiles[0].extractedText.isNotBlank(),
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Text("Make searchable PDF")
            }
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
@Composable fun SettingsScreen(hasPin:Boolean,languages:List<String>,currentScript:String,privacyScreen:Boolean,
    onPrivacyScreenChanged:(Boolean)->Unit,onPin:(String,String)->Unit,onClearPin:(String)->Unit,
    onProfiles:()->Unit,onTemplates:()->Unit,onBackups:()->Unit,onRecovery:()->Unit,onImportLanguage:()->Unit,onScript:(String)->Unit,
    onRemindDays:(Int)->Unit) {
    var pin by remember{mutableStateOf("")}
    var old by remember{mutableStateOf("")}
    var confirmPin by remember{mutableStateOf("")}
    var languageOpen by remember{mutableStateOf(false)}
    var days by remember{mutableStateOf("30")}
    LazyColumn(contentPadding=PaddingValues(16.dp,12.dp,16.dp,90.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
        item{Heading("Vault Settings","Offline-first, plain local files and on-device processing")}
        item{
            SectionSettings("Organization")
            SettingAction("Family profiles","Create and manage people",Icons.Outlined.People,onProfiles)
            SettingAction("Card templates","Reusable information fields",Icons.Outlined.ViewAgenda,onTemplates)
            SettingAction("Backup & restore","Export an ordinary, unencrypted ZIP",Icons.Outlined.Backup,onBackups)
            SettingAction("Recover previous imports","Find unattached files from failed saves in this installation",Icons.Outlined.Restore,onRecovery)
        }
        item {
            SectionSettings("Offline OCR")
            Box {
                OutlinedButton(onClick={languageOpen=true},modifier=Modifier.fillMaxWidth()){
                    Text("Recognition model: "+currentScript);Icon(Icons.Outlined.ArrowDropDown,null)
                }
                DropdownMenu(expanded=languageOpen,onDismissRequest={languageOpen=false}) {
                    languages.forEach{language->DropdownMenuItem(text={Text(language)},onClick={onScript(language);languageOpen=false})}
                }
            }
            OutlinedButton(onClick=onImportLanguage,modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Outlined.Translate,null);Text(" Add an offline recognition language")
            }
            Text("Latin, Hindi/Devanagari, Chinese, Japanese and Korean models are bundled. Add Tesseract traineddata for Telugu or other scripts.",
                style=MaterialTheme.typography.bodySmall)
        }
        item{
            SectionSettings("Display privacy")
            Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Block screenshots and screen recording",fontWeight=FontWeight.SemiBold)
                    Text("Turn off to allow screenshots. Turn on before showing sensitive documents.",style=MaterialTheme.typography.bodySmall)
                }
                Switch(checked=privacyScreen,onCheckedChange=onPrivacyScreenChanged)
            }
        }
        item{
            SectionSettings("App lock")
            Text(if(hasPin)"App access PIN enabled. Documents are not encrypted." else "Files are stored unencrypted in private app storage. You can add an optional app-access PIN.",
                style=MaterialTheme.typography.bodySmall)
            if(hasPin)OutlinedTextField(old,{old=it},label={Text("Current PIN")},modifier=Modifier.fillMaxWidth(),
                visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
            OutlinedTextField(pin,{pin=it},label={Text("New PIN (at least 4 digits)")},modifier=Modifier.fillMaxWidth(),
                visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
            OutlinedTextField(confirmPin,{confirmPin=it},label={Text("Confirm new PIN")},modifier=Modifier.fillMaxWidth(),
                visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
            Button(onClick={onPin(pin,old);pin="";confirmPin=""},enabled=pin.length>=4&&pin==confirmPin&&(!hasPin||old.isNotBlank()),modifier=Modifier.fillMaxWidth()){Text("Set / change PIN")}
            if(hasPin)TextButton(onClick={onClearPin(old);old=""},enabled=old.isNotBlank()){Text("Remove PIN lock")}
        }
        item{
            SectionSettings("Notifications")
            OutlinedTextField(days,{days=it},label={Text("Remind before expiry (days)")},modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick={onRemindDays(days.toIntOrNull()?.coerceIn(1,365)?:30)},modifier=Modifier.fillMaxWidth()){
                Text("Save reminder preference")
            }
        }
        item {
            Text("No account or server required. Documents and ZIP backups are not encrypted. Files remain in app-private storage, and sharing grants access only to the selected files.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun SectionSettings(label:String) {
    Text(label,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(vertical=10.dp))
}
@Composable private fun SettingAction(title:String,desc:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit) {
    Card(onClick=onClick,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null);Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.SemiBold);Text(desc,style=MaterialTheme.typography.bodySmall)}
            Icon(Icons.Outlined.ChevronRight,null)
        }
    }
    Spacer(Modifier.height(8.dp))
}
@Composable fun BackupScreen(onBackup:(String)->Unit,onRestore:(String)->Unit) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding=PaddingValues(16.dp,18.dp,16.dp,30.dp),
        verticalArrangement=Arrangement.spacedBy(18.dp)){
        item{
            VaultHeader("Backup & Recovery","Keep a separate copy of your documents")
        }
        item{
            HubCard(Modifier.fillMaxWidth()){
                Row(verticalAlignment=Alignment.CenterVertically){
                    HubIcon(Icons.Outlined.FileUpload)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){
                        Text("Export backup",style=MaterialTheme.typography.titleLarge)
                        Text("Save documents, information, profiles and templates in one backup file.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(onClick={onBackup("")},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                    Icon(Icons.Outlined.FileUpload,null,Modifier.size(19.dp))
                    Text(" Export Backup")
                }
            }
        }
        item{
            HubCard(Modifier.fillMaxWidth()){
                Row(verticalAlignment=Alignment.CenterVertically){
                    HubIcon(Icons.Outlined.Restore)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){
                        Text("Restore backup",style=MaterialTheme.typography.titleLarge)
                        Text("Replace your current documents using a saved backup.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedButton(onClick={onRestore("")},
                    modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                    Icon(Icons.Outlined.Restore,null,Modifier.size(19.dp))
                    Text(" Choose Backup to Restore")
                }
                Text("You will be asked to confirm before existing documents are replaced.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    style=MaterialTheme.typography.bodySmall)
            }
        }
        item{
            Surface(shape=RoundedCornerShape(14.dp),
                color=MaterialTheme.colorScheme.surface,
                border=BorderStroke(1.dp,VaultColors.expiring)){
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment=Alignment.Top){
                    Icon(Icons.Outlined.Info,null,tint=VaultColors.expiring)
                    Spacer(Modifier.width(12.dp))
                    Column{
                        Text("Backups are not encrypted",style=MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(5.dp))
                        Text("Anyone who has the backup file may read its contents. Store it in a safe place.",
                            color=MaterialTheme.colorScheme.onSurfaceVariant,
                            style=MaterialTheme.typography.bodySmall)
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
