package com.snehith.documentvault

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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

private data class PdfJob(val op:String,val files:List<VaultFile>,val from:Int,val to:Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DocumentVaultApp(activity:MainActivity) {
    val repo=activity.repo
    val engine=activity.engine
    val scope=rememberCoroutineScope()
    val operations=activity.operations
    val operationState by operations.state.collectAsState()
    val working=operationState.busy.isNotBlank()
    val savedScreens=rememberSaveableStateHolder()
    var route by rememberSaveable{mutableStateOf(activity.initialRoute)}
    var privacyScreen by rememberSaveable{mutableStateOf(activity.isPrivacyScreenEnabled())}
    var backStack by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedCardId by rememberSaveable{mutableStateOf("")}
    var selectedPersonId by rememberSaveable{mutableStateOf("")}
    var editorDirty by rememberSaveable{mutableStateOf(false)}
    var discardEditorDialog by rememberSaveable{mutableStateOf(false)}
    var editing by remember{mutableStateOf<VaultCard?>(null)}
    var editingJson by rememberSaveable{mutableStateOf("")}
    var editingIsNew by rememberSaveable{mutableStateOf(false)}
    var viewingId by rememberSaveable{mutableStateOf("")}
    var viewingPage by rememberSaveable{mutableIntStateOf(0)}
    var cropBitmap by remember{mutableStateOf<android.graphics.Bitmap?>(null)}
    var initialCropRotation by rememberSaveable{mutableIntStateOf(0)}
    var cropResult by remember{mutableStateOf<DetectedScan?>(null)}
    var cropSessionId by rememberSaveable{mutableStateOf("")}
    var pendingCropCorners by rememberSaveable{mutableStateOf("")}
    var pendingCropRotation by rememberSaveable{mutableIntStateOf(0)}
    var cameraPath by rememberSaveable{mutableStateOf("")}
    var cameraPending by rememberSaveable{mutableStateOf(false)}
    var retakePending by rememberSaveable{mutableStateOf(false)}
    var previousCapturePath by rememberSaveable{mutableStateOf("")}
    var externalPdfPath by rememberSaveable{mutableStateOf("")}
    var cropFromAttachment by rememberSaveable{mutableStateOf(false)}
    var enhancingFileId by rememberSaveable{mutableStateOf<String?>(null)}
    var enhancingPdfPage by rememberSaveable{mutableIntStateOf(0)}
    var scanSessionContinue by rememberSaveable{mutableStateOf("")}
    var scanTarget by rememberSaveable{mutableStateOf<String?>(null)}
    var addToCardId by rememberSaveable{mutableStateOf<String?>(null)}
    var importTarget by rememberSaveable{mutableStateOf<String?>(null)}
    var userProfile by rememberSaveable{mutableStateOf("self")}
    var categoryFilter by rememberSaveable{mutableStateOf("")}
    val options=remember { activity.getSharedPreferences("vault_options",Context.MODE_PRIVATE) }
    var ocrLanguage by rememberSaveable {
        mutableStateOf(options.getString("ocrLanguage","Latin") ?: "Latin")
    }
    var busy by remember{mutableStateOf("")}
    val workManager=remember(activity){WorkManager.getInstance(activity.applicationContext)}
    val ocrFlow=remember(workManager){workManager.getWorkInfosForUniqueWorkFlow(VaultOcrWorker.UNIQUE)}
    val ocrInfos by ocrFlow.collectAsState(initial=emptyList())
    val runningOcr=ocrInfos.firstOrNull{it.state==WorkInfo.State.RUNNING}
        ?:ocrInfos.firstOrNull{it.state==WorkInfo.State.ENQUEUED}
    var trackedOcr by rememberSaveable { mutableStateOf(emptyMap<String,String>()) }
    var error by remember{mutableStateOf("")}
    var pdfJob by remember{mutableStateOf<PdfJob?>(null)}
    var backupPassword by remember{mutableStateOf("")}
    var themeMode by rememberSaveable { mutableStateOf(options.getString("displayTheme","system") ?: "system") }
    var documentStyle by rememberSaveable {
        mutableStateOf(options.getString("documentStyle","pages") ?: "pages")
    }
    var biometricEnabled by rememberSaveable {
        mutableStateOf(options.getBoolean("biometricUnlock",false))
    }
    val dark=when(themeMode){"dark"->true;"light"->false;else->isSystemInDarkTheme()}
    val current=repo.cards.firstOrNull{it.id==selectedCardId}
    val regular=route in setOf("home","cards","scan","people","more")
    // A saved, ordered history replaces route-specific guesses in Back handling.
    // Root tab changes clear the history; nested screens push their actual caller.
    fun navigate(destination:String) {
        if(working||busy.isNotBlank()||route==destination)return
        backStack=backStack+route
        route=destination
    }
    fun navigateRoot(destination:String) {
        if(working||busy.isNotBlank())return
        backStack=emptyList()
        route=destination
    }
    fun openCard(card:VaultCard){
        selectedCardId=card.id
        navigate("detail")
    }
    fun addFieldsToPerson(personId:String,card:VaultCard,fields:List<DataField>){
        val created=ProfileLogic.buildEntries(personId,card,fields,repo.entries.toList())
        if(created.isEmpty()){activity.notify("Those details are already on the profile");return}
        scope.launch {
            try{
                withContext(Dispatchers.IO){repo.changeEntries(add=created)}
                activity.notify(VaultCounts.format(created.size,"detail")+" added")
            }catch(cancelled:CancellationException){throw cancelled}
            catch(e:Exception){error="Could not update person: "+(e.message?:"Storage error")}
        }
    }
    fun startEdit(card:VaultCard){
        editingIsNew=repo.cards.none{it.id==card.id}
        editing=card
        editingJson=repo.encodeCard(card)
        navigate("edit")
    }
    fun startEnhance(card:VaultCard,file:VaultFile,page:Int){
        scope.launch{
            busy="Preparing page for enhancement"
            try{
                val source=withContext(Dispatchers.IO){
                    val pdf=file.mime=="application/pdf"||file.name.endsWith(".pdf",true)
                    if(!pdf)repo.originalDocumentFile(file).absolutePath
                    else{
                        val staged=File(activity.cacheDir,"shared/pdf-page-"+UUID.randomUUID()+".png")
                        staged.parentFile?.mkdirs()
                        try{
                            PdfPageSession(repo.documentFile(file)).use{session->
                                val bitmap=session.render(page,1700)
                                try{
                                    staged.outputStream().use{
                                        require(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)){
                                            "Could not stage PDF page for enhancement"
                                        }
                                    }
                                }finally{bitmap.recycle()}
                            }
                            staged.absolutePath
                        }catch(e:Exception){staged.delete();throw e}
                    }
                }
                val pdf=file.mime=="application/pdf"||file.name.endsWith(".pdf",true)
                val scan=withContext(Dispatchers.IO){
                    if(pdf)ScanPrepare.loadForEdit(File(source),"",0)
                    else ScanPrepare.loadForEdit(File(source),file.cropCorners,file.cropRotation)
                }
                scanTarget=card.id;cropFromAttachment=true
                enhancingFileId=file.id;enhancingPdfPage=page
                // The crop route uses cameraPath directly for imported PDF pages.
                cameraPath=source
                cropBitmap=scan.preview;cropResult=scan.corners
                cropSessionId=UUID.randomUUID().toString()
                pendingCropCorners=CropGeometry.encode(scan.corners.corners.map{it.x to it.y})
                pendingCropRotation=scan.rotation
                initialCropRotation=scan.rotation
                busy=""
                navigate("crop")
            }catch(cancelled:CancellationException){throw cancelled}
            catch(e:Exception){error="Cannot enhance page: "+(e.message?:"Error")}
            finally{busy=""}
        }
    }

    fun popBack(){
        val previous=backStack.lastOrNull()
        backStack=if(backStack.isEmpty())emptyList()else backStack.dropLast(1)
        route=previous?:"home"
    }
    fun goBack(){
        if(working||busy.isNotBlank())return
        if(route=="edit"&&editorDirty)discardEditorDialog=true
        else popBack()
    }
    fun replaceRoute(destination:String) {
        backStack=VaultRouteHistory.replace(backStack,destination)
        route=destination
    }
    fun completeToCard(cardId:String) {
        selectedCardId=cardId
        backStack=VaultRouteHistory.completedCardStack()
        route="detail"
    }
    LaunchedEffect(operationState.sequence,operationState.busy) {
        if(operationState.busy.isNotBlank())return@LaunchedEffect
        val result=operationState.result
        if(result!=null){
            if(result.clearCrop){
                cropBitmap=null;cropResult=null;enhancingFileId=null
                cameraPath="";retakePending=false;cameraPending=false
            }
            result.cardId?.let(::completeToCard)
            when(result.destination){
                "home","cards","search","people","more"->{
                    backStack=emptyList();route=result.destination ?: "home"
                }
                "externalviewer"->{
                    externalPdfPath=result.externalPath.orEmpty()
                    navigate("externalviewer")
                }
            }
            if(result.note.isNotBlank())activity.notify(result.note)
            if(result.continueScan&&result.cardId!=null) {
                scanSessionContinue=result.cardId
            }
            operations.acknowledge(operationState.sequence)
        } else operationState.error?.let{
            error=it
            operations.acknowledge(operationState.sequence)
        }
    }
    LaunchedEffect(route,selectedCardId,repo.cards.size,viewingId) {
        if(route in setOf("detail","fulldetails","attachments","tools","viewer","selectfields") &&
            (current==null || (route=="viewer"&&current.files.none{it.id==viewingId}))) {
            if(current==null){backStack=emptyList();route="cards"}
            else if(route=="viewer")replaceRoute("detail")
        }
        if(route!="externalviewer"&&externalPdfPath.isNotBlank()){
            val old=externalPdfPath
            externalPdfPath=""
            withContext(Dispatchers.IO){File(old).delete()}
        }
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
        trackedOcr=mapOf(request.id.toString() to f.name)
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
        trackedOcr=requests.mapIndexed{i,req->req.id.toString() to eligible[i].name}.toMap()
        var continuation=workManager.beginUniqueWork(
            VaultOcrWorker.UNIQUE,ExistingWorkPolicy.KEEP,requests.first())
        requests.drop(1).forEach{request->continuation=continuation.then(request)}
        continuation.enqueue()
        activity.notify("Fetching details from "+VaultCounts.format(eligible.size,"attachment")+
            " offline. Open Document Information to review and copy the results.")
    }
    LaunchedEffect(ocrInfos,trackedOcr) {
        if(trackedOcr.isEmpty())return@LaunchedEffect
        val byId=ocrInfos.associateBy{it.id.toString()}
        val entries=trackedOcr.mapNotNull{(id,name)->
            byId[id]?.takeIf{it.state.isFinished}?.let{info->
                OcrBatchOutcome(name,
                    info.state==WorkInfo.State.SUCCEEDED &&
                        info.outputData.getString(VaultOcrWorker.KEY_ERROR).isNullOrBlank(),
                    info.outputData.getString(VaultOcrWorker.KEY_ERROR).orEmpty())
            }
        }
        if(entries.size==trackedOcr.size){
            trackedOcr=emptyMap()
            val summary=OcrBatchSummary.message(entries)
            activity.notify(summary)
        }
    }
    fun importUris(uris:List<Uri>,targetId:String?=null,addPage:Boolean=false,
        originalUri:Uri?=null,savedCorners:String="",savedRotation:Int=0,
        groupAsOne:Boolean=false) {
        if(uris.isEmpty())return
        val chosen=uris.toList()
        val profile=if(userProfile=="all")"self" else userProfile
        operations.start("Importing "+VaultCounts.format(chosen.size,"document")){
            val saved=VaultLongOperations.importDocuments(repo,chosen,targetId,profile,
                originalUri,savedCorners,savedRotation,groupAsOne)
            VaultJobResult(
                note=when{
                    saved.count==0->"No new files were added"
                    saved.skipped>0->"Saved ${saved.count} files; skipped ${saved.skipped}"
                    else->"Import complete"
                },
                cardId=saved.cardId,continueScan=addPage&&saved.cardId!=null,
                clearCrop=originalUri!=null||savedCorners.isNotBlank())
        }
    }
    val importPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uriList->
        importUris(uriList,importTarget)
    }
    var pendingGallery by rememberSaveable{mutableStateOf(emptyList<String>())}
    val galleryPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()){photos->
        if(photos.size>1&&importTarget==null)pendingGallery=photos.map(Uri::toString)
        else importUris(photos,importTarget)
    }
    var cameraUri by rememberSaveable{mutableStateOf<Uri?>(null)}
    val cameraLauncher=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success->
        cameraPending=false
        if(success){
            val uri=cameraUri
            if(uri!=null)scope.launch{
                busy="Detecting document borders"
                try{
                    val prepared=withContext(Dispatchers.IO){
                        val file=File(cameraPath).takeIf{it.isFile&&it.length()>0L}
                            ?:throw IllegalStateException("Camera photo missing or empty")
                        ScanPrepare.load(activity.applicationContext,file)
                    }
                    cropBitmap=prepared.preview;cropResult=prepared.corners
                    cropSessionId=UUID.randomUUID().toString()
                    pendingCropCorners=CropGeometry.encode(prepared.corners.corners.map{it.x to it.y})
                    pendingCropRotation=prepared.rotation
                    initialCropRotation=prepared.rotation
                    retakePending=false;previousCapturePath=""
                    if(route!="crop"){
                        backStack=backStack+route;route="crop"
                    }
                }catch(cancelled:CancellationException){throw cancelled}
                catch(e:Exception){
                    error="Scan preparation failed: "+(e.message?:"Unknown error")
                    if(retakePending){
                        cameraPath=previousCapturePath;retakePending=false
                        previousCapturePath=""
                    }
                }finally{busy=""}
            }
        }else{
            if(retakePending){
                cameraPath=previousCapturePath
                previousCapturePath=""
                retakePending=false
                if(cropBitmap==null&&cropResult==null)replaceRoute("scan")
            }else if(route=="crop"){
                cameraPath=""
                replaceRoute(if(cropFromAttachment&&current!=null)"detail" else "scan")
            }
        }
    }
    LaunchedEffect(route,cameraPath,cameraPending,retakePending){
        if(CaptureRecoveryPolicy.shouldAttempt(route,cropBitmap==null,cameraPending,retakePending)){
            val path=cameraPath
            val recovered=if(path.isBlank())null else try{
                withContext(Dispatchers.IO){
                    val file=File(path)
                    if(!file.isFile||!CaptureRecoveryPolicy.hasPhotoBytes(file.length()))null
                    else if(cropFromAttachment){
                        val image=current?.files?.firstOrNull{it.id==enhancingFileId}
                        if(image!=null)ScanPrepare.loadForEdit(
                            file,image.cropCorners,image.cropRotation) else null
                     }else if(pendingCropCorners.isNotBlank())
                        ScanPrepare.loadForEdit(file,pendingCropCorners,pendingCropRotation)
                    else ScanPrepare.load(activity.applicationContext,file)
                }
            }catch(cancelled:CancellationException){throw cancelled}
            catch(e:Exception){
                error="Unable to reopen scan: "+(e.message?:"Image unavailable")
                null
            }
            if(recovered!=null){
                cropBitmap=recovered.preview;cropResult=recovered.corners
                if(cropSessionId.isBlank())cropSessionId=path
                initialCropRotation=recovered.rotation
            }else{
                cameraPath=""
                replaceRoute(if(cropFromAttachment&&current!=null)"detail" else "scan")
            }
        }
    }
    fun camera(target:String?=null,retake:Boolean=false) {
        importTarget=target;scanTarget=target
        if(!retake){cropFromAttachment=false;enhancingFileId=null}
        if(retake){previousCapturePath=cameraPath;retakePending=true}
        cameraPending=true
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
            }catch(cancelled:CancellationException){throw cancelled}
            catch(e:Exception){
                cameraPending=false
                if(retake){cameraPath=previousCapturePath;retakePending=false}
                else cameraPath=""
                error="Could not start camera: "+(e.message?:"Storage error")
            }
        }
    }
    LaunchedEffect(scanSessionContinue) {
        val next=scanSessionContinue
        if(next.isNotBlank()){scanSessionContinue="";camera(next)}
    }
    var pdfJobToExport by remember{mutableStateOf<PdfJob?>(null)}
    val pdfExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){uri->
        val job=pdfJobToExport
        if(uri!=null&&job!=null){
            val cardId=selectedCardId
            operations.start("Creating PDF"){
                when(job.op){
                    "merge"->engine.merge(job.files,uri)
                    "split"->engine.pages(job.files.first(),job.from,job.to,uri)
                    "rotate"->engine.rotate(job.files.first(),90,uri)
                    "images"->engine.imagesToPdf(job.files,uri)
                    "searchable"->engine.searchableCopy(job.files.first(),uri)
                    "export-combine"->engine.combineDocuments(job.files,uri)
                }
                VaultJobResult(note="PDF exported")
            }
        }
        pdfJobToExport=null
    }
    val modelPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)scope.launch {
            busy="Installing offline OCR language"
            try {
                val lang=withContext(Dispatchers.IO){engine.installLanguage(uri)}
                ocrLanguage="Tesseract: "+lang
                options.edit().putString("ocrLanguage",ocrLanguage).apply()
                activity.notify("Installed "+lang+" offline OCR")
            }catch(cancelled:CancellationException){throw cancelled}catch(e:Exception){error=e.message?:"Could not install language"}
            finally{busy=""}
        }
    }
    val backupExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->
        if(uri!=null)operations.start("Exporting ZIP backup"){
            val warnings=repo.exportBackup(uri,backupPassword)
            VaultJobResult(note=if(warnings.isEmpty())"Backup exported" else
                "Backup exported with ${warnings.size} missing files. See missing-files.txt in the ZIP.")
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
    LaunchedEffect(activity.incoming.size,activity.unlocked.value,working){
        if(activity.unlocked.value&&!working&&activity.incoming.isNotEmpty()){
            importUris(activity.drainIncoming())
        }
    }
    val openWith=activity.pendingView.value
    if(activity.unlocked.value&&openWith!=null){
        AlertDialog(onDismissRequest={activity.clearOpenWith()},
            title={Text("Open PDF")},
            text={Text("Save this file to Document Vault or view it without saving?")},
            confirmButton={TextButton(onClick={
                activity.importOpenWith(openWith)
            }){Text("Save to vault")}},
            dismissButton={
                Row{
                    TextButton(onClick={activity.clearOpenWith()}){Text("Cancel")}
                    TextButton(onClick={
                        activity.clearOpenWith()
                        operations.start("Opening PDF"){
                            val file=VaultLongOperations.viewPdfTemporary(activity.applicationContext,openWith)
                            VaultJobResult(destination="externalviewer",externalPath=file.absolutePath)
                        }
                    }){Text("Just view")}
                }
            })
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
                    operations.start("Restoring ZIP backup"){
                        val warnings=repo.restoreBackup(uri,backupPassword)
                        VaultJobResult(note=if(warnings.isEmpty())"Restore complete" else
                            "Restore complete; ${warnings.size} damaged attachments were skipped",destination="home")
                    }
                }
            }){Text("Replace and restore")}},
            dismissButton={TextButton(onClick={confirmRestore=false}){Text("Cancel")}})
    }
    if(discardEditorDialog) {
        AlertDialog(onDismissRequest={discardEditorDialog=false},
            title={Text("Discard changes?")},
            text={Text("Your edits have not been saved.")},
            confirmButton={TextButton(onClick={
                discardEditorDialog=false
                editorDirty=false
                editing=null
                editingJson=""
                popBack()
            }){Text("Discard",color=MaterialTheme.colorScheme.error)}},
            dismissButton={TextButton(onClick={discardEditorDialog=false}){Text("Keep editing")}})
    }
    if(error.isNotBlank()) {
        AlertDialog(onDismissRequest={error=""},title={Text("Document Vault")},text={Text(error)},
            confirmButton={TextButton(onClick={error=""}){Text("OK")}})
    }

    SideEffect { 
        activity.window.statusBarColor=if(dark)android.graphics.Color.BLACK else android.graphics.Color.rgb(230,236,237)
        activity.window.navigationBarColor=if(dark)android.graphics.Color.BLACK else android.graphics.Color.rgb(230,236,237)
        androidx.core.view.WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply {
            // Android's "light" flags mean DARK icons on a light system bar.
            isAppearanceLightStatusBars=!dark
            isAppearanceLightNavigationBars=!dark
        }
    }
    VaultTheme(dark) {
        if(activity.unlocked.value&&pendingGallery.isNotEmpty())AlertDialog(
            onDismissRequest={pendingGallery=emptyList()},
            title={Text("Import ${pendingGallery.size} photos")},
            text={Text("Save as one document with ${pendingGallery.size} pages, or make separate documents?")},
            confirmButton={TextButton(onClick={
                val chosen=pendingGallery.map(Uri::parse)
                pendingGallery=emptyList()
                importUris(chosen,groupAsOne=true)
            }){Text("One document")}},
            dismissButton={TextButton(onClick={
                val chosen=pendingGallery.map(Uri::parse)
                pendingGallery=emptyList()
                importUris(chosen)
            }){Text("Separate documents")}}
        )
        if(!activity.unlocked.value) {
            UnlockScreen(onPin={pin->
                if(withContext(Dispatchers.Default){repo.checkPin(pin)}){
                    activity.unlocked.value=true;true
                } else false
            },waitSeconds={repo.pinWaitSeconds()},
                onBiometric={activity.authenticate({activity.unlocked.value=true},{activity.notify(it)})},
                biometricAvailable=remember(activity){
                    androidx.biometric.BiometricManager.from(activity).canAuthenticate(
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
                    )==androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
                },
                autoBiometric=biometricEnabled)
        } else {
            androidx.activity.compose.BackHandler(enabled=working||busy.isNotBlank()||!regular||route!="home"){
                if(!working&&busy.isBlank())goBack()
            }
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
                                    "licenses"->"Open-source licenses"
                                    "selectfields"->"Select from document"
                                    "scan"->"Scan"
                                    "crop"->"Crop document"
                                    "edit"->"Edit Document"
                                    "viewer"->current?.files?.firstOrNull{it.id==viewingId}
                                        ?.let{VaultNames.label(it.name)} ?: "Document viewer"
                                    "externalviewer"->"Open PDF"
                                    "tools"->"PDF Tools"
                                    "expiry"->"Expiry and renewal"
                                    "profiles"->"Family profiles"
                                    "person"->"Profile"
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
                                modifier=Modifier.padding(start=8.dp).size(48.dp).clickable(
                                    enabled=!working&&busy.isBlank()){goBack()}){
                                Box(contentAlignment=Alignment.Center){
                                    Icon(Icons.Outlined.ArrowBack,"Back",tint=MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        },
                        actions={Spacer(Modifier.width(48.dp))}
                    )
                },
                bottomBar={
                    if(regular)HubBottomBar(route,
                            {if(!working&&busy.isBlank())navigateRoot(it)},
                            {if(!working&&busy.isBlank())navigate("scan")})
                }
            ){ padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    AnimatedContent(targetState=route,modifier=Modifier.fillMaxSize(),
                        transitionSpec={
                            val roots=setOf("home","cards","people","more")
                            val ease=tween<Float>(220)
                            when {
                                // Switching tabs: a quick cross-fade, no travel.
                                initialState in roots&&targetState in roots->
                                    fadeIn(ease) togetherWith fadeOut(tween(120))
                                // Opening a screen: it arrives from the right.
                                backStack.lastOrNull()==initialState->
                                    (slideInHorizontally(tween(260)){it/10}+fadeIn(ease)) togetherWith
                                        (slideOutHorizontally(tween(260)){-it/10}+fadeOut(tween(160)))
                                // Going back: the previous screen returns from the left.
                                else->
                                    (slideInHorizontally(tween(260)){-it/10}+fadeIn(ease)) togetherWith
                                        (slideOutHorizontally(tween(260)){it/10}+fadeOut(tween(160)))
                            }
                        },label="screen"){screenRoute->
                    savedScreens.SaveableStateProvider(screenRoute) {
                    when(screenRoute){
                        "home"->MockupHome(repo.cards.toList(),repo.profiles.toList(),
                            userProfile,options.getInt("remindDays",30),
                            {userProfile=it},{openCard(it)},
                            {navigateRoot("cards")},{navigateRoot("more")},
                            {navigate("expiry")},documentStyle,repo)
                        "people"->PeopleScreen(repo.profiles.toList(),repo.entries.toList(),
                            repo.cards.toList(),
                            {person->selectedPersonId=person.id;navigate("person")},
                            {name->scope.launch{
                                try{withContext(Dispatchers.IO){
                                    repo.saveProfile(VaultProfile(UUID.randomUUID().toString(),name))
                                }}
                                catch(cancelled:CancellationException){throw cancelled}
                                catch(e:Exception){error=e.message?:"Cannot add person"}
                            }})
                        "person"->repo.profiles.firstOrNull{it.id==selectedPersonId}?.let{person->
                            PersonScreen(person,repo.entries.toList(),repo.cards.toList(),repo.hasPin(),
                                {pin->repo.checkPin(pin)},
                                {entry->scope.launch{
                                    try{withContext(Dispatchers.IO){
                                        repo.changeEntries(removeIds=setOf(entry.id))
                                    }}
                                    catch(cancelled:CancellationException){throw cancelled}
                                    catch(e:Exception){error=e.message?:"Cannot remove detail"}
                                }},
                                {entry,alternative->scope.launch{
                                    try{withContext(Dispatchers.IO){
                                        repo.changeEntries(add=listOf(entry.copy(
                                            cardId=alternative.cardId,fieldId=alternative.fieldId,
                                            value=alternative.value)))
                                    }}
                                    catch(cancelled:CancellationException){throw cancelled}
                                    catch(e:Exception){error=e.message?:"Cannot update source"}
                                }},
                                {entry->scope.launch{
                                    try{withContext(Dispatchers.IO){repo.changeEntries(add=listOf(entry))}}
                                    catch(cancelled:CancellationException){throw cancelled}
                                    catch(e:Exception){error=e.message?:"Cannot add detail"}
                                }},
                                {cardId->repo.cards.firstOrNull{it.id==cardId}?.let(::openCard)},
                                {scope.launch{
                                    try{
                                        withContext(Dispatchers.IO){repo.removePerson(person.id)}
                                        if(userProfile==person.id)userProfile="all"
                                        replaceRoute("people")
                                    }catch(cancelled:CancellationException){throw cancelled}
                                    catch(e:Exception){error=e.message?:"Cannot remove person"}
                                }}
                            )
                        }
                        "cards"->VaultNavCards(repo.cards.toList(),repo,
                            {openCard(it)},{navigate("scan")},categoryFilter,
                            {navigate("search")},repo.profiles.toList(),userProfile,
                            {userProfile=it},{categoryFilter=""},{navigateRoot("people")},
                            documentStyle)
                        "search"->VaultNavSearch(repo.cards.toList(),repo,
                            {openCard(it)},repo.profiles.toList(),userProfile,{userProfile=it})
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
                            {pin,old->try{repo.setPin(pin,old);activity.notify("PIN saved")}catch(cancelled:CancellationException){throw cancelled}catch(e:Exception){error=e.message?:"PIN failed"}},
                            {pin->if(repo.checkPin(pin)){repo.clearPin(pin);activity.notify("PIN removed")} else activity.notify("Wrong PIN")},
                            {navigate("profiles")},{navigate("templates")},{navigate("backup")},{navigate("recovery")},
                            {modelPicker.launch(arrayOf("*/*"))},{
                        ocrLanguage=it
                        options.edit().putString("ocrLanguage",it).apply()
                    },
                            {days->activity.getSharedPreferences("vault_options",Context.MODE_PRIVATE).edit().putInt("remindDays",days).apply()
                                if(Build.VERSION.SDK_INT>=33)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                activity.notify("Expiry reminders: "+days+" days")},
                            options.getBoolean("keepScanOriginal",true),
                            {keep->options.edit().putBoolean("keepScanOriginal",keep).apply()},{navigate("about")},
                            {repo.retainedOriginalBytes()},
                            {operations.start("Removing retained originals"){
                                val count=repo.removeRetainedOriginals()
                                VaultJobResult(note="Removed $count retained originals")
                            }},
                            operationState.sequence,biometricEnabled,{
                                enabled->biometricEnabled=enabled
                                options.edit().putBoolean("biometricUnlock",enabled).apply()
                            },
                            profileName=repo.profiles.firstOrNull{it.id==(if(userProfile=="all")"self" else userProfile)}?.name?:"Me",
                            cardCount=VaultPersonFilter.filter(repo.cards.toList(),userProfile).size,
                            pageCount=VaultPersonFilter.filter(repo.cards.toList(),userProfile)
                                .sumOf{it.files.size},
                            peopleCount=repo.profiles.size,
                            documentStyle=documentStyle,
                            onDocumentStyleChanged={style->
                                documentStyle=style
                                options.edit().putString("documentStyle",style).apply()
                            })
                        "detail"->current?.let{c->VaultNavDetail(c,repo,
                            {f,page->viewingId=f.id;viewingPage=page;navigate("viewer")},
                            {f->activity.share(f)},{f->activity.shareAsPdf(f)},{activity.shareAll(c.files)},
                            {addToCardId=c.id},
                            {navigate("fulldetails")},{navigate("tools")},
                            {startEdit(c)},{navigate("attachments")},
                            {scope.launch{try{
                                busy="Deleting document"
                                withContext(Dispatchers.IO){repo.deleteCard(c)}
                                selectedCardId=""
                                backStack=emptyList();route="cards"
                              }catch(cancelled:CancellationException){throw cancelled}
                              catch(e:Exception){error=e.message?:"Delete failed"}
                              finally{busy=""}}},
                            {fetchDetailsForCard(c)},runningOcr!=null,
                            {file,page->startEnhance(c,file,page)},documentStyle)}
                        "attachments"->current?.let{c->AttachmentManagerScreen(c,repo,
                            {f->viewingId=f.id;viewingPage=0;navigate("viewer")},
                            {f,name->scope.launch {
                                try{withContext(Dispatchers.IO){repo.renameFile(c.id,f.id,name)}}
                                catch(cancelled:CancellationException){throw cancelled}catch(e:Exception){error="Rename failed: "+(e.message?:"Invalid filename")}
                            }},
                            {f->scope.launch {
                                try{withContext(Dispatchers.IO){repo.removeFile(c.id,f.id)}}
                                catch(cancelled:CancellationException){throw cancelled}catch(e:Exception){error="Remove failed: "+(e.message?:"Storage error")}
                            }},
                            {ids->scope.launch {
                                try{withContext(Dispatchers.IO){repo.reorderFiles(c.id,ids)}}
                                catch(cancelled:CancellationException){throw cancelled}catch(e:Exception){error="Reorder failed: "+(e.message?:"Storage error")}
                            }})}
                        "about"->VaultAboutScreen{navigate("licenses")}
                        "licenses"->VaultLicensesScreen()
                        "fulldetails"->current?.let{c->FullDetailsPage(c,
                            {updated->scope.launch{try{withContext(Dispatchers.IO){
                                    repo.updateCard(c.id){latest->
                                        latest.copy(fields=VaultFieldUpdates.merge(
                                            c.fields,updated.fields,latest.fields))
                                    }
                                }}
                                catch(cancelled:CancellationException){throw cancelled}catch(e:Exception){error=e.message?:"Cannot save field"}}},
                            {navigate("selectfields")},
                            {fetchDetailsForCard(c)},runningOcr!=null,
                            repo.profiles.toList(),
                            {personId,chosen->addFieldsToPerson(personId,c,chosen)},
                            {name,chosen->
                                scope.launch{
                                    try {
                                        val person=VaultProfile(UUID.randomUUID().toString(),name)
                                        withContext(Dispatchers.IO){
                                            repo.saveProfile(person)
                                            val added=ProfileLogic.buildEntries(person.id,c,chosen,repo.entries.toList())
                                            repo.changeEntries(add=added)
                                        }
                                        activity.notify("Person created")
                                    }catch(cancelled:CancellationException){throw cancelled}
                                    catch(e:Exception){error="Cannot create person: "+(e.message?:"Error")}
                                }
                            })}
                        "selectfields"->current?.let{c->SelectDocumentTextPage(c,
                            {f->scope.launch{
                                try{withContext(Dispatchers.IO){
                                    val latest=repo.cards.firstOrNull{it.id==c.id}?:return@withContext
                                    repo.updateCard(c.id){it.copy(fields=it.fields+f)}
                                };popBack()}
                                catch(cancelled:CancellationException){throw cancelled}
                                catch(e:Exception){error="Cannot save selected field: "+(e.message?:"Error")}
                            }},
                            {_->fetchDetailsForCard(c)})}
                        "crop"->{
                            val image=cropBitmap;val result=cropResult
                            if(image!=null&&result!=null)CropReviewScreen(image,result,
                                {rotation,corners,settings,addAnother->
                                    val source=cameraPath
                                    val target=scanTarget
                                    val editMode=cropFromAttachment
                                    val chosen=enhancingFileId
                                    val originalUri=cameraUri
                                    val keep=options.getBoolean("keepScanOriginal",true)
                                    val totalRotation=(initialCropRotation+rotation)%360
                                    val normalized=CropGeometry.encode(corners.map{it.x to it.y})
                                    val context=activity.applicationContext
                                    operations.start("Cropping and enhancing document"){
                                        val uri=VaultLongOperations.correctedUri(context,source,
                                            totalRotation,corners,settings)
                                        if(editMode){
                                            require(target!=null&&chosen!=null){"Selected attachment unavailable"}
                                            val original=repo.cards.firstOrNull{it.id==target}
                                                ?.files?.firstOrNull{it.id==chosen}
                                                ?:throw IllegalStateException("Original attachment was removed")
                                            if(original.mime=="application/pdf"||
                                                original.name.endsWith(".pdf",true)){
                                                val tmp=File(activity.cacheDir,
                                                    "shared/enhanced-"+UUID.randomUUID()+".pdf")
                                                tmp.parentFile?.mkdirs()
                                                try{
                                                    val pdfUri=FileProvider.getUriForFile(context,
                                                        context.packageName+".provider",tmp)
                                                    engine.enhancedPdfPage(original,enhancingPdfPage,uri,pdfUri)
                                                    val next=repo.importFile(pdfUri)
                                                    try{repo.updateCard(target){latest->
                                                        latest.copy(files=latest.files+next.copy(
                                                            name=VaultNames.label(original.name)
                                                                .removeSuffix(".pdf")+" - enhanced.pdf"))
                                                    }}catch(cancelled:CancellationException){throw cancelled}
                                                    catch(e:Exception){repo.removeOrphan(next);throw e}
                                                }finally{tmp.delete()}
                                                VaultJobResult(note="Enhanced PDF copy saved; all other pages kept",
                                                    cardId=target,clearCrop=true)
                                            }else{
                                                val next=repo.importFile(uri)
                                                try{repo.replaceImageFile(target,chosen,next,
                                                    normalized,totalRotation)}
                                                catch(cancelled:CancellationException){throw cancelled}
                                                catch(e:Exception){repo.removeOrphan(next);throw e}
                                                VaultJobResult(note="Photo updated",
                                                    cardId=target,clearCrop=true)
                                            }
                                        }else{
                                            val saved=VaultLongOperations.importDocuments(repo,listOf(uri),target,
                                                if(userProfile=="all")"self" else userProfile,
                                                if(keep)originalUri else null,normalized,totalRotation)
                                            VaultJobResult(note=if(saved.count>0)"Page saved" else "Duplicate skipped",
                                                cardId=saved.cardId,continueScan=addAnother&&saved.cardId!=null,
                                                clearCrop=saved.cardId!=null)
                                        }
                                    }
                                },
                                {
                                    if(cropFromAttachment){cropBitmap=null;cropResult=null;goBack()}
                                    else cameraUri?.let{uri->
                                        val target=scanTarget
                                        operations.start("Saving original photo"){
                                            val saved=VaultLongOperations.importDocuments(repo,listOf(uri),target,
                                                if(userProfile=="all")"self" else userProfile)
                                            VaultJobResult(note="Original saved",cardId=saved.cardId,
                                                clearCrop=saved.cardId!=null)
                                        }
                                    }
                                },
                                {cropBitmap=null;cropResult=null;enhancingFileId=null;goBack()},
                                {camera(scanTarget,retake=true)},
                                canRetake=!cropFromAttachment,
                                sessionKey=cropSessionId.ifBlank{cameraPath})
                        }
                        "edit"->(editing?:editingJson.takeIf{it.isNotBlank()}?.let{repo.decodeCard(it)})?.let{c->
                            CardEditorScreen(c,repo.profiles.toList(),
                            {saved->scope.launch {
                                busy="Saving card"
                                try {
                                    withContext(Dispatchers.IO){
                                        if(editingIsNew&&repo.cards.none{it.id==saved.id})
                                            repo.saveCard(saved)
                                        else repo.updateCard(saved.id){latest->
                                            saved.copy(
                                                files=latest.files,
                                                fields=VaultFieldUpdates.merge(
                                                    c.fields,saved.fields,latest.fields)
                                            )
                                        }
                                    }
                                    editingIsNew=false
                                    selectedCardId=saved.id;editing=null;editingJson="";editorDirty=false
                                    if(backStack.lastOrNull()=="detail")popBack()
                                    else completeToCard(saved.id)
                                }catch(cancelled:CancellationException){throw cancelled}catch(e:Exception){error="Cannot save card: "+(e.message?:"Storage error")}
                                finally{busy=""}
                            }},
                            {goBack()},
                            {t->scope.launch {try{withContext(Dispatchers.IO){repo.saveTemplate(t)};activity.notify("Saved template "+t.name)}
                                catch(cancelled:CancellationException){throw cancelled}catch(e:Exception){error=e.message?:"Unable to save template"}}},
                             {editorDirty=it})}
                        "externalviewer"->{
                            val external=File(externalPdfPath)
                            if(externalPdfPath.isNotBlank())
                                DocumentViewerScreen(VaultFile("shared-view",external.name,
                                    "application/pdf",external.length(),""),repo,engine,
                                    {activity.notify("Save to vault before sharing")},sourceOverride=external)
                        }
                        "viewer"->current?.files?.firstOrNull{it.id==viewingId}?.let{f->
                            DocumentViewerScreen(f,repo,engine,{activity.share(f)},
                                initialPage=viewingPage)
                        }
                        "tools"->current?.let{c->PdfToolsScreen(c,repo,
                            {addToCardId=c.id},
                            {op,files,from,to->
                                if(op=="combine"){
                                    operations.start("Saving combined PDF"){
                                        val staged=File(activity.cacheDir,
                                            "shared/combined-"+UUID.randomUUID()+".pdf")
                                        staged.parentFile?.mkdirs()
                                        try{
                                            val uri=FileProvider.getUriForFile(activity,
                                                activity.packageName+".provider",staged)
                                            engine.combineDocuments(files,uri)
                                            val imported=repo.importFile(uri)
                                            try{
                                                repo.updateCard(c.id){latest->
                                                    latest.copy(files=latest.files+imported.copy(
                                                        name=VaultNames.title(c.title)+" - combined.pdf"))
                                                }
                                            }catch(e:Exception){repo.removeOrphan(imported);throw e}
                                            VaultJobResult(note="Combined PDF added",cardId=c.id)
                                        }finally{staged.delete()}
                                    }
                                }else{
                                    pdfJobToExport=PdfJob(op,files,from,to)
                                    pdfExport.launch(if(op=="export-combine")
                                        VaultNames.title(c.title)+" - combined.pdf"
                                        else VaultNames.title(c.title)+" - "+op+".pdf")
                                }
                            },{file,page->startEnhance(c,file,page)})}
                        "expiry"->ExpiriesScreen(repo.cards.toList(),{openCard(it)},
                            options.getInt("remindDays",30))
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
                            {file,title->
                                operations.start("Recovering document"){
                                    repo.attachRecovered(file.id,title)
                                    VaultJobResult(note="Recovered document",destination="cards")
                                }
                            },
                            {navigate("backup")})
                        }
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
                    if(busy.isNotBlank()||working) {
                        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.scrim.copy(alpha=.62f)) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                                CircularProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text(if(working)operationState.busy else busy,color=Color.White)

                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun UnlockScreen(
    onPin:suspend (String)->Boolean,waitSeconds:()->Long,
    onBiometric:()->Unit,biometricAvailable:Boolean,autoBiometric:Boolean=false
) {
    val scope=rememberCoroutineScope()
    var pin by remember{mutableStateOf("")}
    var invalid by remember{mutableStateOf(false)}
    var trying by remember{mutableStateOf(false)}
    var remaining by remember{mutableLongStateOf(waitSeconds())}
    LaunchedEffect(Unit) {
        while(true){
            remaining=withContext(Dispatchers.Default){waitSeconds()}
            kotlinx.coroutines.delay(1000)
        }
    }
    LaunchedEffect(autoBiometric,biometricAvailable) {
        if(autoBiometric&&biometricAvailable)onBiometric()
    }
    Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center,
        horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Lock,null,tint=VaultColors.syntax,modifier=Modifier.size(65.dp))
        Spacer(Modifier.height(24.dp))
        Text("Document Vault",style=MaterialTheme.typography.headlineMedium,
            fontWeight=FontWeight.Bold)
        Text("Unlock your personal document library")
        Spacer(Modifier.height(8.dp))
        Text("The PIN locks the app. It does not encrypt your saved documents.",
            color=MaterialTheme.colorScheme.onSurfaceVariant,
            style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(30.dp))
        OutlinedTextField(pin,{pin=it;invalid=false},label={Text("Vault PIN")},
            visualTransformation=PasswordVisualTransformation(),
            keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(
                keyboardType=androidx.compose.ui.text.input.KeyboardType.NumberPassword),
            enabled=!trying&&remaining==0L,
            singleLine=true,modifier=Modifier.fillMaxWidth())
        if(remaining>0)Text("Too many attempts. Try again in $remaining seconds.",
            color=MaterialTheme.colorScheme.error)
        else if(invalid)Text("Incorrect PIN",color=MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick={
            val submitted=pin
            scope.launch {
                trying=true
                try {
                    if(!onPin(submitted)){invalid=true;pin=""}
                }catch(cancelled:CancellationException){throw cancelled}
                catch(e:Exception){invalid=true}
                finally{trying=false}
            }
        },modifier=Modifier.fillMaxWidth(),
            enabled=pin.isNotBlank()&&!trying&&remaining==0L){
            Text(if(trying)"Checking…" else "Unlock")
        }
        if(biometricAvailable)OutlinedButton(onClick=onBiometric,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Icon(Icons.Outlined.Fingerprint,null);Text(" Use biometrics")
        }
    }
}
