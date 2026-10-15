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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : FragmentActivity() {
    val incoming = mutableStateListOf<Uri>()
    val unlocked = mutableStateOf(true)
    lateinit var repo:VaultRepository
    lateinit var engine:DocumentEngine
    private var lastBackgroundAt=0L
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
        if(::repo.isInitialized && repo.hasPin() && lastBackgroundAt>0L &&
            android.os.SystemClock.elapsedRealtime()-lastBackgroundAt>60_000L) unlocked.value=false
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(6,24,45)
        window.navigationBarColor=android.graphics.Color.rgb(6,24,45)
        androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply {
            isAppearanceLightStatusBars=false
            isAppearanceLightNavigationBars=false
        }
        setPrivacyScreenEnabled(isPrivacyScreenEnabled())
        repo=VaultRepository(applicationContext)
        engine=DocumentEngine(applicationContext,repo)
        unlocked.value=!repo.hasPin()
        repo.cleanupPreviews()
        if(savedInstanceState==null)receive(intent)
        lifecycleScope.launch(Dispatchers.IO) { repo.sweepOrphanBlobs() }
        ExpiryWorker.schedule(applicationContext)
        setContent { DocumentVaultApp(this) }
    }
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }
    private fun receive(intent:Intent?) {
        if(intent==null)return
        fun add(uri:Uri?) { if(uri?.scheme=="content" && uri !in incoming) incoming.add(uri) }
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
            Intent.ACTION_VIEW -> add(intent.data)
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
    // Direct FileProvider URIs: no copy, no decryption, no delayed chooser.
    // Coalesce rapid repeated taps so old share sheets do not pop over View.
    private var lastShareAt=0L
    fun share(file:VaultFile)=shareAll(listOf(file))
    fun shareAll(files:List<VaultFile>) {
        if(files.isEmpty())return
        val now=android.os.SystemClock.elapsedRealtime()
        if(now-lastShareAt<1300L)return
        lastShareAt=now
        try {
            val links=ArrayList<Uri>(files.size)
            for(file in files) {
                val source=repo.documentFile(file)
                links.add(FileProvider.getUriForFile(this,packageName+".provider",source,file.name))
            }
            val send=Intent(if(links.size==1)Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                type=if(files.size==1)files.first().mime.ifBlank{"application/octet-stream"}else "*/*"
                if(links.size==1)putExtra(Intent.EXTRA_STREAM,links.first())
                else putParcelableArrayListExtra(Intent.EXTRA_STREAM,links)
                clipData=ClipData.newUri(contentResolver,"Document",links.first()).also{clip->
                    links.drop(1).forEach{clip.addItem(ClipData.Item(it))}
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send,"Share document"))
        }catch(e:Exception) {
            lastShareAt=0L
            notify(e.message?:"Unable to share document")
        }
    }
    fun notify(message:String)=Toast.makeText(this,message,Toast.LENGTH_LONG).show()
}
