package com.snehith.documentvault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable fun ProfileDocumentsScreen(person:VaultProfile,cards:List<VaultCard>,repo:VaultRepository,
    hidePreviews:Boolean,onOpen:(VaultCard)->Unit,onDetails:()->Unit,onAllDocuments:()->Unit){
    var query by rememberSaveable(person.id){mutableStateOf("")}
    var category by rememberSaveable(person.id){mutableStateOf("All")}
    var sort by rememberSaveable(person.id){mutableStateOf("Recent")}
    var showSort by remember{mutableStateOf(false)}
    val owned=cards.filter{it.profile==person.id}
    val effective=DocumentBrowseLogic.effectiveCategory(owned,category)
    val shown=DocumentBrowseLogic.browse(owned,effective,sort).filter{
        query.isBlank()||it.title.contains(query.trim(),true)||it.tags.contains(query.trim(),true)
    }
    val p=mockupPalette
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(Modifier.fillMaxWidth().padding(top=16.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)){
            PersonAvatar(person.name,48.dp,person.id)
            Column(Modifier.weight(1f)){
                Text(person.name,style=MaterialTheme.typography.headlineSmall)
                Text(VaultCounts.format(owned.size,"document"),style=MaterialTheme.typography.bodyMedium)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            TextButton(onClick=onDetails){Text("Profile details")}
            TextButton(onClick=onAllDocuments){Text("All documents")}
        }
        OutlinedTextField(query,{query=it},label={Text("Search this profile")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(verticalAlignment=Alignment.CenterVertically){
            Text("Documents",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            Box{
                TextButton(onClick={showSort=true}){Text("Sort: $sort")}
                DropdownMenu(showSort,{showSort=false}){
                    listOf("Recent","Name","Expiry").forEach{value->
                        DropdownMenuItem(text={Text(value)},onClick={sort=value;showSort=false})
                    }
                }
            }
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            items(listOf("All")+owned.map{it.category.ifBlank{"Other"}}.distinct().sorted()){value->
                FilterChip(effective==value,{category=value},label={Text(value)})
            }
        }
        if(shown.isEmpty())Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){
            Text(if(owned.isEmpty())"No documents assigned to this profile yet." else "No documents match these filters.")
        }else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            items(shown,key={it.id}){card->
                Surface(onClick={onOpen(card)},shape=MaterialTheme.shapes.large,color=p.panelRaised,border=BorderStroke(1.dp,p.line)){
                    Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        VaultPreviewPrivacy(hidePreviews&&card.category.equals("Identity",true),Modifier.size(54.dp,70.dp),showLabel=false){
                            VaultThumbnail(card.files.firstOrNull(),repo,Modifier.size(54.dp,70.dp))
                        }
                        Column(Modifier.weight(1f)){
                            Text(VaultNames.title(card.title),maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(card.category,style=MaterialTheme.typography.bodySmall)
                        }
                        ExpiryTag(card)
                    }
                }
            }
        }
    }
}
