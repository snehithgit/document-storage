package com.snehith.documentvault

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable fun HubCard(modifier:Modifier=Modifier,onClick:(()->Unit)?=null,content:@Composable ColumnScope.()->Unit){
    val colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)
    val outline=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)
    if(onClick==null){
        Card(modifier=modifier,shape=RoundedCornerShape(14.dp),colors=colors,
            border=outline,elevation=CardDefaults.cardElevation(defaultElevation=0.dp)){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
        }
    }else{
        Card(onClick=onClick,modifier=modifier,shape=RoundedCornerShape(14.dp),
            colors=colors,border=outline,
            elevation=CardDefaults.cardElevation(defaultElevation=0.dp)){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
        }
    }
}
@Composable fun HubIcon(icon:ImageVector,tint:Color=MaterialTheme.colorScheme.secondary,modifier:Modifier=Modifier){
    Surface(modifier=modifier.size(40.dp),shape=RoundedCornerShape(10.dp),color=tint.copy(alpha=.11f)){
        Box(contentAlignment=Alignment.Center){Icon(icon,null,tint=tint,modifier=Modifier.size(21.dp))}
    }
}
@Composable fun HubStatus(card:VaultCard) {
    val(status,color)=vaultValidity(card)
    val actual=when(status) {
        "Expired"->VaultColors.expired
        "Expiring","Check expiry"->VaultColors.expiring
        "Valid"->VaultColors.valid
        else->MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape=RoundedCornerShape(7.dp),color=actual.copy(alpha=.12f)){
        Text(status,color=actual,style=MaterialTheme.typography.labelSmall,
            modifier=Modifier.padding(horizontal=11.dp,vertical=6.dp))
    }
}
private fun categoryIcon(category:String):ImageVector=when {
    category.contains("identity",true)||category.contains("personal",true)->Icons.Outlined.Badge
    category.contains("medical",true)||category.contains("health",true)->Icons.Outlined.FavoriteBorder
    category.contains("education",true)||category.contains("certificate",true)->Icons.Outlined.School
    category.contains("travel",true)||category.contains("passport",true)->Icons.Outlined.Public
    else->Icons.Outlined.FolderOpen
}
@Composable fun HubHome(cards:List<VaultCard>,repo:VaultRepository,profiles:List<VaultProfile>,profile:String,
    onProfile:(String)->Unit,onOpen:(VaultCard)->Unit,onSearch:()->Unit,onScan:()->Unit,
    onImport:()->Unit,onNew:()->Unit,onPdf:()->Unit,onExpiries:()->Unit,
    onCategory:(String)->Unit){
    val shown=remember(cards,profile){cards.filter{profile=="all"||it.profile==profile}}
    val expiring=shown.count{vaultValidity(it).first in listOf("Expired","Expiring","Check expiry")}
    val totalBytes=shown.sumOf{c->c.files.sumOf{it.bytes}}
    val categories=shown.groupBy{it.category.ifBlank{"Other"}}.entries.sortedByDescending{it.value.size}
    val recent=shown.sortedByDescending{it.created}.take(7)
    LazyColumn(modifier=Modifier.fillMaxSize().background(vaultWarmBrush(MaterialTheme.colorScheme.background==VaultColors.darkBg)),
        contentPadding=PaddingValues(start=16.dp,end=16.dp,top=10.dp,bottom=110.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){
                    Text("DOCUMENT / WORKSPACE",color=VaultColors.syntax,
                        style=MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(5.dp))
                    Text("Your vault.",fontWeight=FontWeight.SemiBold,
                        style=MaterialTheme.typography.headlineMedium)
                }
                Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface,
                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){
                    Box(Modifier.size(46.dp),contentAlignment=Alignment.Center){
                        Text("</>",color=VaultColors.syntax,fontFamily=androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight=FontWeight.Bold)
                    }
                }
            }
        }
        item {
            HubCard(Modifier.fillMaxWidth()){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("vault.stats",fontWeight=FontWeight.SemiBold,
                            style=MaterialTheme.typography.titleLarge)
                        Text("// Private · available offline",color=MaterialTheme.colorScheme.onSurfaceVariant,
                            style=MaterialTheme.typography.bodySmall)
                    }
                    HubIcon(Icons.Outlined.Inventory2)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
                    HubMetric(VaultCounts.format(shown.size,"card"),"Saved",Modifier.weight(1f))
                    HubMetric(VaultCounts.format(shown.sumOf{it.files.size},"file"),"Documents",Modifier.weight(1f))
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outline)
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text("warnings",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
                    Text(expiring.toString(),color=VaultColors.expiring,fontWeight=FontWeight.SemiBold)
                }
                LinearProgressIndicator(progress={if(shown.isEmpty())0f else (expiring.toFloat()/shown.size).coerceIn(0f,1f)},
                    modifier=Modifier.fillMaxWidth().height(3.dp),color=VaultColors.syntax,
                    trackColor=MaterialTheme.colorScheme.primary.copy(alpha=.10f))
                Text("disk.local  /  "+readableBytes(totalBytes),color=MaterialTheme.colorScheme.onSurfaceVariant,
                    style=MaterialTheme.typography.bodySmall)
            }
        }
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Directories",style=MaterialTheme.typography.titleLarge,
                    fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                TextButton(onClick=onSearch){Text("Search all",color=MaterialTheme.colorScheme.secondary)}
            }
            Spacer(Modifier.height(8.dp))
            if(categories.isEmpty())HubCard(Modifier.fillMaxWidth()){
                Text("// Empty vault. Open Scan to import or capture your first document.")
            }else{
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                    categories.chunked(2).forEach{row->
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                            row.forEach{(title,list)->
                                HubCard(Modifier.weight(1f),onClick={onCategory(title)}){
                                    HubIcon(categoryIcon(title))
                                    Text(title,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    Text(VaultCounts.format(list.size,"item")+"  /",color=MaterialTheme.colorScheme.onSurfaceVariant,
                                        style=MaterialTheme.typography.bodySmall)
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                                        HubIcon(Icons.Outlined.ArrowForward,
                                            MaterialTheme.colorScheme.secondary,Modifier.size(30.dp))
                                    }
                                }
                            }
                            if(row.size==1)Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(if(expiring>0)"Needs attention" else "Recent files",
                    modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold,
                    style=MaterialTheme.typography.titleLarge)
                if(expiring>0)TextButton(onClick=onExpiries){
                    Text("See all",color=MaterialTheme.colorScheme.secondary)
                }
            }
        }
        val shownList=if(expiring>0)shown.filter{vaultValidity(it).first in listOf("Expired","Expiring","Check expiry")}
            else recent
        items(shownList,key={it.id}){card->
            HubCard(Modifier.fillMaxWidth(),onClick={onOpen(card)}){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)){
                    HubIcon(categoryIcon(card.category))
                    Column(Modifier.weight(1f)){
                        Text(VaultNames.title(card.title),maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                        Text(VaultCounts.format(card.files.size,"file"),style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HubStatus(card)
                    Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
private fun readableBytes(bytes:Long):String=when{
    bytes>=1_073_741_824->"%.1f GB".format(java.util.Locale.US,bytes/1_073_741_824.0)
    bytes>=1_048_576->"%.1f MB".format(java.util.Locale.US,bytes/1_048_576.0)
    bytes>=1024->"%.0f KB".format(java.util.Locale.US,bytes/1024.0)
    else->"$bytes B"
}
@Composable private fun HubMetric(number:String,label:String,modifier:Modifier=Modifier){
    Column(modifier) {
        Text(number,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium)
        Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelSmall)
    }
}
@Composable fun HubBottomBar(active:String,onNavigate:(String)->Unit,onScan:()->Unit){
    Box(Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,bottom=8.dp,top=8.dp).height(66.dp)){
        Surface(
            modifier=Modifier.fillMaxWidth().padding(end=31.dp).height(64.dp),
            color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(16.dp),
            border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),shadowElevation=0.dp){
            Row(Modifier.fillMaxSize().padding(end=32.dp,start=6.dp),
                horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
                listOf(
                    Triple("home",Icons.Outlined.Home,"Home"),
                    Triple("cards",Icons.Outlined.GridView,"Cards"),
                    Triple("search",Icons.Outlined.Search,"Search"),
                    Triple("more",Icons.Outlined.Settings,"Settings")
                ).forEach{(id,icon,label)->
                    val selected=active==id
                    Surface(shape=RoundedCornerShape(10.dp),
                        color=if(selected)MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                        modifier=Modifier.weight(1f).clickable{onNavigate(id)}){
                        Column(Modifier.padding(vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally){
                            Icon(icon,label,tint=if(selected)MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(21.dp))
                            Text(label,style=MaterialTheme.typography.labelSmall,
                                color=if(selected)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.primary,shadowElevation=2.dp,
            modifier=Modifier.align(Alignment.CenterEnd).size(62.dp).clickable(onClick=onScan)){
            Box(contentAlignment=Alignment.Center){
                Icon(Icons.Outlined.Add,"Open Scan",tint=MaterialTheme.colorScheme.onPrimary,
                    modifier=Modifier.size(30.dp))
            }
        }
    }
}
