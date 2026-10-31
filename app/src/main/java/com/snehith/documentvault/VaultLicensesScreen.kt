package com.snehith.documentvault

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read only bundled, offline license and attribution texts. */
@Composable fun VaultLicensesScreen() {
    val context=LocalContext.current
    val documents=listOf(
        "OpenCV / PDFBox / Tesseract4Android / Apache POI / MakeACopy DocQuad"
            to "licenses/Apache-2.0.txt",
        "ONNX Runtime (MIT)" to "licenses/ONNX-Runtime-MIT.txt",
        "ML Kit (Google terms)" to "licenses/ML-Kit-terms.txt")
    var selected by remember{mutableIntStateOf(0)}
    var content by remember{mutableStateOf("Loading license…")}
    LaunchedEffect(selected){
        content=try{
            withContext(Dispatchers.IO){
                context.assets.open(documents[selected].second)
                    .bufferedReader(Charsets.UTF_8).use{it.readText()}
            }
        }catch(cancelled:CancellationException){throw cancelled}
        catch(e:Exception){"License text unavailable: "+(e.message?:"unknown error")}
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),
        contentPadding=PaddingValues(bottom=48.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("Offline software attributions",
            style=MaterialTheme.typography.titleLarge)}
        item{Text("Apache-2.0 covers the listed Apache-licensed components; "+
            "ONNX Runtime uses MIT. Google ML Kit has separate Google terms.",
            color=MaterialTheme.colorScheme.onSurfaceVariant,
            style=MaterialTheme.typography.bodySmall)}
        documents.forEachIndexed{index,(name,_)->
            item{
                OutlinedButton(onClick={selected=index},
                    modifier=Modifier.fillMaxWidth(),
                    colors=ButtonDefaults.outlinedButtonColors(
                        contentColor=if(selected==index)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)){
                    Text(name)
                }
            }
        }
        item {
            Text(content,style=MaterialTheme.typography.bodySmall,
                modifier=Modifier.fillMaxWidth().padding(vertical=12.dp))
        }
    }
}
