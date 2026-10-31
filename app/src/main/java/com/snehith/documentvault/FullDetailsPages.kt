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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val dBlue=VaultColors.primaryText
private val dNavy:Color @Composable get()=MaterialTheme.colorScheme.background
private val dPanel:Color @Composable get()=MaterialTheme.colorScheme.surface
private val dMuted:Color @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant

/** Read-first information sheet; values, not storage syntax, are the focus. */
@Composable fun FullDetailsPage(card:VaultCard,onSave:(VaultCard)->Unit,onSelect:()->Unit,
    onFetchDetails:()->Unit,fetchingDetails:Boolean,
    profiles:List<VaultProfile> = emptyList(),
    onAddToProfile:(String,List<DataField>)->Unit={_,_->},
    onCreatePerson:(String,List<DataField>)->Unit={_,_->}){
    val context=LocalContext.current
    var tab by rememberSaveable(card.id){mutableStateOf("All")}
    var editId by rememberSaveable(card.id){mutableStateOf("")}
    var fieldName by rememberSaveable(card.id){mutableStateOf("")}
    var fieldValue by rememberSaveable(card.id){mutableStateOf("")}
    var arranging by rememberSaveable(card.id){mutableStateOf(false)}
    var selectMode by rememberSaveable(card.id){mutableStateOf(false)}
    var selected by rememberSaveable(card.id){mutableStateOf(emptySet<String>())}
    var choosingPerson by rememberSaveable(card.id){mutableStateOf(false)}
    var revealedFields by rememberSaveable(card.id){mutableStateOf(emptySet<String>())}
    val activity=LocalContext.current as? androidx.activity.ComponentActivity
    DisposableEffect(activity,card.id){
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP)revealedFields=emptySet()
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose{activity?.lifecycle?.removeObserver(observer)}
    }
    val fields=card.fields
    val rows=fields.filter { f ->
        when(tab) {
            "Scanned" -> f.source=="extracted"
            "Picked" -> f.source=="selected"
            "Added" -> f.source=="manual"
            else -> true
        }
    }
    fun copyText(ids:Set<String>):String=fields.filter{it.id in ids&&it.value.isNotBlank()}
        .joinToString("\n"){it.name+": "+it.value}
    fun edit(field:DataField?){
        editId=field?.id ?: "new"
        fieldName=field?.name.orEmpty()
        fieldValue=field?.value.orEmpty()
    }
    fun updateField(id:String,transform:(List<DataField>)->List<DataField>){
        onSave(card.copy(fields=transform(fields)))
        selected=selected-id
    }
    if(choosingPerson){
        val chosen=fields.filter{it.id in selected&&it.value.isNotBlank()}
        AddToProfileDialog(profiles,chosen.size,
            {personId->choosingPerson=false;selected=emptySet();onAddToProfile(personId,chosen)},
            {name->choosingPerson=false;selected=emptySet();onCreatePerson(name,chosen)},
            {choosingPerson=false})
    }
    if(editId.isNotBlank()) AlertDialog(
        onDismissRequest={editId=""},
        title={Text(if(editId=="new")"Add information" else "Edit information")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            OutlinedTextField(fieldName,{fieldName=it},modifier=Modifier.fillMaxWidth(),
                label={Text("Field name")},singleLine=true)
            OutlinedTextField(fieldValue,{fieldValue=it},modifier=Modifier.fillMaxWidth(),
                label={Text("Value")},minLines=2)
        }},
        confirmButton={TextButton(onClick={
            if(fieldName.isNotBlank()){
                val existing=fields.firstOrNull{it.id==editId}
                val item=VaultFieldUpdates.edited(existing,fieldName.trim(),fieldValue.trim())
                onSave(card.copy(fields=if(existing==null)fields+item
                    else fields.map{if(it.id==existing.id)item else it}))
                editId=""
            }
        },enabled=fieldName.isNotBlank()){Text("Save")}},
        dismissButton={TextButton(onClick={editId=""}){Text("Cancel")}}
    )
    val p=mockupPalette
    Column(Modifier.fillMaxSize().background(p.panel)){
        Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=12.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)){
            Column{
                Eyebrow("Full details")
                Spacer(Modifier.height(4.dp))
                Text(VaultNames.title(card.title),color=p.ink,fontSize=22.sp,
                    fontWeight=FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.warningSoft)
                .padding(12.dp),verticalAlignment=Alignment.Top){
                Icon(Icons.Outlined.WarningAmber,null,modifier=Modifier.size(18.dp),tint=p.warning)
                Spacer(Modifier.width(10.dp))
                Text("Values read from pages are suggestions. Check them against the document. "+
                    "They do not verify identity.",color=p.ink,fontSize=12.sp,lineHeight=17.sp)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Button(onClick=onFetchDetails,
                    enabled=!fetchingDetails&&card.files.any{DetailFetchPlan.supported(it)},
                    modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                    Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if(fetchingDetails)"Reading…" else "Get Details")
                }
                OutlinedButton(onClick={edit(null)},modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                    Icon(Icons.Outlined.Add,null,Modifier.size(19.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Add Field")
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick=onSelect){
                    Icon(Icons.Outlined.TextSnippet,null,Modifier.size(19.dp))
                    Text(" Select from page")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick={
                    val next=!selectMode
                    selectMode=next
                    arranging=false
                    if(!next)selected=emptySet()
                }){
                    Text(if(selectMode)"Done" else "Select")
                }
                TextButton(onClick={
                    arranging=!arranging
                    if(arranging){selectMode=false;selected=emptySet()}
                }){
                    Text(if(arranging)"Done" else "Arrange")
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf("All","Picked","Scanned","Added").forEach{option->
                    MockupChip(option,tab==option){tab=option}
                }
            }
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=18.dp,end=18.dp,top=6.dp,bottom=16.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)){
            if(rows.isEmpty())item{
                Surface(shape=RoundedCornerShape(14.dp),color=dPanel,
                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){
                    Column(Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Icon(Icons.Outlined.Description,null,tint=dMuted)
                        Text("No information saved yet",style=MaterialTheme.typography.titleMedium)
                        Text("Get details from an attached document, select recognized text, or add your own field.",
                            color=dMuted,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(rows,key={it.id}){field->
                val isSelected=field.id in selected
                Card(colors=CardDefaults.cardColors(containerColor=p.panelRaised),
                    shape=RoundedCornerShape(18.dp),
                    border=BorderStroke(if(isSelected)1.5.dp else 1.dp,
                        if(isSelected)p.accent else p.line)){
                    Column(Modifier.fillMaxWidth().padding(start=12.dp,end=6.dp,top=12.dp,bottom=10.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            if(selectMode)Checkbox(checked=field.id in selected,
                                onCheckedChange={checked->
                                    selected=if(checked)selected+field.id else selected-field.id
                                },modifier=Modifier.size(48.dp))
                            Column(Modifier.weight(1f).clickable{edit(field)}
                                .padding(start=if(selectMode)0.dp else 4.dp),
                                verticalArrangement=Arrangement.spacedBy(5.dp)){
                                Row(verticalAlignment=Alignment.CenterVertically,
                                    horizontalArrangement=Arrangement.spacedBy(7.dp)){
                                    Text(field.name.uppercase(java.util.Locale.ROOT),color=p.inkFaint,
                                        fontSize=10.sp,letterSpacing=1.2.sp,fontWeight=FontWeight.Medium,
                                        maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier=Modifier.weight(1f,fill=false))
                                    if(field.confidence>0f&&field.confidence<.75f)
                                        MockupTag("Review",p.warning,p.warningSoft)
                                    else if(field.source=="manual")
                                        MockupTag("Manual",p.inkMuted,p.line.copy(alpha=.5f))
                                }
                                SelectionContainer{
                                    Text(if(field.value.isBlank())"Not entered" else
                                        if(ProfileLogic.isSensitive(field.name)&&field.id !in revealedFields)
                                            ProfileLogic.mask(field.value)
                                        else field.value,color=p.ink,
                                        fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                                }
                            }
                            if(ProfileLogic.isSensitive(field.name))IconButton(
                                onClick={revealedFields=if(field.id in revealedFields)
                                    revealedFields-field.id else revealedFields+field.id},
                                modifier=Modifier.size(48.dp)){
                                Icon(if(field.id in revealedFields)Icons.Outlined.VisibilityOff
                                    else Icons.Outlined.Visibility,
                                    if(field.id in revealedFields)"Hide "+field.name else "Reveal "+field.name,
                                    modifier=Modifier.size(20.dp),tint=p.inkMuted)
                            }
                            IconButton(onClick={copyToClipboard(context,field.name,field.value)},
                                enabled=field.value.isNotBlank(),modifier=Modifier.size(48.dp)){
                                Icon(Icons.Outlined.ContentCopy,"Copy "+field.name,
                                    modifier=Modifier.size(20.dp),tint=p.accent)
                            }
                            IconButton(onClick={edit(field)},modifier=Modifier.size(48.dp)){
                                Icon(Icons.Outlined.Edit,"Edit "+field.name,
                                    modifier=Modifier.size(20.dp),tint=p.inkMuted)
                            }
                        }
                        if(arranging){
                            HorizontalDivider(Modifier.padding(top=8.dp))
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                val index=fields.indexOfFirst{it.id==field.id}
                                IconButton(onClick={
                                    if(index>0){
                                        val next=fields.toMutableList()
                                        java.util.Collections.swap(next,index,index-1)
                                        updateField("",{next})
                                    }
                                },enabled=index>0){
                                    Icon(Icons.Outlined.ArrowUpward,"Move "+field.name+" up")
                                }
                                IconButton(onClick={
                                    if(index in 0 until fields.lastIndex){
                                        val next=fields.toMutableList()
                                        java.util.Collections.swap(next,index,index+1)
                                        updateField("",{next})
                                    }
                                },enabled=index>=0&&index<fields.lastIndex){
                                    Icon(Icons.Outlined.ArrowDownward,"Move "+field.name+" down")
                                }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick={edit(field)}){Text("Edit")}
                                TextButton(onClick={updateField(field.id){items->
                                    items.filterNot{it.id==field.id}
                                }}){Text("Remove",color=MaterialTheme.colorScheme.error)}
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color=p.line)
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=10.dp),
            horizontalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedButton(onClick={copyToClipboard(context,"Selected information",copyText(selected))},
                enabled=fields.any{it.id in selected&&it.value.isNotBlank()},
                modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                Icon(Icons.Outlined.Checklist,"Copy selected fields",Modifier.size(19.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy Selected")
            }
            if(fields.any{it.id in selected&&it.value.isNotBlank()}) {
                Button(onClick={choosingPerson=true},
                    modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                    Icon(Icons.Outlined.PersonAdd,"Add selected details to person",
                        Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add to profile · "+fields.count{it.id in selected&&it.value.isNotBlank()})
                }
            }else{
                Button(onClick={copyToClipboard(context,"Document information",
                    copyText(fields.map{it.id}.toSet()))},
                    enabled=fields.any{it.value.isNotBlank()},
                    modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                    Icon(Icons.Outlined.ContentCopy,"Copy all fields",Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy All")
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
        }){Text("Save field")}},
        dismissButton={TextButton(onClick={candidate=null}){Text("Cancel")}})
    Column(Modifier.fillMaxSize().background(dNavy).padding(14.dp)){
        Text("Choose information from a page",color=VaultColors.syntax,style=MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(7.dp))
        Text("Recognized text",color=MaterialTheme.colorScheme.onSurface,
            style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
        Text("Tap a line to save it as a document detail.",color=dMuted,
            style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
            card.files.forEachIndexed{i,f->FilterChip(selected=i==selectedFile,onClick={selectedFile=i},
                label={Text(VaultNames.title(f.name).take(24))})}
        }
        if(file==null)Text("Attach a document first.",color=dMuted)
        else if(file.extractedText.isBlank()){
            Spacer(Modifier.height(15.dp))
            Text("Extract details to select text from this document.",color=dMuted)
            Button(onClick={onRunOcr(file)}){Text("Get Details")}
        }else{
            OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),
                label={Text("Find text")},singleLine=true,leadingIcon={Icon(Icons.Outlined.Search,null)})
            Spacer(Modifier.height(8.dp))
            if(truncated) Text("Showing first 350 matches. Refine search to see more.",
                color=dMuted,style=MaterialTheme.typography.bodySmall)
            LazyColumn(
                modifier=Modifier.weight(1f).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding=PaddingValues(bottom=16.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)){
                items(choices.take(350)){(page,line)->
                    Card(onClick={candidate=line;label="";chosenPage=page},
                        colors=CardDefaults.cardColors(containerColor=dPanel),
                        shape=RoundedCornerShape(11.dp),
                        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){
                        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                            Text((page+1).toString().padStart(2,'0'),color=VaultColors.lineNumber,
                                style=MaterialTheme.typography.labelSmall,modifier=Modifier.width(30.dp))
                            Text(line,color=MaterialTheme.colorScheme.onSurface,
                                style=MaterialTheme.typography.bodyMedium,
                                modifier=Modifier.weight(1f))
                            Icon(Icons.Outlined.Add,null,tint=VaultColors.syntax,modifier=Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
