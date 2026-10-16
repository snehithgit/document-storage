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

@Composable fun FullDetailsPage(card:VaultCard,onSave:(VaultCard)->Unit,onSelect:()->Unit,
    onFetchDetails:()->Unit,fetchingDetails:Boolean){
    val context=LocalContext.current
    var tab by rememberSaveable(card.id){mutableStateOf("All")}
    var editId by rememberSaveable(card.id){mutableStateOf("")}
    var fieldName by rememberSaveable(card.id){mutableStateOf("")}
    var fieldValue by rememberSaveable(card.id){mutableStateOf("")}
    var arranging by rememberSaveable(card.id){mutableStateOf(false)}
    var selected by rememberSaveable(card.id){mutableStateOf(emptySet<String>())}
    val fields=card.fields
    val rows=fields.mapIndexed{index,f->index to f}.filter{(_,f)->
        when(tab) {
            "OCR"->f.source=="extracted"
            "Picked"->f.source=="selected"
            "Custom"->f.source=="manual"
            else->true
        }
    }
    fun copyText(ids:Set<String>)=fields.filter{it.id in ids&&it.value.isNotBlank()}
        .joinToString("\n"){it.name+": "+it.value}
    if(editId.isNotBlank())AlertDialog(
        onDismissRequest={editId=""},
        title={Text(if(editId=="new")"Add a field" else "Edit field")},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(fieldName,{fieldName=it},modifier=Modifier.fillMaxWidth(),
                label={Text("Field name")},singleLine=true)
            OutlinedTextField(fieldValue,{fieldValue=it},modifier=Modifier.fillMaxWidth(),
                label={Text("Value")},minLines=2)
        }},
        confirmButton={TextButton(onClick={
            if(fieldName.isNotBlank()){
                val next=fields.toMutableList()
                val old=fields.firstOrNull{it.id==editId}
                val field=DataField(fieldName.trim(),fieldValue.trim(),old?.page?:-1,
                    old?.source?:"manual",old?.id?:java.util.UUID.randomUUID().toString(),
                    old?.confidence?:0f)
                if(old==null)next.add(field)else next[next.indexOfFirst{it.id==old.id}]=field
                onSave(card.copy(fields=next))
            }
            editId=""
        },enabled=fieldName.isNotBlank()){Text("Save")}},
        dismissButton={TextButton(onClick={editId=""}){Text("Cancel")}}
    )
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){
        Column(Modifier.padding(horizontal=16.dp,vertical=10.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("VAULT / FIELD EDITOR",color=VaultColors.syntax,
                    modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
                Text("● LOCAL",color=VaultColors.valid,style=MaterialTheme.typography.labelSmall)
            }
            Text(VaultNames.title(card.title),color=MaterialTheme.colorScheme.onSurface,
                fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleLarge)
            Row(verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Outlined.DataObject,null,tint=VaultColors.syntax,modifier=Modifier.size(17.dp))
                Spacer(Modifier.width(9.dp))
                Text("details.json",style=MaterialTheme.typography.labelMedium,modifier=Modifier.weight(1f))
                Text(VaultCounts.format(fields.size,"field"),color=VaultColors.lineNumber,
                    style=MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(7.dp)){
                listOf("All","Picked","OCR","Custom").forEach{t->
                    FilterChip(selected=tab==t,onClick={tab=t},
                        label={Text(t)},shape=RoundedCornerShape(8.dp))
                }
            }
            if(card.files.isNotEmpty()){
                OutlinedButton(
                    onClick=onFetchDetails,
                    enabled=!fetchingDetails && card.files.any{DetailFetchPlan.supported(it)},
                    modifier=Modifier.fillMaxWidth(),
                    shape=RoundedCornerShape(10.dp)
                ){
                    Icon(Icons.Outlined.AutoAwesome,null,modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if(fetchingDetails)"Fetching document details…" else "Fetch Details from All Pages",
                        modifier=Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight,null,modifier=Modifier.size(17.dp))
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick=onSelect,modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(10.dp)){
                    Icon(Icons.Outlined.TextSnippet,null,Modifier.size(16.dp))
                    Text(" From document")
                }
                OutlinedButton(onClick={editId="new";fieldName="";fieldValue=""},
                    modifier=Modifier.weight(1f),shape=RoundedCornerShape(10.dp)){
                    Icon(Icons.Outlined.Add,null,Modifier.size(16.dp))
                    Text(" Custom field")
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("// Tap and hold values to select text",color=VaultColors.lineNumber,
                    style=MaterialTheme.typography.labelSmall,modifier=Modifier.weight(1f))
                TextButton(onClick={arranging=!arranging}){
                    Text(if(arranging)"Done" else "Arrange",color=VaultColors.syntax)
                }
            }
        }
        Surface(modifier=Modifier.fillMaxWidth().weight(1f).padding(horizontal=13.dp),
            color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(13.dp),
            border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){
            Column{
                Row(Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=12.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    listOf(VaultColors.expired,VaultColors.expiring,VaultColors.valid).forEach{tint->
                        Box(Modifier.padding(end=5.dp).size(7.dp).background(
                            tint,androidx.compose.foundation.shape.CircleShape))
                    }
                    Spacer(Modifier.width(11.dp))
                    Text("details.json",color=MaterialTheme.colorScheme.onSurfaceVariant,
                        style=MaterialTheme.typography.labelSmall,modifier=Modifier.weight(1f))
                    Text("UTF-8",color=VaultColors.lineNumber,style=MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outline)
                LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=5.dp)){
                    item{
                        Row(Modifier.padding(horizontal=12.dp,vertical=8.dp)){
                            Text("01",color=VaultColors.lineNumber,modifier=Modifier.width(30.dp),
                                style=MaterialTheme.typography.labelSmall)
                            Text("{",fontFamily=androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                    items(rows,key={it.second.id}){(index,f)->
                        Row(Modifier.fillMaxWidth().padding(horizontal=9.dp,vertical=6.dp),
                            verticalAlignment=Alignment.CenterVertically){
                            Text((index+2).toString().padStart(2,'0'),
                                color=VaultColors.lineNumber,modifier=Modifier.width(28.dp),
                                style=MaterialTheme.typography.labelSmall)
                            Checkbox(checked=f.id in selected,onCheckedChange={checked->
                                selected=if(checked)selected+f.id else selected-f.id
                            },modifier=Modifier.size(35.dp))
                            Column(Modifier.weight(1f).padding(start=4.dp),
                                verticalArrangement=Arrangement.spacedBy(3.dp)){
                                Text("\""+f.name+"\":",color=VaultColors.syntax,
                                    fontFamily=androidx.compose.ui.text.font.FontFamily.Monospace,
                                    style=MaterialTheme.typography.bodySmall)
                                SelectionContainer{
                                    Text("\""+f.value.ifBlank{"—"}+"\"",
                                        color=MaterialTheme.colorScheme.onSurface,
                                        fontFamily=androidx.compose.ui.text.font.FontFamily.Monospace,
                                        style=MaterialTheme.typography.bodyMedium)
                                }
                                val origin=if(f.confidence>0f)
                                    "// OCR confidence "+(f.confidence*100).toInt()+"% · review"
                                else "// "+(if(f.page>=0)"page "+(f.page+1)+" · " else "")+f.source
                                Text(origin,color=VaultColors.lineNumber,
                                    style=MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick={copyToClipboard(context,f.name,f.value)},
                                modifier=Modifier.size(34.dp)){
                                Icon(Icons.Outlined.ContentCopy,"Copy "+f.name,
                                    tint=MaterialTheme.colorScheme.onSurface,modifier=Modifier.size(17.dp))
                            }
                            IconButton(onClick={editId=f.id;fieldName=f.name;fieldValue=f.value},
                                modifier=Modifier.size(34.dp)){
                                Icon(Icons.Outlined.Edit,"Edit "+f.name,
                                    tint=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(17.dp))
                            }
                        }
                        if(arranging)Row(Modifier.padding(start=38.dp),
                            horizontalArrangement=Arrangement.spacedBy(3.dp)){
                            TextButton(enabled=index>0,onClick={
                                if(index>0){
                                    val next=fields.toMutableList()
                                    java.util.Collections.swap(next,index,index-1)
                                    onSave(card.copy(fields=next))
                                }
                            }){Text("↑ Up")}
                            TextButton(enabled=index<fields.lastIndex,onClick={
                                if(index<fields.lastIndex){
                                    val next=fields.toMutableList()
                                    java.util.Collections.swap(next,index,index+1)
                                    onSave(card.copy(fields=next))
                                }
                            }){Text("↓ Down")}
                            TextButton(onClick={
                                onSave(card.copy(fields=fields.filterNot{it.id==f.id}))
                                selected=selected-f.id
                            }){Text("Remove",color=MaterialTheme.colorScheme.error)}
                        }
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant,
                            modifier=Modifier.padding(start=42.dp,end=12.dp))
                    }
                    if(rows.isEmpty())item{
                        Text("// Empty. Select from document or add a field.",
                            color=MaterialTheme.colorScheme.onSurfaceVariant,
                            style=MaterialTheme.typography.bodySmall,
                            modifier=Modifier.padding(16.dp))
                    }
                    item{
                        Row(Modifier.padding(horizontal=12.dp,vertical=9.dp)){
                            Text((fields.size+2).toString().padStart(2,'0'),
                                color=VaultColors.lineNumber,modifier=Modifier.width(30.dp),
                                style=MaterialTheme.typography.labelSmall)
                            Text("}",fontFamily=androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(9.dp)){
            OutlinedButton(
                onClick={copyToClipboard(context,"Selected information",copyText(selected))},
                modifier=Modifier.weight(1f),enabled=fields.any{it.id in selected&&it.value.isNotBlank()},
                shape=RoundedCornerShape(10.dp)){
                Icon(Icons.Outlined.Checklist,null,Modifier.size(17.dp))
                Text(" Selected ("+selected.size+")")
            }
            Button(
                onClick={copyToClipboard(context,"Full details",copyText(fields.map{it.id}.toSet()))},
                modifier=Modifier.weight(1f),enabled=fields.any{it.value.isNotBlank()},
                shape=RoundedCornerShape(10.dp)){
                Icon(Icons.Outlined.ContentCopy,null,Modifier.size(17.dp))
                Text(" Copy all")
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
        Text("OCR / SELECT SOURCE",color=VaultColors.syntax,style=MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(7.dp))
        Text("text.extract",color=MaterialTheme.colorScheme.onSurface,
            style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
        Text("// Tap a source line, name it, save to details.json",color=dMuted,
            style=MaterialTheme.typography.bodySmall)
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
                    Card(onClick={candidate=line;label="";chosenPage=page},
                        colors=CardDefaults.cardColors(containerColor=dPanel),
                        shape=RoundedCornerShape(11.dp),
                        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){
                        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                            Text((page+1).toString().padStart(2,'0'),color=VaultColors.lineNumber,
                                style=MaterialTheme.typography.labelSmall,modifier=Modifier.width(30.dp))
                            Text(line,color=MaterialTheme.colorScheme.onSurface,
                                fontFamily=androidx.compose.ui.text.font.FontFamily.Monospace,
                                style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f))
                            Icon(Icons.Outlined.Add,null,tint=VaultColors.syntax,modifier=Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
