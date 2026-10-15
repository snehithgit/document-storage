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
    var page by remember(file.id){mutableIntStateOf(0)}
    var pageCount by remember(file.id){mutableIntStateOf(1)}
    var bitmap by remember(file.id){mutableStateOf<Bitmap?>(null)}
    var previewText by remember(file.id){mutableStateOf(file.extractedText)}
    var showText by remember(file.id){mutableStateOf(!file.name.endsWith(".pdf",true)&& !file.mime.startsWith("image/"))}
    var error by remember(file.id){mutableStateOf("")}
    var zoom by remember(file.id){mutableFloatStateOf(1f)}
    LaunchedEffect(file.id,page,showText) {
        if(showText && previewText.isBlank()) {
            try {previewText=withContext(Dispatchers.IO){engine.extract(file).text}}
            catch(e:Exception){error=e.message?:"Text extraction failed"}
        }
        if(showText)return@LaunchedEffect
        try{
            val result=withContext(Dispatchers.IO) {
                val plain=repo.openDecrypted(file)
                try{
                    if(file.name.endsWith(".pdf",true))engine.pdfPageImage(plain,page)
                    else engine.scaledBitmap(plain,2100) to 1
                }finally{plain.delete()}
            }
            bitmap=result.first
            pageCount=result.second
            error=""
        }catch(e:Exception){error=e.message?:"Unsupported preview"}
    }
    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text(file.name,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            FilterChip(selected=!showText,onClick={showText=false},label={Text("Pages")})
            FilterChip(selected=showText,onClick={showText=true},label={Text("Text")})
            IconButton(onClick=onOcr){Icon(Icons.Outlined.DocumentScanner,"OCR")}
            IconButton(onClick=onShare){Icon(Icons.Outlined.Share,"Share")}
        }
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        if(showText) {
            Surface(modifier=Modifier.weight(1f).fillMaxWidth(),shape=RoundedCornerShape(14.dp),
                color=MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
                    if(previewText.isBlank())Text("No selectable text. Run OCR to extract it.")
                    else SelectionContainer {Text(previewText)}
                }
            }
        } else {
            Surface(modifier=Modifier.weight(1f).fillMaxWidth(),shape=RoundedCornerShape(14.dp),
                color=MaterialTheme.colorScheme.surfaceContainerLow) {
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    if(bitmap!=null) {
                        Image(bitmap!!.asImageBitmap(),contentDescription="Document page",
                            modifier=Modifier.fillMaxSize().graphicsLayer(scaleX=zoom,scaleY=zoom)
                                .pointerInput(file.id,page){detectTransformGestures{_,_,factor,_->zoom=(zoom*factor).coerceIn(1f,5f)}},
                            contentScale=androidx.compose.ui.layout.ContentScale.Fit)
                    } else CircularProgressIndicator()
                }
            }
            if(file.name.endsWith(".pdf",true)){
                Row(modifier=Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.SpaceBetween){
                    OutlinedButton(onClick={page=(page-1).coerceAtLeast(0);zoom=1f},enabled=page>0){Text("Previous")}
                    Text((page+1).toString()+" / "+pageCount)
                    OutlinedButton(onClick={page=(page+1).coerceAtMost(pageCount-1);zoom=1f},enabled=page<pageCount-1){Text("Next")}
                }
            }else {
                Text("Pinch to zoom. OCR enables copy and search of scanned text.",
                    style=MaterialTheme.typography.labelSmall)
            }
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
@Composable fun PdfToolsScreen(card:VaultCard,onExport:(String,List<VaultFile>,Int,Int)->Unit) {
    val files=card.files
    val selected=remember(card.id){mutableStateListOf<String>()}
    var start by remember{mutableStateOf("1")}
    var end by remember{mutableStateOf("1")}
    val selection=files.filter{it.id in selected}
    LazyColumn(contentPadding=PaddingValues(16.dp,14.dp,16.dp,90.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Heading("PDF Utilities","Merge, split, rotate, rasterize images and export searchable PDFs")}
        items(files){file->
            Row(verticalAlignment=Alignment.CenterVertically){
                Checkbox(checked=selected.contains(file.id),onCheckedChange={checked->
                    if(checked)selected.add(file.id) else selected.remove(file.id)
                })
                Column(Modifier.weight(1f)) {
                    Text(file.name,fontWeight=FontWeight.SemiBold,maxLines=1)
                    Text(file.mime,style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(start,{start=it},label={Text("From page")},modifier=Modifier.weight(1f),singleLine=true)
                OutlinedTextField(end,{end=it},label={Text("To page")},modifier=Modifier.weight(1f),singleLine=true)
            }
        }
        item{
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Button(onClick={onExport("merge",selection,1,1)},enabled=selection.size>=2&&selection.all{it.name.endsWith(".pdf",true)},modifier=Modifier.fillMaxWidth()){Text("Merge selected PDFs")}
                OutlinedButton(onClick={onExport("split",selection,start.toIntOrNull()?:1,end.toIntOrNull()?:1)},
                    enabled=selection.size==1&&selection.firstOrNull()?.name?.endsWith(".pdf",true)==true,modifier=Modifier.fillMaxWidth()){Text("Extract selected PDF pages")}
                OutlinedButton(onClick={onExport("rotate",selection,1,1)},
                    enabled=selection.size==1&&selection.firstOrNull()?.name?.endsWith(".pdf",true)==true,modifier=Modifier.fillMaxWidth()){Text("Rotate all PDF pages 90°")}
                OutlinedButton(onClick={onExport("images",selection,1,1)},
                    enabled=selection.isNotEmpty()&&selection.all{it.mime.startsWith("image/")},modifier=Modifier.fillMaxWidth()){Text("Create PDF from selected images")}
                OutlinedButton(onClick={onExport("searchable",selection,1,1)},
                    enabled=selection.size==1&&selection.firstOrNull()?.name?.endsWith(".pdf",true)==true&&selection.first().extractedText.isNotBlank(),
                    modifier=Modifier.fillMaxWidth()){Text("Export searchable PDF (OCR text)")}
            }
        }
        item{
            Text("Originals remain unchanged. Searchable PDF export uses reviewed OCR text as an invisible Latin text layer. Other scripts require further PDF font support.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
@Composable fun SettingsScreen(hasPin:Boolean,languages:List<String>,currentScript:String,onPin:(String)->Unit,onClearPin:(String)->Unit,
    onProfiles:()->Unit,onTemplates:()->Unit,onBackups:()->Unit,onImportLanguage:()->Unit,onScript:(String)->Unit,
    onRemindDays:(Int)->Unit) {
    var pin by remember{mutableStateOf("")}
    var old by remember{mutableStateOf("")}
    var languageOpen by remember{mutableStateOf(false)}
    var days by remember{mutableStateOf("30")}
    LazyColumn(contentPadding=PaddingValues(16.dp,12.dp,16.dp,90.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
        item{Heading("Vault Settings","Offline-first, encrypted storage and local device processing")}
        item{
            SectionSettings("Organization")
            SettingAction("Family profiles","Create and manage people",Icons.Outlined.People,onProfiles)
            SettingAction("Card templates","Reusable information fields",Icons.Outlined.ViewAgenda,onTemplates)
            SettingAction("Encrypted backup & restore","Export a portable password-protected file",Icons.Outlined.Backup,onBackups)
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
            SectionSettings("App lock")
            Text(if(hasPin)"A PIN is configured. Device biometrics can unlock the app." else "Vault files are encrypted. Set a PIN for access control.",
                style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(pin,{pin=it},label={Text("New PIN (at least 4 digits)")},modifier=Modifier.fillMaxWidth())
            Button(onClick={onPin(pin);pin=""},enabled=pin.length>=4,modifier=Modifier.fillMaxWidth()){Text("Set / change PIN")}
            if(hasPin) {
                OutlinedTextField(old,{old=it},label={Text("Current PIN to remove lock")},modifier=Modifier.fillMaxWidth())
                TextButton(onClick={onClearPin(old);old=""},enabled=old.isNotEmpty()){Text("Remove PIN lock")}
            }
        }
        item{
            SectionSettings("Notifications")
            OutlinedTextField(days,{days=it},label={Text("Remind before expiry (days)")},modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick={onRemindDays(days.toIntOrNull()?.coerceIn(1,365)?:30)},modifier=Modifier.fillMaxWidth()){
                Text("Save reminder preference")
            }
        }
        item {
            Text("No account or server required. Documents remain encrypted in app-private storage. Sharing and export temporarily decrypt only the files you choose.",
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
    var password by remember{mutableStateOf("")}
    var confirm by remember{mutableStateOf("")}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Heading("Encrypted Backup","Portable archive for transferring or recovering the entire vault")
        AccentChip("AES-256-GCM + PBKDF2 password protection",Color(0xFF0F9488))
        Text("Backup contains cards, custom templates, family profiles, attachments and saved OCR text. The original documents remain private.",style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(password,{password=it},label={Text("Backup password (8+ characters)")},
            modifier=Modifier.fillMaxWidth(),visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
        OutlinedTextField(confirm,{confirm=it},label={Text("Confirm backup password")},modifier=Modifier.fillMaxWidth(),
            visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
        Button(onClick={onBackup(password)},modifier=Modifier.fillMaxWidth(),enabled=password.length>=8&&password==confirm) {
            Icon(Icons.Outlined.FileUpload,null);Text(" Export protected backup")
        }
        HorizontalDivider()
        Text("Restoring replaces the current vault. Create a backup first. Losing the backup password means the archive cannot be decrypted.",
            style=MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick={onRestore(password)},modifier=Modifier.fillMaxWidth(),enabled=password.isNotBlank()){
            Icon(Icons.Outlined.Restore,null);Text(" Restore from backup")
        }
    }
}
