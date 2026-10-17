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
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.absoluteValue

@Composable fun ExpiryTag(card:VaultCard){
    val (status,_)=vaultValidity(card)
    val tint=when(status){
        "Expired"->VaultColors.expired
        "Expiring","Check expiry"->VaultColors.expiring
        "Valid"->VaultColors.valid
        else->MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape=RoundedCornerShape(8.dp),color=tint.copy(alpha=.13f)){
        Text(if(card.expiry.isBlank())"NO DATE SET" else
            DesignLogic.expiryLabel(card).uppercase(Locale.ROOT),
            modifier=Modifier.padding(horizontal=8.dp,vertical=5.dp),
            color=tint,style=MaterialTheme.typography.labelSmall,maxLines=1)
    }
}
/** Document Vault.html .cf: 1.586 wallet ratio, chip, top title/type,
 * subdued number and cardholder, concentric-radial surface texture. */
@Composable fun CardFace(card:VaultCard,modifier:Modifier=Modifier,onClick:(()->Unit)?=null){
    val (a,b,ink)=DesignLogic.facePalette(card)
    val foreground=Color(ink)
    val shape=RoundedCornerShape(22.dp)
    val surface=modifier.fillMaxWidth().aspectRatio(1.586f)
        .clip(shape)
        .background(Brush.linearGradient(listOf(Color(a),Color(b))))
    BoxWithConstraints(
        if(onClick!=null)surface.clickable(onClick=onClick)else surface
    ){
        val cardWidth=maxWidth
        val cardHeight=maxHeight
        val inset=cardWidth*.054f
        val titleSize=(cardWidth.value*.056f).coerceIn(13f,24f).sp
        val numberSize=(cardWidth.value*.04f).coerceIn(11f,17f).sp
        Canvas(Modifier.matchParentSize()){
            val center=Offset(size.width*1.08f,size.height*(-.25f))
            val step=size.width*.027f
            for(i in 1..36)drawCircle(foreground.copy(alpha=.08f),
                radius=step*i,center=center,style=Stroke(width=1.1.dp.toPx()))
        }
        // Hardware-like gold contact chip at 36% height, as in the HTML card.
        Box(Modifier.offset(x=inset,y=cardHeight*.36f)
            .size(width=cardWidth*.09f,height=cardWidth*.066f)
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.linearGradient(listOf(
                Color(0xFFEBD58E),Color(0xFFB9964A)))))
        Row(Modifier.fillMaxWidth().align(Alignment.TopStart)
            .padding(horizontal=inset,vertical=inset),
            horizontalArrangement=Arrangement.SpaceBetween,
            verticalAlignment=Alignment.Top){
            Text(VaultNames.title(card.title),
                modifier=Modifier.weight(1f),
                fontSize=titleSize,lineHeight=titleSize*1.12f,
                fontWeight=FontWeight.Bold,color=foreground,
                maxLines=2,overflow=TextOverflow.Ellipsis)
            Spacer(Modifier.width(10.dp))
            Text(card.category.ifBlank{card.kind}.uppercase(Locale.ROOT),
                fontSize=(cardWidth.value*.027f).coerceIn(9f,12f).sp,
                letterSpacing=1.1f.sp,
                color=foreground.copy(alpha=.7f),
                maxLines=1)
        }
        Row(Modifier.fillMaxWidth().align(Alignment.BottomStart)
            .padding(horizontal=inset,vertical=inset),
            horizontalArrangement=Arrangement.SpaceBetween,
            verticalAlignment=Alignment.Bottom){
            Column(Modifier.weight(1f)){
                Text("NO.",fontSize=(cardWidth.value*.024f).coerceIn(9f,11f).sp,
                    letterSpacing=1.2f.sp,color=foreground.copy(alpha=.6f))
                Spacer(Modifier.height(4.dp))
                Text(DesignLogic.maskedNumber(card).ifBlank{"Not added"},
                    fontSize=numberSize,fontWeight=FontWeight.Medium,
                    letterSpacing=.5.sp,color=foreground,maxLines=1,
                    overflow=TextOverflow.Ellipsis)
            }
            val holder=DesignLogic.holder(card)
            if(holder.isNotBlank()){
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment=Alignment.End){
                    Text("CARDHOLDER",fontSize=9.sp,letterSpacing=.9.sp,
                        color=foreground.copy(alpha=.65f))
                    Spacer(Modifier.height(4.dp))
                    Text(holder,fontSize=(cardWidth.value*.032f).coerceIn(10f,14f).sp,
                        color=foreground,fontWeight=FontWeight.SemiBold,
                        maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
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

/** Document Vault.html: exact five equal dock slots, central 50dp scan tile,
 * animated 52dp indicator, 68dp floating rounded container. */
@Composable fun VaultDock(items:List<DockItem>,active:String,
    onNavigate:(String)->Unit,onScan:()->Unit) {
    val palette=mockupPalette
    val selected=items.indexOfFirst{it.route==active}.coerceAtLeast(0)
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(start=14.dp,end=14.dp,bottom=14.dp,top=6.dp)
            .height(68.dp)
            .shadow(16.dp,RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(palette.dock)
    ){
        val cell=maxWidth/items.size.coerceAtLeast(1)
        val indicator by animateDpAsState(cell*selected,
            animationSpec=tween(500),label="mockup-dock-indicator")
        val visible by animateFloatAsState(if(active=="scan")0f else 1f,
            animationSpec=tween(180),label="mockup-dock-indicator-opacity")
        if(visible>0.001f)Box(
            Modifier.offset(x=indicator,y=8.dp).width(cell).height(52.dp)
                .padding(horizontal=4.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.dockDim.copy(alpha=.22f*visible))
        )
        Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically) {
            items.forEach{item->
                val isScan=item.route=="scan"
                val chosen=item.route==active
                val tint=if(chosen)palette.dockInk else palette.dockDim
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable{if(isScan)onScan() else onNavigate(item.route)},
                    verticalArrangement=Arrangement.Center,
                    horizontalAlignment=Alignment.CenterHorizontally
                ){
                    if(isScan){
                        Box(Modifier.size(50.dp).clip(RoundedCornerShape(18.dp))
                            .background(palette.accent),contentAlignment=Alignment.Center){
                            Icon(Icons.Outlined.Add,"Scan document",
                                tint=palette.accentInk,modifier=Modifier.size(24.dp))
                        }
                    }else{
                        Icon(item.icon,item.label,modifier=Modifier.size(22.dp),tint=tint)
                        Spacer(Modifier.height(4.dp))
                        Text(item.label.uppercase(java.util.Locale.ROOT),
                            color=tint,fontSize=10.sp,letterSpacing=.6.sp,
                            fontWeight=FontWeight.Medium,maxLines=1)
                    }
                }
            }
        }
    }
}
