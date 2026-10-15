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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val dBlue=Color(0xFF1779FF)
private val dNavy=Color(0xFF061A32)
private val dPanel=Color(0xFF0E3054)
private val dMuted=Color(0xFF9DBBD9)

@Composable fun FullDetailsPage(card:VaultCard,onSave:(VaultCard)->Unit,onSelect:()->Unit) {
    val context=LocalContext.current
    var tab by remember{mutableStateOf("All")}
    var editIndex by remember{mutableIntStateOf(-1)}
    var name by remember{mutableStateOf("")}
    var value by remember{mutableStateOf("")}
    var advanced by remember{mutableStateOf(false)}
    val selected=remember(card.id){mutableStateListOf<Int>()}
    val fields=card.fields
    val rows=fields.mapIndexed{index,f->index to f}.filter{(_,f)->
        tab=="All"||(tab=="OCR"&&f.source=="extracted")||
        (tab=="Selected"&&f.source=="selected")||(tab=="Custom"&&f.source=="manual")
    }
    fun copied(indices:List<Int>)=indices.mapNotNull{fields.getOrNull(it)}
        .filter{it.value.isNotBlank()}.joinToString("\n"){it.name+": "+it.value}
    if(editIndex>=0)AlertDialog(onDismissRequest={editIndex=-1},
        title={Text(if(editIndex==fields.size)"New custom field" else "Edit saved field")},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(name,{name=it},label={Text("Field name")})
            OutlinedTextField(value,{value=it},label={Text("Information to copy")},minLines=2)
        }},
        confirmButton={TextButton(onClick={
            if(name.isNotBlank()){
                val next=fields.toMutableList()
                val old=fields.getOrNull(editIndex)
                val updated=DataField(name.trim(),value.trim(),old?.page?:-1,old?.source?:"manual")
                if(editIndex==fields.size)next.add(updated)else next[editIndex]=updated
                onSave(card.copy(fields=next))
            }
            editIndex=-1
        }){Text("Save")}},
        dismissButton={TextButton(onClick={editIndex=-1}){Text("Cancel")}})
    Column(Modifier.fillMaxSize().background(dNavy)){
        Column(Modifier.padding(16.dp)){
            Text("Full Details",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text(card.title+" · "+fields.size+" fields",color=dMuted)
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
                OutlinedButton(onClick={editIndex=fields.size;name="";value=""},modifier=Modifier.weight(1f)){
                    Icon(Icons.Outlined.Add,null,Modifier.size(17.dp));Text(" Custom Field")
                }
            }
            TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Done arranging" else "Reorder / Remove fields")}
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=14.dp,vertical=4.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(rows,key={it.first}){(index,f)->
                Surface(color=dPanel,shape=RoundedCornerShape(14.dp)){
                    Column(Modifier.padding(10.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Checkbox(checked=index in selected,onCheckedChange={yes->
                                if(yes){if(index !in selected)selected.add(index)}else selected.remove(index)
                            })
                            Column(Modifier.weight(1f)){
                                Text(f.name,color=dMuted,style=MaterialTheme.typography.labelMedium)
                                SelectionContainer{Text(f.value.ifBlank{"—"},color=Color.White,fontWeight=FontWeight.Medium)}
                                Text(if(f.page>=0)"Page ${f.page+1} · ${f.source}" else f.source,
                                    color=Color(0xFF76B7FF),style=MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick={copyToClipboard(context,f.name,f.value)}){
                                Icon(Icons.Outlined.ContentCopy,"Copy "+f.name,tint=Color(0xFF76B7FF))
                            }
                            IconButton(onClick={editIndex=index;name=f.name;value=f.value}){
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
                                onSave(card.copy(fields=fields.filterIndexed{i,_->i!=index}));selected.clear()
                            }){Text("Remove",color=Color(0xFFFFB86A))}
                        }
                    }
                }
            }
            if(rows.isEmpty())item{
                Text("No fields saved in this section. Choose text from your document or add a custom field.",
                    color=dMuted,modifier=Modifier.padding(12.dp))
            }
        }
        Surface(color=Color(0xFF092742),shadowElevation=7.dp){
            Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={copyToClipboard(context,"Selected information",copied(selected.sorted()))},
                    enabled=selected.isNotEmpty(),modifier=Modifier.weight(1f)){
                    Icon(Icons.Outlined.Checklist,null,Modifier.size(16.dp));Text(" Selected (${selected.size})")
                }
                Button(onClick={copyToClipboard(context,"Full details",copied(fields.indices.toList()))},
                    enabled=fields.any{it.value.isNotBlank()},modifier=Modifier.weight(1f)){
                    Icon(Icons.Outlined.ContentCopy,null,Modifier.size(16.dp));Text(" Copy All")
                }
            }
        }
    }
}
@Composable fun SelectDocumentTextPage(card:VaultCard,onAdd:(DataField)->Unit,onRunOcr:(VaultFile)->Unit){
    var selectedFile by remember{mutableIntStateOf(0)}
    var query by remember{mutableStateOf("")}
    var candidate by remember{mutableStateOf<String?>(null)}
    var label by remember{mutableStateOf("")}
    var chosenPage by remember{mutableIntStateOf(-1)}
    val file=card.files.getOrNull(selectedFile)
    var pg=0
    val choices=(file?.extractedText?:"").lineSequence().mapNotNull{line->
        val marker=Regex("--- PAGE (\\d+) ---").find(line.trim())
        if(marker!=null){pg=(marker.groupValues[1].toIntOrNull()?:1)-1;null}
        else if(line.trim().length in 2..220 && (query.isBlank()||line.contains(query,true)))pg to line.trim() else null
    }.take(350).toList()
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
        Text("Select from Document",color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Text("Tap a line of OCR text, give it a name, then save it.",color=dMuted)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
            card.files.forEachIndexed{i,f->FilterChip(selected=i==selectedFile,onClick={selectedFile=i},
                label={Text(f.name.take(15))})}
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
            LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)){
                items(choices){(page,line)->
                    Card(onClick={candidate=line;label="";chosenPage=page},colors=CardDefaults.cardColors(containerColor=dPanel)){
                        Text(line,color=Color.White,modifier=Modifier.fillMaxWidth().padding(13.dp))
                    }
                }
            }
        }
    }
}
