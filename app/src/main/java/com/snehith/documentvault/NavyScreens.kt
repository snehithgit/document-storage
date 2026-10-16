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
private val rounded=RoundedCornerShape(24.dp)

@Composable fun VaultHeader(title:String,subtitle:String?=null) {
    Column {
        Text(title,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.headlineSmall)
        if(subtitle!=null)Text(subtitle,color=VaultMuted,style=MaterialTheme.typography.bodySmall)
    }
}
/** Size-bounded cache, owned by the process; bitmaps are not recycled while Compose may render them. */
private val thumbnailCache=object:LruCache<String,Bitmap>(16*1024*1024){
    override fun sizeOf(key:String,value:Bitmap):Int=value.byteCount
}
@Composable fun VaultThumbnail(file:VaultFile?,repo:VaultRepository,modifier:Modifier=Modifier) {
    var bitmap by remember(file?.id){mutableStateOf<Bitmap?>(null)}
    LaunchedEffect(file?.id) {
        bitmap=null
        if(file==null)return@LaunchedEffect
        try {
            val cacheKey=file.id+"-"+repo.documentFile(file).lastModified()
            val image=withContext(Dispatchers.IO) {
                synchronized(thumbnailCache){thumbnailCache.get(cacheKey)} ?: run {
                    val raw=repo.documentFile(file)
                    val thumbnail=if(file.mime=="application/pdf"||file.name.endsWith(".pdf",true)){
                        PdfPageSession(raw).use{it.render(0,340)}
                    }else if(file.mime.startsWith("image/")){
                        ImageDecode.decode(raw,440)
                    }else null
                    if(thumbnail!=null)synchronized(thumbnailCache){thumbnailCache.put(cacheKey,thumbnail)}
                    thumbnail
                }
            }
            bitmap=image
        }catch(_:Exception){ /* Invalid attachment falls back to the document icon. */ }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant,rounded),contentAlignment=Alignment.Center){
        if(bitmap!=null)Image(bitmap!!.asImageBitmap(),"Document",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
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
        elevation=CardDefaults.cardElevation(defaultElevation=3.dp)){
        Column(Modifier.fillMaxWidth().padding(9.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
            Box {
                VaultThumbnail(card.files.firstOrNull(),repo,Modifier.fillMaxWidth().height(140.dp))
                Box(Modifier.align(Alignment.TopEnd).padding(5.dp)){VaultPill(card.kind.take(12))}
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
        elevation=CardDefaults.cardElevation(defaultElevation=3.dp)){
        Row(Modifier.padding(9.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            VaultThumbnail(card.files.firstOrNull(),repo,Modifier.size(65.dp,72.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)){
                Text(card.title,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(card.kind+" · "+card.category,color=VaultMuted,style=MaterialTheme.typography.labelSmall)
                Text(VaultCounts.format(card.files.size,"attachment"),color=VaultSky,style=MaterialTheme.typography.labelSmall)
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
@Composable fun VaultNavCards(cards:List<VaultCard>,repo:VaultRepository,onOpen:(VaultCard)->Unit,onNew:()->Unit,selectedCategory:String="All"){
    var filter by rememberSaveable{mutableStateOf("All")}
    LaunchedEffect(selectedCategory){ if(selectedCategory!="All")filter=selectedCategory }
    Column(Modifier.fillMaxSize().background(bgBrush)){
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){VaultHeader("Document cards",VaultCounts.format(cards.size,"card")+" saved")}
            IconButton(onClick=onNew){Icon(Icons.Outlined.AddCircle,null,tint=VaultSky)}
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=15.dp),
            horizontalArrangement=Arrangement.spacedBy(7.dp)){
            (listOf("All")+cards.map{it.kind}.distinct()+cards.map{it.category}.distinct()).distinct().forEach{t->
                FilterChip(selected=t==filter,onClick={filter=t},label={Text(t)})
            }
        }
        LazyVerticalGrid(columns=GridCells.Fixed(2),
            contentPadding=PaddingValues(15.dp,10.dp,15.dp,95.dp),
            verticalArrangement=Arrangement.spacedBy(11.dp),horizontalArrangement=Arrangement.spacedBy(11.dp)){
            items(cards.filter{filter=="All"||it.kind==filter||it.category==filter},key={it.id}){c->VaultTile(c,repo){onOpen(c)}}
        }
    }
}
private data class VaultSearchHit(val card:VaultCard,val snippet:String?)

@Composable fun VaultNavSearch(cards:List<VaultCard>,repo:VaultRepository,onOpen:(VaultCard)->Unit){
    var query by rememberSaveable{mutableStateOf("")}
    var mode by rememberSaveable{mutableStateOf("All")}
    var hits by remember(cards){mutableStateOf(cards.map{VaultSearchHit(it,null)})}
    LaunchedEffect(query,mode,cards){
        delay(250)
        val snapshot=cards.toList()
        hits=withContext(Dispatchers.Default){
            snapshot.mapNotNull{card->
                val text=card.title+" "+card.category+" "+card.kind+" "+
                    card.fields.joinToString(" "){it.name+" "+it.value}+" "+
                    card.files.joinToString(" "){it.name+" "+it.extractedText}
                val matches=(query.isBlank()||text.contains(query,true))&&
                    (mode=="All"||(mode=="Fields"&&card.fields.any{it.value.contains(query,true)})||
                        (mode=="OCR"&&card.files.any{it.extractedText.contains(query,true)}))
                if(!matches)null else{
                    val snippet=if(query.isBlank())null else
                        card.fields.firstOrNull{it.value.contains(query,true)}?.let{"${it.name}: ${it.value}"}
                            ?:card.files.firstNotNullOfOrNull{f->
                                val start=f.extractedText.indexOf(query,ignoreCase=true)
                                if(start<0)null else f.extractedText.substring(
                                    (start-25).coerceAtLeast(0),
                                    (start+query.length+55).coerceAtMost(f.extractedText.length))
                            }
                    VaultSearchHit(card,snippet)
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().background(bgBrush).padding(horizontal=15.dp)){
        Spacer(Modifier.height(14.dp))
        VaultHeader("Smart search","Search cards, scanned text, and copyable fields")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
            leadingIcon={Icon(Icons.Outlined.Search,null)},placeholder={Text("Search anything…")})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("All","Fields","OCR").forEach{m->
                FilterChip(selected=mode==m,onClick={mode=m},label={Text(m)})
            }
        }
        Text(VaultCounts.format(hits.size,"matching record"),color=VaultMuted,modifier=Modifier.padding(vertical=12.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=85.dp)){
            items(hits,key={it.card.id}){hit->
                VaultRow(hit.card,repo){onOpen(hit.card)}
                hit.snippet?.let{value->
                    Text(value,color=VaultSky,style=MaterialTheme.typography.bodySmall,
                        modifier=Modifier.padding(horizontal=8.dp))
                }
            }
        }
    }
}
@Composable fun VaultNavDetail(card:VaultCard,repo:VaultRepository,onFile:(VaultFile)->Unit,onOcr:(VaultFile)->Unit,
    onShare:(VaultFile)->Unit,onShareAll:()->Unit,onAdd:()->Unit,
    onFull:()->Unit,onTools:()->Unit,onEdit:()->Unit,onManage:()->Unit,onDelete:()->Unit,
    onEnhance:(VaultFile)->Unit){
    var selected by rememberSaveable(card.id){mutableIntStateOf(0)}
    val context=LocalContext.current
    val(status,color)=vaultValidity(card)
    var confirm by rememberSaveable(card.id){mutableStateOf(false)}
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Delete card?")},
        text={Text("The associated documents will also be removed.")},
        confirmButton={TextButton(onClick={confirm=false;onDelete()}){Text("Delete")}},
        dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
    LazyColumn(Modifier.fillMaxSize().background(bgBrush),contentPadding=PaddingValues(15.dp,10.dp,15.dp,95.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {
            VaultThumbnail(card.files.getOrNull(selected),repo,Modifier.fillMaxWidth().height(254.dp))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                card.files.forEachIndexed{i,f->
                    Box(Modifier.border(if(i==selected)2.dp else 1.dp,
                        if(i==selected)VaultBlue else VaultSky,rounded).clickable{selected=i}.padding(3.dp)){
                        VaultThumbnail(f,repo,Modifier.size(66.dp,69.dp))
                    }
                }
                Card(onClick=onAdd,colors=CardDefaults.cardColors(containerColor=VaultPanel),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
                    Column(Modifier.size(66.dp,75.dp),horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.Center){
                        Icon(Icons.Outlined.Add,null,tint=VaultSky)
                        Text("Add",color=VaultSky)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){VaultHeader(VaultNames.title(card.title),"${card.kind} · ${card.category}")}
                VaultPill(status,color)
                IconButton(onClick=onEdit){Icon(Icons.Outlined.Edit,null,tint=VaultSky)}
            }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                VaultQuick("View",Icons.Outlined.Visibility,Modifier.weight(1f),
                    {card.files.getOrNull(selected)?.let(onFile)})
                VaultQuick("OCR",Icons.Outlined.DocumentScanner,Modifier.weight(1f),
                    {card.files.getOrNull(selected)?.let(onOcr)})
                VaultQuick("Share",Icons.Outlined.Share,Modifier.weight(1f),
                    {card.files.getOrNull(selected)?.let(onShare)})
                VaultQuick("PDF",Icons.Outlined.PictureAsPdf,Modifier.weight(1f),onTools)
            }
        }
        item {
            Button(onClick=onFull,modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Outlined.ContentCopy,null);Text(" Full Details / Copy Fields")
                Spacer(Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(VaultPanel,rounded).border(1.dp,MaterialTheme.colorScheme.outlineVariant,rounded).padding(12.dp)){
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Quick Copy",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    TextButton(onClick=onFull){Text("All fields →")}
                }
                card.fields.take(4).forEach{f->
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text(f.name,color=VaultMuted,style=MaterialTheme.typography.labelSmall)
                            Text(f.value.ifBlank{"—"},color=MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick={copyToClipboard(context,f.name,f.value)}){
                            Icon(Icons.Outlined.ContentCopy,null,tint=VaultSky)
                        }
                    }
                }
                if(card.fields.isEmpty())Text("Choose details from OCR or create fields.",color=VaultMuted)
            }
        }
        item {
            card.files.getOrNull(selected)?.takeIf{it.mime.startsWith("image/")}?.let{f->
                OutlinedButton(onClick={onEnhance(f)},modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Outlined.AutoFixHigh,null)
                    Text(" Enhance / Crop image (keep original)")
                }
            }
            OutlinedButton(onClick=onManage,modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FolderOpen,null)
                Text(" Manage individual attachments")
            }
            OutlinedButton(onClick=onAdd,modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Outlined.Add,null);Text(" Add pages / documents")
            }
            OutlinedButton(onClick=onShareAll,enabled=card.files.isNotEmpty(),modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Outlined.Share,null);Text(" Share all attachments")
            }
            TextButton(onClick={confirm=true}){Text("Delete card",color=MaterialTheme.colorScheme.error)}
        }
    }
}


@Composable fun NavyScanScreen(onCamera:()->Unit,onImport:()->Unit,onNew:()->Unit){
    Column(Modifier.fillMaxSize().background(VaultNavy).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)){
        VaultHeader("Scan & add","Capture clean multi-page documents, all on device")
        Card(onClick=onCamera,colors=CardDefaults.cardColors(containerColor=VaultPanel),shape=RoundedCornerShape(19.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
            Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
                Icon(Icons.Outlined.DocumentScanner,null,tint=VaultSky,modifier=Modifier.size(61.dp))
                Spacer(Modifier.height(12.dp))
                Text("Scan with auto-crop",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,
                    style=MaterialTheme.typography.titleMedium)
                Text("Detect edges · adjust four corners · straighten perspective",
                    color=VaultMuted,style=MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(13.dp))
                Button(onClick=onCamera){Icon(Icons.Outlined.CameraAlt,null);Text(" Open camera")}
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Card(onClick=onImport,modifier=Modifier.weight(1f),
                colors=CardDefaults.cardColors(containerColor=VaultPanel),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
                Column(Modifier.padding(17.dp)){
                    Icon(Icons.Outlined.UploadFile,null,tint=VaultSky,modifier=Modifier.size(29.dp))
                    Spacer(Modifier.height(9.dp))
                    Text("Import files",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold)
                    Text("PDF, JPG, PNG, DOCX",color=VaultMuted,style=MaterialTheme.typography.labelSmall)
                }
            }
            Card(onClick=onNew,modifier=Modifier.weight(1f),
                colors=CardDefaults.cardColors(containerColor=VaultPanel),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
                Column(Modifier.padding(17.dp)){
                    Icon(Icons.Outlined.AddCard,null,tint=VaultSky,modifier=Modifier.size(29.dp))
                    Spacer(Modifier.height(9.dp))
                    Text("Information card",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold)
                    Text("Create without files",color=VaultMuted,style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        Column(Modifier.background(VaultPanel,RoundedCornerShape(12.dp)).border(1.dp,MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(12.dp)).padding(15.dp)){
            Text("Multi-page document workflow",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text("1. Open a document card, then tap Add to attach more scans or PDFs.",
                color=VaultMuted,style=MaterialTheme.typography.bodyMedium)
            Text("2. Open PDF Tools to select pages/files and create one combined PDF.",
                color=VaultMuted,style=MaterialTheme.typography.bodyMedium)
            Text("3. Use Full Details to save and copy selected OCR information.",
                color=VaultMuted,style=MaterialTheme.typography.bodyMedium)
        }
    }
}
