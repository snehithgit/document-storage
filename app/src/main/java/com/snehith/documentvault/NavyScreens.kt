package com.snehith.documentvault

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.temporal.ChronoUnit

val VaultNavy:Color @Composable get()=MaterialTheme.colorScheme.background
val VaultPanel:Color @Composable get()=MaterialTheme.colorScheme.surface
val VaultRaised:Color @Composable get()=MaterialTheme.colorScheme.surface
val VaultBlue=VaultColors.primaryText
val VaultSky:Color @Composable get()=MaterialTheme.colorScheme.primary
val VaultMuted:Color @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant
val bgBrush:Brush @Composable get()=vaultWarmBrush(MaterialTheme.colorScheme.background==VaultColors.darkBg)
private val rounded=RoundedCornerShape(14.dp)

@Composable fun VaultHeader(title:String,subtitle:String?=null) {
    Column {
        Text(title,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.headlineSmall)
        if(subtitle!=null)Text(subtitle,color=VaultMuted,style=MaterialTheme.typography.bodySmall)
    }
}
/** Size-bounded cache, owned by the process; bitmaps are not recycled while Compose may render them. */
private val thumbnailCache=object:LruCache<String,Bitmap>(16*1024*1024){
    override fun sizeOf(key:String,value:Bitmap):Int=value.byteCount
}
@Composable fun VaultThumbnail(file:VaultFile?,repo:VaultRepository,modifier:Modifier=Modifier,maxEdge:Int=640) {
    var bitmap by remember(file?.id,maxEdge){mutableStateOf<Bitmap?>(null)}
    LaunchedEffect(file?.id,maxEdge) {
        bitmap=null
        if(file==null)return@LaunchedEffect
        try {
            val image=withContext(Dispatchers.IO) {
                val raw=repo.documentFile(file)
                val cacheKey=file.id+"-"+maxEdge+"-"+raw.lastModified()
                synchronized(thumbnailCache){thumbnailCache.get(cacheKey)} ?: run {
                    val thumbnail=if(file.mime=="application/pdf"||file.name.endsWith(".pdf",true)){
                        PdfPageSession(raw).use{it.render(0,maxEdge)}
                    }else if(file.mime.startsWith("image/")){
                        ImageDecode.decode(raw,maxEdge,highQuality=true)
                    }else null
                    if(thumbnail!=null)synchronized(thumbnailCache){thumbnailCache.put(cacheKey,thumbnail)}
                    thumbnail
                }
            }
            bitmap=image
        }catch(_:Exception){ /* Invalid attachment falls back to the document icon. */ }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant,rounded),contentAlignment=Alignment.Center){
        if(bitmap!=null)Image(bitmap!!.asImageBitmap(),"Document",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
        else Icon(Icons.Outlined.Description,null,tint=VaultSky,modifier=Modifier.size(37.dp))
    }
}
fun vaultValidity(card:VaultCard):Pair<String,Color> {
    val days=try{ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(card.expiry))}catch(_:Exception){null}
    return when {
        days==null && card.expiry.isNotBlank()->"Check expiry" to VaultColors.expiring
        days==null->"Saved" to Color(0xFF64748B)
        days<0->"Expired" to VaultColors.expired
        days<=30->"Expiring" to VaultColors.expiring
        else->"Valid" to VaultColors.valid
    }
}
@Composable fun VaultPill(text:String,color:Color=VaultBlue){
    Surface(color=color.copy(alpha=.12f),shape=RoundedCornerShape(50)){
        Text(text,color=if(color==VaultBlue)VaultSky else color,style=MaterialTheme.typography.labelSmall,
            modifier=Modifier.padding(horizontal=8.dp,vertical=4.dp),maxLines=1)
    }
}
@Composable fun VaultTile(card:VaultCard,repo:VaultRepository,onClick:()->Unit){
    Card(onClick=onClick,colors=CardDefaults.cardColors(containerColor=VaultPanel),shape=rounded,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),
        elevation=CardDefaults.cardElevation(defaultElevation=0.dp)){
        Column(Modifier.fillMaxWidth().padding(9.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
            Box {
                VaultThumbnail(card.files.firstOrNull(),repo,Modifier.fillMaxWidth().height(140.dp))
                Box(Modifier.align(Alignment.TopEnd).padding(5.dp)){VaultPill(card.category.ifBlank{"Other"}.take(17))}
            }
            Text(VaultNames.title(card.title),color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,
                style=MaterialTheme.typography.bodyMedium,maxLines=2,minLines=2,overflow=TextOverflow.Ellipsis)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                val(v,c)=vaultValidity(card)
                VaultPill(v,c)
                Text(VaultCounts.format(card.files.size,"file"),color=VaultMuted,style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}
@Composable fun VaultRow(card:VaultCard,repo:VaultRepository,onClick:()->Unit){
    val(v,c)=vaultValidity(card)
    Card(onClick=onClick,colors=CardDefaults.cardColors(containerColor=VaultPanel),shape=rounded,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),
        elevation=CardDefaults.cardElevation(defaultElevation=0.dp)){
        Row(Modifier.padding(9.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            VaultThumbnail(card.files.firstOrNull(),repo,Modifier.size(65.dp,72.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)){
                Text(VaultNames.title(card.title),color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(card.category.ifBlank{"Other"},color=VaultMuted,style=MaterialTheme.typography.labelSmall)
                Text(VaultCounts.format(card.files.size,"attachment"),color=VaultColors.syntax,style=MaterialTheme.typography.labelSmall)
            }
            VaultPill(v,c)
        }
    }
}
@Composable fun VaultNavHome(cards:List<VaultCard>,repo:VaultRepository,profiles:List<VaultProfile>,profile:String,
    onProfile:(String)->Unit,onOpen:(VaultCard)->Unit,onSearch:()->Unit,onScan:()->Unit,
    onImport:()->Unit,onNew:()->Unit,onPdf:()->Unit,onExpiries:()->Unit) {
    val visible=cards.filter{profile=="all"||it.profile==profile}
    LazyColumn(Modifier.fillMaxSize().background(bgBrush),contentPadding=PaddingValues(15.dp,12.dp,15.dp,95.dp),
        verticalArrangement=Arrangement.spacedBy(15.dp)){
        item {
            Column(Modifier.fillMaxWidth().background(VaultPanel,rounded)
                .border(1.dp,MaterialTheme.colorScheme.outlineVariant,rounded).padding(17.dp)){
                Text("MY DOCUMENT SPACE",color=VaultSky,style=MaterialTheme.typography.labelSmall)
                Text("Your essentials, organized.",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick=onSearch,modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Outlined.Search,null);Text(" Search documents and details")
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                (listOf(VaultProfile("all","Everyone"))+profiles).forEach{p->
                    FilterChip(selected=profile==p.id,onClick={onProfile(p.id)},label={Text(p.name)})
                }
            }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
                VaultMetric(visible.size.toString(),"Cards",Modifier.weight(1f))
                VaultMetric(visible.sumOf{it.files.size}.toString(),"Files",Modifier.weight(1f))
                VaultMetric(visible.count{vaultValidity(it).first in listOf("Expired","Expiring","Check expiry")}.toString(),
                    "Expiring",Modifier.weight(1f),onExpiries)
            }
        }
        item {
            Text("Quick actions",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                VaultQuick("Scan",Icons.Outlined.DocumentScanner,Modifier.weight(1f),onScan)
                VaultQuick("Import",Icons.Outlined.FileUpload,Modifier.weight(1f),onImport)
                VaultQuick("New Card",Icons.Outlined.AddCard,Modifier.weight(1f),onNew)
                VaultQuick("PDF",Icons.Outlined.PictureAsPdf,Modifier.weight(1f),onPdf)
            }
        }
        item {VaultHeader("Recent documents",VaultCounts.format(visible.size,"card")+" in your vault")}
        items(visible.sortedByDescending{it.created}.take(10),key={it.id}){c->VaultRow(c,repo){onOpen(c)}}
        if(visible.isEmpty())item{
            Text("Import a document or scan a new one to get started.",color=VaultMuted)
        }
    }
}
@Composable fun VaultMetric(n:String,label:String,modifier:Modifier,onClick:()->Unit={}){
    Card(onClick=onClick,modifier=modifier,colors=CardDefaults.cardColors(containerColor=VaultPanel),shape=rounded,
        elevation=CardDefaults.cardElevation(defaultElevation=3.dp)){
        Column(Modifier.padding(11.dp)){
            Text(n,color=VaultSky,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.headlineSmall)
            Text(label,color=VaultMuted,style=MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable fun VaultQuick(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier,onClick:()->Unit){
    Card(onClick=onClick,modifier=modifier,colors=CardDefaults.cardColors(containerColor=VaultPanel),
         border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),shape=RoundedCornerShape(12.dp)){
        Column(Modifier.padding(vertical=12.dp,horizontal=2.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Icon(icon,null,tint=MaterialTheme.colorScheme.onSurface)
            Text(label,color=MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable fun VaultNavCards(cards:List<VaultCard>,repo:VaultRepository,
    onOpen:(VaultCard)->Unit,onNew:()->Unit,selectedCategory:String="All",onSearch:()->Unit={}){
    var filter by rememberSaveable{mutableStateOf("All")}
    var sort by rememberSaveable{mutableStateOf("Recent")}
    var listView by rememberSaveable{mutableStateOf(false)}
    var showSort by remember{mutableStateOf(false)}
    LaunchedEffect(selectedCategory){filter=selectedCategory}
    val categories=remember(cards){listOf("All")+cards.map{it.category.ifBlank{"Other"}}.distinct().sorted()}
    val visible=remember(cards,filter,sort){DocumentBrowseLogic.browse(cards,filter,sort)}
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=14.dp),
            verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                VaultHeader("Documents",VaultCounts.format(visible.size,"document"))
            }
            IconButton(onClick=onSearch,modifier=Modifier.size(48.dp)){
                Icon(Icons.Outlined.Search,"Search documents")
            }
            IconButton(onClick={listView=!listView},modifier=Modifier.size(48.dp)){
                Icon(if(listView)Icons.Outlined.GridView else Icons.Outlined.ViewList,
                    if(listView)"Grid view" else "List view")
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            categories.forEach{category->
                FilterChip(selected=category==filter,onClick={filter=category},
                    label={Text(category)})
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),
            verticalAlignment=Alignment.CenterVertically){
            Box{
                TextButton(onClick={showSort=true}){
                    Icon(Icons.Outlined.Sort,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sort: "+sort)
                    Icon(Icons.Outlined.ArrowDropDown,null)
                }
                DropdownMenu(expanded=showSort,onDismissRequest={showSort=false}){
                    listOf("Recent","Name","Category","Expiry").forEach{choice->
                        DropdownMenuItem(text={Text(choice)},onClick={sort=choice;showSort=false})
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick=onNew){
                Icon(Icons.Outlined.Add,null,Modifier.size(18.dp))
                Text(" Add")
            }
        }
        if(visible.isEmpty()){
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
                Text("No documents in this category.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }else if(listView){
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp,8.dp,16.dp,24.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)){
                items(visible,key={it.id}){card->VaultRow(card,repo){onOpen(card)}}
            }
        }else{
            BoxWithConstraints(Modifier.weight(1f)){
                val columns=if(maxWidth>=600.dp)3 else 2
                LazyVerticalGrid(columns=GridCells.Fixed(columns),
                    contentPadding=PaddingValues(16.dp,8.dp,16.dp,24.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp),
                    horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    items(visible,key={it.id}){card->VaultTile(card,repo){onOpen(card)}}
                }
            }
        }
    }
}
private data class VaultSearchHit(val card:VaultCard,val snippet:String?)

@Composable fun VaultNavSearch(cards:List<VaultCard>,repo:VaultRepository,onOpen:(VaultCard)->Unit){
    var query by rememberSaveable{mutableStateOf("")}
    var mode by rememberSaveable{mutableStateOf("All")}
    var hits by remember(cards){mutableStateOf(cards.map{VaultSearchHit(it,null)})}
    var searching by remember{mutableStateOf(false)}
    LaunchedEffect(query,mode,cards){
        searching=true
        delay(250)
        val snapshot=cards.toList()
        hits=withContext(Dispatchers.Default){
            snapshot.mapNotNull{card->
                val document=card.title+" "+card.category+" "+card.kind+" "+
                    card.files.joinToString(" "){it.name}
                val information=card.fields.joinToString(" "){it.name+" "+it.value}
                val scanned=card.files.joinToString(" "){it.extractedText}
                val matches=when(mode){
                    "Documents"->document.contains(query,true)
                    "Information"->information.contains(query,true)
                    "Scanned Text"->scanned.contains(query,true)
                    else->document.contains(query,true)||information.contains(query,true)||
                        scanned.contains(query,true)
                }
                if(!matches)null else {
                    val excerpt=if(query.isBlank())null else
                        card.fields.firstOrNull{it.name.contains(query,true)||it.value.contains(query,true)}
                            ?.let{it.name+": "+it.value}
                            ?:card.files.firstNotNullOfOrNull{file->
                                val pos=file.extractedText.indexOf(query,ignoreCase=true)
                                if(pos<0)null else file.extractedText.substring(
                                    (pos-30).coerceAtLeast(0),
                                    (pos+query.length+70).coerceAtMost(file.extractedText.length))
                            }
                    VaultSearchHit(card,excerpt)
                }
            }
        }
        searching=false
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .padding(horizontal=16.dp)){
        Spacer(Modifier.height(18.dp))
        VaultHeader("Search","Find documents and saved information")
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
            leadingIcon={Icon(Icons.Outlined.Search,null)},
            placeholder={Text("Search documents or information")})
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("All","Documents","Information","Scanned Text").forEach{choice->
                FilterChip(selected=mode==choice,onClick={mode=choice},label={Text(choice)})
            }
        }
        if(searching)LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=8.dp))
        Text(VaultCounts.format(hits.size,"result"),color=VaultMuted,
            modifier=Modifier.padding(vertical=12.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),
            contentPadding=PaddingValues(bottom=24.dp)){
            items(hits,key={it.card.id}){hit->
                Card(onClick={onOpen(hit.card)},
                    colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){
                    Row(Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment=Alignment.CenterVertically){
                        VaultThumbnail(hit.card.files.firstOrNull(),repo,Modifier.size(64.dp,76.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)){
                            Text(VaultNames.title(hit.card.title),style=MaterialTheme.typography.titleMedium)
                            Text(hit.card.category,color=VaultMuted,style=MaterialTheme.typography.bodySmall)
                            hit.snippet?.let{excerpt->
                                Text(excerpt,color=MaterialTheme.colorScheme.onSurfaceVariant,
                                    style=MaterialTheme.typography.bodySmall,maxLines=3,
                                    overflow=TextOverflow.Ellipsis)
                            }
                        }
                        Icon(Icons.Outlined.ChevronRight,null,tint=VaultMuted)
                    }
                }
            }
        }
    }
}
@Composable fun VaultNavDetail(card:VaultCard,repo:VaultRepository,onFile:(VaultFile)->Unit,onOcr:(VaultFile)->Unit,
    onShare:(VaultFile)->Unit,onShareAll:()->Unit,onAdd:()->Unit,
    onFull:()->Unit,onTools:()->Unit,onEdit:()->Unit,onManage:()->Unit,onDelete:()->Unit,
    onFetchDetails:()->Unit,fetchingDetails:Boolean,onEnhance:(VaultFile)->Unit){
    var selectedId by rememberSaveable(card.id){mutableStateOf("")}
    val selected=card.files.firstOrNull{it.id==selectedId}?:card.files.firstOrNull()
    val context=LocalContext.current
    val (status,color)=vaultValidity(card)
    var showMore by remember{mutableStateOf(false)}
    var fileMenuId by remember{mutableStateOf<String?>(null)}
    var confirmDelete by rememberSaveable(card.id){mutableStateOf(false)}
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},
        title={Text("Delete document?")},
        text={Text("The document and its unshared attachments will be removed. This cannot be undone.")},
        confirmButton={TextButton(onClick={confirmDelete=false;onDelete()}){Text("Delete",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(onClick={confirmDelete=false}){Text("Cancel")}})
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding=PaddingValues(horizontal=16.dp,vertical=16.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text(card.category.ifBlank{"Personal"},color=MaterialTheme.colorScheme.onSurfaceVariant,
                        style=MaterialTheme.typography.bodySmall)
                    Text(VaultCounts.format(card.files.size,"attachment"),
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        style=MaterialTheme.typography.bodySmall)
                }
                if(card.expiry.isNotBlank())VaultPill(status,color)
            }
        }
        item{
            Card(onClick={selected?.let(onFile)},
                enabled=selected!=null,
                colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                shape=RoundedCornerShape(16.dp),
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){
                Column(Modifier.fillMaxWidth()){
                    VaultThumbnail(selected,repo,Modifier.fillMaxWidth().height(300.dp),maxEdge=1550)
                    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),
                        verticalAlignment=Alignment.CenterVertically){
                        Text(if(selected==null)"No attachment added" else
                            "File ${card.files.indexOfFirst{it.id==selected.id}+1} of ${card.files.size}",
                            color=MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier=Modifier.weight(1f),
                            style=MaterialTheme.typography.bodySmall)
                        Text(if(selected!=null)"Tap to view" else "Add a page to begin",
                            color=VaultColors.syntax,style=MaterialTheme.typography.bodySmall)
                        Icon(Icons.Outlined.OpenInFull,null,Modifier.padding(start=5.dp).size(16.dp),
                            tint=VaultColors.syntax)
                    }
                }
            }
            if(card.files.size>1) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    card.files.forEach{file->
                        Surface(
                            modifier=Modifier.size(64.dp,74.dp).clickable{selectedId=file.id},
                            shape=RoundedCornerShape(10.dp),
                            border=BorderStroke(if(selected?.id==file.id)2.dp else 1.dp,
                                if(selected?.id==file.id)VaultColors.syntax else MaterialTheme.colorScheme.outline),
                            color=MaterialTheme.colorScheme.surface){
                            VaultThumbnail(file,repo,Modifier.fillMaxSize().padding(3.dp))
                        }
                    }
                }
            }
        }
        item{
            Button(onClick=onFetchDetails,
                enabled=!fetchingDetails && card.files.any{DetailFetchPlan.supported(it)},
                modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),
                shape=RoundedCornerShape(14.dp)){
                Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(21.dp))
                Spacer(Modifier.width(10.dp))
                Text(if(fetchingDetails)"Getting details…" else "GET DETAILS",
                    style=MaterialTheme.typography.titleMedium)
            }
        }
        item{
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,
                horizontalArrangement=Arrangement.SpaceBetween){
                @Composable fun action(icon:androidx.compose.ui.graphics.vector.ImageVector,
                    label:String,enabled:Boolean=true,click:()->Unit){
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){
                        IconButton(onClick=click,enabled=enabled,modifier=Modifier.size(48.dp)){
                            Icon(icon,label,Modifier.size(22.dp))
                        }
                        Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant,
                            style=MaterialTheme.typography.labelSmall,maxLines=2)
                    }
                }
                action(Icons.Outlined.Edit,"Edit",click=onEdit)
                action(Icons.Outlined.AddCircleOutline,"Add Page",click=onAdd)
                action(Icons.Outlined.Share,"Share",enabled=selected!=null,
                    click={selected?.let(onShare)})
                action(Icons.Outlined.PictureAsPdf,"PDF Tools",click=onTools)
                Box(Modifier.weight(1f),contentAlignment=Alignment.TopCenter){
                    Column(horizontalAlignment=Alignment.CenterHorizontally){
                        IconButton(onClick={showMore=true},modifier=Modifier.size(48.dp)){
                            Icon(Icons.Outlined.MoreHoriz,"More document options")
                        }
                        Text("More",style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded=showMore,onDismissRequest={showMore=false}){
                        DropdownMenuItem(text={Text("Manage attachments")},onClick={showMore=false;onManage()})
                        DropdownMenuItem(text={Text("Share all files")},enabled=card.files.isNotEmpty(),
                            onClick={showMore=false;onShareAll()})
                        if(selected?.mime?.startsWith("image/")==true){
                            DropdownMenuItem(text={Text("Enhance or crop image")},
                                onClick={showMore=false;onEnhance(selected)})
                        }
                        DropdownMenuItem(text={Text("Delete document")},onClick={showMore=false;confirmDelete=true})
                    }
                }
            }
        }
        item{
            HubCard(Modifier.fillMaxWidth()){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text("Information",style=MaterialTheme.typography.titleLarge,
                        modifier=Modifier.weight(1f))
                    TextButton(onClick=onFull){Text("View All Information")}
                }
                if(card.fields.isEmpty())Text("No information saved yet. Use Get Details or add fields.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                card.fields.take(5).forEach{field->
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f).padding(vertical=8.dp)){
                            Text(field.name,color=MaterialTheme.colorScheme.onSurfaceVariant,
                                style=MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            Text(field.value.ifBlank{"Not entered"},style=MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick={copyToClipboard(context,field.name,field.value)},
                            enabled=field.value.isNotBlank(),modifier=Modifier.size(48.dp)){
                            Icon(Icons.Outlined.ContentCopy,"Copy "+field.name,
                                modifier=Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        item{
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text("Attachments",style=MaterialTheme.typography.titleLarge,
                    modifier=Modifier.weight(1f))
                TextButton(onClick=onManage){Text("Manage")}
            }
        }
        if(card.files.isEmpty())item {
            Text("No files attached. Choose Add Page to scan or import one.",
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.bodySmall)
        }
        items(card.files,key={it.id}){file->
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),
                shape=RoundedCornerShape(14.dp)){
                Row(Modifier.fillMaxWidth().padding(start=10.dp,end=4.dp,top=10.dp,bottom=10.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    VaultThumbnail(file,repo,Modifier.size(58.dp,67.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).clickable{onFile(file)}){
                        Text(VaultNames.label(file.name),style=MaterialTheme.typography.bodyMedium,
                            maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(file.mime.substringAfterLast('/')+" · "+
                            (file.bytes/1024).coerceAtLeast(1)+" KB",
                            color=MaterialTheme.colorScheme.onSurfaceVariant,
                            style=MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick={onFile(file)},modifier=Modifier.size(48.dp)){
                        Icon(Icons.Outlined.Visibility,"View "+VaultNames.label(file.name))
                    }
                    Box{
                        IconButton(onClick={fileMenuId=file.id},modifier=Modifier.size(48.dp)){
                            Icon(Icons.Outlined.MoreVert,"More options for "+VaultNames.label(file.name))
                        }
                        DropdownMenu(expanded=fileMenuId==file.id,
                            onDismissRequest={fileMenuId=null}){
                            DropdownMenuItem(text={Text("Share")},onClick={fileMenuId=null;onShare(file)})
                            DropdownMenuItem(text={Text("Read page")},onClick={fileMenuId=null;onOcr(file)})
                            if(file.mime.startsWith("image/"))
                                DropdownMenuItem(text={Text("Enhance or crop")},
                                    onClick={fileMenuId=null;onEnhance(file)})
                            DropdownMenuItem(text={Text("Manage attachments")},onClick={fileMenuId=null;onManage()})
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun EditorImportRow(
    index:String,title:String,detail:String,
    icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit
){
    Surface(
        modifier=Modifier.fillMaxWidth().clickable(onClick=onClick),
        color=MaterialTheme.colorScheme.surface,
        shape=RoundedCornerShape(14.dp),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)
    ){
        Row(Modifier.padding(horizontal=16.dp,vertical=17.dp),verticalAlignment=Alignment.CenterVertically){
            Text(index,color=VaultColors.lineNumber,style=MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier.size(42.dp).background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(10.dp)),
                contentAlignment=Alignment.Center
            ){Icon(icon,null,tint=MaterialTheme.colorScheme.onSurface,modifier=Modifier.size(21.dp))}
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(title,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyLarge)
                Text(detail,color=MaterialTheme.colorScheme.onSurfaceVariant,
                    style=MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.Outlined.NorthEast,null,
                tint=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(18.dp))
        }
    }
}

/** A deliberate source choice: the centered Scan action never opens the camera automatically. */
@Composable fun NavyScanScreen(
    onCamera:()->Unit,
    onGallery:()->Unit,
    onFiles:()->Unit,
    onNew:()->Unit
){
    val choices=listOf(
        Triple("Scan with Camera","Take a photo and crop automatically",Icons.Outlined.CameraAlt),
        Triple("Import Photos","Choose one or more gallery photos",Icons.Outlined.PhotoLibrary),
        Triple("Import Files or PDF","Choose documents from phone storage",Icons.Outlined.FolderOpen),
        Triple("Create Information Card","Save information without a file",Icons.Outlined.NoteAdd)
    )
    val actions=listOf(onCamera,onGallery,onFiles,onNew)
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding=PaddingValues(horizontal=16.dp,vertical=20.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            Text("Add Document",style=MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text("Choose how you want to add your document.",
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
        }
        items(choices.size){i->
            val (title,description,icon)=choices[i]
            Card(onClick=actions[i],
                modifier=Modifier.fillMaxWidth().heightIn(min=100.dp),
                shape=RoundedCornerShape(16.dp),
                colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(14.dp)){
                    Surface(color=MaterialTheme.colorScheme.surfaceVariant,
                        shape=RoundedCornerShape(12.dp),modifier=Modifier.size(52.dp)){
                        Box(contentAlignment=Alignment.Center){
                            Icon(icon,null,modifier=Modifier.size(26.dp),
                                tint=MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text(title,style=MaterialTheme.typography.titleMedium,
                            fontWeight=FontWeight.SemiBold)
                        Text(description,style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ChevronRight,null,
                        tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item{
            Spacer(Modifier.height(10.dp))
            Text("Documents stay on this device unless you choose to share or export them.",
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.bodySmall)
        }
    }
}
