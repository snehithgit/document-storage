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
    val attention=shown.filter{vaultValidity(it).first in listOf("Expired","Expiring","Check expiry")}
    val recent=shown.sortedByDescending{it.created}.take(7)
    val categories=shown.groupBy{it.category.ifBlank{"Other"}}.entries.sortedByDescending{it.value.size}
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding=PaddingValues(start=16.dp,end=16.dp,top=18.dp,bottom=24.dp),
        verticalArrangement=Arrangement.spacedBy(20.dp)){
        item{
            Text("My Documents",style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text("Everything important, in one place.",color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.bodyMedium)
        }
        item{
            HubCard(Modifier.fillMaxWidth()){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    HubMetric(shown.size.toString(),"Documents",Modifier.weight(1f))
                    HubMetric(shown.sumOf{it.files.size}.toString(),"Files",Modifier.weight(1f))
                    HubMetric(attention.size.toString(),"Needs attention",Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick=onScan,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),
                shape=RoundedCornerShape(14.dp)){
                Icon(Icons.Outlined.Add,null,modifier=Modifier.size(23.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Document",fontWeight=FontWeight.SemiBold)
            }
        }
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Categories",style=MaterialTheme.typography.titleLarge,
                    modifier=Modifier.weight(1f))
                TextButton(onClick=onSearch){Text("Search")}
            }
            Spacer(Modifier.height(10.dp))
            if(categories.isEmpty())HubCard(Modifier.fillMaxWidth()){
                Text("Your documents will appear here once you add them.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                    categories.chunked(2).forEach{pair->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                            pair.forEach{entry->
                                HubCard(Modifier.weight(1f),onClick={onCategory(entry.key)}){
                                    HubIcon(categoryIcon(entry.key))
                                    Text(entry.key,fontWeight=FontWeight.SemiBold,
                                        maxLines=2,overflow=TextOverflow.Ellipsis)
                                    Text(VaultCounts.format(entry.value.size,"document"),
                                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                                        style=MaterialTheme.typography.bodySmall)
                                }
                            }
                            if(pair.size==1)Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if(attention.isNotEmpty()){
            item{
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text("Needs Attention",style=MaterialTheme.typography.titleLarge,
                        modifier=Modifier.weight(1f))
                    TextButton(onClick=onExpiries){Text("View all")}
                }
            }
            items(attention.take(4),key={it.id}){card->
                VaultRow(card,repo){onOpen(card)}
            }
        }
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Recent Documents",style=MaterialTheme.typography.titleLarge,
                    modifier=Modifier.weight(1f))
                TextButton(onClick={onCategory("All")}){Text("See all")}
            }
        }
        if(recent.isEmpty())item{
            Text("No documents yet. Use Add Document to scan or import one.",
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(recent,key={it.id}){card->VaultRow(card,repo){onOpen(card)}}
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
    Surface(color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(76.dp)
            .padding(horizontal=6.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.SpaceEvenly){
            @Composable fun tab(id:String,label:String,icon:ImageVector,modifier:Modifier=Modifier){
                val selected=active==id
                Column(modifier.weight(1f).fillMaxHeight()
                    .clickable{onNavigate(id)}
                    .padding(vertical=8.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.Center){
                    Icon(icon,label,modifier=Modifier.size(22.dp),
                        tint=if(selected)VaultColors.syntax else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(5.dp))
                    Text(label,style=MaterialTheme.typography.labelSmall,maxLines=1,
                        color=if(selected)VaultColors.syntax else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            tab("home","Home",Icons.Outlined.Home)
            tab("cards","Documents",Icons.Outlined.FolderOpen)
            Column(Modifier.weight(1f).fillMaxHeight().clickable(onClick=onScan),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.Center){
                Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primary,
                    modifier=Modifier.size(52.dp)){
                    Box(contentAlignment=Alignment.Center){
                        Icon(Icons.Outlined.Add,"Add Document",Modifier.size(28.dp),
                            tint=MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text("Scan",style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurface)
            }
            tab("search","Search",Icons.Outlined.Search)
            tab("more","Settings",Icons.Outlined.Settings)
        }
    }
}
