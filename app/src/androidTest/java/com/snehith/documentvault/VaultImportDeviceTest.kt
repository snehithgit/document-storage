package com.snehith.documentvault

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VaultImportDeviceTest:VaultImportContract() {
    override fun appContext():Context=InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName=="com.snehith.documentvault.uxcheck") { "Isolated uxcheck package required" }
    }
    override fun fixtureUri(file:File):Uri=appContext().let {
        FileProvider.getUriForFile(it,it.packageName+".provider",file)
    }
}
