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
import java.io.File

class MainActivity : FragmentActivity() {
    val incoming = mutableStateListOf<Uri>()
    val unlocked = mutableStateOf(true)
    lateinit var repo:VaultRepository
    lateinit var engine:DocumentEngine

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE)
        repo=VaultRepository(applicationContext)
        engine=DocumentEngine(applicationContext,repo)
        unlocked.value=!repo.hasPin()
        repo.cleanupPreviews()
        receive(intent)
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
        when(intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val uri=intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if(uri!=null)incoming.add(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val items=intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if(items!=null)incoming.addAll(items)
            }
            Intent.ACTION_VIEW -> intent.data?.let{incoming.add(it)}
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
    fun share(file:VaultFile) {
        try {
            val destination=File(cacheDir,"shared").apply{mkdirs()}
            val plain=repo.openDecrypted(file)
            val clean=file.name.replace(Regex("[^a-zA-Z0-9._ -]"),"_").take(100)
            val outbound=File(destination,clean)
            plain.copyTo(outbound,overwrite=true)
            plain.delete()
            val uri=FileProvider.getUriForFile(this,packageName+".provider",outbound)
            val send=Intent(Intent.ACTION_SEND).apply {
                type=file.mime.ifBlank{"application/octet-stream"}
                putExtra(Intent.EXTRA_STREAM,uri)
                clipData=ClipData.newRawUri(file.name,uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send,"Share "+file.name))
        }catch(e:Exception){Toast.makeText(this,e.message?:"Share failed",Toast.LENGTH_LONG).show()}
    }
    fun shareAll(files:List<VaultFile>) {
        if(files.size==1){share(files.first());return}
        if(files.isEmpty())return
        try{
            val destination=File(cacheDir,"shared").apply{mkdirs()}
            val uris=arrayListOf<Uri>()
            files.forEachIndexed{i,file->
                val plain=repo.openDecrypted(file)
                val outbound=File(destination,i.toString()+"_"+file.name.replace(Regex("[^a-zA-Z0-9._ -]"),"_").take(85))
                plain.copyTo(outbound,overwrite=true);plain.delete()
                uris.add(FileProvider.getUriForFile(this,packageName+".provider",outbound))
            }
            val send=Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type="*/*";putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData=ClipData.newUri(contentResolver,"Vault file",uris.first())
                uris.drop(1).forEach{clipData?.addItem(ClipData.Item(it))}
            }
            startActivity(Intent.createChooser(send,"Share documents"))
        }catch(e:Exception){Toast.makeText(this,e.message?:"Share failed",Toast.LENGTH_LONG).show()}
    }
    fun notify(message:String)=Toast.makeText(this,message,Toast.LENGTH_LONG).show()
}
