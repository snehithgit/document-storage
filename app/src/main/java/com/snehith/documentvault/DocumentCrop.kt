package com.snehith.documentvault

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    fun detect(bitmap:Bitmap):DetectedScan{
        val defaults=listOf(PointF(.025f,.025f),PointF(.975f,.025f),PointF(.975f,.975f),PointF(.025f,.975f))
        if(!ready())return DetectedScan(defaults,false)
        val rgba=Mat();val gray=Mat();val blurred=Mat();val edges=Mat();val hierarchy=Mat()
        try {
            Utils.bitmapToMat(bitmap,rgba)
            Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray,blurred,Size(5.0,5.0),0.0)
            Imgproc.Canny(blurred,edges,45.0,140.0)
            val contours=mutableListOf<MatOfPoint>()
            Imgproc.findContours(edges,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE)
            val minimumArea=bitmap.width.toDouble()*bitmap.height.toDouble()*.16
            var best:List<Point>?=null
            var bestArea=0.0
            for(contour in contours) {
                val area=Imgproc.contourArea(contour)
                if(area<minimumArea||area<=bestArea)continue
                val contour2=MatOfPoint2f(*contour.toArray())
                val candidate=MatOfPoint2f()
                try {
                    Imgproc.approxPolyDP(contour2,candidate,.025*Imgproc.arcLength(contour2,true),true)
                    if(candidate.total()==4L) {
                        val pts=candidate.toArray().toList()
                        val convex=MatOfPoint(*pts.toTypedArray())
                        if(Imgproc.isContourConvex(convex)){best=pts;bestArea=area}
                        convex.release()
                    }
                }finally{candidate.release();contour2.release()}
            }
            contours.forEach{it.release()}
            val pts=best?:return DetectedScan(defaults,false)
            val top=pts.sortedBy{it.y}.take(2).sortedBy{it.x}
            val bottom=pts.sortedByDescending{it.y}.take(2).sortedBy{it.x}
            if(top.size!=2||bottom.size!=2)return DetectedScan(defaults,false)
            val ordered=listOf(top[0],top[1],bottom[1],bottom[0])
            val norm=ordered.map{p->PointF((p.x/bitmap.width).toFloat().coerceIn(0f,1f),
                (p.y/bitmap.height).toFloat().coerceIn(0f,1f))}
            return DetectedScan(norm,true)
        }catch(_:Exception){return DetectedScan(defaults,false)}
        finally{rgba.release();gray.release();blurred.release();edges.release();hierarchy.release()}
    }
    fun crop(bitmap:Bitmap,corners:List<PointF>):Bitmap {
        require(corners.size==4)
        if(!ready())throw IllegalStateException("OpenCV could not initialize")
        val p=corners.map{Point(it.x*bitmap.width.toDouble(),it.y*bitmap.height.toDouble())}
        fun distance(a:Point,b:Point)=hypot(a.x-b.x,a.y-b.y)
        val width=max(distance(p[0],p[1]),distance(p[3],p[2])).roundToInt().coerceIn(100,2600)
        val height=max(distance(p[0],p[3]),distance(p[1],p[2])).roundToInt().coerceIn(100,3500)
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

@Composable fun CropReviewScreen(original:Bitmap,initial:DetectedScan,onSave:(List<PointF>)->Unit,
    onKeepOriginal:()->Unit,onCancel:()->Unit){
    val points=remember(original){mutableStateListOf<PointF>().apply{addAll(initial.corners)}}
    var auto by remember{mutableStateOf(initial.automatic)}
    var size by remember{mutableStateOf(IntSize(1,1))}
    var dragging by remember{mutableIntStateOf(-1)}
    Column(Modifier.fillMaxSize().background(VaultNavy).padding(13.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("Adjust & crop",color=Color.White,style=MaterialTheme.typography.titleLarge)
        Text(if(auto)"Document edges detected. Drag blue corners to adjust." else
            "No confident border detected. Adjust the four corners manually.",
            color=VaultMuted,style=MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
            Box(Modifier.fillMaxWidth().aspectRatio(original.width.toFloat()/original.height)
                .onSizeChanged{size=it}) {
                Image(original.asImageBitmap(),"Original captured page",
                    modifier=Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
                Canvas(Modifier.fillMaxSize()
                    .pointerInput(original){
                        detectDragGestures(onDragStart={start->
                            val near=points.indices.minByOrNull{i->
                                val pt=points[i]
                                hypot(pt.x*size.width-start.x,pt.y*size.height-start.y)
                            }?:0
                            dragging=near
                        },onDragEnd={dragging=-1},onDragCancel={dragging=-1},
                            onDrag={change,_->
                                if(dragging in 0..3){
                                    points[dragging]=PointF((change.position.x/size.width).coerceIn(0f,1f),
                                        (change.position.y/size.height).coerceIn(0f,1f))
                                }
                                change.consume()
                            })
                    }) {
                    val offsets=points.map{Offset(it.x*this.size.width,it.y*this.size.height)}
                    for(i in 0..3) {
                        drawLine(Color(0xFF00A6FF),offsets[i],offsets[(i+1)%4],strokeWidth=5f)
                        drawCircle(Color(0xFF167EFF),radius=15f,center=offsets[i])
                        drawCircle(Color.White,radius=7f,center=offsets[i])
                    }
                }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={
                val fresh=DocumentCropEngine.detect(original)
                points.clear();points.addAll(fresh.corners);auto=fresh.automatic
            },modifier=Modifier.weight(1f)){Text("Re-detect")}
            OutlinedButton(onClick=onKeepOriginal,modifier=Modifier.weight(1f)){Text("Keep original")}
        }
        Button(onClick={onSave(points.toList())},modifier=Modifier.fillMaxWidth()){
            Icon(Icons.Outlined.Crop,null);Text(" Save corrected scan")
        }
        TextButton(onClick=onCancel,modifier=Modifier.fillMaxWidth()){Text("Cancel") }
    }
}
