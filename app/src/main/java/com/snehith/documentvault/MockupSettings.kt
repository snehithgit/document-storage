package com.snehith.documentvault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Building blocks for the mockup's Settings screen and bottom sheets. */

@Composable fun MockupGroup(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    val p=mockupPalette
    val shape=RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(p.panelRaised)
        .border(1.dp,p.line,shape),content=content)
}

@Composable fun MockupSettingRow(icon:ImageVector,title:String,subtitle:String,
    onClick:(()->Unit)?=null,value:String?=null,divider:Boolean=true,
    trailing:(@Composable ()->Unit)?=null){
    val p=mockupPalette
    if(divider)HorizontalDivider(color=p.line)
    Row(Modifier.fillMaxWidth()
        .then(if(onClick!=null)Modifier.clickable(role=Role.Button,onClick=onClick) else Modifier)
        .padding(horizontal=14.dp,vertical=13.dp),
        verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(p.accentSoft),
            contentAlignment=Alignment.Center){
            Icon(icon,null,tint=p.accent,modifier=Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)){
            Text(title,color=p.ink,fontSize=14.5.sp,fontWeight=FontWeight.SemiBold)
            Text(subtitle,color=p.inkFaint,fontSize=12.sp)
        }
        if(value!=null){
            Spacer(Modifier.width(8.dp))
            Text(value,color=p.inkFaint,fontSize=12.5.sp,maxLines=1,overflow=TextOverflow.Ellipsis,
                modifier=Modifier.widthIn(max=120.dp))
        }
        if(trailing!=null){
            Spacer(Modifier.width(8.dp));trailing()
        }else if(onClick!=null){
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.ChevronRight,null,tint=p.inkFaint,modifier=Modifier.size(18.dp))
        }
    }
}

@Composable fun MockupSwitch(checked:Boolean,onChange:(Boolean)->Unit){
    val p=mockupPalette
    Switch(checked=checked,onCheckedChange=onChange,colors=SwitchDefaults.colors(
        checkedTrackColor=p.accent,checkedThumbColor=p.accentInk,checkedBorderColor=Color.Transparent,
        uncheckedTrackColor=p.line,uncheckedThumbColor=p.panelRaised,
        uncheckedBorderColor=Color.Transparent))
}

/** Equal-width segmented control, like the mockup's theme switcher. */
@Composable fun MockupSegment(options:List<Pair<String,String>>,selected:String,
    onSelect:(String)->Unit){
    val p=mockupPalette
    val shape=RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(p.line.copy(alpha=.45f)).padding(4.dp),
        horizontalArrangement=Arrangement.spacedBy(4.dp)){
        options.forEach{(key,label)->
            val on=key==selected
            Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                .background(if(on)p.panelRaised else Color.Transparent)
                .clickable(role=Role.RadioButton){onSelect(key)}.padding(vertical=10.dp),
                contentAlignment=Alignment.Center){
                Text(label,color=if(on)p.ink else p.inkMuted,fontSize=13.sp,
                    fontWeight=if(on)FontWeight.Bold else FontWeight.Medium,maxLines=1)
            }
        }
    }
}

/** A selectable row for sheets (.opt-row). */
@Composable fun MockupOptionRow(title:String,subtitle:String,selected:Boolean,onClick:()->Unit){
    val p=mockupPalette
    val shape=RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(p.panel)
        .border(if(selected)1.8.dp else 1.dp,if(selected)p.accent else p.line,shape)
        .clickable(role=Role.RadioButton,onClick=onClick).padding(horizontal=14.dp,vertical=13.dp),
        verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){
            Text(title,color=p.ink,fontSize=14.5.sp,fontWeight=FontWeight.SemiBold)
            if(subtitle.isNotBlank())Text(subtitle,color=p.inkFaint,fontSize=12.sp)
        }
        Box(Modifier.size(24.dp).clip(CircleShape)
            .background(if(selected)p.accent else p.line.copy(alpha=.6f)),
            contentAlignment=Alignment.Center){
            if(selected)Icon(Icons.Outlined.Check,null,tint=p.accentInk,modifier=Modifier.size(14.dp))
        }
    }
}

/** Callout used for privacy and warning notes (.note). */
@Composable fun MockupNote(icon:ImageVector,text:String,warning:Boolean=false,
    modifier:Modifier=Modifier){
    val p=mockupPalette
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(if(warning)p.warningSoft else p.accentSoft).padding(12.dp),
        verticalAlignment=Alignment.Top){
        Icon(icon,null,tint=if(warning)p.warning else p.accent,modifier=Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text,color=p.ink,fontSize=12.sp,lineHeight=17.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MockupSheet(onDismiss:()->Unit,title:String,lead:String? = null,
    content:@Composable ColumnScope.()->Unit){
    val p=mockupPalette
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=p.panelRaised,contentColor=p.ink){
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=24.dp)
            .verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text(title,color=p.ink,fontSize=22.sp,fontWeight=FontWeight.Bold)
            if(lead!=null)Text(lead,color=p.inkMuted,fontSize=13.sp,lineHeight=18.sp)
            content()
        }
    }
}
