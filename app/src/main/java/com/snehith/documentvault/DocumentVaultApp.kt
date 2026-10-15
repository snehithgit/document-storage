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
import androidx.compose.runtime.saveable.rememberSaveable
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
    var route by rememberSaveable{mutableStateOf("home")}
    var privacyScreen by remember{mutableStateOf(activity.isPrivacyScreenEnabled())}
    var lastRoot by rememberSaveable{mutableStateOf("cards")}
    var selectedCardId by rememberSaveable{mutableStateOf("")}
    var editing by remember{mutableStateOf<VaultCard?>(null)}
    var viewing by remember{mutableStateOf<VaultFile?>(null)}
    var cropBitmap by remember{mutableStateOf<android.graphics.Bitmap?>(null)}
    var cropResult by remember{mutableStateOf<DetectedScan?>(null)}
    var cameraFile by remember{mutableStateOf<File?>(null)}
    var scanTarget by remember{mutableStateOf<String?>(null)}
    var importTarget by remember{mutableStateOf<String?>(null)}
    var userProfile by rememberSaveable{mutableStateOf("all")}
    var ocrLanguage by rememberSaveable{mutableStateOf("Latin")}
    var busy by remember{mutableStateOf("")}
    var error by remember{mutableStateOf("")}
    var pdfJob by remember{mutableStateOf<PdfJob?>(null)}
    var backupPassword by remember{mutableStateOf("")}
    val reviewQueue= remember{mutableStateListOf<ReviewJob>()}
    val scheme=darkColorScheme(
        primary=VaultBlue,onPrimary=Color.White,
        secondary=VaultSky,background=VaultNavy,onBackground=Color.White,
        surface=VaultRaised,onSurface=Color.White,
        surfaceContainer=VaultPanel,surfaceContainerLow=VaultPanel,
        outline=Color(0xFF64819E),onSurfaceVariant=VaultMuted)
    val current=repo.cards.firstOrNull{it.id==selectedCardId}
    val regular=route in setOf("home","cards","scan","search","more")
    fun openCard(c:VaultCard){if(regular)lastRoot=route;selectedCardId=c.id;route="detail"}
    fun startEdit(card:VaultCard){editing=card;route="edit"}
    fun goBack(){
        when(route) {
            "edit"->route=if(repo.cards.any{it.id==editing?.id})"detail" else lastRoot
            "viewer","tools","fulldetails"->route="detail"
            "selectfields"->route="fulldetails"
            "crop"->route=if(scanTarget!=null)"detail" else "scan"
            "review"->{if(reviewQueue.isNotEmpty())reviewQueue.removeAt(0);route=if(reviewQueue.isNotEmpty())"review" else if(selectedCardId.isNotBlank())"detail" else "cards"}
            "detail"->route=lastRoot
            "profiles","templates","backup","recovery"->route="more"
            "expiry"->route="home"
            else->route="home"
        }
    }
    fun startOcr(cardId:String,f:VaultFile) {
        busy="Reading "+f.name
        scope.launch {
            try{
                val result=withContext(Dispatchers.IO){engine.extract(f,ocrLanguage)}
                val fresh=repo.cards.firstOrNull{it.id==cardId} ?:return@launch
                val update=f.copy(extractedText=result.text,ocrStatus=result.method)
                withContext(Dispatchers.IO){repo.saveCard(fresh.copy(files=fresh.files.map{if(it.id==f.id)update else it}))}
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
                    withContext(Dispatchers.IO){repo.saveCard(card)}
                    lastCard=card
                    busy="Importing "+(index+1)+"/"+uris.size+": "+file.name
                    // Import now finishes as soon as the file is saved. OCR is opt-in
                    // from each document's OCR button, so a scan cannot stall View/Share.
                }
                if(lastCard!=null) {
                    openCard(repo.cards.first{it.id==lastCard!!.id})
                    activity.notify("Imported. Tap OCR to extract and copy information.")
                }
            }catch(e:Exception){error="Import failed: "+(e.message?:"Unknown error")}
            finally {busy="";importTarget=null}
        }
    }
    val importPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uriList->
        importUris(uriList,importTarget)
    }
    var cameraUri by remember{mutableStateOf<Uri?>(null)}
    val cameraLauncher=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success->
        if(success){
            val uri=cameraUri
            if(uri!=null)scope.launch{
                busy="Detecting document borders"
                try{
                    val(bitmap,detected)=withContext(Dispatchers.IO){
                        val file=cameraFile ?: throw IllegalStateException("Camera file missing")
                        val image=engine.scaledBitmap(file,2050)
                        image to DocumentCropEngine.detect(image)
                    }
                    cropBitmap=bitmap;cropResult=detected;route="crop"
                }catch(e:Exception){error="Scan preparation failed: "+(e.message?:"Unknown error")}
                finally{busy=""}
            }
        }
    }
    fun camera(target:String?=null) {
        importTarget=target;scanTarget=target
        val f=File(activity.cacheDir,"camera").apply{mkdirs()}.let{File(it,"capture-"+UUID.randomUUID()+".jpg")}
        f.createNewFile()
        cameraFile=f
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
                        "combine"->engine.combineDocuments(job.files,uri)
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
    val backupExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->
        if(uri!=null){
            busy="Exporting ZIP backup"
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
                    busy="Restoring ZIP backup"
                    scope.launch{
                        try{withContext(Dispatchers.IO){repo.restoreBackup(uri,backupPassword)};route="home";activity.notify("Restore complete")}
                        catch(e:Exception){error="Restore failed: "+(e.message?:"Invalid or damaged ZIP archive")}
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
                    if(!regular)TopAppBar(colors=TopAppBarDefaults.topAppBarColors(containerColor=VaultNavy),title={Text(when(route){
                        "detail"->current?.title ?: "Document"
                        "fulldetails"->"Full Details"
                        "selectfields"->"Select from Document"
                        "crop"->"Crop Document"
                        "edit"->"Edit information"
                        "viewer"->"Document Viewer"
                        "review"->"OCR Review"
                        "tools"->"PDF Utilities"
                        "expiry"->"Expiry & renewal"
                        "profiles"->"Family profiles"
                        "templates"->"Card templates"
                        "backup"->"Backup & Restore"
                        "recovery"->"Recover imported files"
                        else->"Document Vault"
                    },maxLines=1)},
                        navigationIcon={IconButton(onClick={goBack()}){Icon(Icons.Outlined.ArrowBack,"Back")}})
                    else TopAppBar(colors=TopAppBarDefaults.topAppBarColors(containerColor=VaultNavy),title={
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
                    if(regular)NavigationBar(containerColor=VaultRaised) {
                        listOf(
                            Triple("home",Icons.Outlined.Home,"Home"),
                            Triple("cards",Icons.Outlined.Dashboard,"Cards"),
                            Triple("scan",Icons.Outlined.DocumentScanner,"Scan"),
                            Triple("search",Icons.Outlined.Search,"Search"),
                            Triple("more",Icons.Outlined.Settings,"More")
                        ).forEach{(id,icon,label)->
                            NavigationBarItem(selected=route==id,onClick={route=id},
                                colors=NavigationBarItemDefaults.colors(selectedIconColor=Color.White,
                                    unselectedIconColor=VaultMuted,selectedTextColor=VaultSky,
                                    unselectedTextColor=VaultMuted,indicatorColor=VaultBlue),
                                icon={Icon(icon,label)},label={Text(label)})
                        }
                    }
                }
            ){ padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when(route){
                        "home"->VaultNavHome(repo.cards.toList(),repo,repo.profiles.toList(),userProfile,
                            {userProfile=it},{openCard(it)},{route="search"},{camera()},
                            {importTarget=null;importPicker.launch(arrayOf("*/*"))},
                            {startEdit(repo.newCard(profile=if(userProfile=="all")"self" else userProfile))},
                            {route="cards";activity.notify("Open any card and tap PDF to merge its attachments")},{route="expiry"})
                        "cards"->VaultNavCards(repo.cards.toList(),repo,{openCard(it)},{startEdit(repo.newCard())})
                        "search"->VaultNavSearch(repo.cards.toList(),repo){openCard(it)}
                        "scan"->NavyScanScreen({camera()},
                            {importTarget=null;importPicker.launch(arrayOf("*/*"))},{startEdit(repo.newCard())})
                        "more"->SettingsScreen(repo.hasPin(),engine.availableLanguages(),ocrLanguage,privacyScreen,
                            {enabled->privacyScreen=enabled;activity.setPrivacyScreenEnabled(enabled)},
                            {pin,old->try{repo.setPin(pin,old);activity.notify("PIN saved")}catch(e:Exception){error=e.message?:"PIN failed"}},
                            {pin->if(repo.checkPin(pin)){repo.clearPin();activity.notify("PIN removed")} else activity.notify("Wrong PIN")},
                            {route="profiles"},{route="templates"},{route="backup"},{route="recovery"},
                            {modelPicker.launch(arrayOf("*/*"))},{ocrLanguage=it},
                            {days->activity.getSharedPreferences("vault_options",Context.MODE_PRIVATE).edit().putInt("remindDays",days).apply()
                                if(Build.VERSION.SDK_INT>=33)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                activity.notify("Expiry reminders: "+days+" days")})
                        "detail"->current?.let{c->VaultNavDetail(c,repo,
                            {f->viewing=f;route="viewer"},
                            {f->startOcr(c.id,f)},{f->activity.share(f)},{activity.shareAll(c.files)},
                            {importTarget=c.id;importPicker.launch(arrayOf("*/*"))},
                            {route="fulldetails"},{route="tools"},
                            {startEdit(c)},
                            {scope.launch{try{withContext(Dispatchers.IO){repo.deleteCard(c)};route="cards"}
                                catch(e:Exception){error=e.message?:"Delete failed"}}})}
                        "fulldetails"->current?.let{c->FullDetailsPage(c,
                            {updated->scope.launch{try{withContext(Dispatchers.IO){repo.saveCard(updated)}}
                                catch(e:Exception){error=e.message?:"Cannot save field"}}},
                            {route="selectfields"})}
                        "selectfields"->current?.let{c->SelectDocumentTextPage(c,
                            {f->scope.launch{
                                try{withContext(Dispatchers.IO){
                                    val latest=repo.cards.firstOrNull{it.id==c.id}?:return@withContext
                                    repo.saveCard(latest.copy(fields=latest.fields+f))
                                };route="fulldetails"}
                                catch(e:Exception){error="Cannot save selected field: "+(e.message?:"Error")}
                            }},
                            {f->startOcr(c.id,f)})}
                        "crop"->{
                            val image=cropBitmap;val result=cropResult
                            if(image!=null&&result!=null)CropReviewScreen(image,result,
                                {corners->scope.launch{
                                    busy="Applying perspective correction"
                                    try{
                                        val uri=withContext(Dispatchers.IO){
                                            val corrected=DocumentCropEngine.crop(image,corners)
                                            try{
                                                val output=File(activity.cacheDir,"camera/scan-"+UUID.randomUUID()+".jpg")
                                                DocumentCropEngine.save(corrected,output)
                                                FileProvider.getUriForFile(activity,activity.packageName+".provider",output)
                                            }finally{corrected.recycle()}
                                        }
                                        route=if(scanTarget==null)"scan" else "detail"
                                        importUris(listOf(uri),scanTarget)
                                    }catch(e:Exception){error="Crop failed: "+(e.message?:"Error")}
                                    finally{busy="";cropBitmap=null;cropResult=null}
                                }},
                                {cameraUri?.let{uri->route=if(scanTarget==null)"scan" else "detail";importUris(listOf(uri),scanTarget)}
                                    cropBitmap=null;cropResult=null},
                                {cropBitmap=null;cropResult=null;goBack()})
                        }
                        "edit"->editing?.let{c->CardEditorScreen(c,repo.profiles.toList(),
                            {saved->scope.launch {
                                busy="Saving card"
                                try {
                                    withContext(Dispatchers.IO){repo.saveCard(saved)}
                                    selectedCardId=saved.id;editing=null;route="detail"
                                }catch(e:Exception){error="Cannot save card: "+(e.message?:"Storage error")}
                                finally{busy=""}
                            }},
                            {goBack()},
                            {t->scope.launch {try{withContext(Dispatchers.IO){repo.saveTemplate(t)};activity.notify("Saved template "+t.name)}
                                catch(e:Exception){error=e.message?:"Unable to save template"}}})}
                        "viewer"->viewing?.let{f->DocumentViewerScreen(f,repo,engine,{activity.share(f)},
                            {startOcr(selectedCardId,f)})}
                        "review"->{
                            val job=reviewQueue.firstOrNull()
                            if(job!=null)OcrReviewScreen(job.file.name,job.analysis,
                                {fields->
                                    val original=repo.cards.firstOrNull{it.id==job.cardId}
                                    if(original!=null){
                                        val merged=original.fields.toMutableList()
                                        fields.filter{it.name.isNotBlank()&&it.value.isNotBlank()}.forEach{suggested->
                                            val match=merged.indexOfFirst{it.name.equals(suggested.name,true)}
                                            if(match<0)merged.add(suggested)
                                            else if(merged[match].value.isBlank())merged[match]=suggested
                                        }
                                        val expiryField=merged.firstOrNull{it.name.contains("expiry",true)}
                                        val expiry=if(original.expiry.isNotBlank())original.expiry else expiryField?.value?.let{date->
                                            try{LocalDate.parse(date,DateTimeFormatter.ofPattern("d/M/yyyy")).toString()}
                                            catch(_:Exception){try{LocalDate.parse(date).toString()}catch(_:Exception){""}}
                                        } ?: ""
                                        scope.launch {
                                            try{withContext(Dispatchers.IO){repo.saveCard(original.copy(fields=merged,expiry=expiry))}}
                                            catch(e:Exception){error="Cannot save OCR fields: "+(e.message?:"Storage error")}
                                        }
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
                        "tools"->current?.let{c->PdfToolsScreen(c,
                            {importTarget=c.id;importPicker.launch(arrayOf("*/*"))},
                            {op,files,from,to->
                                pdfJobToExport=PdfJob(op,files,from,to)
                                pdfExport.launch("DocumentVault_"+op+"_"+System.currentTimeMillis()+".pdf")
                            })}
                        "expiry"->ExpiriesScreen(repo.cards.toList()){openCard(it)}
                        "profiles"->ProfilesScreen(repo.profiles.toList()){p->repo.saveProfile(p)}
                        "templates"->TemplatesScreen(FieldSuggestions.templates+repo.customTemplates){template->
                            val card=repo.newCard().copy(title=template.name,kind=template.name,fields=template.fieldNames.map{DataField(it,"")})
                            startEdit(card)
                        }
                        "backup"->BackupScreen(
                            {pwd->backupPassword="";backupExport.launch("DocumentVault_"+LocalDate.now()+".zip")},
                            {pwd->backupPassword="";backupImport.launch(arrayOf("*/*"))}
                        )
                        "recovery"->RecoveryScreen(repo.recoverableFiles(),repo.recoveryError,
                            {file,title->scope.launch{
                                busy="Recovering old document"
                                try{withContext(Dispatchers.IO){repo.attachRecovered(file.id,title)}
                                    activity.notify("Recovered document into a new card");route="cards"}
                                catch(e:Exception){error="Recovery failed: "+(e.message?:"Storage error")}
                                finally{busy=""}
                            }},
                            {route="backup"})
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
        Text("Unlock your personal document library")
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
