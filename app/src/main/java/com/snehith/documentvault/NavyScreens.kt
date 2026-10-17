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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
/** Byte-bounded LRU sized to 1/8 of the device's Android memoryClass. */
private object VaultThumbnailCache {
    @Volatile private var shared:LruCache<String,Bitmap>?=null
    fun get(context:android.content.Context):LruCache<String,Bitmap> =
        shared?:synchronized(this){
            shared?:run {
                val manager=context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
                    as android.app.ActivityManager
                val size=(manager.memoryClass.toLong()*1024*1024/8)
                    .coerceIn(4L*1024*1024,128L*1024*1024).toInt()
                object:LruCache<String,Bitmap>(size){
                    override fun sizeOf(key:String,value:Bitmap):Int=value.byteCount
                }.also{shared=it}
            }
        }
}
@Composable fun VaultThumbnail(file:VaultFile?,repo:VaultRepository,modifier:Modifier=Modifier,maxEdge:Int=360) {
    val context=LocalContext.current
    val cache=remember(context){VaultThumbnailCache.get(context)}
    var measuredPx by remember(file?.id){mutableIntStateOf(0)}
    val targetEdge=if(measuredPx>0)minOf(maxEdge,
        (measuredPx*1.25f).toInt().coerceAtLeast(160)) else maxEdge
    var bitmap by remember(file?.id,targetEdge){mutableStateOf<Bitmap?>(null)}
    var loading by remember(file?.id,targetEdge){mutableStateOf(file!=null)}
    LaunchedEffect(file?.id,targetEdge) {
        bitmap=null
        loading=file!=null
        if(file==null)return@LaunchedEffect
        try {
            val image=withContext(Dispatchers.IO) {
                val raw=repo.documentFile(file)
                val cacheKey=file.id+"-"+targetEdge+"-"+raw.lastModified()
                synchronized(cache){cache.get(cacheKey)} ?: run {
                    val thumbnail=if(file.mime=="application/pdf"||file.name.endsWith(".pdf",true)){
                        PdfPageSession(raw).use{it.render(0,targetEdge)}
                    }else if(file.mime.startsWith("image/")){
                        ImageDecode.decode(raw,targetEdge,highQuality=targetEdge>800)
                    }else null
                    if(thumbnail!=null)synchronized(cache){cache.put(cacheKey,thumbnail)}
                    thumbnail
                }
            }
            bitmap=image
        }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
        catch(e:Exception){
            android.util.Log.w("VaultThumbnail","Cannot preview attachment",e)
        }finally{loading=false}
    }
    Box(modifier.onSizeChanged{measuredPx=maxOf(it.width,it.height)}
        .background(MaterialTheme.colorScheme.surfaceVariant,rounded),
        contentAlignment=Alignment.Center){
        if(bitmap!=null)Image(bitmap!!.asImageBitmap(),"Document",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
        else if(loading)CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)
        else Icon(Icons.Outlined.Description,"Preview unavailable",tint=VaultSky,modifier=Modifier.size(37.dp))
    }
}
fun vaultValidity(card:VaultCard,remindDays:Int=30):Pair<String,Color> {
    val status=VaultExpiryRules.status(card,remindDays)
    return status to when(status) {
        "Expired"->VaultColors.expired
        "Expiring","Check expiry"->VaultColors.expiring
        "Valid"->VaultColors.valid
        else->Color(0xFF64748B)
    }
}
@Composable fun VaultPill(text:String,color:Color=VaultBlue){
    Surface(color=color.copy(alpha=.12f),shape=RoundedCornerShape(50)){
        Text(text,color=if(color==VaultBlue)VaultSky else color,style=MaterialTheme.typography.labelSmall,
            modifier=Modifier.padding(horizontal=8.dp,vertical=4.dp),maxLines=1)
    }
}
/** Document Vault.html .grid2 .tile + .cap: real repo cards, mockup wallet face. */
@Composable fun VaultTile(card:VaultCard,repo:VaultRepository,personName:String="",onClick:()->Unit){
    val palette=mockupPalette
    Column(Modifier.fillMaxWidth().clickable(onClick=onClick)) {
        CardFace(card)
        Row(Modifier.fillMaxWidth().padding(top=9.dp),verticalAlignment=Alignment.CenterVertically){
            Text(VaultCounts.format(card.files.size,"page"),Modifier.weight(1f),
                color=palette.ink,fontSize=13.sp,fontWeight=FontWeight.SemiBold,
                maxLines=1)
            ExpiryTag(card)
        }
        if(personName.isNotBlank())Text(personName,color=palette.inkFaint,
            fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}
@Composable fun VaultRow(card:VaultCard,repo:VaultRepository,personName:String="",onClick:()->Unit){
    val context=androidx.compose.ui.platform.LocalContext.current
    val remind=context.getSharedPreferences("vault_options",android.content.Context.MODE_PRIVATE)
        .getInt("remindDays",30)
    val(v,c)=vaultValidity(card,remind)
    Card(onClick=onClick,colors=CardDefaults.cardColors(containerColor=VaultPanel),shape=rounded,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),
        elevation=CardDefaults.cardElevation(defaultElevation=0.dp)){
        Row(Modifier.padding(9.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            VaultThumbnail(card.files.firstOrNull(),repo,Modifier.size(65.dp,72.dp),maxEdge=280)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)){
                Text(VaultNames.title(card.title),color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(card.category.ifBlank{"Other"},color=VaultMuted,style=MaterialTheme.typography.labelSmall)
                Text(VaultCounts.format(card.files.size,"attachment")+
                    if(personName.isNotBlank())" · "+personName else "",
                    color=VaultColors.syntax,style=MaterialTheme.typography.labelSmall)
            }
            VaultPill(v,c)
        }
    }
}
/** Document Vault.html / VIEWS.library. Search opens the existing full OCR search. */
@Composable fun VaultNavCards(cards:List<VaultCard>,repo:VaultRepository,
    onOpen:(VaultCard)->Unit,onNew:()->Unit,selectedCategory:String="",onSearch:()->Unit={},
    profiles:List<VaultProfile> = emptyList(),selectedPerson:String="all",
    onPerson:(String)->Unit={},onCategoryConsumed:()->Unit={},
    onPeople:()->Unit={}){
    val colors=mockupPalette
    var filter by rememberSaveable{mutableStateOf("All")}
    LaunchedEffect(selectedCategory){
        if(selectedCategory.isNotBlank()){
            filter=selectedCategory
            onCategoryConsumed()
        }
    }
    val personCards=VaultPersonFilter.filter(cards,selectedPerson)
    val categories=listOf("All")+
        personCards.map{it.category.ifBlank{"Other"}}.distinct().sorted()
    val visible=DocumentBrowseLogic.browse(personCards,filter,"Recent")
    val name=profiles.firstOrNull{it.id==selectedPerson}?.name?:"My vault"
    Column(Modifier.fillMaxSize().background(colors.panel)){
        Row(Modifier.fillMaxWidth().padding(start=18.dp,end=18.dp,top=22.dp,bottom=18.dp),
            verticalAlignment=Alignment.Top){
            Column(Modifier.weight(1f)){
                Text(name.uppercase(),color=colors.inkFaint,
                    letterSpacing=1.3f.sp,fontSize=10.sp)
                Spacer(Modifier.height(12.dp))
                Text("Cards",fontWeight=FontWeight.Bold,
                    fontSize=32.sp,lineHeight=34.sp,color=colors.ink)
                Spacer(Modifier.height(8.dp))
                Text("${personCards.size} documents · ${personCards.sumOf{it.files.size}} pages",
                    color=colors.inkMuted,fontSize=14.sp)
            }
            Box(Modifier.size(42.dp).background(colors.ink,
                androidx.compose.foundation.shape.CircleShape)
                .clickable(onClick=onPeople),contentAlignment=Alignment.Center){
                Text(DesignLogic.initials(name),color=colors.panel,
                    fontSize=14.sp,fontWeight=FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp)
            .height(54.dp).background(colors.panelRaised,RoundedCornerShape(18.dp))
            .border(1.dp,colors.line,RoundedCornerShape(18.dp))
            .clickable(onClick=onSearch).padding(horizontal=16.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Icon(Icons.Outlined.Search,null,Modifier.size(20.dp),tint=colors.inkFaint)
            Text("Search names, numbers, text on pages",
                color=colors.inkFaint,fontSize=14.sp,maxLines=1,
                overflow=TextOverflow.Ellipsis)
        }
        if(profiles.size>1)Row(Modifier.horizontalScroll(rememberScrollState())
            .padding(horizontal=18.dp,vertical=12.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            (listOf(VaultProfile("all","Everyone"))+profiles).forEach{person->
                val picked=person.id==selectedPerson
                Surface(onClick={onPerson(person.id)},
                    color=if(picked)colors.ink else colors.panelRaised,
                    shape=androidx.compose.foundation.shape.CircleShape,
                    border=if(picked)null else BorderStroke(1.dp,colors.line)){
                    Text(person.name,Modifier.padding(horizontal=14.dp,vertical=9.dp),
                        color=if(picked)colors.panel else colors.inkMuted,
                        fontSize=13.sp)
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())
            .padding(horizontal=18.dp,vertical=16.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            categories.forEach{category->
                val chosen=category==filter
                val count=if(category=="All")personCards.size else
                    personCards.count{it.category.ifBlank{"Other"}==category}
                Surface(onClick={filter=category},
                    color=if(chosen)colors.ink else colors.panelRaised,
                    shape=androidx.compose.foundation.shape.CircleShape,
                    border=if(chosen)null else BorderStroke(1.dp,colors.line)){
                    Row(Modifier.padding(horizontal=14.dp,vertical=9.dp),
                        horizontalArrangement=Arrangement.spacedBy(7.dp),
                        verticalAlignment=Alignment.CenterVertically){
                        Text(category,color=if(chosen)colors.panel else colors.inkMuted,
                            fontWeight=FontWeight.SemiBold,fontSize=13.sp)
                        Text(count.toString(),
                            color=if(chosen)colors.panel.copy(alpha=.7f) else colors.inkFaint,
                            fontSize=11.sp)
                    }
                }
            }
        }
        if(visible.isEmpty()){
            Box(Modifier.fillMaxSize().padding(18.dp),contentAlignment=Alignment.Center){
                Text("No cards in this category.",color=colors.inkMuted,
                    fontSize=14.sp)
            }
        }else{
            BoxWithConstraints(Modifier.fillMaxSize()){
                val columns=if(maxWidth>=600.dp)3 else 2
                LazyVerticalGrid(columns=GridCells.Fixed(columns),
                    contentPadding=PaddingValues(start=18.dp,end=18.dp,top=0.dp,bottom=30.dp),
                    verticalArrangement=Arrangement.spacedBy(16.dp),
                    horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    items(visible,key={it.id}){card->
                        VaultTile(card,repo,profiles.firstOrNull{it.id==card.profile}?.name.orEmpty()){
                            onOpen(card)
                        }
                    }
                }
            }
        }
    }
}
private data class VaultSearchHit(val card:VaultCard,val snippet:String?)

@Composable fun VaultNavSearch(cards:List<VaultCard>,repo:VaultRepository,
    onOpen:(VaultCard)->Unit,profiles:List<VaultProfile> = emptyList(),
    selectedPerson:String="all",onPerson:(String)->Unit={}){
    var query by rememberSaveable{mutableStateOf("")}
    var mode by rememberSaveable{mutableStateOf("All")}
    var hits by remember(cards){mutableStateOf(cards.map{VaultSearchHit(it,null)})}
    var searching by remember{mutableStateOf(false)}
    LaunchedEffect(query,mode,cards,selectedPerson){
        searching=true
        delay(250)
        val snapshot=VaultPersonFilter.filter(cards,selectedPerson)
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
        if(profiles.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(selected=selectedPerson=="all",onClick={onPerson("all")},
                label={Text("Everyone")})
            profiles.forEach{profile->
                FilterChip(selected=selectedPerson==profile.id,onClick={onPerson(profile.id)},
                    label={Text(profile.name)})
            }
        }
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
                            Text(hit.card.category+" · "+
                                (profiles.firstOrNull{it.id==hit.card.profile}?.name?:"Me"),
                                color=VaultMuted,style=MaterialTheme.typography.bodySmall)
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
    var selectedIndex by rememberSaveable(card.id){mutableIntStateOf(0)}
    val selected=AttachmentSelection.select(card.files,selectedId,selectedIndex)
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
                    card.files.forEachIndexed{index,file->
                        Surface(
                            modifier=Modifier.size(64.dp,74.dp).clickable{
                                selectedId=file.id
                                selectedIndex=index
                            },
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
                    VaultThumbnail(file,repo,Modifier.size(58.dp,67.dp),maxEdge=280)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).clickable{onFile(file)}){
                        Text(VaultNames.label(file.name),style=MaterialTheme.typography.bodyMedium,
                            maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(when {
                            file.mime=="application/pdf"->"PDF"
                            file.mime.startsWith("image/")->"Image"
                            file.mime.contains("word")||file.mime.contains("document")->"Word document"
                            file.mime.startsWith("text/")->"Text file"
                            else->"File"
                        }+" · "+(file.bytes/1024).coerceAtLeast(1)+" KB",
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
