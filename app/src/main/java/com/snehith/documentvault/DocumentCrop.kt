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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
                }catch(cancelled:CancellationException){throw cancelled}
                 catch(_:Throwable){null}
            } ?: com.docuscan.app.scan.Cleanup.detectCorners(bitmap)
            if(detected==null||detected.size!=4)DetectedScan(defaults,false)
            else DetectedScan(detected.map{p->PointF((p.x/bitmap.width).coerceIn(0f,1f),
                (p.y/bitmap.height).coerceIn(0f,1f))},true)
        } catch(cancelled:CancellationException){throw cancelled}
          catch(_:Throwable){DetectedScan(defaults,false)}
    }
    fun crop(bitmap:Bitmap,corners:List<PointF>):Bitmap {
        require(ScanQuadGeometry.valid(corners.map{it.x to it.y})){
            "Corners must form a four-sided document with enough visible area"
        }
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
        var transform:Mat?=null
        return try {
            transform=Imgproc.getPerspectiveTransform(source,dst)
            Utils.bitmapToMat(bitmap,src)
            Imgproc.warpPerspective(src,warped,transform,Size(width.toDouble(),height.toDouble()))
            Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also{Utils.matToBitmap(warped,it)}
        }finally{src.release();warped.release();source.release();dst.release();transform?.release()}
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
    onCancel:()->Unit,onRetake:()->Unit,canRetake:Boolean=true,sessionKey:String="crop"){
    val scanContext=LocalContext.current
    val scope=rememberCoroutineScope()
    var preset by rememberSaveable(sessionKey){mutableStateOf("Original")}
    var brightness by rememberSaveable(sessionKey){mutableIntStateOf(0)}
    var contrast by rememberSaveable(sessionKey){mutableFloatStateOf(1f)}
    var sharpness by rememberSaveable(sessionKey){mutableFloatStateOf(0f)}
    var shadows by rememberSaveable(sessionKey){mutableStateOf(false)}
    val settings=ScanSettings(preset,brightness,contrast,sharpness,shadows)
    fun updateSettings(next:ScanSettings){
        preset=next.preset;brightness=next.brightness
        contrast=next.contrast;sharpness=next.sharpness;shadows=next.removeShadows
    }
    var savedCorners by rememberSaveable(sessionKey) {
        mutableStateOf(CropGeometry.encode(initial.corners.map{it.x to it.y}))
    }
    val points=remember(sessionKey){mutableStateListOf<PointF>().apply{
        addAll((CropGeometry.decode(savedCorners)?:initial.corners.map{it.x to it.y})
            .map{PointF(it.first,it.second)})
    }}
    LaunchedEffect(points,sessionKey){
        snapshotFlow{points.map{it.x to it.y}}.distinctUntilChanged().collect{
            if(it.size==4)savedCorners=CropGeometry.encode(it)
        }
    }
    var quarterTurns by rememberSaveable(sessionKey){mutableIntStateOf(0)}
    var image by remember(original){mutableStateOf(original)}
    var imageTurns by remember(original){mutableIntStateOf(0)}
    var rotating by remember{mutableStateOf(false)}
    var detecting by remember{mutableStateOf(false)}
    var preview by remember(original){mutableStateOf<Bitmap?>(null)}
    var showResult by rememberSaveable(sessionKey){mutableStateOf(false)}
    var validationMessage by remember{mutableStateOf("")}
    var auto by remember(sessionKey){mutableStateOf(initial.automatic)}
    var dragging by remember{mutableIntStateOf(-1)}
    val mutex=remember(original){Mutex()}
    val busy=detecting||rotating
    // Rebuild rotated preview from the same raw source after process/rotation recreation.
    LaunchedEffect(original,quarterTurns){
        if(quarterTurns==imageTurns)return@LaunchedEffect
        rotating=true
        try{
            val turns=quarterTurns
            val rotated=withContext(Dispatchers.Default){
                val m=android.graphics.Matrix().apply{postRotate(turns*90f)}
                Bitmap.createBitmap(original,0,0,original.width,original.height,m,true)
            }
            image=rotated;imageTurns=turns
        }finally{rotating=false}
    }
    val geometry=points.map{it.x to it.y}
    // Debounce fast slider and corner changes; serialize native jobs even when cancelled.
    LaunchedEffect(image,settings,geometry,rotating){
        if(rotating||!ScanQuadGeometry.valid(geometry)) {
            preview=null
            return@LaunchedEffect
        }
        delay(150)
        val source=image
        val quad=geometry.map{PointF(it.first,it.second)}
        val filtered=withContext(Dispatchers.Default){
            mutex.withLock {
                val largest=maxOf(source.width,source.height)
                val small=if(largest>1000){
                    val scale=1000f/largest
                    Bitmap.createScaledBitmap(source,(source.width*scale).toInt().coerceAtLeast(1),
                        (source.height*scale).toInt().coerceAtLeast(1),true)
                }else source
                try {
                    val cropped=DocumentCropEngine.crop(small,quad)
                    try{ScanEnhance.apply(cropped,settings)}
                    finally{cropped.recycle()}
                }finally{if(small!==source)small.recycle()}
            }
        }
        preview=filtered
    }
    fun redetect(){
        if(busy)return
        val snapshot=image
        scope.launch{
            detecting=true
            try{
                val fresh=withContext(Dispatchers.Default){
                    DocumentCropEngine.detect(snapshot,scanContext)
                }
                if(image===snapshot){
                    points.clear();points.addAll(fresh.corners.map{PointF(it.x,it.y)})
                    validationMessage=""
                    auto=fresh.automatic
                }
            }catch(cancelled:CancellationException){throw cancelled}
            catch(e:Exception){validationMessage="Could not detect edges. Adjust corners manually."}
            finally{detecting=false}
        }
    }
    fun save(addAnother:Boolean){
        val corners=points.toList()
        if(!ScanQuadGeometry.valid(corners.map{it.x to it.y})){
            validationMessage="Adjust the four corners to form a valid document."
            showResult=false
        }else{
            validationMessage=""
            onSave(quarterTurns*90,corners,settings,addAnother)
        }
    }
    val palette=mockupPalette
    val tint=if(auto)VaultColors.valid else VaultColors.syntax
    Column(Modifier.fillMaxSize().background(palette.panel)
        .padding(horizontal=18.dp,vertical=10.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)){
        Column{
            Eyebrow("Scan review")
            Spacer(Modifier.height(4.dp))
            Text("Check your page",color=palette.ink,fontSize=24.sp,
                fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
            Text("Drag any corner. Hold it to see a magnified view.",
                color=palette.inkMuted,fontSize=13.sp)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),
            contentAlignment=Alignment.Center){
            if(showResult){
                val result=preview
                if(result!=null&&!result.isRecycled)
                    Image(result.asImageBitmap(),"Processed document preview",
                        Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
                else CircularProgressIndicator()
            }else {
            val ratio=image.width.toFloat()/image.height.coerceAtLeast(1)
            val gutter=22.dp
            val contentW=(maxWidth-gutter*2).coerceAtLeast(24.dp)
            val contentH=(maxHeight-gutter*2).coerceAtLeast(24.dp)
            val displayW=minOf(contentW,contentH*ratio)
            val displayH=displayW/ratio
            val shown=image
            Box(Modifier.width(displayW+gutter*2).height(displayH+gutter*2),
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
        }
        if(showResult && preview!=null){
            Text("Processed preview",style=MaterialTheme.typography.labelMedium)
        }
        if(validationMessage.isNotBlank())
            Text(validationMessage,color=MaterialTheme.colorScheme.error,
                style=MaterialTheme.typography.bodySmall)
        ScanEnhanceControls(settings,{next->
            showResult=ScanPreviewMode.afterSettingsChange(showResult,settings,next)
            updateSettings(next)
        })
        MockupActionRow(buildList{
            add(MockupAction(Icons.Outlined.AutoFixHigh,"Auto edges",enabled=!busy,
                onClick={redetect()}))
            add(MockupAction(Icons.Outlined.RotateRight,"Rotate",enabled=!busy,onClick={
                if(!busy){
                    val transformed=CropGeometry.rotateClockwise(points.map{it.x to it.y})
                    points.clear()
                    points.addAll(transformed.map{PointF(it.first,it.second)})
                    quarterTurns=(quarterTurns+1)%4
                    preview=null
                    showResult=false
                    auto=false
                }
            }))
            add(MockupAction(if(showResult)Icons.Outlined.Crop else Icons.Outlined.Visibility,
                if(showResult)"Corners" else "Preview",primary=showResult,enabled=!busy,
                onClick={showResult=!showResult}))
            if(canRetake)add(MockupAction(Icons.Outlined.CameraAlt,"Retake",enabled=!busy,
                onClick=onRetake))
        },tileHeight=64.dp)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            if(canRetake)OutlinedButton(onClick={save(true)},
                enabled=!busy,shape=RoundedCornerShape(16.dp),
                border=BorderStroke(1.dp,palette.line),
                colors=ButtonDefaults.outlinedButtonColors(contentColor=palette.ink),
                modifier=Modifier.weight(1f).heightIn(min=52.dp)){
                Text("Add Page")
            }
            Button(onClick={save(false)},
                enabled=!busy,shape=RoundedCornerShape(16.dp),
                colors=ButtonDefaults.buttonColors(containerColor=palette.accent,
                    contentColor=palette.accentInk),
                modifier=Modifier.weight(1.4f).heightIn(min=52.dp)){
                Icon(Icons.Outlined.Check,null,Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if(canRetake)"Save to vault" else "Save changes")
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            TextButton(onClick=onKeepOriginal,enabled=!busy){Text("Use original")}
            TextButton(onClick=onCancel,enabled=!busy){Text("Cancel")}
        }
    }
}
