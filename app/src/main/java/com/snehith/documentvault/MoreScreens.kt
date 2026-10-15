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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.io.File
import java.util.UUID

@Composable fun DocumentViewerScreen(file:VaultFile,repo:VaultRepository,engine:DocumentEngine,onShare:()->Unit,onOcr:()->Unit) {
    val pdf=file.name.endsWith(".pdf",true)||file.mime=="application/pdf"
    var page by remember(file.id){mutableIntStateOf(0)}
    var pages by remember(file.id){mutableIntStateOf(1)}
    var bitmap by remember(file.id){mutableStateOf<Bitmap?>(null)}
    var pdfSession by remember(file.id){mutableStateOf<PdfPageSession?>(null)}
    var previewText by remember(file.id){mutableStateOf(file.extractedText)}
    var showText by remember(file.id){mutableStateOf(!pdf&&!file.mime.startsWith("image/"))}
    var error by remember(file.id){mutableStateOf("")}
    var zoom by remember(file.id){mutableFloatStateOf(1f)}
    var panX by remember(file.id){mutableFloatStateOf(0f)}
    var panY by remember(file.id){mutableFloatStateOf(0f)}
    var loading by remember(file.id){mutableStateOf(false)}

    DisposableEffect(file.id) {
        onDispose {pdfSession?.close()}
    }
    LaunchedEffect(file.id) {
        if(pdf)try{
            loading=true
            val session=withContext(Dispatchers.IO){PdfPageSession(repo.documentFile(file))}
            pdfSession=session
            pages=session.pageCount
        }catch(e:Exception){error=e.message?:"Cannot read PDF"}
        finally{loading=false}
    }
    LaunchedEffect(file.id,page,showText,pdfSession) {
        if(showText){
            if(previewText.isBlank()) {
                try{loading=true;previewText=withContext(Dispatchers.IO){engine.extract(file).text}}
                catch(e:Exception){error=e.message?:"Text extraction failed"}
                finally{loading=false}
            }
            return@LaunchedEffect
        }
        if(pdf && pdfSession==null)return@LaunchedEffect
        try{
            loading=true
            val rendered=withContext(Dispatchers.IO){
                if(pdf)pdfSession!!.render(page)
                else engine.scaledBitmap(repo.documentFile(file),1550)
            }
            bitmap=rendered
            error=""
        }catch(e:Exception){error=e.message?:"Unable to preview document"}
        finally{loading=false}
    }

    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(file.name,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            FilterChip(selected=!showText,onClick={showText=false},label={Text("Pages")})
            FilterChip(selected=showText,onClick={showText=true},label={Text("Text")})
            IconButton(onClick=onOcr){Icon(Icons.Outlined.DocumentScanner,"OCR")}
            IconButton(onClick=onShare){Icon(Icons.Outlined.Share,"Share")}
        }
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        if(showText){
            Surface(Modifier.weight(1f).fillMaxWidth(),shape=RoundedCornerShape(14.dp),
                color=MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
                    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(previewText.isBlank())Text("No selectable text. Run OCR to extract it.")
                    else SelectionContainer{Text(previewText)}
                }
            }
        }else {
            Surface(modifier=Modifier.weight(1f).fillMaxWidth(),shape=RoundedCornerShape(14.dp),
                color=MaterialTheme.colorScheme.surfaceContainerLow) {
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    val image=bitmap
                    if(image!=null){
                        Image(image.asImageBitmap(),contentDescription="Document page",
                            modifier=Modifier.fillMaxSize()
                                .pointerInput(file.id,page){detectTransformGestures{_,move,factor,_->
                                    zoom=(zoom*factor).coerceIn(1f,5f)
                                    panX=(panX+move.x).coerceIn(-1100f,1100f)
                                    panY=(panY+move.y).coerceIn(-1800f,1800f)
                                }}
                                .graphicsLayer(scaleX=zoom,scaleY=zoom,translationX=panX,translationY=panY),
                            contentScale=androidx.compose.ui.layout.ContentScale.Fit)
                    }
                    if(loading||image==null)CircularProgressIndicator()
                }
            }
            if(pdf){
                Row(modifier=Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.SpaceBetween){
                    OutlinedButton(onClick={page=(page-1).coerceAtLeast(0);zoom=1f;panX=0f;panY=0f},enabled=page>0){Text("Previous")}
                    Text((page+1).toString()+" / "+pages)
                    OutlinedButton(onClick={page=(page+1).coerceAtMost(pages-1);zoom=1f;panX=0f;panY=0f},enabled=page<pages-1){Text("Next")}
                }
            }else Text("Pinch to zoom and drag to pan.",style=MaterialTheme.typography.labelSmall)
        }
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
@Composable fun PdfToolsScreen(card:VaultCard,onAdd:()->Unit,
    onExport:(String,List<VaultFile>,Int,Int)->Unit) {
    val selected=remember(card.id){mutableStateListOf<String>().apply{addAll(card.files.map{it.id})}}
    var start by remember{mutableStateOf("1")}
    var end by remember{mutableStateOf("1")}
    val selectedFiles=selected.mapNotNull{id->card.files.firstOrNull{it.id==id}}
    val combinedPossible=selectedFiles.isNotEmpty()&&selectedFiles.all{
        it.mime=="application/pdf"||it.name.endsWith(".pdf",true)||it.mime.startsWith("image/")
    }
    LazyColumn(Modifier.fillMaxSize().background(VaultNavy),
        contentPadding=PaddingValues(15.dp,12.dp,15.dp,95.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {
            VaultHeader("PDF Studio",card.title+" · Combine, reorder, split, rotate or export")
            Spacer(Modifier.height(8.dp))
            Button(onClick=onAdd,modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Outlined.Add,null);Text(" Add scans, photos or PDF files")
            }
            Text("Select attachments below. Use ↑ and ↓ to choose their order in the combined PDF.",
                color=VaultMuted,style=MaterialTheme.typography.bodySmall)
        }
        items(selectedFiles,key={it.id}){file->
            Card(colors=CardDefaults.cardColors(containerColor=VaultPanel),
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),shape=RoundedCornerShape(10.dp)) {
                Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically){
                    Checkbox(checked=true,onCheckedChange={selected.remove(file.id)})
                    Icon(if(file.mime.startsWith("image/"))Icons.Outlined.Image else Icons.Outlined.PictureAsPdf,
                        null,tint=VaultSky)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)){
                        Text(file.name,color=MaterialTheme.colorScheme.onSurface,maxLines=2)
                        Text("PDF page or document",color=VaultMuted,style=MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick={
                        val i=selected.indexOf(file.id)
                        if(i>0){selected.removeAt(i);selected.add(i-1,file.id)}
                    }){Icon(Icons.Outlined.KeyboardArrowUp,null,tint=VaultSky)}
                    IconButton(onClick={
                        val i=selected.indexOf(file.id)
                        if(i<selected.lastIndex){selected.removeAt(i);selected.add(i+1,file.id)}
                    }){Icon(Icons.Outlined.KeyboardArrowDown,null,tint=VaultSky)}
                }
            }
        }
        if(card.files.size>selected.size)item {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                card.files.filter{it.id !in selected}.forEach{f->
                    AssistChip(onClick={selected.add(f.id)},label={Text("+ "+f.name.take(20))})
                }
            }
        }
        item {
            Button(onClick={onExport("combine",selectedFiles,1,1)},
                enabled=combinedPossible,modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Outlined.PictureAsPdf,null);Text(" Create one PDF from selected files")
            }
        }
        item {
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Text("More PDF tools",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(start,{start=it},label={Text("From page")},modifier=Modifier.weight(1f),singleLine=true)
                OutlinedTextField(end,{end=it},label={Text("To page")},modifier=Modifier.weight(1f),singleLine=true)
            }
            Spacer(Modifier.height(9.dp))
            OutlinedButton(onClick={onExport("split",selectedFiles,start.toIntOrNull()?:1,end.toIntOrNull()?:1)},
                enabled=selectedFiles.size==1&&selectedFiles[0].name.endsWith(".pdf",true),
                modifier=Modifier.fillMaxWidth()){Text("Export selected page range")}
            OutlinedButton(onClick={onExport("rotate",selectedFiles,1,1)},
                enabled=selectedFiles.size==1&&selectedFiles[0].name.endsWith(".pdf",true),
                modifier=Modifier.fillMaxWidth()){Text("Rotate PDF 90°")}
            OutlinedButton(onClick={onExport("searchable",selectedFiles,1,1)},
                enabled=selectedFiles.size==1&&selectedFiles[0].name.endsWith(".pdf",true)&&selectedFiles[0].extractedText.isNotBlank(),
                modifier=Modifier.fillMaxWidth()){Text("Export searchable PDF")}
            Text("Documents remain unchanged. The combined PDF is exported to a location you choose.",
                style=MaterialTheme.typography.bodySmall,color=VaultMuted)
        }
    }
}
@Composable fun ProfilesScreen(profiles:List<VaultProfile>,onSave:(VaultProfile)->Unit) {
    var name by remember{mutableStateOf("")}
    LazyColumn(contentPadding=PaddingValues(16.dp,12.dp,16.dp,90.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Heading("Family profiles","Organize documents by person without uploading identity details")}
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
        item{Heading("Card templates","Start from a structure and edit any field")}
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
                Icon(Icons.Outlined.Translate,null);Text(" Import offline .traineddata OCR pack")
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Heading("Backup & Restore","Export your cards and documents as a standard ZIP file")
        Text("Backups are NOT encrypted. Anyone with the ZIP can read its documents and personal details.",
            color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
        Text("The ZIP includes card fields, OCR text, templates, family profiles and original attachments.",
            style=MaterialTheme.typography.bodyMedium)
        Button(onClick={onBackup("")},modifier=Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.FileUpload,null);Text(" Export ZIP backup")
        }
        HorizontalDivider()
        Text("Restoring replaces the card list. Create a ZIP backup first. Older encrypted .dvault backups are not supported in this plaintext test version.",
            style=MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick={onRestore("")},modifier=Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Restore,null);Text(" Restore ZIP backup")
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
        item{Text(files.size.toString()+" unattached files found. These are not automatically deleted. Recovery only applies to this installation.",
            style=MaterialTheme.typography.bodyMedium)}
        items(files){file->
            ElevatedCard {
                Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(file.name,fontWeight=FontWeight.SemiBold)
                    Text(file.id,style=MaterialTheme.typography.labelSmall)
                    OutlinedButton(onClick={selected=file;name="Recovered "+file.id.take(8)}){Text("Recover into new card")}
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
