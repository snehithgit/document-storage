package com.snehith.documentvault

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private data class ReviewJob(val cardId:String,val file:VaultFile,val analysis:ExtractedDocument)
private data class PdfJob(val op:String,val files:List<VaultFile>,val from:Int,val to:Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DocumentVaultApp(activity:MainActivity) {
    val repo=activity.repo
    val engine=activity.engine
    val scope=rememberCoroutineScope()
    var route by remember{mutableStateOf("home")}
    var selectedCardId by remember{mutableStateOf("")}
    var editing by remember{mutableStateOf<VaultCard?>(null)}
    var viewing by remember{mutableStateOf<VaultFile?>(null)}
    var importTarget by remember{mutableStateOf<String?>(null)}
    var userProfile by remember{mutableStateOf("all")}
    var ocrLanguage by remember{mutableStateOf("Latin")}
    var busy by remember{mutableStateOf("")}
    var error by remember{mutableStateOf("")}
    var pdfJob by remember{mutableStateOf<PdfJob?>(null)}
    var backupPassword by remember{mutableStateOf("")}
    val reviewQueue= remember{mutableStateListOf<ReviewJob>()}
    val dark=androidx.compose.foundation.isSystemInDarkTheme()
    val scheme=if(dark)darkColorScheme(primary=Color(0xFFA9B8FF),secondary=Color(0xFF65D5C7))
        else lightColorScheme(primary=Color(0xFF4361EE),secondary=Color(0xFF078F81),background=Color(0xFFF8FAFF))
    val current=repo.cards.firstOrNull{it.id==selectedCardId}
    val regular=route in setOf("home","cards","scan","search","more")
    fun openCard(c:VaultCard){selectedCardId=c.id;route="detail"}
    fun startEdit(card:VaultCard){editing=card;route="edit"}
    fun goBack(){
        when(route) {
            "edit"->route=if(repo.cards.any{it.id==editing?.id})"detail" else "cards"
            "viewer","tools"->route="detail"
            "detail"->route="cards"
            else->route="more"
        }
    }
    fun startOcr(cardId:String,f:VaultFile) {
        busy="Reading "+f.name
        scope.launch {
            try{
                val result=withContext(Dispatchers.IO){engine.extract(f,ocrLanguage)}
                val fresh=repo.cards.firstOrNull{it.id==cardId} ?:return@launch
                val update=f.copy(extractedText=result.text,ocrStatus=result.method)
                repo.saveCard(fresh.copy(files=fresh.files.map{if(it.id==f.id)update else it}))
                if(result.fields.isNotEmpty()) {reviewQueue.add(ReviewJob(cardId,update,result));route="review"}
                else {activity.notify("Text extracted. Open the document Text tab to copy it.");route="detail"}
            }catch(e:Exception){error="OCR failed: "+(e.message?:"Unknown format")}
            finally{busy=""}
        }
    }
    fun importUris(uris:List<Uri>,targetId:String?=null) {
        if(uris.isEmpty())return
        busy="Importing "+uris.size+" documents"
        scope.launch {
            var lastCard:VaultCard?=null
            try {
                for((index,uri) in uris.withIndex()) {
                    val file=withContext(Dispatchers.IO){repo.importFile(uri)}
                    if(repo.isDuplicate(file.sha256)) {
                        withContext(Dispatchers.IO){repo.removeOrphan(file)}
                        activity.notify("Duplicate skipped: "+file.name)
                        continue
                    }
                    val existing=targetId?.let{id->repo.cards.firstOrNull{it.id==id}}
                    val card=if(existing!=null)existing.copy(files=existing.files+file)
                        else {
                            val t=when {
                                file.name.contains("passport",true)->"Passport"
                                file.name.contains("cdc",true)->"Seafarer CDC"
                                file.name.contains("medical",true)->"Medical"
                                file.name.contains("certificate",true)->"COC / STCW"
                                else -> "Custom"
                            }
                            repo.newCard(t,if(userProfile=="all")"self" else userProfile)
                                .copy(title=file.name.substringBeforeLast('.',file.name),files=listOf(file))
                        }
                    repo.saveCard(card)
                    lastCard=card
                    busy="Scanning "+(index+1)+"/"+uris.size+": "+file.name
                    try {
                        val analysis=withContext(Dispatchers.IO){engine.extract(file,ocrLanguage)}
                        val updatedFile=file.copy(extractedText=analysis.text,ocrStatus=analysis.method)
                        val fresh=repo.cards.first{it.id==card.id}
                        repo.saveCard(fresh.copy(files=fresh.files.map{if(it.id==file.id)updatedFile else it}))
                        if(analysis.fields.isNotEmpty())reviewQueue.add(ReviewJob(card.id,updatedFile,analysis))
                    } catch(e:Exception) {
                        val fresh=repo.cards.first{it.id==card.id}
                        repo.saveCard(fresh.copy(files=fresh.files.map{if(it.id==file.id)it.copy(ocrStatus="Manual review required")else it}))
                    }
                }
                if(reviewQueue.isNotEmpty()){route="review"}
                else if(lastCard!=null)openCard(repo.cards.first{it.id==lastCard!!.id})
            }catch(e:Exception){error="Import failed: "+(e.message?:"Unknown error")}
            finally {busy="";importTarget=null}
        }
    }
    val importPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uriList->
        importUris(uriList,importTarget)
    }
    var cameraUri by remember{mutableStateOf<Uri?>(null)}
    val cameraLauncher=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success->
        if(success)cameraUri?.let{importUris(listOf(it),importTarget)}
    }
    fun camera(target:String?=null) {
        importTarget=target
        val f=File(activity.cacheDir,"camera").apply{mkdirs()}.let{File(it,"capture-"+UUID.randomUUID()+".jpg")}
        f.createNewFile()
        cameraUri=FileProvider.getUriForFile(activity,activity.packageName+".provider",f)
        cameraLauncher.launch(cameraUri!!)
    }
    var pdfJobToExport by remember{mutableStateOf<PdfJob?>(null)}
    val pdfExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){uri->
        val job=pdfJobToExport
        if(uri!=null&&job!=null) {
            busy="Creating PDF"
            scope.launch{
                try{withContext(Dispatchers.IO){
                    when(job.op){
                        "merge"->engine.merge(job.files,uri)
                        "split"->engine.pages(job.files.first(),job.from,job.to,uri)
                        "rotate"->engine.rotate(job.files.first(),90,uri)
                        "images"->engine.imagesToPdf(job.files,uri)
                        "searchable"->engine.searchableCopy(job.files.first(),uri)
                    }
                };activity.notify("PDF exported successfully")}
                catch(e:Exception){error="PDF export failed: "+(e.message?:"Error")}
                finally{busy="";pdfJobToExport=null}
            }
        }
    }
    val modelPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)try{val lang=engine.installLanguage(uri);ocrLanguage="Tesseract: "+lang;activity.notify("Installed "+lang+" offline OCR")}
        catch(e:Exception){error=e.message?:"Could not install language"}
    }
    val backupExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
        if(uri!=null){
            busy="Encrypting backup"
            scope.launch{
                try{withContext(Dispatchers.IO){repo.exportBackup(uri,backupPassword)};activity.notify("Backup exported")}
                catch(e:Exception){error="Backup failed: "+(e.message?:"Error")}
                finally{busy=""}
            }
        }
    }
    var restoreUri by remember{mutableStateOf<Uri?>(null)}
    var confirmRestore by remember{mutableStateOf(false)}
    val backupImport=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null){restoreUri=uri;confirmRestore=true}
    }
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(!granted) activity.notify("Enable notifications in system settings to receive reminders")
    }
    LaunchedEffect(activity.incoming.size,activity.unlocked.value){
        if(activity.unlocked.value&&activity.incoming.isNotEmpty()){
            val received=activity.incoming.toList()
            activity.incoming.clear()
            importUris(received)
        }
    }
    if(confirmRestore) {
        AlertDialog(onDismissRequest={confirmRestore=false},title={Text("Replace current vault?")},
            text={Text("Restoring a backup will replace every card and attachment in the current vault. This cannot be undone without your own backup.")},
            confirmButton={TextButton(onClick={
                confirmRestore=false
                val uri=restoreUri
                if(uri!=null){
                    busy="Restoring encrypted backup"
                    scope.launch{
                        try{withContext(Dispatchers.IO){repo.restoreBackup(uri,backupPassword)};route="home";activity.notify("Restore complete")}
                        catch(e:Exception){error="Restore failed: "+(e.message?:"Wrong password or damaged archive")}
                        finally{busy=""}
                    }
                }
            }){Text("Replace and restore")}},
            dismissButton={TextButton(onClick={confirmRestore=false}){Text("Cancel")}})
    }
    if(error.isNotBlank()) {
        AlertDialog(onDismissRequest={error=""},title={Text("Document Vault")},text={Text(error)},
            confirmButton={TextButton(onClick={error=""}){Text("OK")}})
    }

    MaterialTheme(colorScheme=scheme,shapes=Shapes(medium=RoundedCornerShape(17.dp),large=RoundedCornerShape(22.dp))) {
        if(!activity.unlocked.value) {
            UnlockScreen(onPin={pin->
                if(repo.checkPin(pin)){activity.unlocked.value=true;true}else false
            },onBiometric={activity.authenticate({activity.unlocked.value=true},{activity.notify(it)})})
        } else {
            androidx.activity.compose.BackHandler(enabled=!regular||route!="home"){goBack()}
            Scaffold(
                topBar={
                    if(!regular)TopAppBar(title={Text(when(route){
                        "detail"->current?.title ?: "Document"
                        "edit"->"Edit information"
                        "viewer"->"Document Viewer"
                        "review"->"OCR Review"
                        "tools"->"PDF Utilities"
                        "expiry"->"Expiry & renewal"
                        "profiles"->"Family profiles"
                        "templates"->"Card templates"
                        "backup"->"Encrypted backup"
                        else->"Document Vault"
                    },maxLines=1)},
                        navigationIcon={IconButton(onClick={goBack}){Icon(Icons.Outlined.ArrowBack,"Back")}})
                    else TopAppBar(title={
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Inventory2,null,tint=MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(9.dp));Text("Document Vault",fontWeight=FontWeight.Bold)
                        }
                    },actions={
                        IconButton(onClick={route="expiry"}){Icon(Icons.Outlined.NotificationsActive,"Expiry alerts")}
                        IconButton(onClick={route="more"}){Icon(Icons.Outlined.Settings,"Settings")}
                    })
                },
                bottomBar={
                    if(regular)NavigationBar {
                        listOf(
                            Triple("home",Icons.Outlined.Home,"Home"),
                            Triple("cards",Icons.Outlined.Dashboard,"Cards"),
                            Triple("scan",Icons.Outlined.DocumentScanner,"Scan"),
                            Triple("search",Icons.Outlined.Search,"Search"),
                            Triple("more",Icons.Outlined.Settings,"More")
                        ).forEach{(id,icon,label)->
                            NavigationBarItem(selected=route==id,onClick={route=id},icon={Icon(icon,label)},label={Text(label)})
                        }
                    }
                }
            ){ padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when(route){
                        "home"->VaultHome(repo.cards.toList(),repo.profiles.toList(),userProfile,{userProfile=it},{openCard(it)},
                            {startEdit(repo.newCard(profile=if(userProfile=="all")"self" else userProfile))},
                            {importTarget=null;importPicker.launch(arrayOf("*/*"))},{camera()},{route="expiry"})
                        "cards"->VaultLibrary(repo.cards.toList(),repo.profiles.toList(),{openCard(it)},{startEdit(repo.newCard())})
                        "search"->VaultSearch(repo.cards.toList()){openCard(it)}
                        "scan"->ScanImportScreen({camera()},{importTarget=null;importPicker.launch(arrayOf("*/*"))},{startEdit(repo.newCard())})
                        "more"->SettingsScreen(repo.hasPin(),engine.availableLanguages(),ocrLanguage,
                            {pin->try{repo.setPin(pin);activity.notify("PIN saved")}catch(e:Exception){error=e.message?:"PIN failed"}},
                            {pin->if(repo.checkPin(pin)){repo.clearPin();activity.notify("PIN removed")} else activity.notify("Wrong PIN")},
                            {route="profiles"},{route="templates"},{route="backup"},
                            {modelPicker.launch(arrayOf("*/*"))},{ocrLanguage=it},
                            {days->activity.getSharedPreferences("vault_options",Context.MODE_PRIVATE).edit().putInt("remindDays",days).apply()
                                if(Build.VERSION.SDK_INT>=33)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                activity.notify("Expiry reminders: "+days+" days")})
                        "detail"->current?.let{c->CardDetailScreen(c,{startEdit(c)},
                            {importTarget=c.id;importPicker.launch(arrayOf("*/*"))},
                            {f->viewing=f;route="viewer"},
                            {f->startOcr(c.id,f)},{f->activity.share(f)},{activity.shareAll(c.files)},
                            {route="tools"},
                            {startEdit(c.copy(id=UUID.randomUUID().toString(),title=c.title+" (renewal)",files=emptyList(),
                                expiry="",previousId=c.id,created=System.currentTimeMillis()))},
                            {repo.deleteCard(c);route="cards"})}
                        "edit"->editing?.let{c->CardEditorScreen(c,repo.profiles.toList(),
                            {saved->repo.saveCard(saved);selectedCardId=saved.id;editing=null;route="detail"},
                            {goBack()},
                            {t->repo.saveTemplate(t);activity.notify("Saved template "+t.name)})}
                        "viewer"->viewing?.let{f->DocumentViewerScreen(f,repo,engine,{activity.share(f)},
                            {startOcr(selectedCardId,f)})}
                        "review"->{
                            val job=reviewQueue.firstOrNull()
                            if(job!=null)OcrReviewScreen(job.file.name,job.analysis,
                                {fields->
                                    val original=repo.cards.firstOrNull{it.id==job.cardId}
                                    if(original!=null){
                                        val previous=original.fields.filter{it.value.isNotBlank()}
                                        val merged=previous+fields.filter{it.name.isNotBlank()&&it.value.isNotBlank()&&previous.none{p->p.name.equals(it.name,true)}}
                                        val expiryField=merged.firstOrNull{it.name.contains("expiry",true)}
                                        val expiry=if(original.expiry.isNotBlank())original.expiry else expiryField?.value?.let{date->
                                            try{LocalDate.parse(date,DateTimeFormatter.ofPattern("dd/MM/yyyy")).toString()}
                                            catch(_:Exception){try{LocalDate.parse(date).toString()}catch(_:Exception){""}}
                                        } ?: ""
                                        repo.saveCard(original.copy(fields=merged,expiry=expiry))
                                        selectedCardId=job.cardId
                                    }
                                    reviewQueue.removeAt(0)
                                    route=if(reviewQueue.isEmpty())"detail" else "review"
                                },{
                                    selectedCardId=job.cardId;reviewQueue.removeAt(0)
                                    route=if(reviewQueue.isEmpty())"detail" else "review"
                                })
                            else route="detail"
                        }
                        "tools"->current?.let{c->PdfToolsScreen(c){op,files,from,to->
                            pdfJobToExport=PdfJob(op,files,from,to)
                            pdfExport.launch("DocumentVault_"+op+"_"+System.currentTimeMillis()+".pdf")
                        }}
                        "expiry"->ExpiriesScreen(repo.cards.toList()){openCard(it)}
                        "profiles"->ProfilesScreen(repo.profiles.toList()){p->repo.saveProfile(p)}
                        "templates"->TemplatesScreen(FieldSuggestions.templates+repo.customTemplates){template->
                            val card=repo.newCard().copy(title=template.name,kind=template.name,fields=template.fieldNames.map{DataField(it,"")})
                            startEdit(card)
                        }
                        "backup"->BackupScreen(
                            {pwd->backupPassword=pwd;backupExport.launch("DocumentVault_"+LocalDate.now()+".dvault")},
                            {pwd->backupPassword=pwd;backupImport.launch(arrayOf("*/*"))}
                        )
                    }
                    if(busy.isNotBlank()) {
                        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.scrim.copy(alpha=.62f)) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                                CircularProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text(busy,color=Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun UnlockScreen(onPin:(String)->Boolean,onBiometric:()->Unit) {
    var pin by remember{mutableStateOf("")}
    var invalid by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Lock,null,tint=Color(0xFF4361EE),modifier=Modifier.size(65.dp))
        Spacer(Modifier.height(24.dp))
        Text("Document Vault",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text("Unlock your encrypted personal library")
        Spacer(Modifier.height(30.dp))
        OutlinedTextField(pin,{pin=it;invalid=false},label={Text("Vault PIN")},visualTransformation=PasswordVisualTransformation(),
            singleLine=true,modifier=Modifier.fillMaxWidth())
        if(invalid)Text("Incorrect PIN",color=MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick={if(!onPin(pin))invalid=true},modifier=Modifier.fillMaxWidth(),enabled=pin.isNotBlank()){Text("Unlock")}
        OutlinedButton(onClick=onBiometric,modifier=Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Fingerprint,null);Text(" Use biometrics")
        }
    }
}
