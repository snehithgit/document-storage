package com.snehith.documentvault

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withContext

@Composable fun PremiumSettings(
    hasPin:Boolean,languages:List<String>,currentScript:String,privacyScreen:Boolean,
    themeMode:String,onThemeModeChanged:(String)->Unit,
    onPrivacyScreenChanged:(Boolean)->Unit,onPin:(String,String)->Unit,onClearPin:(String)->Unit,
    onProfiles:()->Unit,onTemplates:()->Unit,onBackups:()->Unit,onRecovery:()->Unit,
    onImportLanguage:()->Unit,onScript:(String)->Unit,onRemindDays:(Int)->Unit,
    keepOriginal:Boolean,onKeepOriginalChanged:(Boolean)->Unit,onAbout:()->Unit,
    onOriginalUsage:suspend ()->Long,onRemoveOriginals:()->Unit,
    originalsRevision:Long=0L,
    biometricEnabled:Boolean=false,onBiometricChanged:(Boolean)->Unit={},
    profileName:String="My vault",cardCount:Int=0,pageCount:Int=0,peopleCount:Int=0
){
    val context=LocalContext.current
    var dialog by rememberSaveable{mutableStateOf("")}
    var originalBytes by remember{mutableLongStateOf(0L)}
    LaunchedEffect(originalsRevision,keepOriginal){
        originalBytes=withContext(kotlinx.coroutines.Dispatchers.IO){onOriginalUsage()}
    }
    var pin by remember{mutableStateOf("")}
    var old by remember{mutableStateOf("")}
    var confirm by remember{mutableStateOf("")}
    var days by rememberSaveable{mutableStateOf(
        context.getSharedPreferences("vault_options",android.content.Context.MODE_PRIVATE)
            .getInt("remindDays",30).toString())}
    if(dialog=="ocr")MockupSheet({dialog=""},"OCR language",
        "Bundled scripts work offline out of the box. Language packs are files you supply."){
        languages.forEach{lang->
            MockupOptionRow(lang,"",lang==currentScript){onScript(lang)}
        }
        OutlinedButton(onClick={onImportLanguage();dialog=""},
            modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),
            shape=RoundedCornerShape(16.dp)){
            Text("Add an offline recognition language")
        }
        Button(onClick={dialog=""},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),
            shape=RoundedCornerShape(16.dp)){Text("Done")}
    }
    if(dialog.isNotBlank()&&dialog!="ocr")AlertDialog(onDismissRequest={dialog=""},title={Text(when(dialog){
        "ocr"->"Offline OCR language"
        "pin"->"App lock"
        "remind"->"Expiry reminders"
        "originals"->"Remove saved originals?"
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
                    Text("Add an offline recognition language")
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
            "originals"->{
                Text("Delete retained raw scans only? The edited documents stay available. Reopening a deleted original for re-cropping will no longer be possible.")
                Button(onClick={onRemoveOriginals();dialog=""}){Text("Remove originals")}
            }
        }
    }},confirmButton={TextButton(onClick={dialog=""}){Text("Close")}})
    val p=mockupPalette
    LazyColumn(Modifier.fillMaxSize().background(p.panel),
        contentPadding=PaddingValues(18.dp,22.dp,18.dp,110.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{
            Eyebrow("Settings")
            Spacer(Modifier.height(10.dp))
            Text("Preferences",color=p.ink,fontSize=32.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            MockupGroup{
                Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(46.dp).background(p.ink,androidx.compose.foundation.shape.CircleShape),
                        contentAlignment=Alignment.Center){
                        Text(DesignLogic.initials(profileName),color=p.panel,fontSize=15.sp,
                            fontWeight=FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column{
                        Text(profileName,color=p.ink,fontSize=16.sp,fontWeight=FontWeight.Bold)
                        Text(VaultCounts.format(cardCount,"card")+" · "+
                            VaultCounts.format(pageCount,"page")+" on this device",
                            color=p.inkFaint,fontSize=12.sp)
                    }
                }
            }
        }
        item{MockupSectionHeader("Scanning")}
        item{
            MockupGroup{
                MockupSettingRow(Icons.Outlined.Translate,"OCR language","Read text offline",
                    {dialog="ocr"},value=currentScript,divider=false)
                MockupSettingRow(Icons.Outlined.Collections,"Keep untouched scan original",
                    "Keeps the original for later editing without adding another visible page",
                    {onKeepOriginalChanged(!keepOriginal)},
                    trailing={MockupSwitch(keepOriginal,onKeepOriginalChanged)})
                MockupSettingRow(Icons.Outlined.Image,"Retained original photos",
                    if(originalBytes>=1024*1024)
                        String.format(java.util.Locale.US,"%.1f MB",originalBytes/1048576.0)
                    else "${originalBytes/1024} KB",
                    if(originalBytes>0)({dialog="originals"}) else null,
                    value=if(originalBytes>0)"Remove" else null)
            }
        }
        item{MockupSectionHeader("Security")}
        item{
            MockupGroup{
                MockupSettingRow(Icons.Outlined.Lock,"App lock",
                    if(hasPin)"PIN configured (files are not encrypted)" else "Optional UI PIN",
                    {dialog="pin"},value=if(hasPin)"On" else "Off",divider=false)
                MockupSettingRow(Icons.Outlined.Fingerprint,"Unlock with biometrics",
                    "Prompt on opening when available",
                    {onBiometricChanged(!biometricEnabled)},
                    trailing={MockupSwitch(biometricEnabled,onBiometricChanged)})
                MockupSettingRow(Icons.Outlined.VisibilityOff,"Block screenshots",
                    "Optional · off allows normal screenshots",
                    {onPrivacyScreenChanged(!privacyScreen)},
                    trailing={MockupSwitch(privacyScreen,onPrivacyScreenChanged)})
            }
        }
        item{MockupSectionHeader("Data")}
        item{
            MockupGroup{
                MockupSettingRow(Icons.Outlined.Backup,"Backup and restore",
                    "Export an ordinary ZIP of all files and details",onBackups,divider=false)
                MockupSettingRow(Icons.Outlined.Restore,"Recover imports",
                    "Find any unlinked files from failed saves",onRecovery)
                MockupSettingRow(Icons.Outlined.Notifications,"Expiry reminders",
                    "Notify before documents expire",{dialog="remind"})
            }
        }
        item{MockupSectionHeader("Appearance")}
        item{
            MockupGroup(Modifier.padding(0.dp)){
                Box(Modifier.padding(14.dp)){
                    MockupSegment(listOf("system" to "System","light" to "Graphite",
                        "dark" to "OLED black"),themeMode,onThemeModeChanged)
                }
            }
        }
        item{MockupSectionHeader("People")}
        item{
            MockupGroup{
                MockupSettingRow(Icons.Outlined.People,"Family profiles",
                    "Organize documents for your family",onProfiles,
                    value=VaultCounts.format(peopleCount,"person","people"),
                    divider=false)
                MockupSettingRow(Icons.Outlined.ViewAgenda,"Information templates",
                    "Reuse common document details",onTemplates)
                MockupSettingRow(Icons.Outlined.Info,"About Document Vault",
                    "App version, privacy and open-source attribution",onAbout)
            }
        }
        item{
            MockupNote(Icons.Outlined.Shield,
                "No internet permission. Files and backups are not encrypted. "+
                "The PIN locks this app only.",modifier=Modifier.padding(top=4.dp))
        }
    }
}
