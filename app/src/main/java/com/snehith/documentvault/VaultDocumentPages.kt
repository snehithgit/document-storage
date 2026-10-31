package com.snehith.documentvault

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Real page references: an imported PDF is ONE attachment with MANY viewable pages. */
data class VaultDocumentPage(val file:VaultFile,val index:Int,val total:Int,
    val aspect:Float=.75f)

object VaultPageLayout {
    fun kind(aspect:Float):String=when{
        aspect>=1.45f->"ID card / landscape"
        aspect>=1.16f->"Landscape page"
        aspect>=.58f->"Portrait page"
        else->"Long document"
    }
    fun widthFraction(aspect:Float):Float=when{
        aspect>=1.16f->.95f
        aspect>=.62f->.72f
        else->.6f
    }
}


object VaultDocumentPages {
    fun expand(repo:VaultRepository,files:List<VaultFile>):List<VaultDocumentPage> =
        files.flatMap{file->
            if(file.mime=="application/pdf"||file.name.endsWith(".pdf",true)){
                runCatching {
                    PdfPageSession(repo.documentFile(file)).use{session->
                        val count=session.pageCount.coerceIn(1,5000)
                        (0 until count).map{index->
                            VaultDocumentPage(file,index,count,session.pageAspect(index))
                        }
                    }
                }.getOrElse{listOf(VaultDocumentPage(file,0,1))}
            }else{
                val aspect=runCatching{
                    val original=repo.documentFile(file)
                    val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
                    android.graphics.BitmapFactory.decodeFile(original.absolutePath,bounds)
                    val initial=bounds.outWidth.toFloat()/bounds.outHeight.coerceAtLeast(1)
                    val orientation=androidx.exifinterface.media.ExifInterface(original)
                        .getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
                    val sideways=orientation in listOf(5,6,7,8)
                    (if(sideways)1f/initial else initial).coerceIn(.38f,2.2f)
                }.getOrDefault(.75f)
                listOf(VaultDocumentPage(file,0,1,aspect))
            }
        }
    fun identity(files:List<VaultFile>)=files.joinToString(";"){it.id+":"+it.sha256+":"+it.bytes}
    /** Swiping past the end of one attachment continues into the next one: forward opens
     * its first page, backward the previous attachment's last page. Null at either end. */
    fun neighbour(pages:List<VaultDocumentPage>,fileId:String,delta:Int):Pair<String,Int>? {
        val files=pages.map{it.file.id}.distinct()
        val target=files.getOrNull(files.indexOf(fileId).takeIf{it>=0}?.plus(delta)?:return null)?:return null
        val targetPages=pages.filter{it.file.id==target}
        return target to (if(delta>0)0 else (targetPages.maxOfOrNull{it.index}?:0))
    }
}

@Composable fun rememberVaultDocumentPages(repo:VaultRepository,files:List<VaultFile>):List<VaultDocumentPage>{
    val identity=VaultDocumentPages.identity(files)
    var pages by remember(repo,identity){
        mutableStateOf(files.map{VaultDocumentPage(it,0,1)})
    }
    LaunchedEffect(repo,identity){
        pages=withContext(Dispatchers.IO){VaultDocumentPages.expand(repo,files)}
    }
    return pages
}

@Composable fun rememberVaultCategoryPageCounts(
    repo:VaultRepository,cards:List<VaultCard>
):List<Pair<String,Int>>{
    val signature=cards.joinToString("|"){it.id+":"+it.category+":"+VaultDocumentPages.identity(it.files)}
    var results by remember(repo,signature){
        mutableStateOf(cards.groupBy{it.category.ifBlank{"Other"}}
            .map{(category,group)->category to group.sumOf{it.files.size}}
            .sortedByDescending{it.second})
    }
    LaunchedEffect(repo,signature){
        results=withContext(Dispatchers.IO){
            cards.groupBy{it.category.ifBlank{"Other"}}
                .map{(category,group)->
                    category to group.sumOf{VaultDocumentPages.expand(repo,it.files).size}
                }.sortedByDescending{it.second}
        }
    }
    return results
}

@Composable fun rememberVaultPageTotal(repo:VaultRepository,cards:List<VaultCard>):Int{
    val signature=cards.joinToString("|"){it.id+":"+VaultDocumentPages.identity(it.files)}
    var total by remember(repo,signature){mutableIntStateOf(cards.sumOf{it.files.size})}
    LaunchedEffect(repo,signature){
        total=withContext(Dispatchers.IO){
            cards.sumOf{VaultDocumentPages.expand(repo,it.files).size}
        }
    }
    return total
}

