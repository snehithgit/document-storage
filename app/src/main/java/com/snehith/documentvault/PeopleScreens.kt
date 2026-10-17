package com.snehith.documentvault

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Wipe only the clip written by us, not a newer clip copied in another app. */
/** Remembers what we copied so the clear still happens if the timer was lost. */
object PendingClipboardClear{ @Volatile var value:String="";@Volatile var deadline:Long=0L }
/** Clears the clipboard if it still holds our copied value and the time is up. */
fun clearClipboardIfExpired(context:Context){
    val value=PendingClipboardClear.value
    if(value.isEmpty()||System.currentTimeMillis()<PendingClipboardClear.deadline)return
    val application=context.applicationContext
    val clipboard=application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    try{
        val text=clipboard.primaryClip?.getItemAt(0)?.coerceToText(application)?.toString()
        if(text==value){
            if(Build.VERSION.SDK_INT>=28)clipboard.clearPrimaryClip()
            else clipboard.setPrimaryClip(ClipData.newPlainText("",""))
        }
    }catch(_:SecurityException){}
    PendingClipboardClear.value="";PendingClipboardClear.deadline=0L
}
fun copyAndClearLater(context:Context,label:String,value:String,sensitive:Boolean=false){
    if(value.isBlank())return
    val application=context.applicationContext
    val clipboard=application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip=ClipData.newPlainText(label,value)
    if(sensitive && Build.VERSION.SDK_INT>=33)
        clip.description.extras=android.os.PersistableBundle().apply{
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE,true)
        }
    clipboard.setPrimaryClip(clip)
    PendingClipboardClear.value=value
    PendingClipboardClear.deadline=System.currentTimeMillis()+ProfileLogic.CLIPBOARD_CLEAR_MS
    val handler=Handler(Looper.getMainLooper())
    handler.postDelayed({clearClipboardIfExpired(application)},ProfileLogic.CLIPBOARD_CLEAR_MS)
    if(Build.VERSION.SDK_INT<33)
        Toast.makeText(application,"Copied",Toast.LENGTH_SHORT).show()
}

@Composable private fun PersonAvatar(name:String,size:androidx.compose.ui.unit.Dp=46.dp){
    val initials=name.trim().split(Regex("\\s+")).filter{it.isNotBlank()}.take(2)
        .joinToString(""){it.take(1).uppercase()}.ifBlank{"?"}
    val p=mockupPalette
    Box(Modifier.size(size).background(p.dock,CircleShape),
        contentAlignment=Alignment.Center){
        Text(initials,color=p.dockInk,fontSize=(size.value*.34f).sp,
            fontWeight=FontWeight.Bold)
    }
}

