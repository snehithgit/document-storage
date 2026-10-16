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
import androidx.work.*
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
    var backStack by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedCardId by rememberSaveable{mutableStateOf("")}
    var editing by remember{mutableStateOf<VaultCard?>(null)}
    var editingJson by rememberSaveable{mutableStateOf("")}
    var viewingId by rememberSaveable{mutableStateOf("")}
    var cropBitmap by remember{mutableStateOf<android.graphics.Bitmap?>(null)}
    var initialCropRotation by rememberSaveable{mutableIntStateOf(0)}
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
    val workManager=remember(activity){WorkManager.getInstance(activity.applicationContext)}
    val ocrFlow=remember(workManager){workManager.getWorkInfosForUniqueWorkFlow(VaultOcrWorker.UNIQUE)}
    val ocrInfos by ocrFlow.collectAsState(initial=emptyList())
    val runningOcr=ocrInfos.firstOrNull{it.state==WorkInfo.State.RUNNING}
        ?:ocrInfos.firstOrNull{it.state==WorkInfo.State.ENQUEUED}
    val latestOcr=ocrInfos.firstOrNull()
    var notifiedOcrId by rememberSaveable{mutableStateOf("")}
    var error by remember{mutableStateOf("")}
    var pdfJob by remember{mutableStateOf<PdfJob?>(null)}
    var backupPassword by remember{mutableStateOf("")}
    val reviewQueue= remember{mutableStateListOf<ReviewJob>()}
    val options=remember { activity.getSharedPreferences("vault_options",Context.MODE_PRIVATE) }
    var themeMode by rememberSaveable { mutableStateOf(options.getString("displayTheme","dark") ?: "dark") }
    val dark=when(themeMode){"dark"->true;"light"->false;else->isSystemInDarkTheme()}
    val current=repo.cards.firstOrNull{it.id==selectedCardId}
    val regular=route in setOf("home","cards","search","more")
    // A saved, ordered history replaces route-specific guesses in Back handling.
    // Root tab changes clear the history; nested screens push their actual caller.
    fun navigate(destination:String) {
        if(route==destination)return
        backStack=backStack+route
        route=destination
    }
    fun navigateRoot(destination:String) {
        backStack=emptyList()
        route=destination
    }
    fun openCard(card:VaultCard){
        selectedCardId=card.id
        navigate("detail")
    }
    fun startEdit(card:VaultCard){
        editing=card
        editingJson=repo.encodeCard(card)
        navigate("edit")
    }
    fun goBack(){
        val previous=backStack.lastOrNull()
        backStack=if(backStack.isEmpty())emptyList()else backStack.dropLast(1)
        route=previous?:"home"
    }
    fun startOcr(cardId:String,f:VaultFile) {
        if(runningOcr!=null) {
            activity.notify("OCR is already running. Cancel it before starting another file.")
            return
        }
        val request=OneTimeWorkRequestBuilder<VaultOcrWorker>()
            .setInputData(workDataOf(VaultOcrWorker.KEY_CARD to cardId,
                VaultOcrWorker.KEY_FILE to f.id,VaultOcrWorker.KEY_SCRIPT to ocrLanguage))
            .addTag("offline-ocr").build()
        workManager.enqueueUniqueWork(VaultOcrWorker.UNIQUE,ExistingWorkPolicy.KEEP,request)
        activity.notify("OCR queued — you can leave this screen")
    }
    fun fetchDetailsForCard(card:VaultCard) {
        if(runningOcr!=null){
            activity.notify("OCR is already running. Wait for it or use Cancel.")
            return
        }
        val eligible=DetailFetchPlan.filesToProcess(card.files)
        if(eligible.isEmpty()){
            activity.notify("No supported image, PDF or text attachments to extract")
            return
        }
        val requests=eligible.map { file->
            OneTimeWorkRequestBuilder<VaultOcrWorker>()
                .setInputData(workDataOf(
                    VaultOcrWorker.KEY_CARD to card.id,
                    VaultOcrWorker.KEY_FILE to file.id,
                    VaultOcrWorker.KEY_SCRIPT to ocrLanguage
                ))
                .addTag("offline-fetch-details")
                .build()
        }
        var continuation=workManager.beginUniqueWork(
            VaultOcrWorker.UNIQUE,ExistingWorkPolicy.KEEP,requests.first())
        requests.drop(1).forEach{request->continuation=continuation.then(request)}
        continuation.enqueue()
        activity.notify("Fetching details from "+VaultCounts.format(eligible.size,"attachment")+
            " offline. Open Document Information to review and copy the results.")
    }
    LaunchedEffect(latestOcr?.id,latestOcr?.state) {
        val info=latestOcr?:return@LaunchedEffect
        if(!info.state.isFinished || info.id.toString()==notifiedOcrId)return@LaunchedEffect
        notifiedOcrId=info.id.toString()
        when(info.state) {
            WorkInfo.State.SUCCEEDED->activity.notify("Reading complete. Review results in Document Information.")
            WorkInfo.State.FAILED->error="OCR failed: "+info.outputData.getString(VaultOcrWorker.KEY_ERROR).orEmpty()
            else->Unit
        }
    }
    fun importUris(uris:List<Uri>,targetId:String?=null,addPage:Boolean=false,originalUri:Uri?=null,onSaved:(()->Unit)?=null) {
        if(uris.isEmpty())return
        busy="Importing "+VaultCounts.format(uris.size,"document")
        scope.launch {
            var lastCard:VaultCard?=null
            try {
                for((index,uri) in uris.withIndex()) {
                    ImportTargetPolicy.requireAvailable(targetId,repo.cards.map{it.id}.toSet())
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
                    else activity.notify("Import complete. Use Get Details to read and copy information.")
                }
            }catch(e:Exception){error="Import failed: "+(e.message?:"Unknown error")}
            finally {busy="";importTarget=null}
        }
    }
    val importPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uriList->
        importUris(uriList,importTarget)
    }
    val galleryPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()){photos->
        importUris(photos,importTarget)
    }
    var cameraUri by rememberSaveable{mutableStateOf<Uri?>(null)}
    val cameraLauncher=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success->
        if(success){
            val uri=cameraUri
            if(uri!=null)scope.launch{
                busy="Detecting document borders"
                try{
                    val prepared=withContext(Dispatchers.IO){
                        val file=File(cameraPath).takeIf{it.isFile}
                            ?:throw IllegalStateException("Camera file missing")
                        ScanPrepare.load(activity.applicationContext,file)
                    }
                    cropBitmap=prepared.preview;cropResult=prepared.corners
                    initialCropRotation=prepared.rotation;navigate("crop")
                }catch(e:Exception){error="Scan preparation failed: "+(e.message?:"Unknown error")}
                finally{busy=""}
            }
        }
    }
    LaunchedEffect(route,cameraPath){
        if(route=="crop" && cropBitmap==null && cameraPath.isNotBlank()) {
            val recovered=withContext(Dispatchers.IO){
                val file=File(cameraPath)
                if(file.isFile)ScanPrepare.load(activity.applicationContext,file) else null
            }
            if(recovered!=null){cropBitmap=recovered.preview;cropResult=recovered.corners;initialCropRotation=recovered.rotation}
            else navigate("scan")
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
        ModalBottomSheet(onDismissRequest={addToCardId=null},
            containerColor=MaterialTheme.colorScheme.surface){
            Column(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,bottom=24.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Add Page",style=MaterialTheme.typography.titleLarge)
                Text("Choose a source to add to this document.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(
                    Triple("Scan with Camera",Icons.Outlined.CameraAlt,
                        {addToCardId=null;camera(target)}),
                    Triple("Import Photos",Icons.Outlined.PhotoLibrary,
                        {addToCardId=null;importTarget=target;galleryPicker.launch("image/*")}),
                    Triple("Import Files or PDF",Icons.Outlined.FolderOpen,
                        {addToCardId=null;importTarget=target;importPicker.launch(arrayOf("*/*"))})
                ).forEach{(label,icon,action)->
                    Row(Modifier.fillMaxWidth().heightIn(min=62.dp)
                        .clickable(onClick=action).padding(horizontal=8.dp),
                        verticalAlignment=Alignment.CenterVertically){
                        Icon(icon,null,Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(label,style=MaterialTheme.typography.bodyLarge,
                            modifier=Modifier.weight(1f))
                        Icon(Icons.Outlined.ChevronRight,null,
                            tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                }
            }
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
                        try{withContext(Dispatchers.IO){repo.restoreBackup(uri,backupPassword)};navigateRoot("home");activity.notify("Restore complete")}
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
        activity.window.statusBarColor=if(dark)android.graphics.Color.BLACK else android.graphics.Color.rgb(11,13,16)
        activity.window.navigationBarColor=if(dark)android.graphics.Color.BLACK else android.graphics.Color.rgb(11,13,16)
        androidx.core.view.WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply {
            isAppearanceLightStatusBars=false
            isAppearanceLightNavigationBars=false
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
                                    "fulldetails"->"Document Information"
                                    "attachments"->"Manage attachments"
                                    "about"->"About"
                                    "selectfields"->"Select from document"
                                    "scan"->"Scan"
                                    "crop"->"Crop document"
                                    "edit"->"Edit Document"
                                    "viewer"->"Document viewer"
                                    "review"->"OCR review"
                                    "tools"->"PDF Tools"
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
                                modifier=Modifier.padding(start=8.dp).size(48.dp).clickable{goBack()}){
                                Box(contentAlignment=Alignment.Center){
                                    Icon(Icons.Outlined.ArrowBack,"Back",tint=MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        },
                        actions={Spacer(Modifier.width(48.dp))}
                    )
                },
                bottomBar={
                    if(regular)HubBottomBar(route,{navigateRoot(it)},{navigate("scan")})
                }
            ){ padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    savedScreens.SaveableStateProvider(route) {
                    when(route){
                        "home"->HubHome(repo.cards.toList(),repo,repo.profiles.toList(),userProfile,
                            {userProfile=it},{openCard(it)},{navigate("search")},{navigate("scan")},
                            {importTarget=null;importPicker.launch(arrayOf("*/*"))},
                            {startEdit(repo.newCard(profile=if(userProfile=="all")"self" else userProfile))},
                            {navigate("cards");activity.notify("Open any card and tap PDF to merge its attachments")},{navigate("expiry")},
                            {categoryFilter=it;navigate("cards")})
                        "cards"->VaultNavCards(repo.cards.toList(),repo,{openCard(it)},{navigate("scan")},categoryFilter,{navigateRoot("search")})
                        "search"->VaultNavSearch(repo.cards.toList(),repo){openCard(it)}
                        "scan"->NavyScanScreen(
                            onCamera={camera()},
                            onGallery={importTarget=null;galleryPicker.launch("image/*")},
                            onFiles={importTarget=null;importPicker.launch(arrayOf("*/*"))},
                            onNew={startEdit(repo.newCard())}
                        )
                        "more"->PremiumSettings(repo.hasPin(),engine.availableLanguages(),ocrLanguage,privacyScreen,
                            themeMode,
                            {value->
                                themeMode=value
                                options.edit().putString("displayTheme",value).apply()
                            },
                            {enabled->privacyScreen=enabled;activity.setPrivacyScreenEnabled(enabled)},
                            {pin,old->try{repo.setPin(pin,old);activity.notify("PIN saved")}catch(e:Exception){error=e.message?:"PIN failed"}},
                            {pin->if(repo.checkPin(pin)){repo.clearPin(pin);activity.notify("PIN removed")} else activity.notify("Wrong PIN")},
                            {navigate("profiles")},{navigate("templates")},{navigate("backup")},{navigate("recovery")},
                            {modelPicker.launch(arrayOf("*/*"))},{ocrLanguage=it},
                            {days->activity.getSharedPreferences("vault_options",Context.MODE_PRIVATE).edit().putInt("remindDays",days).apply()
                                if(Build.VERSION.SDK_INT>=33)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                activity.notify("Expiry reminders: "+days+" days")},
                            options.getBoolean("keepScanOriginal",true),
                            {keep->options.edit().putBoolean("keepScanOriginal",keep).apply()},{navigate("about")})
                        "detail"->current?.let{c->VaultNavDetail(c,repo,
                            {f->viewingId=f.id;navigate("viewer")},
                            {f->startOcr(c.id,f)},{f->activity.share(f)},{activity.shareAll(c.files)},
                            {addToCardId=c.id},
                            {navigate("fulldetails")},{navigate("tools")},
                            {startEdit(c)},{navigate("attachments")},
                            {scope.launch{try{withContext(Dispatchers.IO){repo.deleteCard(c)};navigate("cards")}
                                catch(e:Exception){error=e.message?:"Delete failed"}}},
                            {fetchDetailsForCard(c)},runningOcr!=null,
                            {imageFile->scope.launch {
                                busy="Preparing photo for non-destructive enhancement"
                                try {
                                    val source=withContext(Dispatchers.IO){repo.documentFile(imageFile)}
                                    val prepared=withContext(Dispatchers.IO){
                                        ScanPrepare.load(activity.applicationContext,source)
                                    }
                                    scanTarget=c.id;cropFromAttachment=true
                                    cameraUri=null;cameraPath=source.absolutePath
                                    cropBitmap=prepared.preview;cropResult=prepared.corners
                                    initialCropRotation=prepared.rotation;navigate("crop")
                                }catch(e:Exception){error="Cannot enhance image: "+(e.message?:"Error")}
                                finally{busy=""}
                            }})}
                        "attachments"->current?.let{c->AttachmentManagerScreen(c,repo,
                            {f->viewingId=f.id;navigate("viewer")},
                            {f,name->scope.launch {
                                try{withContext(Dispatchers.IO){repo.renameFile(c.id,f.id,name)}}
                                catch(e:Exception){error="Rename failed: "+(e.message?:"Invalid filename")}
                            }},
                            {f->scope.launch {
                                try{withContext(Dispatchers.IO){repo.removeFile(c.id,f.id)}}
                                catch(e:Exception){error="Remove failed: "+(e.message?:"Storage error")}
                            }},
                            {ids->scope.launch {
                                try{withContext(Dispatchers.IO){repo.reorderFiles(c.id,ids)}}
                                catch(e:Exception){error="Reorder failed: "+(e.message?:"Storage error")}
                            }})}
                        "about"->VaultAboutScreen()
                        "fulldetails"->current?.let{c->FullDetailsPage(c,
                            {updated->scope.launch{try{withContext(Dispatchers.IO){repo.saveCard(updated)}}
                                catch(e:Exception){error=e.message?:"Cannot save field"}}},
                            {navigate("selectfields")},
                            {fetchDetailsForCard(c)},runningOcr!=null)}
                        "selectfields"->current?.let{c->SelectDocumentTextPage(c,
                            {f->scope.launch{
                                try{withContext(Dispatchers.IO){
                                    val latest=repo.cards.firstOrNull{it.id==c.id}?:return@withContext
                                    repo.saveCard(latest.copy(fields=latest.fields+f))
                                };navigate("fulldetails")}
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
                                            val fullRotation=(initialCropRotation+rotation)%360
                                            val oriented=if(fullRotation==0)original else{
                                                try {
                                                    val matrix=android.graphics.Matrix().apply{postRotate(fullRotation.toFloat())}
                                                    android.graphics.Bitmap.createBitmap(original,0,0,original.width,original.height,matrix,true)
                                                }finally{original.recycle()}
                                            }
                                            try {
                                                val corrected=DocumentCropEngine.crop(oriented,corners)
                                                try {
                                                    val processed=ScanEnhance.apply(corrected,settings)
                                                    try {
                                                        // The cache/camera directory can be removed by Android
                                                        // after the photo was captured. Recreate it at save time.
                                                        val output=ScanOutputFiles.newScan(activity.cacheDir)
                                                        DocumentCropEngine.save(processed,output)
                                                        FileProvider.getUriForFile(activity,activity.packageName+".provider",output)
                                                    }finally{if(processed!==corrected)processed.recycle()}
                                                }finally{corrected.recycle()}
                                            }finally{oriented.recycle()}
                                        }
                                        val keep=activity.getSharedPreferences("vault_options",Context.MODE_PRIVATE)
                                            .getBoolean("keepScanOriginal",true)
                                        importUris(listOf(uri),scanTarget,addAnother,
                                            if(keep&&!cropFromAttachment)cameraUri else null,
                                            onSaved={goBack();cropBitmap=null;cropResult=null})
                                    }catch(e:java.io.IOException){
                                        error="Could not save the cropped page. Check free storage and retry. Your original image is still available."
                                    }catch(e:Exception){error="Crop failed: "+(e.message?:"Error")}
                                    catch(e:OutOfMemoryError){error="Image too large to process safely; try a lower camera resolution"}
                                    finally{busy=""}
                                }},
                                {
                                    if(cropFromAttachment)goBack()
                                    else cameraUri?.let{uri->
                                        goBack()
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
                                    selectedCardId=saved.id;editing=null;editingJson=""
                                     if(backStack.lastOrNull()=="detail")goBack()
                                     else route="detail"
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
                                    navigate(if(reviewQueue.isEmpty())"detail" else "review")
                                },{
                                    selectedCardId=job.cardId;reviewQueue.removeAt(0)
                                    navigate(if(reviewQueue.isEmpty())"detail" else "review")
                                })
                            else navigate("detail")
                        }
                        "tools"->current?.let{c->PdfToolsScreen(c,repo,
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
                                    activity.notify("Recovered document into a new card");navigate("cards")}
                                catch(e:Exception){error="Recovery failed: "+(e.message?:"Storage error")}
                                finally{busy=""}
                            }},
                            {navigate("backup")})
                        }
                    }
                    }
                    if(runningOcr!=null && busy.isBlank()) {
                        val page=runningOcr.progress.getInt(VaultOcrWorker.KEY_PAGE,0)
                        val total=runningOcr.progress.getInt(VaultOcrWorker.KEY_TOTAL,0)
                        Surface(modifier=Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                            shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface,
                            shadowElevation=5.dp) {
                            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if(total>0)"OCR page "+page+" / "+total else "OCR preparing…")
                                    Text("Runs on device",color=MaterialTheme.colorScheme.onSurfaceVariant,
                                        style=MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick={workManager.cancelUniqueWork(VaultOcrWorker.UNIQUE)}){
                                    Text("Cancel")
                                }
                            }
                        }
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

@Composable fun UnlockScreen(onPin:(String)->Boolean,waitSeconds:()->Long,onBiometric:()->Unit) {
    var pin by remember{mutableStateOf("")}
    var invalid by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Lock,null,tint=VaultColors.syntax,modifier=Modifier.size(65.dp))
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
