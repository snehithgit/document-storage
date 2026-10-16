package com.snehith.documentvault

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

data class DetectedScan(val corners:List<PointF>,val automatic:Boolean)
object DocumentCropEngine {
    private fun ready():Boolean=OpenCVLoader.initLocal()
    /** GitHub DocuScan's multi-pass OpenCV quad detector, fully offline. */
    fun detect(bitmap:Bitmap,context:android.content.Context?=null):DetectedScan {
        val defaults=listOf(PointF(.07f,.07f),PointF(.93f,.07f),PointF(.93f,.93f),PointF(.07f,.93f))
        return try {
            val detected=context?.let{ctx->
                try {
                    DocQuadModel.detect(ctx.applicationContext,bitmap)?.map{
                        p->PointF(p.x*bitmap.width,p.y*bitmap.height)
                    }
                }catch(_:Throwable){null}
            } ?: com.docuscan.app.scan.Cleanup.detectCorners(bitmap)
            if(detected==null||detected.size!=4)DetectedScan(defaults,false)
            else DetectedScan(detected.map{p->PointF((p.x/bitmap.width).coerceIn(0f,1f),
                (p.y/bitmap.height).coerceIn(0f,1f))},true)
        } catch(_:Throwable){DetectedScan(defaults,false)}
    }
    fun crop(bitmap:Bitmap,corners:List<PointF>):Bitmap {
        require(corners.size==4)
        if(!ready())throw IllegalStateException("OpenCV could not initialize")
        val p=corners.map{Point(it.x*bitmap.width.toDouble(),it.y*bitmap.height.toDouble())}
        fun distance(a:Point,b:Point)=hypot(a.x-b.x,a.y-b.y)
        val width=max(distance(p[0],p[1]),distance(p[3],p[2])).roundToInt().coerceAtLeast(100)
        val height=max(distance(p[0],p[3]),distance(p[1],p[2])).roundToInt().coerceAtLeast(100)
        require(width.toLong()*height.toLong()<=32_000_000L) {
            "Perspective crop exceeds the safe image size on this device"
        }
        val src=Mat();val warped=Mat()
        val source=MatOfPoint2f(*p.toTypedArray())
        val dst=MatOfPoint2f(Point(0.0,0.0),Point(width-1.0,0.0),
            Point(width-1.0,height-1.0),Point(0.0,height-1.0))
        val transform=Imgproc.getPerspectiveTransform(source,dst)
        return try {
            Utils.bitmapToMat(bitmap,src)
            Imgproc.warpPerspective(src,warped,transform,Size(width.toDouble(),height.toDouble()))
            Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also{Utils.matToBitmap(warped,it)}
        }finally{src.release();warped.release();source.release();dst.release();transform.release()}
    }
    fun save(bitmap:Bitmap,output:File) {
        ScanOutputFiles.ensureParent(output)
        try {
            FileOutputStream(output).use{out->
                if(!bitmap.compress(Bitmap.CompressFormat.JPEG,93,out))
                    throw IllegalStateException("Cannot save corrected image")
                out.fd.sync()
            }
        }catch(e:Exception){
            // Do not leave a corrupt, half-written JPEG in the import picker.
            output.delete()
            throw e
        }
    }
}

