package com.snehith.documentvault

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pieces of the Document Vault.html detail screen. */

/** Pointer-driven 3D tilt, as in the mockup's hero. It never consumes touches,
 * so taps and list scrolling keep working. */
@Composable fun TiltBox(modifier:Modifier=Modifier,content:@Composable BoxScope.()->Unit){
    var box by remember{mutableStateOf(IntSize.Zero)}
    var target by remember{mutableStateOf(Offset.Zero)}
    val tx by animateFloatAsState(target.x,spring(stiffness=Spring.StiffnessMedium),label="tilt-x")
    val ty by animateFloatAsState(target.y,spring(stiffness=Spring.StiffnessMedium),label="tilt-y")
    val density=LocalDensity.current.density
    Box(
        modifier.onSizeChanged{box=it}
            .pointerInput(Unit){
                awaitEachGesture{
                    val down=awaitFirstDown(requireUnconsumed=false)
                    fun track(x:Float,y:Float){
                        if(box.width>0&&box.height>0)target=Offset(
                            (x/box.width-.5f).coerceIn(-.5f,.5f),
                            (y/box.height-.5f).coerceIn(-.5f,.5f))
                    }
                    track(down.position.x,down.position.y)
                    do{
                        val event=awaitPointerEvent()
                        event.changes.firstOrNull()?.let{track(it.position.x,it.position.y)}
                    }while(event.changes.any{it.pressed})
                    target=Offset.Zero
                }
            }
            .graphicsLayer{
                rotationY=tx*14f
                rotationX=-ty*14f
                cameraDistance=12f*density
            },
        content=content)
}

@Composable fun MockupTag(text:String,tint:Color,soft:Color){
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(soft)
        .padding(horizontal=9.dp,vertical=5.dp)){
        Text(text.uppercase(Locale.ROOT),color=tint,fontSize=10.sp,
            letterSpacing=.8.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
    }
}

@Composable fun MockupSectionHeader(title:String,trailing:String?=null,
    onTrailing:(()->Unit)?=null){
    val p=mockupPalette
    Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically){
        Text(title,Modifier.weight(1f),color=p.ink,fontSize=17.sp,fontWeight=FontWeight.Bold)
        if(trailing!=null){
            if(onTrailing!=null)Text(trailing,color=p.accent,fontSize=13.sp,
                fontWeight=FontWeight.SemiBold,
                modifier=Modifier.clickable(onClick=onTrailing).padding(vertical=6.dp))
            else Text(trailing.uppercase(Locale.ROOT),color=p.inkFaint,fontSize=10.sp,
                letterSpacing=1.3f.sp,fontWeight=FontWeight.Medium)
        }
    }
}

private data class ExpiryLook(val title:String,val sub:String,val tag:String,
    val tint:Color,val soft:Color,val icon:ImageVector)

@Composable fun MockupExpiryRow(card:VaultCard,modifier:Modifier=Modifier){
    val p=mockupPalette
    val days=DesignLogic.daysLeft(card)
    val date=runCatching{LocalDate.parse(card.expiry)
        .format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.ENGLISH))}.getOrDefault(card.expiry)
    val look=when{
        card.expiry.isBlank()->ExpiryLook("No expiry date recorded","Validity not recorded",
            "Unknown",p.inkMuted,p.line.copy(alpha=.5f),Icons.Outlined.Shield)
        days==null->ExpiryLook("Check expiry date",card.expiry,"Check",
            p.warning,p.warningSoft,Icons.Outlined.Schedule)
        days<0->ExpiryLook(date,DesignLogic.humanSpan(-days)+" ago","Renew now",
            p.danger,p.dangerSoft,Icons.Outlined.Schedule)
        days<=90->ExpiryLook(date,if(days==0L)"Expires today" else DesignLogic.humanSpan(days)+" left",
            "Renew soon",p.warning,p.warningSoft,Icons.Outlined.Schedule)
        else->ExpiryLook(date,DesignLogic.humanSpan(days)+" left","Valid",
            p.good,p.goodSoft,Icons.Outlined.Schedule)
    }
    val shape=RoundedCornerShape(20.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(p.panelRaised)
        .border(1.dp,p.line,shape).padding(14.dp),
        verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(look.soft),
            contentAlignment=Alignment.Center){
            Icon(look.icon,null,tint=look.tint,modifier=Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)){
            Text(look.title,color=p.ink,fontSize=15.sp,fontWeight=FontWeight.Bold)
            Text(look.sub,color=p.inkMuted,fontSize=12.sp)
        }
        MockupTag(look.tag,look.tint,look.soft)
    }
}

