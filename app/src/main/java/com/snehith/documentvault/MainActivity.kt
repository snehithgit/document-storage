package com.snehith.documentvault

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Saved pending share/Open With URI grants survive rotation and process recreation. */
internal class VaultLockViewModel(private val saved:SavedStateHandle) : ViewModel() {
    val unlocked=mutableStateOf(false)
    var initialized=false
    val pending=mutableStateListOf<Uri>().apply{
        addAll(saved.get<ArrayList<String>>("pending")?.map(Uri::parse).orEmpty())
    }
    val pendingView=mutableStateOf(saved.get<String>("view")?.let(Uri::parse))
    fun queue(uri:Uri) {
        if(uri !in pending){pending.add(uri);saved["pending"]=ArrayList(pending.map(Uri::toString))}
    }
    fun drain():List<Uri> = pending.toList().also{
        pending.clear();saved["pending"]=arrayListOf<String>()
    }
    fun openWith(uri:Uri) {pendingView.value=uri;saved["view"]=uri.toString()}
    fun clearOpenWith() {pendingView.value=null;saved["view"]=null as String?}
}

class MainActivity : FragmentActivity() {
    private val lockModel by lazy { ViewModelProvider(this)[VaultLockViewModel::class.java] }
    val incoming get()=lockModel.pending
    fun drainIncoming():List<Uri> = lockModel.drain()
    val pendingView get()=lockModel.pendingView
    fun clearOpenWith()=lockModel.clearOpenWith()
    fun importOpenWith(uri:Uri){lockModel.clearOpenWith();lockModel.queue(uri)}
    val operations by lazy { ViewModelProvider(this)[VaultOperationModel::class.java] }
    val unlocked get()=lockModel.unlocked
    val loaded = mutableStateOf(false)
    private val startupError=mutableStateOf<String?>(null)
    var initialRoute by mutableStateOf("home")
    lateinit var repo:VaultRepository
    lateinit var engine:DocumentEngine
    private var lastBackgroundAt=0L
    private val startupRestore=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)lifecycleScope.launch{
            try{
                val loadedRepo=withContext(Dispatchers.IO){
                    VaultStorage.get(applicationContext).also{it.restoreBackup(uri,"")}
                }
                repo=loadedRepo
                engine=withContext(Dispatchers.IO){DocumentEngine(applicationContext,loadedRepo)}
                initialRoute="home"
                startupError.value=null
                loaded.value=true
                notify("Backup restored")
            }catch(cancelled:CancellationException){throw cancelled}
            catch(e:Exception){startupError.value="Restore failed: "+(e.message?:"Invalid backup")}
        }
    }
    private fun initializeVault(destination:String="home"){
        if(loaded.value)return
        startupError.value=null
        lifecycleScope.launch{
            try{
                val pair=withContext(Dispatchers.IO){
                    val loadedRepo=VaultStorage.get(applicationContext)
                    if(loadedRepo.recoveryError!=null && destination=="home") {
                        loadedRepo.load() // Retry already runs off the UI thread.
                    }
                    val loadedEngine=DocumentEngine(applicationContext,loadedRepo)
                    loadedRepo.cleanupPreviews()
                    loadedRepo.sweepOrphanBlobs()
                    loadedRepo to loadedEngine
                }
                repo=pair.first;engine=pair.second
                if(repo.recoveryError!=null && destination!="recovery") {
                    startupError.value=repo.recoveryError
                    return@launch
                }
                if(!lockModel.initialized){
                    unlocked.value=!repo.hasPin()
                    lockModel.initialized=true
                }
                initialRoute=destination
                ExpiryWorker.schedule(applicationContext)
                loaded.value=true
            }catch(cancelled:CancellationException){throw cancelled}
            catch(e:Exception){startupError.value="Unable to open vault: "+(e.message?:"Storage error")}
        }
    }
    fun isPrivacyScreenEnabled():Boolean = getSharedPreferences("vault_options",MODE_PRIVATE).getBoolean("blockScreenshots",false)
    fun setPrivacyScreenEnabled(enabled:Boolean) {
        getSharedPreferences("vault_options",MODE_PRIVATE).edit().putBoolean("blockScreenshots",enabled).apply()
        if(enabled)window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    override fun onStop() {
        lastBackgroundAt=android.os.SystemClock.elapsedRealtime()
        super.onStop()
    }
    override fun onStart() {
        super.onStart()
        clearClipboardIfExpired(this)
        if(::repo.isInitialized && repo.hasPin() && lastBackgroundAt>0L &&
            android.os.SystemClock.elapsedRealtime()-lastBackgroundAt>60_000L) unlocked.value=false
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(8,10,13)
        window.navigationBarColor=android.graphics.Color.rgb(8,10,13)
        androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply {
            isAppearanceLightStatusBars=false
            isAppearanceLightNavigationBars=false
        }
        setPrivacyScreenEnabled(isPrivacyScreenEnabled())
        if(savedInstanceState==null)receive(intent)
        setContent {
            if(loaded.value)DocumentVaultApp(this)
            else VaultTheme(true){
                androidx.compose.material3.Surface(
                    color=androidx.compose.material3.MaterialTheme.colorScheme.background){
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.fillMaxSize(),
                        contentAlignment=androidx.compose.ui.Alignment.Center){
                        val failure=startupError.value
                        if(failure==null)androidx.compose.material3.CircularProgressIndicator()
                        else androidx.compose.foundation.layout.Column(
                            modifier=androidx.compose.ui.Modifier
                                .fillMaxSize().padding(20.dp),
                            verticalArrangement=androidx.compose.foundation.layout.Arrangement.Center,
                            horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally){
                            androidx.compose.material3.Text("Cannot open Document Vault",
                                style=androidx.compose.material3.MaterialTheme.typography.titleLarge)
                            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(12.dp))
                            androidx.compose.material3.Text(failure,
                                color=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(20.dp))
                            androidx.compose.material3.Button(onClick={initializeVault()}) {
                                androidx.compose.material3.Text("Retry")
                            }
                            androidx.compose.material3.OutlinedButton(
                                onClick={startupRestore.launch(arrayOf("application/zip","application/octet-stream"))}){
                                androidx.compose.material3.Text("Restore Backup")
                            }
                            androidx.compose.material3.TextButton(onClick={initializeVault("recovery")}) {
                                androidx.compose.material3.Text("Recovery Options")
                            }
                        }
                    }
                }
            }
        }
        initializeVault()
    }
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }
    private fun receive(intent:Intent?) {
        if(intent==null)return
        fun add(uri:Uri?) { if(uri?.scheme=="content")lockModel.queue(uri) }
        when(intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val uri=intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri
                add(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val items=intent.getParcelableArrayListExtra<android.os.Parcelable>(Intent.EXTRA_STREAM)
                items?.forEach { add(it as? Uri) }
            }
            Intent.ACTION_VIEW -> intent.data?.takeIf{it.scheme=="content"}?.let(lockModel::openWith)
        }
    }
    fun authenticate(onSuccess:()->Unit,onError:(String)->Unit = {}) {
        val prompt=BiometricPrompt(this,ContextCompat.getMainExecutor(this),
            object:BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult) {onSuccess()}
                override fun onAuthenticationError(errorCode:Int,errString:CharSequence){onError(errString.toString())}
            })
        val info=BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Document Vault")
            .setSubtitle("Use your device biometrics")
            .setNegativeButtonText("Use PIN").build()
        prompt.authenticate(info)
    }
    // Shared exports get real filenames and MIME types, unlike internal UUID.dat blobs.
    // File preparation is streamed on IO; no image/PDF decoding or copying on the UI thread.
    private var lastShareAt=0L
    fun share(file:VaultFile)=shareAll(listOf(file))
    fun shareAll(files:List<VaultFile>) {
        if(files.isEmpty())return
        val now=android.os.SystemClock.elapsedRealtime()
        if(now-lastShareAt<1300L)return
        lastShareAt=now
        val selected=files.toList()
        lifecycleScope.launch {
            try {
                val prepared=withContext(Dispatchers.IO){
                    VaultShareFiles.prepare(applicationContext,repo,selected)
                }
                if(prepared.isEmpty())return@launch
                val links=ArrayList(prepared.map{it.uri})
                val send=Intent(if(links.size==1)Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                    type=VaultFileTypes.chooserMime(prepared.map{it.type})
                    if(links.size==1)putExtra(Intent.EXTRA_STREAM,links.first())
                    else putParcelableArrayListExtra(Intent.EXTRA_STREAM,links)
                    clipData=ClipData.newUri(contentResolver,"Document",links.first()).also{clip->
                        links.drop(1).forEach{clip.addItem(ClipData.Item(it))}
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send,"Share document"))
            }catch(cancelled:CancellationException){throw cancelled}
            catch(e:Exception) {
                lastShareAt=0L
                notify(e.message?:"Unable to share document")
            }
        }
    }
    fun notify(message:String)=Toast.makeText(this,message,Toast.LENGTH_LONG).show()
}
