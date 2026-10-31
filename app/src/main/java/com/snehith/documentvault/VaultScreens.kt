package com.snehith.documentvault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val Blue=VaultColors.syntax
private val Mint=VaultColors.valid
private val Gold=VaultColors.expiring
private val Corners=RoundedCornerShape(20.dp)

fun copyToClipboard(context:Context,label:String,value:String) {
    if(value.isBlank())return
    // Anything that looks private is copied through the auto-clearing path.
    if(ProfileLogic.isSensitive(label)||label.contains("details",true)||
        label.contains("information",true)||label.contains("selected",true)){
        copyAndClearLater(context,label,value,true);return
    }
    val clip=ClipData.newPlainText(label,value)
    if(android.os.Build.VERSION.SDK_INT>=33 &&
        (ProfileLogic.isSensitive(label) ||
        label.contains("details",true) || label.contains("information",true) ||
        label.contains("selected",true))){
        clip.description.extras=android.os.PersistableBundle().apply{
            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE,true)
        }
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(clip)
    if(android.os.Build.VERSION.SDK_INT<33)
        android.widget.Toast.makeText(context,"Copied",android.widget.Toast.LENGTH_SHORT).show()
}
@Composable fun Heading(title:String,description:String?=null) {
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        if(description!=null)Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun AccentChip(label:String,color:Color=Blue) {
    Surface(color=color.copy(alpha=.11f),shape=RoundedCornerShape(10.dp)) {
        Text(label,modifier=Modifier.padding(horizontal=10.dp,vertical=6.dp),color=color,style=MaterialTheme.typography.labelSmall)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CardEditorScreen(card:VaultCard,profiles:List<VaultProfile>,onSave:(VaultCard)->Unit,onCancel:()->Unit,onSaveTemplate:(CardTemplate)->Unit,onDirtyChange:(Boolean)->Unit,categories:List<String> = emptyList()) {
    var title by rememberSaveable(card.id){mutableStateOf(card.title)}
    var category by rememberSaveable(card.id){mutableStateOf(card.category)}
    var kind by rememberSaveable(card.id){mutableStateOf(card.kind)}
    var tags by rememberSaveable(card.id){mutableStateOf(card.tags)}
    var notes by rememberSaveable(card.id){mutableStateOf(card.notes)}
    var expiry by rememberSaveable(card.id){mutableStateOf(card.expiry)}
    var profile by rememberSaveable(card.id){mutableStateOf(card.profile)}
    val fieldsSaver=listSaver<SnapshotStateList<DataField>,String>(
        save={list->list.flatMap{listOf(it.id,it.name,it.value,it.page.toString(),it.source,it.confidence.toString())}},
        restore={items->mutableStateListOf<DataField>().apply {
            items.chunked(6).forEach{v->if(v.size==6)
                add(DataField(v[1],v[2],v[3].toIntOrNull()?:-1,v[4],v[0],v[5].toFloatOrNull()?:0f))
            }
        }}
    )
    val fields=rememberSaveable(card.id,saver=fieldsSaver){
        mutableStateListOf<DataField>().apply{addAll(card.fields)}
    }
    var categoryExpanded by rememberSaveable{mutableStateOf(false)}
    var newCategory by rememberSaveable(card.id){mutableStateOf("")}
    var creatingCategory by rememberSaveable(card.id){mutableStateOf(false)}
    var choosingDate by rememberSaveable(card.id){mutableStateOf(false)}
    var kindExpanded by rememberSaveable{mutableStateOf(false)}
    var profileExpanded by rememberSaveable{mutableStateOf(false)}
    var error by rememberSaveable{mutableStateOf("")}
    val changed=title!=card.title||category!=card.category||kind!=card.kind||
        tags!=card.tags||notes!=card.notes||expiry!=card.expiry||profile!=card.profile||
        fields.toList()!=card.fields
    LaunchedEffect(changed){onDirtyChange(changed)}
    fun save(){
        if(title.isBlank()){error="Enter a title";return}
        if(expiry.isNotBlank()&&!VaultExpiry.isIsoDate(expiry)){error="Choose a valid expiry date";return}
        onSave(card.copy(title=title.trim(),kind=kind,category=category,profile=profile,tags=tags,notes=notes,
            expiry=expiry,fields=fields.filter{it.name.isNotBlank()}))
    }
    if(creatingCategory)AlertDialog(onDismissRequest={creatingCategory=false},title={Text("New category")},
        text={OutlinedTextField(newCategory,{newCategory=it},label={Text("Category name")},singleLine=true)},
        confirmButton={TextButton(enabled=newCategory.isNotBlank(),onClick={category=newCategory.trim();creatingCategory=false}){Text("Add")}},
        dismissButton={TextButton(onClick={creatingCategory=false}){Text("Cancel")}})
    if(choosingDate){
        val initial=runCatching{LocalDate.parse(expiry).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()}.getOrNull()
        val picker=rememberDatePickerState(initialSelectedDateMillis=initial)
        DatePickerDialog(onDismissRequest={choosingDate=false},
            confirmButton={TextButton(enabled=picker.selectedDateMillis!=null,onClick={
                expiry=java.time.Instant.ofEpochMilli(picker.selectedDateMillis!!).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                choosingDate=false
            }){Text("Choose date")}},
            dismissButton={Row{TextButton(onClick={expiry="";choosingDate=false}){Text("Clear")}
                TextButton(onClick={choosingDate=false}){Text("Cancel")}}}){DatePicker(picker)}
    }
    Column(Modifier.fillMaxSize().imePadding()){
    LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(16.dp,8.dp,16.dp,16.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
        item{OutlinedTextField(title,{title=it},label={Text("Document title")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item{
            Box {
                OutlinedButton(onClick={kindExpanded=true},modifier=Modifier.fillMaxWidth()){Text("Type: "+kind);Icon(Icons.Outlined.ArrowDropDown,null)}
                DropdownMenu(expanded=kindExpanded,onDismissRequest={kindExpanded=false}){
                    FieldSuggestions.templates.forEach{t-> DropdownMenuItem(text={Text(t.name)},onClick={
                        kind=t.name;kindExpanded=false
                        if(fields.all{it.value.isBlank()}){fields.clear();fields.addAll(t.fieldNames.map{DataField(it,"")})}
                    })}
                }
            }
        }
        item{Box{
            OutlinedButton(onClick={categoryExpanded=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Text("Category: "+category,Modifier.weight(1f));Icon(Icons.Outlined.ArrowDropDown,null)
            }
            DropdownMenu(expanded=categoryExpanded,onDismissRequest={categoryExpanded=false}){
                (categories+category+listOf("Personal","Identity","Work","Scans")).filter{it.isNotBlank()}.distinct().sorted().forEach{name->
                    DropdownMenuItem(text={Text(name)},onClick={category=name;categoryExpanded=false})
                }
                DropdownMenuItem(text={Text("New…")},onClick={categoryExpanded=false;newCategory="";creatingCategory=true})
            }
        }}
        item{
            Box {
                OutlinedButton(onClick={profileExpanded=true},modifier=Modifier.fillMaxWidth()){
                    Text("Profile: "+(profiles.firstOrNull{it.id==profile}?.name?:"Me"));Icon(Icons.Outlined.ArrowDropDown,null)
                }
                DropdownMenu(expanded=profileExpanded,onDismissRequest={profileExpanded=false}){
                    profiles.forEach{p->DropdownMenuItem(text={Text(p.name)},onClick={profile=p.id;profileExpanded=false})}
                }
            }
        }
        item{OutlinedTextField(tags,{tags=it},label={Text("Tags (comma-separated)")},modifier=Modifier.fillMaxWidth())}
        item{OutlinedTextField(expiry,{},label={Text("Expiry date")},readOnly=true,
            placeholder={Text("Not set")},modifier=Modifier.fillMaxWidth(),singleLine=true,
            trailingIcon={IconButton(onClick={choosingDate=true}){Icon(Icons.Outlined.CalendarMonth,"Choose expiry date")}})}
        item{Text("Custom fields",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)}
        items(fields.size){i->
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        OutlinedTextField(fields[i].name,{value->fields[i]=fields[i].copy(name=value)},
                            label={Text("Field name")},modifier=Modifier.weight(1f),singleLine=true)
                        IconButton(onClick={fields.removeAt(i)}){Icon(Icons.Outlined.Close,"Remove field")}
                    }
                    OutlinedTextField(fields[i].value,{value->fields[i]=fields[i].copy(value=value,source="manual",confidence=0f)},
                        label={Text("Value")},modifier=Modifier.fillMaxWidth())
                }
            }
        }
        item{OutlinedButton(onClick={fields.add(DataField("New field",""))},modifier=Modifier.fillMaxWidth()){
            Icon(Icons.Outlined.Add,null);Text(" Add custom field")
        }}
        item{OutlinedTextField(notes,{notes=it},label={Text("Notes")},modifier=Modifier.fillMaxWidth(),minLines=2)}
        item{TextButton(onClick={
            if(title.isNotBlank())onSaveTemplate(CardTemplate(java.util.UUID.randomUUID().toString(),title+" template",fields.map{it.name}.filter{it.isNotBlank()}))
        },modifier=Modifier.fillMaxWidth()){Text("Save these fields as reusable template")}}
    }
    Surface(shadowElevation=6.dp){Column(Modifier.fillMaxWidth().padding(16.dp)){
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick=onCancel,modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("Cancel")}
            Button(onClick={save()},modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                Icon(Icons.Outlined.Save,null);Text(" Save")
            }
        }
    }}
    }

}
@Composable fun ExpiriesScreen(cards:List<VaultCard>,onOpen:(VaultCard)->Unit,
    remindDays:Int=30) {
    val active=VaultExpiryRules.active(cards)
    val dated=active.mapNotNull{card->
        val days=runCatching{
            ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(card.expiry))
        }.getOrNull()
        if(days==null)null else card to days
    }
    val invalid=active.filter{it.expiry.isNotBlank()&&!VaultExpiry.isIsoDate(it.expiry)}
    val expired=dated.filter{it.second<0}.sortedBy{it.second}
    val soon=dated.filter{it.second in 0..remindDays.toLong()}.sortedBy{it.second}
    val valid=dated.filter{it.second>remindDays}.sortedBy{it.second}
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding=PaddingValues(16.dp,16.dp,16.dp,24.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Heading("Expiry & Renewal","View dates and upcoming renewals")}
        if(expired.isEmpty()&&soon.isEmpty()&&valid.isEmpty()&&invalid.isEmpty())
            item{Text("No expiry dates added yet. Edit a document to add one.",
                color=MaterialTheme.colorScheme.onSurfaceVariant)}
        fun androidx.compose.foundation.lazy.LazyListScope.group(
            title:String,entries:List<Pair<VaultCard,Long>>,color:Color){
            if(entries.isNotEmpty()){
                item{Text(title,style=MaterialTheme.typography.titleLarge,
                    modifier=Modifier.padding(top=8.dp))}
                items(entries,key={it.first.id}){(card,days)->
                    Card(onClick={onOpen(card)},
                        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){
                        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Outlined.Event,null,tint=color)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)){
                                Text(VaultNames.title(card.title),style=MaterialTheme.typography.titleMedium)
                                Text(card.expiry,color=MaterialTheme.colorScheme.onSurfaceVariant,
                                    style=MaterialTheme.typography.bodySmall)
                            }
                            Text(when{
                                days<0 -> "${-days} days overdue"
                                days==0L -> "Expires today"
                                else -> "${days} days left"
                            },color=color,style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        group("Expired",expired,VaultColors.expired)
        group("Expiring Soon",soon,VaultColors.expiring)
        group("Valid",valid,VaultColors.valid)
        if(invalid.isNotEmpty()) {
            item{Text("Dates to check",style=MaterialTheme.typography.titleLarge,
                modifier=Modifier.padding(top=8.dp))}
            items(invalid,key={it.id}){card->
                Card(onClick={onOpen(card)},
                    colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                    border=BorderStroke(1.dp,VaultColors.expiring)){
                    Column(Modifier.fillMaxWidth().padding(14.dp)){
                        Text(VaultNames.title(card.title),style=MaterialTheme.typography.titleMedium)
                        Text("Check expiry date: "+card.expiry,
                            color=VaultColors.expiring,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
