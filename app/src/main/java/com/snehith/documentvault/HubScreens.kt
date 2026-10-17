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
@Composable fun HubHome(cards:List<VaultCard>,repo:VaultRepository,
    profiles:List<VaultProfile>,profile:String,onProfile:(String)->Unit,
    onOpen:(VaultCard)->Unit,onSearch:()->Unit,onScan:()->Unit,
    onImport:()->Unit,onNew:()->Unit,onPdf:()->Unit,onExpiries:()->Unit,
    onCategory:(String)->Unit){
    val displayed=remember(cards,profile){cards.filter{profile=="all"||it.profile==profile}}
    val latest=displayed.sortedByDescending{it.created}
    val context=androidx.compose.ui.platform.LocalContext.current
    val reminderDays=context.getSharedPreferences("vault_options",android.content.Context.MODE_PRIVATE)
        .getInt("remindDays",30)
    val attention=VaultExpiryRules.needsAttention(displayed,reminderDays)
    val categories=displayed.groupBy{it.category.ifBlank{"Other"}}
        .entries.sortedByDescending{it.value.size}
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding=PaddingValues(16.dp,22.dp,16.dp,32.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Eyebrow("YOUR PERSONAL VAULT")
                    Spacer(Modifier.height(5.dp))
                    Text("My Documents",style=MaterialTheme.typography.headlineMedium)
                    Text("Everything important, in one place.",
                        style=MaterialTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick=onSearch,modifier=Modifier.size(48.dp)){
                    Icon(Icons.Outlined.Search,"Search documents",tint=MaterialTheme.colorScheme.primary)
                }
            }
        }
        item{
            HubCard(Modifier.fillMaxWidth()){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(11.dp)){
                        Eyebrow("VAULT OVERVIEW")
                        Text(VaultCounts.format(displayed.size,"document"),
                            style=MaterialTheme.typography.titleLarge)
                        Text(VaultCounts.format(displayed.sumOf{it.files.size},"file")+" saved",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(attention.isNotEmpty())TextButton(onClick=onExpiries){
                            Text(VaultCounts.format(attention.size,"item")+" to review")
                        }
                    }
                    val tracked=VaultExpiryRules.trackedCount(displayed)
                    if(tracked>0){
                        Column(horizontalAlignment=Alignment.CenterHorizontally){
                            HealthRing(VaultExpiryRules.healthyShare(displayed,reminderDays),size=80.dp)
                            Text("Valid expiry dates",style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }else{
                        Text("No expiry dates\nbeing tracked",
                            textAlign=androidx.compose.ui.text.style.TextAlign.End,
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item{
            Button(onClick=onScan,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),
                shape=RoundedCornerShape(16.dp)){
                Icon(Icons.Outlined.Add,"Add Document",Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Document")
            }
        }
        if(profiles.size>1)item{
            SectionTitle("People")
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(selected=profile=="all",onClick={onProfile("all")},
                    label={Text("Everyone")})
                profiles.forEach{person->
                    FilterChip(selected=profile==person.id,onClick={onProfile(person.id)},
                        label={Text(person.name)})
                }
            }
        }
        // Recent Documents already lists every card on a small vault; don't repeat
        // a giant decorative card with no useful thumbnail.
        item{
            SectionTitle("Categories","Search",onSearch)
            Spacer(Modifier.height(9.dp))
            if(categories.isEmpty()){
                HubCard(Modifier.fillMaxWidth()){
                    Text("Start by adding a document.",
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }else{
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                    categories.chunked(2).forEach{group->
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                            group.forEach{entry->
                                HubCard(Modifier.weight(1f),onClick={onCategory(entry.key)}){
                                    HubIcon(categoryIcon(entry.key))
                                    Text(entry.key,style=MaterialTheme.typography.titleMedium,
                                        maxLines=2,overflow=TextOverflow.Ellipsis)
                                    Text(VaultCounts.format(entry.value.size,"document"),
                                        style=MaterialTheme.typography.bodySmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if(group.size==1)Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if(attention.isNotEmpty()){
            item{SectionTitle("Needs attention","View all",onExpiries)}
            items(attention.take(3),key={it.id}){card->VaultRow(card,repo){onOpen(card)}}
        }
        item{SectionTitle("Recent Documents","View all"){onCategory("All")}}
        if(latest.isEmpty())item{
            Text("Your recently saved documents will appear here.",
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(latest.take(6),key={it.id}){card->VaultRow(card,repo){onOpen(card)}}
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
    Box(Modifier.fillMaxWidth().background(mockupPalette.panel)
        .navigationBarsPadding()){
        // HTML mockup: HOME / CARDS / (+) / PEOPLE / SETTINGS.
        VaultDock(listOf(
            DockItem("home",Icons.Outlined.Home,"Home"),
            DockItem("cards",Icons.Outlined.CreditCard,"Cards"),
            DockItem("scan",Icons.Outlined.Add,"Scan"),
            DockItem("people",Icons.Outlined.People,"People"),
            DockItem("more",Icons.Outlined.Settings,"Settings")
        ),active,onNavigate,onScan)
    }
}
