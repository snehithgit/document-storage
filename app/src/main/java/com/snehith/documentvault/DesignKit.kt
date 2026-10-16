package com.snehith.documentvault

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.absoluteValue

object DesignLogic{
    val faceHues=listOf(
        0xFF0B5E70L to 0xFF0A3440L,
        0xFF3C4AA8L to 0xFF222B66L,
        0xFF8A5A14L to 0xFF4A2F0AL,
        0xFF1F7A57L to 0xFF0F4533L,
        0xFF4A5A60L to 0xFF222B2EL
    )
    fun hueIndex(kind:String):Int=(kind.lowercase(Locale.ROOT).hashCode().toLong().absoluteValue%
        faceHues.size).toInt()
    fun holder(card:VaultCard):String{
        val names=listOf("full name","name","account holder","holder name","certificate name")
        return names.firstNotNullOfOrNull{n->
            card.fields.firstOrNull{it.name.trim().equals(n,true)&&it.value.isNotBlank()}?.value
        }.orEmpty()
    }
    fun number(card:VaultCard):String{
        val names=listOf("passport number","cdc number","account number",
            "identification number","certificate number","document number","number")
        return names.firstNotNullOfOrNull{n->
            card.fields.firstOrNull{it.name.trim().equals(n,true)&&it.value.isNotBlank()}?.value
        }.orEmpty()
    }
    fun maskedNumber(card:VaultCard)=number(card).let{if(it.isBlank())"" else ProfileLogic.mask(it)}
    fun daysLeft(card:VaultCard,today:LocalDate=LocalDate.now()):Long?=
        runCatching{ChronoUnit.DAYS.between(today,LocalDate.parse(card.expiry))}.getOrNull()
    fun expiryLabel(card:VaultCard,today:LocalDate=LocalDate.now()):String{
        val days=daysLeft(card,today)?:return if(card.expiry.isBlank())"No expiry" else "Check date"
        return when {
            days<0->"Expired "+(-days)+"d ago"
            days==0L->"Expires today"
            days<=90->"In $days days"
            else->LocalDate.parse(card.expiry)
                .format(DateTimeFormatter.ofPattern("MMM yyyy",Locale.ENGLISH))
        }
    }
    /** Only cards with valid expiry dates contribute to the percentage. */
    fun trackedExpiryCount(cards:List<VaultCard>):Int =
        VaultExpiryRules.trackedCount(cards)
    fun healthyShare(cards:List<VaultCard>,today:LocalDate=LocalDate.now(),
        remindDays:Int=30):Float =
        VaultExpiryRules.healthyShare(cards,remindDays,today)
    fun initials(name:String)=name.trim().split(Regex("\\s+"))
        .filter{it.isNotBlank()}.take(2).joinToString(""){it.take(1).uppercase(Locale.ROOT)}
        .ifBlank{"?"}
}
@Composable fun ExpiryTag(card:VaultCard){
    val (status,_)=vaultValidity(card)
    val tint=when(status){
        "Expired"->VaultColors.expired
        "Expiring","Check expiry"->VaultColors.expiring
        "Valid"->VaultColors.valid
        else->MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape=RoundedCornerShape(8.dp),color=tint.copy(alpha=.13f)){
        Text(DesignLogic.expiryLabel(card).uppercase(Locale.ROOT),
            modifier=Modifier.padding(horizontal=8.dp,vertical=5.dp),
            color=tint,style=MaterialTheme.typography.labelSmall,maxLines=1)
    }
}
@Composable fun CardFace(card:VaultCard,modifier:Modifier=Modifier,onClick:(()->Unit)?=null){
    val hues=DesignLogic.faceHues[DesignLogic.hueIndex(card.kind)]
    val frame=modifier.fillMaxWidth().aspectRatio(1.586f)
        .clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(listOf(Color(hues.first),Color(hues.second))))
    Box((if(onClick!=null)frame.clickable(onClick=onClick)else frame).padding(18.dp)){
        Row(Modifier.fillMaxWidth().align(Alignment.TopStart),verticalAlignment=Alignment.CenterVertically){
            Text(card.kind.uppercase(Locale.ROOT),maxLines=1,
                color=Color.White.copy(alpha=.75f),
                modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
            Surface(shape=RoundedCornerShape(8.dp),color=Color.Black.copy(alpha=.25f)){
                Text(DesignLogic.expiryLabel(card),color=Color.White,
                    modifier=Modifier.padding(horizontal=8.dp,vertical=5.dp),
                    style=MaterialTheme.typography.labelSmall)
            }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(),
            verticalArrangement=Arrangement.spacedBy(3.dp)){
            val number=DesignLogic.maskedNumber(card)
            if(number.isNotBlank())Text(number,color=Color.White.copy(alpha=.8f),
                style=MaterialTheme.typography.labelMedium)
            Text(VaultNames.title(card.title),style=MaterialTheme.typography.titleLarge,
                maxLines=1,overflow=TextOverflow.Ellipsis,color=Color.White)
            val holder=DesignLogic.holder(card)
            if(holder.isNotBlank())Text(holder,color=Color.White.copy(alpha=.8f),
                maxLines=1,style=MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable fun SectionTitle(title:String,action:String?=null,onAction:()->Unit={}){
    Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically){
        Text(title,modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
        if(action!=null)TextButton(onClick=onAction){Text(action)}
    }
}
@Composable fun Eyebrow(text:String){
    Text(text.uppercase(Locale.ROOT),color=MaterialTheme.colorScheme.onSurfaceVariant,
        style=MaterialTheme.typography.labelSmall)
}
@Composable fun HealthRing(share:Float,modifier:Modifier=Modifier,size:Dp=92.dp){
    val value by animateFloatAsState(share.coerceIn(0f,1f),tween(180),label="ring")
    val tint=if(share>=.75f)VaultColors.valid else if(share>=.4f)VaultColors.expiring
        else VaultColors.expired
    Box(modifier.size(size),contentAlignment=Alignment.Center){
        Canvas(Modifier.fillMaxSize()){
            val width=9.dp.toPx()
            val sz=Size(this.size.width-width,this.size.height-width)
            val origin=Offset(width/2,width/2)
            drawArc(Color.Gray.copy(alpha=.25f),0f,360f,false,origin,sz,
                style=Stroke(width))
            drawArc(tint,-90f,360f*value,false,origin,sz,
                style=Stroke(width,cap=StrokeCap.Round))
        }
        Text("${(value*100).toInt()}%",style=MaterialTheme.typography.titleLarge)
    }
}
data class DockItem(val route:String,val icon:ImageVector,val label:String)
@Composable fun VaultDock(items:List<DockItem>,active:String,
    onNavigate:(String)->Unit,onScan:()->Unit){
    val current=items.indexOfFirst{it.route==active}.coerceAtLeast(0)
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=7.dp),
        verticalAlignment=Alignment.CenterVertically){
        BoxWithConstraints(Modifier.weight(1f).height(64.dp)
            .shadow(12.dp,RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.inverseSurface)){
            val cell=maxWidth/items.size
            val offset by animateDpAsState(cell*current,tween(160),label="dock")
            Box(Modifier.offset(x=offset).width(cell).fillMaxHeight().padding(6.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha=.17f)))
            Row(Modifier.fillMaxSize()){
                items.forEach{item->
                    val selected=item.route==active
                    val color by animateColorAsState(if(selected)
                        MaterialTheme.colorScheme.inverseOnSurface else
                        MaterialTheme.colorScheme.inverseOnSurface.copy(alpha=.60f),
                        tween(160),label="tab")
                    Column(Modifier.weight(1f).fillMaxHeight()
                        .clickable{onNavigate(item.route)},
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.Center){
                        Icon(item.icon,item.label,Modifier.size(22.dp),tint=color)
                        Spacer(Modifier.height(3.dp))
                        Text(item.label,color=color,style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Surface(onClick=onScan,modifier=Modifier.size(64.dp),
            shape=CircleShape,color=MaterialTheme.colorScheme.primary,
            shadowElevation=9.dp){
            Box(contentAlignment=Alignment.Center){
                Icon(Icons.Outlined.Add,"Add Document",modifier=Modifier.size(29.dp),
                    tint=MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
