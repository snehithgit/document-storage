package com.snehith.documentvault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun DocumentMergeScreen(target:VaultCard,cards:List<VaultCard>,profiles:List<VaultProfile>,onMerge:(Set<String>,Boolean)->Unit) {
    var chosen by rememberSaveable(target.id){mutableStateOf(emptySet<String>())}
    var mode by rememberSaveable{mutableStateOf("copy")}
    var confirm by remember{mutableStateOf(false)}
    val available=cards.filter{it.id!=target.id}
    val valid=chosen.intersect(available.map{it.id}.toSet())
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Merge into ${target.title}?")},
        text={Text(if(mode=="copy")"The selected documents stay unchanged. Their files and details are copied into this document."
            else "Selected source documents will be removed after saving. Their files, fields and notes are kept in the destination. Profile links follow the moved fields.")},
        confirmButton={TextButton(onClick={confirm=false;onMerge(valid,mode=="move")}){Text(if(mode=="move")"Move and merge" else "Copy and merge")}},
        dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Destination: ${target.title}",style=MaterialTheme.typography.titleMedium)
        Text("Person: "+(profiles.firstOrNull{it.id==target.profile}?.name?:"Unknown person"))
        if(available.any{it.id in valid&&it.profile!=target.profile})Text(
            "Some selected documents belong to other people. Their files will use the destination person; profile links are retained.",
            color=MaterialTheme.colorScheme.error)
        Text("The destination keeps its person, category and expiry. Source details are also recorded in notes.")
        MockupSegment(listOf("copy" to "Copy","move" to "Move"),mode,{mode=it})
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(available.isEmpty())item{Text("There are no other documents to merge.")}
            items(available,key={it.id}){card->
                OutlinedCard(Modifier.fillMaxWidth().clickable{chosen=if(card.id in valid)valid-card.id else valid+card.id}){
                    Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                        Checkbox(card.id in valid,{checked->chosen=if(checked)valid+card.id else valid-card.id})
                        Column(Modifier.weight(1f)){
                            Text(card.title)
                            Text(profiles.firstOrNull{it.id==card.profile}?.name?:"Unknown person",style=MaterialTheme.typography.bodySmall)
                            Text(VaultCounts.format(card.files.size,"attachment"),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Button(onClick={confirm=true},enabled=valid.isNotEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
            Text(if(mode=="move")"Move and merge selected documents" else "Copy and merge selected documents")
        }
    }
}

@Composable fun DocumentSplitScreen(card:VaultCard,repo:VaultRepository,onSplit:(Map<String,Set<Int>>,Boolean)->Unit) {
    val pages=rememberVaultDocumentPages(repo,card.files)
    fun key(page:VaultDocumentPage)=page.file.id+":"+page.index
    var chosen by rememberSaveable(card.id){mutableStateOf(emptySet<String>())}
    var mode by rememberSaveable{mutableStateOf("copy")}
    var confirm by remember{mutableStateOf(false)}
    val valid=chosen.intersect(pages.map{key(it)}.toSet())
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Create ${valid.size} documents?")},
        text={Text((if(mode=="copy")"The original stays unchanged. " else "Selected files or PDF pages leave the original. If none remain, the original is replaced by the new documents. ")+
            "Details are copied to each new document; review page-specific information. Existing profile links follow the first new document when the original is removed.")},
        confirmButton={TextButton(onClick={
            confirm=false
            val selected=pages.filter{key(it) in valid}.groupBy{it.file.id}.mapValues{(_,items)->items.map{it.index}.toSet()}
            onSplit(selected,mode=="move")
        }){Text(if(mode=="move")"Move into documents" else "Copy into documents")}},
        dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Choose files or PDF pages. Each selected item becomes a separate document.")
        MockupSegment(listOf("copy" to "Copy","move" to "Move"),mode,{mode=it})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text("${valid.size} selected")
            TextButton(onClick={chosen=if(valid.size==pages.size)emptySet() else pages.take(100).map{key(it)}.toSet()}){
                Text(if(valid.size==pages.size)"Clear selection" else "Select up to 100")
            }
        }
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(pages,key={key(it)}){page->
                val id=key(page)
                Row(Modifier.fillMaxWidth().clickable{
                    if(id in valid)chosen=valid-id else if(valid.size<100)chosen=valid+id
                }.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                    Checkbox(id in valid,{checked->if(!checked)chosen=valid-id else if(valid.size<100)chosen=valid+id})
                    Text(page.file.name+(if(page.file.mime=="application/pdf"||page.file.name.endsWith(".pdf",true))" · Page ${page.index+1}" else ""),Modifier.weight(1f))
                }
            }
        }
        Button(onClick={confirm=true},enabled=valid.isNotEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
            Text(if(mode=="move")"Move selected items into documents" else "Copy selected items into documents")
        }
    }
}
