package com.snehith.documentvault

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

/**
 * Native, repository-backed port of Document Vault.html / VIEWS.home.
 * The sample DOCS/PROFILES array from the HTML is deliberately not used.
 * Scanner, PDF, OCR and sharing continue through the existing app callbacks.
 */
@Composable fun MockupHome(
    cards:List<VaultCard>,profiles:List<VaultProfile>,profile:String,
    reminderDays:Int,onProfile:(String)->Unit,onOpen:(VaultCard)->Unit,
    onAllCards:()->Unit,onSettings:()->Unit,onExpiries:()->Unit,
    documentStyle:String="pages",repo:VaultRepository,onBackup:()->Unit={},hidePreviews:Boolean=true
) {
    val colors=mockupPalette
    val displayed=cards.filter{profile=="all"||it.profile==profile}
        .sortedByDescending{it.created}
    val stack=displayed.take(4)
    val alert=(VaultExpiryRules.needsAttention(displayed,reminderDays)+
        displayed.filter{card->card.fields.any{f->f.confidence>0f&&f.confidence<.75f} ||
            DesignLogic.hasHolderConflict(card)}).distinctBy{it.id}.take(5)
    val categories=rememberVaultCategoryPageCounts(repo,displayed)
        .filter{it.second>0}
    val pages=categories.sumOf{it.second}
    val first=profiles.firstOrNull{it.id==profile}?.name
        ?.substringBefore(' ')?.takeIf{it.isNotBlank()}
        ?:if(profile=="all")"everyone" else "there"
    val greeting=when(LocalTime.now().hour){in 0..11->"Good morning"
        in 12..17->"Good afternoon";else->"Good evening"}
    LazyColumn(
        modifier=Modifier.fillMaxSize().background(colors.panel),
        contentPadding=PaddingValues(start=18.dp,end=18.dp,top=22.dp,bottom=30.dp),
        verticalArrangement=Arrangement.spacedBy(0.dp)
    ){
        item{
            Row(Modifier.fillMaxWidth().padding(bottom=18.dp),
                horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.Top){
                Column(Modifier.weight(1f)){
                    Row(verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        Box(Modifier.size(6.dp).background(colors.good,CircleShape))
                        MockupEyebrow("Offline · on this device",colors.good)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("$greeting,\n$first",
                        color=colors.ink,fontSize=32.sp,lineHeight=33.sp,
                        fontWeight=FontWeight.Bold,letterSpacing=(-0.9f).sp)
                }
                PersonAvatar(profiles.firstOrNull{it.id==profile}?.name?:"Everyone",42.dp,profile,
                    Modifier.clickable(onClickLabel="Open settings",onClick=onSettings))
            }
        }
        item{MockupSection(if(documentStyle=="wallet")"Your cards" else "Recent documents",
            "All "+displayed.size,onAllCards)}
        if(stack.isEmpty()){
            item{
                Column(Modifier.fillMaxWidth().padding(vertical=46.dp),
                    horizontalAlignment=Alignment.CenterHorizontally){
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp))
                        .background(colors.panelRaised),contentAlignment=Alignment.Center){
                        Icon(Icons.Outlined.FolderOpen,null,
                            tint=colors.inkFaint,modifier=Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("No documents yet",color=colors.ink,fontSize=18.sp,
                        fontWeight=FontWeight.Bold)
                    Text("Add a document to start your vault.",
                        color=colors.inkMuted,fontSize=14.sp)
                }
            }
        }else{
            if(documentStyle=="wallet"){
                item{
                    BoxWithConstraints(Modifier.fillMaxWidth()){
                        val step=58.dp
                        val height=maxWidth/1.586f + step*(stack.size-1)
                        Box(Modifier.fillMaxWidth().height(height)){
                            stack.forEachIndexed{index,card->
                                val concealed=hidePreviews&&card.category.equals("Identity",true)
                                Box(Modifier.fillMaxWidth().aspectRatio(1.586f).offset(y=step*index)
                                    .zIndex(index.toFloat()).clickable{onOpen(card)}){
                                    CardFace(card,Modifier.fillMaxWidth()
                                        .then(if(concealed&&android.os.Build.VERSION.SDK_INT>=31)Modifier.blur(14.dp) else Modifier),
                                        onClick={onOpen(card)})
                                    if(concealed)Box(Modifier.matchParentSize().background(colors.panelRaised.copy(
                                        alpha=if(android.os.Build.VERSION.SDK_INT>=31).5f else .96f)),contentAlignment=Alignment.Center){
                                        Text("Private preview · tap to view",color=colors.inkMuted,fontSize=12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }else{
                item{
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(vertical=4.dp),
                        horizontalArrangement=Arrangement.spacedBy(14.dp)){
                        displayed.take(8).forEach{card->
                            VaultDocumentTile(card,repo,Modifier.width(138.dp),uniform=true,privatePreview=hidePreviews){
                                onOpen(card)
                            }
                        }
                    }
                }
            }
        }
        item{
            MockupSection("Needs attention",
                if(alert.isEmpty())null else "Reminders",onExpiries)
            if(alert.isEmpty()){
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(colors.panelRaised)
                    .border(1.dp,colors.line,RoundedCornerShape(18.dp))
                    .padding(14.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Icon(Icons.Outlined.Shield,null,modifier=Modifier.size(20.dp),
                        tint=colors.good)
                    Text("No documents need attention right now.",
                        color=colors.inkMuted,fontSize=13.sp)
                }
            }
        }
        items(alert,key={it.id}){card->
            val days=DesignLogic.daysLeft(card)
            val overdue=(days?:1L)<0L
            val emphasis=if(overdue)colors.danger else colors.warning
            val iconBg=if(overdue)colors.dangerSoft else colors.warningSoft
            Row(Modifier.fillMaxWidth().padding(bottom=9.dp)
                .clip(RoundedCornerShape(18.dp)).background(colors.panelRaised)
                .border(1.dp,colors.line,RoundedCornerShape(18.dp))
                .clickable{onOpen(card)}.padding(13.dp),
                verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(iconBg),contentAlignment=Alignment.Center){
                    Icon(Icons.Outlined.Schedule,null,tint=emphasis,modifier=Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal=12.dp)){
                    Text(VaultNames.title(card.title),maxLines=1,
                        overflow=TextOverflow.Ellipsis,
                        color=colors.ink,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    Text(DesignLogic.expiryLabel(card),
                        color=colors.inkFaint,fontSize=12.sp)
                }
                if(days!=null){
                    Column(horizontalAlignment=Alignment.End){
                        Text(if(days<0) "${-days}" else "$days",
                            fontSize=18.sp,fontWeight=FontWeight.Bold,color=emphasis)
                        MockupEyebrow(if(days<0)"LATE" else "DAYS",colors.inkFaint)
                    }
                }
            }
        }
        item {
            MockupSection("Vault","Backup",onBackup)
            Row(Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.panelRaised)
                .border(1.dp,colors.line,RoundedCornerShape(22.dp))
                .padding(16.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(18.dp)){
                MockupCategoryRing(categories,pages,Modifier.size(92.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)){
                    if(categories.isEmpty())Text("No attachments yet",
                        color=colors.inkMuted,fontSize=13.sp)
                    else categories.take(5).forEach{(name,count)->
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp))
                                .background(mockupCategoryColor(name,colors)))
                            Spacer(Modifier.width(8.dp))
                            Text(name,Modifier.weight(1f),maxLines=1,
                                overflow=TextOverflow.Ellipsis,
                                fontSize=12.sp,color=colors.inkMuted)
                            Text(count.toString(),fontSize=12.sp,color=colors.ink,
                                fontWeight=FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
@Composable private fun MockupEyebrow(label:String,color:Color){
    Text(label.uppercase(),color=color,fontSize=10.sp,
        letterSpacing=1.3f.sp,fontWeight=FontWeight.Medium,lineHeight=12.sp)
}
@Composable private fun MockupSection(title:String,action:String?=null,onClick:()->Unit={}){
    val colors=mockupPalette
    Row(Modifier.fillMaxWidth().padding(top=24.dp,bottom=12.dp),
        verticalAlignment=Alignment.CenterVertically){
        Text(title,Modifier.weight(1f),color=colors.ink,
            fontSize=17.sp,fontWeight=FontWeight.Bold)
        if(action!=null){
            if(action=="Reminders")MockupEyebrow(action,colors.inkFaint)
            else Text(action,color=colors.accent,fontSize=13.sp,
                modifier=Modifier.clickable(onClick=onClick),
                fontWeight=FontWeight.SemiBold)
        }
    }
}
private fun mockupCategoryColor(category:String,colors:MockupPalette):Color =
    when(category.lowercase()){
        "identity"->colors.accent
        "personal"->Color(0xFFD99A2B)
        "transport"->Color(0xFFD99A2B)
        "work"->Color(0xFF4B8FB0)
        "notes"->colors.inkFaint
        "scans"->Color(0xFF7E9A8A)
        else->colors.inkFaint
    }
@Composable private fun MockupCategoryRing(
    categories:List<Pair<String,Int>>,pages:Int,modifier:Modifier=Modifier
){
    val colors=mockupPalette
    Box(modifier,contentAlignment=Alignment.Center){
        Canvas(Modifier.fillMaxSize()){
            val width=9.dp.toPx()
            val size=Size(this.size.width-width,this.size.height-width)
            val pos=Offset(width/2,width/2)
            drawArc(colors.line,0f,360f,false,pos,size,style=Stroke(width))
            var start=-90f
            categories.forEach{(category,count)->
                val angle=if(pages<=0)0f else count*360f/pages
                if(angle>3.5f)drawArc(
                    mockupCategoryColor(category,colors),start,angle-3.5f,false,
                    pos,size,style=Stroke(width,cap=StrokeCap.Round))
                start+=angle
            }
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally){
            Text(pages.toString(),color=colors.ink,fontWeight=FontWeight.Bold,
                fontSize=26.sp,lineHeight=27.sp)
            MockupEyebrow("PAGES",colors.inkFaint)
        }
    }
}