/** Page-first paper design from HTML v3, using actual PDF and image bytes. */
@Composable fun VaultDocumentPaper(
    page:VaultDocumentPage?,repo:VaultRepository,modifier:Modifier=Modifier,
    category:String="",numberOfPages:Int=1,maxEdge:Int=520,onClick:(()->Unit)?=null
){
    val p=mockupPalette
    val shape=RoundedCornerShape(14.dp)
    Box(modifier.aspectRatio(page?.aspect?:.75f).clip(shape)
        .background(p.panelRaised)
        .border(1.dp,p.line,shape)
        .then(if(onClick!=null)Modifier.clickable(onClick=onClick) else Modifier)){
        if(page!=null){
            VaultThumbnail(page.file,repo,Modifier.fillMaxSize(),
                maxEdge=maxEdge,pageIndex=page.index)
        }
        if(category.isNotBlank()){
            Surface(Modifier.align(Alignment.TopStart).padding(7.dp),
                color=p.dock.copy(alpha=.91f),shape=RoundedCornerShape(6.dp)){
                Text(category.uppercase(),modifier=Modifier.padding(horizontal=7.dp,vertical=4.dp),
                    color=p.dockInk,fontSize=9.sp,fontWeight=FontWeight.SemiBold,
                    maxLines=1)
            }
        }
        if(numberOfPages>1){
            Surface(Modifier.align(Alignment.BottomEnd).padding(7.dp),
                color=p.dock.copy(alpha=.91f),shape=RoundedCornerShape(6.dp)){
                Row(Modifier.padding(horizontal=7.dp,vertical=4.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    Icon(Icons.Outlined.Layers,null,Modifier.size(11.dp),tint=p.dockInk)
                    Text(numberOfPages.toString(),fontSize=10.sp,color=p.dockInk)
                }
            }
        }
    }
}

@Composable fun VaultDocumentTile(card:VaultCard,repo:VaultRepository,
    modifier:Modifier=Modifier,uniform:Boolean=false,privatePreview:Boolean=false,onClick:()->Unit
){
    val p=mockupPalette
    val pages=rememberVaultDocumentPages(repo,card.files)
    Column(modifier.clickable(onClick=onClick)){
        val concealed=privatePreview&&card.category.equals("Identity",true)
        Box(Modifier.fillMaxWidth().then(if(uniform)Modifier.height(184.dp) else Modifier),
            contentAlignment=Alignment.Center){
            VaultDocumentPaper(pages.firstOrNull(),repo,Modifier.fillMaxWidth()
                .then(if(concealed&&android.os.Build.VERSION.SDK_INT>=31)Modifier.blur(14.dp) else Modifier),
                category=card.category,numberOfPages=pages.size,onClick=onClick)
            if(concealed)Column(Modifier.matchParentSize()
                .background(p.panelRaised.copy(alpha=if(android.os.Build.VERSION.SDK_INT>=31).5f else .96f)),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                Icon(Icons.Outlined.VisibilityOff,"Private preview",tint=p.inkMuted)
                Text("Tap to view",fontSize=11.sp,color=p.inkMuted)
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(VaultNames.title(card.title),fontSize=13.sp,fontWeight=FontWeight.SemiBold,
            color=p.ink,maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(VaultCounts.format(pages.size,"page"),fontSize=11.sp,color=p.inkFaint,
            maxLines=1)
        Spacer(Modifier.height(4.dp))
        if(card.expiry.isNotBlank())ExpiryTag(card)
        else if(uniform)Spacer(Modifier.height(28.dp))
    }
}

@Composable fun VaultDocumentPageStrip(
    pages:List<VaultDocumentPage>,repo:VaultRepository,
    selectedIndex:Int,onSelect:(Int)->Unit,onAdd:()->Unit
){
    val p=mockupPalette
    LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp),
        contentPadding=PaddingValues(end=18.dp)){
        itemsIndexed(pages,key={index,page->page.file.id+":"+page.index}){index,page->
            val chosen=index==selectedIndex
            Box(Modifier.width(78.dp).clip(RoundedCornerShape(12.dp))
                .border(if(chosen)2.dp else 1.dp,
                    if(chosen)p.accent else p.line,RoundedCornerShape(12.dp))){
                VaultDocumentPaper(page,repo,Modifier.fillMaxWidth(),maxEdge=220,
                    onClick={onSelect(index)})
                Surface(Modifier.align(Alignment.BottomStart).padding(5.dp),
                    color=p.dock.copy(alpha=.85f),shape=RoundedCornerShape(5.dp)){
                    Text((index+1).toString(),
                        Modifier.padding(horizontal=6.dp,vertical=2.dp),
                        fontSize=10.sp,color=p.dockInk)
                }
            }
        }
        item{
            Box(Modifier.width(78.dp).aspectRatio(.75f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp,p.line,RoundedCornerShape(12.dp))
                .clickable(onClick=onAdd),contentAlignment=Alignment.Center){
                Icon(Icons.Outlined.Add,"Add a page",tint=p.accent,
                    modifier=Modifier.size(24.dp))
            }
        }
    }
}
