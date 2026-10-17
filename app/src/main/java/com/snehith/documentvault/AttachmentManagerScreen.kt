package com.snehith.documentvault

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun AttachmentManagerScreen(card:VaultCard,repo:VaultRepository,onView:(VaultFile)->Unit,
    onRename:(VaultFile,String)->Unit,onDelete:(VaultFile)->Unit,onReorder:(List<String>)->Unit){
    var renameId by rememberSaveable(card.id){mutableStateOf("")}
    var filename by rememberSaveable(card.id){mutableStateOf("")}
    var deleteId by rememberSaveable(card.id){mutableStateOf("")}
    val toRename=card.files.firstOrNull{it.id==renameId}
    val toDelete=card.files.firstOrNull{it.id==deleteId}
    if(toRename!=null)AlertDialog(onDismissRequest={renameId=""},title={Text("Rename attachment")},
        text={OutlinedTextField(filename,{filename=it},singleLine=true,label={Text("Display filename")})},
        confirmButton={TextButton(onClick={onRename(toRename,filename);renameId=""},enabled=filename.isNotBlank()){Text("Save")}},
        dismissButton={TextButton(onClick={renameId=""}){Text("Cancel")}})
    if(toDelete!=null)AlertDialog(onDismissRequest={deleteId=""},title={Text("Remove attachment?")},
        text={Text("Remove "+VaultNames.label(toDelete.name)+" from this card?")},
        confirmButton={TextButton(onClick={onDelete(toDelete);deleteId=""}){
            Text("Remove",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(onClick={deleteId=""}){Text("Cancel")}})
    LazyColumn(Modifier.fillMaxSize().background(vaultWarmBrush(MaterialTheme.colorScheme.primary==VaultColors.darkAccent)),
        contentPadding=PaddingValues(16.dp,12.dp,16.dp,96.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            Text("Attachments",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            Text(VaultCounts.format(card.files.size,"file")+" · Rename, reorder or remove",
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        itemsIndexed(card.files,key={_,file->file.id}){i,file->
            HubCard(Modifier.fillMaxWidth()){
                Row(verticalAlignment=Alignment.CenterVertically){
                    VaultThumbnail(file,repo,Modifier.size(58.dp,68.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)){
                        Text(VaultNames.label(file.name),fontWeight=FontWeight.Medium,maxLines=2)
                        Text(((file.bytes/1024).coerceAtLeast(1)).toString()+" KB",
                            color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick={onView(file)},modifier=Modifier.size(48.dp)){Icon(Icons.Outlined.Visibility,"View "+VaultNames.label(file.name))}
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    TextButton(onClick={renameId=file.id;filename=file.name}){
                        Icon(Icons.Outlined.Edit,null,Modifier.size(18.dp));Text(" Rename")
                    }
                    IconButton(onClick={
                        if(i>0){
                            val ids=card.files.map{it.id}.toMutableList()
                            java.util.Collections.swap(ids,i,i-1);onReorder(ids)
                        }
                    },enabled=i>0){Icon(Icons.Outlined.KeyboardArrowUp,"Move up")}
                    IconButton(onClick={
                        if(i<card.files.lastIndex){
                            val ids=card.files.map{it.id}.toMutableList()
                            java.util.Collections.swap(ids,i,i+1);onReorder(ids)
                        }
                    },enabled=i<card.files.lastIndex){
                        Icon(Icons.Outlined.KeyboardArrowDown,"Move down")
                    }
                    IconButton(onClick={deleteId=file.id},modifier=Modifier.size(48.dp)){
                        Icon(Icons.Outlined.Delete,"Remove attachment",tint=MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if(card.files.isEmpty())item{HubCard(Modifier.fillMaxWidth()){Text("No attachments yet")}}
    }
}
@Composable fun VaultAboutScreen(onLicenses:()->Unit={}){
    val context=androidx.compose.ui.platform.LocalContext.current
    val pkg=context.packageManager.getPackageInfo(context.packageName,0)
    val p=mockupPalette
    LazyColumn(Modifier.fillMaxSize().background(p.panel),
        contentPadding=PaddingValues(18.dp,22.dp,18.dp,80.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{
            Eyebrow("About")
            Spacer(Modifier.height(10.dp))
            Text("Document Vault",color=p.ink,fontSize=32.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Version "+(pkg.versionName?:"unknown")+" · offline document wallet",
                color=p.inkMuted,fontSize=14.sp)
        }
        item{
            MockupGroup{
                MockupSettingRow(Icons.Outlined.Shield,"No internet permission",
                    "The app has no way to send anything online.",divider=false)
                MockupSettingRow(Icons.Outlined.WarningAmber,"Not encrypted",
                    "Files and ZIP backups are stored as plain files.")
                MockupSettingRow(Icons.Outlined.Lock,"PIN locks the app only",
                    "It does not encrypt documents or the index.")
                MockupSettingRow(Icons.Outlined.Info,"Suggestions only",
                    "Read values need your review. They do not verify identity.")
            }
        }
        item{
            HubCard(Modifier.fillMaxWidth()){
                Text("Open-source components and applicable licenses:",
                    style=MaterialTheme.typography.titleMedium)
                Text("OpenCV — Apache License 2.0; ONNX Runtime — MIT License; "+
                    "DocQuad model from MakeACopy — Apache License 2.0 (pinned model cdf0e668); "+
                    "PDFBox-Android — Apache License 2.0; "+
                    "Tesseract4Android — Apache License 2.0; "+
                    "Apache POI — Apache License 2.0; "+
                    "ML Kit Text Recognition — Google ML Kit terms of service.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    style=MaterialTheme.typography.bodySmall)
                Text("Full license texts are bundled with this offline app.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick=onLicenses,modifier=Modifier.fillMaxWidth()){
                    Text("Open-source licenses")
                }
            }
        }
    }
}