@Composable fun PeopleScreen(profiles:List<VaultProfile>,entries:List<ProfileEntry>,
    cards:List<VaultCard>,onOpen:(VaultProfile)->Unit,onAdd:(String)->Unit){
    var creating by rememberSaveable{mutableStateOf(false)}
    var name by rememberSaveable{mutableStateOf("")}
    if(creating)AlertDialog(onDismissRequest={creating=false},title={Text("New person")},
        text={OutlinedTextField(name,{name=it},label={Text("Name or relation")},
            modifier=Modifier.fillMaxWidth(),singleLine=true)},
        confirmButton={TextButton(enabled=name.isNotBlank(),onClick={
            onAdd(name.trim());name="";creating=false
        }){Text("Create")}},
        dismissButton={TextButton(onClick={creating=false}){Text("Cancel")}})
    val p=mockupPalette
    LazyColumn(Modifier.fillMaxSize().background(p.panel),
        contentPadding=PaddingValues(16.dp,18.dp,16.dp,24.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Eyebrow("Household")
                    Text("People",color=p.ink,fontSize=32.sp,fontWeight=FontWeight.Bold)
                    Text("Keep important details together",color=p.inkMuted,fontSize=14.sp)
                }
                IconButton(onClick={creating=true},modifier=Modifier.size(48.dp)){
                    Icon(Icons.Outlined.PersonAdd,"Add person",tint=p.ink)
                }
            }
        }
        items(profiles,key={it.id}){person->
            val count=entries.count{it.personId==person.id}
            val documents=cards.count{it.profile==person.id}
            Surface(onClick={onOpen(person)},shape=RoundedCornerShape(18.dp),
                color=p.panelRaised,border=BorderStroke(1.dp,p.line)){
                Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                    PersonAvatar(person.name)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){
                        Text(person.name,color=p.ink,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                        Text(VaultCounts.format(count,"detail")+" · "+
                            VaultCounts.format(documents,"document"),
                            color=p.inkMuted,fontSize=12.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight,null,tint=p.inkFaint)
                }
            }
        }
        item{Text("Profile information is saved offline. The app PIN does not encrypt it.",
            color=p.inkFaint,fontSize=12.sp)}
    }
}
@Composable private fun PersonPinDialog(onVerify:suspend(String)->Boolean,
    onDismiss:()->Unit,onSuccess:()->Unit){
    val scope=rememberCoroutineScope()
    var pin by remember{mutableStateOf("")}
    var checking by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Enter vault PIN")},
        text={
            Column{
                OutlinedTextField(pin,{pin=it;error=false},
                    label={Text("PIN")},singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),
                    visualTransformation=PasswordVisualTransformation())
                if(error)Text("PIN incorrect or temporarily locked",
                    color=MaterialTheme.colorScheme.error)
            }
        },
        confirmButton={TextButton(enabled=pin.isNotBlank()&&!checking,onClick={
            scope.launch {
                checking=true
                try{if(withContext(Dispatchers.Default){onVerify(pin)})onSuccess()
                    else{pin="";error=true}}
                catch(cancelled:CancellationException){throw cancelled}
                catch(_:Exception){pin="";error=true}
                finally{checking=false}
            }
        }){Text("Unlock")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
@Composable fun AddToProfileDialog(
    profiles:List<VaultProfile>,fieldCount:Int,
    onChoose:(String)->Unit,onNew:(String)->Unit,onDismiss:()->Unit
){
    var adding by rememberSaveable{mutableStateOf(false)}
    var name by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,
        title={Text("Add "+VaultCounts.format(fieldCount,"detail")+" to a person")},
        text={
            Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(7.dp)){
                profiles.forEach{person->
                    OutlinedButton(onClick={onChoose(person.id)},
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                        Text(person.name,modifier=Modifier.weight(1f))
                        Icon(Icons.Outlined.ChevronRight,null)
                    }
                }
                if(adding)OutlinedTextField(name,{name=it},
                    label={Text("New person name")},singleLine=true)
                TextButton(onClick={
                    if(adding){if(name.isNotBlank())onNew(name.trim())}
                    else adding=true
                }){
                    Text(if(adding)"Create and add" else "+ New person")
                }
            }
        },
        confirmButton={},
        dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
@Composable fun PersonScreen(person:VaultProfile,entries:List<ProfileEntry>,
    cards:List<VaultCard>,hasPin:Boolean,verifyPin:suspend(String)->Boolean,
    onRemoveEntry:(ProfileEntry)->Unit,onSwitchSource:(ProfileEntry,SourceAlternative)->Unit,
    onAddTyped:(ProfileEntry)->Unit,onOpenCard:(String)->Unit,onRemovePerson:()->Unit){
    val context=LocalContext.current
    val gate=remember(person.id){RevealGate()}
    var revealed by remember(person.id){mutableStateOf(emptySet<String>())}
    var revealDeadline by remember(person.id){mutableLongStateOf(0L)}
    // Leaving the app hides revealed values and closes the PIN window.
    DisposableEffect(person.id){
        val owner=context as? androidx.activity.ComponentActivity
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP){
                revealed=emptySet();revealDeadline=0L;gate.close()
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose{owner?.lifecycle?.removeObserver(observer)}
    }
    LaunchedEffect(revealDeadline,hasPin){
        if(hasPin && revealDeadline>0L){
            delay((revealDeadline-System.currentTimeMillis()).coerceAtLeast(0L))
            revealed=emptySet()
            gate.close()
            revealDeadline=0L
        }
    }
    var pending by remember{mutableStateOf<(() -> Unit)?>(null)}
    var adding by rememberSaveable(person.id){mutableStateOf(false)}
    var deleting by rememberSaveable(person.id){mutableStateOf(false)}
    var conflict by remember{mutableStateOf<ProfileEntry?>(null)}
    var label by rememberSaveable(person.id){mutableStateOf("")}
    var value by rememberSaveable(person.id){mutableStateOf("")}
    val rows=entries.filter{it.personId==person.id}.map{ProfileLogic.resolve(it,cards)}
    // The PIN protects sensitive values only; names, dates of birth etc. copy freely.
    fun guarded(sensitive:Boolean=true,action:()->Unit){
        if(!sensitive||!hasPin||gate.isOpen(System.currentTimeMillis()))action()
        else pending=action
    }
    pending?.let{
        val action=it
        PersonPinDialog(verifyPin,{pending=null}){
            val now=System.currentTimeMillis()
            gate.open(now)
            revealDeadline=now+60_000L
            pending=null
            action()
        }
    }
    if(adding)AlertDialog(onDismissRequest={adding=false},title={Text("Add detail")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(label,{label=it},label={Text("Field name")},singleLine=true)
            OutlinedTextField(value,{value=it},label={Text("Value")})
        }},
        confirmButton={TextButton(enabled=label.isNotBlank()&&value.isNotBlank(),onClick={
            onAddTyped(ProfileEntry(personId=person.id,section=ProfileLogic.sectionFor(label),
                label=label.trim(),value=value.trim(),sensitive=ProfileLogic.isSensitive(label)))
            label="";value="";adding=false
        }){Text("Add")}},
        dismissButton={TextButton(onClick={adding=false}){Text("Cancel")}})
    if(deleting)AlertDialog(onDismissRequest={deleting=false},
        title={Text("Remove "+person.name+"?")},
        text={Text("Profile details will be removed. Documents stay in the vault.")},
        confirmButton={TextButton(onClick={deleting=false;onRemovePerson()}){Text("Remove")}},
        dismissButton={TextButton(onClick={deleting=false}){Text("Cancel")}})
    conflict?.let{entry->
        val choices=ProfileLogic.alternatives(entry,cards)
        AlertDialog(onDismissRequest={conflict=null},title={Text("Sources differ")},
            text={Column(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState())){
                choices.forEach{candidate->
                    OutlinedButton(onClick={onSwitchSource(entry,candidate);conflict=null},
                        modifier=Modifier.fillMaxWidth()){
                        Column(Modifier.fillMaxWidth()){
                            Text(candidate.cardTitle)
                            Text(if(entry.sensitive)ProfileLogic.mask(candidate.value)
                                else candidate.value,style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }},
            confirmButton={},dismissButton={TextButton(onClick={conflict=null}){Text("Close")}})
    }
    val p=mockupPalette
    LazyColumn(Modifier.fillMaxSize().background(p.panel),
        contentPadding=PaddingValues(16.dp,14.dp,16.dp,32.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                PersonAvatar(person.name,56.dp);Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)){
                    Eyebrow("Profile")
                    Text(person.name,color=p.ink,fontSize=26.sp,fontWeight=FontWeight.Bold)
                }
                IconButton(onClick={adding=true},modifier=Modifier.size(48.dp)){
                    Icon(Icons.Outlined.Add,"Add detail")
                }
                if(person.id!="self")IconButton(onClick={deleting=true},modifier=Modifier.size(48.dp)){
                    Icon(Icons.Outlined.DeleteOutline,"Remove person")
                }
            }
        }
        for(section in ProfileLogic.sections){
            val sectionRows=rows.filter{it.entry.section==section}
            if(sectionRows.isEmpty())continue
            item(key="section-$section"){
                MockupSectionHeader(section,VaultCounts.format(sectionRows.size,"detail"))
            }
            items(sectionRows,key={it.entry.id}){record->
                val entry=record.entry
                val hidden=entry.sensitive&&entry.id !in revealed
                val alternatives=ProfileLogic.alternatives(entry,cards)
                var menu by remember{mutableStateOf(false)}
                Surface(color=p.panelRaised,shape=RoundedCornerShape(16.dp),
                    border=BorderStroke(1.dp,p.line)){
                    Column(Modifier.fillMaxWidth().padding(start=14.dp,top=5.dp,end=6.dp,bottom=10.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Text(entry.label.uppercase(java.util.Locale.ROOT),
                                modifier=Modifier.weight(1f),color=p.inkFaint,fontSize=10.sp,
                                letterSpacing=1.sp,fontWeight=FontWeight.Medium)
                            if(entry.sensitive)IconButton(modifier=Modifier.size(48.dp),
                                onClick={guarded {
                                    if(entry.id in revealed)revealed=revealed-entry.id
                                    else revealed=revealed+entry.id
                                }}){
                                Icon(if(hidden)Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    if(hidden)"Reveal "+entry.label else "Hide "+entry.label,
                                    tint=p.inkMuted)
                            }
                            IconButton(modifier=Modifier.size(48.dp),
                                onClick={guarded(entry.sensitive) {
                                    copyAndClearLater(context,entry.label,record.value,entry.sensitive)
                                }},enabled=record.value.isNotBlank()){
                                Icon(Icons.Outlined.ContentCopy,"Copy "+entry.label,tint=p.inkMuted)
                            }
                            Box {
                                IconButton(onClick={menu=true},modifier=Modifier.size(48.dp)){
                                    Icon(Icons.Outlined.MoreVert,"More for "+entry.label,tint=p.inkMuted)
                                }
                                DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                                    DropdownMenuItem(text={Text("Remove field",color=p.danger)},
                                        onClick={menu=false;onRemoveEntry(entry)})
                                }
                            }
                        }
                        Text(if(hidden)ProfileLogic.mask(record.value)else record.value.ifBlank{"—"},
                            color=p.ink,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                        if(record.sourceMissing)Text("Original field unavailable",
                            color=MaterialTheme.colorScheme.error,
                            style=MaterialTheme.typography.bodySmall)
                        else if(record.sourceTitle.isNotBlank())TextButton(
                            onClick={onOpenCard(entry.cardId)}){
                            Text("From "+record.sourceTitle)
                        }
                        if(alternatives.isNotEmpty())TextButton(onClick={conflict=entry}){
                            Text("Sources differ · Review")
                        }
                     }
                }
            }
        }
        if(rows.isEmpty())item{
            Text("Add a detail or select information from a document.",
                color=p.inkMuted,fontSize=14.sp)
        }
        item{
            OutlinedButton(onClick={guarded(rows.any{it.entry.sensitive}) {
                copyAndClearLater(context,person.name,ProfileLogic.asText(rows,true),
                    rows.any{it.entry.sensitive})
            }},enabled=rows.isNotEmpty(),modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Outlined.ContentCopy,null);Text(" Copy profile")
            }
        }
    }
}
