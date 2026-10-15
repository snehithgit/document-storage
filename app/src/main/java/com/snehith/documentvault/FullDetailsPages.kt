package com.snehith.documentvault

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val dBlue=VaultColors.primaryText
private val dNavy:Color @Composable get()=MaterialTheme.colorScheme.background
private val dPanel:Color @Composable get()=MaterialTheme.colorScheme.surface
private val dMuted:Color @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant

@Composable fun FullDetailsPage(card:VaultCard,onSave:(VaultCard)->Unit,onSelect:()->Unit) {
    val context=LocalContext.current
    var tab by rememberSaveable(card.id){mutableStateOf("All")}
    var editId by rememberSaveable(card.id){mutableStateOf("")}
    var name by rememberSaveable(card.id){mutableStateOf("")}
    var value by rememberSaveable(card.id){mutableStateOf("")}
    var advanced by rememberSaveable(card.id){mutableStateOf(false)}
    var selected by rememberSaveable(card.id){mutableStateOf(emptySet<String>())}
    val fields=card.fields
    val rows=fields.mapIndexed{index,f->index to f}.filter{(_,f)->
        tab=="All"||(tab=="OCR"&&f.source=="extracted")||
        (tab=="Selected"&&f.source=="selected")||(tab=="Custom"&&f.source=="manual")
    }
    fun copied(ids:Set<String>)=fields.filter{it.id in ids && it.value.isNotBlank()}
        .joinToString("\n"){it.name+": "+it.value}
    if(editId.isNotBlank())AlertDialog(onDismissRequest={editId=""},
        title={Text(if(editId=="new")"New custom field" else "Edit saved field")},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(name,{name=it},label={Text("Field name")})
            OutlinedTextField(value,{value=it},label={Text("Information to copy")},minLines=2)
        }},
        confirmButton={TextButton(onClick={
            if(name.isNotBlank()){
                val next=fields.toMutableList()
                val old=fields.firstOrNull{it.id==editId}
                val updated=DataField(name.trim(),value.trim(),old?.page?:-1,old?.source?:"manual",
                    old?.id?:java.util.UUID.randomUUID().toString())
                if(old==null)next.add(updated)else next[next.indexOfFirst{it.id==old.id}]=updated
                onSave(card.copy(fields=next))
            }
            editId=""
        }){Text("Save")}},
        dismissButton={TextButton(onClick={editId=""}){Text("Cancel")}})
    Column(Modifier.fillMaxSize().background(dNavy)){
        Column(Modifier.padding(16.dp)){
            Text("Full Details",color=MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text(VaultNames.title(card.title)+" · "+VaultCounts.format(fields.size,"field"),color=dMuted)
            Spacer(Modifier.height(11.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                listOf("All","Selected","OCR","Custom").forEach{t->
                    FilterChip(selected=tab==t,onClick={tab=t},label={Text(t)})
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                Button(onClick=onSelect,modifier=Modifier.weight(1f)){
                    Icon(Icons.Outlined.TextSnippet,null,Modifier.size(17.dp));Text(" From Document")
                }
                OutlinedButton(onClick={editId="new";name="";value=""},modifier=Modifier.weight(1f)){
                    Icon(Icons.Outlined.Add,null,Modifier.size(17.dp));Text(" Custom Field")
                }
            }
            TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Done arranging" else "Reorder / Remove fields")}
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=14.dp,vertical=4.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(rows,key={it.second.id}){(index,f)->
                Surface(color=dPanel,shape=RoundedCornerShape(24.dp),
                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
                    Column(Modifier.padding(10.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Checkbox(checked=f.id in selected,onCheckedChange={yes->
                                selected=if(yes)selected+f.id else selected-f.id
                            })
                            Column(Modifier.weight(1f)){
                                Text(f.name,color=dMuted,style=MaterialTheme.typography.labelMedium)
                                SelectionContainer{Text(f.value.ifBlank{"—"},color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Medium)}
                                Text(if(f.page>=0)"Page ${f.page+1} · ${f.source}" else f.source,
                                    color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick={copyToClipboard(context,f.name,f.value)}){
                                Icon(Icons.Outlined.ContentCopy,"Copy "+f.name,tint=MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick={editId=f.id;name=f.name;value=f.value}){
                                Icon(Icons.Outlined.Edit,"Edit field",tint=dMuted)
                            }
                        }
                        if(advanced)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            TextButton(onClick={
                                if(index>0){
                                    val next=fields.toMutableList()
                                    java.util.Collections.swap(next,index,index-1)
                                    onSave(card.copy(fields=next))
                                }
                            }){Text("Move up")}
                            TextButton(onClick={
                                if(index<fields.lastIndex){
                                    val next=fields.toMutableList()
                                    java.util.Collections.swap(next,index,index+1)
                                    onSave(card.copy(fields=next))
                                }
                            }){Text("Move down")}
                            TextButton(onClick={
                                onSave(card.copy(fields=fields.filterIndexed{i,_->i!=index}));selected=selected-f.id
                            }){Text("Remove",color=VaultColors.expiring)}
                        }
                    }
                }
            }
            if(rows.isEmpty())item{
                Text("No fields saved in this section. Choose text from your document or add a custom field.",
                    color=dMuted,modifier=Modifier.padding(12.dp))
            }
        }
        Surface(color=MaterialTheme.colorScheme.surface,shadowElevation=0.dp,
            border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
            Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={copyToClipboard(context,"Selected information",copied(selected))},
                    enabled=selected.isNotEmpty(),modifier=Modifier.weight(1f)){
                    Icon(Icons.Outlined.Checklist,null,Modifier.size(16.dp));Text(" Selected (${selected.size})")
                }
                Button(onClick={copyToClipboard(context,"Full details",copied(fields.map{it.id}.toSet()))},
                    enabled=fields.any{it.value.isNotBlank()},modifier=Modifier.weight(1f)){
                    Icon(Icons.Outlined.ContentCopy,null,Modifier.size(16.dp));Text(" Copy All")
                }
            }
        }
    }
}
@Composable fun SelectDocumentTextPage(card:VaultCard,onAdd:(DataField)->Unit,onRunOcr:(VaultFile)->Unit){
    var selectedFile by rememberSaveable(card.id){mutableIntStateOf(0)}
    var query by rememberSaveable(card.id){mutableStateOf("")}
    var candidate by rememberSaveable(card.id){mutableStateOf<String?>(null)}
    var label by rememberSaveable(card.id){mutableStateOf("")}
    var chosenPage by rememberSaveable(card.id){mutableIntStateOf(-1)}
    val file=card.files.getOrNull(selectedFile)
    var pg=0
    val choices=(file?.extractedText?:"").lineSequence().mapNotNull{line->
        val marker=Regex("--- PAGE (\\d+) ---").find(line.trim())
        if(marker!=null){pg=(marker.groupValues[1].toIntOrNull()?:1)-1;null}
        else if(line.trim().length in 2..220 && (query.isBlank()||line.contains(query,true)))pg to line.trim() else null
    }.take(351).toList()
    val truncated=choices.size>350
    if(candidate!=null)AlertDialog(onDismissRequest={candidate=null},title={Text("Save selected detail")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(label,{label=it},label={Text("Field name")})
            OutlinedTextField(candidate?:"",{candidate=it},label={Text("Value")},minLines=2)
        }},
        confirmButton={TextButton(onClick={
            if(label.isNotBlank()&&!candidate.isNullOrBlank()){
                onAdd(DataField(label.trim(),candidate!!.trim(),chosenPage,"selected"))
                candidate=null;label=""
            }
        }){Text("Add to Full Details")}},
        dismissButton={TextButton(onClick={candidate=null}){Text("Cancel")}})
    Column(Modifier.fillMaxSize().background(dNavy).padding(14.dp)){
        Text("Select from Document",color=MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Text("Tap a line of OCR text, give it a name, then save it.",color=dMuted)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
            card.files.forEachIndexed{i,f->FilterChip(selected=i==selectedFile,onClick={selectedFile=i},
                label={Text(VaultNames.title(f.name).take(24))})}
        }
        if(file==null)Text("Attach a document first.",color=dMuted)
        else if(file.extractedText.isBlank()){
            Spacer(Modifier.height(15.dp))
            Text("This document needs OCR before individual text can be selected.",color=dMuted)
            Button(onClick={onRunOcr(file)}){Text("Run on-device OCR")}
        }else{
            OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),
                label={Text("Find text")},singleLine=true,leadingIcon={Icon(Icons.Outlined.Search,null)})
            Spacer(Modifier.height(8.dp))
            if(truncated) Text("Showing first 350 matches. Refine search to see more.",
                color=dMuted,style=MaterialTheme.typography.bodySmall)
            LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)){
                items(choices.take(350)){(page,line)->
                    Card(onClick={candidate=line;label="";chosenPage=page},colors=CardDefaults.cardColors(containerColor=dPanel),
                        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
                        Text(line,color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.fillMaxWidth().padding(13.dp))
                    }
                }
            }
        }
    }
}
