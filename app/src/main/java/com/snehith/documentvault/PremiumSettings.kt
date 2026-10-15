package com.snehith.documentvault

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable fun PremiumSettings(
    hasPin:Boolean,languages:List<String>,currentScript:String,privacyScreen:Boolean,
    onPrivacyScreenChanged:(Boolean)->Unit,onPin:(String,String)->Unit,onClearPin:(String)->Unit,
    onProfiles:()->Unit,onTemplates:()->Unit,onBackups:()->Unit,onRecovery:()->Unit,
    onImportLanguage:()->Unit,onScript:(String)->Unit,onRemindDays:(Int)->Unit
){
    var dialog by remember{mutableStateOf("")}
    var pin by remember{mutableStateOf("")}
    var old by remember{mutableStateOf("")}
    var confirm by remember{mutableStateOf("")}
    var days by remember{mutableStateOf("30")}
    if(dialog.isNotBlank())AlertDialog(onDismissRequest={dialog=""},title={Text(when(dialog){
        "ocr"->"Offline OCR language"
        "pin"->"App lock"
        "remind"->"Expiry reminders"
        else->"Settings"
    })},text={Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
        when(dialog){
            "ocr"->{
                Text("Choose an on-device recognition model.")
                languages.forEach{lang->
                    TextButton(onClick={onScript(lang);dialog=""}){
                        Text(if(lang==currentScript)"✓ "+lang else lang)
                    }
                }
                OutlinedButton(onClick={onImportLanguage();dialog=""}){
                    Text("Import Tesseract .traineddata")
                }
            }
            "pin"->{
                if(hasPin)OutlinedTextField(old,{old=it},label={Text("Current PIN")},
                    visualTransformation=PasswordVisualTransformation())
                OutlinedTextField(pin,{pin=it},label={Text("New PIN")},
                    visualTransformation=PasswordVisualTransformation())
                OutlinedTextField(confirm,{confirm=it},label={Text("Repeat PIN")},
                    visualTransformation=PasswordVisualTransformation())
                Button(onClick={onPin(pin,old);dialog="";pin="";old="";confirm=""},
                    enabled=pin.length>=4&&pin==confirm&&(!hasPin||old.isNotBlank())){Text("Save PIN")}
                if(hasPin)TextButton(onClick={onClearPin(old);dialog=""},enabled=old.isNotBlank()){
                    Text("Remove PIN")}
            }
            "remind"->{
                OutlinedTextField(days,{days=it},label={Text("Remind before expiry (days)")})
                Button(onClick={onRemindDays(days.toIntOrNull()?.coerceIn(1,365)?:30);dialog=""}){
                    Text("Save reminder")
                }
            }
        }
    }},confirmButton={TextButton(onClick={dialog=""}){Text("Close")}})
    LazyColumn(Modifier.fillMaxSize().background(VaultNavy),
        contentPadding=PaddingValues(15.dp,12.dp,15.dp,100.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{
            VaultHeader("Settings","Your offline document workspace")
            Spacer(Modifier.height(14.dp))
            Text("ORGANIZE",color=VaultSky,fontWeight=FontWeight.Bold)
        }
        item {NavySettingRow(Icons.Outlined.People,"Family profiles","Manage your personal and family cards",onProfiles)}
        item {NavySettingRow(Icons.Outlined.ViewAgenda,"Card templates","Your reusable document field layouts",onTemplates)}
        item {NavySettingRow(Icons.Outlined.Folder,"Categories and tags","Organize your cards",onTemplates)}
        item{
            Spacer(Modifier.height(5.dp))
            Text("DATA & BACKUP",color=VaultSky,fontWeight=FontWeight.Bold)
        }
        item{NavySettingRow(Icons.Outlined.Backup,"Backup and restore","Export an ordinary ZIP of all files and details",onBackups)}
        item{NavySettingRow(Icons.Outlined.Restore,"Recover imports","Find any unlinked files from failed saves",onRecovery)}
        item{
            Spacer(Modifier.height(5.dp))
            Text("OCR & SCANNING",color=VaultSky,fontWeight=FontWeight.Bold)
        }
        item{NavySettingRow(Icons.Outlined.Translate,"OCR language",currentScript,{dialog="ocr"})}
        item{NavySettingRow(Icons.Outlined.DocumentScanner,"Scanner quality","Offline auto-detect, perspective correction and manual crop",{dialog=""})}
        item{
            Spacer(Modifier.height(5.dp))
            Text("PRIVACY & REMINDERS",color=VaultSky,fontWeight=FontWeight.Bold)
        }
        item{
            Surface(color=VaultPanel,shape=RoundedCornerShape(14.dp)){
                Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Outlined.VisibilityOff,null,tint=VaultSky)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)){
                        Text("Block screenshots",color=Color.White,fontWeight=FontWeight.SemiBold)
                        Text("Optional · off allows normal screenshots",color=VaultMuted,style=MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked=privacyScreen,onCheckedChange=onPrivacyScreenChanged)
                }
            }
        }
        item{NavySettingRow(Icons.Outlined.Lock,"App lock",if(hasPin)"PIN configured (files are not encrypted)" else "Optional PIN / biometrics",{dialog="pin"})}
        item{NavySettingRow(Icons.Outlined.Notifications,"Expiry reminders","Notify before documents expire",{dialog="remind"})}
        item {
            Spacer(Modifier.height(8.dp))
            Text("Document Vault • Local-only files • No encryption. The optional PIN protects the app interface, not files or exported ZIPs.",
                color=VaultMuted,style=MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun NavySettingRow(
    icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,subtitle:String,onClick:()->Unit){
    Card(onClick=onClick,colors=CardDefaults.cardColors(containerColor=VaultPanel),shape=RoundedCornerShape(14.dp)){
        Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(icon,null,tint=VaultSky)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)){
                Text(title,color=Color.White,fontWeight=FontWeight.SemiBold)
                Text(subtitle,color=VaultMuted,style=MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Outlined.ChevronRight,null,tint=VaultSky)
        }
    }
}
