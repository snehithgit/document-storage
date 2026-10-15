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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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
    val savedScreens=rememberSaveableStateHolder()
    var route by rememberSaveable{mutableStateOf("home")}
    var privacyScreen by rememberSaveable{mutableStateOf(activity.isPrivacyScreenEnabled())}
    var lastRoot by rememberSaveable{mutableStateOf("cards")}
    var selectedCardId by rememberSaveable{mutableStateOf("")}
    var editing by remember{mutableStateOf<VaultCard?>(null)}
    var editingJson by rememberSaveable{mutableStateOf("")}
    var viewingId by rememberSaveable{mutableStateOf("")}
    var cropBitmap by remember{mutableStateOf<android.graphics.Bitmap?>(null)}
    var cropResult by remember{mutableStateOf<DetectedScan?>(null)}
    var cameraPath by rememberSaveable{mutableStateOf("")}
    var cropFromAttachment by rememberSaveable{mutableStateOf(false)}
    var scanSessionContinue by rememberSaveable{mutableStateOf("")}
    var scanTarget by rememberSaveable{mutableStateOf<String?>(null)}
    var addToCardId by rememberSaveable{mutableStateOf<String?>(null)}
    var importTarget by rememberSaveable{mutableStateOf<String?>(null)}
    var userProfile by rememberSaveable{mutableStateOf("all")}
    var categoryFilter by rememberSaveable{mutableStateOf("All")}
    var ocrLanguage by rememberSaveable{mutableStateOf("Latin")}
    var busy by remember{mutableStateOf("")}
    var ocrJob by remember{mutableStateOf<Job?>(null)}
    var error by remember{mutableStateOf("")}
    var pdfJob by remember{mutableStateOf<PdfJob?>(null)}
    var backupPassword by remember{mutableStateOf("")}
    val reviewQueue= remember{mutableStateListOf<ReviewJob>()}
    val options=remember { activity.getSharedPreferences("vault_options",Context.MODE_PRIVATE) }
    var themeMode by rememberSaveable { mutableStateOf(options.getString("displayTheme","system") ?: "system") }
    val dark=when(themeMode){"dark"->true;"light"->false;else->isSystemInDarkTheme()}
    val current=repo.cards.firstOrNull{it.id==selectedCardId}
    val regular=route in setOf("home","cards","search","more")
    fun openCard(c:VaultCard){if(regular)lastRoot=route;selectedCardId=c.id;route="detail"}
    fun startEdit(card:VaultCard){editing=card;editingJson=repo.encodeCard(card);route="edit"}
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
        if(ocrJob?.isActive==true)return
        busy="Preparing OCR: "+VaultNames.title(f.name)
        ocrJob=scope.launch {
            try{
                val result=engine.extract(f,ocrLanguage){done,total->
                    busy="OCR page "+done+" of "+total+": "+VaultNames.title(f.name)
                }
                val fresh=repo.cards.firstOrNull{it.id==cardId} ?:return@launch
                val update=f.copy(extractedText=result.text,ocrStatus=result.method)
                withContext(Dispatchers.IO){repo.saveCard(fresh.copy(files=fresh.files.map{if(it.id==f.id)update else it}))}
                if(result.fields.isNotEmpty()) {reviewQueue.add(ReviewJob(cardId,update,result));route="review"}
                else {activity.notify("Text extracted. Open the document Text tab to copy it.");route="detail"}
            }catch(_:CancellationException){activity.notify("OCR cancelled")}
            catch(e:Exception){error="OCR failed: "+(e.message?:"Unknown format")}
            finally{busy="";ocrJob=null}
        }
    }
    fun importUris(uris:List<Uri>,targetId:String?=null,addPage:Boolean=false,originalUri:Uri?=null) {
        if(uris.isEmpty())return
        busy="Importing "+VaultCounts.format(uris.size,"document")
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
                                .copy(title=if(file.name.startsWith("scan-",true)||file.name.startsWith("capture-",true))
                                    "Scanned document · "+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM HH:mm"))
                                else file.name.substringBeforeLast('.',file.name),files=listOf(file))
                        }
                    withContext(Dispatchers.IO){repo.saveCard(card)}
                    lastCard=card
                    busy="Importing "+(index+1)+"/"+uris.size+": "+file.name
                    // Import now finishes as soon as the file is saved. OCR is opt-in
                    // from each document's OCR button, so a scan cannot stall View/Share.
                }
                if(lastCard!=null) {
                    val card=repo.cards.first{it.id==lastCard!!.id}
                    if(originalUri!=null) {
                        withContext(Dispatchers.IO) {
                            val retained=repo.importFile(originalUri)
                            if(!repo.isDuplicate(retained.sha256)) {
                                val updated=repo.cards.first{it.id==card.id}
                                repo.saveCard(updated.copy(files=updated.files+retained))
                            }else repo.removeOrphan(retained)
                        }
                    }
                    openCard(repo.cards.first{it.id==card.id})
                    if(addPage)scanSessionContinue=card.id
                    else activity.notify("Imported. Tap OCR to extract and copy information.")
                }
            }catch(e:Exception){error="Import failed: "+(e.message?:"Unknown error")}
            finally {busy="";importTarget=null}
        }
    }
    val importPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uriList->
        importUris(uriList,importTarget)
    }
    var cameraUri by rememberSaveable{mutableStateOf<Uri?>(null)}
    val cameraLauncher=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success->
        if(success){
            val uri=cameraUri
            if(uri!=null)scope.launch{
                busy="Detecting document borders"
                try{
                    val(bitmap,detected)=withContext(Dispatchers.IO){
                        val file=File(cameraPath).takeIf{it.isFile}
                            ?:throw IllegalStateException("Camera file missing")
                        val image=engine.scaledBitmap(file,2050)
                        image to DocumentCropEngine.detect(image,activity.applicationContext)
                    }
                    cropBitmap=bitmap;cropResult=detected;route="crop"
                }catch(e:Exception){error="Scan preparation failed: "+(e.message?:"Unknown error")}
                finally{busy=""}
            }
        }
    }
    LaunchedEffect(route,cameraPath){
        if(route=="crop" && cropBitmap==null && cameraPath.isNotBlank()) {
            val recovered=withContext(Dispatchers.IO){
                val file=File(cameraPath)
                if(file.isFile) {
                    val photo=engine.scaledBitmap(file,2050)
                    photo to DocumentCropEngine.detect(photo,activity.applicationContext)
                }else null
            }
            if(recovered!=null){cropBitmap=recovered.first;cropResult=recovered.second}
            else route="scan"
        }
    }
    fun camera(target:String?=null) {
        importTarget=target;scanTarget=target;cropFromAttachment=false
        scope.launch {
            try {
                val f=withContext(Dispatchers.IO){
                    File(activity.cacheDir,"camera").apply{mkdirs()}
                        .let{File(it,"capture-"+UUID.randomUUID()+".jpg")}
                        .also{it.createNewFile()}
                }
                cameraPath=f.absolutePath
                cameraUri=FileProvider.getUriForFile(activity,activity.packageName+".provider",f)
                cameraLauncher.launch(cameraUri!!)
            }catch(e:Exception){error="Could not start camera: "+(e.message?:"Storage error")}
        }
    }
    LaunchedEffect(scanSessionContinue) {
        val next=scanSessionContinue
        if(next.isNotBlank()){scanSessionContinue="";camera(next)}
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
                }
                    if(job.op=="combine"){
                        // The exported PDF becomes a new attachment while originals remain.
                        val combined=withContext(Dispatchers.IO){repo.importFile(uri)}
                        val original=repo.cards.firstOrNull{it.id==selectedCardId}
                        if(original!=null)withContext(Dispatchers.IO){
                            repo.saveCard(original.copy(files=original.files+combined))
                        }
                    }
                    activity.notify(if(job.op=="combine")"Combined PDF saved to the card and exported" else "PDF exported successfully")
                }
                catch(e:Exception){error="PDF export failed: "+(e.message?:"Error")}
                finally{busy="";pdfJobToExport=null}
            }
        }
    }
    val modelPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)scope.launch {
            busy="Installing offline OCR language"
            try {
                val lang=withContext(Dispatchers.IO){engine.installLanguage(uri)}
                ocrLanguage="Tesseract: "+lang
                activity.notify("Installed "+lang+" offline OCR")
            }catch(e:Exception){error=e.message?:"Could not install language"}
            finally{busy=""}
        }
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
    var restoreUri by rememberSaveable{mutableStateOf<Uri?>(null)}
    var confirmRestore by rememberSaveable{mutableStateOf(false)}
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
    if(addToCardId!=null){
        val target=addToCardId
        AlertDialog(onDismissRequest={addToCardId=null},title={Text("Add document pages")},
            text={Text("Add another scan with automatic crop or import existing PDFs and images.")},
            confirmButton={TextButton(onClick={
                addToCardId=null
                camera(target)
            }){Text("Scan new page")}},
            dismissButton={TextButton(onClick={
                addToCardId=null
                importTarget=target
                importPicker.launch(arrayOf("*/*"))
            }){Text("Import files")}})
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

    SideEffect { 
        activity.window.statusBarColor=if(dark)android.graphics.Color.rgb(16,21,28) else android.graphics.Color.WHITE
        activity.window.navigationBarColor=if(dark)android.graphics.Color.rgb(16,21,28) else android.graphics.Color.WHITE
        androidx.core.view.WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply {
            isAppearanceLightStatusBars=!dark
            isAppearanceLightNavigationBars=!dark
        }
    }
    VaultTheme(dark) {
        if(!activity.unlocked.value) {
            UnlockScreen(onPin={pin->
                if(repo.checkPin(pin)){activity.unlocked.value=true;true}else false
            },waitSeconds={repo.pinWaitSeconds()},
                onBiometric={activity.authenticate({activity.unlocked.value=true},{activity.notify(it)})})
        } else {
            androidx.activity.compose.BackHandler(enabled=!regular||route!="home"){goBack()}
            Scaffold(
                topBar={
                    if(!regular)TopAppBar(
                        colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background),
                        title={
                            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){
                                Text(when(route){
                                    "detail"->current?.let{VaultNames.title(it.title)} ?: "Document"
                                    "fulldetails"->"Full Details"
                                    "selectfields"->"Select from document"
                                    "scan"->"Scan"
                                    "crop"->"Crop document"
                                    "edit"->"Edit information"
                                    "viewer"->"Document viewer"
                                    "review"->"OCR review"
                                    "tools"->"PDF Studio"
                                    "expiry"->"Expiry and renewal"
                                    "profiles"->"Family profiles"
                                    "templates"->"Card templates"
                                    "backup"->"Backup and restore"
                                    "recovery"->"Recovery"
                                    else->"Document Vault"
                                },maxLines=1,style=MaterialTheme.typography.titleMedium)
                            }
                        },
                        navigationIcon={
                            Surface(shape=androidx.compose.foundation.shape.CircleShape,
                                color=MaterialTheme.colorScheme.surface,
                                modifier=Modifier.padding(start=8.dp).size(40.dp).clickable{goBack()}){
                                Box(contentAlignment=Alignment.Center){
                                    Icon(Icons.Outlined.ArrowBack,"Back",tint=MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        },
                        actions={Spacer(Modifier.width(48.dp))}
                    )
                },
                bottomBar={
                    if(regular)HubBottomBar(route,{route=it},{camera()})
                }
            ){ padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    savedScreens.SaveableStateProvider(route) {
                    when(route){
                        "home"->HubHome(repo.cards.toList(),repo,repo.profiles.toList(),userProfile,
                            {userProfile=it},{openCard(it)},{route="search"},{camera()},
                            {importTarget=null;importPicker.launch(arrayOf("*/*"))},
                            {startEdit(repo.newCard(profile=if(userProfile=="all")"self" else userProfile))},
                            {route="cards";activity.notify("Open any card and tap PDF to merge its attachments")},{route="expiry"},
                            {categoryFilter=it;route="cards"})
                        "cards"->VaultNavCards(repo.cards.toList(),repo,{openCard(it)},{startEdit(repo.newCard())},categoryFilter)
                        "search"->VaultNavSearch(repo.cards.toList(),repo){openCard(it)}
                        "scan"->NavyScanScreen({camera()},
                            {importTarget=null;importPicker.launch(arrayOf("*/*"))},{startEdit(repo.newCard())})
                        "more"->PremiumSettings(repo.hasPin(),engine.availableLanguages(),ocrLanguage,privacyScreen,
                            themeMode,
                            {value->
                                themeMode=value
                                options.edit().putString("displayTheme",value).apply()
                            },
                            {enabled->privacyScreen=enabled;activity.setPrivacyScreenEnabled(enabled)},
                            {pin,old->try{repo.setPin(pin,old);activity.notify("PIN saved")}catch(e:Exception){error=e.message?:"PIN failed"}},
                            {pin->if(repo.checkPin(pin)){repo.clearPin(pin);activity.notify("PIN removed")} else activity.notify("Wrong PIN")},
                            {route="profiles"},{route="templates"},{route="backup"},{route="recovery"},
                            {modelPicker.launch(arrayOf("*/*"))},{ocrLanguage=it},
                            {days->activity.getSharedPreferences("vault_options",Context.MODE_PRIVATE).edit().putInt("remindDays",days).apply()
                                if(Build.VERSION.SDK_INT>=33)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                activity.notify("Expiry reminders: "+days+" days")})
                        "detail"->current?.let{c->VaultNavDetail(c,repo,
                            {f->viewingId=f.id;route="viewer"},
                            {f->startOcr(c.id,f)},{f->activity.share(f)},{activity.shareAll(c.files)},
                            {addToCardId=c.id},
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
                            if(image!=null && result!=null)CropReviewScreen(image,result,
                                {rotation,corners,settings,addAnother->scope.launch{
                                    busy="Cropping and enhancing full-resolution scan"
                                    try {
                                        val uri=withContext(Dispatchers.IO){
                                            val original=ImageDecode.decode(File(cameraPath),Int.MAX_VALUE,fullResolution=true)
                                            val oriented=if(rotation==0)original else{
                                                try {
                                                    val matrix=android.graphics.Matrix().apply{postRotate(rotation.toFloat())}
                                                    android.graphics.Bitmap.createBitmap(original,0,0,original.width,original.height,matrix,true)
                                                }finally{original.recycle()}
                                            }
                                            try {
                                                val corrected=DocumentCropEngine.crop(oriented,corners)
                                                try {
                                                    val processed=ScanEnhance.apply(corrected,settings)
                                                    try {
                                                        val output=File(activity.cacheDir,"camera/scan-"+UUID.randomUUID()+".jpg")
                                                        DocumentCropEngine.save(processed,output)
                                                        FileProvider.getUriForFile(activity,activity.packageName+".provider",output)
                                                    }finally{if(processed!==corrected)processed.recycle()}
                                                }finally{corrected.recycle()}
                                            }finally{oriented.recycle()}
                                        }
                                        route=if(scanTarget==null)"scan" else "detail"
                                        val keep=activity.getSharedPreferences("vault_options",Context.MODE_PRIVATE)
                                            .getBoolean("keepScanOriginal",true)
                                        importUris(listOf(uri),scanTarget,addAnother,
                                            if(keep&&!cropFromAttachment)cameraUri else null)
                                    }catch(e:Exception){error="Crop failed: "+(e.message?:"Error")}
                                    catch(e:OutOfMemoryError){error="Image too large to process safely; try a lower camera resolution"}
                                    finally{busy="";cropBitmap=null;cropResult=null}
                                }},
                                {
                                    if(cropFromAttachment)route="detail"
                                    else cameraUri?.let{uri->
                                        route=if(scanTarget==null)"scan" else "detail"
                                        importUris(listOf(uri),scanTarget)
                                    }
                                    cropBitmap=null;cropResult=null
                                },
                                {cropBitmap=null;cropResult=null;goBack()},
                                {cropBitmap=null;cropResult=null;camera(scanTarget)})
                        }
                        "edit"->(editing?:editingJson.takeIf{it.isNotBlank()}?.let{repo.decodeCard(it)})?.let{c->
                            CardEditorScreen(c,repo.profiles.toList(),
                            {saved->scope.launch {
                                busy="Saving card"
                                try {
                                    withContext(Dispatchers.IO){repo.saveCard(saved)}
                                    selectedCardId=saved.id;editing=null;editingJson="";route="detail"
                                }catch(e:Exception){error="Cannot save card: "+(e.message?:"Storage error")}
                                finally{busy=""}
                            }},
                            {goBack()},
                            {t->scope.launch {try{withContext(Dispatchers.IO){repo.saveTemplate(t)};activity.notify("Saved template "+t.name)}
                                catch(e:Exception){error=e.message?:"Unable to save template"}}})}
                        "viewer"->current?.files?.firstOrNull{it.id==viewingId}?.let{f->
                            DocumentViewerScreen(f,repo,engine,{activity.share(f)},
                                {startOcr(selectedCardId,f)})
                        }
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
                            {addToCardId=c.id},
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
                        "recovery"->{
                            var recoverables by remember {mutableStateOf(emptyList<VaultFile>())}
                            LaunchedEffect(Unit){
                                recoverables=withContext(Dispatchers.IO){repo.recoverableFiles()}
                            }
                            RecoveryScreen(recoverables,repo.recoveryError,
                            {file,title->scope.launch{
                                busy="Recovering old document"
                                try{withContext(Dispatchers.IO){repo.attachRecovered(file.id,title)}
                                    activity.notify("Recovered document into a new card");route="cards"}
                                catch(e:Exception){error="Recovery failed: "+(e.message?:"Storage error")}
                                finally{busy=""}
                            }},
                            {route="backup"})
                        }
                    }
                    }
                    if(busy.isNotBlank()) {
                        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.scrim.copy(alpha=.62f)) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                                CircularProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text(busy,color=Color.White)
                                if(ocrJob?.isActive==true){
                                    Spacer(Modifier.height(16.dp))
                                    OutlinedButton(onClick={ocrJob?.cancel()}){
                                        Text("Cancel OCR")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun UnlockScreen(onPin:(String)->Boolean,waitSeconds:()->Long,onBiometric:()->Unit) {
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
        if(invalid)Text(if(waitSeconds()>0)"Too many attempts. Try again in "+waitSeconds()+" seconds."
            else "Incorrect PIN",color=MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick={if(!onPin(pin))invalid=true},modifier=Modifier.fillMaxWidth(),enabled=pin.isNotBlank()){Text("Unlock")}
        OutlinedButton(onClick=onBiometric,modifier=Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Fingerprint,null);Text(" Use biometrics")
        }
    }
}