@Composable fun CropReviewScreen(original:Bitmap,initial:DetectedScan,
    onSave:(Int,List<PointF>,ScanSettings,Boolean)->Unit,onKeepOriginal:()->Unit,
    onCancel:()->Unit,onRetake:()->Unit){
    val scanContext=LocalContext.current
    val scope=rememberCoroutineScope()
    var settings by remember{mutableStateOf(ScanSettings())}
    var preview by remember{mutableStateOf<Bitmap?>(null)}
    var detecting by remember{mutableStateOf(false)}
    var rotating by remember{mutableStateOf(false)}
    var image by remember(original){mutableStateOf(original)}
    val ownedRotations=remember(original){mutableListOf<Bitmap>()}
    val points=remember(original){mutableStateListOf<PointF>().apply{
        addAll(initial.corners.map{PointF(it.x,it.y)})
    }}
    var auto by remember(original){mutableStateOf(initial.automatic)}
    var quarterTurns by rememberSaveable(original){mutableIntStateOf(0)}
    var dragging by remember{mutableIntStateOf(-1)}
    val busy=detecting||rotating
    DisposableEffect(original){
        onDispose {
            // Cropping owns the preview; no bitmap is recycled while still displayed.
            ownedRotations.forEach { if(!it.isRecycled)it.recycle() }
            if(!original.isRecycled)original.recycle()
        }
    }
    LaunchedEffect(image,settings){
        val filtered=withContext(Dispatchers.Default){ScanEnhance.apply(image,settings)}
        preview=filtered
    }
    DisposableEffect(preview){
        val old=preview
        onDispose {if(old!=null&&old!==image&&!old.isRecycled)old.recycle()}
    }
    fun redetect() {
        if(busy)return
        val snapshot=image
        scope.launch{
            detecting=true
            try{
                val fresh=withContext(Dispatchers.Default){
                    DocumentCropEngine.detect(snapshot,scanContext)
                }
                if(image===snapshot){
                    points.clear()
                    points.addAll(fresh.corners.map{PointF(it.x,it.y)})
                    auto=fresh.automatic
                }
            }finally{detecting=false}
        }
    }
    val scheme=MaterialTheme.colorScheme
    val tint=if(auto)VaultColors.valid else VaultColors.syntax
    Column(Modifier.fillMaxSize().background(scheme.background)
        .padding(horizontal=12.dp,vertical=8.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Adjust your document",style=MaterialTheme.typography.titleLarge,
            color=scheme.onBackground)
        Text("Drag any corner. Hold it to see a magnified view.",
            color=scheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),
            contentAlignment=Alignment.Center){
            val ratio=image.width.toFloat()/image.height.coerceAtLeast(1)
            val gutter=22.dp
            val contentW=(maxWidth-2*gutter).coerceAtLeast(24.dp)
            val contentH=(maxHeight-2*gutter).coerceAtLeast(24.dp)
            val displayW=minOf(contentW,contentH*ratio)
            val displayH=displayW/ratio
            val shown=preview?.takeUnless{it.isRecycled}?:image
            Box(Modifier.width(displayW+2*gutter).height(displayH+2*gutter),
                contentAlignment=Alignment.Center){
                Image(shown.asImageBitmap(),"Image to adjust",
                    modifier=Modifier.width(displayW).height(displayH),
                    contentScale=ContentScale.FillBounds)
                val pxGutter=with(androidx.compose.ui.platform.LocalDensity.current){gutter.toPx()}
                val dragRadius=with(androidx.compose.ui.platform.LocalDensity.current){76.dp.toPx()}
                val lensRadius=with(androidx.compose.ui.platform.LocalDensity.current){54.dp.toPx()}
                Canvas(Modifier.fillMaxSize().pointerInput(image,displayW,displayH){
                    detectDragGestures(onDragStart={pos->
                        val iw=(size.width-2*pxGutter).coerceAtLeast(1f)
                        val ih=(size.height-2*pxGutter).coerceAtLeast(1f)
                        val nearest=points.indices.minByOrNull{index->
                            val p=points[index]
                            hypot(pxGutter+p.x*iw-pos.x,pxGutter+p.y*ih-pos.y)
                        }?:-1
                        dragging=nearest.takeIf{index->
                            index>=0 && hypot(
                                pxGutter+points[index].x*iw-pos.x,
                                pxGutter+points[index].y*ih-pos.y
                            )<=dragRadius
                        }?:-1
                    },onDragEnd={dragging=-1},onDragCancel={dragging=-1},
                        onDrag={change,_->
                            val idx=dragging
                            if(idx in 0..3&&!busy){
                                val iw=(size.width-2*pxGutter).coerceAtLeast(1f)
                                val ih=(size.height-2*pxGutter).coerceAtLeast(1f)
                                points[idx]=PointF(
                                    ((change.position.x-pxGutter)/iw).coerceIn(0f,1f),
                                    ((change.position.y-pxGutter)/ih).coerceIn(0f,1f))
                                auto=false
                                change.consume()
                            }
                        })
                }){
                    val iw=(size.width-2*pxGutter).coerceAtLeast(1f)
                    val ih=(size.height-2*pxGutter).coerceAtLeast(1f)
                    val corners=points.map{Offset(pxGutter+it.x*iw,pxGutter+it.y*ih)}
                    if(corners.size==4){
                        val shape=androidx.compose.ui.graphics.Path().apply{
                            moveTo(corners[0].x,corners[0].y)
                            for(i in 1..3)lineTo(corners[i].x,corners[i].y)
                            close()
                        }
                        drawPath(shape,Color.Black.copy(alpha=.22f))
                        drawPath(shape,Color.Black,style=Stroke(width=10f))
                        drawPath(shape,Color.White,style=Stroke(width=5f))
                        corners.forEachIndexed{index,center->
                            drawCircle(Color.Black,radius=24f,center=center)
                            drawCircle(Color.White,radius=19f,center=center)
                            drawCircle(if(index==dragging)VaultColors.expiring else tint,
                                radius=12f,center=center)
                            drawCircle(Color.Black,radius=4f,center=center)
                        }
                        if(dragging in 0..3){
                            val p=points[dragging]
                            val lensX=if(p.x<.5f)size.width-lensRadius-8f
                                else lensRadius+8f
                            val lensY=if(p.y<.5f)size.height-lensRadius-8f
                                else lensRadius+8f
                            val img=shown
                            if(!img.isRecycled){
                                drawIntoCanvas{canvas->
                                    val native=canvas.nativeCanvas
                                    val saveCount=native.save()
                                    val circle=android.graphics.Path().apply{
                                        addCircle(lensX,lensY,lensRadius,android.graphics.Path.Direction.CW)
                                    }
                                    native.clipPath(circle)
                                    val x=(p.x*img.width).toInt()
                                    val y=(p.y*img.height).toInt()
                                    val half=(minOf(img.width,img.height)/9).coerceAtLeast(12)
                                    val rect=android.graphics.Rect(
                                        (x-half).coerceAtLeast(0),
                                        (y-half).coerceAtLeast(0),
                                        (x+half).coerceAtMost(img.width),
                                        (y+half).coerceAtMost(img.height))
                                    val dest=android.graphics.RectF(lensX-lensRadius,lensY-lensRadius,
                                        lensX+lensRadius,lensY+lensRadius)
                                    native.drawBitmap(img,rect,dest,
                                        android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                                    native.restoreToCount(saveCount)
                                    val border=android.graphics.Paint(3).apply{
                                        color=android.graphics.Color.WHITE
                                        style=android.graphics.Paint.Style.STROKE
                                        strokeWidth=5f
                                    }
                                    native.drawCircle(lensX,lensY,lensRadius,border)
                                    border.color=android.graphics.Color.BLACK
                                    border.strokeWidth=2f
                                    native.drawLine(lensX-18f,lensY,lensX+18f,lensY,border)
                                    native.drawLine(lensX,lensY-18f,lensX,lensY+18f,border)
                                }
                            }
                        }
                    }
                }
            }
        }
        ScanEnhanceControls(settings,{settings=it})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            OutlinedButton(onClick={redetect()},enabled=!busy,
                modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                Icon(Icons.Outlined.AutoFixHigh,null,Modifier.size(18.dp))
                Text(" Detect")
            }
            OutlinedButton(onClick={
                if(!busy) {
                    val previous=image
                    rotating=true
                    scope.launch {
                        try{
                            val next=withContext(Dispatchers.Default){
                                val matrix=android.graphics.Matrix().apply{postRotate(90f)}
                                Bitmap.createBitmap(previous,0,0,previous.width,previous.height,matrix,true)
                            }
                            if(image===previous){
                                if(next!==previous)ownedRotations.add(next)
                                val transformed=CropGeometry.rotateClockwise(
                                    points.map{it.x to it.y})
                                points.clear()
                                points.addAll(transformed.map{PointF(it.first,it.second)})
                                quarterTurns=(quarterTurns+1)%4
                                image=next
                                auto=false
                            } else if(next!==previous)next.recycle()
                        }finally{rotating=false}
                    }
                }
            },enabled=!busy,modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                Icon(Icons.Outlined.RotateRight,null,Modifier.size(18.dp))
                Text(" Rotate")
            }
            OutlinedButton(onClick=onRetake,enabled=!busy,
                modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("Retake")}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick={onSave(quarterTurns*90,points.toList(),settings,true)},
                enabled=!busy,modifier=Modifier.weight(1f).heightIn(min=50.dp)){
                Text("Add Page")
            }
            Button(onClick={onSave(quarterTurns*90,points.toList(),settings,false)},
                enabled=!busy,modifier=Modifier.weight(1f).heightIn(min=50.dp)){
                Text("Save")
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            TextButton(onClick=onKeepOriginal,enabled=!busy){Text("Use original")}
            TextButton(onClick=onCancel,enabled=!busy){Text("Cancel")}
        }
    }
}