data class MockupAction(val icon:ImageVector,val label:String,val primary:Boolean=false,
    val enabled:Boolean=true,val onClick:()->Unit)

@Composable fun MockupActionRow(actions:List<MockupAction>,modifier:Modifier=Modifier,
    tileHeight:androidx.compose.ui.unit.Dp=76.dp){
    val p=mockupPalette
    val shape=RoundedCornerShape(18.dp)
    Row(modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
        actions.forEach{a->
            val bg=if(a.primary)p.accent else p.panelRaised
            val fg=(if(a.primary)p.accentInk else p.ink).copy(alpha=if(a.enabled)1f else .4f)
            Column(
                Modifier.weight(1f).height(tileHeight).clip(shape).background(bg)
                    .then(if(a.primary)Modifier else Modifier.border(1.dp,p.line,shape))
                    .clickable(enabled=a.enabled,onClick=a.onClick).padding(8.dp),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.Center
            ){
                Icon(a.icon,null,tint=fg,modifier=Modifier.size(22.dp))
                Spacer(Modifier.height(6.dp))
                Text(a.label,color=fg,fontSize=12.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
            }
        }
    }
}

@Composable fun MockupPagesStrip(files:List<VaultFile>,repo:VaultRepository,
    selectedId:String?,onOpen:(VaultFile)->Unit,onAdd:()->Unit){
    val p=mockupPalette
    val shape=RoundedCornerShape(14.dp)
    Row(Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement=Arrangement.spacedBy(10.dp)){
        files.forEachIndexed{i,file->
            val on=file.id==selectedId
            Box(Modifier.size(78.dp,104.dp).clip(shape).background(p.panelRaised)
                .border(if(on)2.dp else 1.dp,if(on)p.accent else p.line,shape)
                .clickable{onOpen(file)}){
                VaultThumbnail(file,repo,Modifier.fillMaxSize().padding(4.dp))
                Text("${i+1}",
                    Modifier.align(Alignment.BottomStart).padding(6.dp)
                        .clip(RoundedCornerShape(6.dp)).background(p.dock.copy(alpha=.78f))
                        .padding(horizontal=6.dp,vertical=2.dp),
                    color=p.dockInk,fontSize=10.sp,fontWeight=FontWeight.Medium)
            }
        }
        Box(Modifier.size(78.dp,104.dp).clip(shape).border(1.dp,p.line,shape)
            .clickable(onClick=onAdd),contentAlignment=Alignment.Center){
            Icon(Icons.Outlined.Add,"Add a page",tint=p.accent,modifier=Modifier.size(24.dp))
        }
    }
}

@Composable fun MockupQuickCopy(fields:List<DataField>,onCopy:(DataField)->Unit){
    val p=mockupPalette
    val shape=RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(p.panelRaised)
        .border(1.dp,p.line,shape).padding(horizontal=14.dp)){
        if(fields.isEmpty())Text("No fields yet. Read the pages or add one.",
            color=p.inkMuted,fontSize=14.sp,modifier=Modifier.padding(vertical=16.dp))
        fields.forEachIndexed{i,f->
            if(i>0)HorizontalDivider(color=p.line)
            Row(Modifier.fillMaxWidth().padding(vertical=12.dp),
                verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Row(verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(7.dp)){
                        Text(f.name.uppercase(Locale.ROOT),color=p.inkFaint,fontSize=10.sp,
                            letterSpacing=1.2.sp,fontWeight=FontWeight.Medium,maxLines=1,
                            overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false))
                        if(f.confidence>0f&&f.confidence<.75f)
                            MockupTag("Review",p.warning,p.warningSoft)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(if(ProfileLogic.isSensitive(f.name))ProfileLogic.mask(f.value)
                        else f.value.ifBlank{"Not entered"},
                        color=p.ink,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                }
                Spacer(Modifier.width(10.dp))
                Row(Modifier.clip(RoundedCornerShape(12.dp)).background(p.accentSoft)
                    .clickable(enabled=f.value.isNotBlank()){onCopy(f)}
                    .padding(horizontal=12.dp,vertical=9.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Outlined.ContentCopy,null,tint=p.accent,modifier=Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Copy",color=p.accent,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                }
            }
        }
    }
}
