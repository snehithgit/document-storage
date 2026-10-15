package com.snehith.documentvault

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.time.LocalDate
import java.time.temporal.ChronoUnit

val VaultNavy=Color(0xFF06182D)
val VaultPanel=Color(0xFF103252)
val VaultRaised=Color(0xFF09243D)
val VaultBlue=Color(0xFF217FFF)
val VaultSky=Color(0xFF91C3FF)
val VaultMuted=Color(0xFF9EBBD9)
private val bgBrush=Brush.verticalGradient(listOf(Color(0xFF0A3159),VaultNavy))
private val rounded=RoundedCornerShape(15.dp)

@Composable fun VaultHeader(title:String,subtitle:String?=null) {
    Column {
        Text(title,color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.headlineSmall)
        if(subtitle!=null)Text(subtitle,color=VaultMuted,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable fun VaultThumbnail(file:VaultFile?,repo:VaultRepository,modifier:Modifier=Modifier) {
    var bitmap by remember(file?.id){mutableStateOf<Bitmap?>(null)}
    LaunchedEffect(file?.id){
        bitmap=null
        if(file!=null)try {
            bitmap=withContext(Dispatchers.IO){
                val raw=repo.documentFile(file)
                if(file.mime=="application/pdf"||file.name.endsWith(".pdf",true)){
                    PdfPageSession(raw).use{it.render(0,340)}
                }else if(file.mime.startsWith("image/")){
                    val dims=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
                    android.graphics.BitmapFactory.decodeFile(raw.absolutePath,dims)
                    var sample=1
                    while(maxOf(dims.outWidth,dims.outHeight)/sample>440)sample*=2
                    android.graphics.BitmapFactory.decodeFile(raw.absolutePath,
                        android.graphics.BitmapFactory.Options().apply{inSampleSize=sample})
                }else null
            }
        }catch(_:Exception){}
    }
    Box(modifier.background(Color(0xFF244969),rounded),contentAlignment=Alignment.Center){
        if(bitmap!=null)Image(bitmap!!.asImageBitmap(),"Document",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Icon(Icons.Outlined.Description,null,tint=VaultSky,modifier=Modifier.size(37.dp))
    }
}
fun vaultValidity(card:VaultCard):Pair<String,Color> {
    val days=try{ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(card.expiry))}catch(_:Exception){null}
    return when {
        days==null->"Saved" to VaultSky
        days<0->"Expired" to Color(0xFFFF7E85)
        days<=30->"Expiring" to Color(0xFFFFC26A)
        else->"Valid" to Color(0xFF53DDA8)
    }
}
@Composable fun VaultPill(text:String,color:Color=VaultBlue){
    Surface(color=color.copy(alpha=.18f),shape=RoundedCornerShape(8.dp)){
        Text(text,color=if(color==VaultBlue)VaultSky else color,style=MaterialTheme.typography.labelSmall,
            modifier=Modifier.padding(horizontal=8.dp,vertical=4.dp),maxLines=1)
    }
}
@Composable fun VaultTile(card:VaultCard,repo:VaultRepository,onClick:()->Unit){
    Card(onClick=onClick,colors=CardDefaults.cardColors(containerColor=Color(0xFFF1F6FF)),shape=rounded){
        Column(Modifier.fillMaxWidth().padding(9.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
            Box {
                VaultThumbnail(card.files.firstOrNull(),repo,Modifier.fillMaxWidth().height(140.dp))
                Box(Modifier.align(Alignment.TopEnd).padding(5.dp)){VaultPill(card.kind.take(12))}
            }
            Text(card.title,color=Color(0xFF102741),fontWeight=FontWeight.Bold,
                style=MaterialTheme.typography.bodyMedium,maxLines=2,minLines=2,overflow=TextOverflow.Ellipsis)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                val(v,c)=vaultValidity(card)
                VaultPill(v,c)
                Text("${card.files.size} files",color=Color(0xFF60738A),style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}
@Composable fun VaultRow(card:VaultCard,repo:VaultRepository,onClick:()->Unit){
    val(v,c)=vaultValidity(card)
    Card(onClick=onClick,colors=CardDefaults.cardColors(containerColor=VaultPanel),shape=rounded){
        Row(Modifier.padding(9.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            VaultThumbnail(card.files.firstOrNull(),repo,Modifier.size(65.dp,72.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)){
                Text(card.title,color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(card.kind+" · "+card.category,color=VaultMuted,style=MaterialTheme.typography.labelSmall)
                Text("${card.files.size} attachments",color=VaultSky,style=MaterialTheme.typography.labelSmall)
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
            Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF15569B),Color(0xFF0D2E53))),rounded).padding(17.dp)){
                Text("MY DOCUMENT SPACE",color=VaultSky,style=MaterialTheme.typography.labelSmall)
                Text("Your essentials, organized.",color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
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
                VaultMetric(visible.count{vaultValidity(it).first in listOf("Expired","Expiring")}.toString(),
                    "Expiring",Modifier.weight(1f),onExpiries)
            }
        }
        item {
            Text("Quick actions",color=Color.White,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                VaultQuick("Scan",Icons.Outlined.DocumentScanner,Modifier.weight(1f),onScan)
                VaultQuick("Import",Icons.Outlined.FileUpload,Modifier.weight(1f),onImport)
                VaultQuick("New Card",Icons.Outlined.AddCard,Modifier.weight(1f),onNew)
                VaultQuick("PDF",Icons.Outlined.PictureAsPdf,Modifier.weight(1f),onPdf)
            }
        }
        item {VaultHeader("Recent documents",visible.size.toString()+" cards in your vault")}
        items(visible.sortedByDescending{it.created}.take(10),key={it.id}){c->VaultRow(c,repo){onOpen(c)}}
        if(visible.isEmpty())item{
            Text("Import a document or scan a new one to get started.",color=VaultMuted)
        }
    }
}
@Composable fun VaultMetric(n:String,label:String,modifier:Modifier,onClick:()->Unit={}){
    Card(onClick=onClick,modifier=modifier,colors=CardDefaults.cardColors(containerColor=VaultPanel),shape=rounded){
        Column(Modifier.padding(11.dp)){
            Text(n,color=VaultSky,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.headlineSmall)
            Text(label,color=VaultMuted,style=MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable fun VaultQuick(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier,onClick:()->Unit){
    Card(onClick=onClick,modifier=modifier,colors=CardDefaults.cardColors(containerColor=VaultBlue),shape=RoundedCornerShape(12.dp)){
        Column(Modifier.padding(vertical=12.dp,horizontal=2.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Icon(icon,null,tint=Color.White)
            Text(label,color=Color.White,style=MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable fun VaultNavCards(cards:List<VaultCard>,repo:VaultRepository,onOpen:(VaultCard)->Unit,onNew:()->Unit){
    var filter by remember{mutableStateOf("All")}
    Column(Modifier.fillMaxSize().background(bgBrush)){
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){VaultHeader("Document cards","${cards.size} saved")}
            IconButton(onClick=onNew){Icon(Icons.Outlined.AddCircle,null,tint=VaultSky)}
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=15.dp),
            horizontalArrangement=Arrangement.spacedBy(7.dp)){
            (listOf("All")+cards.map{it.kind}.distinct()).forEach{t->
                FilterChip(selected=t==filter,onClick={filter=t},label={Text(t)})
            }
        }
        LazyVerticalGrid(columns=GridCells.Fixed(2),
            contentPadding=PaddingValues(15.dp,10.dp,15.dp,95.dp),
            verticalArrangement=Arrangement.spacedBy(11.dp),horizontalArrangement=Arrangement.spacedBy(11.dp)){
            items(cards.filter{filter=="All"||it.kind==filter},key={it.id}){c->VaultTile(c,repo){onOpen(c)}}
        }
    }
}
@Composable fun VaultNavSearch(cards:List<VaultCard>,repo:VaultRepository,onOpen:(VaultCard)->Unit){
    var query by remember{mutableStateOf("")}
    var mode by remember{mutableStateOf("All")}
    val filtered=cards.filter{card->
        val text=card.title+" "+card.category+" "+card.kind+" "+
            card.fields.joinToString(" "){it.name+" "+it.value}+" "+
            card.files.joinToString(" "){it.name+" "+it.extractedText}
        (query.isBlank()||text.contains(query,true))&&
        (mode=="All"||(mode=="Fields"&&card.fields.any{it.value.contains(query,true)})||
            (mode=="OCR"&&card.files.any{it.extractedText.contains(query,true)}))
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
        Text("${filtered.size} matching records",color=VaultMuted,modifier=Modifier.padding(vertical=12.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=85.dp)){
            items(filtered,key={it.id}){c->
                VaultRow(c,repo){onOpen(c)}
                if(query.isNotBlank()) {
                    val snippet=c.fields.firstOrNull{it.value.contains(query,true)}?.let{"${it.name}: ${it.value}"}
                        ?:c.files.firstNotNullOfOrNull{f->
                            val p=f.extractedText.indexOf(query,true)
                            if(p<0)null else f.extractedText.substring((p-25).coerceAtLeast(0),
                                (p+query.length+55).coerceAtMost(f.extractedText.length))
                        }
                    if(snippet!=null)Text(snippet,color=VaultSky,style=MaterialTheme.typography.bodySmall,
                        modifier=Modifier.padding(horizontal=8.dp))
                }
            }
        }
    }
}
@Composable fun VaultNavDetail(card:VaultCard,repo:VaultRepository,onFile:(VaultFile)->Unit,onOcr:(VaultFile)->Unit,
    onShare:(VaultFile)->Unit,onShareAll:()->Unit,onAdd:()->Unit,
    onFull:()->Unit,onTools:()->Unit,onEdit:()->Unit,onDelete:()->Unit){
    var selected by remember(card.id){mutableIntStateOf(0)}
    val context=LocalContext.current
    val(status,color)=vaultValidity(card)
    var confirm by remember{mutableStateOf(false)}
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
                Card(onClick=onAdd,colors=CardDefaults.cardColors(containerColor=VaultPanel)){
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
                Column(Modifier.weight(1f)){VaultHeader(card.title,"${card.kind} · ${card.category}")}
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
            Column(Modifier.fillMaxWidth().background(VaultPanel,rounded).padding(12.dp)){
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Quick Copy",color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    TextButton(onClick=onFull){Text("All fields →")}
                }
                card.fields.take(4).forEach{f->
                    HorizontalDivider(color=Color(0xFF284A6D))
                    Row(Modifier.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text(f.name,color=VaultMuted,style=MaterialTheme.typography.labelSmall)
                            Text(f.value.ifBlank{"—"},color=Color.White)
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
            OutlinedButton(onClick=onAdd,modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Outlined.Add,null);Text(" Add pages / documents")
            }
            OutlinedButton(onClick=onShareAll,enabled=card.files.isNotEmpty(),modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Outlined.Share,null);Text(" Share all attachments")
            }
            TextButton(onClick={confirm=true}){Text("Delete card",color=Color(0xFFFF8E95))}
        }
    }
}
