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
    // Stable size buckets avoid new decodes after every measurement or re-layout.
    val wanted=if(measuredPx>0)(measuredPx*1.25f).toInt() else maxEdge
    val targetEdge=listOf(160,240,360,540,800).firstOrNull{it>=wanted&&it<=maxEdge}
        ?:minOf(maxEdge,wanted).coerceAtLeast(160)
    var bitmap by remember(file?.id){mutableStateOf<Bitmap?>(null)}
    var loading by remember(file?.id,targetEdge){mutableStateOf(file!=null)}
    LaunchedEffect(file?.id,targetEdge) {
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
            if(image!=null)bitmap=image
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
            Text(VaultNames.title(card.title),Modifier.weight(1f),
                color=palette.ink,fontSize=13.sp,fontWeight=FontWeight.SemiBold,
                maxLines=1)
            val needsReview=card.fields.count{it.confidence>0f&&it.confidence<.75f}+
                (if(DesignLogic.hasHolderConflict(card))1 else 0)
            if(needsReview>0)Surface(shape=RoundedCornerShape(8.dp),
                color=palette.warningSoft){
                Text("$needsReview TO REVIEW",modifier=Modifier.padding(
                    horizontal=8.dp,vertical=5.dp),
                    color=palette.warning,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
            }else ExpiryTag(card)
        }
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
    val name=profiles.firstOrNull{it.id==selectedPerson}?.name?:if(selectedPerson=="all")"My vault" else "Me"
    var chooseProfile by remember{mutableStateOf(false)}
    if(chooseProfile)AlertDialog(onDismissRequest={chooseProfile=false},
        title={Text("Show cards for")},
        text={Column{
            (listOf(VaultProfile("all","Everyone"))+profiles).forEach{person->
                TextButton(onClick={chooseProfile=false;onPerson(person.id)},
                    modifier=Modifier.fillMaxWidth()){
                    Text(person.name,modifier=Modifier.weight(1f))
                    if(selectedPerson==person.id)Icon(Icons.Outlined.Check,null)
                }
            }
        }},
        confirmButton={TextButton(onClick={chooseProfile=false}){Text("Close")}})
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
                Text("${personCards.size} documents · ${personCards.sumOf{it.files.size}} attachments",
                    color=colors.inkMuted,fontSize=14.sp)
            }
            Box(Modifier.size(42.dp).background(colors.ink,
                androidx.compose.foundation.shape.CircleShape)
                .clickable(onClick={chooseProfile=true}),contentAlignment=Alignment.Center){
                Text(DesignLogic.initials(profiles.firstOrNull{
                    it.id==(if(selectedPerson=="all")"self" else selectedPerson)}?.name?:"Me"),color=colors.panel,
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
        Row(Modifier.horizontalScroll(rememberScrollState())
            .padding(horizontal=18.dp,vertical=6.dp),
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
@Composable fun VaultNavDetail(card:VaultCard,repo:VaultRepository,onFile:(VaultFile)->Unit,
    onShare:(VaultFile)->Unit,onSharePdf:(VaultFile)->Unit,onShareAll:()->Unit,onAdd:()->Unit,
    onFull:()->Unit,onTools:()->Unit,onEdit:()->Unit,onManage:()->Unit,onDelete:()->Unit,
    onFetchDetails:()->Unit,fetchingDetails:Boolean,onEnhance:(VaultFile)->Unit){
    var selectedId by rememberSaveable(card.id){mutableStateOf("")}
    var selectedIndex by rememberSaveable(card.id){mutableIntStateOf(0)}
    val selected=AttachmentSelection.select(card.files,selectedId,selectedIndex)
    val context=LocalContext.current
    var showMore by remember{mutableStateOf(false)}
    var confirmDelete by rememberSaveable(card.id){mutableStateOf(false)}
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},
        title={Text("Delete document?")},
        text={Text("The document and its unshared attachments will be removed. This cannot be undone.")},
        confirmButton={TextButton(onClick={confirmDelete=false;onDelete()}){Text("Delete",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(onClick={confirmDelete=false}){Text("Cancel")}})
    val palette=mockupPalette
    val reviewCount=card.fields.count{it.confidence>0f&&it.confidence<.75f}
    LazyColumn(Modifier.fillMaxSize().background(palette.panel),
        contentPadding=PaddingValues(horizontal=18.dp,vertical=16.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Eyebrow(card.category.ifBlank{"Personal"})
                    Text(VaultCounts.format(card.files.size,"attachment"),
                        color=palette.inkMuted,fontSize=13.sp)
                }
                Box{
                    IconButton(onClick={showMore=true},modifier=Modifier.size(48.dp)){
                        Icon(Icons.Outlined.MoreHoriz,"More document options",tint=palette.ink)
                    }
                    DropdownMenu(expanded=showMore,onDismissRequest={showMore=false}){
                        DropdownMenuItem(text={Text("Edit details")},onClick={showMore=false;onEdit()})
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
            TiltBox{
                CardFace(card,onClick={selected?.let(onFile)})
            }
        }
        item{ MockupExpiryRow(card) }
        if(DesignLogic.hasHolderConflict(card))item{
            MockupNote(Icons.Outlined.WarningAmber,
                "The saved Full Name matches a parent's name. Please check and correct it in Full details.",
                warning=true)
        }
        item{
            MockupActionRow(listOf(
                MockupAction(Icons.Outlined.Visibility,"View",primary=true,enabled=selected!=null,
                    onClick={selected?.let(onFile)}),
                MockupAction(Icons.Outlined.Share,"Share",enabled=selected!=null,
                    onClick={selected?.let(onShare)}),
                if(selected?.mime?.startsWith("image/")==true)
                    MockupAction(Icons.Outlined.PictureAsPdf,"Share PDF",
                        onClick={selected?.let{onSharePdf(it)}})
                else MockupAction(Icons.Outlined.PictureAsPdf,"PDF tools",onClick=onTools)
            ))
        }
        item{
            Button(onClick=onFetchDetails,
                enabled=!fetchingDetails && card.files.any{DetailFetchPlan.supported(it)},
                modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),
                shape=RoundedCornerShape(16.dp),
                colors=ButtonDefaults.buttonColors(containerColor=palette.accentSoft,
                    contentColor=palette.accent)){
                Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(21.dp))
                Spacer(Modifier.width(10.dp))
                Text(if(fetchingDetails)"Getting details…" else "GET DETAILS",
                    style=MaterialTheme.typography.titleMedium)
            }
        }
        item{
            Column{
                MockupSectionHeader("Attachments",VaultCounts.format(card.files.size,"attachment"))
                Spacer(Modifier.height(12.dp))
                MockupPagesStrip(card.files,repo,selected?.id,
                    onOpen={file->
                        selectedId=file.id
                        selectedIndex=card.files.indexOfFirst{it.id==file.id}
                        onFile(file)
                    },onAdd=onAdd)
            }
        }
        item{
            Column{
                MockupSectionHeader("Quick copy","All ${card.fields.size}",onFull)
                Spacer(Modifier.height(12.dp))
                MockupQuickCopy(card.fields.take(3),
                    onCopy={field->copyToClipboard(context,field.name,field.value)})
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick=onFull,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),
                    shape=RoundedCornerShape(16.dp),
                    border=BorderStroke(1.dp,palette.line),
                    colors=ButtonDefaults.outlinedButtonColors(contentColor=palette.ink)){
                    Icon(Icons.Outlined.Edit,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Full details"+if(reviewCount>0)" · $reviewCount to review" else "")
                }
            }
        }
    }
}
