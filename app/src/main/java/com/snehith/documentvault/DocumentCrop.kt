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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                        p->org.opencv.core.Point(p.x*bitmap.width.toDouble(),p.y*bitmap.height.toDouble())
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
        FileOutputStream(output).use{out->
            if(!bitmap.compress(Bitmap.CompressFormat.JPEG,93,out))throw IllegalStateException("Cannot save corrected image")
            out.fd.sync()
        }
    }
}

@Composable fun CropReviewScreen(original:Bitmap,initial:DetectedScan,
    onSave:(Int,List<PointF>)->Unit,onKeepOriginal:()->Unit,onCancel:()->Unit){
    var image by remember(original){mutableStateOf(original)}
    val points=remember(original){mutableStateListOf<PointF>().apply{addAll(initial.corners)}}
    var auto by remember{mutableStateOf(initial.automatic)}
    var quarterTurns by rememberSaveable{mutableIntStateOf(0)}
    var viewSize by remember{mutableStateOf(IntSize(1,1))}
    var dragging by remember{mutableIntStateOf(-1)}
    DisposableEffect(original) {
        onDispose {
            if(image !== original && !image.isRecycled)image.recycle()
            if(!original.isRecycled)original.recycle()
        }
    }
    val color=if(auto)Color(0xFF18A676) else Color(0xFFFF9D35)
    val scheme=MaterialTheme.colorScheme
    fun reset(){
        val fresh=DocumentCropEngine.detect(image)
        points.clear();points.addAll(fresh.corners);auto=fresh.automatic
    }
    Column(Modifier.fillMaxSize().background(scheme.background).padding(horizontal=15.dp,vertical=10.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)){
        Text("Crop document",style=MaterialTheme.typography.titleLarge,
            fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,color=scheme.onBackground)
        Text(if(auto)"Automatic border detected — adjust the handles if needed." else
            "Auto-detection uncertain. Position the four handles over the page corners.",
            color=scheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
        Surface(color=color.copy(alpha=.1f),shape=RoundedCornerShape(9.dp),
            border=BorderStroke(1.dp,color)){
            Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(if(auto)Icons.Outlined.CheckCircle else Icons.Outlined.Tune,null,tint=color,
                    modifier=Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(if(auto)"Edges found" else "Manual crop selected",color=color,
                    style=MaterialTheme.typography.labelMedium)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
            val ratio=image.width.toFloat()/image.height.coerceAtLeast(1)
            val availableW=maxWidth
            val availableH=maxHeight
            val drawingW=minOf(availableW,availableH*ratio)
            val drawingH=drawingW/ratio
            Box(Modifier.width(drawingW).height(drawingH).border(1.dp,scheme.outlineVariant)
                .onSizeChanged{viewSize=it}){
                Image(image.asImageBitmap(),"Photo to crop",modifier=Modifier.fillMaxSize(),
                    contentScale=ContentScale.FillBounds)
                Canvas(Modifier.fillMaxSize().pointerInput(image,viewSize){
                    detectDragGestures(onDragStart={start->
                        dragging=points.indices.minByOrNull{index->
                            val p=points[index]
                            hypot(p.x*viewSize.width-start.x,p.y*viewSize.height-start.y)
                        }?:-1
                    },onDragEnd={dragging=-1},onDragCancel={dragging=-1},
                        onDrag={change,_->
                            if(dragging in 0..3 && viewSize.width>0 && viewSize.height>0){
                                points[dragging]=PointF(
                                    (change.position.x/viewSize.width).coerceIn(0.01f,.99f),
                                    (change.position.y/viewSize.height).coerceIn(0.01f,.99f))
                            }
                            change.consume()
                        })
                }){
                    val coordinates=points.map{Offset(it.x*size.width,it.y*size.height)}
                    if(coordinates.size==4){
                        val poly=androidx.compose.ui.graphics.Path().apply{
                            moveTo(coordinates[0].x,coordinates[0].y)
                            for(i in 1..3)lineTo(coordinates[i].x,coordinates[i].y)
                            close()
                        }
                        drawPath(poly,color.copy(alpha=.13f))
                        drawPath(poly,Color.White,style=Stroke(width=7f))
                        drawPath(poly,color,style=Stroke(width=4f))
                        coordinates.forEachIndexed{i,p->
                            drawCircle(Color.White,radius=24f,center=p)
                            drawCircle(color,radius=19f,center=p)
                            drawCircle(Color.White,radius=6f,center=p)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick={reset()},modifier=Modifier.weight(1f)){
                Icon(Icons.Outlined.AutoFixHigh,null,Modifier.size(17.dp));Text(" Detect")
            }
            OutlinedButton(onClick={
                val matrix=android.graphics.Matrix().apply{postRotate(90f)}
                val rotated=Bitmap.createBitmap(image,0,0,image.width,image.height,matrix,true)
                if(image !== original && !image.isRecycled)image.recycle()
                image=rotated
                quarterTurns=(quarterTurns+1)%4
                reset()
            },modifier=Modifier.weight(1f)){
                Icon(Icons.Outlined.RotateRight,null,Modifier.size(17.dp));Text(" Rotate")
            }
            OutlinedButton(onClick=onKeepOriginal,modifier=Modifier.weight(1f)){Text(" Original")}
        }
        Button(onClick={onSave(quarterTurns*90,points.toList())},modifier=Modifier.fillMaxWidth()){
            Icon(Icons.Outlined.Crop,null);Spacer(Modifier.width(7.dp));Text("Apply perspective crop")
        }
        TextButton(onClick=onCancel,modifier=Modifier.fillMaxWidth()){Text("Cancel")}
    }
}
