package com.snehith.documentvault

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val finderTeal=Color(0xFF58CBE4)

/** The mockup's viewfinder card: corner brackets, a sweeping scan line, privacy line. */
@Composable private fun ScanViewfinder(){
    val sweep by rememberInfiniteTransition(label="scan-line").animateFloat(
        initialValue=.12f,targetValue=.88f,
        animationSpec=infiniteRepeatable(tween(2200,easing=LinearEasing),RepeatMode.Reverse),
        label="scan-sweep")
    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF0B1416),Color(0xFF16262B)))),
        contentAlignment=Alignment.Center){
        Canvas(Modifier.matchParentSize()){
            val inset=22.dp.toPx();val arm=30.dp.toPx();val w=3.dp.toPx()
            fun corner(x:Float,y:Float,dx:Float,dy:Float){
                drawLine(finderTeal,Offset(x,y),Offset(x+dx*arm,y),w,StrokeCap.Round)
                drawLine(finderTeal,Offset(x,y),Offset(x,y+dy*arm),w,StrokeCap.Round)
            }
            corner(inset,inset,1f,1f);corner(size.width-inset,inset,-1f,1f)
            corner(inset,size.height-inset,1f,-1f);corner(size.width-inset,size.height-inset,-1f,-1f)
            val y=size.height*sweep
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent,finderTeal,Color.Transparent)),
                Offset(inset,y),Offset(size.width-inset,y),2.dp.toPx())
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally){
            Text("Edges found automatically",color=Color(0xFFE8F1F3),fontSize=15.sp,
                fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("NOTHING LEAVES THIS DEVICE",color=Color(0xFF9DB0B5),fontSize=10.sp,
                letterSpacing=1.3f.sp,fontWeight=FontWeight.Medium)
        }
    }
}

@Composable private fun ScanOption(icon:ImageVector,title:String,subtitle:String,
    modifier:Modifier,onClick:()->Unit){
    val p=mockupPalette
    val shape=RoundedCornerShape(22.dp)
    Column(modifier.heightIn(min=128.dp).clip(shape).background(p.panelRaised)
        .border(1.dp,p.line,shape).clickable(role=Role.Button,onClick=onClick).padding(16.dp),
        verticalArrangement=Arrangement.SpaceBetween){
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(p.accentSoft),
            contentAlignment=Alignment.Center){
            Icon(icon,null,tint=p.accent,modifier=Modifier.size(22.dp))
        }
        Column{
            Text(title,color=p.ink,fontSize=16.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(subtitle,color=p.inkFaint,fontSize=12.sp)
        }
    }
}

@Composable fun NavyScanScreen(
    onCamera:()->Unit,
    onGallery:()->Unit,
    onFiles:()->Unit,
    onNew:()->Unit
){
    val p=mockupPalette
    Column(Modifier.fillMaxSize().background(p.panel).verticalScroll(rememberScrollState())
        .padding(horizontal=18.dp,vertical=22.dp)){
        Eyebrow("Add documents")
        Spacer(Modifier.height(10.dp))
        Text("New document",color=p.ink,fontSize=32.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        ScanViewfinder()
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            ScanOption(Icons.Outlined.CameraAlt,"Camera","Scan a page now",Modifier.weight(1f),onCamera)
            ScanOption(Icons.Outlined.PhotoLibrary,"Gallery","Pick several photos",Modifier.weight(1f),onGallery)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            ScanOption(Icons.Outlined.FolderOpen,"Files / PDF","Import from storage",Modifier.weight(1f),onFiles)
            ScanOption(Icons.Outlined.NoteAdd,"Manual card","Type the details",Modifier.weight(1f),onNew)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.accentSoft)
            .padding(12.dp),verticalAlignment=Alignment.Top){
            Icon(Icons.Outlined.Info,null,tint=p.accent,modifier=Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Imported originals are kept. You can add more pages to a card later. "+
                "Documents stay on this device unless you choose to share or export them.",
                color=p.ink,fontSize=12.sp,lineHeight=17.sp)
        }
    }
}
