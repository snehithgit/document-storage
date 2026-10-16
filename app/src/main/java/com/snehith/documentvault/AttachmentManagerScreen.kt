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

@Composable fun AttachmentManagerScreen(card:VaultCard,onView:(VaultFile)->Unit,
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
    LazyColumn(Modifier.fillMaxSize().background(vaultWarmBrush(MaterialTheme.colorScheme.background==VaultColors.darkBg)),
        contentPadding=PaddingValues(16.dp,12.dp,16.dp,96.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            Text("Manage attachments",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            Text(VaultCounts.format(card.files.size,"file")+" · Rename, reorder, remove",
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        itemsIndexed(card.files,key={_,file->file.id}){i,file->
            HubCard(Modifier.fillMaxWidth()){
                Row(verticalAlignment=Alignment.CenterVertically){
                    HubIcon(if(file.mime.startsWith("image/"))Icons.Outlined.Image else Icons.Outlined.Description)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)){
                        Text(VaultNames.label(file.name),fontWeight=FontWeight.Medium,maxLines=2)
                        Text(((file.bytes/1024).coerceAtLeast(1)).toString()+" KB",
                            color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick={onView(file)}){Icon(Icons.Outlined.Visibility,"Open")}
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
                    IconButton(onClick={deleteId=file.id}){
                        Icon(Icons.Outlined.Delete,"Remove attachment",tint=MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if(card.files.isEmpty())item{HubCard(Modifier.fillMaxWidth()){Text("No attachments yet")}}
    }
}
@Composable fun VaultAboutScreen(){
    val context=androidx.compose.ui.platform.LocalContext.current
    val pkg=context.packageManager.getPackageInfo(context.packageName,0)
    LazyColumn(Modifier.fillMaxSize().background(vaultWarmBrush(MaterialTheme.colorScheme.background==VaultColors.darkBg)),
        contentPadding=PaddingValues(16.dp,12.dp,16.dp,80.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{
            HubCard(Modifier.fillMaxWidth()){
                Row(verticalAlignment=Alignment.CenterVertically){
                    HubIcon(Icons.Outlined.Inventory2);Spacer(Modifier.width(12.dp))
                    Column{
                        Text("Document Vault",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                        Text("Version "+(pkg.versionName?:"unknown"),
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Text("Offline document wallet with local-only OCR and PDF tools.")
                Text("Documents and ZIP backups are not encrypted. Your optional PIN protects the interface only.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Third-party attribution: THIRD_PARTY_SCAN_NOTICES.md in the repository.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
