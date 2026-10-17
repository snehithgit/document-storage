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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Real page references: an imported PDF is ONE attachment with MANY viewable pages. */
data class VaultDocumentPage(val file:VaultFile,val index:Int,val total:Int)

object VaultDocumentPages {
    fun expand(repo:VaultRepository,files:List<VaultFile>):List<VaultDocumentPage> =
        files.flatMap{file->
            if(file.mime=="application/pdf"||file.name.endsWith(".pdf",true)){
                val pages=runCatching{
                    PdfPageSession(repo.documentFile(file)).use{it.pageCount}
                }.getOrDefault(1).coerceIn(1,5000)
                (0 until pages).map{VaultDocumentPage(file,it,pages)}
            }else listOf(VaultDocumentPage(file,0,1))
        }
    fun identity(files:List<VaultFile>)=files.joinToString(";"){it.id+":"+it.sha256+":"+it.bytes}
}

@Composable fun rememberVaultDocumentPages(repo:VaultRepository,files:List<VaultFile>):List<VaultDocumentPage>{
    val identity=VaultDocumentPages.identity(files)
    val pages by produceState(
        initialValue=files.map{VaultDocumentPage(it,0,1)},
        key1=repo,key2=identity
    ){
        value=withContext(Dispatchers.IO){VaultDocumentPages.expand(repo,files)}
    }
    return pages
}

@Composable fun rememberVaultPageTotal(repo:VaultRepository,cards:List<VaultCard>):Int{
    val signature=cards.joinToString("|"){it.id+":"+VaultDocumentPages.identity(it.files)}
    val total by produceState(initialValue=cards.sumOf{it.files.size},key1=repo,key2=signature){
        value=withContext(Dispatchers.IO){
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
    Box(modifier.aspectRatio(.75f).clip(shape)
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
    modifier:Modifier=Modifier,onClick:()->Unit
){
    val p=mockupPalette
    val pages=rememberVaultDocumentPages(repo,card.files)
    Column(modifier.clickable(onClick=onClick)){
        VaultDocumentPaper(pages.firstOrNull(),repo,Modifier.fillMaxWidth(),
            category=card.category,numberOfPages=pages.size,onClick=onClick)
        Spacer(Modifier.height(9.dp))
        Text(VaultNames.title(card.title),fontSize=13.sp,fontWeight=FontWeight.SemiBold,
            color=p.ink,maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(VaultCounts.format(pages.size,"page"),fontSize=11.sp,color=p.inkFaint,
            maxLines=1)
        Spacer(Modifier.height(4.dp))
        ExpiryTag(card)
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
