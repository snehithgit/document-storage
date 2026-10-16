package com.snehith.documentvault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val Blue=VaultColors.syntax
private val Mint=VaultColors.valid
private val Gold=VaultColors.expiring
private val Corners=RoundedCornerShape(20.dp)

fun copyToClipboard(context:Context,label:String,value:String) {
    if(value.isBlank())return
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText(label,value))
    android.widget.Toast.makeText(context,"Copied",android.widget.Toast.LENGTH_SHORT).show()
}
@Composable fun Heading(title:String,description:String?=null) {
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        if(description!=null)Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun AccentChip(label:String,color:Color=Blue) {
    Surface(color=color.copy(alpha=.11f),shape=RoundedCornerShape(10.dp)) {
        Text(label,modifier=Modifier.padding(horizontal=10.dp,vertical=6.dp),color=color,style=MaterialTheme.typography.labelSmall)
    }
}
@Composable fun CardTile(card:VaultCard,onOpen:()->Unit) {
    Card(onClick=onOpen,shape=Corners,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow),
        modifier=Modifier.fillMaxWidth().height(171.dp)) {
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Surface(shape=RoundedCornerShape(12.dp),color=if(card.expiry.isNotBlank())Gold.copy(alpha=.13f)else Blue.copy(alpha=.12f)) {
                Icon(if(card.kind.contains("Passport",true)) Icons.Outlined.MenuBook else if(card.kind.contains("Medical",true))Icons.Outlined.FavoriteBorder else Icons.Outlined.Description,
                    null,tint=if(card.expiry.isNotBlank())Gold else Blue,modifier=Modifier.padding(10.dp).size(23.dp))
            }
            Column {
                Text(VaultNames.title(card.title),maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
                Text(card.category,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.AttachFile,null,modifier=Modifier.size(14.dp))
                    Text(VaultCounts.format(card.files.size,"file"),style=MaterialTheme.typography.labelSmall)
                    if(card.expiry.isNotBlank())Text(" · "+card.expiry,style=MaterialTheme.typography.labelSmall,maxLines=1)
                }
            }
        }
    }
}
@Composable fun VaultHome(cards:List<VaultCard>,profiles:List<VaultProfile>,profileId:String,onProfile:(String)->Unit,onOpen:(VaultCard)->Unit,onNew:()->Unit,onImport:()->Unit,onScan:()->Unit,onExpiries:()->Unit) {
    val current=cards.filter{profileId=="all"||it.profile==profileId}
    val expiring=current.count{card -> try{ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(card.expiry))<=30}catch(_:Exception){false}}
    LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=12.dp,bottom=92.dp),
        horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(span={GridItemSpan(2)}) {
            Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Heading("My Document Vault","Your documents and details, available offline")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected=profileId=="all",onClick={onProfile("all")},label={Text("Everyone")})
                    profiles.forEach{p->FilterChip(selected=profileId==p.id,onClick={onProfile(p.id)},label={Text(p.name)})}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    SummaryStat(current.size.toString(),"Cards",Blue,Modifier.weight(1f))
                    SummaryStat(current.sumOf{it.files.size}.toString(),"Files",Mint,Modifier.weight(1f))
                    SummaryStat(expiring.toString(),"Expiring",Gold,Modifier.weight(1f),onExpiries)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Button(onClick=onNew,modifier=Modifier.weight(1f),shape=RoundedCornerShape(13.dp)){
                        Icon(Icons.Outlined.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("New Card")
                    }
                    OutlinedButton(onClick=onImport,modifier=Modifier.weight(1f),shape=RoundedCornerShape(13.dp)){
                        Icon(Icons.Outlined.UploadFile,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Import")
                    }
                }
                OutlinedButton(onClick=onScan,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp)){
                    Icon(Icons.Outlined.CameraAlt,null);Spacer(Modifier.width(8.dp));Text("Scan with Camera")
                }
                Text("Recent cards",fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium)
            }
        }
        items(current.sortedByDescending{it.created},key={it.id}){c->CardTile(c){onOpen(c)}}
        if(current.isEmpty()) item(span={GridItemSpan(2)}){
            Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally){
                Icon(Icons.Outlined.FolderOpen,null,Modifier.size(46.dp),tint=Blue)
                Spacer(Modifier.height(10.dp));Text("Your vault is ready",fontWeight=FontWeight.SemiBold)
                Text("Import a document or create a reusable information card.",style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
@Composable fun SummaryStat(value:String,label:String,color:Color,modifier:Modifier=Modifier,onClick:()->Unit={}) {
    Card(onClick=onClick,modifier=modifier,colors=CardDefaults.cardColors(containerColor=color.copy(alpha=.09f)),shape=RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(13.dp)) {
            Text(value,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge,color=color)
            Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun VaultLibrary(cards:List<VaultCard>,profiles:List<VaultProfile>,onOpen:(VaultCard)->Unit,onNew:()->Unit) {
    var filter by remember{mutableStateOf("All")}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Heading("All cards",VaultCounts.format(cards.size,"saved record"))
            IconButton(onClick=onNew){Icon(Icons.Outlined.AddCircleOutline,null)}
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (listOf("All")+cards.map{it.category}.distinct().sorted()).forEach{
                FilterChip(selected=filter==it,onClick={filter=it},label={Text(it)})
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(horizontal=16.dp,vertical=4.dp),
            horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(cards.filter{filter=="All"||it.category==filter},key={it.id}){c->CardTile(c){onOpen(c)}}
        }
    }
}
@Composable fun VaultSearch(cards:List<VaultCard>,onOpen:(VaultCard)->Unit) {
    var query by remember{mutableStateOf("")}
    val results=remember(query,cards.toList()) {
        val q=query.trim()
        if(q.isEmpty())cards else cards.filter{c->
            (listOf(c.title,c.kind,c.category,c.notes,c.tags,c.expiry)+c.fields.flatMap{listOf(it.name,it.value)}+
                c.files.flatMap{listOf(it.name,it.extractedText)}).any{it.contains(q,true)}
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Heading("Smart Search","Find information inside cards, fields and OCR text")
        OutlinedTextField(value=query,onValueChange={query=it},label={Text("Search everything")},
            leadingIcon={Icon(Icons.Outlined.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Text(results.size.toString()+" results",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelMedium)
        LazyColumn(contentPadding=PaddingValues(bottom=90.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            items(results,key={it.id}){c->
                Card(onClick={onOpen(c)},modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Outlined.Description,null,tint=Blue)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)){
                            Text(c.title,fontWeight=FontWeight.SemiBold)
                            Text(c.category+" · "+VaultCounts.format(c.files.size,"file"),style=MaterialTheme.typography.labelSmall)
                        }
                        Icon(Icons.Outlined.ChevronRight,null)
                    }
                }
            }
        }
    }
}
@Composable fun CardDetailScreen(card:VaultCard,onEdit:()->Unit,onAddFile:()->Unit,onPreview:(VaultFile)->Unit,onExtract:(VaultFile)->Unit,onShare:(VaultFile)->Unit,onShareAll:()->Unit,onPdfTools:()->Unit,onRenew:()->Unit,onDelete:()->Unit) {
    val context=LocalContext.current
    var confirmDelete by remember{mutableStateOf(false)}
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete this card?")},
        text={Text("The card and its encrypted attachments will be deleted from this device.")},
        confirmButton={TextButton(onClick={confirmDelete=false;onDelete()}){Text("Delete")}},
        dismissButton={TextButton(onClick={confirmDelete=false}){Text("Cancel")}})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,16.dp,16.dp,100.dp),
        verticalArrangement=Arrangement.spacedBy(15.dp)) {
        item{
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Heading(card.title,card.category+" · "+card.kind)
                    Spacer(Modifier.height(8.dp))
                    if(card.expiry.isNotBlank())AccentChip("Expiry "+card.expiry,Gold)
                }
                IconButton(onClick=onEdit){Icon(Icons.Outlined.Edit,null)}
            }
        }
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick=onAddFile,modifier=Modifier.weight(1f)){Icon(Icons.Outlined.AttachFile,null);Spacer(Modifier.width(4.dp));Text("Attach")}
                OutlinedButton(onClick=onShareAll,modifier=Modifier.weight(1f),enabled=card.files.isNotEmpty()){
                    Icon(Icons.Outlined.Share,null);Spacer(Modifier.width(4.dp));Text("Share all")
                }
            }
        }
        item{Text(VaultCounts.format(card.files.size,"document"),fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium)}
        items(card.files,key={it.id}){f->
            ElevatedCard {
                Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Icon(if(f.mime.startsWith("image"))Icons.Outlined.Image else Icons.Outlined.Description,null,tint=Blue)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)){
                            Text(VaultNames.label(f.name),fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(((f.bytes/1024).coerceAtLeast(1)).toString()+" KB · "+f.ocrStatus,style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(5.dp),modifier=Modifier.fillMaxWidth()){
                        TextButton(onClick={onPreview(f)}){Icon(Icons.Outlined.Visibility,null,Modifier.size(17.dp));Text(" View")}
                        TextButton(onClick={onExtract(f)}){Icon(Icons.Outlined.DocumentScanner,null,Modifier.size(17.dp));Text(" OCR")}
                        TextButton(onClick={onShare(f)}){Icon(Icons.Outlined.Share,null,Modifier.size(17.dp));Text(" Share")}
                    }
                }
            }
        }
        item{
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Information fields",fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium)
                TextButton(onClick=onEdit){Text("Edit fields")}
            }
        }
        // Index keys are unique even when two manual fields have identical names/values.
        items(card.fields.size){i->
            val f=card.fields[i]
            Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(13.dp)){
                Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text(f.name,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionContainer{Text(f.value.ifBlank{"—"},fontWeight=FontWeight.Medium)}
                        if(f.source=="extracted")Text("Extracted · review before use",style=MaterialTheme.typography.labelSmall,color=Gold)
                    }
                    IconButton(onClick={copyToClipboard(context,f.name,f.value)}){Icon(Icons.Outlined.ContentCopy,"Copy "+f.name,tint=Blue)}
                }
            }
        }
        item{
            OutlinedButton(onClick={
                copyToClipboard(context,"Card details",card.fields.joinToString("\n"){it.name+": "+it.value})
            },modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Outlined.ContentCopy,null);Spacer(Modifier.width(8.dp));Text("Copy all information")
            }
        }
        if(card.notes.isNotBlank()) item {
            Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
                Text("Notes",fontWeight=FontWeight.SemiBold);SelectionContainer{Text(card.notes)}
            }
        }
        item {
            HorizontalDivider()
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                TextButton(onClick=onPdfTools){Icon(Icons.Outlined.PictureAsPdf,null);Text(" PDF tools")}
                TextButton(onClick=onRenew){Icon(Icons.Outlined.History,null);Text(" Renew")}
                TextButton(onClick={confirmDelete=true}){Icon(Icons.Outlined.Delete,null);Text(" Delete")}
            }
        }
    }
}
@Composable fun CardEditorScreen(card:VaultCard,profiles:List<VaultProfile>,onSave:(VaultCard)->Unit,onCancel:()->Unit,onSaveTemplate:(CardTemplate)->Unit,onDirtyChange:(Boolean)->Unit) {
    var title by rememberSaveable(card.id){mutableStateOf(card.title)}
    var category by rememberSaveable(card.id){mutableStateOf(card.category)}
    var kind by rememberSaveable(card.id){mutableStateOf(card.kind)}
    var tags by rememberSaveable(card.id){mutableStateOf(card.tags)}
    var notes by rememberSaveable(card.id){mutableStateOf(card.notes)}
    var expiry by rememberSaveable(card.id){mutableStateOf(card.expiry)}
    var profile by rememberSaveable(card.id){mutableStateOf(card.profile)}
    val fieldsSaver=listSaver<SnapshotStateList<DataField>,String>(
        save={list->list.flatMap{listOf(it.id,it.name,it.value,it.page.toString(),it.source,it.confidence.toString())}},
        restore={items->mutableStateListOf<DataField>().apply {
            items.chunked(6).forEach{v->if(v.size==6)
                add(DataField(v[1],v[2],v[3].toIntOrNull()?:-1,v[4],v[0],v[5].toFloatOrNull()?:0f))
            }
        }}
    )
    val fields=rememberSaveable(card.id,saver=fieldsSaver){
        mutableStateListOf<DataField>().apply{addAll(card.fields)}
    }
    var categoryExpanded by rememberSaveable{mutableStateOf(false)}
    var kindExpanded by rememberSaveable{mutableStateOf(false)}
    var profileExpanded by rememberSaveable{mutableStateOf(false)}
    var error by rememberSaveable{mutableStateOf("")}
    val changed=title!=card.title||category!=card.category||kind!=card.kind||
        tags!=card.tags||notes!=card.notes||expiry!=card.expiry||profile!=card.profile||
        fields.toList()!=card.fields
    LaunchedEffect(changed){onDirtyChange(changed)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,8.dp,16.dp,105.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
        item{Heading(if(card.title=="New Card")"Create Document" else "Edit Document","Save information and attach files when needed")}
        item{OutlinedTextField(title,{title=it},label={Text("Document title")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item{
            Box {
                OutlinedButton(onClick={kindExpanded=true},modifier=Modifier.fillMaxWidth()){Text("Type: "+kind);Icon(Icons.Outlined.ArrowDropDown,null)}
                DropdownMenu(expanded=kindExpanded,onDismissRequest={kindExpanded=false}){
                    FieldSuggestions.templates.forEach{t-> DropdownMenuItem(text={Text(t.name)},onClick={
                        kind=t.name;kindExpanded=false
                        if(fields.all{it.value.isBlank()}){fields.clear();fields.addAll(t.fieldNames.map{DataField(it,"")})}
                    })}
                }
            }
        }
        item{OutlinedTextField(category,{category=it},label={Text("Category")},modifier=Modifier.fillMaxWidth())}
        item{
            Box {
                OutlinedButton(onClick={profileExpanded=true},modifier=Modifier.fillMaxWidth()){
                    Text("Profile: "+(profiles.firstOrNull{it.id==profile}?.name?:"Me"));Icon(Icons.Outlined.ArrowDropDown,null)
                }
                DropdownMenu(expanded=profileExpanded,onDismissRequest={profileExpanded=false}){
                    profiles.forEach{p->DropdownMenuItem(text={Text(p.name)},onClick={profile=p.id;profileExpanded=false})}
                }
            }
        }
        item{OutlinedTextField(tags,{tags=it},label={Text("Tags (comma-separated)")},modifier=Modifier.fillMaxWidth())}
        item{OutlinedTextField(expiry,{expiry=it},label={Text("Expiry (YYYY-MM-DD)")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item{Text("Custom fields",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)}
        items(fields.size){i->
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        OutlinedTextField(fields[i].name,{value->fields[i]=fields[i].copy(name=value)},
                            label={Text("Field name")},modifier=Modifier.weight(1f),singleLine=true)
                        IconButton(onClick={fields.removeAt(i)}){Icon(Icons.Outlined.Close,"Remove field")}
                    }
                    OutlinedTextField(fields[i].value,{value->fields[i]=fields[i].copy(value=value,source="manual")},
                        label={Text("Value")},modifier=Modifier.fillMaxWidth())
                }
            }
        }
        item{OutlinedButton(onClick={fields.add(DataField("New field",""))},modifier=Modifier.fillMaxWidth()){
            Icon(Icons.Outlined.Add,null);Text(" Add custom field")
        }}
        item{OutlinedTextField(notes,{notes=it},label={Text("Notes")},modifier=Modifier.fillMaxWidth(),minLines=2)}
        if(error.isNotBlank())item{Text(error,color=MaterialTheme.colorScheme.error)}
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=onCancel,modifier=Modifier.weight(1f)){Text("Cancel")}
                Button(onClick={
                    if(title.isBlank()){error="Enter a title";return@Button}
                    if(expiry.isNotBlank())try{LocalDate.parse(expiry)}catch(_:Exception){error="Use YYYY-MM-DD for expiry";return@Button}
                    onSave(card.copy(title=title,kind=kind,category=category,profile=profile,tags=tags,notes=notes,expiry=expiry,
                        fields=fields.filter{it.name.isNotBlank()}))
                },modifier=Modifier.weight(1f)){Icon(Icons.Outlined.Save,null);Text(" Save")}
            }
        }
        item{TextButton(onClick={
            if(title.isNotBlank())onSaveTemplate(CardTemplate(java.util.UUID.randomUUID().toString(),title+" template",fields.map{it.name}.filter{it.isNotBlank()}))
        },modifier=Modifier.fillMaxWidth()){Text("Save these fields as reusable template")}}
    }
}
@Composable fun OcrReviewScreen(fileName:String,extracted:ExtractedDocument,onApprove:(List<DataField>)->Unit,onCancel:()->Unit) {
    val fields=remember(extracted){mutableStateListOf<DataField>().apply{addAll(extracted.fields)}}
    var showingText by remember{mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,10.dp,16.dp,110.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Heading("Review extracted details",fileName+" · "+extracted.method)}
        item{AccentChip("Review before saving",Gold)}
        item{Text("Detected text is a suggestion, not a verified identity record. Edit or delete incorrect values.",
            style=MaterialTheme.typography.bodySmall)}
        items(fields.size){i->
            Card {
                Column(Modifier.padding(11.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        OutlinedTextField(fields[i].name,{fields[i]=fields[i].copy(name=it)},
                            modifier=Modifier.weight(1f),label={Text("Field name")})
                        IconButton(onClick={fields.removeAt(i)}){Icon(Icons.Outlined.Delete,null)}
                    }
                    if(fields[i].confidence>0f) {
                        Surface(shape=RoundedCornerShape(50),
                            color=MaterialTheme.colorScheme.primary.copy(alpha=.12f)){
                            Text("Confidence ${(fields[i].confidence*100).toInt()}% · review",
                                color=MaterialTheme.colorScheme.secondary,
                                style=MaterialTheme.typography.labelSmall,
                                modifier=Modifier.padding(horizontal=9.dp,vertical=5.dp))
                        }
                    }
                    OutlinedTextField(fields[i].value,{fields[i]=fields[i].copy(value=it)},
                        modifier=Modifier.fillMaxWidth(),label={Text("Extracted value")})
                }
            }
        }
        item{OutlinedButton(onClick={fields.add(DataField("New field","","-1".toInt(),"manual"))},modifier=Modifier.fillMaxWidth()){
            Icon(Icons.Outlined.Add,null);Text(" Add field")
        }}
        item{TextButton(onClick={showingText=!showingText}){
            Icon(Icons.Outlined.TextSnippet,null);Text(if(showingText)" Hide full extracted text" else " Show full extracted text")
        }}
        if(showingText)item{
            Surface(shape=Corners,color=MaterialTheme.colorScheme.surfaceContainer) {
                SelectionContainer{Text(extracted.text,Modifier.fillMaxWidth().padding(12.dp),style=MaterialTheme.typography.bodySmall)}
            }
        }
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=onCancel,modifier=Modifier.weight(1f)){Text("Skip fields")}
                Button(onClick={onApprove(fields.toList())},modifier=Modifier.weight(1f)){Text("Approve & save")}
            }
        }
    }
}
@Composable fun ExpiriesScreen(cards:List<VaultCard>,onOpen:(VaultCard)->Unit) {
    val renewed=cards.map{it.previousId}.filter{it.isNotBlank()}.toSet()
    val active=cards.filter{it.id !in renewed}
    val dated=active.mapNotNull{card->
        val days=runCatching{
            ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(card.expiry))
        }.getOrNull()
        if(days==null)null else card to days
    }
    val invalid=active.filter{it.expiry.isNotBlank()&&!VaultExpiry.isIsoDate(it.expiry)}
    val expired=dated.filter{it.second<0}.sortedBy{it.second}
    val soon=dated.filter{it.second in 0..30}.sortedBy{it.second}
    val valid=dated.filter{it.second>30}.sortedBy{it.second}
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding=PaddingValues(16.dp,16.dp,16.dp,24.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Heading("Expiry & Renewal","View dates and upcoming renewals")}
        if(expired.isEmpty()&&soon.isEmpty()&&valid.isEmpty()&&invalid.isEmpty())
            item{Text("No expiry dates added yet. Edit a document to add one.",
                color=MaterialTheme.colorScheme.onSurfaceVariant)}
        fun androidx.compose.foundation.lazy.LazyListScope.group(
            title:String,entries:List<Pair<VaultCard,Long>>,color:Color){
            if(entries.isNotEmpty()){
                item{Text(title,style=MaterialTheme.typography.titleLarge,
                    modifier=Modifier.padding(top=8.dp))}
                items(entries,key={it.first.id}){(card,days)->
                    Card(onClick={onOpen(card)},
                        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){
                        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Outlined.Event,null,tint=color)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)){
                                Text(VaultNames.title(card.title),style=MaterialTheme.typography.titleMedium)
                                Text(card.expiry,color=MaterialTheme.colorScheme.onSurfaceVariant,
                                    style=MaterialTheme.typography.bodySmall)
                            }
                            Text(when{
                                days<0 -> "${-days} days overdue"
                                days==0L -> "Expires today"
                                else -> "${days} days left"
                            },color=color,style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        group("Expired",expired,VaultColors.expired)
        group("Expiring Soon",soon,VaultColors.expiring)
        group("Valid",valid,VaultColors.valid)
        if(invalid.isNotEmpty()) {
            item{Text("Dates to check",style=MaterialTheme.typography.titleLarge,
                modifier=Modifier.padding(top=8.dp))}
            items(invalid,key={it.id}){card->
                Card(onClick={onOpen(card)},
                    colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                    border=BorderStroke(1.dp,VaultColors.expiring)){
                    Column(Modifier.fillMaxWidth().padding(14.dp)){
                        Text(VaultNames.title(card.title),style=MaterialTheme.typography.titleMedium)
                        Text("Check expiry date: "+card.expiry,
                            color=VaultColors.expiring,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
